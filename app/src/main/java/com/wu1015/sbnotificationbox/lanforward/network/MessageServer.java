package com.wu1015.sbnotificationbox.lanforward.network;

import android.util.Log;

import com.wu1015.sbnotificationbox.lanforward.model.LanMessage;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TCP 消息服务器。监听端口，接收文字/图片/文件消息。
 */
public class MessageServer {

    private static final String TAG = "MessageServer";

    private final int port;
    private final String saveDir;          // 文件保存目录
    private final MessageListener listener;

    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public interface MessageListener {
        /** 收到文字消息 */
        void onTextMessage(LanMessage message);
        /** 收到图片/文件消息（等待用户确认接收） */
        LanMessage onFileMessageRequest(LanMessage message);
        /** 文件接收完成 */
        void onFileReceived(LanMessage message);
    }

    public MessageServer(int port, String saveDir, MessageListener listener) {
        this.port = port;
        this.saveDir = saveDir;
        this.listener = listener;
    }

    public void start() {
        if (running.getAndSet(true)) return;

        // 每次启动重建线程池（上次 stop() 已 shutdown）
        threadPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "MessageServer");
            t.setDaemon(true);
            return t;
        });

        ensureSaveDir();
        threadPool.execute(() -> {
            while (running.get()) {
                try {
                    serverSocket = new ServerSocket(port);
                    Log.i(TAG, "Server listening on port " + port);

                    while (running.get()) {
                        try {
                            Socket client = serverSocket.accept();
                            threadPool.execute(() -> handleClient(client));
                        } catch (IOException e) {
                            if (running.get()) {
                                Log.e(TAG, "Accept error, restarting listener", e);
                                break; // 跳出内层循环，重建 ServerSocket
                            }
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Server bind error", e);
                    if (running.get()) {
                        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                    }
                }
            }
        });
    }

    public void stop() {
        running.set(false);
        // 先关闭 accept socket，让 accept() 抛出异常退出
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
        // 使用 shutdown() 而非 shutdownNow()，让正在传输的客户端正常完成
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                threadPool.shutdownNow(); // 超时则强制中断
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        Log.i(TAG, "Server stopped");
    }

    public int getPort() { return port; }

    // === 客户端处理 ===

    private void handleClient(Socket client) {
        try {
            client.setSoTimeout(30000); // 30秒超时
            InputStream in = client.getInputStream();
            OutputStream out = client.getOutputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));

            // 读取 JSON 头
            String headerLine = reader.readLine();
            if (headerLine == null) {
                client.close();
                return;
            }

            JSONObject header = new JSONObject(headerLine);
            String type = header.optString("type", "");
            String deviceName = header.optString("deviceName", "Unknown");

            Log.d(TAG, "Received: type=" + type + " from " + deviceName);

            if ("text".equals(type)) {
                handleTextMessage(deviceName, header, client);
            } else if ("image".equals(type) || "file".equals(type)) {
                handleFileMessage(deviceName, header, type, in, out, client);
            } else {
                Log.w(TAG, "Unknown message type: " + type);
            }

            client.close();
        } catch (Exception e) {
            Log.e(TAG, "Client handler error", e);
        } finally {
            try { client.close(); } catch (IOException ignored) {}
        }
    }

    private void handleTextMessage(String deviceName, JSONObject header, Socket client) {
        String content = header.optString("content", "");
        long ts = header.optLong("timestamp", System.currentTimeMillis());

        LanMessage msg = LanMessage.createText(deviceName, content, LanMessage.Direction.RECEIVED);
        msg.setTimestamp(ts);

        if (listener != null) {
            listener.onTextMessage(msg);
        }

        // 发送 ACK 响应，让客户端知道发送成功
        try {
            OutputStream out = client.getOutputStream();
            JSONObject ack = new JSONObject();
            ack.put("status", "ok");
            out.write((ack.toString() + "\n").getBytes("UTF-8"));
            out.flush();
        } catch (Exception e) {
            Log.w(TAG, "Failed to send text ACK", e);
        }
    }

    private void handleFileMessage(String deviceName, JSONObject header, String type,
                                   InputStream in, OutputStream out,
                                   Socket client) throws IOException {
        String fileName = header.optString("fileName", "unknown");
        long fileSize = header.optLong("fileSize", 0);
        long ts = header.optLong("timestamp", System.currentTimeMillis());

        LanMessage.Type msgType = "image".equals(type) ? LanMessage.Type.IMAGE : LanMessage.Type.FILE;
        LanMessage msg = LanMessage.createFile(deviceName, fileName, fileSize,
                msgType, LanMessage.Direction.RECEIVED);
        msg.setTimestamp(ts);

        // 通知 UI 层，等待用户确认
        if (listener != null) {
            LanMessage confirmed = listener.onFileMessageRequest(msg);
            if (confirmed != null && confirmed.isFileReceived()) {
                // 用户确认接收：读取文件数据
                File saveFile = new File(saveDir, fileName);
                // 避免文件名冲突
                saveFile = ensureUniqueFile(saveFile);

                byte[] buffer = new byte[8192];
                long remaining = fileSize;
                FileOutputStream fos = new FileOutputStream(saveFile);

                try {
                    while (remaining > 0) {
                        int toRead = (int) Math.min(buffer.length, remaining);
                        int read = in.read(buffer, 0, toRead);
                        if (read < 0) break;
                        fos.write(buffer, 0, read);
                        remaining -= read;
                    }
                } finally {
                    fos.close();
                }

                msg.setFilePath(saveFile.getAbsolutePath());
                msg.setFileReceived(true);
                Log.i(TAG, "File saved: " + saveFile.getAbsolutePath());

                // 发送 ACK
                JSONObject ack = new JSONObject();
                try { ack.put("status", "ok"); } catch (Exception ignored) {}
                out.write((ack.toString() + "\n").getBytes("UTF-8"));
                out.flush();

                if (listener != null) {
                    listener.onFileReceived(msg);
                }
            } else {
                // 用户拒绝
                JSONObject ack = new JSONObject();
                try { ack.put("status", "reject"); } catch (Exception ignored) {}
                out.write((ack.toString() + "\n").getBytes("UTF-8"));
                out.flush();
                Log.d(TAG, "File rejected by user");
            }
        }
    }

    private void ensureSaveDir() {
        File dir = new File(saveDir);
        if (!dir.exists()) dir.mkdirs();
    }

    private File ensureUniqueFile(File file) {
        if (!file.exists()) return file;
        String name = file.getName();
        String base;
        String ext;
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        } else {
            base = name;
            ext = "";
        }
        int i = 1;
        File parent = file.getParentFile();
        File candidate;
        do {
            candidate = new File(parent, base + "_(" + i + ")" + ext);
            i++;
        } while (candidate.exists());
        return candidate;
    }
}
