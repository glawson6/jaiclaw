package io.jclaw.canvas

import io.jclaw.core.tool.ToolResult
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class CanvasToolsSpec extends Specification {

    @TempDir
    Path tempDir

    CanvasService canvasService

    def setup() {
        def config = new CanvasConfig(true, 18793, "127.0.0.1", true)
        def fileManager = new CanvasFileManager(tempDir)
        canvasService = new CanvasService(config, fileManager)
    }

    def "all() returns 3 canvas tools"() {
        when:
        def tools = CanvasTools.all(canvasService)

        then:
        tools.size() == 3
        tools.collect { it.definition().name() }.containsAll(["canvas_present", "canvas_eval", "canvas_snapshot"])
    }

    def "canvas_present tool requires html"() {
        given:
        def tool = CanvasTools.all(canvasService).find { it.definition().name() == "canvas_present" }

        when:
        def result = tool.execute([:], null)

        then:
        result instanceof ToolResult.Error
    }

    def "canvas_present creates content"() {
        given:
        def tool = CanvasTools.all(canvasService).find { it.definition().name() == "canvas_present" }

        when:
        def result = tool.execute([html: "<h1>Test</h1>"], null)

        then:
        result instanceof ToolResult.Success
        ((ToolResult.Success) result).content().contains("Canvas presented at:")
    }

    def "canvas_snapshot returns content after present"() {
        given:
        def presentTool = CanvasTools.all(canvasService).find { it.definition().name() == "canvas_present" }
        def snapshotTool = CanvasTools.all(canvasService).find { it.definition().name() == "canvas_snapshot" }
        presentTool.execute([html: "<h1>Snapshot Test</h1>"], null)

        when:
        def result = snapshotTool.execute([:], null)

        then:
        result instanceof ToolResult.Success
        ((ToolResult.Success) result).content().contains("Snapshot Test")
    }
}
