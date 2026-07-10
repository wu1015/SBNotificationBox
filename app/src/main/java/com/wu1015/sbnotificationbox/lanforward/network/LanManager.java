package com.wu1015.sbnotificationbox.lanforward.network;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.wu1015.sbnotificationbox.R;
import com.wu1015.sbnotificationbox.lanforward.model.LanDevice;
import com.wu1015.sbnotificationbox.lanforward.model.LanMessage;
import com.wu1015.sbnotificationbox.lanforward.storage.LanPreferences;
import com.wu1015.sbnotificationbox.lanforward.storage.LanPreferences.LanMode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 局域网管理器（单例）。
 * 持久化 TCP 服务器，跟踪已连接设备，提供广播接口。
 * 支持 WakeLock/WifiLock 保活、网络变化自动恢复。
 */
public class LanManager {

    private static final String TAG = "LanManager";
    private static final int SERVER_PORT = 9877;
    private static final long DEVICE_TIMEOUT_MS = 120000; // 120 秒无心跳 = 离线
    private static final long HEALTH_CHECK_INTERVAL_MS = 30000; // 30 秒健康检查
    private static volatile LanManager instance;

    private final Context appContext;
    private MessageServer messageServer;
    private DiscoveryService discoveryService;
    private final List<LanDevice> deviceList = new CopyOnWriteArrayList<>();
    private ExecutorService sendExecutor;
    private ScheduledExecutorService timeoutExecutor;
    private ScheduledExecutorService healthCheckExecutor;

    // 保活
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private BroadcastReceiver networkReceiver;
    private final AtomicBoolean networkReceiverRegistered = new AtomicBoolean(false);

    private volatile boolean serverRunning = false;
    private String deviceName;

    public interface LanStatusListener {
        void onStatusChanged(boolean running, int deviceCount);
        void onTextReceived(LanMessage message);
    }

    private final List<LanStatusListener> listeners = new CopyOnWriteArrayList<>();

    private LanManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.deviceName = LanPreferences.getDeviceName(appContext);
        createSendExecutor();
    }

    public static LanManager getInstance(Context context) {
        if (instance == null) {
            synchronized (LanManager.class) {
                if (instance == null) {
                    instance = new LanManager(context);
                }
            }
        }
        return instance;
    }

    // === 生命周期 ===

    /** 启动局域网服务（持久化，可在 Application 或首次使用时调用） */
    public void start() {
        if (serverRunning) return;

        // 重建 sendExecutor（如果之前被 stop() 关闭）
        if (sendExecutor == null || sendExecutor.isShutdown()) {
            createSendExecutor();
        }

        // 刷新设备名和模式（用户可能在设置中改了）
        deviceName = LanPreferences.getDeviceName(appContext);
        LanMode mode = LanPreferences.getLanMode(appContext);

        String saveDir = LanPreferences.getStorageDir(appContext);
        new File(saveDir).mkdirs();

        // 获取保活锁
        acquireKeepAliveLocks();

        // 注册网络变化监听
        registerNetworkReceiver();

        // 启动 TCP 服务器（仅当模式允许接收时）
        if (mode.canReceive()) {
            String secret = LanPreferences.getConnectionSecret(appContext);
            messageServer = new MessageServer(SERVER_PORT, saveDir, secret,
                    createMessageListener());
            messageServer.start();
        } else {
            Log.i(TAG, "Send-only mode: MessageServer not started");
        }

        // 启动持续设备发现 + 心跳
        discoveryService = new DiscoveryService(appContext, deviceName, SERVER_PORT,
                new DiscoveryService.DiscoveryListener() {
                    @Override
                    public void onDeviceFound(LanDevice device) {
                        int idx = deviceList.indexOf(device);
                        if (idx >= 0) {
                            // 更新已有设备：刷新心跳时间，标记在线，更新名称
                            LanDevice existing = deviceList.get(idx);
                            existing.setLastSeen(System.currentTimeMillis());
                            existing.setOnline(true);
                            if (!existing.getDeviceName().equals(device.getDeviceName())) {
                                existing.setDeviceName(device.getDeviceName());
                            }
                            Log.d(TAG, "Device heartbeat: " + existing);
                        } else {
                            device.setOnline(true);
                            device.setLastSeen(System.currentTimeMillis());
                            deviceList.add(device);
                            Log.d(TAG, "Device found: " + device);
                        }
                        notifyStatusChanged();
                    }

                    @Override
                    public void onDiscoveryError(String error) {
                        Log.w(TAG, "Discovery error: " + error);
                    }
                });
        discoveryService.start();

        // 恢复已持久化的手动连接设备
        List<LanDevice> savedDevices = LanPreferences.loadDevices(appContext);
        for (LanDevice saved : savedDevices) {
            if (!deviceList.contains(saved)) {
                deviceList.add(saved);
                Log.d(TAG, "Restored saved device: " + saved);
            }
        }

        // 启动设备超时检测
        startTimeoutChecker();

        // 启动健康检查（定期确认服务器存活）
        startHealthChecker();

        // 启动前台保活服务
        LanKeepAliveService.start(appContext);

        serverRunning = true;
        notifyStatusChanged();
        Log.i(TAG, "LanManager started");
    }

    public void stop() {
        if (!serverRunning) return;
        if (messageServer != null) messageServer.stop();
        if (discoveryService != null) discoveryService.stop();
        if (timeoutExecutor != null) timeoutExecutor.shutdown();
        if (healthCheckExecutor != null) healthCheckExecutor.shutdown();
        if (sendExecutor != null) sendExecutor.shutdown();
        releaseKeepAliveLocks();
        unregisterNetworkReceiver();
        deviceList.clear();
        serverRunning = false;

        // 停止前台保活服务
        LanKeepAliveService.stop(appContext);

        notifyStatusChanged();
        Log.i(TAG, "LanManager stopped");
    }

    // === 保活机制 ===

    private void acquireKeepAliveLocks() {
        try {
            // CPU 部分唤醒锁（屏幕关闭时保持 CPU 运行）
            if (wakeLock == null) {
                PowerManager pm = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                            "SBNotificationBox:LANKeepAlive");
                    wakeLock.setReferenceCounted(false);
                }
            }
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(60 * 60 * 1000L); // 最长持有一小时，超时自动释放防止电池耗尽
                Log.d(TAG, "WakeLock acquired");
            }

            // 高性能 WiFi 锁（屏幕关闭时保持 WiFi 活跃）
            if (wifiLock == null) {
                WifiManager wm = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
                if (wm != null) {
                    wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                            "SBNotificationBox:LANWiFiLock");
                    wifiLock.setReferenceCounted(false);
                }
            }
            if (wifiLock != null && !wifiLock.isHeld()) {
                wifiLock.acquire();
                Log.d(TAG, "WifiLock acquired");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to acquire keep-alive locks", e);
        }
    }

    private void releaseKeepAliveLocks() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                Log.d(TAG, "WakeLock released");
            }
            if (wifiLock != null && wifiLock.isHeld()) {
                wifiLock.release();
                Log.d(TAG, "WifiLock released");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to release keep-alive locks", e);
        } finally {
            wakeLock = null;
            wifiLock = null;
        }
    }

    // === 网络变化监听 ===

    private void registerNetworkReceiver() {
        if (networkReceiverRegistered.getAndSet(true)) return;

        networkReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                ConnectivityManager cm = (ConnectivityManager)
                        appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null) {
                    NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                    boolean isConnected = activeNetwork != null && activeNetwork.isConnected();
                    Log.d(TAG, "Network change: connected=" + isConnected);
                    if (isConnected && serverRunning) {
                        // 网络操作必须在后台线程执行（onReceive 在主线程）
                        sendExecutor.execute(() -> {
                            if (discoveryService != null) {
                                discoveryService.rescan();
                            }
                            // 重启 TCP 服务器以确保绑定到新 IP
                            restartServerIfNeeded();
                        });
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ 需要使用 FLAG_RECEIVER_EXPORTED 或不声明 exported
        }
        appContext.registerReceiver(networkReceiver, filter);
        Log.d(TAG, "Network receiver registered");
    }

    private void unregisterNetworkReceiver() {
        if (!networkReceiverRegistered.getAndSet(false)) return;
        try {
            if (networkReceiver != null) {
                appContext.unregisterReceiver(networkReceiver);
                networkReceiver = null;
                Log.d(TAG, "Network receiver unregistered");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to unregister network receiver", e);
        }
    }

    // === 健康检查 ===

    private void startHealthChecker() {
        if (healthCheckExecutor != null && !healthCheckExecutor.isShutdown()) {
            healthCheckExecutor.shutdown();
        }
        healthCheckExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HealthCheck");
            t.setDaemon(true);
            return t;
        });
        healthCheckExecutor.scheduleAtFixedRate(this::checkServerHealth,
                HEALTH_CHECK_INTERVAL_MS, HEALTH_CHECK_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private void checkServerHealth() {
        if (!serverRunning) return;

        // 刷新 WakeLock（超时前重新获取）
        refreshWakeLockIfNeeded();

        try {
            // 简单健康检查：尝试创建一个到本地的短连接
            java.net.Socket testSocket = new java.net.Socket();
            testSocket.connect(new java.net.InetSocketAddress("127.0.0.1", SERVER_PORT), 3000);
            testSocket.close();
            Log.d(TAG, "Health check: server OK");
        } catch (Exception e) {
            Log.w(TAG, "Health check failed, restarting server...", e);
            restartServerIfNeeded();
        }
    }

    /** 在 WakeLock 超时前重新获取 */
    private void refreshWakeLockIfNeeded() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
            if (wakeLock != null) {
                wakeLock.acquire(60 * 60 * 1000L); // 重新获取 1 小时超时
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to refresh WakeLock", e);
        }
    }

    private synchronized void restartServerIfNeeded() {
        if (!serverRunning) return;
        Log.i(TAG, "Restarting server components...");

        LanMode mode = LanPreferences.getLanMode(appContext);

        // 重启 TCP 服务器（仅当模式允许接收时）
        if (mode.canReceive()) {
            if (messageServer != null) {
                messageServer.stop();
            }
            String saveDir = LanPreferences.getStorageDir(appContext);
            String secret = LanPreferences.getConnectionSecret(appContext);
            messageServer = new MessageServer(SERVER_PORT, saveDir, secret,
                    createMessageListener());
            messageServer.start();
        } else {
            Log.d(TAG, "Send-only mode: skipping MessageServer restart");
        }

        // 重新发送广播
        if (discoveryService != null) {
            discoveryService.rescan();
        }

        Log.i(TAG, "Server restarted");
    }

    /** 创建 MessageServer 回调（避免在 restart 中重复代码） */
    private MessageServer.MessageListener createMessageListener() {
        return new MessageServer.MessageListener() {
            @Override
            public void onTextMessage(LanMessage message) {
                Log.d(TAG, "Text from " + message.getDeviceName() + ": " + message.getContent());
                if (message.getIpAddress() != null) {
                    touchDevice(message.getDeviceName(), message.getIpAddress());
                }
                // 发送系统通知（走 MyNotificationListenerService 的保存路径，不在此处重复保存）
                sendLanNotification(message);
                for (LanStatusListener l : listeners) {
                    l.onTextReceived(message);
                }
            }

            @Override
            public LanMessage onFileMessageRequest(LanMessage message) {
                message.setFileReceived(true);
                return message;
            }

            @Override
            public void onFileReceived(LanMessage message) {
                Log.i(TAG, "File received: " + message.getFileName());
                if (message.getIpAddress() != null) {
                    touchDevice(message.getDeviceName(), message.getIpAddress());
                }
                for (LanStatusListener l : listeners) {
                    l.onTextReceived(message);
                }
            }
        };
    }

    // === 设备超时检测 ===

    private void startTimeoutChecker() {
        if (timeoutExecutor != null && !timeoutExecutor.isShutdown()) {
            timeoutExecutor.shutdown();
        }
        timeoutExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DeviceTimeout");
            t.setDaemon(true);
            return t;
        });
        timeoutExecutor.scheduleAtFixedRate(this::checkDeviceTimeouts,
                30, 30, TimeUnit.SECONDS);
    }

    private void checkDeviceTimeouts() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (LanDevice device : deviceList) {
            if (device.isOnline() && (now - device.getLastSeen()) > DEVICE_TIMEOUT_MS) {
                device.setOnline(false);
                changed = true;
                Log.d(TAG, "Device offline (timeout): " + device);
            }
        }
        if (changed) {
            notifyStatusChanged();
        }
    }

    private void createSendExecutor() {
        sendExecutor = Executors.newFixedThreadPool(8, r -> {
            Thread t = new Thread(r, "LanSend");
            t.setDaemon(true);
            return t;
        });
    }

    // === 通知 + 持久化 ===

    private static final String LAN_CHANNEL_ID = "lan_forward_channel";
    private static final String LAN_CHANNEL_NAME = "LAN Messages";
    private static int lanNotificationId = 2000;

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager)
                    appContext.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(LAN_CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        LAN_CHANNEL_ID, LAN_CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_DEFAULT);
                nm.createNotificationChannel(channel);
            }
        }
    }

    /** 发系统通知 */
    private void sendLanNotification(LanMessage message) {
        try {
            ensureNotificationChannel();
            NotificationCompat.Builder builder = new NotificationCompat.Builder(
                    appContext, LAN_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("LAN: " + message.getDeviceName())
                    .setContentText(message.getContent())
                    .setStyle(new NotificationCompat.BigTextStyle()
                            .bigText(message.getContent()))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true);

            NotificationManager nm = (NotificationManager)
                    appContext.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.notify(lanNotificationId++, builder.build());
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to send LAN notification", e);
        }
    }

    /**
     * 持久化写入日志文件（仅接收到的消息和手动发送的消息）。
     * 转发出去的通知不在此处保存（由 MyNotificationListenerService 统一记录）。
     */
    private void appendToLanLog(LanMessage message) {
        FileOutputStream fos = null;
        OutputStreamWriter writer = null;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.US);
            String date = sdf.format(new Date());
            String fileName = date + "_notifications_log.md";

            fos = appContext.openFileOutput(fileName, Context.MODE_APPEND);
            writer = new OutputStreamWriter(fos, "UTF-8");

            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    .format(new Date(message.getTimestamp()));
            String direction = message.getDirection() == LanMessage.Direction.SENT
                    ? "SENT to " : "RECV from ";
            String entry = time + " " + direction + message.getDeviceName() + "\n"
                    + message.getContent() + "\n\n";
            writer.write(entry);
            writer.flush();
        } catch (IOException e) {
            Log.w(TAG, "Failed to write LAN log", e);
        } finally {
            // 安全关闭流，防止资源泄露
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {}
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException ignored) {}
            }
        }
    }

    // === 状态查询 ===

    public boolean isRunning() { return serverRunning; }

    public int getDeviceCount() { return deviceList.size(); }

    public List<LanDevice> getDevices() { return new ArrayList<>(deviceList); }

    public String getDeviceName() { return deviceName; }

    public int getServerPort() { return SERVER_PORT; }

    // === 设备管理（手动连接/断开） ===

    /** 手动添加设备（持久化 + 加入列表） */
    public void addManualDevice(LanDevice device) {
        if (!deviceList.contains(device)) {
            device.setOnline(true); // 手动添加默认在线（用户明确知道该设备）
            device.setLastSeen(System.currentTimeMillis());
            deviceList.add(device);
            Log.d(TAG, "Manual device added: " + device);
        } else {
            // 已在列表中，标记在线
            int idx = deviceList.indexOf(device);
            if (idx >= 0) {
                deviceList.get(idx).setOnline(true);
                deviceList.get(idx).setLastSeen(System.currentTimeMillis());
            }
        }
        // 总是持久化（即使设备已通过发现存在于列表中）
        try {
            LanPreferences.addSavedDevice(appContext, device);
        } catch (Exception e) {
            Log.w(TAG, "Failed to persist device, but it's still in memory", e);
        }
        notifyStatusChanged();
    }

    /** TCP 消息到达时更新发送方 lastSeen（阻止超时离线） */
    private void touchDevice(String deviceName, String ipAddress) {
        for (LanDevice d : deviceList) {
            if (d.getIpAddress().equals(ipAddress)) {
                d.setLastSeen(System.currentTimeMillis());
                if (!d.isOnline()) {
                    d.setOnline(true);
                    Log.d(TAG, "Device back online via TCP: " + d);
                    notifyStatusChanged();
                }
                return;
            }
        }
        // 未知设备发来消息：自动添加到列表
        LanDevice newDevice = new LanDevice(deviceName, ipAddress, SERVER_PORT);
        newDevice.setOnline(true);
        newDevice.setLastSeen(System.currentTimeMillis());
        deviceList.add(newDevice);
        LanPreferences.addSavedDevice(appContext, newDevice);
        notifyStatusChanged();
        Log.d(TAG, "Auto-added device from TCP message: " + newDevice);
    }

    /** 移除设备（从列表 + 持久化中删除） */
    public void removeDevice(LanDevice device) {
        deviceList.remove(device);
        LanPreferences.removeSavedDevice(appContext, device);
        notifyStatusChanged();
        Log.d(TAG, "Device removed: " + device);
    }

    // === 设备发现 ===

    /** 重新扫描设备 */
    public void rescan() {
        if (discoveryService != null) {
            discoveryService.rescan();
        }
    }

    // === 消息发送 ===

    /**
     * 向所有已知设备广播文字消息。
     * 此方法用于转发系统通知到局域网设备，不再额外保存日志
     * （转发源通知已由 MyNotificationListenerService 记录）。
     */
    public void broadcastText(String content) {
        if (!serverRunning || deviceList.isEmpty()) return;

        // 仅接收模式：不发送广播
        LanMode mode = LanPreferences.getLanMode(appContext);
        if (!mode.canSend()) {
            Log.d(TAG, "Receive-only mode: skipping broadcast");
            return;
        }

        // 通知 UI 监听器（不持久化——转发通知已有原始日志）
        LanMessage sentMsg = LanMessage.createText(deviceName, content,
                LanMessage.Direction.SENT);
        for (LanStatusListener l : listeners) {
            l.onTextReceived(sentMsg);
        }

        // 发送到所有在线设备
        String secret = LanPreferences.getConnectionSecret(appContext);
        for (LanDevice device : deviceList) {
            if (!device.isOnline()) continue; // 跳过离线设备
            sendExecutor.execute(() -> {
                MessageClient.sendText(deviceName,
                        device.getIpAddress(), device.getPort(), content, secret);
            });
        }
    }

    /**
     * 向指定设备发送文字（手动发送）。
     * 手动发送的消息会保存到日志文件。
     */
    public boolean sendTextTo(String ip, int port, String content) {
        LanMode mode = LanPreferences.getLanMode(appContext);
        if (!mode.canSend()) {
            Log.w(TAG, "Receive-only mode: refusing manual send");
            return false;
        }
        String secret = LanPreferences.getConnectionSecret(appContext);
        boolean result = MessageClient.sendText(deviceName, ip, port, content, secret);

        // 手动发送的消息需要保存到日志文件
        if (result) {
            String targetName = null;
            for (LanDevice d : deviceList) {
                if (d.getIpAddress().equals(ip)) {
                    targetName = d.getDeviceName();
                    break;
                }
            }
            if (targetName == null) {
                targetName = ip;
            }
            LanMessage sentMsg = LanMessage.createText(targetName, content,
                    LanMessage.Direction.SENT);
            appendToLanLog(sentMsg);
        }

        return result;
    }

    /** 向指定设备发送文件 */
    public boolean sendFileTo(String ip, int port, File file, LanMessage.Type type) {
        LanMode mode = LanPreferences.getLanMode(appContext);
        if (!mode.canSend()) {
            Log.w(TAG, "Receive-only mode: refusing file send");
            return false;
        }
        String secret = LanPreferences.getConnectionSecret(appContext);
        return MessageClient.sendFile(deviceName, ip, port, file, type, secret);
    }

    // === 监听器 ===

    public void addListener(LanStatusListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(LanStatusListener listener) {
        listeners.remove(listener);
    }

    private void notifyStatusChanged() {
        for (LanStatusListener l : listeners) {
            l.onStatusChanged(serverRunning, deviceList.size());
        }
    }
}
