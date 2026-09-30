package io.jaiclaw.autoconfigure

import io.jaiclaw.config.JaiClawProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import spock.lang.Specification

import java.lang.reflect.Method

/**
 * Locks the {@code @Primary} annotation on
 * {@code TelegramAutoConfiguration.telegramUserIdFilter(...)} that resolves
 * the {@code ChannelMessageHandler} autowire ambiguity reported in
 * {@code docs/issues/camel-channel-handler-disambiguation.md}.
 *
 * <p>Without {@code @Primary}, Spring boot fails with
 * {@code NoUniqueBeanDefinitionException} whenever an app depends on both
 * {@code jaiclaw-starter-pipeline} (which transitively pulls
 * {@code jaiclaw-camel} and its
 * {@code ObjectProvider<ChannelMessageHandler>.getIfAvailable()} call) AND a
 * Telegram channel with rate-limiting (which adds
 * {@code TelegramUserIdFilter} as a second {@code ChannelMessageHandler}
 * bean). The filter wraps the gateway and IS the canonical handler from
 * the channel-adapter perspective; {@code @Primary} encodes that.
 *
 * <p>A future refactor that drops the annotation will fail this spec
 * immediately rather than re-introducing the runtime crash for downstream
 * apps. (It did exactly that during the 1.3.0 filter-chaining refactor, which
 * removed {@code @Primary} on the mistaken assumption it governed filter
 * selection — it governs {@code ChannelMessageHandler} autowiring, which is a
 * different concern and still needs it.)
 */
class CamelChannelHandlerDisambiguationSpec extends Specification {

    def "telegramUserIdFilter @Bean factory carries @Primary"() {
        given:
        Class<?> telegramConfig = Class.forName(
                "io.jaiclaw.autoconfigure.JaiClawChannelAutoConfiguration\$TelegramAutoConfiguration")
        // 1.3.0: the GatewayService parameter was removed — FilteredGatewayLifecycle
        // now wires each filter's downstream, so the filter no longer needs a
        // reference to the gateway. @Primary is still required for the reason
        // below and is what this spec exists to lock.
        Method beanMethod = telegramConfig.getDeclaredMethod(
                "telegramUserIdFilter",
                JaiClawProperties,
                io.jaiclaw.security.ratelimit.UserRateLimiter)

        expect: "the bean factory exists and produces a TelegramUserIdFilter"
        beanMethod.getAnnotation(Bean) != null
        beanMethod.returnType == io.jaiclaw.channel.telegram.TelegramUserIdFilter

        and: "@Primary is present so it wins the ChannelMessageHandler autowire"
        beanMethod.getAnnotation(Primary) != null
    }
}
