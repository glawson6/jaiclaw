package io.jaiclaw.web.errors.mvc;

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
 * Auto-configuration for the JaiClaw WebMVC default exception handler.
 *
 * <p>Activates when:
 * <ul>
 *   <li>Spring WebMVC ({@code HandlerExceptionResolver}) is on the classpath.</li>
 *   <li>{@code jaiclaw.web.errors.enabled} is {@code true} (the default).</li>
 * </ul>
 *
 * <p>The class-name matcher sets encode Spring 6+'s framework
 * exceptions for the servlet stack. Adopters can override any of the
 * three beans ({@link WebErrorMapper}, {@link WebErrorLogger},
 * {@link JaiclawDefaultExceptionHandler}) via {@code @ConditionalOnMissingBean}.
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.web.servlet.HandlerExceptionResolver")
@ConditionalOnProperty(prefix = "jaiclaw.web.errors", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(WebErrorProperties.class)
public class JaiclawWebMvcErrorAutoConfiguration {

    /**
     * Spring 6+ WebMVC framework-exception class names for each category.
     * Kept here (not in the -core module) so the shared module has zero
     * spring-webmvc dependency.
     */
    static final WebErrorMapper.ExceptionCategoryMatchers MVC_MATCHERS =
            new WebErrorMapper.ExceptionCategoryMatchers(
                    Set.of(
                            "org.springframework.web.servlet.resource.NoResourceFoundException",
                            "org.springframework.web.servlet.NoHandlerFoundException"),
                    Set.of(
                            "org.springframework.web.HttpRequestMethodNotSupportedException"),
                    Set.of(
                            "org.springframework.web.HttpMediaTypeNotSupportedException"),
                    Set.of(
                            "org.springframework.web.HttpMediaTypeNotAcceptableException"),
                    Set.of(
                            "org.springframework.web.bind.MethodArgumentNotValidException",
                            "org.springframework.validation.BindException",
                            "org.springframework.web.bind.MissingRequestValueException",
                            "org.springframework.web.bind.MissingServletRequestParameterException"),
                    Set.of(
                            "org.springframework.security.core.AuthenticationException"),
                    Set.of(
                            "org.springframework.security.access.AccessDeniedException"),
                    Set.of(
                            "org.springframework.web.server.ResponseStatusException"));

    @Bean
    @ConditionalOnMissingBean
    public WebErrorMapper webErrorMapper() {
        return new WebErrorMapper(MVC_MATCHERS);
    }

    @Bean
    @ConditionalOnMissingBean
    public WebErrorLogger webErrorLogger() {
        return new Slf4jMvcWebErrorLogger();
    }

    @Bean
    @ConditionalOnMissingBean
    public JaiclawDefaultExceptionHandler jaiclawDefaultExceptionHandler(
            WebErrorMapper mapper,
            WebErrorProperties properties,
            WebErrorLogger errorLogger) {
        return new JaiclawDefaultExceptionHandler(mapper, properties, errorLogger);
    }
}
