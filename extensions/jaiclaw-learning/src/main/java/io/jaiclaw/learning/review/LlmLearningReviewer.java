package io.jaiclaw.learning.review;

import io.jaiclaw.core.api.Experimental;
import io.jaiclaw.learning.proposal.MemoryProposal;
import io.jaiclaw.learning.proposal.Proposal;
import io.jaiclaw.learning.proposal.ProposalState;
import io.jaiclaw.learning.proposal.SkillPatchProposal;
import io.jaiclaw.learning.proposal.SkillProposal;
import io.jaiclaw.core.model.MemoryScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Default {@link LearningReviewer}: one bounded, structured-output call on an
 * auxiliary {@link ChatModel}.
 *
 * <p>The prompt lives in {@code prompts/learning-review.md} rather than a string
 * literal, so it can be reviewed and tuned like the product text it is.
 *
 * <p>Everything here is defensive by design. The reviewer is reading a transcript
 * that may contain anything a user typed, and its output is a suggestion that a
 * human will read. So:
 * <ul>
 *   <li>the transcript is truncated head+tail to a character budget;</li>
 *   <li>a malformed or non-JSON reply yields an empty outcome, never an exception;</li>
 *   <li>proposals missing required fields are dropped individually rather than
 *       failing the batch.</li>
 * </ul>
 *
 * <p>Phase 4 of the 1.2.0 plan.
 */
@Experimental
public class LlmLearningReviewer implements LearningReviewer {

    private static final Logger log = LoggerFactory.getLogger(LlmLearningReviewer.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final String PROMPT_RESOURCE = "/prompts/learning-review.md";

    /** Fallback cap when no selectivity level is supplied. */
    private static final int DEFAULT_MAX_PROPOSALS = 5;

    private final ChatModel chatModel;
    private final int maxTranscriptChars;
    private final String promptTemplate;
    private final io.jaiclaw.learning.LearningSelectivity selectivity;

    public LlmLearningReviewer(ChatModel chatModel, int maxTranscriptChars) {
        this(chatModel, maxTranscriptChars, io.jaiclaw.learning.LearningSelectivity.BALANCED);
    }

    public LlmLearningReviewer(ChatModel chatModel, int maxTranscriptChars,
                               io.jaiclaw.learning.LearningSelectivity selectivity) {
        this.chatModel = chatModel;
        this.maxTranscriptChars = Math.max(500, maxTranscriptChars);
        this.selectivity = selectivity == null
                ? io.jaiclaw.learning.LearningSelectivity.BALANCED : selectivity;
        this.promptTemplate = loadTemplate();
    }

    /** Cap on proposals accepted from one review, so a runaway reply cannot flood the queue. */
    private int maxProposals() {
        return selectivity == null ? DEFAULT_MAX_PROPOSALS : selectivity.maxProposalsPerReview();
    }

    @Override
    public ReviewOutcome review(ReviewInput input) {
        if (input == null || input.transcript() == null || input.transcript().isBlank()) {
            return ReviewOutcome.empty();
        }
        String prompt = promptTemplate
                .replace("{{selectivityGuidance}}", selectivity.promptGuidance())
                .replace("{{existingSkills}}", input.existingSkills().isEmpty()
                        ? "(none yet)" : String.join(", ", input.existingSkills()))
                .replace("{{existingMemory}}", blankToNone(input.existingMemory()))
                .replace("{{transcript}}", truncate(input.transcript(), maxTranscriptChars));

        String reply;
        try {
            reply = chatModel.call(new Prompt(prompt)).getResult().getOutput().getText();
        } catch (Exception e) {
            // A failed review is a non-event: the agent keeps working, we try again
            // on the next cadence window.
            log.warn("Learning review call failed for session {}", input.sessionKey(), e);
            return ReviewOutcome.empty();
        }
        return parse(reply, input);
    }

    /** Parses the model's reply, tolerating code fences and surrounding prose. */
    ReviewOutcome parse(String reply, ReviewInput input) {
        String json = extractJson(reply);
        if (json == null) {
            log.debug("Learning review for {} returned no parseable JSON", input.sessionKey());
            return ReviewOutcome.empty();
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (Exception e) {
            log.debug("Learning review for {} returned malformed JSON", input.sessionKey());
            return ReviewOutcome.empty();
        }
        JsonNode array = root.get("proposals");
        if (array == null || !array.isArray()) return ReviewOutcome.empty();

        List<Proposal> proposals = new ArrayList<>();
        for (JsonNode node : array) {
            if (proposals.size() >= maxProposals()) {
                log.debug("Capping learning review at {} proposals ({} selectivity)",
                        maxProposals(), selectivity);
                break;
            }
            Proposal p = toProposal(node, input);
            if (p != null) proposals.add(p);
        }
        return ReviewOutcome.of(proposals);
    }

    private Proposal toProposal(JsonNode n, ReviewInput input) {
        String kind = text(n, "kind");
        if (kind == null) return null;
        String summary = text(n, "summary");
        Instant now = Instant.now();

        return switch (kind.trim().toLowerCase(Locale.ROOT)) {
            case "memory" -> {
                String content = text(n, "content");
                if (isBlank(content)) yield null;
                yield new MemoryProposal(null, input.tenantId(), input.sessionKey(), now,
                        ProposalState.PENDING, orDefault(summary, "memory update"),
                        MemoryScope.AGENT, orDefault(text(n, "heading"), "Learned"), content);
            }
            case "skill" -> {
                String name = text(n, "skillName");
                String body = text(n, "body");
                if (isBlank(name) || isBlank(body)) yield null;
                yield new SkillProposal(null, input.tenantId(), input.sessionKey(), now,
                        ProposalState.PENDING, orDefault(summary, "new skill: " + name),
                        sanitizeSkillName(name), orDefault(text(n, "description"), ""), body);
            }
            case "skill_patch" -> {
                String name = text(n, "skillName");
                String find = text(n, "findText");
                String replace = text(n, "replaceText");
                if (isBlank(name) || isBlank(find) || replace == null) yield null;
                yield new SkillPatchProposal(null, input.tenantId(), input.sessionKey(), now,
                        ProposalState.PENDING, orDefault(summary, "patch skill: " + name),
                        sanitizeSkillName(name), find, replace);
            }
            default -> null;
        };
    }

    /**
     * Pulls the first balanced JSON object out of a reply, so a model that wraps
     * its answer in ```json fences or explanatory prose still parses.
     */
    static String extractJson(String reply) {
        if (reply == null) return null;
        int start = reply.indexOf('{');
        if (start < 0) return null;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < reply.length(); i++) {
            char c = reply.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') inString = true;
            else if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return reply.substring(start, i + 1);
            }
        }
        return null;
    }

    /**
     * Keeps the head and tail of a long transcript. The opening establishes what
     * the user wanted and the closing shows how it resolved; the middle is the
     * most expendable part.
     */
    static String truncate(String transcript, int budget) {
        if (transcript == null || transcript.length() <= budget) return transcript;
        int half = (budget - 40) / 2;
        return transcript.substring(0, half)
                + "\n\n… [transcript truncated] …\n\n"
                + transcript.substring(transcript.length() - half);
    }

    /** Reduces a proposed skill name to a safe kebab-case directory segment. */
    static String sanitizeSkillName(String raw) {
        if (raw == null || raw.isBlank()) return "learned-skill";
        String cleaned = raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        if (cleaned.isBlank()) return "learned-skill";
        return cleaned.length() > 64 ? cleaned.substring(0, 64) : cleaned;
    }

    private String loadTemplate() {
        try (InputStream in = LlmLearningReviewer.class.getResourceAsStream(PROMPT_RESOURCE)) {
            if (in == null) throw new IllegalStateException("Missing " + PROMPT_RESOURCE);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not load " + PROMPT_RESOURCE, e);
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String orDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private static String blankToNone(String s) {
        return isBlank(s) ? "(none yet)" : s;
    }
}
