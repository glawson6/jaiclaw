package io.jaiclaw.modelcatalog;

/**
 * Capabilities that a model may support.
 */
public enum ModelCapability {
    CHAT,
    VISION,
    TOOL_CALLING,
    JSON_MODE,
    STREAMING,
    CODE,
    REASONING,
    LONG_CONTEXT,
    IMAGE_GENERATION,
    EMBEDDINGS
}
