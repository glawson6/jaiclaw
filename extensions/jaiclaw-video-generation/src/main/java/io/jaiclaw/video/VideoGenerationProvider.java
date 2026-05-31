package io.jaiclaw.video;

/**
 * SPI for video generation providers.
 *
 * <p>Video generation is inherently asynchronous — providers accept a request,
 * return a job ID, and the caller polls for completion. This SPI models that
 * pattern with {@link #submit(VideoGenerationRequest)} and
 * {@link #poll(String)}.
 */
public interface VideoGenerationProvider {

    /**
     * Unique provider identifier (e.g. "runway", "sora", "veo").
     */
    String providerId();

    /**
     * Submit a video generation request.
     *
     * @param request the generation request
     * @return a result with status QUEUED and a job ID for polling
     */
    VideoGenerationResult submit(VideoGenerationRequest request);

    /**
     * Poll the status of a previously submitted job.
     *
     * @param jobId the job ID returned by {@link #submit}
     * @return current job status with progress and/or video URL
     */
    VideoGenerationResult poll(String jobId);

    /**
     * The capabilities of this provider.
     */
    VideoCapabilities capabilities();
}
