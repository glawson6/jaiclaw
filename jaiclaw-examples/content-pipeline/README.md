# Content Pipeline Example

Multi-modal content analysis — images, audio, and PDFs processed into structured metadata via plugin SPI.

## What This Demonstrates

- **Media** module for async media analysis (vision/audio LLM pipeline)
- **Documents** module for PDF/HTML document processing
- **JaiClawPlugin** SPI with multiple tool registrations (AnalyzeImageTool, ExtractMetadataTool)
- Plugin lifecycle (definition → register → activate)

## Architecture

Where this example fits in JaiClaw:

```
┌───────────────────────────────────────────────────────────┐
│                   CONTENT PIPELINE APP                      │
│                 (standalone Spring Boot)                    │
├──────────────────┬────────────────────────────────────────┤
│ Gateway          │  REST API (/api/chat, /api/health)      │
├──────────────────┼────────────────────────────────────────┤
│ Agent Runtime    │  AgentRuntime → LLM → Tools             │
├──────────────────┼────────────────────────────────────────┤
│ Media Analysis   │  [jaiclaw-media] → vision / audio LLM     │
├──────────────────┼────────────────────────────────────────┤
│ Document Parsing │  [jaiclaw-documents] → PDF / HTML         │
├──────────────────┼────────────────────────────────────────┤
│ Plugin SPI       │  [jaiclaw-plugin-sdk] → tool registration │
│                  │  AnalyzeImageTool, ExtractMetadataTool   │
├──────────────────┼────────────────────────────────────────┤
│ Core             │  jaiclaw-core (records, SPI)              │
└──────────────────┴────────────────────────────────────────┘

Data flow:
  User ──("analyze image")──► AgentRuntime
                                    │
                  ┌─────────────────┼─────────────────┐
                  ▼                 ▼                  ▼
           AnalyzeImageTool  ExtractMetadataTool     LLM
                  │                 │                  │
                  ▼                 ▼                  │
            Media module     Documents module          │
           (async analysis)  (PDF/HTML parse)          │
                  │                 │                  │
                  └─────────────────┼──────────────────┘
                                    ▼
                          Structured metadata
```

## Prerequisites

- Java 21+
- JaiClaw built and installed (`./mvnw install -DskipTests` from project root)
- Anthropic/OpenAI/Gemini API key OR Ollama running locally

## Build & Run

```bash
cd jaiclaw-examples/content-pipeline
export JAVA_HOME=$HOME/.sdkman/candidates/java/21.0.9-oracle
ANTHROPIC_API_KEY=sk-ant-... ../../mvnw spring-boot:run
```

### Using MiniMax

To run with MiniMax instead of Anthropic:

```bash
AI_PROVIDER=minimax MINIMAX_ENABLED=true MINIMAX_API_KEY=your-key ../../mvnw spring-boot:run
```


## Testing It

```bash
# Analyze an image
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $(cat ~/.jaiclaw/api-key)" \
  -d '{"content": "Analyze the image at https://example.com/product-photo.jpg and extract metadata"}'

# Process a document
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $(cat ~/.jaiclaw/api-key)" \
  -d '{"content": "Extract metadata from the PDF at https://example.com/quarterly-report.pdf"}'

# Health check
curl http://localhost:8080/api/health
```
