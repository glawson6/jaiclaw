package io.jaiclaw.examples.codereview;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.OperationContext;

/**
 * Embabel GOAP agent that performs multi-step code review.
 *
 * <p>The GOAP planner automatically chains the two actions:
 * <ol>
 *   <li>analyzeDiff — takes a String (the diff), produces DiffAnalysis</li>
 *   <li>generateReview — takes DiffAnalysis, produces ReviewComplete (goal)</li>
 * </ol>
 */
@Agent(description = "Reviews code diffs and generates structured feedback with issue tracking")
public class CodeReviewAgent {

    @Action(description = "Analyze a code diff for bugs, style issues, and improvements")
    public DiffAnalysis analyzeDiff(String diff, OperationContext context) {
        return context.ai()
                .withDefaultLlm()
                .createObject(
                        "Analyze this code diff for bugs, security issues, style problems, "
                                + "and potential improvements. Return a structured analysis.\n\n"
                                + "Diff:\n" + diff,
                        DiffAnalysis.class
                );
    }

    @Action(description = "Generate a formatted code review from the analysis")
    @AchievesGoal(description = "Code review is complete with actionable feedback")
    public ReviewComplete generateReview(DiffAnalysis analysis, OperationContext context) {
        String review = context.ai()
                .withDefaultLlm()
                .generateText(
                        "Based on this diff analysis, generate a professional code review:\n"
                                + "Summary: " + analysis.summary() + "\n"
                                + "Issues: " + String.join(", ", analysis.issues()) + "\n"
                                + "Suggestions: " + String.join(", ", analysis.suggestions()) + "\n"
                                + "Severity: " + analysis.severity() + "\n\n"
                                + "Format as a clear, actionable review with numbered points."
                );
        boolean approved = "low".equalsIgnoreCase(analysis.severity());
        return new ReviewComplete(review, approved, analysis.issues().size());
    }
}
