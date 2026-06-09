package io.jaiclaw.camel;

import io.jaiclaw.channel.ChannelMessage;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultExchange;

import java.util.HashMap;
import java.util.Map;

/**
 * Static utility for converting between Camel {@link Exchange} and JaiClaw {@link ChannelMessage}.
 */
public final class CamelMessageConverter {

    public static final String HEADER_PEER_ID = "JaiClawPeerId";
    public static final String HEADER_PIPELINE = "JaiClawPipeline";
    public static final String HEADER_PIPELINE_CONTEXT = "JaiClawPipelineContext";
    public static final String HEADER_CHANNEL_ID = "JaiClawChannelId";
    public static final String HEADER_SESSION_KEY = "JaiClawSessionKey";

    private CamelMessageConverter() {}

    /**
     * Convert a Camel Exchange into a JaiClaw inbound ChannelMessage.
     *
     * @param exchange  the Camel exchange
     * @param channelId the channel identifier
     * @param accountId the account identifier
     * @return an inbound ChannelMessage
     */
    public static ChannelMessage toChannelMessage(Exchange exchange, String channelId, String accountId) {
        String content = exchange.getIn().getBody(String.class);
        String peerId = exchange.getIn().getHeader(HEADER_PEER_ID, "anonymous", String.class);

        Map<String, Object> platformData = new HashMap<>(exchange.getIn().getHeaders());

        Object pipeline = exchange.getIn().getHeader(HEADER_PIPELINE);
        if (pipeline instanceof PipelineEnvelope envelope) {
            platformData.put("pipeline", envelope);
        }

        Object pipelineContext = exchange.getIn().getHeader(HEADER_PIPELINE_CONTEXT);
        if (pipelineContext != null) {
            platformData.put("pipelineContext", pipelineContext);
        }

        return ChannelMessage.inbound(
                exchange.getExchangeId(),
                channelId,
                accountId,
                peerId,
                content,
                platformData
        );
    }

    /**
     * Convert a JaiClaw ChannelMessage into a Camel Exchange.
     *
     * @param message      the channel message
     * @param camelContext  the Camel context for exchange creation
     * @return a new DefaultExchange populated from the message
     */
    public static Exchange toExchange(ChannelMessage message, CamelContext camelContext) {
        DefaultExchange exchange = new DefaultExchange(camelContext);
        exchange.getIn().setBody(message.content());
        exchange.getIn().setHeader(HEADER_CHANNEL_ID, message.channelId());
        exchange.getIn().setHeader(HEADER_PEER_ID, message.peerId());
        exchange.getIn().setHeader(HEADER_SESSION_KEY, message.sessionKey("default"));

        Object pipeline = message.platformData().get("pipeline");
        if (pipeline instanceof PipelineEnvelope envelope) {
            exchange.getIn().setHeader(HEADER_PIPELINE, envelope);
        }

        Object pipelineContext = message.platformData().get("pipelineContext");
        if (pipelineContext != null) {
            exchange.getIn().setHeader(HEADER_PIPELINE_CONTEXT, pipelineContext);
        }

        return exchange;
    }
}
