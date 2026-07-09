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
import com.wu1015.sbnotificationbox.notification.MyNotification;
import com.wu1015.sbnotificationbox.notification.NotificationWidgetProvider;

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

/**
 * 局域网管理器（单例）。
 * 持久化 TCP 服务器，跟踪已连接设备，提供广播接口。
 */
public class LanManager {

    private static final String TAG = "LanManager";
    private static final int SERVER_PORT = 9877;
    private static volatile LanManager instance;

    private final Context appContext;
    private MessageServer messageServer;
    private DiscoveryService discoveryService;
    private final List<LanDevice> deviceList = new CopyOnWriteArrayList<>();
    private final ExecutorService sendExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "LanSend");
        t.setDaemon(true);
        return t;
    });

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

        // 刷新设备名（用户可能在设置中改了）
        deviceName = LanPreferences.getDeviceName(appContext);

        String saveDir = LanPreferences.getStorageDir(appContext);
        new File(saveDir).mkdirs();

        // 启动 TCP 服务器
        messageServer = new MessageServer(SERVER_PORT, saveDir, new MessageServer.MessageListener() {
            @Override
            public void onTextMessage(LanMessage message) {
                Log.d(TAG, "Text from " + message.getDeviceName() + ": " + message.getContent());
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
                for (LanStatusListener l : listeners) {
                    l.onTextReceived(message);
                }
            }
        });
        messageServer.start();

        // 启动持续设备发现
        discoveryService = new DiscoveryService(appContext, deviceName, SERVER_PORT,
                new DiscoveryService.DiscoveryListener() {
                    @Override
                    public void onDeviceFound(LanDevice device) {
                        if (!deviceList.contains(device)) {
                            deviceList.add(device);
                            Log.d(TAG, "Device found: " + device);
                            notifyStatusChanged();
                        }
                    }

                    @Override
                    public void onDiscoveryError(String error) {
                        Log.w(TAG, "Discovery error: " + error);
                    }
                });
        discoveryService.start();

        serverRunning = true;
        notifyStatusChanged();
        Log.i(TAG, "LanManager started");
    }

    public void stop() {
        if (!serverRunning) return;
        if (messageServer != null) messageServer.stop();
        if (discoveryService != null) discoveryService.stop();
        sendExecutor.shutdown();
        deviceList.clear();
        serverRunning = false;
        notifyStatusChanged();
        Log.i(TAG, "LanManager stopped");
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
            String fileName = date + "_lan_log.md";

            FileOutputStream fos = appContext.openFileOutput(fileName, Context.MODE_APPEND);
            OutputStreamWriter writer = new OutputStreamWriter(fos, "UTF-8");

            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    .format(new Date(message.getTimestamp()));
            String entry = time + " LAN:" + message.getDeviceName() + "\n"
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

        for (LanDevice device : deviceList) {
            sendExecutor.execute(() -> {
                MessageClient.sendText(deviceName,
                        device.getIpAddress(), device.getPort(), content);
            });
        }
    }

    /** 向指定设备发送文字 */
    public boolean sendTextTo(String ip, int port, String content) {
        return MessageClient.sendText(deviceName, ip, port, content);
    }

    /** 向指定设备发送文件 */
    public boolean sendFileTo(String ip, int port, File file, LanMessage.Type type) {
        return MessageClient.sendFile(deviceName, ip, port, file, type);
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
