import { describe, expect, it } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { useDraftStore } from "../src/state/draftStore";
import type { StageDefinition } from "../src/types/pipeline";

const stage = (name: string): StageDefinition => ({
  name,
  type: "PROCESSOR",
  bean: "someBean"
});

describe("useDraftStore", () => {
  it("starts clean with an empty pipeline", () => {
    const { result } = renderHook(() => useDraftStore("test-pipe"));
    expect(result.current.definition.id).toBe("test-pipe");
    expect(result.current.definition.stages).toHaveLength(0);
    expect(result.current.dirty).toBe(false);
  });

  it("insertStage appends by default and marks dirty", () => {
    const { result } = renderHook(() => useDraftStore());
    act(() => result.current.insertStage(0, stage("a")));
    act(() => result.current.insertStage(1, stage("b")));
    expect(result.current.definition.stages.map((s) => s.name)).toEqual(["a", "b"]);
    expect(result.current.dirty).toBe(true);
    expect(result.current.selectedStageName).toBe("b");
  });

  it("insertStage at an interior index splices correctly", () => {
    const { result } = renderHook(() => useDraftStore());
    act(() => result.current.insertStage(0, stage("first")));
    act(() => result.current.insertStage(1, stage("third")));
    act(() => result.current.insertStage(1, stage("second")));
    expect(result.current.definition.stages.map((s) => s.name))
      .toEqual(["first", "second", "third"]);
  });

  it("moveStage swaps with neighbour and stays in bounds", () => {
    const { result } = renderHook(() => useDraftStore());
    act(() => result.current.insertStage(0, stage("a")));
    act(() => result.current.insertStage(1, stage("b")));
    act(() => result.current.moveStage("a", 1));
    expect(result.current.definition.stages.map((s) => s.name)).toEqual(["b", "a"]);
    act(() => result.current.moveStage("a", 1)); // already last — no-op
    expect(result.current.definition.stages.map((s) => s.name)).toEqual(["b", "a"]);
    act(() => result.current.moveStage("b", -1)); // already first — no-op
    expect(result.current.definition.stages.map((s) => s.name)).toEqual(["b", "a"]);
  });

  it("removeStage clears the selection when the selected stage is removed", () => {
    const { result } = renderHook(() => useDraftStore());
    act(() => result.current.insertStage(0, stage("a")));
    expect(result.current.selectedStageName).toBe("a");
    act(() => result.current.removeStage("a"));
    expect(result.current.selectedStageName).toBeNull();
    expect(result.current.definition.stages).toHaveLength(0);
  });

  it("updateStage patches by name", () => {
    const { result } = renderHook(() => useDraftStore());
    act(() => result.current.insertStage(0, stage("a")));
    act(() => result.current.updateStage("a", { bean: "otherBean" }));
    expect(result.current.definition.stages[0].bean).toBe("otherBean");
  });

  it("updateStage rename follows selectedStageName so the Inspector stays put", () => {
    // Repro: without the reconciliation, selectedStageName would still be "a"
    // after the rename and Inspector.selectedStage would resolve to null —
    // the pipeline-level panel would take over. See
    // docs/issues/studio-updatestage-renames-drop-selection.md.
    const { result } = renderHook(() => useDraftStore());
    act(() => result.current.insertStage(0, stage("agent-1")));   // auto-selects
    expect(result.current.selectedStageName).toBe("agent-1");
    act(() => result.current.updateStage("agent-1", { name: "my-agent" }));
    expect(result.current.definition.stages[0].name).toBe("my-agent");
    expect(result.current.selectedStageName).toBe("my-agent");
  });

  it("updateStage non-rename patches leave selectedStageName untouched", () => {
    // Regression guard on the `if (patch.name && patch.name !== name)` gate.
    const { result } = renderHook(() => useDraftStore());
    act(() => result.current.insertStage(0, stage("a")));
    act(() => result.current.updateStage("a", { bean: "newBean" }));
    expect(result.current.selectedStageName).toBe("a");
    // A no-op rename (patch.name === current name) also stays put.
    act(() => result.current.updateStage("a", { name: "a" }));
    expect(result.current.selectedStageName).toBe("a");
  });

  it("updateStage renaming a *different* stage doesn't move the selection", () => {
    // Selection is on "b"; renaming "a" should leave the selection on "b".
    const { result } = renderHook(() => useDraftStore());
    act(() => result.current.insertStage(0, stage("a")));
    act(() => result.current.insertStage(1, stage("b")));    // auto-selects "b"
    expect(result.current.selectedStageName).toBe("b");
    act(() => result.current.updateStage("a", { name: "az" }));
    expect(result.current.definition.stages.map((s) => s.name)).toEqual(["az", "b"]);
    expect(result.current.selectedStageName).toBe("b");
  });

  it("markPristine flips dirty back to false", () => {
    const { result } = renderHook(() => useDraftStore());
    act(() => result.current.insertStage(0, stage("a")));
    expect(result.current.dirty).toBe(true);
    act(() => result.current.markPristine());
    expect(result.current.dirty).toBe(false);
  });

  it("load replaces the whole definition and resets selection + dirty", () => {
    const { result } = renderHook(() => useDraftStore());
    act(() => result.current.insertStage(0, stage("a")));
    act(() => result.current.load({
      id: "new",
      enabled: true,
      maxRetries: 3,
      stages: [stage("x")]
    }));
    expect(result.current.definition.id).toBe("new");
    expect(result.current.definition.stages.map((s) => s.name)).toEqual(["x"]);
    expect(result.current.dirty).toBe(false);
    expect(result.current.selectedStageName).toBeNull();
  });
});
