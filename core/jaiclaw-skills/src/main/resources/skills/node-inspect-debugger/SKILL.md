---
name: node-inspect-debugger
description: Terminal-based Node.js debugging via V8 inspector protocol
alwaysInclude: false
requiredBins: [node]
platforms: [darwin, linux]
version: 1.0.0
tenantIds: []
---

# Node.js Inspector Debugger

Debug Node.js applications using the built-in V8 inspector protocol from the terminal. This skill covers launching debug sessions, setting breakpoints, inspecting state, and diagnosing issues without a GUI debugger.

## Starting a Debug Session

```bash
# Inspect mode — pauses at first line
node --inspect-brk script.js

# Inspect mode — runs immediately, attach later
node --inspect script.js

# Specific port
node --inspect=0.0.0.0:9230 script.js

# Debug a test runner
node --inspect-brk node_modules/.bin/jest --runInBand
```

## Chrome DevTools Protocol (CDP) via curl

Query the inspector endpoint directly:

```bash
# List debuggable targets
curl -s http://127.0.0.1:9229/json/list | python3 -m json.tool

# Get the WebSocket debugger URL
curl -s http://127.0.0.1:9229/json/list | python3 -c "import sys,json; print(json.load(sys.stdin)[0]['webSocketDebuggerUrl'])"
```

## Using node inspect (Built-in CLI Debugger)

```bash
# Launch the built-in CLI debugger
node inspect script.js
```

### Key Commands

| Command | Action |
|---------|--------|
| `c` / `cont` | Continue execution |
| `n` / `next` | Step over |
| `s` / `step` | Step into |
| `o` / `out` | Step out |
| `sb('file.js', line)` | Set breakpoint |
| `cb('file.js', line)` | Clear breakpoint |
| `repl` | Enter REPL to evaluate expressions |
| `exec('expr')` | Evaluate expression in current scope |
| `bt` / `backtrace` | Print call stack |
| `list(n)` | Show source around current line |
| `watch('expr')` | Add watch expression |
| `watchers` | Show all watch expressions |

## Programmatic Debugging with inspector Module

```javascript
const inspector = require('node:inspector');
const session = new inspector.Session();
session.connect();

// Enable debugging domains
session.post('Debugger.enable');
session.post('Runtime.enable');

// Set breakpoint by URL
session.post('Debugger.setBreakpointByUrl', {
  lineNumber: 10,
  url: 'file:///path/to/script.js'
});

// Evaluate expression
session.post('Runtime.evaluate', {
  expression: 'myVariable',
  returnByValue: true
}, (err, result) => {
  console.log(result);
});
```

## Heap Snapshots and Profiling

```bash
# Generate heap snapshot (sends SIGUSR2 or use inspector)
node --inspect -e "
  const v8 = require('v8');
  const fs = require('fs');
  const snap = v8.writeHeapSnapshot();
  console.log('Heap snapshot written to', snap);
"

# CPU profiling
node --prof script.js
node --prof-process isolate-*.log > profile.txt
```

## Common Debugging Patterns

### Memory Leak Investigation

```bash
# Start with increased heap reporting
node --inspect --max-old-space-size=512 --trace-gc script.js
```

### Async Stack Traces

```bash
# Enable full async stack traces
node --inspect --async-stack-traces script.js
```

### Debug Environment Variables

```bash
# Enable verbose Node.js debug output
NODE_DEBUG=http,net,tls node script.js

# Enable V8 flags
node --v8-options | grep -i debug
```

## Rules

1. **Use `--inspect-brk`** when you need to set breakpoints before code runs.
2. **Use `--runInBand`** with Jest to avoid multi-process debugging complexity.
3. **Prefer `node inspect`** over GUI tools for terminal-only environments.
4. **Capture heap snapshots** before and after suspected leak points for comparison.
5. **Check `NODE_DEBUG`** for module-level tracing before attaching a full debugger.
