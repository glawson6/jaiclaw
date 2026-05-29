package io.jaiclaw.tools.builtin

import io.jaiclaw.core.tool.ToolContext
import io.jaiclaw.core.tool.ToolProfile
import io.jaiclaw.core.tool.ToolResult
import org.springframework.ai.image.*
import spock.lang.Specification

class ImageGenerationToolSpec extends Specification {

    ToolContext context = new ToolContext("agent", "session", "sid", ".")

    def "tool name is generate_image"() {
        given:
        def imageModel = Stub(ImageModel)
        def tool = new ImageGenerationTool(imageModel)

        expect:
        tool.definition().name() == "generate_image"
    }

    def "tool is only available in FULL profile"() {
        given:
        def imageModel = Stub(ImageModel)
        def tool = new ImageGenerationTool(imageModel)

        expect:
        tool.definition().isAvailableIn(ToolProfile.FULL)
        !tool.definition().isAvailableIn(ToolProfile.CODING)
    }

    def "generates image and returns URL"() {
        given:
        def image = new Image("https://cdn.openai.com/image.png", null)
        def generation = new ImageGeneration(image)
        def response = new ImageResponse(List.of(generation))

        def imageModel = Stub(ImageModel) {
            call(_) >> response
        }
        def tool = new ImageGenerationTool(imageModel)

        when:
        def result = tool.execute(Map.of("prompt", "a sunset over mountains"), context)

        then:
        result instanceof ToolResult.Success
        def success = result as ToolResult.Success
        success.content().contains("https://cdn.openai.com/image.png")
        success.metadata().get("imageUrl") == "https://cdn.openai.com/image.png"
    }

    def "generates image and returns base64 when no URL"() {
        given:
        def image = new Image(null, "iVBORw0KGgoAAAANSUhEUg...")
        def generation = new ImageGeneration(image)
        def response = new ImageResponse(List.of(generation))

        def imageModel = Stub(ImageModel) {
            call(_) >> response
        }
        def tool = new ImageGenerationTool(imageModel)

        when:
        def result = tool.execute(Map.of("prompt", "a cat"), context)

        then:
        result instanceof ToolResult.Success
        def success = result as ToolResult.Success
        success.content().contains("base64")
        success.metadata().containsKey("imageBase64")
    }

    def "returns error when no result from model"() {
        given:
        def response = new ImageResponse(List.of())

        def imageModel = Stub(ImageModel) {
            call(_) >> response
        }
        def tool = new ImageGenerationTool(imageModel)

        when:
        def result = tool.execute(Map.of("prompt", "anything"), context)

        then:
        result instanceof ToolResult.Error
    }

    def "requires prompt parameter"() {
        given:
        def imageModel = Stub(ImageModel)
        def tool = new ImageGenerationTool(imageModel)

        when:
        def result = tool.execute(Map.of(), context)

        then:
        result instanceof ToolResult.Error
        (result as ToolResult.Error).message().contains("prompt")
    }

    def "passes optional parameters to ImageModel"() {
        given:
        ImagePrompt capturedPrompt = null
        def image = new Image("https://example.com/image.png", null)
        def response = new ImageResponse(List.of(new ImageGeneration(image)))

        def imageModel = Stub(ImageModel) {
            call(_) >> { ImagePrompt prompt ->
                capturedPrompt = prompt
                response
            }
        }
        def tool = new ImageGenerationTool(imageModel)

        when:
        tool.execute(Map.of(
            "prompt", "a dog",
            "model", "dall-e-3",
            "width", 1024,
            "height", 1024
        ), context)

        then:
        capturedPrompt != null
    }
}
