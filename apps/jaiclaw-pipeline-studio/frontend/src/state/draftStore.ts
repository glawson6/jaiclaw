import { useCallback, useMemo, useState } from "react";
import type {
  PipelineDefinition,
  StageDefinition
} from "../types/pipeline";
import { emptyDefinition } from "../types/pipeline";

// Client-side draft state. Doesn't call the backend — the parent
// component owns the fetch + save cycle. Every mutator returns the
// new definition so React re-renders the canvas + inspector cleanly.
//
// Kept in a hook (not context) so tests can drive it directly.

export interface DraftState {
  definition: PipelineDefinition;
  selectedStageName: string | null;
  dirty: boolean;
}

export interface DraftActions {
  load: (definition: PipelineDefinition) => void;
  selectStage: (name: string | null) => void;
  updatePipeline: (patch: Partial<PipelineDefinition>) => void;
  updateStage: (name: string, patch: Partial<StageDefinition>) => void;
  insertStage: (index: number, stage: StageDefinition) => void;
  removeStage: (name: string) => void;
  moveStage: (name: string, delta: -1 | 1) => void;
  markPristine: () => void;
}

export function useDraftStore(initialId = "new-pipeline"): DraftState & DraftActions {
  const [definition, setDefinition] = useState<PipelineDefinition>(
    () => emptyDefinition(initialId)
  );
  const [selectedStageName, setSelectedStageName] = useState<string | null>(null);
  const [dirty, setDirty] = useState(false);

  const load = useCallback((next: PipelineDefinition) => {
    setDefinition(next);
    setSelectedStageName(null);
    setDirty(false);
  }, []);

  const selectStage = useCallback((name: string | null) => {
    setSelectedStageName(name);
  }, []);

  const updatePipeline = useCallback((patch: Partial<PipelineDefinition>) => {
    setDefinition((prev) => ({ ...prev, ...patch }));
    setDirty(true);
  }, []);

  const updateStage = useCallback((name: string, patch: Partial<StageDefinition>) => {
    setDefinition((prev) => ({
      ...prev,
      stages: prev.stages.map((s) => (s.name === name ? { ...s, ...patch } : s))
    }));
    // If the patch renames the stage, keep the Inspector's selection
    // pointing at the same logical stage (now under its new name).
    // Without this, Inspector.selectedStage resolves to null on the very
    // next render because stages[].find(s => s.name === selectedStageName)
    // no longer matches — the Inspector snaps back to the pipeline-level
    // panel mid-edit. Non-rename patches skip this branch entirely.
    if (patch.name && patch.name !== name) {
      setSelectedStageName((prev) => (prev === name ? patch.name! : prev));
    }
    setDirty(true);
  }, []);

  const insertStage = useCallback((index: number, stage: StageDefinition) => {
    setDefinition((prev) => {
      const stages = [...prev.stages];
      const at = Math.max(0, Math.min(index, stages.length));
      stages.splice(at, 0, stage);
      return { ...prev, stages };
    });
    setSelectedStageName(stage.name);
    setDirty(true);
  }, []);

  const removeStage = useCallback((name: string) => {
    setDefinition((prev) => ({
      ...prev,
      stages: prev.stages.filter((s) => s.name !== name)
    }));
    setSelectedStageName((cur) => (cur === name ? null : cur));
    setDirty(true);
  }, []);

  const moveStage = useCallback((name: string, delta: -1 | 1) => {
    setDefinition((prev) => {
      const idx = prev.stages.findIndex((s) => s.name === name);
      if (idx < 0) return prev;
      const target = idx + delta;
      if (target < 0 || target >= prev.stages.length) return prev;
      const stages = [...prev.stages];
      const [moved] = stages.splice(idx, 1);
      stages.splice(target, 0, moved);
      return { ...prev, stages };
    });
    setDirty(true);
  }, []);

  const markPristine = useCallback(() => setDirty(false), []);

  return useMemo(
    () => ({
      definition,
      selectedStageName,
      dirty,
      load,
      selectStage,
      updatePipeline,
      updateStage,
      insertStage,
      removeStage,
      moveStage,
      markPristine
    }),
    [definition, selectedStageName, dirty, load, selectStage,
      updatePipeline, updateStage, insertStage, removeStage, moveStage, markPristine]
  );
}
