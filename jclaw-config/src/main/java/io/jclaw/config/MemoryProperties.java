package io.jclaw.config;

public record MemoryProperties(
        String backend,
        String provider,
        String model
) {
    public static final MemoryProperties DEFAULT = new MemoryProperties(
            "builtin", "openai", null
    );
}
