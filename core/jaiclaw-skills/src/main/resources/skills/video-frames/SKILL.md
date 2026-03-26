---
name: video-frames
description: "Extract frames or short clips from videos using ffmpeg. Use when asked to capture a frame, create a thumbnail, or extract a clip from a video file."
alwaysInclude: false
requiredBins: [ffmpeg]
platforms: [darwin, linux]
---

# Video Frames (ffmpeg)

Extract frames and clips from video files.

## Install

```bash
brew install ffmpeg
```

## Extract a Frame

```bash
# First frame
ffmpeg -i /path/to/video.mp4 -vframes 1 /tmp/frame.jpg

# Frame at specific timestamp
ffmpeg -ss 00:00:10 -i /path/to/video.mp4 -vframes 1 /tmp/frame-10s.jpg

# High-quality PNG
ffmpeg -ss 00:01:30 -i /path/to/video.mp4 -vframes 1 /tmp/frame.png
```

## Extract Multiple Frames

```bash
# One frame per second
ffmpeg -i /path/to/video.mp4 -vf "fps=1" /tmp/frames/frame_%04d.jpg

# One frame every 10 seconds
ffmpeg -i /path/to/video.mp4 -vf "fps=1/10" /tmp/frames/frame_%04d.jpg
```

## Extract a Clip

```bash
# 10-second clip starting at 30s
ffmpeg -ss 00:00:30 -i /path/to/video.mp4 -t 10 -c copy /tmp/clip.mp4

# Re-encode clip (for precise cuts)
ffmpeg -ss 00:00:30 -i /path/to/video.mp4 -t 10 -c:v libx264 -c:a aac /tmp/clip.mp4
```

## Create Thumbnail Grid

```bash
# 3x3 grid of thumbnails
ffmpeg -i /path/to/video.mp4 -vf "select='not(mod(n\,100))',scale=320:-1,tile=3x3" -frames:v 1 /tmp/grid.jpg
```

## Notes

- Use `-ss` before `-i` for fast seeking (keyframe-based)
- Use `.jpg` for quick sharing; `.png` for crisp UI frames
- `-c copy` avoids re-encoding (fast but less precise cuts)
- Check video info: `ffprobe /path/to/video.mp4`
