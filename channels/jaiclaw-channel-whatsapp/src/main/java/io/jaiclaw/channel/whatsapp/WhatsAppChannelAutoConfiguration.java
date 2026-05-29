package io.jaiclaw.channel.whatsapp;

import io.jaiclaw.camel.CamelChannelAdapter;
import io.jaiclaw.camel.CamelChannelConfig;
import io.jaiclaw.channel.chunking.PlatformLimits;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for the WhatsApp channel adapter using Apache Camel's
 * WhatsApp component. Activated when WhatsApp properties are configured.
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.apache.camel.component.whatsapp.WhatsAppComponent")
@ConditionalOnProperty(prefix = "jaiclaw.channels.whatsapp", name = "phone-number-id")
@EnableConfigurationProperties(WhatsAppProperties.class)
public class WhatsAppChannelAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppChannelAutoConfiguration.class);

    @Bean
    public RouteBuilder whatsAppInboundRoute(WhatsAppProperties properties) {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("whatsapp:" + properties.phoneNumberId())
                        .routeId("jaiclaw-whatsapp-inbound")
                        .to("seda:jaiclaw-whatsapp-in");
            }
        };
    }

    @Bean
    public RouteBuilder whatsAppOutboundRoute(WhatsAppProperties properties) {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:jaiclaw-whatsapp-out")
                        .routeId("jaiclaw-whatsapp-outbound")
                        .to("whatsapp:" + properties.phoneNumberId());
            }
        };
    }

    @Bean
    public CamelChannelAdapter whatsAppChannelAdapter(
            CamelContext camelContext,
            ProducerTemplate producerTemplate) {

        CamelChannelConfig config = new CamelChannelConfig(
                WhatsAppMessageConverter.CHANNEL_ID,
                "WhatsApp",
                "default",
                null,  // outboundUri handled by route
                null,  // inboundUri handled by route
                null,
                false,
                PlatformLimits.WHATSAPP
        );

        CamelChannelAdapter adapter = new CamelChannelAdapter(config, producerTemplate, camelContext);

        log.info("WhatsApp channel adapter configured");
        return adapter;
    }
}
