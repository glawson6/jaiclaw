---
name: summarize
description: Content summarization for URLs, files, and conversations
alwaysInclude: false
requiredBins: []
platforms: [darwin, linux]
---

# Content Summarization

Guidelines for summarizing content from various sources:

## URLs
- Use **WebFetch** to retrieve web page content.
- Extract the main content, ignoring navigation, ads, and boilerplate.
- Provide a concise summary with key points.

## Files
- Use **FileRead** to load file content.
- For code files, summarize purpose, key functions, and dependencies.
- For documents, extract the thesis, main arguments, and conclusions.

## Conversations
- Identify the key topics, decisions, and action items.
- Highlight unresolved questions or open issues.
- Organize by topic rather than chronologically when clearer.

## Summarization Techniques
- Lead with the most important information (inverted pyramid).
- Use bullet points for multiple discrete facts.
- Include specific numbers, names, and dates rather than vague references.
- Note what was omitted if the source is substantially longer than the summary.
- Adjust summary length to the complexity and length of the source material.
