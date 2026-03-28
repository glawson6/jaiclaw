package io.jaiclaw.core.hook;

/**
 * A registered hook handler with priority and source metadata.
 */
public record HookRegistration<E, C>(
        String pluginId,
        HookName hookName,
        HookHandler<E, C> handler,
        int priority,
        String source
) {
    public static final int DEFAULT_PRIORITY = 100;

    public HookRegistration(String pluginId, HookName hookName, HookHandler<E, C> handler) {
        this(pluginId, hookName, handler, DEFAULT_PRIORITY, pluginId);
    }

    public static <E, C> Builder<E, C> builder() { return new Builder<>(); }

    public static final class Builder<E, C> {
        private String pluginId;
        private HookName hookName;
        private HookHandler<E, C> handler;
        private int priority;
        private String source;

        public Builder<E, C> pluginId(String pluginId) { this.pluginId = pluginId; return this; }
        public Builder<E, C> hookName(HookName hookName) { this.hookName = hookName; return this; }
        public Builder<E, C> handler(HookHandler<E, C> handler) { this.handler = handler; return this; }
        public Builder<E, C> priority(int priority) { this.priority = priority; return this; }
        public Builder<E, C> source(String source) { this.source = source; return this; }

        public HookRegistration<E, C> build() {
            return new HookRegistration<>(pluginId, hookName, handler, priority, source);
        }
    }
}
