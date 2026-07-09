package com.wu1015.sbnotificationbox.lanforward.network;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.wu1015.sbnotificationbox.lanforward.model.LanDevice;

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
 */
public class DiscoveryService {

    private static final String TAG = "DiscoveryService";
    private static final String BROADCAST_ADDR = "255.255.255.255";
    static final int DISCOVERY_PORT = 9876;
    private static final int RECEIVE_TIMEOUT_MS = 4000;
    private static final int LISTEN_TIMEOUT_MS = 5000;

    private final String deviceName;
    private final int serverPort;
    private final Context context;
    private final DiscoveryListener listener;

    private DatagramSocket listenSocket;
    private volatile boolean running = false;
    private Thread listenThread;

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

    /** 开始持续监听（后台线程） */
    public void start() {
        if (running) return;
        running = true;
        listenThread = new Thread(this::listenLoop, "DiscoveryListen");
        listenThread.setDaemon(true);
        listenThread.start();
        Log.i(TAG, "Discovery listener started (WiFi IP: " + localWifiIp + ")");
    }

    /** 重新发送广播 */
    public void rescan() {
        if (!running) start();
        sendBroadcast();
    }

    /** 单次广播并等待回复（阻塞，最多 4 秒） */
    public List<LanDevice> discoverOnce() {
        List<LanDevice> found = new ArrayList<>();

        // 临时监听 socket
        DatagramSocket tempSocket = null;
        try {
            tempSocket = createBoundSocket(DISCOVERY_PORT);
            tempSocket.setSoTimeout(RECEIVE_TIMEOUT_MS);
        } catch (Exception e) {
            Log.e(TAG, "Cannot create temp socket", e);
            return found;
        }

        // 发送广播
        sendBroadcastTo(null); // 使用临时 socket 发送

        // 接收回复
        byte[] buf = new byte[1024];
        long deadline = System.currentTimeMillis() + RECEIVE_TIMEOUT_MS;

        DatagramSocket recvSocket = tempSocket;
        try {
            while (System.currentTimeMillis() < deadline) {
                try {
                    DatagramPacket recv = new DatagramPacket(buf, buf.length);
                    recvSocket.receive(recv);
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

    private void listenLoop() {
        try {
            listenSocket = createBoundSocket(DISCOVERY_PORT);
            listenSocket.setSoTimeout(LISTEN_TIMEOUT_MS);
            Log.i(TAG, "Listen socket bound to port " + DISCOVERY_PORT);

            byte[] buf = new byte[1024];
            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    listenSocket.receive(packet);
                    LanDevice device = parseResponse(packet);
                    if (device != null && listener != null) {
                        listener.onDeviceFound(device);
                    }
                } catch (SocketTimeoutException e) {
                    // 超时，继续循环检查 running
                } catch (IOException e) {
                    if (running) Log.w(TAG, "Receive error in loop", e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Listen loop error", e);
            if (listener != null) listener.onDiscoveryError(e.getMessage());
        } finally {
            closeQuietly(listenSocket);
            Log.i(TAG, "Listen loop exited");
        }
    }

    private LanDevice parseResponse(DatagramPacket packet) {
        try {
            String json = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
            JSONObject obj = new JSONObject(json);
            if ("discovery".equals(obj.optString("type"))) {
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

    /** 创建用于发送的 DatagramSocket（绑定 WiFi 接口） */
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
            sock = new DatagramSocket(port);
            sock.setReuseAddress(true);
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
