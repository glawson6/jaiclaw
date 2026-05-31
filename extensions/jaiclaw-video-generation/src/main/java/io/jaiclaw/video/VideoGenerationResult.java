package io.jaiclaw.video;

/**
 * Result of a video generation job.
 *
 * @param jobId     provider-specific job identifier
 * @param status    current job status
 * @param videoUrl  URL of the generated video (non-null when COMPLETED)
 * @param error     error message (non-null when FAILED)
 * @param progress  completion progress as a percentage (0-100)
 */
public record VideoGenerationResult(
        String jobId,
        VideoJobStatus status,
        String videoUrl,
        String error,
        int progress
) {
    public static VideoGenerationResult queued(String jobId) {
        return new VideoGenerationResult(jobId, VideoJobStatus.QUEUED, null, null, 0);
    }

    public static VideoGenerationResult processing(String jobId, int progress) {
        return new VideoGenerationResult(jobId, VideoJobStatus.PROCESSING, null, null, progress);
    }

    public static VideoGenerationResult completed(String jobId, String videoUrl) {
        return new VideoGenerationResult(jobId, VideoJobStatus.COMPLETED, videoUrl, null, 100);
    }

    public static VideoGenerationResult failed(String jobId, String error) {
        return new VideoGenerationResult(jobId, VideoJobStatus.FAILED, null, error, 0);
    }
}
