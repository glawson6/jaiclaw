import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ValidationPanel } from "../src/components/ValidationPanel";

describe("ValidationPanel", () => {
  it("renders an ok state when hasErrors is false", () => {
    render(
      <ValidationPanel
        report={{ hasErrors: false, formatted: "ok", errors: [] }}
      />
    );
    expect(screen.getByText(/no errors/i)).toBeInTheDocument();
  });

  it("renders each error with its code + message + suggestion", () => {
    render(
      <ValidationPanel
        report={{
          hasErrors: true,
          formatted: "1 error",
          errors: [{
            pipelineId: "p1",
            location: "stage 'find-account'",
            code: "UNKNOWN_BEAN",
            message: "PROCESSOR bean 'findAccount' not found",
            suggestion: "findAccountService"
          }]
        }}
      />
    );
    expect(screen.getByText("UNKNOWN_BEAN")).toBeInTheDocument();
    expect(screen.getByText(/not found/i)).toBeInTheDocument();
    expect(screen.getByText(/findAccountService/)).toBeInTheDocument();
  });

  it("clicking the stage chip calls onFocusStage with that name", () => {
    const focus = vi.fn();
    render(
      <ValidationPanel
        onFocusStage={focus}
        report={{
          hasErrors: true,
          formatted: "",
          errors: [{
            pipelineId: "p1",
            location: "stage 'my-stage'",
            code: "X",
            message: "y"
          }]
        }}
      />
    );
    fireEvent.click(screen.getByText("my-stage"));
    expect(focus).toHaveBeenCalledWith("my-stage");
  });

  it("returns null when report is null", () => {
    const { container } = render(<ValidationPanel report={null} />);
    expect(container.firstChild).toBeNull();
  });
});
