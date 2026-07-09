package com.wu1015.sbnotificationbox.lanforward.network;

import android.util.Log;

import com.wu1015.sbnotificationbox.lanforward.model.LanMessage;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * TCP 消息客户端。连接到目标设备发送文字/图片/文件。
 */
public class MessageClient {

    private static final String TAG = "MessageClient";
    private static final int CONNECT_TIMEOUT = 10000;

    /**
     * 发送文字消息
     */
    public static boolean sendText(String deviceName, String ip, int port,
                                   String content) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", "text");
            json.put("deviceName", deviceName);
            json.put("content", content);
            json.put("timestamp", System.currentTimeMillis());

            return sendJson(ip, port, json) != null;
        } catch (Exception e) {
            Log.e(TAG, "Send text error", e);
            return false;
        }
    }

    /**
     * 发送文件（图片+普通文件）
     */
    public static boolean sendFile(String deviceName, String ip, int port,
                                   File file, LanMessage.Type fileType) {
        Socket socket = null;
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(ip, port), CONNECT_TIMEOUT);
            OutputStream out = socket.getOutputStream();

            // 1. 发送 JSON 头
            JSONObject header = new JSONObject();
            header.put("type", fileType == LanMessage.Type.IMAGE ? "image" : "file");
            header.put("deviceName", deviceName);
            header.put("fileName", file.getName());
            header.put("fileSize", file.length());
            header.put("timestamp", System.currentTimeMillis());

            out.write((header.toString() + "\n").getBytes("UTF-8"));
            out.flush();

            // 2. 发送文件二进制数据
            FileInputStream fis = new FileInputStream(file);
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            fis.close();
            out.flush();

            // 3. 读取 ACK
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"));
            String ackLine = reader.readLine();
            if (ackLine != null) {
                JSONObject ack = new JSONObject(ackLine);
                String status = ack.optString("status", "");
                Log.d(TAG, "File send ACK: " + status);
                return "ok".equals(status);
            }

        } catch (Exception e) {
            Log.e(TAG, "Send file error", e);
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (Exception ignored) {}
            }
        }
        return false;
    }

    /**
     * 发送 JSON 并读取响应
     */
    private static String sendJson(String ip, int port, JSONObject json) throws Exception {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(ip, port), CONNECT_TIMEOUT);

        OutputStream out = socket.getOutputStream();
        out.write((json.toString() + "\n").getBytes("UTF-8"));
        out.flush();

        // 读取响应（可选）
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), "UTF-8"));
        String response = reader.readLine();
        socket.close();
        return response;
    }
}
