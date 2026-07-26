# Pipeline Studio Processors — SPI reference

Phase 1 of the Pipeline Studio buildout adds a first-class processor
SPI (`ConfigurableStageProcessor`) that carries its own metadata and
per-stage configuration. This is what makes the palette useful: every
`@PipelineProcessor`-annotated bean shows up as a draggable node with
its own inspector form (schema-driven), instead of the
name-only-and-nothing-else "custom bean" nodes the legacy
`Function<String,String>` shape produces.

Phase 4 will populate the `jaiclaw-pipeline-processors` module with
the baseline pack (~15 core processors + AI presets + Camel
templates). This doc is for anyone writing their own processors
today — either inside a downstream app or in a follow-up processor
module.

---

## The two shapes

`BeanStageProcessor` dispatches to whichever shape the Spring bean
implements:

### Legacy — `Function<String, String>`

```java
@Bean
public Function<String, String> upperCase() {
    return String::toUpperCase;
}
```

Still supported forever. Shows up in the catalog under `customBeans`,
palette renders as a name-only node. No config, no description, no
icon.

### First-class — `@PipelineProcessor` + `ConfigurableStageProcessor`

```java
@PipelineProcessor(
    name = "Regex Extract",
    category = "Transform",
    description = "Extract a group from the input via a regex",
    icon = "regex")
public class RegexExtractProcessor implements ConfigurableStageProcessor {

    @Override
    public void process(Exchange exchange,
                        StageDefinition stage,
                        PipelineContext context,
                        Map<String, String> config) {
        String pattern = config.getOrDefault("pattern", "");
        String group   = config.getOrDefault("group", "0");
        String input   = exchange.getIn().getBody(String.class);
        Matcher m = Pattern.compile(pattern).matcher(input);
        String result = m.find() ? m.group(Integer.parseInt(group)) : "";
        exchange.getIn().setBody(result);
    }

    @Override
    public String configSchema() {
        return """
            {
              "type": "object",
              "required": ["pattern"],
              "properties": {
                "pattern": { "type": "string", "description": "Java regex" },
                "group":   { "type": "string", "default": "0" }
              }
            }
            """;
    }
}
```

Register it as a Spring bean anywhere in the consuming app; the
autoconfig scans for `@PipelineProcessor` and surfaces it in
`GET /api/pipeline-studio/catalog`.

---

## Contract details

### `@PipelineProcessor` attributes

- **`name`** (required) — palette display name. Short and
  human-readable ("Regex Extract", not "regexExtractProcessor").
- **`category`** (default `"Custom"`) — palette grouping. Shipped
  conventions: `"Transform"`, `"Validate"`, `"Control"`, `"Data"`,
  `"Integration"`. Add new ones freely — the catalog surfaces
  whatever it finds.
- **`description`** (default empty) — one-sentence hover text + default
  inspector help copy.
- **`icon`** (default empty) — frontend-mapped icon id. Blank falls
  back to the default node icon.

### `ConfigurableStageProcessor.process(...)`

Signature intentionally matches `StageProcessor.process(...)` with
one extra arg — the stage's config map. The map is never `null` (an
empty map is passed when no config was set). Every hook / audit /
metric side effect fires at the same points as
`BeanStageProcessor` — the SPI is dispatched from inside
`BeanStageProcessor.process()` after the route builder's common
setup.

Implementations should be side-effect free with respect to shared
state — the runtime may invoke the same instance from multiple
stages concurrently.

### `ConfigurableStageProcessor.configSchema()`

Return a JSON Schema string (Draft-07 recommended, matches the
Studio's overall schema). The Studio SPA feeds this to
react-jsonschema-form to render the inspector panel. Default is
an empty-object schema — override when your processor accepts
configuration.

---

## Stage wiring

The stage definition carries the config in a new nullable
`config: Map<String, String>` field:

```yaml
stages:
  - name: extract-order-id
    type: PROCESSOR
    bean: regexExtractProcessor
    config:
      pattern: "order-(\\d+)"
      group: "1"
```

Or from Java:

```java
StageDefinition stage = new StageDefinition(
    "extract-order-id", StageType.PROCESSOR, "regexExtractProcessor",
    null, null, null, null, null, null,
    StageRuntime.NATIVE, null,
    Map.of("pattern", "order-(\\d+)", "group", "1"));
```

The 11-arg and 9-arg backward-compat constructors still work —
existing YAML fixtures and Spock specs compile unchanged.

---

## Validation

`PipelineValidator.validate(PipelineDefinition)` (the new per-draft
overload — Phase 1 B3) checks that PROCESSOR-stage beans implement
either `Function<String,String>` or `ConfigurableStageProcessor`.
Bare `Object` beans get flagged with `WRONG_BEAN_TYPE`.

---

## Testing

Test your processor as a plain Spring bean. `BeanStageProcessor` +
`PipelineRouteBuilder` are covered by their own specs
(`ConfigurableStageProcessorDispatchSpec` in `jaiclaw-pipeline`) so
you don't need to boot Camel — just instantiate the processor and
call `process(exchange, stage, context, config)` with a mocked
exchange.

```groovy
def "regex extract pulls the first group"() {
    given:
    RegexExtractProcessor p = new RegexExtractProcessor()
    Exchange exchange = Mock()
    Message message = Mock()
    exchange.getIn() >> message
    message.getBody(String.class) >> "order-12345 total $99"

    when:
    p.process(exchange, stubStage(), stubContext(),
              [pattern: "order-(\\d+)", group: "1"])

    then:
    1 * message.setBody("12345")
}
```

---

## Non-goals for the SPI

- **Stateful processors** — none of the shipped baseline processors
  will hold state. The runtime may share instances across stages.
- **Dynamic schema** — `configSchema()` is expected to return the
  same string for the lifetime of the bean. If a schema needs to
  vary at runtime, ship it in the config and validate downstream.
- **Non-JSON config** — the map is `Map<String,String>` deliberately.
  Complex config goes in as a JSON string with a schema field of
  `"type": "string", "contentMediaType": "application/json"`.

---

## Migration from `Function<String,String>`

No migration required. Existing bare-function beans keep working.
Adopt the new SPI only when you want the node to have a palette
form. Both shapes coexist — Studio-authored pipelines can reference
either.
