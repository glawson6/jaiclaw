package io.jaiclaw.tools.k8s;

import io.jaiclaw.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration that registers Kubernetes tools into the JaiClaw ToolRegistry.
 * Runs after {@code JaiClawAutoConfiguration} so that {@link ToolRegistry} is available.
 *
 * <p>Since {@link io.jaiclaw.agent.AgentRuntime} resolves tools lazily from the registry
 * on each run() call, tools registered here are automatically available to the agent.
 */
@AutoConfiguration
@AutoConfigureAfter(name = "io.jaiclaw.autoconfigure.JaiClawAutoConfiguration")
@ConditionalOnClass(name = "io.fabric8.kubernetes.client.KubernetesClient")
@ConditionalOnBean(ToolRegistry.class)
public class KubernetesToolsAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KubernetesToolsAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public KubernetesClientProvider kubernetesClientProvider() {
        return new KubernetesClientProvider();
    }

    @Bean
    public KubernetesToolsRegistrar kubernetesToolsRegistrar(
            ToolRegistry toolRegistry,
            KubernetesClientProvider clientProvider) {
        log.info("Registering Kubernetes tools into ToolRegistry");
        KubernetesTools.registerAll(toolRegistry, clientProvider);
        return new KubernetesToolsRegistrar();
    }

    /**
     * Marker bean to indicate that Kubernetes tools have been registered.
     */
    public static class KubernetesToolsRegistrar {}
}
