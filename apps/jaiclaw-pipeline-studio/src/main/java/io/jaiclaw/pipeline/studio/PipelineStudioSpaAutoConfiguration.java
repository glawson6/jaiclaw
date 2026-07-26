package io.jaiclaw.pipeline.studio;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Auto-configuration that serves the Pipeline Studio SPA at
 * {@code /studio/**}.
 *
 * <p>The SPA bundle lives at
 * {@code classpath:/META-INF/resources/studio/} — a location Spring
 * Boot's default static-resource handling already understands. What
 * this config adds is the SPA-fallback behaviour: any unmatched path
 * under {@code /studio/**} returns {@code index.html} instead of 404,
 * so React Router owns client-side routing.
 *
 * <p>Activates when Spring WebMVC is on the classpath and
 * {@code jaiclaw.pipeline.studio.spa.enabled} is {@code true}
 * (default). Turn it off by setting the property to {@code false} —
 * useful when a downstream app wants to serve its own SPA at a
 * different path.
 */
@AutoConfiguration
@ConditionalOnClass(DispatcherServlet.class)
@ConditionalOnProperty(prefix = "jaiclaw.pipeline.studio.spa",
        name = "enabled", havingValue = "true", matchIfMissing = true)
public class PipelineStudioSpaAutoConfiguration {

    @Bean
    public WebMvcConfigurer pipelineStudioSpaWebMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                registry.addResourceHandler("/studio/**")
                        .addResourceLocations("classpath:/META-INF/resources/studio/")
                        .resourceChain(true)
                        .addResolver(new PathResourceResolver() {
                            @Override
                            protected org.springframework.core.io.Resource getResource(
                                    String resourcePath,
                                    org.springframework.core.io.Resource location) throws IOException {
                                org.springframework.core.io.Resource requested = location.createRelative(resourcePath);
                                if (requested.exists() && requested.isReadable()) {
                                    return requested;
                                }
                                // Any unmatched sub-path falls back to
                                // index.html so React Router owns
                                // client-side navigation.
                                ClassPathResource index = new ClassPathResource(
                                        "META-INF/resources/studio/index.html");
                                return index.exists() ? index : null;
                            }
                        });
            }
        };
    }
}
