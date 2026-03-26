---
name: openai-whisper-api
description: "Transcribe audio via the OpenAI Audio Transcriptions API (Whisper). Use when asked to transcribe audio files, convert speech to text, or extract text from recordings. Requires OPENAI_API_KEY."
alwaysInclude: false
requiredBins: [curl]
platforms: [darwin, linux]
---

# OpenAI Whisper API

Transcribe audio files using OpenAI's Whisper API.

## Quick Start

```bash
curl -s https://api.openai.com/v1/audio/transcriptions \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -F file="@/path/to/audio.m4a" \
  -F model="whisper-1"
```

## With Options

```bash
# Specify language
curl -s https://api.openai.com/v1/audio/transcriptions \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -F file="@/path/to/audio.ogg" \
  -F model="whisper-1" \
  -F language="en"

# With prompt for context
curl -s https://api.openai.com/v1/audio/transcriptions \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -F file="@/path/to/audio.m4a" \
  -F model="whisper-1" \
  -F prompt="Speaker names: Peter, Daniel"

# JSON output with timestamps
curl -s https://api.openai.com/v1/audio/transcriptions \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -F file="@/path/to/audio.m4a" \
  -F model="whisper-1" \
  -F response_format="verbose_json"

# SRT subtitle format
curl -s https://api.openai.com/v1/audio/transcriptions \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -F file="@/path/to/audio.m4a" \
  -F model="whisper-1" \
  -F response_format="srt"
```

## Save to File

```bash
curl -s https://api.openai.com/v1/audio/transcriptions \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -F file="@/path/to/audio.m4a" \
  -F model="whisper-1" \
  -o transcript.txt
```

## Supported Formats

mp3, mp4, mpeg, mpga, m4a, wav, webm, ogg, flac

## Notes

- Model: `whisper-1`
- Max file size: 25 MB
- For longer files, split with ffmpeg first
- Response formats: `json`, `text`, `srt`, `verbose_json`, `vtt`
