package io.jclaw.examples.content;

import io.jclaw.core.tool.ToolCallback;
import io.jclaw.core.tool.ToolContext;
import io.jclaw.core.tool.ToolDefinition;
import io.jclaw.core.tool.ToolResult;
import io.jclaw.plugin.JClawPlugin;
import io.jclaw.plugin.PluginApi;
import io.jclaw.core.plugin.PluginDefinition;
import io.jclaw.core.plugin.PluginKind;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Plugin that registers content analysis tools for multi-modal processing.
 * Demonstrates the JClawPlugin SPI with multiple tool registrations.
 */
@Component
public class ContentAnalysisPlugin implements JClawPlugin {

    @Override
    public PluginDefinition definition() {
        return new PluginDefinition(
                "content-analysis",
                "Content Analysis Plugin",
                "Multi-modal content analysis — images, audio, and documents",
                "1.0.0",
                PluginKind.GENERAL
        );
    }

    @Override
    public void register(PluginApi api) {
        api.registerTool(new AnalyzeImageTool());
        api.registerTool(new ExtractMetadataTool());
    }

    static class AnalyzeImageTool implements ToolCallback {

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "analyze_image",
                    "Analyze an image and extract description, objects, and text (OCR)",
                    "content",
                    """
                    {
                      "type": "object",
                      "properties": {
                        "image_url": { "type": "string", "description": "URL or path to the image" },
                        "analysis_type": { "type": "string", "description": "Type: description, ocr, objects, or all", "default": "all" }
                      },
                      "required": ["image_url"]
                    }
                    """
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            String imageUrl = (String) parameters.get("image_url");
            String analysisType = (String) parameters.getOrDefault("analysis_type", "all");

            // Simulated — in production, use MediaAnalysisProvider SPI
            String result = String.format("""
                    {
                      "image_url": "%s",
                      "analysis_type": "%s",
                      "description": "A product photo showing a laptop on a wooden desk",
                      "objects": ["laptop", "desk", "coffee cup", "notebook"],
                      "text_detected": "MacBook Pro — Apple",
                      "dimensions": "1920x1080",
                      "format": "JPEG"
                    }""", imageUrl, analysisType);
            return new ToolResult.Success(result);
        }
    }

    static class ExtractMetadataTool implements ToolCallback {

        @Override
        public ToolDefinition definition() {
            return new ToolDefinition(
                    "extract_metadata",
                    "Extract structured metadata from a document or media file",
                    "content",
                    """
                    {
                      "type": "object",
                      "properties": {
                        "file_url": { "type": "string", "description": "URL or path to the file" },
                        "file_type": { "type": "string", "description": "File type: pdf, image, audio, html" }
                      },
                      "required": ["file_url", "file_type"]
                    }
                    """
            );
        }

        @Override
        public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
            String fileUrl = (String) parameters.get("file_url");
            String fileType = (String) parameters.get("file_type");
            String metadataId = UUID.randomUUID().toString().substring(0, 8);

            String result = String.format("""
                    {
                      "id": "%s",
                      "file_url": "%s",
                      "file_type": "%s",
                      "title": "Extracted Document Title",
                      "author": "Unknown",
                      "created_date": "2024-01-15",
                      "page_count": 12,
                      "word_count": 3500,
                      "language": "en",
                      "tags": ["report", "quarterly", "finance"]
                    }""", metadataId, fileUrl, fileType);
            return new ToolResult.Success(result);
        }
    }
}
