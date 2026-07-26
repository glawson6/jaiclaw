import Form from "@rjsf/core";
import validator from "@rjsf/validator-ajv8";
import { useMemo } from "react";
import type {
  Catalog,
  PipelineDefinition,
  StageDefinition
} from "../types/pipeline";

// Inspector: shows the settings for whichever node is selected. Two
// modes:
//   • no stage selected → pipeline-level fields (name, description,
//     resultTemplate, errorStrategy)
//   • stage selected → stage-type-specific panel (AGENT / PROCESSOR /
//     CAMEL). PROCESSOR nodes whose bean carries a config schema get
//     an @rjsf schema-driven form for the config map.

interface InspectorProps {
  definition: PipelineDefinition;
  selectedStageName: string | null;
  catalog: Catalog | null;
  onUpdatePipeline: (patch: Partial<PipelineDefinition>) => void;
  onUpdateStage: (name: string, patch: Partial<StageDefinition>) => void;
  onRemoveStage: (name: string) => void;
  onMoveStage: (name: string, delta: -1 | 1) => void;
}

export function Inspector(props: InspectorProps) {
  const { definition, selectedStageName, catalog } = props;
  const selectedStage = selectedStageName
    ? definition.stages.find((s) => s.name === selectedStageName) ?? null
    : null;

  return (
    <div className="pane inspector" data-testid="inspector">
      <div className="pane-header">Inspector</div>
      <div className="pane-body">
        {selectedStage ? (
          <StagePanel
            stage={selectedStage}
            catalog={catalog}
            onChange={(patch) => props.onUpdateStage(selectedStage.name, patch)}
            onRemove={() => props.onRemoveStage(selectedStage.name)}
            onMove={(d) => props.onMoveStage(selectedStage.name, d)}
          />
        ) : (
          <PipelinePanel
            definition={definition}
            onChange={props.onUpdatePipeline}
          />
        )}
      </div>
    </div>
  );
}

function PipelinePanel({
  definition,
  onChange
}: {
  definition: PipelineDefinition;
  onChange: (patch: Partial<PipelineDefinition>) => void;
}) {
  return (
    <>
      <div className="form-group">
        <label htmlFor="pipe-id">Id</label>
        <input
          id="pipe-id"
          type="text"
          value={definition.id}
          onChange={(e) => onChange({ id: e.target.value })}
        />
      </div>
      <div className="form-group">
        <label htmlFor="pipe-name">Name</label>
        <input
          id="pipe-name"
          type="text"
          value={definition.name ?? ""}
          onChange={(e) => onChange({ name: e.target.value })}
        />
      </div>
      <div className="form-group">
        <label htmlFor="pipe-desc">Description</label>
        <textarea
          id="pipe-desc"
          value={definition.description ?? ""}
          onChange={(e) => onChange({ description: e.target.value })}
        />
      </div>
      <div className="form-group">
        <label htmlFor="pipe-error">Error strategy</label>
        <select
          id="pipe-error"
          value={definition.errorStrategy ?? "STOP"}
          onChange={(e) => onChange({ errorStrategy: e.target.value as PipelineDefinition["errorStrategy"] })}
        >
          <option value="STOP">STOP</option>
          <option value="RETRY_THEN_FAIL">RETRY_THEN_FAIL</option>
          <option value="DEAD_LETTER">DEAD_LETTER</option>
        </select>
      </div>
      <div className="form-group">
        <label htmlFor="pipe-result">Result template</label>
        <textarea
          id="pipe-result"
          value={definition.resultTemplate ?? ""}
          placeholder='e.g. "Processed: {{stages.last.output}}"'
          onChange={(e) => onChange({ resultTemplate: e.target.value })}
        />
        <div className="form-help">
          Placeholders: <code>{"{{"}stages.X.output{"}}"}</code>,{" "}
          <code>{"{{"}input{"}}"}</code>, <code>{"{{"}pipeline.id{"}}"}</code>.
        </div>
      </div>
    </>
  );
}

function StagePanel({
  stage,
  catalog,
  onChange,
  onRemove,
  onMove
}: {
  stage: StageDefinition;
  catalog: Catalog | null;
  onChange: (patch: Partial<StageDefinition>) => void;
  onRemove: () => void;
  onMove: (delta: -1 | 1) => void;
}) {
  const processorSchema = useMemo(() => {
    if (stage.type !== "PROCESSOR" || !catalog) return null;
    const p = (catalog.processors ?? []).find((x) => x.beanName === stage.bean);
    if (!p?.configSchema) return null;
    try {
      return JSON.parse(p.configSchema);
    } catch {
      return null;
    }
  }, [stage, catalog]);

  return (
    <>
      <div style={{ display: "flex", gap: 6, marginBottom: 12 }}>
        <button type="button" className="btn secondary" onClick={() => onMove(-1)}>↑</button>
        <button type="button" className="btn secondary" onClick={() => onMove(1)}>↓</button>
        <button type="button" className="btn secondary" onClick={onRemove}>Remove</button>
      </div>
      <div className="form-group">
        <label htmlFor="stage-name">Name</label>
        <input
          id="stage-name"
          type="text"
          value={stage.name}
          onChange={(e) => onChange({ name: e.target.value })}
        />
      </div>
      <div className="form-group">
        <label htmlFor="stage-type">Type</label>
        <select
          id="stage-type"
          value={stage.type}
          onChange={(e) => onChange({ type: e.target.value as StageDefinition["type"] })}
        >
          <option value="AGENT">AGENT</option>
          <option value="PROCESSOR">PROCESSOR</option>
          <option value="CAMEL">CAMEL</option>
        </select>
      </div>

      {stage.type === "AGENT" && (
        <>
          <div className="form-group">
            <label htmlFor="stage-agent">Agent id</label>
            <input
              id="stage-agent"
              type="text"
              value={stage.agentId ?? ""}
              onChange={(e) => onChange({ agentId: e.target.value })}
            />
          </div>
          <div className="form-group">
            <label htmlFor="stage-prompt">System prompt</label>
            <textarea
              id="stage-prompt"
              value={stage.systemPrompt ?? ""}
              onChange={(e) => onChange({ systemPrompt: e.target.value })}
            />
            <div className="form-help">
              Supports <code>{"{{"}stages.X.output{"}}"}</code>, <code>{"{{"}input{"}}"}</code>.
            </div>
          </div>
          <div className="form-group">
            <label htmlFor="stage-channel">Channel id</label>
            <input
              id="stage-channel"
              type="text"
              value={stage.channelId ?? ""}
              placeholder="pipeline-internal"
              onChange={(e) => onChange({ channelId: e.target.value })}
            />
          </div>
        </>
      )}

      {stage.type === "PROCESSOR" && (
        <>
          <div className="form-group">
            <label htmlFor="stage-bean">Bean name</label>
            <input
              id="stage-bean"
              type="text"
              value={stage.bean ?? ""}
              onChange={(e) => onChange({ bean: e.target.value })}
            />
          </div>
          {processorSchema && (
            <div className="form-group">
              <label>Config</label>
              <Form
                schema={processorSchema}
                validator={validator}
                formData={stage.config ?? {}}
                onChange={(evt) => onChange({ config: evt.formData as Record<string, string> })}
                liveValidate={false}
                showErrorList={false}
              >
                {/* rjsf renders a submit button by default — hide it. */}
                <span />
              </Form>
            </div>
          )}
        </>
      )}

      {stage.type === "CAMEL" && (
        <div className="form-group">
          <label htmlFor="stage-uri">Camel URI</label>
          <input
            id="stage-uri"
            type="text"
            value={stage.uri ?? ""}
            placeholder="e.g. seda:my-queue, log:foo"
            onChange={(e) => onChange({ uri: e.target.value })}
          />
          <div className="form-help">
            Phase 3 will enforce a URI-scheme allowlist on UI-authored pipelines.
          </div>
        </div>
      )}
    </>
  );
}
