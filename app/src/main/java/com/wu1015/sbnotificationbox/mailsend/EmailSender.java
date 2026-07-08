package com.wu1015.sbnotificationbox.mailsend;

import android.util.Log;

import java.io.File;
import java.util.Locale;

import javax.mail.*;
import javax.mail.internet.*;

/**
 * 邮件发送工具类。
 * 支持 QQ、Gmail、Outlook、163、126、Yeah、Yahoo 等主流邮箱。
 * 根据邮箱域名自动检测 SMTP 服务器配置。
 */
public class EmailSender {

    /**
     * 邮件服务商配置
     */
    public enum Provider {
        QQ("smtp.qq.com", "465", "QQ邮箱"),
        GMAIL("smtp.gmail.com", "587", "Gmail"),
        OUTLOOK("smtp.office365.com", "587", "Outlook / Office365"),
        MAIL_163("smtp.163.com", "465", "163邮箱"),
        MAIL_126("smtp.126.com", "465", "126邮箱"),
        YEAH("smtp.yeah.net", "465", "Yeah邮箱"),
        YAHOO("smtp.mail.yahoo.com", "587", "Yahoo邮箱"),
        SINA("smtp.sina.com", "465", "新浪邮箱"),
        SOHU("smtp.sohu.com", "465", "搜狐邮箱"),
        CUSTOM("", "587", "自定义");

        private final String host;
        private final String port;
        private final String displayName;

        Provider(String host, String port, String displayName) {
            this.host = host;
            this.port = port;
            this.displayName = displayName;
        }

        public String getHost() { return host; }
        public String getPort() { return port; }
        public String getDisplayName() { return displayName; }
    }

    /**
     * 根据邮箱地址自动检测邮件服务商
     */
    public static Provider detectProvider(String email) {
        if (email == null || !email.contains("@")) {
            return Provider.CUSTOM;
        }

        String domain = email.substring(email.indexOf("@") + 1).toLowerCase(Locale.ROOT).trim();

        if (domain.equals("qq.com")) {
            return Provider.QQ;
        } else if (domain.equals("gmail.com") || domain.equals("googlemail.com")) {
            return Provider.GMAIL;
        } else if (domain.equals("outlook.com") || domain.equals("hotmail.com")
                || domain.equals("live.com") || domain.equals("msn.com")
                || domain.endsWith("office365.com")) {
            return Provider.OUTLOOK;
        } else if (domain.equals("163.com")) {
            return Provider.MAIL_163;
        } else if (domain.equals("126.com")) {
            return Provider.MAIL_126;
        } else if (domain.equals("yeah.net")) {
            return Provider.YEAH;
        } else if (domain.endsWith("yahoo.com") || domain.endsWith("yahoo.co.jp")
                || domain.endsWith("yahoo.com.cn")) {
            return Provider.YAHOO;
        } else if (domain.equals("sina.com") || domain.equals("sina.cn")) {
            return Provider.SINA;
        } else if (domain.equals("sohu.com")) {
            return Provider.SOHU;
        }

        return Provider.CUSTOM;
    }

    /**
     * 使用自动检测的提供商初始化邮件会话
     */
    public static boolean initSession(String email, String password) {
        Provider provider = detectProvider(email);
        return initSession(provider, email, password);
    }

    /**
     * 使用指定提供商初始化邮件会话
     */
    public static boolean initSession(Provider provider, String email, String password) {
        if (provider == Provider.CUSTOM) {
            Log.w("EmailSender", "Custom provider requires manual SMTP configuration");
            return false;
        }
        MailSessionManager.getSession(provider.getHost(), provider.getPort(), email, password);
        return true;
    }

    /**
     * 使用自定义 SMTP 初始化邮件会话
     */
    public static void initSession(String smtpHost, String smtpPort, String email, String password) {
        MailSessionManager.getSession(smtpHost, smtpPort, email, password);
    }

    /**
     * 发送纯文本邮件（使用已配置的会话）
     *
     * @param email       发件人邮箱（需与会话中的邮箱一致）
     * @param toEmail     收件人邮箱
     * @param subject     邮件主题
     * @param messageText 邮件正文
     * @return 发送成功返回 true
     */
    public static boolean sendEmail2(String email, String toEmail, String subject, String messageText) {
        try {
            Session session = MailSessionManager.getSession();
            if (session == null) {
                Log.e("EmailSender", "No mail session configured. Please login first.");
                return false;
            }

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(email));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject(subject);
            message.setText(messageText);

            PersistentTransport.sendEmail(session, message);
            Log.d("EmailSender", "Email sent to: " + toEmail);
            return true;
        } catch (MessagingException e) {
            Log.e("EmailSender", "Failed to send email", e);
            return false;
        }
    }

    /**
     * 发送带附件的邮件
     *
     * @param email          发件人邮箱
     * @param toEmail        收件人邮箱
     * @param subject        邮件主题
     * @param messageText    邮件正文
     * @param attachmentFile 附件文件
     * @return 发送成功返回 true
     */
    public static boolean sendEmailWithAttachment(String email, String toEmail,
                                                  String subject, String messageText,
                                                  File attachmentFile) {
        try {
            Session session = MailSessionManager.getSession();
            if (session == null) {
                Log.e("EmailSender", "No mail session configured. Please login first.");
                return false;
            }

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(email));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject(subject);

            // 创建正文部分
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(messageText, "UTF-8");

            // 创建附件部分
            MimeBodyPart attachmentPart = new MimeBodyPart();
            attachmentPart.attachFile(attachmentFile);
            attachmentPart.setFileName(attachmentFile.getName());

            // 组装邮件（正文 + 附件）
            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(textPart);
            multipart.addBodyPart(attachmentPart);
            message.setContent(multipart);

            PersistentTransport.sendEmail(session, message);
            Log.d("EmailSender", "Email with attachment sent to: " + toEmail);
            return true;
        } catch (Exception e) {
            Log.e("EmailSender", "Failed to send email with attachment", e);
            return false;
        }
    }

    /**
     * 发送测试邮件以验证邮箱配置
     *
     * @param email    发件人邮箱
     * @param password 密码/授权码
     * @param toEmail  收件人邮箱
     * @return 发送成功返回 true
     */
    public static boolean sendTestEmail(String email, String password, String toEmail) {
        try {
            Provider provider = detectProvider(email);
            if (provider == Provider.CUSTOM) {
                Log.e("EmailSender", "Cannot auto-detect provider for: " + email);
                return false;
            }

            initSession(provider, email, password);
            return sendEmail2(email, toEmail,
                    "[SBNotificationBox] Test Email",
                    "Hello! This is a test email from SBNotificationBox.\n\n"
                            + "Provider: " + provider.getDisplayName() + "\n"
                            + "SMTP: " + provider.getHost() + ":" + provider.getPort() + "\n\n"
                            + "If you received this, your email configuration is working correctly.");
        } catch (Exception e) {
            Log.e("EmailSender", "Test email failed", e);
            return false;
        }
    }
}
