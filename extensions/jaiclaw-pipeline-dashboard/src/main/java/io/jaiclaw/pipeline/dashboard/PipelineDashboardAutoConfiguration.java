package io.jaiclaw.pipeline.dashboard;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Auto-configuration for the read-only Pipeline Dashboard.
 *
 * <p>Activates when:
 * <ul>
 *   <li>Spring WebMVC ({@link DispatcherServlet}) is on the classpath.</li>
 *   <li>{@code jaiclaw.pipeline.dashboard.enabled} is {@code true}
 *       (default).</li>
 * </ul>
 *
 * <p>Registers:
 * <ul>
 *   <li>{@link PipelineDashboardController} — HTML shell + whoami.</li>
 *   <li>A {@link WebMvcConfigurer} mapping
 *       {@code /pipelines/dashboard/**} → {@code classpath:/jaiclaw-pipeline-dashboard/}
 *       so the CSS/JS assets load without the controller having to
 *       stream every one.</li>
 * </ul>
 */
@AutoConfiguration
@ConditionalOnClass(DispatcherServlet.class)
@ConditionalOnProperty(prefix = "jaiclaw.pipeline.dashboard",
        name = "enabled", havingValue = "true", matchIfMissing = true)
public class PipelineDashboardAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PipelineDashboardController pipelineDashboardController() {
        return new PipelineDashboardController();
    }

    @Bean
    @ConditionalOnMissingBean(name = "pipelineDashboardWebMvcConfigurer")
    public WebMvcConfigurer pipelineDashboardWebMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                registry.addResourceHandler("/pipelines/dashboard/assets/**")
                        .addResourceLocations("classpath:/jaiclaw-pipeline-dashboard/assets/");
                registry.addResourceHandler("/pipelines/dashboard/*.css",
                                            "/pipelines/dashboard/*.js")
                        .addResourceLocations("classpath:/jaiclaw-pipeline-dashboard/");
            }
        };
    }
}
