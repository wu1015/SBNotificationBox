package com.wu1015.sbnotificationbox.mailsend;

import android.util.Log;

import javax.mail.*;

/**
 * 持久化 SMTP 传输连接管理器。
 * 复用 Transport 连接以减少多次发送时的握手开销。
 */
public class PersistentTransport {
    private static Transport transport = null;
    private static Session boundSession = null;

    /**
     * 获取或创建持久化的 Transport 连接。
     * 仅在连接断开或 session 变更时重新连接。
     */
    public static synchronized Transport getTransport(Session session) throws MessagingException {
        if (transport == null || !transport.isConnected() || session != boundSession) {
            // 关闭旧连接
            closeQuietly();

            transport = session.getTransport("smtp");
            transport.connect();
            boundSession = session;
            Log.d("PersistentTransport", "New SMTP transport connected");
        }
        return transport;
    }

    /**
     * 使用持久化连接发送邮件（不关闭连接，供后续复用）
     */
    public static synchronized void sendEmail(Session session, Message message) throws MessagingException {
        Transport t = getTransport(session);
        t.sendMessage(message, message.getAllRecipients());
        Log.d("PersistentTransport", "Email sent successfully");
        // 不关闭 transport，保持连接供下次复用
    }

    /**
     * 发送邮件并在发送后关闭连接（一次性发送场景）
     */
    public static synchronized void sendEmailAndClose(Session session, Message message) throws MessagingException {
        Transport t = getTransport(session);
        try {
            t.sendMessage(message, message.getAllRecipients());
            Log.d("PersistentTransport", "Email sent (connection will close)");
        } finally {
            close();
        }
    }

    /**
     * 检查连接是否活跃
     */
    public static synchronized boolean isConnected() {
        return transport != null && transport.isConnected();
    }

    /**
     * 关闭持久化连接
     */
    public static synchronized void close() {
        closeQuietly();
        boundSession = null;
    }

    private static void closeQuietly() {
        if (transport != null) {
            try {
                if (transport.isConnected()) {
                    transport.close();
                    Log.d("PersistentTransport", "Transport closed");
                }
            } catch (MessagingException e) {
                Log.w("PersistentTransport", "Error closing transport", e);
            }
            transport = null;
        }
    }
}
