package io.jaiclaw.channel.whatsapp;

import io.jaiclaw.channel.ChannelMessage;
import org.apache.camel.Exchange;

import java.util.HashMap;
import java.util.Map;

/**
 * Converts between Camel WhatsApp exchanges and JaiClaw {@link ChannelMessage}.
 */
public final class WhatsAppMessageConverter {

    static final String CHANNEL_ID = "whatsapp";

    private WhatsAppMessageConverter() {}

    /**
     * Convert an inbound Camel WhatsApp exchange to a JaiClaw ChannelMessage.
     */
    public static ChannelMessage toChannelMessage(Exchange exchange, String accountId) {
        String body = exchange.getIn().getBody(String.class);

        // WhatsApp component sets CamelWhatsappSenderName and phone number headers
        String peerId = exchange.getIn().getHeader("CamelWhatsappSenderPhone", String.class);
        if (peerId == null || peerId.isBlank()) {
            peerId = exchange.getIn().getHeader("CamelWhatsappFrom", String.class);
        }
        if (peerId == null || peerId.isBlank()) {
            peerId = "unknown";
        }

        Map<String, Object> platformData = new HashMap<>();
        // Preserve relevant WhatsApp headers
        exchange.getIn().getHeaders().forEach((k, v) -> {
            if (k.startsWith("CamelWhatsapp")) {
                platformData.put(k, v);
            }
        });

        return ChannelMessage.inbound(
                exchange.getExchangeId(),
                CHANNEL_ID,
                accountId,
                peerId,
                body != null ? body : "",
                platformData
        );
    }

    /**
     * Populate a Camel Exchange from a JaiClaw outbound ChannelMessage for WhatsApp delivery.
     */
    public static void populateExchange(Exchange exchange, ChannelMessage message) {
        exchange.getIn().setBody(message.content());
        exchange.getIn().setHeader("CamelWhatsappTo", message.peerId());
    }
}
