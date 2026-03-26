---
name: openai-image-gen
description: "Generate images via the OpenAI Images API (DALL-E 3, GPT Image). Use when asked to create, generate, or produce images. Requires OPENAI_API_KEY."
alwaysInclude: false
requiredBins: [curl]
platforms: [darwin, linux]
---

# OpenAI Image Generation

Generate images using the OpenAI Images API.

## Quick Start

```bash
curl -s https://api.openai.com/v1/images/generations \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -d '{
    "model": "dall-e-3",
    "prompt": "A serene mountain landscape at sunset",
    "n": 1,
    "size": "1024x1024"
  }' | jq -r '.data[0].url'
```

## Models

### DALL-E 3

```bash
curl -s https://api.openai.com/v1/images/generations \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -d '{
    "model": "dall-e-3",
    "prompt": "description here",
    "n": 1,
    "size": "1024x1024",
    "quality": "hd",
    "style": "vivid"
  }'
```

- Sizes: `1024x1024`, `1792x1024`, `1024x1792`
- Quality: `standard`, `hd`
- Style: `vivid` (hyper-real), `natural` (more natural)
- Only supports `n=1`

### GPT Image Models

```bash
curl -s https://api.openai.com/v1/images/generations \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -d '{
    "model": "gpt-image-1",
    "prompt": "description here",
    "n": 4,
    "size": "1024x1024",
    "quality": "high"
  }'
```

- Sizes: `1024x1024`, `1536x1024` (landscape), `1024x1536` (portrait), `auto`
- Quality: `auto`, `high`, `medium`, `low`
- Supports multiple images per request

## Downloading Images

The API returns URLs. Download with:

```bash
URL=$(curl -s https://api.openai.com/v1/images/generations \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -d '{"model": "dall-e-3", "prompt": "a cat", "n": 1, "size": "1024x1024"}' \
  | jq -r '.data[0].url')

curl -s "$URL" -o generated-image.png
```

## Notes

- Image generation can take 10-30+ seconds
- URLs expire after ~1 hour; download immediately
- DALL-E 3 automatically enhances short prompts
- Use specific, detailed prompts for best results
