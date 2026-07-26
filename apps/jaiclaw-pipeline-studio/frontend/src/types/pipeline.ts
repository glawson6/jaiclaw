// Shape of the JaiClaw pipeline definition + supporting records the
// Studio manipulates. Mirrors the Java record hierarchy in
// io.jaiclaw.pipeline, kept intentionally loose so a new nullable
// field on the backend doesn't break the frontend.

export type TriggerType = "MANUAL" | "HTTP" | "FILE" | "CRON" | "CAMEL_URI";
export type StageType = "AGENT" | "PROCESSOR" | "CAMEL";
export type OutputType = "CHANNEL" | "CAMEL_URI" | "LOG" | "NONE";
export type ErrorStrategy = "STOP" | "RETRY_THEN_FAIL" | "DEAD_LETTER";

export interface TriggerDefinition {
  type: TriggerType;
  uri?: string | null;
  expression?: string | null;
  path?: string | null;
}

export interface StageDefinition {
  name: string;
  type: StageType;
  bean?: string | null;
  agentId?: string | null;
  systemPrompt?: string | null;
  channelId?: string | null;
  uri?: string | null;
  timeout?: string | null;
  runtime?: "NATIVE" | "EMBABEL" | null;
  embabelWorkflow?: string | null;
  config?: Record<string, string> | null;
  transport?: unknown | null;
}

export interface OutputDefinition {
  type: OutputType;
  channelId?: string | null;
  uri?: string | null;
  template?: string | null;
}

export interface PipelineDefinition {
  id: string;
  name?: string | null;
  description?: string | null;
  tenantIds?: string[];
  enabled: boolean;
  errorStrategy?: ErrorStrategy | null;
  maxRetries: number;
  deadLetterUri?: string | null;
  resultTemplate?: string | null;
  trigger?: TriggerDefinition | null;
  stages: StageDefinition[];
  output?: OutputDefinition | null;
  security?: unknown | null;
}

export interface PipelineDraft {
  id: string;
  revision: number;
  definition: PipelineDefinition;
  tenantId?: string | null;
  status: "DRAFT" | "VALIDATED" | "DEPLOYED" | "DISABLED";
  origin: "STUDIO" | "YAML_IMPORT" | "CODE_BEAN";
  lastModifiedAt: string;
}

export interface CatalogProcessor {
  beanName: string;
  name: string;
  category: string;
  description: string;
  icon: string;
  configSchema?: string;
}

export interface Catalog {
  triggerTypes: TriggerType[];
  stageTypes: StageType[];
  outputTypes: OutputType[];
  errorStrategies: ErrorStrategy[];
  processors: CatalogProcessor[];
  customBeans: string[];
  channels: string[];
  cameltemplates: unknown[];
}

export interface ValidationError {
  pipelineId?: string;
  location?: string;
  code?: string;
  message?: string;
  suggestion?: string | null;
}

export interface ValidationReport {
  hasErrors: boolean;
  formatted: string;
  errors: ValidationError[];
}

export function emptyDefinition(id: string): PipelineDefinition {
  return {
    id,
    name: id,
    enabled: true,
    maxRetries: 3,
    trigger: { type: "MANUAL" },
    stages: [],
    output: { type: "NONE" }
  };
}
