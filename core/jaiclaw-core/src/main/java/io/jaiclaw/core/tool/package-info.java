/**
 * Tool SPI — the core interface tool authors implement, plus the
 * value types that flow through it.
 *
 * <p>{@link io.jaiclaw.core.tool.ToolCallback},
 * {@link io.jaiclaw.core.tool.ToolDefinition},
 * {@link io.jaiclaw.core.tool.ToolContext}, and
 * {@link io.jaiclaw.core.tool.ToolResult} are stable surfaces under
 * the 0.8.0 stability program (audit §3.5 / P3.5). The package is
 * {@link org.jspecify.annotations.NullMarked} — every method return
 * is non-null unless explicitly annotated
 * {@link org.jspecify.annotations.Nullable}.
 */
@org.jspecify.annotations.NullMarked
package io.jaiclaw.core.tool;
