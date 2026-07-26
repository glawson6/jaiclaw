// Read-only pipeline dashboard.
//
// Consumes the existing server surfaces — no build step, no
// framework, just fetch + EventSource. If any dependency shape
// changes on the server, update the four endpoint constants at the
// top and the two payload-parse blocks (executions, events).

const API = {
    pipelinesList: "/actuator/pipelines",
    pipelineDetail: (id) => `/actuator/pipelines/${encodeURIComponent(id)}`,
    render: (id) => `/api/pipelines/${encodeURIComponent(id)}/render.html?view=flow&format=svg`,
    events: (id) => `/api/pipelines/${encodeURIComponent(id)}/events`,
    trigger: "/api/pipelines/trigger",
    whoami: "/pipelines/dashboard/whoami"
};

// ── state ────────────────────────────────────────────

let selectedId = null;
let eventSource = null;

// ── boot ─────────────────────────────────────────────

document.addEventListener("DOMContentLoaded", async () => {
    document.getElementById("refresh-list").addEventListener("click", loadPipelineList);
    await Promise.all([loadTenant(), loadPipelineList()]);
});

// ── tenant ───────────────────────────────────────────

async function loadTenant() {
    try {
        const res = await fetch(API.whoami);
        if (!res.ok) return;
        const body = await res.json();
        if (body.multiTenant && body.tenantId) {
            document.getElementById("tenant-name").textContent =
                    body.tenantName || body.tenantId;
            document.getElementById("tenant-indicator").hidden = false;
        }
    } catch (e) {
        // Non-fatal — the dashboard still works.
    }
}

// ── pipeline list ────────────────────────────────────

async function loadPipelineList() {
    const list = document.getElementById("pipeline-list");
    list.innerHTML = "<li class=\"placeholder\">Loading…</li>";
    try {
        const res = await fetch(API.pipelinesList);
        if (!res.ok) {
            list.innerHTML = `<li class="placeholder">Error ${res.status}</li>`;
            return;
        }
        const body = await res.json();
        const pipelines = Array.isArray(body.pipelines) ? body.pipelines : [];
        renderPipelineList(pipelines);
    } catch (e) {
        list.innerHTML = `<li class="placeholder">Network error: ${escapeHtml(e.message)}</li>`;
    }
}

function renderPipelineList(pipelines) {
    const list = document.getElementById("pipeline-list");
    if (pipelines.length === 0) {
        list.innerHTML = "<li class=\"placeholder\">No pipelines registered</li>";
        return;
    }
    list.innerHTML = "";
    for (const p of pipelines) {
        const id = p.id || p.pipelineId;
        const name = p.name || id;
        const li = document.createElement("li");
        li.dataset.pipelineId = id;
        li.textContent = name;
        if (id === selectedId) li.classList.add("selected");
        li.addEventListener("click", () => selectPipeline(id));
        list.appendChild(li);
    }
}

// ── selection ────────────────────────────────────────

async function selectPipeline(id) {
    selectedId = id;
    for (const li of document.querySelectorAll("#pipeline-list li")) {
        li.classList.toggle("selected", li.dataset.pipelineId === id);
    }
    await renderDetail(id);
    subscribeToEvents(id);
}

async function renderDetail(id) {
    const panel = document.getElementById("detail-panel");
    const template = document.getElementById("pipeline-detail-template");
    const fragment = template.content.cloneNode(true);
    panel.innerHTML = "";
    panel.appendChild(fragment);

    const root = panel.querySelector(".pipeline-detail");
    root.querySelector(".pipeline-name").textContent = id;
    root.querySelector(".pipeline-id").textContent = id;
    hookTriggerForm(root, id);

    // Fetch definition + executions + rendered SVG in parallel.
    const [detail, svg] = await Promise.all([
        fetch(API.pipelineDetail(id)).then(r => r.ok ? r.json() : null).catch(() => null),
        fetch(API.render(id)).then(r => r.ok ? r.text() : "").catch(() => "")
    ]);

    root.querySelector(".pipeline-flow").innerHTML = svg;
    if (detail) {
        if (detail.name) root.querySelector(".pipeline-name").textContent = detail.name;
        renderExecutions(root, detail.recentExecutions || detail.executions || []);
    }
}

function renderExecutions(root, executions) {
    const tbody = root.querySelector(".executions-body");
    if (!executions.length) {
        tbody.innerHTML = "<tr class=\"placeholder\"><td colspan=\"4\">No executions yet</td></tr>";
        return;
    }
    tbody.innerHTML = "";
    for (const e of executions) {
        const tr = document.createElement("tr");
        const shortId = (e.executionId || "").substring(0, 8);
        const status = e.status || "?";
        const started = e.startedAt ? new Date(e.startedAt).toLocaleTimeString() : "—";
        const duration = e.totalDurationMs != null
                ? `${e.totalDurationMs} ms`
                : (e.completedAt && e.startedAt)
                        ? `${new Date(e.completedAt) - new Date(e.startedAt)} ms`
                        : "—";
        tr.innerHTML = `<td>${escapeHtml(shortId)}…</td>` +
                       `<td class="status-${escapeHtml(status)}">${escapeHtml(status)}</td>` +
                       `<td>${escapeHtml(started)}</td>` +
                       `<td>${escapeHtml(duration)}</td>`;
        tbody.appendChild(tr);
    }
}

// ── trigger form ─────────────────────────────────────

function hookTriggerForm(root, id) {
    const form = root.querySelector(".trigger-form");
    const result = root.querySelector(".trigger-result");
    form.addEventListener("submit", async (e) => {
        e.preventDefault();
        const alias = form.alias.value.trim();
        const input = form.input.value;
        if (!alias) {
            result.textContent = "Alias required — see jaiclaw.pipeline.http-trigger.allowed";
            return;
        }
        result.textContent = "Triggering…";
        try {
            const res = await fetch(API.trigger, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ alias, input })
            });
            const body = await res.text();
            result.textContent = res.ok
                    ? `Triggered — ${body.substring(0, 60)}`
                    : `Error ${res.status} — ${body.substring(0, 120)}`;
        } catch (err) {
            result.textContent = `Network error: ${err.message}`;
        }
    });
}

// ── live events ──────────────────────────────────────

function subscribeToEvents(id) {
    if (eventSource) {
        eventSource.close();
        eventSource = null;
    }
    const status = document.getElementById("connection-status");
    status.className = "connection-status";

    const source = new EventSource(API.events(id));
    eventSource = source;

    source.addEventListener("open", () => status.classList.add("connected"));
    source.addEventListener("error", () => {
        status.classList.remove("connected");
        status.classList.add("error");
    });

    const eventNames = [
        "snapshot",
        "execution-started",
        "execution-completed",
        "execution-failed",
        "stage-started",
        "stage-completed",
        "stage-failed"
    ];
    for (const name of eventNames) {
        source.addEventListener(name, (ev) => handleEvent(name, ev));
    }
}

function handleEvent(name, ev) {
    const panel = document.getElementById("detail-panel");
    const log = panel.querySelector(".event-log");
    if (!log) return;

    let payload = null;
    try { payload = JSON.parse(ev.data); } catch (e) { /* raw */ }

    const li = document.createElement("li");
    const stage = payload && (payload.stageName || payload.stage) || "";
    const stageMarkup = stage ? ` <span class="event-stage">[${escapeHtml(stage)}]</span>` : "";
    li.innerHTML = `<span class="event-name">${escapeHtml(name)}</span>${stageMarkup} ${escapeHtml(new Date().toLocaleTimeString())}`;
    log.prepend(li);
    while (log.children.length > 200) log.removeChild(log.lastChild);

    // Overlay: apply per-stage CSS classes to the SVG.
    if (stage) {
        const flow = panel.querySelector(".pipeline-flow");
        const target = flow ? flow.querySelector(`[data-stage="${cssEscape(stage)}"]`) : null;
        if (target) {
            if (name === "stage-started") {
                clearStageClasses(flow);
                target.classList.add("active");
            } else if (name === "stage-completed") {
                target.classList.remove("active");
                target.classList.add("completed");
            } else if (name === "stage-failed") {
                target.classList.remove("active");
                target.classList.add("failed");
            }
        }
    } else if (name === "execution-started") {
        const flow = panel.querySelector(".pipeline-flow");
        if (flow) clearStageClasses(flow);
    } else if (name === "execution-completed" || name === "execution-failed") {
        // Refresh executions table so the just-finished run shows up.
        if (selectedId) {
            fetch(API.pipelineDetail(selectedId))
                .then(r => r.ok ? r.json() : null)
                .then(d => {
                    if (d) {
                        const root = panel.querySelector(".pipeline-detail");
                        if (root) renderExecutions(root, d.recentExecutions || d.executions || []);
                    }
                })
                .catch(() => {});
        }
    }
}

function clearStageClasses(flow) {
    for (const el of flow.querySelectorAll("[data-stage]")) {
        el.classList.remove("active", "completed", "failed");
    }
}

// ── helpers ──────────────────────────────────────────

function escapeHtml(s) {
    if (s == null) return "";
    return String(s)
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
}

function cssEscape(s) {
    if (window.CSS && typeof window.CSS.escape === "function") return window.CSS.escape(s);
    return String(s).replace(/[^a-zA-Z0-9_-]/g, "\\$&");
}
