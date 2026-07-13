package io.jaiclaw.video

import tools.jackson.databind.ObjectMapper
import io.jaiclaw.core.tool.ToolContext
import io.jaiclaw.core.tool.ToolResult
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpResponse

class VideoGenerationSpec extends Specification {

    static final ObjectMapper MAPPER = new ObjectMapper()

    // --- VideoGenerationResult tests ---

    def "VideoGenerationResult.queued creates correct result"() {
        when:
        VideoGenerationResult result = VideoGenerationResult.queued("job-123")

        then:
        result.jobId() == "job-123"
        result.status() == VideoJobStatus.QUEUED
        result.videoUrl() == null
        result.progress() == 0
    }

    def "VideoGenerationResult.processing creates correct result"() {
        when:
        VideoGenerationResult result = VideoGenerationResult.processing("job-123", 50)

        then:
        result.status() == VideoJobStatus.PROCESSING
        result.progress() == 50
    }

    def "VideoGenerationResult.completed creates correct result"() {
        when:
        VideoGenerationResult result = VideoGenerationResult.completed("job-123", "https://example.com/video.mp4")

        then:
        result.status() == VideoJobStatus.COMPLETED
        result.videoUrl() == "https://example.com/video.mp4"
        result.progress() == 100
    }

    def "VideoGenerationResult.failed creates correct result"() {
        when:
        VideoGenerationResult result = VideoGenerationResult.failed("job-123", "GPU timeout")

        then:
        result.status() == VideoJobStatus.FAILED
        result.error() == "GPU timeout"
    }

    // --- VideoGenerationRequest tests ---

    def "VideoGenerationRequest defaults handle nulls"() {
        when:
        VideoGenerationRequest request = new VideoGenerationRequest(null, null, 0, null, null)

        then:
        request.prompt() == ""
        request.durationSecs() == 5
        request.resolution() == "1280x768"
        request.options() == Map.of()
    }

    def "VideoGenerationRequest builder creates request"() {
        when:
        VideoGenerationRequest request = VideoGenerationRequest.builder()
                .prompt("A cat surfing")
                .imageUrl("https://example.com/cat.jpg")
                .durationSecs(10)
                .resolution("768x1280")
                .build()

        then:
        request.prompt() == "A cat surfing"
        request.imageUrl() == "https://example.com/cat.jpg"
        request.durationSecs() == 10
        request.resolution() == "768x1280"
    }

    // --- VideoCapabilities tests ---

    def "VideoCapabilities defaults handle nulls"() {
        when:
        VideoCapabilities caps = new VideoCapabilities(null, 10, true, true)

        then:
        caps.supportedResolutions() == []
        caps.maxDurationSeconds() == 10
    }

    // --- VideoConfig tests ---

    def "VideoConfig defaults handle nulls"() {
        when:
        VideoConfig config = new VideoConfig(null, null, null)

        then:
        config.defaultProvider() == "runway"
        config.runwayModel() == "gen3a_turbo"
    }

    // --- VideoGenerationRegistry tests ---

    def "registry resolves providers by ID"() {
        given:
        VideoGenerationProvider mockProvider = Mock(VideoGenerationProvider) {
            providerId() >> "test-provider"
        }
        VideoGenerationRegistry registry = new VideoGenerationRegistry([mockProvider])

        expect:
        registry.getProvider("test-provider").isPresent()
        !registry.getProvider("nonexistent").isPresent()
        registry.availableProviders() == ["test-provider"]
    }

    def "registry submit delegates to provider"() {
        given:
        VideoGenerationProvider mockProvider = Mock(VideoGenerationProvider) {
            providerId() >> "test"
        }
        VideoGenerationRegistry registry = new VideoGenerationRegistry([mockProvider])
        VideoGenerationRequest request = VideoGenerationRequest.builder().prompt("test").build()

        when:
        registry.submit("test", request)

        then:
        1 * mockProvider.submit(request) >> VideoGenerationResult.queued("job-1")
    }

    def "registry submit returns failure for unknown provider"() {
        given:
        VideoGenerationRegistry registry = new VideoGenerationRegistry([])
        VideoGenerationRequest request = VideoGenerationRequest.builder().prompt("test").build()

        when:
        VideoGenerationResult result = registry.submit("nonexistent", request)

        then:
        result.status() == VideoJobStatus.FAILED
        result.error().contains("nonexistent")
    }

    def "registry poll delegates to provider"() {
        given:
        VideoGenerationProvider mockProvider = Mock(VideoGenerationProvider) {
            providerId() >> "test"
        }
        VideoGenerationRegistry registry = new VideoGenerationRegistry([mockProvider])

        when:
        registry.poll("test", "job-1")

        then:
        1 * mockProvider.poll("job-1") >> VideoGenerationResult.completed("job-1", "https://example.com/v.mp4")
    }

    // --- RunwayVideoProvider tests ---

    def "RunwayVideoProvider has correct providerId"() {
        given:
        RunwayVideoProvider provider = new RunwayVideoProvider("key", "gen3a_turbo")

        expect:
        provider.providerId() == "runway"
    }

    def "RunwayVideoProvider capabilities returns expected values"() {
        given:
        RunwayVideoProvider provider = new RunwayVideoProvider("key", null)

        when:
        VideoCapabilities caps = provider.capabilities()

        then:
        caps.supportsTextInput()
        caps.supportsImageInput()
        caps.maxDurationSeconds() == 10
        caps.supportedResolutions().contains("1280x768")
    }

    def "RunwayVideoProvider submit returns queued on success"() {
        given:
        HttpClient mockHttpClient = Mock(HttpClient)
        RunwayVideoProvider provider = new RunwayVideoProvider("test-key", "gen3a_turbo", mockHttpClient)
        VideoGenerationRequest request = VideoGenerationRequest.builder().prompt("A sunset").build()

        HttpResponse<String> mockResponse = Mock(HttpResponse)
        mockResponse.statusCode() >> 200
        mockResponse.body() >> '{"id": "task-abc-123"}'
        mockHttpClient.send(_, _) >> mockResponse

        when:
        VideoGenerationResult result = provider.submit(request)

        then:
        result.status() == VideoJobStatus.QUEUED
        result.jobId() == "task-abc-123"
    }

    def "RunwayVideoProvider submit returns failure on HTTP error"() {
        given:
        HttpClient mockHttpClient = Mock(HttpClient)
        RunwayVideoProvider provider = new RunwayVideoProvider("test-key", "gen3a_turbo", mockHttpClient)
        VideoGenerationRequest request = VideoGenerationRequest.builder().prompt("fail").build()

        HttpResponse<String> mockResponse = Mock(HttpResponse)
        mockResponse.statusCode() >> 401
        mockResponse.body() >> '{"error": "unauthorized"}'
        mockHttpClient.send(_, _) >> mockResponse

        when:
        VideoGenerationResult result = provider.submit(request)

        then:
        result.status() == VideoJobStatus.FAILED
        result.error().contains("401")
    }

    def "RunwayVideoProvider submit returns failure on exception"() {
        given:
        HttpClient mockHttpClient = Mock(HttpClient)
        RunwayVideoProvider provider = new RunwayVideoProvider("test-key", "gen3a_turbo", mockHttpClient)
        VideoGenerationRequest request = VideoGenerationRequest.builder().prompt("error").build()
        mockHttpClient.send(_, _) >> { throw new IOException("connection refused") }

        when:
        VideoGenerationResult result = provider.submit(request)

        then:
        result.status() == VideoJobStatus.FAILED
        result.error() == "connection refused"
    }

    def "RunwayVideoProvider poll returns completed with video URL"() {
        given:
        HttpClient mockHttpClient = Mock(HttpClient)
        RunwayVideoProvider provider = new RunwayVideoProvider("test-key", "gen3a_turbo", mockHttpClient)

        HttpResponse<String> mockResponse = Mock(HttpResponse)
        mockResponse.statusCode() >> 200
        mockResponse.body() >> '{"status": "SUCCEEDED", "output": ["https://cdn.runway.com/video.mp4"]}'
        mockHttpClient.send(_, _) >> mockResponse

        when:
        VideoGenerationResult result = provider.poll("task-abc")

        then:
        result.status() == VideoJobStatus.COMPLETED
        result.videoUrl() == "https://cdn.runway.com/video.mp4"
    }

    def "RunwayVideoProvider poll returns processing with progress"() {
        given:
        HttpClient mockHttpClient = Mock(HttpClient)
        RunwayVideoProvider provider = new RunwayVideoProvider("test-key", "gen3a_turbo", mockHttpClient)

        HttpResponse<String> mockResponse = Mock(HttpResponse)
        mockResponse.statusCode() >> 200
        mockResponse.body() >> '{"status": "RUNNING", "progress": 42}'
        mockHttpClient.send(_, _) >> mockResponse

        when:
        VideoGenerationResult result = provider.poll("task-abc")

        then:
        result.status() == VideoJobStatus.PROCESSING
        result.progress() == 42
    }

    def "RunwayVideoProvider poll returns failed status"() {
        given:
        HttpClient mockHttpClient = Mock(HttpClient)
        RunwayVideoProvider provider = new RunwayVideoProvider("test-key", "gen3a_turbo", mockHttpClient)

        HttpResponse<String> mockResponse = Mock(HttpResponse)
        mockResponse.statusCode() >> 200
        mockResponse.body() >> '{"status": "FAILED", "failure": "GPU out of memory"}'
        mockHttpClient.send(_, _) >> mockResponse

        when:
        VideoGenerationResult result = provider.poll("task-abc")

        then:
        result.status() == VideoJobStatus.FAILED
        result.error() == "GPU out of memory"
    }

    // --- VideoGenerationTool tests ---

    def "VideoGenerationTool definition has correct name"() {
        given:
        VideoGenerationRegistry registry = new VideoGenerationRegistry([])
        VideoGenerationTool tool = new VideoGenerationTool(registry, "runway")

        expect:
        tool.definition().name() == "video_generate"
        tool.definition().section() == "media"
    }

    def "VideoGenerationTool generate action submits job"() {
        given:
        VideoGenerationProvider mockProvider = Mock(VideoGenerationProvider) {
            providerId() >> "runway"
            submit(_) >> VideoGenerationResult.queued("job-xyz")
        }
        VideoGenerationRegistry registry = new VideoGenerationRegistry([mockProvider])
        VideoGenerationTool tool = new VideoGenerationTool(registry, "runway")
        ToolContext ctx = new ToolContext("agent1", "sess1", "s1", null, Map.of())

        when:
        ToolResult result = tool.execute(Map.of("action", "generate", "prompt", "A sunset over mountains"), ctx)

        then:
        result instanceof ToolResult.Success
        (result as ToolResult.Success).content().contains("job-xyz")
    }

    def "VideoGenerationTool generate action requires prompt"() {
        given:
        VideoGenerationRegistry registry = new VideoGenerationRegistry([])
        VideoGenerationTool tool = new VideoGenerationTool(registry, "runway")
        ToolContext ctx = new ToolContext("agent1", "sess1", "s1", null, Map.of())

        when:
        ToolResult result = tool.execute(Map.of("action", "generate"), ctx)

        then:
        result instanceof ToolResult.Error
        (result as ToolResult.Error).message().contains("prompt")
    }

    def "VideoGenerationTool status action polls job"() {
        given:
        VideoGenerationProvider mockProvider = Mock(VideoGenerationProvider) {
            providerId() >> "runway"
            poll("job-xyz") >> VideoGenerationResult.completed("job-xyz", "https://cdn.example.com/video.mp4")
        }
        VideoGenerationRegistry registry = new VideoGenerationRegistry([mockProvider])
        VideoGenerationTool tool = new VideoGenerationTool(registry, "runway")
        ToolContext ctx = new ToolContext("agent1", "sess1", "s1", null, Map.of())

        when:
        ToolResult result = tool.execute(Map.of("action", "status", "job_id", "job-xyz"), ctx)

        then:
        result instanceof ToolResult.Success
        (result as ToolResult.Success).content().contains("https://cdn.example.com/video.mp4")
    }

    def "VideoGenerationTool status action requires job_id"() {
        given:
        VideoGenerationRegistry registry = new VideoGenerationRegistry([])
        VideoGenerationTool tool = new VideoGenerationTool(registry, "runway")
        ToolContext ctx = new ToolContext("agent1", "sess1", "s1", null, Map.of())

        when:
        ToolResult result = tool.execute(Map.of("action", "status"), ctx)

        then:
        result instanceof ToolResult.Error
        (result as ToolResult.Error).message().contains("job_id")
    }
}
