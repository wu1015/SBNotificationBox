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
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * UDP 组播设备发现服务。
 * 同时发送广播和监听其他设备的广播。
 */
public class DiscoveryService {

    private static final String TAG = "DiscoveryService";
    private static final String MULTICAST_ADDR = "230.0.0.1";
    private static final int DISCOVERY_PORT = 9876;
    private static final int TIMEOUT_MS = 3000;

    private final String deviceName;
    private final int serverPort;
    private final Context context;
    private final DiscoveryListener listener;

    private MulticastSocket multicastSocket;
    private DatagramSocket broadcastSocket;
    private WifiManager.MulticastLock multicastLock;
    private volatile boolean running = false;
    private Thread listenThread;
    private Thread broadcastThread;

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

        // 获取 MulticastLock 以保证 WiFi 组播正常工作
        WifiManager wifi = (WifiManager) this.context.getSystemService(Context.WIFI_SERVICE);
        if (wifi != null) {
            multicastLock = wifi.createMulticastLock("SBNotificationBox-Discovery");
        }
    }

    /** 开始发现（发送广播 + 持续监听） */
    public void start() {
        if (running) return;
        running = true;

        // 发送一次广播
        sendBroadcast();

        // 持续监听回复
        listenThread = new Thread(this::listenLoop, "DiscoveryListen");
        listenThread.start();
    }

    /** 发送广播，主动搜索设备 */
    public List<LanDevice> discoverOnce() {
        List<LanDevice> found = new ArrayList<>();
        DatagramSocket sock = null;
        MulticastSocket recvSock = null;

        acquireLock();
        try {
            // 发送广播
            sock = new DatagramSocket();
            sock.setBroadcast(true);
            JSONObject json = buildDiscoveryMessage();
            byte[] data = json.toString().getBytes("UTF-8");
            DatagramPacket packet = new DatagramPacket(data, data.length,
                    InetAddress.getByName(MULTICAST_ADDR), DISCOVERY_PORT);
            sock.send(packet);
            Log.d(TAG, "Broadcast sent");

            // 接收回复（超时 3 秒）
            recvSock = new MulticastSocket(DISCOVERY_PORT);
            recvSock.joinGroup(InetAddress.getByName(MULTICAST_ADDR));
            recvSock.setSoTimeout(TIMEOUT_MS);

            byte[] buf = new byte[1024];
            long deadline = System.currentTimeMillis() + TIMEOUT_MS;

            while (System.currentTimeMillis() < deadline) {
                try {
                    DatagramPacket recv = new DatagramPacket(buf, buf.length);
                    recvSock.receive(recv);
                    LanDevice device = parseDiscoveryResponse(recv);
                    if (device != null && !found.contains(device)) {
                        found.add(device);
                        Log.d(TAG, "Found: " + device);
                    }
                } catch (SocketTimeoutException e) {
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Discovery error", e);
            if (listener != null) {
                listener.onDiscoveryError(e.getMessage());
            }
        } finally {
            closeQuietly(sock, recvSock);
            releaseLock();
        }
        return found;
    }

    public void stop() {
        releaseLock();
        running = false;
        if (listenThread != null) listenThread.interrupt();
        if (broadcastThread != null) broadcastThread.interrupt();
        closeQuietly(multicastSocket, broadcastSocket);
        multicastSocket = null;
        broadcastSocket = null;
    }

    // === 内部方法 ===

    private void sendBroadcast() {
        broadcastThread = new Thread(() -> {
            DatagramSocket sock = null;
            acquireLock();
            try {
                sock = new DatagramSocket();
                sock.setBroadcast(true);
                JSONObject json = buildDiscoveryMessage();
                byte[] data = json.toString().getBytes("UTF-8");
                DatagramPacket packet = new DatagramPacket(data, data.length,
                        InetAddress.getByName(MULTICAST_ADDR), DISCOVERY_PORT);
                sock.send(packet);
                Log.d(TAG, "Discovery broadcast sent from " + deviceName);
            } catch (Exception e) {
                Log.e(TAG, "Broadcast error", e);
            } finally {
                closeQuietly(sock);
                releaseLock();
            }
        }, "DiscoveryBroadcast");
        broadcastThread.start();
    }

    private void listenLoop() {
        acquireLock();
        try {
            multicastSocket = new MulticastSocket(DISCOVERY_PORT);
            multicastSocket.joinGroup(InetAddress.getByName(MULTICAST_ADDR));
            multicastSocket.setSoTimeout(5000);

            byte[] buf = new byte[1024];
            while (running) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    multicastSocket.receive(packet);
                    LanDevice device = parseDiscoveryResponse(packet);
                    if (device != null && listener != null) {
                        listener.onDeviceFound(device);
                    }
                } catch (SocketTimeoutException e) {
                    // 超时继续循环
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Listen error", e);
        } finally {
            closeQuietly(multicastSocket);
            releaseLock(); // 确保在退出循环时释放锁
        }
    }

    private JSONObject buildDiscoveryMessage() throws Exception {
        JSONObject json = new JSONObject();
        json.put("type", "discovery");
        json.put("deviceName", deviceName);
        json.put("port", serverPort);
        return json;
    }

    private LanDevice parseDiscoveryResponse(DatagramPacket packet) {
        try {
            String json = new String(packet.getData(), 0, packet.getLength(), "UTF-8");
            JSONObject obj = new JSONObject(json);
            if ("discovery".equals(obj.optString("type"))) {
                String name = obj.optString("deviceName", "Unknown");
                int port = obj.optInt("port", 9877);
                String ip = packet.getAddress().getHostAddress();
                // 排除自己（用 IP 而非设备名，避免同名设备互相排斥）
                if (isLocalAddress(ip)) return null;
                return new LanDevice(name, ip, port);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse discovery response", e);
        }
        return null;
    }

    /** 检查 IP 是否是本机地址 */
    private static boolean isLocalAddress(String ip) {
        try {
            for (NetworkInterface ni : Collections.list(
                    NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr.getHostAddress().equals(ip)) return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private void acquireLock() {
        if (multicastLock != null && !multicastLock.isHeld()) {
            try { multicastLock.acquire(); } catch (Exception ignored) {}
        }
    }

    private void releaseLock() {
        if (multicastLock != null && multicastLock.isHeld()) {
            try { multicastLock.release(); } catch (Exception ignored) {}
        }
    }

    private void closeQuietly(java.io.Closeable... closeables) {
        for (java.io.Closeable c : closeables) {
            if (c != null) {
                try { c.close(); } catch (IOException ignored) {}
            }
        }
    }
}
