package io.jclaw.channel.email;

import io.jclaw.channel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Email channel adapter supporting IMAP inbound and SMTP outbound.
 * Polls IMAP folders for new messages and dispatches them as ChannelMessages.
 * Supports file attachments via MIME multipart parsing.
 */
public class EmailAdapter implements ChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(EmailAdapter.class);

    private final EmailConfig config;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ChannelMessageHandler handler;
    private Thread pollingThread;

    public EmailAdapter(EmailConfig config) {
        this.config = config;
    }

    @Override
    public String channelId() {
        return "email";
    }

    @Override
    public String displayName() {
        return "Email";
    }

    @Override
    public void start(ChannelMessageHandler handler) {
        this.handler = handler;
        running.set(true);

        pollingThread = Thread.ofVirtual().name("email-poller").start(() -> {
            log.info("Email polling started (interval={}s, folders={})",
                    config.pollingInterval(), Arrays.toString(config.folders()));
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    pollForMessages();
                    Thread.sleep(config.pollingInterval() * 1000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.warn("Email polling error (will retry): {}", e.getMessage());
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            log.info("Email polling stopped");
        });

        log.info("Email adapter started: provider={}, user={}", config.provider(), config.username());
    }

    @Override
    public DeliveryResult sendMessage(ChannelMessage message) {
        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host", config.smtpHost());
            props.put("mail.smtp.port", String.valueOf(config.smtpPort()));

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(config.username(), config.password());
                }
            });

            MimeMessage mimeMessage = new MimeMessage(session);
            mimeMessage.setFrom(new InternetAddress(config.username()));
            mimeMessage.setRecipients(Message.RecipientType.TO,
                    InternetAddress.parse(message.peerId()));
            mimeMessage.setSubject("Re: JClaw Response");
            mimeMessage.setText(message.content());

            Transport.send(mimeMessage);
            return new DeliveryResult.Success(mimeMessage.getMessageID());
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", message.peerId(), e.getMessage());
            return new DeliveryResult.Failure("email_send_failed", e.getMessage(), true);
        }
    }

    @Override
    public void stop() {
        running.set(false);
        if (pollingThread != null) {
            pollingThread.interrupt();
        }
        log.info("Email adapter stopped");
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    void pollForMessages() throws Exception {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", config.host());
        props.put("mail.imaps.port", String.valueOf(config.port()));

        Session session = Session.getInstance(props);
        try (Store store = session.getStore("imaps")) {
            store.connect(config.host(), config.username(), config.password());

            for (String folderName : config.folders()) {
                try (Folder folder = store.getFolder(folderName)) {
                    folder.open(Folder.READ_WRITE);
                    Message[] messages = folder.search(
                            new jakarta.mail.search.FlagTerm(new Flags(Flags.Flag.SEEN), false));

                    for (Message msg : messages) {
                        processMessage(msg);
                        msg.setFlag(Flags.Flag.SEEN, true);
                    }
                }
            }
        }
    }

    void processMessage(Message message) throws Exception {
        String from = ((InternetAddress) message.getFrom()[0]).getAddress();

        if (!config.isSenderAllowed(from)) {
            log.debug("Dropping email from non-allowed sender {}", from);
            return;
        }

        String subject = message.getSubject();
        String messageId = message instanceof MimeMessage mm ? mm.getMessageID() : UUID.randomUUID().toString();

        String body = extractTextContent(message);
        List<ChannelMessage.Attachment> attachments = extractAttachments(message);

        Map<String, Object> platformData = new HashMap<>();
        platformData.put("subject", subject != null ? subject : "");
        platformData.put("from", from);

        ChannelMessage channelMessage = ChannelMessage.inbound(
                messageId, "email", config.username(), from,
                body, attachments, platformData);

        if (handler != null) {
            handler.onMessage(channelMessage);
        }
    }

    String extractTextContent(Message message) throws Exception {
        if (message.isMimeType("text/plain")) {
            return (String) message.getContent();
        }
        if (message.isMimeType("text/html")) {
            return (String) message.getContent(); // Could strip HTML later
        }
        if (message.isMimeType("multipart/*")) {
            MimeMultipart multipart = (MimeMultipart) message.getContent();
            return extractTextFromMultipart(multipart);
        }
        return "";
    }

    private String extractTextFromMultipart(MimeMultipart multipart) throws Exception {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            if (part.isMimeType("text/plain")) {
                text.append(part.getContent());
            } else if (part.isMimeType("multipart/*")) {
                text.append(extractTextFromMultipart((MimeMultipart) part.getContent()));
            }
        }
        return text.toString();
    }

    List<ChannelMessage.Attachment> extractAttachments(Message message) throws Exception {
        List<ChannelMessage.Attachment> attachments = new ArrayList<>();
        if (!message.isMimeType("multipart/*")) return attachments;

        MimeMultipart multipart = (MimeMultipart) message.getContent();
        for (int i = 0; i < multipart.getCount(); i++) {
            BodyPart part = multipart.getBodyPart(i);
            if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) ||
                    (part.getFileName() != null && !part.getFileName().isBlank())) {
                String fileName = part.getFileName() != null ? part.getFileName() : "attachment";
                String mimeType = part.getContentType().split(";")[0].trim();
                byte[] data = readBytes(part.getInputStream());
                attachments.add(new ChannelMessage.Attachment(fileName, mimeType, null, data));
            }
        }
        return attachments;
    }

    private byte[] readBytes(InputStream is) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int len;
        while ((len = is.read(chunk)) != -1) {
            buffer.write(chunk, 0, len);
        }
        return buffer.toByteArray();
    }
}
