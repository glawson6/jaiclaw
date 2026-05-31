package io.jaiclaw.video;

import io.jaiclaw.core.tool.ToolCallback;
import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * LLM tool for submitting and polling video generation jobs.
 *
 * <p>Supports two operations:
 * <ul>
 *   <li>{@code action=generate} — submit a new video generation job</li>
 *   <li>{@code action=status} — poll the status of an existing job</li>
 * </ul>
 */
public class VideoGenerationTool implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(VideoGenerationTool.class);

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "action": {"type": "string", "enum": ["generate", "status"], "description": "generate=start new job, status=poll existing job"},
                "prompt": {"type": "string", "description": "Text description of the video to generate (required for generate)"},
                "image_url": {"type": "string", "description": "Reference image URL for image-to-video (optional)"},
                "duration": {"type": "integer", "description": "Video duration in seconds, default 5"},
                "resolution": {"type": "string", "description": "Output resolution, default 1280x768"},
                "provider": {"type": "string", "description": "Provider ID (optional, defaults to configured provider)"},
                "job_id": {"type": "string", "description": "Job ID to check status of (required for status)"}
              },
              "required": ["action"]
            }""";

    private final VideoGenerationRegistry registry;
    private final String defaultProvider;

    public VideoGenerationTool(VideoGenerationRegistry registry, String defaultProvider) {
        this.registry = registry;
        this.defaultProvider = defaultProvider;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                "video_generate",
                "Generate a video from a text prompt or check the status of a video generation job. " +
                        "Use action='generate' with a prompt to start a new job, or action='status' with a job_id to check progress.",
                "media",
                INPUT_SCHEMA);
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        String action = (String) parameters.getOrDefault("action", "generate");
        String provider = (String) parameters.getOrDefault("provider", defaultProvider);

        return switch (action) {
            case "generate" -> executeGenerate(parameters, provider);
            case "status" -> executeStatus(parameters, provider);
            default -> new ToolResult.Error("Unknown action: " + action + ". Use 'generate' or 'status'.");
        };
    }

    private ToolResult executeGenerate(Map<String, Object> params, String providerId) {
        String prompt = (String) params.get("prompt");
        if (prompt == null || prompt.isBlank()) {
            return new ToolResult.Error("Missing required parameter: prompt");
        }

        String imageUrl = (String) params.get("image_url");
        int duration = params.containsKey("duration")
                ? ((Number) params.get("duration")).intValue()
                : 5;
        String resolution = (String) params.getOrDefault("resolution", "1280x768");

        VideoGenerationRequest request = VideoGenerationRequest.builder()
                .prompt(prompt)
                .imageUrl(imageUrl)
                .durationSecs(duration)
                .resolution(resolution)
                .build();

        VideoGenerationResult result = registry.submit(providerId, request);

        if (result.status() == VideoJobStatus.FAILED) {
            return new ToolResult.Error("Video generation failed: " + result.error());
        }

        return new ToolResult.Success(
                "Video generation job submitted. Job ID: " + result.jobId() +
                        " (provider: " + providerId + "). " +
                        "Use action='status' with this job_id to check progress.");
    }

    private ToolResult executeStatus(Map<String, Object> params, String providerId) {
        String jobId = (String) params.get("job_id");
        if (jobId == null || jobId.isBlank()) {
            return new ToolResult.Error("Missing required parameter: job_id");
        }

        VideoGenerationResult result = registry.poll(providerId, jobId);

        return switch (result.status()) {
            case QUEUED -> new ToolResult.Success("Job " + jobId + " is queued. Please check again shortly.");
            case PROCESSING -> new ToolResult.Success("Job " + jobId + " is processing (" + result.progress() + "% complete).");
            case COMPLETED -> new ToolResult.Success("Video ready! URL: " + result.videoUrl());
            case FAILED -> new ToolResult.Error("Video generation failed: " + result.error());
        };
    }
}
