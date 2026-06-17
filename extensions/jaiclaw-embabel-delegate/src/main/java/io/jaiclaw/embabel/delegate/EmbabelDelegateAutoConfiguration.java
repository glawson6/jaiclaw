package io.jaiclaw.embabel.delegate;

import com.embabel.agent.core.AgentPlatform;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jaiclaw.tools.bridge.embabel.AgentOrchestrationPort;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration that registers {@link EmbabelAgentLoopDelegate} when
 * Embabel's {@link AgentPlatform} is available on the classpath and as a bean.
 *
 * <p>Must run after Embabel's {@code AgentPlatformAutoConfiguration} (which creates
 * the {@link AgentPlatform} bean) and before JaiClaw's auto-config collects delegates
 * into the {@code AgentLoopDelegateRegistry}.
 *
 * <p>The delegate bean is automatically discovered by
 * {@code AgentLoopDelegateRegistry} via {@code ObjectProvider<List<AgentLoopDelegate>>}.
 */
@AutoConfiguration
@AutoConfigureAfter(name = "com.embabel.agent.autoconfigure.platform.AgentPlatformAutoConfiguration")
// Run BEFORE the tools auto-config so the real Embabel-backed
// AgentOrchestrationPort wins over the NoOp default registered there.
@AutoConfigureBefore(name = "io.jaiclaw.autoconfigure.JaiClawToolsAutoConfiguration")
@ConditionalOnClass(name = "com.embabel.agent.core.AgentPlatform")
public class EmbabelDelegateAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(AgentPlatform.class)
    public EmbabelAgentLoopDelegate embabelAgentLoopDelegate(
            AgentPlatform agentPlatform, ObjectMapper objectMapper) {
        return new EmbabelAgentLoopDelegate(agentPlatform, objectMapper);
    }

    /**
     * Wires Embabel's {@link AgentPlatform} into the JaiClaw
     * {@link AgentOrchestrationPort} SPI so pipeline {@code AGENT} stages
     * with {@code runtime: EMBABEL} can route through the GOAP planner.
     *
     * <p>Conditional on {@code AgentPlatform} so this bean only lands
     * when Embabel is actually configured (not just on the classpath).
     * Apps without Embabel get no port; pipeline stages requesting
     * EMBABEL runtime then fail fast at validator time with a clear
     * "Embabel runtime not available" message.
     */
    @Bean
    @ConditionalOnMissingBean(AgentOrchestrationPort.class)
    @ConditionalOnBean(AgentPlatform.class)
    public AgentOrchestrationPort embabelAgentOrchestrationPort(
            AgentPlatform agentPlatform, ObjectMapper objectMapper) {
        return new EmbabelAgentOrchestrationPort(agentPlatform, objectMapper);
    }
}
