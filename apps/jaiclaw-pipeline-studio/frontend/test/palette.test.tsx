import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  Palette,
  __resetPaletteSequenceForTests
} from "../src/components/Palette";
import type { Catalog } from "../src/types/pipeline";

afterEach(() => __resetPaletteSequenceForTests());

const catalog: Catalog = {
  triggerTypes: ["MANUAL"],
  stageTypes: ["AGENT", "PROCESSOR", "CAMEL"],
  outputTypes: ["NONE"],
  errorStrategies: ["STOP"],
  processors: [
    {
      beanName: "regexExtract",
      name: "Regex Extract",
      category: "Transform",
      description: "Pull a regex group out of the input",
      icon: "regex",
      configSchema: '{"type":"object","properties":{"pattern":{"type":"string"}}}'
    }
  ],
  customBeans: ["addExclaim"],
  channels: [],
  cameltemplates: []
};

describe("Palette", () => {
  it("renders built-in generic entries even when the catalog is null", () => {
    render(<Palette catalog={null} onInsert={() => {}} />);
    expect(screen.getByText("Agent")).toBeInTheDocument();
    expect(screen.getByText("Camel URI")).toBeInTheDocument();
    expect(screen.getByText("Custom Bean")).toBeInTheDocument();
  });

  it("surfaces @PipelineProcessor beans + custom Function beans", () => {
    render(<Palette catalog={catalog} onInsert={() => {}} />);
    expect(screen.getByText("Regex Extract")).toBeInTheDocument();
    expect(screen.getByText("addExclaim")).toBeInTheDocument();
  });

  it("clicking a palette item calls onInsert with a stage from that template", () => {
    const onInsert = vi.fn();
    render(<Palette catalog={catalog} onInsert={onInsert} />);
    fireEvent.click(screen.getByText("Regex Extract"));
    expect(onInsert).toHaveBeenCalledTimes(1);
    const stage = onInsert.mock.calls[0][0];
    expect(stage.type).toBe("PROCESSOR");
    expect(stage.bean).toBe("regexExtract");
    expect(stage.config).toEqual({});
  });

  it("search filter narrows visible entries", async () => {
    const user = userEvent.setup();
    render(<Palette catalog={catalog} onInsert={() => {}} />);
    await user.type(screen.getByLabelText("Search palette"), "regex");
    expect(screen.getByText("Regex Extract")).toBeInTheDocument();
    // The built-in "Agent" node should not survive the filter.
    expect(screen.queryByText("Agent")).not.toBeInTheDocument();
  });
});
