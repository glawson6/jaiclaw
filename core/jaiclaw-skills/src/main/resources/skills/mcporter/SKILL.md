---
name: mcporter
description: "List, configure, authenticate, and call MCP servers/tools directly via the mcporter CLI. Use when interacting with MCP (Model Context Protocol) servers, listing available tools, or calling tool endpoints."
alwaysInclude: false
requiredBins: [mcporter]
platforms: [darwin, linux]
---

# MCP Porter

Use `mcporter` to work with MCP (Model Context Protocol) servers directly.

## Install

```bash
npm install -g mcporter
```

## Quick Start

```bash
# List configured servers
mcporter list

# List tools with schema
mcporter list <server> --schema

# Call a tool
mcporter call <server.tool> key=value
```

## Calling Tools

```bash
# Selector syntax
mcporter call linear.list_issues team=ENG limit:5

# Function syntax
mcporter call "linear.create_issue(title: \"Bug\")"

# Full URL
mcporter call https://api.example.com/mcp.fetch url:https://example.com

# Stdio transport
mcporter call --stdio "bun run ./server.ts" scrape url=https://example.com

# JSON payload
mcporter call <server.tool> --args '{"limit":5}'
```

## Auth and Config

```bash
# OAuth authentication
mcporter auth <server | url> [--reset]

# Configuration management
mcporter config list|get|add|remove|import|login|logout
```

## Daemon

```bash
mcporter daemon start|status|stop|restart
```

## Code Generation

```bash
# Generate CLI from MCP server
mcporter generate-cli --server <name>

# Generate TypeScript types
mcporter emit-ts <server> --mode client|types
```

## Notes

- Config default: `./config/mcporter.json` (override with `--config`)
- Prefer `--output json` for machine-readable results
- Supports HTTP and stdio transports
