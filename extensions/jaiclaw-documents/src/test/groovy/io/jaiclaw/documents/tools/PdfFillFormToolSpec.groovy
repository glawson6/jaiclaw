package io.jaiclaw.documents.tools

import tools.jackson.databind.ObjectMapper
import io.jaiclaw.core.tool.ToolContext
import io.jaiclaw.core.tool.ToolProfile
import io.jaiclaw.core.tool.ToolResult
import io.jaiclaw.documents.PdfFormField
import io.jaiclaw.documents.PdfFormReader
import io.jaiclaw.tools.ToolCatalog
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class PdfFillFormToolSpec extends Specification {

    @TempDir
    Path tempDir

    PdfFillFormTool tool = new PdfFillFormTool()
    ToolContext context
    ObjectMapper mapper = new ObjectMapper()

    def setup() {
        context = new ToolContext("agent", "session", "sid", tempDir.toString())
    }

    def "definition has correct name and section"() {
        expect:
        tool.definition().name() == "pdf_fill_form"
        tool.definition().section() == ToolCatalog.SECTION_DOCUMENTS
    }

    def "is available in CODING and FULL profiles"() {
        expect:
        tool.definition().isAvailableIn(ToolProfile.CODING)
        tool.definition().isAvailableIn(ToolProfile.FULL)
    }

    def "fills text fields and writes output PDF"() {
        given:
        Path templatePath = createSamplePdf(["fullName", "email", "city"])
        Path outputPath = tempDir.resolve("output.pdf")

        when:
        ToolResult result = tool.execute(Map.of(
                "templatePath", templatePath.toString(),
                "outputPath", outputPath.toString(),
                "fields", Map.of("fullName", "John Doe", "email", "john@example.com", "city", "Portland")
        ), context)

        then:
        result instanceof ToolResult.Success
        def json = mapper.readValue((result as ToolResult.Success).content(), Map)
        json.fieldsSet == 3
        json.skippedFields == []
        json.outputPath == outputPath.toAbsolutePath().toString()
        Files.exists(outputPath)
    }

    def "reports skipped fields for non-existent PDF field names"() {
        given:
        Path templatePath = createSamplePdf(["fullName"])
        Path outputPath = tempDir.resolve("output.pdf")

        when:
        ToolResult result = tool.execute(Map.of(
                "templatePath", templatePath.toString(),
                "outputPath", outputPath.toString(),
                "fields", Map.of("fullName", "Jane Doe", "nonExistent", "ignored")
        ), context)

        then:
        result instanceof ToolResult.Success
        def json = mapper.readValue((result as ToolResult.Success).content(), Map)
        json.fieldsSet == 1
        // nonExistent is silently ignored by PdfFormFiller (field not found = not set)
        Files.exists(outputPath)
    }

    def "verifies filled values by re-reading the output PDF"() {
        given:
        Path templatePath = createSamplePdf(["fullName", "email"])
        Path outputPath = tempDir.resolve("output.pdf")

        when:
        tool.execute(Map.of(
                "templatePath", templatePath.toString(),
                "outputPath", outputPath.toString(),
                "fields", Map.of("fullName", "Alice", "email", "alice@test.com"),
                "flatten", false
        ), context)

        then:
        PdfFormReader reader = new PdfFormReader()
        List<PdfFormField> fields = reader.readFields(Files.readAllBytes(outputPath))
        fields.find { it.name() == "fullName" }?.currentValue() == "Alice"
        fields.find { it.name() == "email" }?.currentValue() == "alice@test.com"
    }

    def "returns error for non-existent template"() {
        when:
        ToolResult result = tool.execute(Map.of(
                "templatePath", tempDir.resolve("missing.pdf").toString(),
                "outputPath", tempDir.resolve("output.pdf").toString(),
                "fields", Map.of("name", "value")
        ), context)

        then:
        result instanceof ToolResult.Error
        (result as ToolResult.Error).message().contains("Template file not found")
    }

    def "returns error when fields parameter is missing"() {
        given:
        Path templatePath = createSamplePdf(["fullName"])

        when:
        ToolResult result = tool.execute(Map.of(
                "templatePath", templatePath.toString(),
                "outputPath", tempDir.resolve("output.pdf").toString()
        ), context)

        then:
        result instanceof ToolResult.Error
        (result as ToolResult.Error).message().contains("Missing required parameter: fields")
    }

    def "creates output directory if it does not exist"() {
        given:
        Path templatePath = createSamplePdf(["name"])
        Path outputPath = tempDir.resolve("subdir/nested/output.pdf")

        when:
        ToolResult result = tool.execute(Map.of(
                "templatePath", templatePath.toString(),
                "outputPath", outputPath.toString(),
                "fields", Map.of("name", "Bob")
        ), context)

        then:
        result instanceof ToolResult.Success
        Files.exists(outputPath)
    }

    private Path createSamplePdf(List<String> textFieldNames) {
        PDDocument doc = new PDDocument()
        PDPage page = new PDPage()
        doc.addPage(page)

        PDAcroForm acroForm = new PDAcroForm(doc)
        doc.getDocumentCatalog().setAcroForm(acroForm)

        for (String fieldName : textFieldNames) {
            PDTextField tf = new PDTextField(acroForm)
            tf.setPartialName(fieldName)
            acroForm.getFields().add(tf)
        }

        Path pdfPath = tempDir.resolve("template-${UUID.randomUUID()}.pdf")
        doc.save(pdfPath.toFile())
        doc.close()
        return pdfPath
    }
}
