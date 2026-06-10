package io.jaiclaw.examples.helloworld;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.Set;

/**
 * Wires the example's one custom tool.
 *
 * <p>JaiClaw prefers {@code @Configuration} over {@code @Component} for tool
 * registration — the bean wiring is explicit and visible in one place, which
 * makes overrides and testing easier than scattering {@code @Component}
 * annotations across tool classes.
 */
@Configuration
public class HelloWorldConfig {

    /**
     * The smallest possible {@link ToolCallback}: takes a {@code text}
     * parameter and returns it. Demonstrates the full tool authoring shape
     * without any external dependency.
     */
    @Bean
    public ToolCallback echoTool() {
        return new ToolCallback() {
            @Override
            public ToolDefinition definition() {
                return ToolDefinition.builder()
                        .name("echo")
                        .description("Echo back the supplied text. Use this to demonstrate tool calls.")
                        .section("hello-world")
                        .inputSchema("""
                                {
                                  "type": "object",
                                  "properties": {
                                    "text": {
                                      "type": "string",
                                      "description": "The text to echo back."
                                    }
                                  },
                                  "required": ["text"]
                                }""")
                        .profiles(Set.of(ToolProfile.FULL))
                        .build();
            }

            @Override
            public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
                Object text = parameters.get("text");
                if (text == null) {
                    return new ToolResult.Error("missing required parameter: text");
                }
                return new ToolResult.Success(String.valueOf(text));
            }
        };
    }
}
