package com.wu1015.sbnotificationbox.lanforward.network;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.wu1015.sbnotificationbox.lanforward.model.LanDevice;
import com.wu1015.sbnotificationbox.lanforward.storage.LanPreferences;

import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * UDP 广播设备发现服务。
 * 使用 UDP 广播（255.255.255.255）替代组播，兼容性更好。
 * 支持持续监听、心跳保活、双向发现。
 */
public class DiscoveryService {

    private static final String TAG = "DiscoveryService";
    private static final String BROADCAST_ADDR = "255.255.255.255";
    static final int DISCOVERY_PORT = 9876;
    private static final int RECEIVE_TIMEOUT_MS = 4000;
    private static final int LISTEN_TIMEOUT_MS = 5000;
    private static final int HEARTBEAT_INTERVAL_MS = 10000; // 10 秒心跳

    private final String deviceName;
    private final int serverPort;
    private final Context context;
    private final DiscoveryListener listener;

    private volatile DatagramSocket listenSocket;
    private volatile boolean running = false;
    private Thread listenThread;
    private Thread heartbeatThread;
    private int consecutiveListenErrors = 0;
    private static final int MAX_LISTEN_ERRORS_BEFORE_RESTART = 10;

    // 本机 WiFi IP（null = 未找到）
    private String localWifiIp;

    public interface DiscoveryListener {
        void onDeviceFound(LanDevice device);
        void onDiscoveryError(String error);
    }

    public DiscoveryService(Context context, String deviceName, int serverPort,
                            DiscoveryListener listener) {
        this.context = context.getApplicationContext();
        this.deviceName = deviceName;
        this.serverPort = serverPort;
        this.listener = listener;
        this.localWifiIp = getWifiIpAddress();
    }

    /** 开始持续监听（后台线程）+ 心跳广播 */
    public void start() {
        if (running) return;
        running = true;

        // 启动监听线程
        listenThread = new Thread(this::listenLoop, "DiscoveryListen");
        listenThread.setDaemon(true);
        listenThread.start();

        // 启动心跳线程（定期广播自身存在）
        heartbeatThread = new Thread(() -> {
            while (running) {
                sendBroadcast();
                try {
                    Thread.sleep(HEARTBEAT_INTERVAL_MS);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "DiscoveryHeartbeat");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();

        // 初始广播放在后台线程执行（避免 NetworkOnMainThreadException）
        new Thread(this::sendBroadcast, "DiscoveryInitBroadcast").start();

        Log.i(TAG, "Discovery listener started (WiFi IP: " + localWifiIp + ")");
    }

    /** 重新发送广播（线程安全，可在主线程调用） */
    public void rescan() {
        if (!running) {
            // start() 会启动后台线程发广播
            start();
        } else {
            // 在后台线程发广播
            new Thread(this::sendBroadcast, "DiscoveryRescan").start();
        }
    }

    /** 单次广播并等待回复（阻塞，最多 4 秒）。
     *  使用临时 socket 绑定到临时端口，不与 listenSocket 冲突。 */
    public List<LanDevice> discoverOnce() {
        List<LanDevice> found = new ArrayList<>();
        DatagramSocket tempSocket = null;

        try {
            // 使用临时端口，不与 listenLoop 的端口 9876 冲突
            tempSocket = createSendSocket();
            tempSocket.setSoTimeout(RECEIVE_TIMEOUT_MS);
        } catch (Exception e) {
            Log.e(TAG, "Cannot create temp socket", e);
            return found;
        }

        // 通过临时 socket 发送广播
        sendBroadcastTo(tempSocket);

        // 接收回复（远程设备会回复到临时 socket 的源端口）
        byte[] buf = new byte[1024];
        long deadline = System.currentTimeMillis() + RECEIVE_TIMEOUT_MS;

        try {
            while (System.currentTimeMillis() < deadline) {
                try {
                    DatagramPacket recv = new DatagramPacket(buf, buf.length);
                    tempSocket.receive(recv);
                    LanDevice device = parseResponse(recv);
                    if (device != null && !found.contains(device)) {
                        found.add(device);
                        Log.d(TAG, "Found: " + device);
                    }
                } catch (SocketTimeoutException e) {
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Receive error", e);
        } finally {
            closeQuietly(tempSocket);
        }
        return found;
    }

    public void stop() {
        running = false;
        if (listenThread != null) listenThread.interrupt();
        if (heartbeatThread != null) heartbeatThread.interrupt();
        closeQuietly(listenSocket);
        listenSocket = null;
        Log.i(TAG, "Discovery stopped");
    }

    // === 内部方法 ===

    private void sendBroadcast() {
        DatagramSocket sock = null;
        try {
            sock = createSendSocket();
            sendBroadcastTo(sock);
            Log.d(TAG, "Broadcast sent from " + deviceName);
        } catch (Exception e) {
            Log.e(TAG, "Broadcast error", e);
        } finally {
            closeQuietly(sock);
        }
    }

    /** 通过指定 socket 发送广播（如果 sock 为 null 则创建新的） */
    private void sendBroadcastTo(DatagramSocket sock) {
        DatagramSocket s = sock;
        boolean created = false;
        try {
            if (s == null) {
                s = createSendSocket();
                created = true;
            }
            JSONObject json = new JSONObject();
            json.put("type", "discovery");
            json.put("deviceName", deviceName);
            json.put("port", serverPort);
            String secret = LanPreferences.getConnectionSecret(context);
            if (!secret.isEmpty()) {
                json.put("secret", secret);
            }
            byte[] data = json.toString().getBytes("UTF-8");

            DatagramPacket packet = new DatagramPacket(data, data.length,
                    InetAddress.getByName(BROADCAST_ADDR), DISCOVERY_PORT);
            s.send(packet);
        } catch (Exception e) {
            Log.e(TAG, "Send broadcast error", e);
        } finally {
            if (created) closeQuietly(s);
        }
    }

    /** 监听循环：接收发现包并回复发送方，实现双向发现 */
    private void listenLoop() {
        int backoffMs = 0;
        while (running) {
            try {
                listenSocket = createBoundSocket(DISCOVERY_PORT);
                listenSocket.setSoTimeout(LISTEN_TIMEOUT_MS);
                Log.i(TAG, "Listen socket bound to port " + DISCOVERY_PORT);

                consecutiveListenErrors = 0;
                backoffMs = 0; // 连接成功后重置退避

                byte[] buf = new byte[1024];
                while (running) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        listenSocket.receive(packet);
                        LanDevice device = parseResponse(packet);
                        if (device != null) {
                            if (listener != null) {
                                listener.onDeviceFound(device);
                            }

                            // 回复发送方，告知本设备信息（双向发现的关键）
                            try {
                                JSONObject resp = new JSONObject();
                                resp.put("type", "discovery");
                                resp.put("deviceName", deviceName);
                                resp.put("port", serverPort);
                                String secret = LanPreferences.getConnectionSecret(context);
                                if (!secret.isEmpty()) {
                                    resp.put("secret", secret);
                                }
                                byte[] respData = resp.toString().getBytes("UTF-8");
                                DatagramPacket response = new DatagramPacket(
                                        respData, respData.length,
                                        packet.getAddress(),    // 发送方 IP
                                        DISCOVERY_PORT);        // 发到发现端口
                                listenSocket.send(response);
                            } catch (Exception e) {
                                Log.w(TAG, "Failed to send discovery response", e);
                            }
                        }
                    } catch (SocketTimeoutException e) {
                        // 超时，继续循环检查 running
                    } catch (IOException e) {
                        if (running) {
                            consecutiveListenErrors++;
                            Log.w(TAG, "Receive error in loop ("
                                    + consecutiveListenErrors + "/"
                                    + MAX_LISTEN_ERRORS_BEFORE_RESTART + ")", e);
                            // 连续错误过多，跳出重建 listen socket
                            if (consecutiveListenErrors >= MAX_LISTEN_ERRORS_BEFORE_RESTART) {
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Listen loop error", e);
                if (listener != null) listener.onDiscoveryError(e.getMessage());
            } finally {
                closeQuietly(listenSocket);
                listenSocket = null;
                Log.i(TAG, "Listen loop exited");
            }

            // 指数退避重连
            if (running) {
                // 重新获取本机 WiFi IP（网络可能已切换）
                this.localWifiIp = getWifiIpAddress();
                Log.i(TAG, "Current WiFi IP: " + localWifiIp);

                backoffMs = Math.min(backoffMs == 0 ? 1000 : backoffMs * 2, 30000);
                Log.i(TAG, "Reconnecting listen socket in " + backoffMs + "ms...");
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    private LanDevice parseResponse(DatagramPacket packet) {
        try {
            String json = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
            JSONObject obj = new JSONObject(json);
            if ("discovery".equals(obj.optString("type"))) {
                // 验证密钥（如果本地设置了密钥）
                String localSecret = LanPreferences.getConnectionSecret(context);
                if (!localSecret.isEmpty()) {
                    String remoteSecret = obj.optString("secret", "");
                    if (!localSecret.equals(remoteSecret)) {
                        Log.w(TAG, "Secret mismatch from " + packet.getAddress().getHostAddress());
                        return null; // 密钥不匹配，忽略该设备
                    }
                }
                String name = obj.optString("deviceName", "Unknown");
                int port = obj.optInt("port", 9877);
                String ip = packet.getAddress().getHostAddress();
                // 排除本机
                if (ip.equals(localWifiIp) || ip.equals("127.0.0.1")) return null;
                return new LanDevice(name, ip, port);
            }
        } catch (Exception e) {
            Log.w(TAG, "Parse error", e);
        }
        return null;
    }

    /** 创建用于发送的 DatagramSocket（绑定临时端口） */
    private DatagramSocket createSendSocket() throws IOException {
        DatagramSocket sock;
        if (localWifiIp != null) {
            sock = new DatagramSocket(null);
            sock.setReuseAddress(true);
            sock.bind(new InetSocketAddress(localWifiIp, 0));
        } else {
            sock = new DatagramSocket();
        }
        sock.setBroadcast(true);
        return sock;
    }

    /** 创建绑定到指定端口的 DatagramSocket（用于接收） */
    private DatagramSocket createBoundSocket(int port) throws IOException {
        DatagramSocket sock;
        if (localWifiIp != null) {
            sock = new DatagramSocket(null);
            sock.setReuseAddress(true);
            sock.bind(new InetSocketAddress(localWifiIp, port));
        } else {
            sock = new DatagramSocket(null);
            sock.setReuseAddress(true);
            sock.bind(new InetSocketAddress(port));
        }
        return sock;
    }

    /** 获取本机 WiFi IP 地址 */
    private String getWifiIpAddress() {
        // 方法1：通过 WifiManager
        try {
            WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                int ipInt = wifi.getConnectionInfo().getIpAddress();
                if (ipInt != 0) {
                    return String.format("%d.%d.%d.%d",
                            (ipInt & 0xff),
                            (ipInt >> 8 & 0xff),
                            (ipInt >> 16 & 0xff),
                            (ipInt >> 24 & 0xff));
                }
            }
        } catch (Exception ignored) {}

        // 方法2：遍历网络接口
        try {
            for (NetworkInterface ni : Collections.list(
                    NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isLoopback() && ni.isUp()) {
                    for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                        if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                            String ip = addr.getHostAddress();
                            Log.d(TAG, "Found IP: " + ip + " on " + ni.getDisplayName());
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        Log.w(TAG, "No WiFi IP found");
        return null;
    }

    private void closeQuietly(java.io.Closeable... closeables) {
        for (java.io.Closeable c : closeables) {
            if (c != null) {
                try { c.close(); } catch (IOException ignored) {}
            }
        }
    }
}
