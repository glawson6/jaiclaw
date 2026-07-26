package io.jaiclaw.asciirender.skill;

import java.util.Map;
import java.util.Set;

/**
 * SPI for domain-shape ASCII templates called through the framework's
 * {@code render_response} tool.
 *
 * <p>Every JaiClaw app that wants to render a domain object (event, task,
 * ticket, patient, order, …) as framed monospaced ASCII implements this
 * interface once per shape and registers each implementation as a Spring
 * {@code @Bean}. The framework's {@link RenderableTemplateRegistry} collects
 * them by {@link #name()} and its companion {@code RenderResponseTool}
 * dispatches LLM tool-calls to the right template.
 *
 * <p><strong>Implementation guidance:</strong>
 * <ul>
 *   <li>Names are the tool-call discriminator: lowercase snake_case,
 *       stable across releases (LLMs will call by name in system prompts
 *       + user follow-ups).</li>
 *   <li>Data hydration is the template's job — inject the app's storage
 *       service in the constructor (e.g.
 *       {@code new EventCardTemplate(EventService events)}). The
 *       framework doesn't define a Repository SPI; adopters own their
 *       storage abstraction.</li>
 *   <li>{@link #render(Map)} should <strong>never throw</strong>. Return
 *       an {@code [ ERROR ]}-banner string on bad input so the LLM
 *       always receives something parseable that it can then paste
 *       verbatim (per the object-rendering skill's fidelity rules).</li>
 *   <li>Use {@link SceneSpecHelpers#activeWidth(int, int)} to size the
 *       layout to the deployment's active {@code AsciiRenderProfile} —
 *       adopters flip {@code jaiclaw.ascii.default-profile: telegram_mobile}
 *       to re-route every template's width with no code change.</li>
 * </ul>
 *
 * <p>Companion pieces: {@link RenderableTemplateRegistry} +
 * {@code RenderResponseTool} (in {@code jaiclaw-tools}) +
 * {@code object-rendering} bundled skill (in this module's
 * {@code /skills/object-rendering/SKILL.md}) — the skill ships the
 * LLM-side fidelity rules that make the whole loop reliable.
 */
public interface RenderableTemplate {

    /** Tool-call discriminator. Lowercase snake_case (e.g. {@code event_card}, {@code task_kanban}). */
    String name();

    /** One-sentence description shown to the LLM in the tool's parameter docs. */
    String description();

    /**
     * Names of the params this template understands. Used by the registry
     * to build the union JSON schema exposed to the LLM. A template ignores
     * params it doesn't recognize; the framework doesn't police this.
     */
    Set<String> parameterNames();

    /**
     * Build the framed ASCII output. Implementations should NOT throw —
     * return an {@code [ ERROR ]}-banner string on bad input so the LLM
     * always gets something parseable it can paste verbatim.
     */
    String render(Map<String, Object> params);
}
