---
name: system-admin
description: System administration and diagnostics
alwaysInclude: false
requiredBins: []
platforms: [darwin, linux]
---

# System Administration

Guidelines for system administration tasks:

## Diagnostics
- Use **ShellExec** for running diagnostic commands (ps, df, top, netstat, systemctl).
- Check service status before attempting restarts or configuration changes.
- Read log files with **FileRead** to investigate issues.

## File Management
- Verify paths exist before operations.
- Use appropriate permissions — do not escalate to root unless necessary.
- Back up configuration files before modifying them.

## Safety
- Never run destructive commands (rm -rf, mkfs, dd) without explicit user confirmation.
- Confirm the target before operations that affect running services.
- Prefer non-destructive investigation before taking corrective action.
- Show the user what commands will be run before executing them on production systems.

## Networking
- Check connectivity with ping/curl before diagnosing application issues.
- Use netstat/ss to verify port bindings and active connections.
- Review firewall rules when diagnosing access problems.
