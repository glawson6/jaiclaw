---
name: tmux
description: "Remote-control tmux sessions for interactive CLIs by sending keystrokes and scraping pane output. Use when monitoring background sessions, sending input to interactive terminals, or managing parallel workers."
alwaysInclude: false
requiredBins: [tmux]
platforms: [darwin, linux]
---

# tmux Session Control

Control tmux sessions by sending keystrokes and reading output.

## When to Use

- Monitoring long-running processes in tmux
- Sending input to interactive terminal applications
- Scraping output from background sessions
- Managing parallel worker sessions

## When NOT to Use

- Running one-off shell commands — use **ShellExec** directly
- Non-interactive scripts — use **ShellExec**
- Process not in tmux

## Common Commands

### List Sessions

```bash
tmux list-sessions
```

### Capture Output

```bash
# Last 20 lines
tmux capture-pane -t session-name -p | tail -20

# Entire scrollback
tmux capture-pane -t session-name -p -S -

# Specific pane
tmux capture-pane -t session-name:0.0 -p
```

### Send Keys

```bash
# Send text (doesn't press Enter)
tmux send-keys -t session-name "hello"

# Send text + Enter
tmux send-keys -t session-name "command" Enter

# Special keys
tmux send-keys -t session-name Escape
tmux send-keys -t session-name C-c          # Ctrl+C
tmux send-keys -t session-name C-d          # Ctrl+D (EOF)
```

### Session Management

```bash
tmux new-session -d -s newsession
tmux kill-session -t sessionname
tmux rename-session -t old new
```

### Window/Pane Navigation

```bash
tmux select-window -t session-name:0
tmux select-pane -t session-name:0.1
tmux list-windows -t session-name
```

## Safe Input Pattern

For interactive TUIs, split text and Enter to avoid paste issues:

```bash
tmux send-keys -t session-name -l -- "command text here"
sleep 0.1
tmux send-keys -t session-name Enter
```

## Check All Sessions Status

```bash
for s in $(tmux list-sessions -F '#{session_name}'); do
  echo "=== $s ==="
  tmux capture-pane -t $s -p 2>/dev/null | tail -5
done
```

## Notes

- Use `capture-pane -p` to print to stdout
- `-S -` captures entire scrollback history
- Target format: `session:window.pane`
- Sessions persist across SSH disconnects
