import yaml from "js-yaml";
import { useEffect, useState } from "react";
import type { PipelineDefinition } from "../types/pipeline";

interface YamlViewProps {
  definition: PipelineDefinition;
  onReplace: (next: PipelineDefinition) => void;
  onClose: () => void;
}

// Two-way YAML editor. Applying a valid parse replaces the entire
// definition — good enough for Phase 2 which doesn't try to keep the
// canvas and YAML in sync mid-edit (adopters get a "apply" button).

export function YamlView({ definition, onReplace, onClose }: YamlViewProps) {
  const [text, setText] = useState(() => yaml.dump(definition, { indent: 2, lineWidth: 100 }));
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setText(yaml.dump(definition, { indent: 2, lineWidth: 100 }));
    setError(null);
  }, [definition]);

  const apply = () => {
    try {
      const parsed = yaml.load(text);
      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
        setError("YAML must parse to an object");
        return;
      }
      onReplace(parsed as PipelineDefinition);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  };

  const download = () => {
    const blob = new Blob([text], { type: "application/x-yaml" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${definition.id || "pipeline"}.yml`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="yaml-view" data-testid="yaml-view">
      <div style={{ display: "flex", gap: 8, marginBottom: 8 }}>
        <button className="btn" type="button" onClick={apply}>Apply</button>
        <button className="btn secondary" type="button" onClick={download}>Download YAML</button>
        <button className="btn secondary" type="button" onClick={onClose}>Back to canvas</button>
      </div>
      {error && (
        <div className="validation-panel errors" style={{ marginBottom: 8 }}>
          {error}
        </div>
      )}
      <textarea
        value={text}
        onChange={(e) => setText(e.target.value)}
        spellCheck={false}
        aria-label="YAML editor"
      />
    </div>
  );
}
