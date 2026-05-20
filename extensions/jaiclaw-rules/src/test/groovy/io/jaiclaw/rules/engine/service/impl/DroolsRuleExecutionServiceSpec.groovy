package io.jaiclaw.rules.engine.service.impl

import io.jaiclaw.rules.engine.model.RuleExecutionRequest
import io.jaiclaw.rules.engine.model.RuleExecutionResponse
import org.kie.api.KieServices
import org.kie.api.builder.KieBuilder
import org.kie.api.builder.KieFileSystem
import org.kie.api.builder.Message
import org.kie.api.runtime.KieContainer
import org.kie.api.runtime.StatelessKieSession
import org.kie.internal.io.ResourceFactory
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import spock.lang.Specification

/**
 * Spock specification for DroolsRuleExecutionService.
 * Tests the integration between Drools engine and the service layer.
 */
class DroolsRuleExecutionServiceSpec extends Specification {

    DroolsRuleExecutionService service
    KieContainer kieContainer
    StatelessKieSession kieSession

    def setup() {
        // Manually create KieContainer with rules loaded from classpath
        kieContainer = createKieContainerWithRules()
        kieSession = kieContainer.newStatelessKieSession()
        service = new DroolsRuleExecutionService(kieSession)
    }

    /**
     * Creates a KieContainer with all rules loaded from the classpath.
     * This method replicates what DroolsConfig does but without requiring Spring dependencies.
     */
    private KieContainer createKieContainerWithRules() {
        KieServices kieServices = KieServices.Factory.get()
        KieFileSystem kieFileSystem = kieServices.newKieFileSystem()

        // Load kmodule.xml
        kieFileSystem.write(ResourceFactory.newClassPathResource("META-INF/kmodule.xml"))

        // Load all .drl files from classpath
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver()
        Resource[] resources = resolver.getResources("classpath*:rules/**/*.drl")

        for (Resource resource : resources) {
            String path = "src/main/resources/" + resource.getFilename()
            kieFileSystem.write(path, ResourceFactory.newInputStreamResource(resource.getInputStream()))
        }

        // Build the KieContainer
        KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem)
        kieBuilder.buildAll()

        if (kieBuilder.getResults().hasMessages(Message.Level.ERROR)) {
            throw new RuntimeException("Drools Build Errors: " + kieBuilder.getResults().toString())
        }

        return kieServices.newKieContainer(kieServices.getRepository().getDefaultReleaseId())
    }

    def "should list available rules"() {
        when: "listing available rules"
        def rules = service.listAvailableRules()

        then: "should return all configured rules"
        rules != null
        rules.size() == 4
        rules.contains("text-analysis")
        rules.contains("decision-making")
        rules.contains("decision")
        rules.contains("validation")
    }

    def "should check if rule is available"() {
        expect: "valid rules to be available"
        service.isRuleAvailable("text-analysis") == true
        service.isRuleAvailable("decision-making") == true
        service.isRuleAvailable("decision") == true
        service.isRuleAvailable("validation") == true

        and: "invalid rules to not be available"
        service.isRuleAvailable("unknown-rule") == false
        service.isRuleAvailable("") == false
    }

    def "should execute text analysis rule with positive sentiment"() {
        given: "a request with positive text"
        def request = RuleExecutionRequest.builder()
            .ruleName("text-analysis")
            .facts([text: "This is a great and amazing product!"])
            .build()

        when: "executing the rule"
        RuleExecutionResponse response = service.executeRule(request)

        then: "should detect positive sentiment"
        response != null
        response.success
        response.results.sentiment == "positive"
    }

    def "should execute text analysis rule with negative sentiment"() {
        given: "a request with negative text"
        def request = RuleExecutionRequest.builder()
            .ruleName("text-analysis")
            .facts([text: "This is terrible and awful!"])
            .build()

        when: "executing the rule"
        RuleExecutionResponse response = service.executeRule(request)

        then: "should detect negative sentiment"
        response != null
        response.success
        response.results.sentiment == "negative"
    }

    def "should execute text analysis rule with neutral sentiment"() {
        given: "a request with neutral text"
        def request = RuleExecutionRequest.builder()
            .ruleName("text-analysis")
            .facts([text: "This is a product."])
            .build()

        when: "executing the rule"
        RuleExecutionResponse response = service.executeRule(request)

        then: "should detect neutral sentiment"
        response != null
        response.success
        response.results.sentiment == "neutral"
    }

    def "should extract technology keywords"() {
        given: "a request with technical text"
        def request = RuleExecutionRequest.builder()
            .ruleName("text-analysis")
            .facts([text: "We are using AI and machine learning with kubernetes."])
            .build()

        when: "executing the rule"
        RuleExecutionResponse response = service.executeRule(request)

        then: "should extract technology keywords"
        response != null
        response.success
        response.results.keywords.contains("technology")
        response.results.categories.contains("Technical Content")
    }

    def "should execute decision rule with high priority"() {
        given: "a request with high urgency"
        def request = RuleExecutionRequest.builder()
            .ruleName("decision-making")
            .facts([parameters: [urgency: "high", type: "technical"]])
            .build()

        when: "executing the rule"
        RuleExecutionResponse response = service.executeRule(request)

        then: "should assign high priority and escalate"
        response != null
        response.success
        response.results.priority == 1
        response.results.decision == "ESCALATE"
        response.results.recommendation != null
    }

    def "should execute decision rule for auto-approval"() {
        given: "a request with small amount"
        def request = RuleExecutionRequest.builder()
            .ruleName("decision-making")
            .facts([parameters: [amount: "50"]])
            .build()

        when: "executing the rule"
        RuleExecutionResponse response = service.executeRule(request)

        then: "should auto-approve"
        response != null
        response.success
        response.results.decision == "APPROVED"
        response.results.recommendation.contains("Auto-approved")
    }

    def "should execute decision rule requiring manager approval"() {
        given: "a request with large amount"
        def request = RuleExecutionRequest.builder()
            .ruleName("decision-making")
            .facts([parameters: [amount: "500"]])
            .build()

        when: "executing the rule"
        RuleExecutionResponse response = service.executeRule(request)

        then: "should require manager approval"
        response != null
        response.success
        response.results.decision == "PENDING_APPROVAL"
        response.results.recommendation.contains("Manager approval required")
    }

    def "should execute validation rule with valid data"() {
        given: "a request with valid data"
        def request = RuleExecutionRequest.builder()
            .ruleName("validation")
            .facts([data: [
                email: "user@example.com",
                name: "John Doe",
                age: "25"
            ]])
            .build()

        when: "executing the rule"
        RuleExecutionResponse response = service.executeRule(request)

        then: "should pass validation with no errors"
        response != null
        response.success
        response.results.valid == true
        response.results.errors == null || response.results.errors.isEmpty()
    }

    def "should execute validation rule with missing email"() {
        given: "a request with missing email"
        def request = RuleExecutionRequest.builder()
            .ruleName("validation")
            .facts([data: [name: "John"]])
            .build()

        when: "executing the rule"
        RuleExecutionResponse response = service.executeRule(request)

        then: "should fail validation"
        response != null
        response.success
        response.results.valid == false
        response.results.errors.contains("Email is required")
    }

    def "should execute validation rule with invalid email format"() {
        given: "a request with invalid email"
        def request = RuleExecutionRequest.builder()
            .ruleName("validation")
            .facts([data: [
                email: "invalid-email",
                name: "John",
                age: "25"
            ]])
            .build()

        when: "executing the rule"
        RuleExecutionResponse response = service.executeRule(request)

        then: "should fail validation"
        response != null
        response.success
        response.results.valid == false
        response.results.errors.contains("Email format is invalid")
    }

    def "should execute validation rule with invalid age"() {
        given: "a request with invalid age"
        def request = RuleExecutionRequest.builder()
            .ruleName("validation")
            .facts([data: [
                email: "user@example.com",
                name: "John",
                age: "15"
            ]])
            .build()

        when: "executing the rule"
        RuleExecutionResponse response = service.executeRule(request)

        then: "should fail validation"
        response != null
        response.success
        response.results.valid == false
        response.results.errors.contains("Age must be between 18 and 120")
    }

    def "should handle unknown rule name"() {
        given: "a request with unknown rule"
        def request = RuleExecutionRequest.builder()
            .ruleName("unknown-rule")
            .facts([:])
            .build()

        when: "executing the rule"
        RuleExecutionResponse response = service.executeRule(request)

        then: "should return error response"
        response != null
        !response.success
        response.error != null
    }

    def "should include trace information when enabled"() {
        given: "a request with trace enabled"
        def request = RuleExecutionRequest.builder()
            .ruleName("text-analysis")
            .facts([text: "Sample text"])
            .enableTrace(true)
            .build()

        when: "executing the rule"
        RuleExecutionResponse response = service.executeRule(request)

        then: "should include trace information"
        response != null
        response.success
        response.trace != null
        !response.trace.isEmpty()
    }

    def "should not include trace information when disabled"() {
        given: "a request with trace disabled"
        def request = RuleExecutionRequest.builder()
            .ruleName("text-analysis")
            .facts([text: "Sample text"])
            .enableTrace(false)
            .build()

        when: "executing the rule"
        RuleExecutionResponse response = service.executeRule(request)

        then: "should not include trace information"
        response != null
        response.success
        response.trace == null
    }
}
