---
name: blogwatcher
description: "Monitor blogs and RSS/Atom feeds for updates using the blogwatcher CLI. Use when asked to track blogs, monitor RSS feeds, check for new articles, or follow content updates."
alwaysInclude: false
requiredBins: [blogwatcher]
platforms: [darwin, linux]
---

# Blog Watcher

Track blog and RSS/Atom feed updates with the `blogwatcher` CLI.

## Install

```bash
# Go
go install github.com/Hyaxia/blogwatcher/cmd/blogwatcher@latest

# Or via brew (if available)
brew install blogwatcher
```

## Common Commands

```bash
# Add a blog to track
blogwatcher add "My Blog" https://example.com

# List tracked blogs
blogwatcher blogs

# Scan for new articles
blogwatcher scan

# List all articles
blogwatcher articles

# Mark an article as read
blogwatcher read 1

# Mark all articles as read
blogwatcher read-all

# Remove a blog
blogwatcher remove "My Blog"
```

## Notes

- Use `blogwatcher <command> --help` to discover flags and options
- Supports RSS and Atom feeds
- No API key needed
