---
name: healthcheck
description: "Host security hardening and system health assessment. Use when asked for security audits, system hardening, exposure review, firewall/SSH checks, or overall system health status."
alwaysInclude: false
requiredBins: []
platforms: [darwin, linux]
---

# System Health Check and Security Hardening

Assess and harden the host system's security posture.

## Core Rules

- Require explicit approval before any state-changing action
- Do not modify remote access settings without confirming how the user connects
- Prefer reversible, staged changes with a rollback plan
- Never run destructive commands without confirmation

## Workflow

### 1. Establish Context (Read-Only)

Infer from the environment before asking:

1. OS and version: `uname -a`, `sw_vers`, `cat /etc/os-release`
2. Privilege level (root/admin vs user)
3. Access path (local, SSH, tailnet)
4. Network exposure (public IP, proxy, tunnel)
5. Listening ports:
   - Linux: `ss -ltnp`
   - macOS: `lsof -nP -iTCP -sTCP:LISTEN`
6. Firewall status:
   - Linux: `ufw status`, `firewall-cmd --state`, `nft list ruleset`
   - macOS: `/usr/libexec/ApplicationFirewall/socketfilterfw --getglobalstate`
7. Disk encryption: FileVault / LUKS / BitLocker
8. OS automatic security updates status

### 2. Determine Risk Tolerance

Offer profiles:

1. **Home/Workstation Balanced** — firewall on with reasonable defaults, remote access restricted to LAN/tailnet
2. **VPS Hardened** — deny-by-default inbound, minimal open ports, key-only SSH, no root login, auto security updates
3. **Developer Convenience** — more local services allowed, explicit exposure warnings, still audited
4. **Custom** — user-defined constraints

### 3. Produce Remediation Plan

Include:
- Target profile and current posture summary
- Gaps vs target
- Step-by-step remediation with exact commands
- Access-preservation strategy and rollback
- Risks and potential lockout scenarios

Always show the plan before any changes.

### 4. Execute with Confirmations

For each step:
- Show the exact command
- Explain impact and rollback
- Confirm access will remain available
- Stop on unexpected output

### 5. Verify and Report

Re-check:
- Firewall status
- Listening ports
- Remote access still works

Deliver a final posture report.

## Required Confirmations (Always)

- Firewall rule changes
- Opening/closing ports
- SSH/RDP configuration changes
- Installing/removing packages
- Enabling/disabling services
- User/group modifications
- Scheduling tasks
