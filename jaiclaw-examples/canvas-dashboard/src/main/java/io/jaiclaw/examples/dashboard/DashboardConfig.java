package io.jaiclaw.examples.dashboard;

import io.jaiclaw.canvas.CanvasConfig;
import io.jaiclaw.canvas.CanvasFileManager;
import io.jaiclaw.canvas.CanvasService;
import io.jaiclaw.canvas.CanvasTools;
import io.jaiclaw.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires canvas beans (no auto-config exists) and registers all tools into the ToolRegistry.
 */
@Configuration
public class DashboardConfig {

    private static final Logger log = LoggerFactory.getLogger(DashboardConfig.class);

    @Bean
    CanvasFileManager canvasFileManager() {
        return new CanvasFileManager();
    }

    @Bean
    CanvasService canvasService(CanvasFileManager fileManager) {
        return new CanvasService(CanvasConfig.DEFAULT, fileManager);
    }

    @Bean
    ApplicationRunner registerDashboardTools(
            ToolRegistry toolRegistry,
            CanvasService canvasService,
            @Value("${spring.ai.model.chat:anthropic}") String aiProvider,
            @Value("${spring.ai.anthropic.chat.options.model:claude-sonnet-4-5}") String anthropicModel,
            @Value("${spring.ai.minimax.chat.options.model:M2-her}") String minimaxModel) {
        return args -> {
            toolRegistry.registerAll(CanvasTools.all(canvasService));
            toolRegistry.register(new SystemMetricsTool());
            toolRegistry.register(new ProjectStatusTool());

            String model = switch (aiProvider) {
                case "minimax" -> minimaxModel;
                case "anthropic" -> anthropicModel;
                default -> aiProvider;
            };
            log.info("Canvas Dashboard started — provider={}, model={}", aiProvider, model);
        };
    }
}
