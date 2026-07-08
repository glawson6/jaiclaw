package io.jaiclaw.blueprints;

/**
 * A single user-fillable slot in a {@link BlueprintDefinition}. Slots are
 * the parameterized parts of the blueprint's schedule and prompt templates
 * (e.g. "9" in "9 AM", "Monday" in "every Monday", "product-launch" in
 * "watch for mentions of product-launch").
 *
 * <p>Every renderer (dashboard form, CLI slash-command, docs card, agent
 * seed prompt) uses this description to know how to present the slot to
 * its audience: a form field label, a slash-command argument name, a
 * cards' inline help, or a question the agent asks the user when the slot
 * is left blank.
 *
 * @param key         short identifier the templates reference (e.g. "hour")
 * @param label       human-facing label for forms + slash-command help
 * @param type        semantic type — the enum values map to concrete
 *                    validation and UI widgets
 * @param required    whether the blueprint cannot be scheduled without a
 *                    value for this slot
 * @param defaultValue default filled into the form / offered as the CLI
 *                    default (null means no default)
 * @param description longer help text shown in the docs card + as a
 *                    tooltip in the dashboard form
 */
public record BlueprintSlot(
        String key,
        String label,
        SlotType type,
        boolean required,
        String defaultValue,
        String description
) {

    public BlueprintSlot {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("slot.key must not be blank");
        }
        if (label == null || label.isBlank()) label = key;
        if (type == null) type = SlotType.TEXT;
        if (description == null) description = "";
    }

    /** Simple text slot; the caller supplies any string. */
    public static BlueprintSlot text(String key, String label, String defaultValue, String description) {
        return new BlueprintSlot(key, label, SlotType.TEXT, defaultValue == null, defaultValue, description);
    }

    /** Integer slot with optional default. Callers should validate the input themselves. */
    public static BlueprintSlot integer(String key, String label, Integer defaultValue, String description) {
        return new BlueprintSlot(key, label, SlotType.INTEGER,
                defaultValue == null, defaultValue == null ? null : defaultValue.toString(), description);
    }

    /**
     * Renderers pick a UI widget based on this. Blueprint authors don't have
     * to enumerate their concerns; adding a type later is a compatible change
     * for existing blueprints because unknown types just fall back to TEXT.
     */
    public enum SlotType {
        /** Free-form string. */
        TEXT,
        /** Integer, positive or negative. */
        INTEGER,
        /** 0–23 hour. Dashboards render as a time picker. */
        HOUR_OF_DAY,
        /** 0–59 minute. */
        MINUTE_OF_HOUR,
        /** Monday…Sunday. */
        DAY_OF_WEEK,
        /** Multi-select day set (dashboards render as checkboxes). */
        DAY_OF_WEEK_SET,
        /** ISO 8601 duration. */
        DURATION,
        /** Fixed-choice enum; the {@link BlueprintSlot#description} names the choices. */
        CHOICE,
        /** URL string. Dashboards render as a URL input. */
        URL
    }
}
