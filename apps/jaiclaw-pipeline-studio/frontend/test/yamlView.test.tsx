import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { YamlView } from "../src/components/YamlView";
import { emptyDefinition } from "../src/types/pipeline";

describe("YamlView", () => {
  it("renders the current definition as YAML in the textarea", () => {
    render(
      <YamlView
        definition={emptyDefinition("hello")}
        onReplace={() => {}}
        onClose={() => {}}
      />
    );
    const editor = screen.getByLabelText("YAML editor") as HTMLTextAreaElement;
    expect(editor.value).toContain("id: hello");
    expect(editor.value).toContain("type: MANUAL");
  });

  it("apply parses the edited YAML and calls onReplace", () => {
    const onReplace = vi.fn();
    render(
      <YamlView
        definition={emptyDefinition("hello")}
        onReplace={onReplace}
        onClose={() => {}}
      />
    );
    const editor = screen.getByLabelText("YAML editor") as HTMLTextAreaElement;
    // fireEvent.change is a single synchronous value swap — avoids
    // userEvent.type's per-keystroke expansion, which mishandles the
    // colon + newline sequences in YAML on jsdom.
    fireEvent.change(editor, {
      target: { value: "id: renamed\nenabled: true\nmaxRetries: 2\nstages: []\n" }
    });
    fireEvent.click(screen.getByText("Apply"));
    expect(onReplace).toHaveBeenCalledTimes(1);
    const parsed = onReplace.mock.calls[0][0];
    expect(parsed.id).toBe("renamed");
    expect(parsed.maxRetries).toBe(2);
  });

  it("shows a parse error when YAML is malformed", () => {
    const onReplace = vi.fn();
    render(
      <YamlView
        definition={emptyDefinition("hello")}
        onReplace={onReplace}
        onClose={() => {}}
      />
    );
    const editor = screen.getByLabelText("YAML editor") as HTMLTextAreaElement;
    fireEvent.change(editor, { target: { value: "not-an-object" } });
    fireEvent.click(screen.getByText("Apply"));
    expect(onReplace).not.toHaveBeenCalled();
    expect(screen.getByText(/YAML must parse to an object/i)).toBeInTheDocument();
  });

  it("Back to canvas triggers onClose", () => {
    const onClose = vi.fn();
    render(
      <YamlView
        definition={emptyDefinition("x")}
        onReplace={() => {}}
        onClose={onClose}
      />
    );
    fireEvent.click(screen.getByText("Back to canvas"));
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
