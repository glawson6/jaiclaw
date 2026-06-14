---
name: python-debugpy
description: Python debugging with pdb, debugpy, and remote attach
alwaysInclude: false
requiredBins: [python3]
platforms: [darwin, linux]
version: 1.0.0
tenantIds: []
---

# Python Debugging

Debug Python applications using pdb (built-in), debugpy (VS Code protocol), and remote attach workflows. Covers breakpoints, post-mortem debugging, and profiling.

## pdb — Built-in Debugger

### Launching

```bash
# Run script under pdb
python3 -m pdb script.py

# Post-mortem on crash
python3 -c "
import mymodule
try:
    mymodule.run()
except Exception:
    import pdb; pdb.post_mortem()
"
```

### Inline Breakpoints

```python
# Insert in source code (Python 3.7+)
breakpoint()

# Or explicitly
import pdb; pdb.set_trace()
```

### Key Commands

| Command | Action |
|---------|--------|
| `n` / `next` | Step over |
| `s` / `step` | Step into |
| `c` / `continue` | Continue execution |
| `r` / `return` | Continue until function returns |
| `l` / `list` | Show source around current line |
| `ll` | Show full source of current function |
| `p expr` | Print expression value |
| `pp expr` | Pretty-print expression value |
| `w` / `where` | Print stack trace |
| `u` / `up` | Move up one frame |
| `d` / `down` | Move down one frame |
| `b file:line` | Set breakpoint |
| `cl num` | Clear breakpoint |
| `commands num` | Set commands to run at breakpoint |
| `interact` | Start interactive interpreter in current frame |

### Conditional Breakpoints

```python
# Break only when condition is true
import pdb; pdb.set_trace() if len(items) > 100 else None

# In pdb prompt:
# b 42, x > 10       # break at line 42 when x > 10
```

## debugpy — Remote Debugging

### Install

```bash
pip install debugpy
```

### Launch Modes

```bash
# Listen for attach (pauses until client connects)
python3 -m debugpy --listen 0.0.0.0:5678 --wait-for-client script.py

# Listen without waiting
python3 -m debugpy --listen 5678 script.py

# Attach to running process by PID
python3 -m debugpy --listen 5678 --pid 12345
```

### Programmatic Setup

```python
import debugpy

# Start debug server
debugpy.listen(("0.0.0.0", 5678))
print("Waiting for debugger attach...")
debugpy.wait_for_client()
debugpy.breakpoint()

# Now run your code
main()
```

### Remote Attach via DAP

Connect any DAP-compatible client to the debug server:

```json
{
  "type": "debugpy",
  "request": "attach",
  "connect": { "host": "localhost", "port": 5678 },
  "pathMappings": [
    { "localRoot": "${workspaceFolder}", "remoteRoot": "/app" }
  ]
}
```

## Profiling

### cProfile

```bash
# Profile script and sort by cumulative time
python3 -m cProfile -s cumulative script.py

# Save profile data
python3 -m cProfile -o profile.stats script.py

# Analyze saved profile
python3 -c "
import pstats
p = pstats.Stats('profile.stats')
p.sort_stats('cumulative')
p.print_stats(20)
"
```

### line_profiler

```bash
pip install line_profiler

# Decorate functions with @profile, then:
kernprof -l -v script.py
```

### memory_profiler

```bash
pip install memory_profiler

# Decorate functions with @profile, then:
python3 -m memory_profiler script.py
```

## Common Patterns

### Debug Django

```bash
# Django with debugpy
python3 -m debugpy --listen 5678 manage.py runserver --noreload
```

### Debug pytest

```bash
# Drop into pdb on failure
python3 -m pytest --pdb

# Drop into pdb on first failure then quit
python3 -m pytest -x --pdb

# Use debugpy with pytest
python3 -m debugpy --listen 5678 -m pytest tests/
```

### Debug in Docker

```dockerfile
# Expose debug port
EXPOSE 5678
CMD ["python3", "-m", "debugpy", "--listen", "0.0.0.0:5678", "app.py"]
```

## Rules

1. **Use `breakpoint()`** over `import pdb; pdb.set_trace()` for Python 3.7+.
2. **Use `--wait-for-client`** when you need to debug startup code.
3. **Use `--noreload`** with Django to avoid debugger disconnects on file changes.
4. **Profile first** — use cProfile before reaching for a debugger on performance issues.
5. **Check `PYTHONBREAKPOINT`** env var — it controls what `breakpoint()` calls.
