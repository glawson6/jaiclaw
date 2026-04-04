# Camel PDF Filler

## Problem

You have structured JSON data (from APIs, forms, databases) and a PDF form template that needs to be filled. The JSON field names don't match the PDF form field names exactly, so you need an intelligent mapping step. Manual field mapping is tedious and breaks when either the JSON schema or the PDF template changes.

## Solution

This example uses JaiClaw's Camel SEDA routing to build an automated PDF form-filling pipeline. An LLM maps JSON keys to PDF form field names, then the PDF is filled using PDFBox. Input arrives via file drop (Camel file watcher) or REST API. The filled PDF is stored in an ArtifactStore and written to an outbox directory. A REST endpoint allows polling for the result by job ID.

## Architecture

```
JSON file (data/inbox/)     REST API (POST /api/fill -> returns jobId)
        |                            |
        +---- JsonIngestRoute -------+
                    |
         seda:jaiclaw-pdf-filler-in
                    |
            CamelChannelAdapter -> AgentRuntime
                    |
         LLM receives: PDF field descriptions + JSON data
         LLM returns: { "fieldMappings": {...}, "unmapped": [...], "warnings": [...] }
                    |
         seda:jaiclaw-pdf-filler-out
                    |
              PdfOutputRoute
                    |
         1. Parse LLM response as JSON field mapping
         2. PdfFormFiller.fill(template, mapping)
         3. ArtifactStore.save(jobId, pdfBytes, warnings metadata)
         4. Write to data/outbox/{jobId}.pdf
                    |
         GET /api/fill/{jobId}
                    |
         COMPLETED? -> Return PDF bytes (application/pdf)
         PENDING?   -> Return JSON status
         FAILED?    -> Return JSON with error message
```

### Key Classes

| Class | Role |
|-------|------|
| `CamelPdfFillerApp` | Spring Boot entry point, wires ArtifactStore + PDF form beans |
| `JsonIngestRoute` | Camel RouteBuilder - watches inbox for `.json` files, enriches with PDF field descriptions |
| `PdfOutputRoute` | Camel RouteBuilder - parses LLM mapping, fills PDF, stores in ArtifactStore + outbox |
| `PdfFillerRestController` | REST API: `POST /api/fill` (submit), `GET /api/fill/{id}` (retrieve) |
| `TemplateManager` | Loads and caches PDF template + field metadata |
| `SampleFormGenerator` | Creates a sample PDF form template at startup if absent |

## Design

**LLM as field mapper:** Instead of hardcoding field-name mappings, the LLM understands the semantics of both the JSON data and the PDF form fields, producing correct mappings even when names differ (e.g., `name` -> `fullName`, `zip` -> `zipCode`). This makes the pipeline resilient to schema changes.

**ArtifactStore SPI:** Filled PDFs are stored in an `ArtifactStore` (in-memory for this example). The same SPI can be backed by S3, a database, or Redis in production. The REST GET endpoint reads from the store.

**Dual input modes:** File drop (Camel file watcher) for batch processing, REST API for on-demand use. Both converge on the same SEDA queue.

**Graceful degradation:** If the LLM can't map some JSON fields, they're returned as `unmapped` with `warnings`. The PDF is still filled with whatever was mapped, and the warnings are stored as metadata on the artifact.

## Build & Run

### Prerequisites

- Java 21+
- An Anthropic API key (or any compatible provider)

### Environment Variables

```bash
# Required
export ANTHROPIC_API_KEY=sk-your-key-here

# Optional — PDF template (the unfilled form to use as a template)
# Supports file: and classpath: prefixes
# If the file doesn't exist, a sample 8-field form is generated automatically
export APP_TEMPLATE=file:/path/to/your-form.pdf   # default: file:target/data/templates/sample-form.pdf

# Optional — other overrides
export AI_PROVIDER=anthropic              # default
export ANTHROPIC_MODEL=claude-haiku-4-5   # default
export APP_INBOX=/path/to/custom/inbox    # default: target/data/inbox
export APP_OUTBOX=/path/to/custom/outbox  # default: target/data/outbox
```

### Build

```bash
export JAVA_HOME=/Users/tap/.sdkman/candidates/java/21.0.9-oracle

# From the jaiclaw root:
./mvnw package -pl :jaiclaw-example-camel-pdf-filler -am -DskipTests
```

### Run

```bash
# Create directories
mkdir -p target/data/inbox

# Start the application
java -jar jaiclaw-examples/camel-pdf-filler/target/jaiclaw-example-camel-pdf-filler-0.1.0-SNAPSHOT.jar
```

### Verify

**Inspect the loaded template:**

```bash
# See what form fields are available in the loaded PDF template
curl -s http://localhost:8080/api/template | jq .
# Returns: {"fieldCount":8,"fields":[{"name":"fullName","type":"TEXT"},{"name":"email","type":"TEXT"},...]}
```

**Via REST API:**

```bash
# Submit JSON for form filling
curl -s -X POST http://localhost:8080/api/fill \
     -H "Content-Type: application/json" \
     -d '{"fullName":"John Doe","email":"john@example.com","phone":"555-1234"}'
# Returns: {"jobId":"abc-123","status":"PENDING"}

# Poll for result (wait a few seconds for LLM processing)
curl -s http://localhost:8080/api/fill/{jobId}
# Returns PDF bytes when COMPLETED, or status JSON when still processing

# Download filled PDF
curl -s http://localhost:8080/api/fill/{jobId} -o filled.pdf
```

**Via file drop:**

```bash
cp jaiclaw-examples/camel-pdf-filler/src/test/resources/test-data.json target/data/inbox/
# Watch logs - filled PDF appears in target/data/outbox/
```
