package com.wu1015.sbnotificationbox.mailsend;

import javax.mail.*;

public class PersistentTransport {
    private static Transport transport = null;

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
        if (transport.isConnected()) {
            transport.close();
        }
    }
}
