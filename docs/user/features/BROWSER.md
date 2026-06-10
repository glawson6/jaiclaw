# Browser Automation

Module: `jaiclaw-browser`

## Overview

Browser automation enables agents to navigate web pages, interact with elements, take screenshots, and extract page content. Built on Playwright with a reflection-based adapter to keep the dependency optional at runtime.

## Prerequisites

Playwright must be on the classpath and browsers installed:

```bash
# Add to your pom.xml (already included in jaiclaw-browser)
# <dependency>
#     <groupId>com.microsoft.playwright</groupId>
#     <artifactId>playwright</artifactId>
#     <version>1.49.0</version>
# </dependency>

# Install browser binaries
mvn exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"
```

If Playwright is not on the classpath, the browser tools will not be available (graceful degradation).

## Configuration

```java
BrowserConfig config = new BrowserConfig(
    true,           // enabled
    true,           // headless (false for debugging)
    null,           // profilesDir (null = temp)
    null,           // downloadDir (null = temp)
    30_000,         // timeoutMs
    "1280x720"      // viewport
);
```

## Agent Tools

The browser provides 8 tools in the "Browser" section:

| Tool | Description |
|------|-------------|
| `browser_navigate` | Navigate to a URL |
| `browser_click` | Click an element by CSS selector |
| `browser_type` | Type text into an input field |
| `browser_screenshot` | Take a screenshot (returns base64) |
| `browser_evaluate` | Execute JavaScript in the page |
| `browser_read_page` | Extract page content as accessibility tree text |
| `browser_list_tabs` | List open browser tabs |
| `browser_close_tab` | Close a browser tab |

## Architecture

```
BrowserTools (ToolCallback implementations)
    ↓
BrowserService (session pool, lazy Playwright init)
    ↓
BrowserSession (wraps Playwright Page via reflection)
    ↓
PageSnapshot (structured page content as PageElement list)
```

- `BrowserService` manages a `ConcurrentHashMap` of sessions keyed by session ID
- `BrowserSession` wraps all Playwright API calls via reflection to avoid compile-time coupling
- `PageSnapshot.toText()` converts page content to an accessibility-tree-style text format for LLM consumption

## Usage Notes

- Sessions are pooled and reused within the same agent conversation
- Screenshots are returned as base64 strings for embedding in messages
- The `browser_read_page` tool returns a structured text representation suitable for LLM processing
- Headless mode is the default; set `headless=false` for visual debugging
