import type {
  Catalog,
  PipelineDefinition,
  PipelineDraft,
  ValidationReport
} from "../types/pipeline";

// Thin fetch wrappers around /api/pipeline-studio/*. Every endpoint
// is same-origin (SPA is served by the same gateway); no auth is
// added here — the app's Spring Security chain owns that.
//
// Every call throws on !ok so callers can rely on the parsed body.

const BASE = "/api/pipeline-studio";

async function jsonOrThrow<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const body = await res.text().catch(() => "");
    throw new Error(`${res.status} ${res.statusText}: ${body}`);
  }
  return res.json() as Promise<T>;
}

export const api = {
  async listDrafts(): Promise<PipelineDraft[]> {
    return jsonOrThrow(await fetch(`${BASE}/drafts`));
  },

  async getDraft(id: string): Promise<PipelineDraft> {
    return jsonOrThrow(await fetch(`${BASE}/drafts/${encodeURIComponent(id)}`));
  },

  async createDraft(definition: PipelineDefinition): Promise<PipelineDraft> {
    return jsonOrThrow(await fetch(`${BASE}/drafts`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(definition)
    }));
  },

  async updateDraft(
    id: string,
    definition: PipelineDefinition,
    revision: number
  ): Promise<PipelineDraft> {
    return jsonOrThrow(await fetch(`${BASE}/drafts/${encodeURIComponent(id)}`, {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
        "If-Match": `"${revision}"`
      },
      body: JSON.stringify(definition)
    }));
  },

  async deleteDraft(id: string): Promise<void> {
    const res = await fetch(`${BASE}/drafts/${encodeURIComponent(id)}`, {
      method: "DELETE"
    });
    if (!res.ok && res.status !== 204) {
      throw new Error(`Delete failed: ${res.status}`);
    }
  },

  async validate(definition: PipelineDefinition): Promise<ValidationReport> {
    return jsonOrThrow(await fetch(`${BASE}/validate`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(definition)
    }));
  },

  async validateDraft(id: string): Promise<ValidationReport> {
    return jsonOrThrow(await fetch(
      `${BASE}/drafts/${encodeURIComponent(id)}/validate`,
      { method: "POST" }
    ));
  },

  async catalog(): Promise<Catalog> {
    return jsonOrThrow(await fetch(`${BASE}/catalog`));
  },

  async schema(): Promise<unknown> {
    return jsonOrThrow(await fetch(`${BASE}/schema`));
  },

  async exportYaml(id: string): Promise<string> {
    const res = await fetch(`${BASE}/drafts/${encodeURIComponent(id)}/yaml`);
    if (!res.ok) {
      throw new Error(`YAML export failed: ${res.status}`);
    }
    return res.text();
  },

  async importYaml(yaml: string, id?: string): Promise<PipelineDraft> {
    const url = id
      ? `${BASE}/import?id=${encodeURIComponent(id)}`
      : `${BASE}/import`;
    return jsonOrThrow(await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/x-yaml" },
      body: yaml
    }));
  },

  async variables(
    id: string,
    stage: string | undefined
  ): Promise<{ stage: string | null; variables: Record<string, string> }> {
    const url = stage
      ? `${BASE}/drafts/${encodeURIComponent(id)}/variables?stage=${encodeURIComponent(stage)}`
      : `${BASE}/drafts/${encodeURIComponent(id)}/variables`;
    return jsonOrThrow(await fetch(url));
  },

  // ── Phase 3: deploy + test-run ─────────────────

  async deploy(id: string): Promise<DeployResponse> {
    return jsonOrThrow(await fetch(
      `${BASE}/drafts/${encodeURIComponent(id)}/deploy`,
      { method: "POST" }
    ));
  },

  async undeploy(id: string): Promise<DeployResponse> {
    return jsonOrThrow(await fetch(
      `${BASE}/drafts/${encodeURIComponent(id)}/undeploy`,
      { method: "POST" }
    ));
  },

  async redeploy(id: string, definition?: PipelineDefinition): Promise<DeployResponse> {
    return jsonOrThrow(await fetch(
      `${BASE}/drafts/${encodeURIComponent(id)}/redeploy`,
      {
        method: "POST",
        headers: definition ? { "Content-Type": "application/json" } : {},
        body: definition ? JSON.stringify(definition) : undefined
      }
    ));
  },

  async testRun(id: string, input: string, timeoutSeconds?: number): Promise<TestRunResponse> {
    return jsonOrThrow(await fetch(
      `${BASE}/drafts/${encodeURIComponent(id)}/test-run`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ input, timeoutSeconds })
      }
    ));
  }
};

export interface DeployResponse {
  pipelineId: string;
  status: string;
  stageCount?: number;
}

export interface TestRunResponse {
  executionId: string;
  status: string;
  totalDurationMs: number;
  stageOutputs: Record<string, string>;
  failureReason: string;
  startedAt: string;
}
