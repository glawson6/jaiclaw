# Canvas Dashboard Example

On-demand interactive dashboards via chat. Ask the agent to create a dashboard and it generates a live HTML page with Chart.js charts, rendered via Canvas (A2UI).

## What It Demonstrates

- **Canvas (A2UI)** — Agent generates self-contained HTML and pushes it via `canvas_present`, returning a URL you can open in any browser
- **Custom tools** — `get_system_metrics` returns live JVM/OS data; `get_project_status` returns simulated project management data
- **Skill-guided generation** — A custom skill teaches the agent to produce polished Chart.js dashboards with a dark theme and responsive grid

## Running

From the project root:

```bash
ANTHROPIC_API_KEY=sk-ant-... ./mvnw spring-boot:run -pl :jaiclaw-example-canvas-dashboard
```

### Using MiniMax

To run with MiniMax instead of Anthropic:

```bash
AI_PROVIDER=minimax MINIMAX_ENABLED=true MINIMAX_API_KEY=your-key ../../mvnw spring-boot:run
```


Or from the example directory:

```bash
cd jaiclaw-examples/canvas-dashboard
ANTHROPIC_API_KEY=sk-ant-... ../../mvnw spring-boot:run
```

## Try It

```bash
# System metrics dashboard (real JVM data)
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $(cat ~/.jaiclaw/api-key)" \
  -d '{"content": "Create a system metrics dashboard"}'

# Project status dashboard (simulated sprint data)
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $(cat ~/.jaiclaw/api-key)" \
  -d '{"content": "Show me a project status dashboard with sprint progress and team velocity"}'

# Combined dashboard
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $(cat ~/.jaiclaw/api-key)" \
  -d '{"content": "Create a full dashboard with both system metrics and project status"}'
```

The response will include a canvas URL (e.g., `http://127.0.0.1:18793/...`). Open it in a browser to see the rendered dashboard.

## Architecture

```
CanvasDashboardApplication
  └── DashboardConfig
        ├── CanvasFileManager (temp dir for HTML files)
        ├── CanvasService (presents HTML, returns URL)
        └── ApplicationRunner (registers canvas + data tools into ToolRegistry)
              ├── canvas_present, canvas_eval, canvas_snapshot (from CanvasTools)
              ├── get_system_metrics (live JVM/OS data)
              └── get_project_status (simulated sprint/task data)
```
