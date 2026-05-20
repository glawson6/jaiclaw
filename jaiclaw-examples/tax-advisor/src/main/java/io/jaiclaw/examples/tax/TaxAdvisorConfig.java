package io.jaiclaw.examples.tax;

import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.StatelessKieSession;
import org.kie.internal.io.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates a dedicated StatelessKieSession that includes tax rules.
 * <p>
 * The default kmodule.xml in jaiclaw-rules only loads {@code packages="rules"},
 * which excludes tax DRLs (they declare {@code package io.jaiclaw.rules.tax}).
 * This config overrides the kmodule with one that includes both packages.
 */
@Configuration
public class TaxAdvisorConfig {

    private static final Logger log = LoggerFactory.getLogger(TaxAdvisorConfig.class);

    private static final String[] TAX_RULE_FILES = {
            "rules/tax/2026/standard-deductions-2026.drl",
            "rules/tax/2026/tax-brackets-2026.drl",
            "rules/tax/2026/child-tax-credit-2026.drl",
            "rules/tax/2026/earned-income-credit-2026.drl",
            "rules/tax/2026/alternative-minimum-tax-2026.drl",
            "rules/tax/2026/other-credits-2026.drl"
    };

    private static final String[] VALIDATION_RULE_FILES = {
            "rules/validation-rules.drl"
    };

    /**
     * Tax-specific KieSession that loads all tax DRL files plus validation rules.
     * Named bean so it doesn't conflict with the default auto-configured session.
     */
    @Bean("taxKieSession")
    public StatelessKieSession taxKieSession() {
        KieServices kieServices = KieServices.Factory.get();
        KieFileSystem kfs = kieServices.newKieFileSystem();

        // Write the extended kmodule that includes the tax package
        kfs.write(ResourceFactory.newClassPathResource("META-INF/kmodule.xml"));

        // Load tax rules
        for (String ruleFile : TAX_RULE_FILES) {
            log.info("Loading tax rule: {}", ruleFile);
            kfs.write(ResourceFactory.newClassPathResource(ruleFile));
        }

        // Load validation rules (for input validation)
        for (String ruleFile : VALIDATION_RULE_FILES) {
            log.info("Loading validation rule: {}", ruleFile);
            kfs.write(ResourceFactory.newClassPathResource(ruleFile));
        }

        KieBuilder kieBuilder = kieServices.newKieBuilder(kfs);
        kieBuilder.buildAll();

        if (kieBuilder.getResults().hasMessages(Message.Level.ERROR)) {
            String errors = kieBuilder.getResults().toString();
            log.error("Tax rules build errors: {}", errors);
            throw new RuntimeException("Tax rules build errors:\n" + errors);
        }

        KieContainer container = kieServices.newKieContainer(
                kieServices.getRepository().getDefaultReleaseId());

        log.info("Tax KieContainer initialized with {} tax rules and {} validation rules",
                TAX_RULE_FILES.length, VALIDATION_RULE_FILES.length);

        return container.newStatelessKieSession();
    }
}
