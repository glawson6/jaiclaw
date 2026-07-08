package io.jaiclaw.blueprints;

import io.jaiclaw.blueprints.BlueprintSlot.SlotType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Load blueprint definitions from YAML — either a single file whose contents
 * are one blueprint, or a directory whose {@code *.yml} / {@code *.yaml} files
 * are each a blueprint.
 *
 * <p>YAML shape:
 * <pre>
 * id: daily-security-audit
 * title: Daily Security Audit
 * description: Scan hosts every morning at 6 AM for CVE regressions.
 * category: devops
 * tags: [security, ops]
 * scheduleTemplate: "0 6 * * *"
 * scheduleHuman: "Daily at 6 AM"
 * promptTemplate: "Run the healthcheck skill against {host_group} and report new findings to {alert_channel}."
 * slots:
 *   - key: host_group
 *     label: Host group
 *     type: TEXT
 *     required: true
 *     description: A hosts.yml group name to audit.
 *   - key: alert_channel
 *     label: Alert channel
 *     type: TEXT
 *     required: true
 *     defaultValue: "#sec-alerts"
 *     description: Where to post the summary.
 * deepLinkPath: /blueprints/daily-security-audit
 * </pre>
 *
 * <p>Missing fields are filled with the record's own compact-constructor
 * defaults. Missing {@code id} is a hard error.
 */
public final class BlueprintYamlLoader {

    private static final Logger log = LoggerFactory.getLogger(BlueprintYamlLoader.class);

    private BlueprintYamlLoader() {}

    /** Load every blueprint YAML under {@code dir}. Non-existent dir → empty list. */
    public static List<BlueprintDefinition> loadDirectory(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) return List.of();
        List<BlueprintDefinition> out = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            var files = stream
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".yml") || name.endsWith(".yaml");
                    })
                    .sorted()
                    .toList();
            for (Path file : files) {
                try {
                    out.add(loadFile(file));
                } catch (RuntimeException e) {
                    log.warn("Skipping unparseable blueprint YAML {}: {}", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    /** Load a single blueprint file. */
    public static BlueprintDefinition loadFile(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return fromMap(new Yaml().load(in));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Convert a parsed YAML map into a {@link BlueprintDefinition}. */
    @SuppressWarnings("unchecked")
    public static BlueprintDefinition fromMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(
                    "Blueprint YAML must be a mapping at the top level, got: "
                            + (raw == null ? "null" : raw.getClass().getSimpleName()));
        }
        Map<String, Object> m = (Map<String, Object>) map;
        List<BlueprintSlot> slots = new ArrayList<>();
        Object rawSlots = m.get("slots");
        if (rawSlots instanceof List<?> list) {
            for (Object s : list) {
                if (s instanceof Map<?, ?> sm) slots.add(slotFromMap((Map<String, Object>) sm));
            }
        }
        return new BlueprintDefinition(
                str(m, "id"),
                str(m, "title"),
                str(m, "description"),
                str(m, "category"),
                strList(m, "tags"),
                str(m, "scheduleTemplate"),
                str(m, "scheduleHuman"),
                str(m, "promptTemplate"),
                slots,
                str(m, "deepLinkPath"));
    }

    private static BlueprintSlot slotFromMap(Map<String, Object> m) {
        String key = str(m, "key");
        String label = str(m, "label");
        SlotType type = SlotType.TEXT;
        Object rawType = m.get("type");
        if (rawType != null) {
            try {
                type = SlotType.valueOf(String.valueOf(rawType).toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Unknown slot type '{}' on slot '{}' — falling back to TEXT", rawType, key);
            }
        }
        boolean required = bool(m, "required", true);
        String defaultValue = str(m, "defaultValue");
        String description = str(m, "description");
        return new BlueprintSlot(key, label, type, required, defaultValue, description);
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }

    @SuppressWarnings("unchecked")
    private static List<String> strList(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof List<?> list) {
            List<String> out = new ArrayList<>(list.size());
            for (Object o : list) if (o != null) out.add(o.toString());
            return List.copyOf(out);
        }
        return List.of();
    }

    private static boolean bool(Map<String, Object> m, String key, boolean fallback) {
        Object v = m.get(key);
        if (v == null) return fallback;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(v.toString());
    }
}
