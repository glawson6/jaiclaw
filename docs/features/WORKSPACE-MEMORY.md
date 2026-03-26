# Workspace Memory

Module: `jaiclaw-memory` (enhanced)

## Overview

Workspace memory provides persistent, file-based memory for agents — including a long-term MEMORY.md file, daily logs, session transcripts, and hybrid search combining keyword matching with temporal decay.

## Components

### WorkspaceMemoryManager

Manages a `MEMORY.md` file in the workspace root. Supports reading, writing, and appending to named sections.

```java
WorkspaceMemoryManager memory = new WorkspaceMemoryManager(workspaceDir);

// Read entire memory
String content = memory.read();

// Write/overwrite
memory.write("# Agent Memory\n\n## Preferences\n- User likes concise answers");

// Append to a specific section
memory.appendToSection("Preferences", "- User prefers dark mode");
```

### DailyLogAppender

Appends timestamped entries to daily log files (`memory/YYYY-MM-DD.md`). Useful for tracking agent activities over time.

```java
DailyLogAppender log = new DailyLogAppender(workspaceDir);
log.append("Completed deployment to staging");
log.append("User requested rollback");

// Read today's log
String today = log.readToday();

// Read a specific date
String history = log.read(LocalDate.of(2026, 3, 19));
```

### SessionTranscriptStore

Persists conversation transcripts in JSONL format for later retrieval and search.

```java
SessionTranscriptStore transcripts = new SessionTranscriptStore(workspaceDir);

// Save a transcript
transcripts.save("session-123", messages);

// Load a transcript
List<Message> loaded = transcripts.load("session-123");

// List all sessions
List<String> sessions = transcripts.listSessions();
```

### HybridSearchManager

Combines keyword search with temporal decay scoring. More recent entries score higher for the same keyword match.

```java
HybridSearchManager search = new HybridSearchManager();

// Index entries
search.index("entry-1", "Deploy to production", Instant.now());
search.index("entry-2", "Fix login bug", Instant.now().minus(7, ChronoUnit.DAYS));

// Search — recent entries score higher
List<SearchResult> results = search.search("deploy", 10);
```

### MemorySaveTool

A `ToolCallback` that allows the agent to save notes to workspace memory during conversation. Registered in the "Memory" tool section.

## Memory Sources

The `MemorySource` enum categorizes where memory comes from:

| Source | Description |
|--------|-------------|
| `MEMORY` | Long-term notes in MEMORY.md |
| `SESSIONS` | Previous conversation sessions |
| `WORKSPACE` | Workspace-level persistent memory |
| `DAILY_LOG` | Daily activity logs |
| `TRANSCRIPT` | Full session transcripts |
