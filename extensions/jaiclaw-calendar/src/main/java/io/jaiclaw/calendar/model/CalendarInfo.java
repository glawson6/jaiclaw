package io.jaiclaw.calendar.model;

import java.time.Instant;
import java.util.Map;

public record CalendarInfo(
        String id,
        String tenantId,
        String name,
        String description,
        String color,
        boolean visible,
        boolean isDefault,
        String timezone,
        Map<String, Object> metadata,
        Instant createdAt,
        Instant updatedAt
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String tenantId;
        private String name;
        private String description = "";
        private String color = "#667eea";
        private boolean visible = true;
        private boolean isDefault = false;
        private String timezone = "UTC";
        private Map<String, Object> metadata = Map.of();
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(String id) { this.id = id; return this; }
        public Builder tenantId(String tenantId) { this.tenantId = tenantId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder color(String color) { this.color = color; return this; }
        public Builder visible(boolean visible) { this.visible = visible; return this; }
        public Builder isDefault(boolean isDefault) { this.isDefault = isDefault; return this; }
        public Builder timezone(String timezone) { this.timezone = timezone; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public CalendarInfo build() {
            return new CalendarInfo(id, tenantId, name, description, color, visible,
                    isDefault, timezone, metadata, createdAt, updatedAt);
        }
    }
}
