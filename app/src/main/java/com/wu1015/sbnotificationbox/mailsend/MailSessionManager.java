package com.wu1015.sbnotificationbox.mailsend;

import android.util.Log;

import java.util.Properties;
import javax.mail.*;

public class MailSessionManager {
    private static Session session;
    private static String currentEmail;

    /**
     * 创建或获取邮件会话。
     * 根据端口自动选择 SSL（465）或 STARTTLS（587）。
     *
     * @param smtpHost SMTP 服务器地址
     * @param smtpPort SMTP 端口（465=SSL, 587=STARTTLS）
     * @param email    发件人邮箱
     * @param password 密码/授权码
     * @return 配置好的 Session 对象
     */
    public static Session getSession(String smtpHost, String smtpPort, String email, String password) {
        // 如果已存在会话且邮箱相同，直接复用
        if (session != null && email.equals(currentEmail)) {
            return session;
        }

        // 邮箱变更或首次创建：先关闭旧连接
        closeTransport();

        Properties props = new Properties();
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", smtpPort);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", "15000"); // 15秒连接超时
        props.put("mail.smtp.timeout", "15000");           // 15秒读写超时
        props.put("mail.smtp.writeTimeout", "15000");      // 15秒写超时

        // 根据端口选择加密方式
        if ("465".equals(smtpPort)) {
            // SSL 直连模式（QQ邮箱、网易邮箱等）
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.port", smtpPort);
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.socketFactory.fallback", "false");
        } else {
            // STARTTLS 模式（Gmail、Outlook 等，端口 587）
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }

        // 调试模式（发布时关闭）
        // props.put("mail.debug", "true");

        session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(email, password);
            }
        });

        currentEmail = email;
        Log.d("MailSessionManager", "Session created for: " + email + " via " + smtpHost + ":" + smtpPort);
        return session;
    }

    /**
     * 获取当前已存储的邮件会话（可能为 null）
     */
    public static Session getSession() {
        if (session == null) {
            Log.w("MailSessionManager", "getSession() called but no session exists");
            return null;
        }
        return session;
    }

    /**
     * 获取当前会话关联的邮箱地址
     */
    public static String getCurrentEmail() {
        return currentEmail;
    }

    /**
     * 重置会话（切换账号时使用）
     */
    public static void resetSession() {
        closeTransport();
        session = null;
        currentEmail = null;
        Log.d("MailSessionManager", "Session reset");
    }

    /**
     * 关闭持久化 Transport 连接
     */
    private static void closeTransport() {
        PersistentTransport.close();
    }
}
