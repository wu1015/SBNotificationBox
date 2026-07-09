package com.wu1015.sbnotificationbox.lanforward.network;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.wu1015.sbnotificationbox.R;
import com.wu1015.sbnotificationbox.lanforward.model.LanDevice;
import com.wu1015.sbnotificationbox.lanforward.model.LanMessage;
import com.wu1015.sbnotificationbox.lanforward.storage.LanPreferences;
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

/**
 * 局域网管理器（单例）。
 * 持久化 TCP 服务器，跟踪已连接设备，提供广播接口。
 */
public class LanManager {

    private static final String TAG = "LanManager";
    private static final int SERVER_PORT = 9877;
    private static final long DEVICE_TIMEOUT_MS = 120000; // 120 秒无心跳 = 离线
    private static volatile LanManager instance;

    private final Context appContext;
    private MessageServer messageServer;
    private DiscoveryService discoveryService;
    private final List<LanDevice> deviceList = new CopyOnWriteArrayList<>();
    private ExecutorService sendExecutor; // 非 final，stop 后可重建
    private ScheduledExecutorService timeoutExecutor;

    private boolean serverRunning = false;
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

        // 刷新设备名（用户可能在设置中改了）
        deviceName = LanPreferences.getDeviceName(appContext);

        String saveDir = LanPreferences.getStorageDir(appContext);
        new File(saveDir).mkdirs();

        // 启动 TCP 服务器
        String secret = LanPreferences.getConnectionSecret(appContext);
        messageServer = new MessageServer(SERVER_PORT, saveDir, secret,
                new MessageServer.MessageListener() {
            @Override
            public void onTextMessage(LanMessage message) {
                Log.d(TAG, "Text from " + message.getDeviceName() + ": " + message.getContent());
                // 更新发送方设备的 lastSeen（TCP 消息也是心跳）
                if (message.getIpAddress() != null) {
                    touchDevice(message.getDeviceName(), message.getIpAddress());
                }
                // 发送系统通知
                sendLanNotification(message);
                // 持久化写入日志文件
                appendToLanLog(message);
                // 通知所有监听器（UI）
                for (LanStatusListener l : listeners) {
                    l.onTextReceived(message);
                }
            }

            @Override
            public LanMessage onFileMessageRequest(LanMessage message) {
                // 文件接收请求：此处不阻塞，自动接收
                message.setFileReceived(true);
                return message;
            }

            @Override
            public void onFileReceived(LanMessage message) {
                Log.i(TAG, "File received: " + message.getFileName());
                // 更新发送方设备的 lastSeen
                if (message.getIpAddress() != null) {
                    touchDevice(message.getDeviceName(), message.getIpAddress());
                }
                for (LanStatusListener l : listeners) {
                    l.onTextReceived(message);
                }
            }
        });
        messageServer.start();

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

        serverRunning = true;
        notifyStatusChanged();
        Log.i(TAG, "LanManager started");
    }

    public void stop() {
        if (!serverRunning) return;
        if (messageServer != null) messageServer.stop();
        if (discoveryService != null) discoveryService.stop();
        if (timeoutExecutor != null) timeoutExecutor.shutdown();
        if (sendExecutor != null) sendExecutor.shutdown();
        deviceList.clear();
        serverRunning = false;
        notifyStatusChanged();
        Log.i(TAG, "LanManager stopped");
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
        sendExecutor = Executors.newCachedThreadPool(r -> {
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

    /** 持久化写入日志文件 */
    private void appendToLanLog(LanMessage message) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.US);
            String date = sdf.format(new Date());
            String fileName = date + "_notifications_log.md";

            FileOutputStream fos = appContext.openFileOutput(fileName, Context.MODE_APPEND);
            OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8");

            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    .format(new Date(message.getTimestamp()));
            String direction = message.getDirection() == LanMessage.Direction.SENT
                    ? "SENT to " : "RECV from ";
            String entry = time + " " + direction + message.getDeviceName() + "\n"
                    + message.getContent() + "\n\n";
            writer.write(entry);
            writer.close();
            fos.close();
        } catch (IOException e) {
            Log.w(TAG, "Failed to write LAN log", e);
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

    /** 向所有已知设备广播文字消息 */
    public void broadcastText(String content) {
        if (!serverRunning || deviceList.isEmpty()) return;

        // 记录已发送的消息（持久化 + 通知 UI）
        LanMessage sentMsg = LanMessage.createText(deviceName, content,
                LanMessage.Direction.SENT);
        appendToLanLog(sentMsg);
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

    /** 向指定设备发送文字 */
    public boolean sendTextTo(String ip, int port, String content) {
        String secret = LanPreferences.getConnectionSecret(appContext);
        return MessageClient.sendText(deviceName, ip, port, content, secret);
    }

    /** 向指定设备发送文件 */
    public boolean sendFileTo(String ip, int port, File file, LanMessage.Type type) {
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
