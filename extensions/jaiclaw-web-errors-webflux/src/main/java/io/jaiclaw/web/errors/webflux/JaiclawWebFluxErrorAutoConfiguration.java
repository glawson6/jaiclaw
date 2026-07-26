package io.jaiclaw.web.errors.webflux;

import io.jaiclaw.web.errors.core.WebErrorLogger;
import io.jaiclaw.web.errors.core.WebErrorMapper;
import io.jaiclaw.web.errors.core.WebErrorProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Set;

/**
 * Auto-configuration for the JaiClaw WebFlux default error handler.
 *
 * <p>Activates when:
 * <ul>
 *   <li>{@code spring-boot-webflux} is on the classpath (guard class:
 *       {@code org.springframework.boot.webflux.error.ErrorWebExceptionHandler}).</li>
 *   <li>{@code jaiclaw.web.errors.enabled} is {@code true} (the default).</li>
 * </ul>
 *
 * <p>The guard MUST reference a class in {@code spring-boot-webflux} rather
 * than the higher-level {@code WebExceptionHandler} from {@code spring-web} /
 * {@code spring-webflux}. Reason: this autoconfig's {@code @Bean} method
 * returns {@link JaiclawWebFluxErrorHandler}, which
 * {@code implements ErrorWebExceptionHandler}. When Spring's
 * {@code OnBeanCondition} introspects the bean method's return type it
 * force-loads the entire interface hierarchy — if the top-level guard
 * passes on a servlet-stack app (because {@code WebExceptionHandler} is
 * present via {@code spring-web}) but {@code spring-boot-webflux} is not,
 * that introspection throws {@code NoClassDefFoundError} and boot dies.
 * Guarding on the bean's actual superinterface skips the whole
 * introspection when the reactive Boot layer isn't loaded.
 *
 * <p>The class-name matcher sets encode Spring 6+'s framework
 * exceptions for the reactive stack — note {@code NoResourceFoundException}
 * lives in a different package than its servlet counterpart, and the
 * binding-family names differ ({@code WebExchangeBindException},
 * {@code ServerWebInputException}).
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.boot.webflux.error.ErrorWebExceptionHandler")
@ConditionalOnProperty(prefix = "jaiclaw.web.errors", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(WebErrorProperties.class)
public class JaiclawWebFluxErrorAutoConfiguration {

    /**
     * Spring 6+ WebFlux framework-exception class names for each
     * category. Kept here (not in the -core module) so the shared
     * module has zero spring-webflux dependency.
     */
    static final WebErrorMapper.ExceptionCategoryMatchers FLUX_MATCHERS =
            new WebErrorMapper.ExceptionCategoryMatchers(
                    Set.of(
                            "org.springframework.web.reactive.resource.NoResourceFoundException"),
                    Set.of(
                            "org.springframework.web.HttpRequestMethodNotSupportedException"),
                    Set.of(
                            "org.springframework.web.HttpMediaTypeNotSupportedException"),
                    Set.of(
                            "org.springframework.web.HttpMediaTypeNotAcceptableException"),
                    Set.of(
                            "org.springframework.web.bind.support.WebExchangeBindException",
                            "org.springframework.web.server.ServerWebInputException",
                            "org.springframework.web.bind.MethodArgumentNotValidException",
                            "org.springframework.validation.BindException"),
                    Set.of(
                            "org.springframework.security.core.AuthenticationException"),
                    Set.of(
                            "org.springframework.security.access.AccessDeniedException"),
                    Set.of(
                            "org.springframework.web.server.ResponseStatusException"));

    @Bean
    @ConditionalOnMissingBean
    public WebErrorMapper webErrorMapper() {
        return new WebErrorMapper(FLUX_MATCHERS);
    }

    @Bean
    @ConditionalOnMissingBean
    public WebErrorLogger webErrorLogger() {
        return new Slf4jFluxWebErrorLogger();
    }

    @Bean
    @ConditionalOnMissingBean
    public JaiclawWebFluxErrorHandler jaiclawWebFluxErrorHandler(
            WebErrorMapper mapper,
            WebErrorProperties properties,
            WebErrorLogger errorLogger) {
        return new JaiclawWebFluxErrorHandler(mapper, properties, errorLogger);
    }
}
