package io.jclaw.tools.k8s;

import io.fabric8.kubernetes.api.model.Event;
import io.jclaw.core.tool.ToolContext;
import io.jclaw.core.tool.ToolDefinition;
import io.jclaw.core.tool.ToolProfile;
import io.jclaw.core.tool.ToolResult;
import io.jclaw.tools.ToolCatalog;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lists recent Kubernetes events, optionally filtered by namespace and field selector.
 */
public class ListEventsTool extends AbstractK8sTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "namespace": {
                  "type": "string",
                  "description": "Kubernetes namespace (omit for all namespaces)"
                },
                "fieldSelector": {
                  "type": "string",
                  "description": "Field selector to filter events (e.g. 'type=Warning')"
                }
              },
              "required": []
            }""";

    public ListEventsTool(KubernetesClientProvider clientProvider) {
        super(new ToolDefinition(
                "k8s_list_events",
                "List recent cluster events (warnings, errors). Useful for triaging issues. Optionally filter by namespace and field selector.",
                ToolCatalog.SECTION_KUBERNETES,
                INPUT_SCHEMA,
                Set.of(ToolProfile.FULL)
        ), clientProvider);
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String namespace = optionalParam(parameters, "namespace", null);
        String fieldSelector = optionalParam(parameters, "fieldSelector", null);

        List<Event> events;
        if (namespace != null) {
            if (fieldSelector != null) {
                String[] parts = parseFieldSelector(fieldSelector);
                events = clientProvider.getClient().v1().events().inNamespace(namespace)
                        .withField(parts[0], parts[1]).list().getItems();
            } else {
                events = clientProvider.getClient().v1().events().inNamespace(namespace)
                        .list().getItems();
            }
        } else {
            if (fieldSelector != null) {
                String[] parts = parseFieldSelector(fieldSelector);
                events = clientProvider.getClient().v1().events().inAnyNamespace()
                        .withField(parts[0], parts[1]).list().getItems();
            } else {
                events = clientProvider.getClient().v1().events().inAnyNamespace()
                        .list().getItems();
            }
        }

        // Sort by last timestamp descending
        events.sort(Comparator.comparing(
                (Event e) -> e.getLastTimestamp() != null ? e.getLastTimestamp() : "",
                Comparator.reverseOrder()
        ));

        // Limit to 50 most recent
        List<Event> recent = events.subList(0, Math.min(50, events.size()));

        if (recent.isEmpty()) {
            return new ToolResult.Success("No events found.");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-22s %-8s %-20s %-15s %s%n",
                "LAST SEEN", "TYPE", "OBJECT", "REASON", "MESSAGE"));
        sb.append("-".repeat(100)).append('\n');

        for (Event event : recent) {
            String lastSeen = event.getLastTimestamp() != null ? event.getLastTimestamp() : "N/A";
            String type = event.getType() != null ? event.getType() : "Normal";
            String object = event.getInvolvedObject() != null
                    ? event.getInvolvedObject().getKind() + "/" + event.getInvolvedObject().getName()
                    : "N/A";
            String reason = event.getReason() != null ? event.getReason() : "N/A";
            String message = event.getMessage() != null ? event.getMessage() : "";

            sb.append(String.format("%-22s %-8s %-20s %-15s %s%n",
                    truncate(lastSeen, 22), type, truncate(object, 20),
                    truncate(reason, 15), truncate(message, 80)));
        }

        return new ToolResult.Success(sb.toString());
    }

    private String[] parseFieldSelector(String selector) {
        String[] parts = selector.split("=", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "Field selector must be in 'key=value' format. Got: " + selector);
        }
        return parts;
    }

    private String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 2) + "..";
    }
}
