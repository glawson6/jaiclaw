package io.jaiclaw.calendar.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.calendar.mcp.CalendarMcpToolProvider;
import io.jaiclaw.calendar.provider.CalendarProvider;
import io.jaiclaw.calendar.provider.InMemoryCalendarProvider;
import io.jaiclaw.calendar.provider.RedisCalendarProvider;
import io.jaiclaw.calendar.service.CalendarService;
import io.jaiclaw.calendar.tool.CalendarTools;
import io.jaiclaw.calendar.util.CalendarEventValidator;
import io.jaiclaw.core.tenant.TenantGuard;
import io.jaiclaw.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

@AutoConfiguration
@AutoConfigureAfter(name = "io.jaiclaw.autoconfigure.JaiClawAutoConfiguration")
@ConditionalOnProperty(name = "jaiclaw.calendar.enabled", havingValue = "true", matchIfMissing = false)
public class JaiClawCalendarAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JaiClawCalendarAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public CalendarProperties calendarProperties(Environment environment) {
        return Binder.get(environment)
                .bind("jaiclaw.calendar", CalendarProperties.class)
                .orElse(new CalendarProperties());
    }

    @Bean
    @ConditionalOnMissingBean
    public CalendarEventValidator calendarEventValidator(CalendarProperties properties) {
        return new CalendarEventValidator(properties);
    }

    @Bean
    @ConditionalOnProperty(name = "jaiclaw.calendar.provider", havingValue = "redis")
    @ConditionalOnBean(ReactiveStringRedisTemplate.class)
    public CalendarProvider redisCalendarProvider(
            ReactiveStringRedisTemplate redisTemplate,
            CalendarEventValidator eventValidator) {
        ObjectMapper objectMapper = CalendarTools.createObjectMapper();
        RedisCalendarProvider provider = new RedisCalendarProvider(redisTemplate, objectMapper, eventValidator);
        log.info("Calendar provider initialized: {}", provider.getProviderName());
        return provider;
    }

    @Bean
    @ConditionalOnMissingBean(CalendarProvider.class)
    public CalendarProvider calendarProvider(CalendarProperties properties, TenantGuard tenantGuard) {
        InMemoryCalendarProvider provider = new InMemoryCalendarProvider();
        provider.setTenantGuard(tenantGuard);
        if ("in-memory".equals(properties.provider())) {
            provider.initialize();
        }
        log.info("Calendar provider initialized: {}", provider.getProviderName());
        return provider;
    }

    @Bean
    @ConditionalOnMissingBean
    public CalendarService calendarService(CalendarProvider calendarProvider) {
        return new CalendarService(calendarProvider);
    }

    @Bean
    @ConditionalOnBean(ToolRegistry.class)
    public CalendarToolsRegistrar calendarToolsRegistrar(
            ToolRegistry toolRegistry, CalendarService calendarService,
            CalendarProperties properties, CalendarEventValidator validator) {
        log.info("Registering Calendar tools into ToolRegistry");
        ObjectMapper objectMapper = CalendarTools.createObjectMapper();
        CalendarTools.registerAll(toolRegistry, calendarService, properties, objectMapper, validator);
        return new CalendarToolsRegistrar();
    }

    @Bean
    @ConditionalOnMissingBean
    public CalendarMcpToolProvider calendarMcpToolProvider(
            CalendarService calendarService, CalendarProperties properties,
            CalendarEventValidator validator) {
        ObjectMapper objectMapper = CalendarTools.createObjectMapper();
        return new CalendarMcpToolProvider(calendarService, properties, validator, objectMapper);
    }

    public static class CalendarToolsRegistrar {}
}
