import { useState } from "react";
import type { DeployResponse, TestRunResponse } from "../api/client";
import { api } from "../api/client";
import type { PipelineDefinition } from "../types/pipeline";

interface DeployToolbarProps {
  draftId: string | null;
  definition: PipelineDefinition;
}

// Deploy / undeploy / redeploy + test-run controls. Renders as a
// horizontal strip in the header. All four buttons hit the Phase 3
// authoring API. The status line under each button carries the last
// response for that action so failures are visible next to the
// trigger.

export function DeployToolbar({ draftId, definition }: DeployToolbarProps) {
  const [status, setStatus] = useState<string>("");
  const [testInput, setTestInput] = useState<string>("");
  const [testResult, setTestResult] = useState<TestRunResponse | null>(null);
  const [testRunning, setTestRunning] = useState(false);

  const targetId = draftId ?? definition.id;
  const canAct = Boolean(targetId);

  const withStatus = async (label: string, action: () => Promise<DeployResponse>) => {
    setStatus(`${label}…`);
    try {
      const res = await action();
      setStatus(`${label}: ${res.pipelineId} → ${res.status}`);
    } catch (e) {
      setStatus(`${label} failed: ${e instanceof Error ? e.message : String(e)}`);
    }
  };

  const runTest = async () => {
    if (!targetId) return;
    setTestRunning(true);
    setTestResult(null);
    try {
      const res = await api.testRun(targetId, testInput);
      setTestResult(res);
    } catch (e) {
      setTestResult({
        executionId: "",
        status: "ERROR",
        totalDurationMs: 0,
        stageOutputs: {},
        failureReason: e instanceof Error ? e.message : String(e),
        startedAt: new Date().toISOString()
      });
    } finally {
      setTestRunning(false);
    }
  };

  return (
    <div data-testid="deploy-toolbar" style={{ display: "flex", flexDirection: "column", gap: 8 }}>
      <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
        <button
          type="button"
          className="btn"
          disabled={!canAct}
          onClick={() => withStatus("Deploy", () => api.deploy(targetId!))}
        >
          Deploy
        </button>
        <button
          type="button"
          className="btn secondary"
          disabled={!canAct}
          onClick={() => withStatus("Undeploy", () => api.undeploy(targetId!))}
        >
          Undeploy
        </button>
        <button
          type="button"
          className="btn secondary"
          disabled={!canAct}
          onClick={() => withStatus("Redeploy", () => api.redeploy(targetId!, definition))}
        >
          Redeploy
        </button>
      </div>
      {status && (
        <div style={{ fontSize: 11, color: "var(--text-dim)" }} data-testid="deploy-status">
          {status}
        </div>
      )}
      <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
        <input
          type="text"
          placeholder="Test-run input payload"
          value={testInput}
          onChange={(e) => setTestInput(e.target.value)}
          aria-label="Test-run input"
          style={{
            flex: 1,
            padding: "4px 8px",
            background: "var(--bg)",
            color: "var(--text)",
            border: "1px solid var(--border)",
            borderRadius: 4,
            fontSize: 12
          }}
        />
        <button
          type="button"
          className="btn"
          disabled={!canAct || testRunning}
          onClick={runTest}
        >
          {testRunning ? "Running…" : "Test-run"}
        </button>
      </div>
      {testResult && (
        <div
          data-testid="test-result"
          className={`validation-panel ${testResult.status === "SUCCESS" ? "ok" : "errors"}`}
          style={{ fontSize: 12 }}
        >
          <div>
            <strong>{testResult.status}</strong> — {testResult.totalDurationMs} ms —
            exec <code>{testResult.executionId.substring(0, 8) || "—"}</code>
          </div>
          {testResult.failureReason && (
            <div style={{ marginTop: 4, color: "var(--danger)" }}>
              {testResult.failureReason}
            </div>
          )}
          {Object.keys(testResult.stageOutputs).length > 0 && (
            <details style={{ marginTop: 4 }}>
              <summary>stage outputs</summary>
              <pre style={{ margin: 0, fontSize: 11 }}>
                {JSON.stringify(testResult.stageOutputs, null, 2)}
              </pre>
            </details>
          )}
        </div>
      )}
    </div>
  );
}
