package io.jclaw.examples.compliance;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.OperationContext;

/**
 * Embabel GOAP agent for document compliance checking.
 *
 * <p>The planner chains two actions:
 * <ol>
 *   <li>extractPolicy — takes a String (policy text), produces PolicyDocument</li>
 *   <li>checkCompliance — takes PolicyDocument + String (document to check), produces ComplianceReport (goal)</li>
 * </ol>
 *
 * <p>In production, the input would come from jclaw-documents (PDF/HTML parsing).
 */
@Agent(description = "Checks documents against compliance policies and generates audit reports")
public class ComplianceAgent {

    @Action(description = "Extract compliance rules from a policy document")
    public PolicyDocument extractPolicy(String policyText, OperationContext context) {
        return context.ai()
                .withDefaultLlm()
                .createObject(
                        "Extract compliance rules from this policy document. "
                                + "Identify the policy name, all specific rules/requirements, "
                                + "and which sections must be present in compliant documents.\n\n"
                                + "Policy document:\n" + policyText,
                        PolicyDocument.class
                );
    }

    @Action(description = "Check a document against extracted compliance rules and generate a report")
    @AchievesGoal(description = "Document compliance has been verified with a detailed report")
    public ComplianceReport checkCompliance(PolicyDocument policy, OperationContext context) {
        return context.ai()
                .withDefaultLlm()
                .createObject(
                        "Check a document for compliance against these rules:\n"
                                + "Policy: " + policy.policyName() + "\n"
                                + "Rules: " + String.join("; ", policy.rules()) + "\n"
                                + "Required sections: " + String.join(", ", policy.requiredSections()) + "\n\n"
                                + "Simulate checking a sample document. Generate realistic findings "
                                + "including any violations, warnings, and a compliance score (0-100).",
                        ComplianceReport.class
                );
    }
}
