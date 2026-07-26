import { useMemo, useState } from "react";
import type {
  Catalog,
  StageDefinition,
  StageType
} from "../types/pipeline";

// The palette groups every draggable node by category. Phase 1 of the
// buildout only ships PROCESSOR beans + custom Function beans + a
// stubbed camel-templates section (populated in Phase 4). The palette
// tolerates an empty catalog gracefully — it always shows the built-in
// stage-type buckets (AGENT / CAMEL / PROCESSOR) as generic
// "empty stage" cards so the user can start assembling before any
// processor beans register.

interface PaletteProps {
  catalog: Catalog | null;
  onInsert: (stage: StageDefinition) => void;
}

interface PaletteEntry {
  category: string;
  title: string;
  description?: string;
  build: () => StageDefinition;
}

export function Palette({ catalog, onInsert }: PaletteProps) {
  const [query, setQuery] = useState("");

  const entries: PaletteEntry[] = useMemo(() => {
    const list: PaletteEntry[] = [];

    // Built-in generic stage templates — always available.
    list.push({
      category: "AI",
      title: "Agent",
      description: "Invoke an agent with a system prompt",
      build: () => genericStage("agent", "AGENT", { agentId: "" })
    });
    list.push({
      category: "Integration",
      title: "Camel URI",
      description: "Route through any allowlisted Camel endpoint",
      build: () => genericStage("camel", "CAMEL", { uri: "" })
    });
    list.push({
      category: "Custom",
      title: "Custom Bean",
      description: "Function<String,String> Spring bean by name",
      build: () => genericStage("processor", "PROCESSOR", { bean: "" })
    });

    // Discovered @PipelineProcessor beans.
    if (catalog) {
      for (const p of catalog.processors ?? []) {
        list.push({
          category: p.category || "Custom",
          title: p.name,
          description: p.description,
          build: () => genericStage(
            slugify(p.name),
            "PROCESSOR",
            { bean: p.beanName, config: {} }
          )
        });
      }
      for (const beanName of catalog.customBeans ?? []) {
        list.push({
          category: "Custom",
          title: beanName,
          description: "Function<String,String> bean",
          build: () => genericStage(
            slugify(beanName),
            "PROCESSOR",
            { bean: beanName }
          )
        });
      }
    }
    return list;
  }, [catalog]);

  const filtered = query.trim()
    ? entries.filter((e) =>
        e.title.toLowerCase().includes(query.toLowerCase())
        || (e.description ?? "").toLowerCase().includes(query.toLowerCase()))
    : entries;

  const grouped = useMemo(() => {
    const map = new Map<string, PaletteEntry[]>();
    for (const e of filtered) {
      const list = map.get(e.category) ?? [];
      list.push(e);
      map.set(e.category, list);
    }
    return Array.from(map.entries()).sort(([a], [b]) => a.localeCompare(b));
  }, [filtered]);

  return (
    <div className="pane palette" data-testid="palette">
      <div className="pane-header">Palette</div>
      <div className="pane-body">
        <input
          className="palette-search"
          type="search"
          placeholder="Search…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          aria-label="Search palette"
        />
        {grouped.length === 0 && (
          <p style={{ fontSize: 12, color: "var(--text-dim)" }}>
            No matching palette entries.
          </p>
        )}
        {grouped.map(([category, group]) => (
          <div className="palette-group" key={category}>
            <h3>{category}</h3>
            {group.map((entry) => (
              <button
                key={entry.title}
                type="button"
                className="palette-item"
                onClick={() => onInsert(entry.build())}
                title={entry.description ?? entry.title}
              >
                {entry.title}
                {entry.description && (
                  <span className="desc">{entry.description}</span>
                )}
              </button>
            ))}
          </div>
        ))}
      </div>
    </div>
  );
}

function slugify(s: string): string {
  return s.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
}

let stageSequence = 0;
function genericStage(
  namePrefix: string,
  type: StageType,
  extras: Partial<StageDefinition>
): StageDefinition {
  stageSequence += 1;
  return {
    name: `${namePrefix}-${stageSequence}`,
    type,
    ...extras
  };
}

// For test cleanup — resets the monotonic name suffix so specs are
// deterministic across runs.
export function __resetPaletteSequenceForTests(): void {
  stageSequence = 0;
}
