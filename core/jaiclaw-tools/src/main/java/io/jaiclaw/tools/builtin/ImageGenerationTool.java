package io.jaiclaw.tools.builtin;

import io.jaiclaw.core.tool.ToolContext;
import io.jaiclaw.core.tool.ToolDefinition;
import io.jaiclaw.core.tool.ToolProfile;
import io.jaiclaw.core.tool.ToolResult;
import io.jaiclaw.tools.ToolCatalog;
import org.springframework.ai.image.*;

import java.util.Map;
import java.util.Set;

/**
 * Tool that generates images using Spring AI's {@link ImageModel}.
 * Only available when an {@code ImageModel} bean is present (e.g. OpenAI DALL-E).
 */
public class ImageGenerationTool extends AbstractBuiltinTool {

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "prompt": {
                  "type": "string",
                  "description": "Text description of the image to generate"
                },
                "width": {
                  "type": "integer",
                  "description": "Image width in pixels (optional, model-dependent)"
                },
                "height": {
                  "type": "integer",
                  "description": "Image height in pixels (optional, model-dependent)"
                },
                "model": {
                  "type": "string",
                  "description": "Model to use for generation (optional, e.g. dall-e-3)"
                }
              },
              "required": ["prompt"]
            }""";

    private final ImageModel imageModel;

    public ImageGenerationTool(ImageModel imageModel) {
        super(new ToolDefinition(
                "generate_image",
                "Generate an image from a text description. Returns the URL of the generated image.",
                ToolCatalog.SECTION_MEDIA,
                INPUT_SCHEMA,
                Set.of(ToolProfile.FULL)
        ));
        this.imageModel = imageModel;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> parameters, ToolContext context) throws Exception {
        String prompt = requireParam(parameters, "prompt");

        var optionsBuilder = ImageOptionsBuilder.builder();

        if (parameters.containsKey("model")) {
            optionsBuilder.model(parameters.get("model").toString());
        }
        if (parameters.containsKey("width")) {
            optionsBuilder.width(((Number) parameters.get("width")).intValue());
        }
        if (parameters.containsKey("height")) {
            optionsBuilder.height(((Number) parameters.get("height")).intValue());
        }

        ImageResponse response = imageModel.call(new ImagePrompt(prompt, optionsBuilder.build()));
        ImageGeneration result = response.getResult();

        if (result == null || result.getOutput() == null) {
            return new ToolResult.Error("Image generation returned no result");
        }

        Image image = result.getOutput();
        String url = image.getUrl();
        String b64 = image.getB64Json();

        if (url != null && !url.isBlank()) {
            return new ToolResult.Success(
                    "Image generated successfully: " + url,
                    Map.of("imageUrl", url));
        } else if (b64 != null && !b64.isBlank()) {
            return new ToolResult.Success(
                    "Image generated successfully (base64 encoded, " + b64.length() + " chars)",
                    Map.of("imageBase64", b64));
        } else {
            return new ToolResult.Error("Image generation returned empty output");
        }
    }
}
