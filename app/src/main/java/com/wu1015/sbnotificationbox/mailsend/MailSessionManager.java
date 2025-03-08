package com.wu1015.sbnotificationbox.mailsend;

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
        return session;
    }
}
