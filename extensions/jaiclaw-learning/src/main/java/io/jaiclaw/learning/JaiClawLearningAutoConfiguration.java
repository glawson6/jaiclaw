package io.jaiclaw.learning;

import io.jaiclaw.agent.session.SessionManager;
import io.jaiclaw.core.agent.AgentHookDispatcher;
import io.jaiclaw.core.agent.AgentMindMemoryProvider;
import io.jaiclaw.learning.apply.MemoryProposalApplier;
import io.jaiclaw.learning.apply.ProposalApplier;
import io.jaiclaw.learning.proposal.JsonFileProposalStore;
import io.jaiclaw.learning.proposal.ProposalService;
import io.jaiclaw.learning.proposal.ProposalStore;
import io.jaiclaw.learning.proposal.ProposalStoreProvider;
import io.jaiclaw.learning.review.LearningReviewer;
import io.jaiclaw.learning.review.LlmLearningReviewer;
import io.jaiclaw.learning.review.ReviewCadenceGate;
import io.jaiclaw.learning.review.ReviewTrigger;
import io.jaiclaw.learning.review.TranscriptSourceAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.nio.file.Path;

/**
 * Wires the learning loop.
 *
 * <p>Every bean here is gated on {@code jaiclaw.learning.mode} being something
 * other than {@code off}, so the default configuration creates <strong>nothing</strong>
 * and the module is safe to keep on the classpath in a deployment that does not
 * want it. {@code @ConditionalOnProperty} cannot express "not equal to a value",
 * hence the small explicit {@link LearningEnabledCondition}.
 *
 * <p>The reviewer prefers a bean named {@code learningChatModel} when present, so
 * reviews can run on a cheap auxiliary model rather than the one serving
 * conversations; it falls back to the primary {@link ChatModel}.
 *
 * <p>Phase 4A of the 1.2.0 plan.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LearningProperties.class)
@Conditional(JaiClawLearningAutoConfiguration.LearningEnabledCondition.class)
public class JaiClawLearningAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JaiClawLearningAutoConfiguration.class);

    /** True unless {@code jaiclaw.learning.mode} is absent or {@code off}. */
    static class LearningEnabledCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String mode = context.getEnvironment().getProperty("jaiclaw.learning.mode", "off");
            return !"off".equalsIgnoreCase(mode.trim());
        }
    }

    @Bean
    @ConditionalOnMissingBean(ProposalStore.class)
    public ProposalStore learningProposalStore(LearningProperties properties,
                                               ObjectProvider<ProposalStoreProvider> providers) {
        ProposalStoreProvider provider = providers.getIfAvailable();
        if (provider != null) {
            log.info("Learning proposal store: {} (custom provider)", provider.name());
            return provider.get();
        }
        Path dir = Path.of(properties.proposalsDir());
        log.info("Learning proposal store: json-file at {}", dir);
        return new JsonFileProposalStore(dir);
    }

    @Bean
    @ConditionalOnMissingBean
    public ProposalService learningProposalService(ProposalStore store) {
        return new ProposalService(store);
    }

    @Bean
    @ConditionalOnMissingBean
    public TranscriptSourceAdapter learningTranscriptSource(SessionManager sessionManager) {
        return new TranscriptSourceAdapter(sessionManager);
    }

    @Bean
    @ConditionalOnMissingBean
    public ReviewCadenceGate learningCadenceGate(LearningProperties properties) {
        return new ReviewCadenceGate(properties.reviewMinInterval(), properties.reviewMinTurns());
    }

    /**
     * The default reviewer. Requires a {@link ChatModel}; without one the learning
     * loop simply does not review, rather than failing the context.
     */
    @Bean
    @ConditionalOnBean(ChatModel.class)
    @ConditionalOnMissingBean(LearningReviewer.class)
    public LearningReviewer learningReviewer(ObjectProvider<ChatModel> chatModels,
                                             LearningProperties properties) {
        ChatModel auxiliary = chatModels.getIfAvailable();
        return new LlmLearningReviewer(auxiliary, properties.maxTranscriptChars());
    }

    @Bean
    @ConditionalOnMissingBean
    public io.jaiclaw.learning.skill.SkillWriter learningSkillWriter(LearningProperties properties) {
        return new io.jaiclaw.learning.skill.SkillWriter(Path.of(properties.skillsDir()));
    }

    @Bean
    @ConditionalOnMissingBean
    public io.jaiclaw.learning.ledger.LearningLedger learningLedger(LearningProperties properties) {
        return new io.jaiclaw.learning.ledger.LearningLedger(Path.of(properties.proposalsDir()));
    }

    @Bean
    @ConditionalOnMissingBean
    public io.jaiclaw.learning.curator.SkillCurator learningSkillCurator(
            io.jaiclaw.learning.skill.SkillWriter skills, LearningProperties properties) {
        return new io.jaiclaw.learning.curator.SkillCurator(skills, properties);
    }

    /**
     * The applier the rest of the module uses. Skill handling wraps memory
     * handling rather than replacing it, so one bean covers all three proposal
     * kinds and callers never have to pick.
     */
    @Bean
    @ConditionalOnMissingBean(ProposalApplier.class)
    public ProposalApplier learningProposalApplier(
            ObjectProvider<AgentMindMemoryProvider> memoryProviders,
            ProposalService proposals,
            LearningProperties properties,
            ObjectProvider<AgentHookDispatcher> hooks,
            io.jaiclaw.learning.skill.SkillWriter skills,
            io.jaiclaw.learning.ledger.LearningLedger ledger) {
        MemoryProposalApplier memory = new MemoryProposalApplier(
                memoryProviders.getIfAvailable(), proposals, properties, hooks.getIfAvailable());
        return new io.jaiclaw.learning.apply.SkillProposalApplier(
                skills, ledger, proposals, properties, memory);
    }

    @Bean
    @ConditionalOnBean(LearningReviewer.class)
    @ConditionalOnMissingBean
    public ReviewTrigger learningReviewTrigger(LearningReviewer reviewer,
                                               ProposalService proposals,
                                               TranscriptSourceAdapter transcripts,
                                               ReviewCadenceGate cadenceGate,
                                               LearningProperties properties,
                                               ObjectProvider<ProposalApplier> appliers) {
        log.info("Learning loop ENABLED — mode={} minTurns={} minInterval={} transcriptCap={}",
                properties.mode(), properties.reviewMinTurns(),
                properties.reviewMinInterval(), properties.maxTranscriptChars());
        if (properties.isAuto()) {
            log.warn("Learning mode is 'auto' — memory and new-skill proposals apply "
                    + "WITHOUT operator review. Skill patches still require an explicit apply "
                    + "unless jaiclaw.learning.auto-allow-patches=true.");
        }
        return new ReviewTrigger(reviewer, proposals, transcripts, cadenceGate,
                properties, appliers.getIfAvailable());
    }
}
