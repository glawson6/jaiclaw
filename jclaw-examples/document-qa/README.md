# Document Q&A Example

PDF ingestion and semantic search Q&A with context compaction for long conversations.

## What This Demonstrates

- **Documents** module for PDF/HTML/text parsing
- **Memory** module for semantic search (in-memory or vector store)
- **Compaction** module for context window management
- Custom **ToolCallback** implementations (DocumentIngestTool, DocumentSearchTool)

## Architecture

Where this example fits in JClaw:

```
┌───────────────────────────────────────────────────────────┐
│                    DOCUMENT Q&A APP                        │
│                 (standalone Spring Boot)                    │
├──────────────────┬────────────────────────────────────────┤
│ Gateway          │  REST API (/api/chat, /api/health)      │
├──────────────────┼────────────────────────────────────────┤
│ Context Mgmt     │  [jclaw-compaction] → window management │
├──────────────────┼────────────────────────────────────────┤
│ Agent Runtime    │  AgentRuntime → LLM → Tools             │
├──────────────────┼────────────────────────────────────────┤
│ Memory           │  [jclaw-memory] → semantic search       │
├──────────────────┼────────────────────────────────────────┤
│ Document Parsing │  [jclaw-documents] → PDF/HTML/text      │
├──────────────────┼────────────────────────────────────────┤
│ Custom Tools     │  [DocumentIngestTool] [DocumentSearch]  │
├──────────────────┼────────────────────────────────────────┤
│ Core             │  jclaw-core (records, SPI)              │
└──────────────────┴────────────────────────────────────────┘

Data flow:
  Ingest:
    PDF/HTML ──► Documents module ──► chunks ──► Memory (index)

  Query:
    User ──("question")──► AgentRuntime
                               │
                      ┌────────┼────────┐
                      ▼        ▼        ▼
              DocumentSearch  Memory   LLM
              (semantic)      (recall)  │
                      │        │        │
                      └────────┼────────┘
                               ▼
                      Answer + citations
                               │
                      Compaction (if context grows large)
```

## Prerequisites

- Java 21+
- JClaw built and installed (`./mvnw install -DskipTests` from project root)
- Anthropic/OpenAI/Gemini API key OR Ollama running locally

## Build & Run

```bash
cd jclaw-examples/document-qa
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.9-oracle
ANTHROPIC_API_KEY=sk-ant-... ../../mvnw spring-boot:run
```

## Testing It

```bash
# Ingest a document
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $(cat ~/.jclaw/api-key)" \
  -d '{"content": "Ingest this document: Title=Company Handbook, Content=All employees are entitled to 20 days of PTO per year. Remote work is allowed 3 days per week."}'

# Ask a question
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $(cat ~/.jclaw/api-key)" \
  -d '{"content": "How many PTO days do employees get?"}'
```
