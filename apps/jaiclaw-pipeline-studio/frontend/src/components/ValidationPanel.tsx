import type { ValidationReport } from "../types/pipeline";

interface ValidationPanelProps {
  report: ValidationReport | null;
  onFocusStage?: (stageName: string) => void;
}

// Renders the {@link ValidationReport} the backend returns. Error
// entries whose `location` names a stage (e.g. `stage 'find-account'`)
// are click-to-focus — the parent wires the click to
// `onSelectStage(name)`.

const STAGE_LOCATION_PATTERN = /^stage '(.+)'$/;

export function ValidationPanel({
  report,
  onFocusStage
}: ValidationPanelProps) {
  if (!report) return null;
  if (!report.hasErrors) {
    return (
      <div className="validation-panel ok" data-testid="validation-panel">
        Validation: no errors.
      </div>
    );
  }
  return (
    <div className="validation-panel errors" data-testid="validation-panel">
      <strong>{report.errors.length} validation error{report.errors.length === 1 ? "" : "s"}</strong>
      <ul>
        {report.errors.map((e, i) => {
          const stage = STAGE_LOCATION_PATTERN.exec(e.location ?? "")?.[1];
          return (
            <li key={i}>
              {stage ? (
                <button
                  type="button"
                  className="btn secondary"
                  style={{ padding: "2px 6px", fontSize: 11, marginRight: 6 }}
                  onClick={() => onFocusStage?.(stage)}
                >
                  {stage}
                </button>
              ) : (
                <span style={{ color: "var(--text-dim)", marginRight: 6 }}>
                  {e.location ?? "pipeline"}:
                </span>
              )}
              <code>{e.code}</code> — {e.message}
              {e.suggestion && (
                <>
                  {" "}<span className="suggest">did you mean “{e.suggestion}”?</span>
                </>
              )}
            </li>
          );
        })}
      </ul>
    </div>
  );
}
