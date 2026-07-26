import {
  Background,
  Controls,
  ReactFlow,
  type Edge,
  type Node,
  type NodeProps
} from "@xyflow/react";
import { useMemo } from "react";
import type { PipelineDefinition } from "../types/pipeline";

// Linear vertical canvas: [Trigger] → [Stage] → ... → [Output].
// Nodes are laid out on a fixed Y offset per rank because Phase 1 of
// the Studio buildout only ships linear pipelines. When engine
// branching (switch/parallel) lands, this file swaps to auto-layout.

const NODE_SPACING_Y = 110;
const NODE_X = 0;

interface CanvasProps {
  definition: PipelineDefinition;
  selectedStageName: string | null;
  onSelectStage: (name: string | null) => void;
  onInsertAt: (index: number) => void;
}

type NodeKind = "trigger" | "stage" | "output";

interface NodeData extends Record<string, unknown> {
  kind: NodeKind;
  title: string;
  subtitle?: string;
  stageName?: string;
  selected?: boolean;
}

function NodeCard({ data }: NodeProps) {
  const nd = data as NodeData;
  return (
    <div className={`rf-node ${nd.kind}${nd.selected ? " selected" : ""}`}>
      <span className="kind">{nd.kind}</span>
      <span className="name">{nd.title}</span>
      {nd.subtitle && <div className="subtitle">{nd.subtitle}</div>}
    </div>
  );
}

const NODE_TYPES = { pipeline: NodeCard } as const;

export function Canvas({
  definition,
  selectedStageName,
  onSelectStage,
  onInsertAt
}: CanvasProps) {
  const nodes: Node<NodeData>[] = useMemo(() => {
    const list: Node<NodeData>[] = [];
    let rank = 0;
    list.push({
      id: "__trigger__",
      type: "pipeline",
      position: { x: NODE_X, y: rank++ * NODE_SPACING_Y },
      data: {
        kind: "trigger",
        title: definition.trigger?.type ?? "MANUAL",
        subtitle: definition.trigger?.uri ?? undefined
      }
    });
    for (const stage of definition.stages) {
      list.push({
        id: `stage-${stage.name}`,
        type: "pipeline",
        position: { x: NODE_X, y: rank++ * NODE_SPACING_Y },
        data: {
          kind: "stage",
          title: stage.name,
          subtitle: `${stage.type}${stage.bean ? ` · ${stage.bean}` : ""}${
            stage.agentId ? ` · ${stage.agentId}` : ""
          }`,
          stageName: stage.name,
          selected: stage.name === selectedStageName
        }
      });
    }
    list.push({
      id: "__output__",
      type: "pipeline",
      position: { x: NODE_X, y: rank++ * NODE_SPACING_Y },
      data: {
        kind: "output",
        title: definition.output?.type ?? "NONE",
        subtitle: definition.output?.channelId ?? definition.output?.uri ?? undefined
      }
    });
    return list;
  }, [definition, selectedStageName]);

  const edges: Edge[] = useMemo(() => {
    const list: Edge[] = [];
    for (let i = 0; i < nodes.length - 1; i++) {
      list.push({
        id: `e-${nodes[i].id}-${nodes[i + 1].id}`,
        source: nodes[i].id,
        target: nodes[i + 1].id,
        type: "smoothstep"
      });
    }
    return list;
  }, [nodes]);

  if (definition.stages.length === 0) {
    return (
      <div className="canvas-empty" data-testid="canvas-empty">
        Drag a stage from the palette to start building the pipeline.
        <br />
        <button
          className="btn secondary"
          style={{ marginTop: 12 }}
          onClick={() => onInsertAt(0)}
        >
          Add first stage
        </button>
      </div>
    );
  }

  return (
    <ReactFlow
      nodes={nodes}
      edges={edges}
      nodeTypes={NODE_TYPES}
      fitView
      fitViewOptions={{ padding: 0.3 }}
      onNodeClick={(_, node) => {
        const nd = node.data as NodeData;
        if (nd.stageName) onSelectStage(nd.stageName);
        else onSelectStage(null);
      }}
      onPaneClick={() => onSelectStage(null)}
      defaultEdgeOptions={{ animated: false }}
    >
      <Background gap={16} size={1} />
      <Controls showInteractive={false} />
    </ReactFlow>
  );
}
