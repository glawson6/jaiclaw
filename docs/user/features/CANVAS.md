# Canvas (A2UI)

Module: `jaiclaw-canvas`

## Overview

Canvas provides an agent-to-user interface (A2UI) for rendering rich HTML artifacts. Agents can create, update, and snapshot interactive content like dashboards, diagrams, and forms — similar to Claude's Artifacts feature.

## Agent Tools

Three tools in the "Canvas" section:

| Tool | Description |
|------|-------------|
| `canvas_present` | Create or update an HTML artifact |
| `canvas_eval` | Execute JavaScript on the current canvas |
| `canvas_snapshot` | Read the current canvas HTML content |

## Usage

### From Agent Tools

```
// Agent writes HTML to canvas
canvas_present: {"title": "Dashboard", "html": "<h1>System Status</h1><p>All green</p>"}

// Agent runs JS on canvas
canvas_eval: {"script": "document.querySelector('h1').textContent = 'Updated!'"}

// Agent reads current state
canvas_snapshot: {}
// Returns: "<h1>Updated!</h1><p>All green</p>"
```

### Programmatic

```java
CanvasConfig config = new CanvasConfig(true, 18793, "127.0.0.1");
CanvasFileManager files = new CanvasFileManager();
CanvasService canvas = new CanvasService(config, files);

// Present content
canvas.present("my-artifact", "Dashboard", "<h1>Hello</h1>");

// Get current content
String html = canvas.getCurrentContent("my-artifact");

// Hide
canvas.hide("my-artifact");
```

## Architecture

```
CanvasTools (3 ToolCallback implementations)
    ↓
CanvasService (orchestrator: present/hide/snapshot)
    ↓
CanvasFileManager (HTML file I/O in temp directory)
```

- `CanvasFileManager` writes HTML files to a temp directory, allowing external renderers to pick them up
- `CanvasService` manages the lifecycle of artifacts by ID
- `CanvasTools` exposes the functionality as agent-callable tools

## Configuration

```java
CanvasConfig config = new CanvasConfig(
    true,       // enabled
    18793,      // port (for future HTTP server)
    "127.0.0.1" // host
);
```

## Core Type (in jaiclaw-core)

```java
public record CanvasAction(String type, Map<String, Object> params) {}
```

Action types: `"present"`, `"eval"`, `"snapshot"`, `"hide"`
