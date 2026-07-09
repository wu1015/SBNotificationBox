package com.wu1015.sbnotificationbox.lanforward.network;

import android.content.Context;
import android.util.Log;

import com.wu1015.sbnotificationbox.lanforward.model.LanDevice;
import com.wu1015.sbnotificationbox.lanforward.model.LanMessage;
import com.wu1015.sbnotificationbox.lanforward.storage.LanPreferences;
import com.wu1015.sbnotificationbox.notification.MyNotification;
import com.wu1015.sbnotificationbox.notification.NotificationWidgetProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
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
                // 通知所有监听器
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
