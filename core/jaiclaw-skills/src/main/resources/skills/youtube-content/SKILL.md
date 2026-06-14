---
name: youtube-content
description: Extract YouTube transcripts and transform into structured content
alwaysInclude: false
requiredBins: [python3]
platforms: [darwin, linux]
version: 1.0.0
tenantIds: []
---

# YouTube Content

Extract transcripts from YouTube videos and transform them into structured content formats: summaries, blog posts, show notes, and study guides.

## Transcript Extraction

### Using yt-dlp

```bash
# Install yt-dlp if not present
pip install yt-dlp

# Download auto-generated subtitles
yt-dlp --write-auto-sub --sub-lang en --skip-download -o "%(id)s" "VIDEO_URL"

# Download manual subtitles (preferred when available)
yt-dlp --write-sub --sub-lang en --skip-download -o "%(id)s" "VIDEO_URL"

# List available subtitle languages
yt-dlp --list-subs "VIDEO_URL"

# Get video metadata as JSON
yt-dlp --dump-json --skip-download "VIDEO_URL"
```

### Using youtube-transcript-api (Python)

```bash
pip install youtube-transcript-api
```

```python
from youtube_transcript_api import YouTubeTranscriptApi

# Get transcript by video ID
transcript = YouTubeTranscriptApi.get_transcript("VIDEO_ID")

# With language preference
transcript = YouTubeTranscriptApi.get_transcript("VIDEO_ID", languages=["en"])

# Format as plain text
text = " ".join([entry["text"] for entry in transcript])

# Format with timestamps
for entry in transcript:
    mins = int(entry["start"]) // 60
    secs = int(entry["start"]) % 60
    print(f"[{mins:02d}:{secs:02d}] {entry['text']}")
```

## Content Transformation

### Summary Generation

Given a transcript, produce:

1. **Title and metadata** — video title, channel, duration, publish date.
2. **Executive summary** — 2-3 sentence overview.
3. **Key points** — bulleted list of main ideas with timestamps.
4. **Notable quotes** — verbatim quotes worth highlighting.
5. **Action items** — if the video contains advice or instructions.

### Blog Post Format

```markdown
# [Video Title]

*Based on [Channel Name]'s video ([link])*

## Overview
[2-3 paragraph summary]

## Key Takeaways

### 1. [First major point]
[Expanded discussion with timestamp reference]

### 2. [Second major point]
...

## Conclusion
[Wrap-up and implications]
```

### Show Notes Format

```markdown
## Show Notes: [Video Title]

**Channel:** [Name]
**Duration:** [HH:MM:SS]
**Published:** [Date]

### Timestamps
- 00:00 — Introduction
- 02:30 — [Topic 1]
- 15:45 — [Topic 2]
...

### Links Mentioned
- [Resource 1](url)
- [Resource 2](url)

### Key Quotes
> "Quote from the video" — [Speaker] at [timestamp]
```

## Batch Processing

```python
import json

video_ids = ["id1", "id2", "id3"]

for vid in video_ids:
    try:
        transcript = YouTubeTranscriptApi.get_transcript(vid)
        text = " ".join([e["text"] for e in transcript])
        with open(f"{vid}_transcript.txt", "w") as f:
            f.write(text)
        print(f"Saved transcript for {vid}")
    except Exception as e:
        print(f"Failed for {vid}: {e}")
```

## Video Metadata Extraction

```bash
# Get structured metadata
yt-dlp --dump-json --skip-download "VIDEO_URL" | python3 -c "
import sys, json
data = json.load(sys.stdin)
print(f'Title: {data[\"title\"]}')
print(f'Channel: {data[\"channel\"]}')
print(f'Duration: {data[\"duration\"]} seconds')
print(f'Views: {data.get(\"view_count\", \"N/A\")}')
print(f'Upload date: {data[\"upload_date\"]}')
print(f'Description: {data[\"description\"][:500]}')
"
```

## Rules

1. **Prefer manual subtitles** over auto-generated when available — they are more accurate.
2. **Preserve timestamps** — always include timestamp references in transformed content.
3. **Attribute content** — always credit the original video and channel.
4. **Handle missing transcripts** — some videos have no subtitles; report this clearly rather than failing silently.
5. **Respect rate limits** — add delays between batch requests to avoid API throttling.
