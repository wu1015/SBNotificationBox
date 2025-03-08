package com.wu1015.sbnotificationbox.mailsend;

import javax.mail.*;

public class PersistentTransport {
    private static Transport transport;
    private static Message message;

    public static Transport getTransport(Session session) throws MessagingException {
        if (transport == null || !transport.isConnected()) {
            transport = session.getTransport("smtp");
            transport.connect();
        }
        return transport;
    }

    public static void sendEmail(Session session, Message message) throws MessagingException {
        Transport transport = getTransport(session);
        transport.sendMessage(message, message.getAllRecipients());
    }

    public static void sendEmail2(Session session,String subject, String messageText) throws MessagingException {
        Transport transport = getTransport(session);
        message.setSubject(subject);
        message.setText(messageText);
        transport.sendMessage(message, message.getAllRecipients());
    }

    public static void setMessage(Message message){
        PersistentTransport.message = message;
    }
}
