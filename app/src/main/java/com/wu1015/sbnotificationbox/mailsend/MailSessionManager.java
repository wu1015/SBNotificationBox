package com.wu1015.sbnotificationbox.mailsend;

import android.util.Log;

import java.util.Properties;
import javax.mail.*;

public class MailSessionManager {
    private static Session session;

    public static Session getSession(String smtpHost, String smtpPort, String email, String password) {
        if (session == null) {
            Properties props = new Properties();
            props.put("mail.smtp.host", smtpHost);
            props.put("mail.smtp.port", smtpPort);
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.connectiontimeout", "10000"); // 10秒连接超时
            props.put("mail.smtp.timeout", "10000"); // 10秒读写超时

            session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(email, password);
                }
            });
        }
        return session;
    }
    public static Session getSession() {
        if (session == null) {
            return null;
        }
        Log.d("TAG", "getSession: "+session);
        return session;
    }
}
