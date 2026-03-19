package io.jclaw.core.model;

import java.util.Map;

/**
 * Action to perform on the canvas (rich visual output).
 *
 * @param type   action type: "present", "hide", "navigate", "eval", "snapshot"
 * @param params action-specific parameters
 */
public record CanvasAction(String type, Map<String, Object> params) {
    public CanvasAction {
        if (type == null) type = "present";
        if (params == null) params = Map.of();
    }
}
