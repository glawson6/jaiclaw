package io.jaiclaw.invoice

import io.jaiclaw.core.tool.ToolCallback
import io.jaiclaw.core.tool.ToolContext
import io.jaiclaw.core.tool.ToolProfile
import io.jaiclaw.core.tool.ToolResult
import io.jaiclaw.tools.ToolRegistry
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class InvoiceToolsSpec extends Specification {

    @TempDir
    Path tempDir

    InvoiceConfig config
    ToolContext context

    def setup() {
        // Create a data dir with a sample YAML template
        def dataDir = tempDir.resolve("data")
        Files.createDirectories(dataDir)
        Files.writeString(dataDir.resolve("invoice_001.yaml"), SAMPLE_YAML)

        def outputDir = tempDir.resolve("output")
        Files.createDirectories(outputDir)

        config = new InvoiceConfig(
                dataDir.toString(),
                outputDir.toString(),
                "TapTech Holdings, Inc",
                100.00
        )
        context = new ToolContext("test-agent", "test:telegram:bot:123", "session-1", tempDir.toString())
    }

    def "all() returns 5 tools"() {
        when:
        def tools = InvoiceTools.all(config)

        then:
        tools.size() == 5
        tools.collect { it.definition().name() }.sort() == [
                "invoice_from_template",
                "invoice_generate",
                "invoice_list_templates",
                "invoice_timesheet_excel",
                "invoice_timesheet_pdf"
        ]
    }

    def "all tools are in FULL profile"() {
        when:
        def tools = InvoiceTools.all(config)

        then:
        tools.every { it.definition().isAvailableIn(ToolProfile.FULL) }
    }

    def "all tools are in Invoice section"() {
        when:
        def tools = InvoiceTools.all(config)

        then:
        tools.every { it.definition().section() == "Invoice" }
    }

    def "registerAll adds tools to registry"() {
        given:
        def registry = new ToolRegistry()

        when:
        InvoiceTools.registerAll(registry, config)

        then:
        registry.contains("invoice_generate")
        registry.contains("invoice_timesheet_excel")
        registry.contains("invoice_timesheet_pdf")
        registry.contains("invoice_list_templates")
        registry.contains("invoice_from_template")
    }

    def "invoice_generate produces PDF from structured fields"() {
        given:
        def tool = InvoiceTools.all(config).find { it.definition().name() == "invoice_generate" }

        when:
        def result = tool.execute([
                company_name  : "Test Corp",
                client_name   : "Client Inc",
                invoice_number: "TEST-001",
                invoice_date  : "March 1, 2026",
                default_rate  : 150.0,
                line_items    : [
                        [date: "March 1, 2026", description: "Consulting", hours: 8, rate: 150.0],
                        [date: "March 2, 2026", description: "Development", hours: 6, rate: 150.0]
                ]
        ], context)

        then:
        result instanceof ToolResult.Success
        def success = result as ToolResult.Success
        success.content().contains("Invoice TEST-001 generated")
        success.metadata().get("file_type") == "application/pdf"
        success.metadata().get("file_name").toString().endsWith(".pdf")
        Files.exists(Path.of(success.metadata().get("file_path").toString()))
    }

    def "invoice_generate produces PDF from yaml_content"() {
        given:
        def tool = InvoiceTools.all(config).find { it.definition().name() == "invoice_generate" }

        when:
        def result = tool.execute([yaml_content: SAMPLE_YAML], context)

        then:
        result instanceof ToolResult.Success
        def success = result as ToolResult.Success
        success.content().contains("Invoice 001 generated")
        success.metadata().containsKey("file_path")
    }

    def "invoice_timesheet_excel produces XLSX"() {
        given:
        def tool = InvoiceTools.all(config).find { it.definition().name() == "invoice_timesheet_excel" }

        when:
        def result = tool.execute([
                company_name  : "Test Corp",
                client_name   : "Employee",
                invoice_number: "TS-001",
                invoice_date  : "March 1, 2026",
                line_items    : [
                        [date: "March 1, 2026", description: "Development", hours: 8]
                ]
        ], context)

        then:
        result instanceof ToolResult.Success
        def success = result as ToolResult.Success
        success.metadata().get("file_type") == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }

    def "invoice_timesheet_pdf produces PDF"() {
        given:
        def tool = InvoiceTools.all(config).find { it.definition().name() == "invoice_timesheet_pdf" }

        when:
        def result = tool.execute([
                company_name    : "Test Corp",
                client_name     : "Employee",
                invoice_number  : "TS-001",
                invoice_date    : "March 1, 2026",
                invoice_period  : "March 2026",
                include_calendar: true,
                line_items      : [
                        [date: "March 1, 2026", description: "Development", hours: 8]
                ]
        ], context)

        then:
        result instanceof ToolResult.Success
        def success = result as ToolResult.Success
        success.metadata().get("file_type") == "application/pdf"
    }

    def "invoice_list_templates lists YAML files"() {
        given:
        def tool = InvoiceTools.all(config).find { it.definition().name() == "invoice_list_templates" }

        when:
        def result = tool.execute([:], context)

        then:
        result instanceof ToolResult.Success
        (result as ToolResult.Success).content().contains("invoice_001.yaml")
    }

    def "invoice_from_template loads template and generates"() {
        given:
        def tool = InvoiceTools.all(config).find { it.definition().name() == "invoice_from_template" }

        when:
        def result = tool.execute([
                template_name: "invoice_001.yaml",
                output_types : ["invoice_pdf"],
                overrides    : [client_name: "Override Client"]
        ], context)

        then:
        result instanceof ToolResult.Success
        def success = result as ToolResult.Success
        success.content().contains("Invoice")
        success.metadata().containsKey("file_path")
    }

    def "invoice_from_template errors on missing template"() {
        given:
        def tool = InvoiceTools.all(config).find { it.definition().name() == "invoice_from_template" }

        when:
        def result = tool.execute([template_name: "nonexistent.yaml"], context)

        then:
        result instanceof ToolResult.Error
        (result as ToolResult.Error).message().contains("Template not found")
    }

    static final String SAMPLE_YAML = """\
invoice:
  number: "001"
  date: "January 2, 2026"
  period: "December 22-31, 2025"

company:
  name: "TapTech Holdings, Inc"
  default_rate: 100.00

client:
  name: "WadiTek/Deloitte"

line_items:
  - date: "December 22, 2025"
    description: "Professional Services"
    hours: 8
    rate: 100.00
  - date: "December 23, 2025"
    description: "Professional Services"
    hours: 4
    rate: 100.00

payment_terms: "Due upon receipt"
footer_message: "Thank you for your business!"
"""
}
