package com.wu1015.sbnotificationbox.mailsend;

import javax.mail.*;
import javax.mail.internet.*;

// 邮件发送工具类
public class EmailSender {
    private String smtpHost;
    private String smtpPort;

    public EmailSender(String smtpHost, String smtpPort, String username, String password) {
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        Session session = MailSessionManager.getSession(smtpHost, smtpPort, username, password);
    }

    public EmailSender() {
    }

    public void outlookSender(String username, String password){
        this.smtpHost = "smtp.office365.com";
        this.smtpPort = "587";
        Session session = MailSessionManager.getSession(smtpHost, smtpPort, username, password);
    }

    public void qqMailSender(String username, String password){
        this.smtpHost = "smtp.qq.com";
        this.smtpPort = "465";
        Session session = MailSessionManager.getSession(smtpHost, smtpPort, username, password);
    }

    /*
     public void sendEmail(String toEmail, String subject, String messageBody) throws MessagingException {
         Properties props = new Properties();
         props.put("mail.smtp.host", smtpHost);
         props.put("mail.smtp.port", smtpPort);
         props.put("mail.smtp.auth", "true");
         props.put("mail.smtp.starttls.enable", "true");

         Session session = Session.getInstance(props, new Authenticator() {
             @Override
             protected PasswordAuthentication getPasswordAuthentication() {
                 return new PasswordAuthentication(username, password);
             }
         });

         Message message = new MimeMessage(session);
         message.setFrom(new InternetAddress(username));
         message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
         message.setSubject(subject);
         message.setText(messageBody);

         Transport.send(message);
     }
    */

    // 第一次登录测试邮件发送
    public static boolean sendEmail2(String email, String toEmail, String subject, String messageText) {
        try {
            Session session =MailSessionManager.getSession();
            if(session == null){
                return false;
            }
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(email));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject(subject);
            message.setText(messageText);

            PersistentTransport.sendEmail(session, message); // 使用持久化连接发送邮件
            System.out.println("📧 邮件已发送到：" + toEmail);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    // 反复发送
    public static boolean sendEmail3(String subject, String messageText) {
        try {
            Session session = MailSessionManager.getSession();
            if(session == null){
                return false;
            }
            Message message = new MimeMessage(session);

            message.setSubject(subject);
            message.setText(messageText);

            PersistentTransport.sendEmail2(session,subject, messageText); // 使用持久化连接发送邮件

            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
