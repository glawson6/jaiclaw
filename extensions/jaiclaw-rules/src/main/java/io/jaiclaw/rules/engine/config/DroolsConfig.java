package io.jaiclaw.rules.engine.config;

import io.jaiclaw.rules.engine.loader.RuleLoader;
import io.jaiclaw.rules.engine.loader.RuleLoaderFactory;
import io.jaiclaw.rules.engine.loader.RuleLoadingException;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieRepository;
import org.kie.api.builder.Message;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.StatelessKieSession;
import org.kie.internal.io.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * Drools configuration for KIE services.
 * Sets up the KieContainer and provides stateless sessions for thread-safe rule execution.
 *
 * This configuration supports configurable rule loading from multiple sources
 * including classpath, filesystem, and URLs. The rule sources are configured via
 * application properties (see DroolsProperties).
 */
public class DroolsConfig {

    private static final Logger logger = LoggerFactory.getLogger(DroolsConfig.class);

    private final KieServices kieServices = KieServices.Factory.get();
    private final DroolsProperties droolsProperties;
    private final RuleLoaderFactory ruleLoaderFactory;

    public DroolsConfig(DroolsProperties droolsProperties, RuleLoaderFactory ruleLoaderFactory) {
        this.droolsProperties = droolsProperties;
        this.ruleLoaderFactory = ruleLoaderFactory;
    }

    public KieContainer kieContainer() {
        logger.info("Initializing Drools KieContainer with configurable rule loaders");

        KieRepository kieRepository = kieServices.getRepository();
        KieFileSystem kieFileSystem = kieFileSystem();

        KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
        kieBuilder.buildAll();

        if (kieBuilder.getResults().hasMessages(Message.Level.ERROR)) {
            String errorMessages = kieBuilder.getResults().toString();
            logger.error("Drools build errors: {}", errorMessages);
            throw new RuntimeException("Drools Build Errors:\n" + errorMessages);
        }

        if (kieBuilder.getResults().hasMessages(Message.Level.WARNING)) {
            logger.warn("Drools build warnings: {}", kieBuilder.getResults().toString());
        }

        KieContainer container = kieServices.newKieContainer(kieRepository.getDefaultReleaseId());
        logger.info("KieContainer initialized successfully");

        return container;
    }

    public StatelessKieSession kieSession(KieContainer kieContainer) {
        return kieContainer.newStatelessKieSession();
    }

    private KieFileSystem kieFileSystem() {
        KieFileSystem kieFileSystem = kieServices.newKieFileSystem();

        kieFileSystem.write(ResourceFactory.newClassPathResource("META-INF/kmodule.xml"));

        List<RuleLoader> loaders;
        try {
            loaders = ruleLoaderFactory.createLoaders(droolsProperties);
        } catch (RuleLoadingException e) {
            logger.error("Failed to create rule loaders: {}", e.getMessage(), e);
            if (droolsProperties.isFailFast()) {
                throw new RuntimeException("Failed to create rule loaders", e);
            }
            logger.warn("Continuing without rule loaders due to configuration errors");
            return kieFileSystem;
        }

        int totalRulesLoaded = 0;
        int failedLoaders = 0;

        for (RuleLoader loader : loaders) {
            if (!loader.isEnabled()) {
                logger.debug("Skipping disabled loader: {}", loader.getLoaderType());
                continue;
            }

            try {
                logger.info("Loading rules using {} loader from locations: {}",
                    loader.getLoaderType(), loader.getLocations());

                loader.loadRules(kieFileSystem);
                totalRulesLoaded++;

                logger.info("Successfully loaded rules using {} loader", loader.getLoaderType());

            } catch (IOException | RuleLoadingException e) {
                failedLoaders++;
                logger.error("Failed to load rules using {} loader: {}",
                    loader.getLoaderType(), e.getMessage(), e);

                if (droolsProperties.isFailFast()) {
                    throw new RuntimeException(
                        "Failed to load rules using " + loader.getLoaderType() + " loader", e);
                }
            }
        }

        if (totalRulesLoaded == 0 && failedLoaders > 0) {
            String message = "No rules could be loaded from any configured source";
            logger.error(message);
            throw new RuntimeException(message);
        }

        logger.info("Rule loading completed. Loaded from {} source(s), {} failed",
            totalRulesLoaded, failedLoaders);

        return kieFileSystem;
    }
}
