package io.jaiclaw.tools.k8s;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.tools.ToolRegistry;
import io.jaiclaw.tools.exec.KubectlPolicyConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration that contributes Kubernetes tools as Spring beans.
 * Registration into {@link ToolRegistry} is handled by
 * {@code ToolBeanDiscovery} — no explicit
 * {@code toolRegistry.register(...)} call needed.
 */
@AutoConfiguration
@AutoConfigureAfter(name = "io.jaiclaw.autoconfigure.JaiClawAgentAutoConfiguration")
@ConditionalOnClass(name = "io.fabric8.kubernetes.client.KubernetesClient")
@ConditionalOnBean(ToolRegistry.class)
public class KubernetesToolsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public KubernetesClientProvider kubernetesClientProvider() {
        return new KubernetesClientProvider();
    }

    @Bean
    public ToolCallback listNamespacesTool(KubernetesClientProvider clientProvider) {
        return new ListNamespacesTool(clientProvider);
    }

    @Bean
    public ToolCallback listPodsTool(KubernetesClientProvider clientProvider) {
        return new ListPodsTool(clientProvider);
    }

    @Bean
    public ToolCallback getPodLogsTool(KubernetesClientProvider clientProvider) {
        return new GetPodLogsTool(clientProvider);
    }

    @Bean
    public ToolCallback describeResourceTool(KubernetesClientProvider clientProvider) {
        return new DescribeResourceTool(clientProvider);
    }

    @Bean
    public ToolCallback listEventsTool(KubernetesClientProvider clientProvider) {
        return new ListEventsTool(clientProvider);
    }

    @Bean
    public ToolCallback listNodesTool(KubernetesClientProvider clientProvider) {
        return new ListNodesTool(clientProvider);
    }

    @Bean
    public ToolCallback listDeploymentsTool(KubernetesClientProvider clientProvider) {
        return new ListDeploymentsTool(clientProvider);
    }

    @Bean
    public ToolCallback getResourceUsageTool(KubernetesClientProvider clientProvider) {
        return new GetResourceUsageTool(clientProvider);
    }

    @Bean
    public ToolCallback kubectlExecTool(ObjectProvider<KubectlPolicyConfig> kubectlPolicyConfigProvider) {
        KubectlPolicyConfig policyConfig = kubectlPolicyConfigProvider.getIfAvailable(
                () -> KubectlPolicyConfig.DEFAULT);
        return new KubectlExecTool(policyConfig);
    }
}
