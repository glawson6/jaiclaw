package io.jaiclaw.copilot;

/**
 * Known-good GitHub Copilot backend model identifiers.
 *
 * <p>Copilot exposes multiple foundation models under a single auth surface —
 * OpenAI GPT variants, Anthropic Claude variants, Google Gemini, and others.
 * The available matrix is <b>entitlement-driven</b> (varies per user, per
 * account, and over time), so this enum is a <b>convenience</b>, not a gate.
 * Consumers may pass any string via {@link CopilotChatOptions#setModel(String)};
 * the enum simply provides IDE autocomplete + a single source of truth for the
 * documented set.
 *
 * <p>Each constant carries its raw API name via {@link #toApiName()} — this is
 * the string that eventually lands in {@code SessionConfig.setModel(String)}.
 * Copilot's session-scoped model selection means changing model between calls
 * requires a new session (see {@code CopilotSessionPool}); per-call overrides
 * always create a fresh session.
 *
 * <p>The set below reflects the entitled matrix at time of module authorship.
 * New models can be used immediately by passing their raw name directly; the
 * enum will be extended in follow-up releases as new models become widely
 * available.
 */
public enum CopilotModel {

    GPT_4O("gpt-4o"),
    GPT_4O_MINI("gpt-4o-mini"),
    GPT_5("gpt-5"),
    O1_PREVIEW("o1-preview"),
    O1_MINI("o1-mini"),
    CLAUDE_3_5_SONNET("claude-3.5-sonnet"),
    CLAUDE_3_7_SONNET("claude-3.7-sonnet"),
    CLAUDE_SONNET_4_5("claude-sonnet-4.5"),
    GEMINI_2_0_FLASH("gemini-2.0-flash");

    private final String apiName;

    CopilotModel(String apiName) {
        this.apiName = apiName;
    }

    /**
     * Returns the raw model identifier passed to the Copilot SDK's
     * {@code SessionConfig.setModel(String)}. Do not attempt to normalize or
     * canonicalize this string — Copilot's server matches it verbatim against
     * its provider registry.
     */
    public String toApiName() {
        return apiName;
    }

    /**
     * Reverse lookup: returns the enum constant matching {@code apiName}, or
     * {@code null} if the name is not one of the known-good set. Callers should
     * treat a null return as "unknown but possibly valid" and pass the string
     * through to the SDK unchanged.
     */
    public static CopilotModel fromApiName(String apiName) {
        if (apiName == null) {
            return null;
        }
        for (CopilotModel m : values()) {
            if (m.apiName.equals(apiName)) {
                return m;
            }
        }
        return null;
    }
}
