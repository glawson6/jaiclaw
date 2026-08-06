import { ReactFlowProvider } from "@xyflow/react";
import { useCallback, useEffect, useState } from "react";
import { api } from "../api/client";
import { useDraftStore } from "../state/draftStore";
import type { Catalog, ValidationReport } from "../types/pipeline";
import { Canvas } from "./Canvas";
import { DeployToolbar } from "./DeployToolbar";
import { Inspector } from "./Inspector";
import { Palette } from "./Palette";
import { ValidationPanel } from "./ValidationPanel";
import { YamlView } from "./YamlView";

// Root shell. Owns the draft-load / save cycle, catalog load, and
// switches between canvas + YAML mode. Everything under it is
// presentational — mutations flow up through onXxx callbacks so
// components can be unit-tested without the App wrapper.

export function App() {
  const store = useDraftStore();
  const [catalog, setCatalog] = useState<Catalog | null>(null);
  const [report, setReport] = useState<ValidationReport | null>(null);
  const [yamlMode, setYamlMode] = useState(false);
  const [saveStatus, setSaveStatus] = useState<string>("");
  const [currentDraftRevision, setCurrentDraftRevision] = useState<number | null>(null);

  useEffect(() => {
    api.catalog().then(setCatalog).catch(() => {
      // Non-fatal — the palette shows built-in generic entries anyway.
    });
  }, []);

  const validate = useCallback(async () => {
    try {
      const r = await api.validate(store.definition);
      setReport(r);
    } catch (e) {
      setReport({
        hasErrors: true,
        formatted: String(e),
        errors: [{ code: "NETWORK", message: String(e) }]
      });
    }
  }, [store.definition]);

  const save = useCallback(async () => {
    setSaveStatus("Saving…");
    try {
      const saved = currentDraftRevision === null
        ? await api.createDraft(store.definition)
        : await api.updateDraft(store.definition.id, store.definition, currentDraftRevision);
      setCurrentDraftRevision(saved.revision);
      store.markPristine();
      setSaveStatus(`Saved (rev ${saved.revision})`);
    } catch (e) {
      setSaveStatus(String(e));
    }
  }, [store, currentDraftRevision]);

  return (
    <div className="studio-app">
      {/* Section 508 / WCAG 2.0 AA — semantic landmarks for screen readers */}
      <header className="studio-header" role="banner">
        <h1>Pipeline Studio</h1>
        <span className="draft-id" aria-label="Draft identifier">{store.definition.id}</span>
        <span
          className={`status${store.dirty ? " dirty" : ""}`}
          role="status"
          aria-live="polite"
        >
          {store.dirty ? "unsaved changes" : "clean"}
        </span>
        <div className="spacer" />
        <div className="header-actions" role="toolbar" aria-label="Studio actions">
          <button
            type="button"
            className="btn secondary"
            onClick={() => setYamlMode((v) => !v)}
            aria-pressed={yamlMode}
          >
            {yamlMode ? "Canvas" : "YAML"}
          </button>
          <button
            type="button"
            className="btn secondary"
            onClick={validate}
          >
            Validate
          </button>
          <button type="button" className="btn" onClick={save}>
            Save
          </button>
        </div>
      </header>

      <main className="studio-body" role="main" aria-label="Pipeline editor">
        {!yamlMode && (
          <aside role="complementary" aria-label="Stage palette">
            <Palette
              catalog={catalog}
              onInsert={(stage) => store.insertStage(store.definition.stages.length, stage)}
            />
          </aside>
        )}

        <section className="pane canvas" aria-label={yamlMode ? "YAML editor" : "Pipeline canvas"}>
          <div className="pane-header">
            {yamlMode ? "YAML" : "Canvas"}
            {saveStatus && (
              <span
                style={{ float: "right", color: "var(--text-dim)" }}
                role="status"
                aria-live="polite"
              >
                {saveStatus}
              </span>
            )}
          </div>
          <div style={{ flex: 1, position: "relative", display: "flex", flexDirection: "column" }}>
            {yamlMode ? (
              <YamlView
                definition={store.definition}
                onReplace={(next) => store.load(next)}
                onClose={() => setYamlMode(false)}
              />
            ) : (
              <ReactFlowProvider>
                <Canvas
                  definition={store.definition}
                  selectedStageName={store.selectedStageName}
                  onSelectStage={store.selectStage}
                  onInsertAt={(_) => {
                    // "Add first stage" button — same builtin PROCESSOR
                    // template as the palette's default.
                    store.insertStage(0, {
                      name: `stage-${store.definition.stages.length + 1}`,
                      type: "PROCESSOR",
                      bean: ""
                    });
                  }}
                />
              </ReactFlowProvider>
            )}
          </div>
          {report && (
            <div
              style={{ padding: 12, borderTop: "1px solid var(--border)" }}
              role="region"
              aria-label="Validation results"
              aria-live="polite"
            >
              <ValidationPanel
                report={report}
                onFocusStage={(name) => store.selectStage(name)}
              />
            </div>
          )}
          <div
            style={{ padding: 12, borderTop: "1px solid var(--border)" }}
            role="region"
            aria-label="Deploy actions"
          >
            <DeployToolbar
              draftId={store.definition.id}
              definition={store.definition}
            />
          </div>
        </section>

        {!yamlMode && (
          <aside role="complementary" aria-label="Stage inspector">
            <Inspector
              definition={store.definition}
              selectedStageName={store.selectedStageName}
              catalog={catalog}
              onUpdatePipeline={store.updatePipeline}
              onUpdateStage={store.updateStage}
              onRemoveStage={store.removeStage}
              onMoveStage={store.moveStage}
            />
          </aside>
        )}
      </main>
    </div>
  );
}
