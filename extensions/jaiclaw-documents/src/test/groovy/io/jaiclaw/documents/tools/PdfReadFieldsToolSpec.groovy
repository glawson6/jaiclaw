package io.jaiclaw.documents.tools

import tools.jackson.databind.ObjectMapper
import io.jaiclaw.core.tool.ToolContext
import io.jaiclaw.core.tool.ToolProfile
import io.jaiclaw.core.tool.ToolResult
import io.jaiclaw.tools.ToolCatalog
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class PdfReadFieldsToolSpec extends Specification {

    @TempDir
    Path tempDir

    PdfReadFieldsTool tool = new PdfReadFieldsTool()
    ToolContext context
    ObjectMapper mapper = new ObjectMapper()

    def setup() {
        context = new ToolContext("agent", "session", "sid", tempDir.toString())
    }

    def "definition has correct name and section"() {
        expect:
        tool.definition().name() == "pdf_read_fields"
        tool.definition().section() == ToolCatalog.SECTION_DOCUMENTS
    }

    def "is available in CODING and FULL profiles"() {
        expect:
        tool.definition().isAvailableIn(ToolProfile.CODING)
        tool.definition().isAvailableIn(ToolProfile.FULL)
    }

    def "is not available in MINIMAL profile"() {
        expect:
        !tool.definition().isAvailableIn(ToolProfile.MINIMAL)
    }

    def "reads text and checkbox fields from a PDF form"() {
        given:
        Path pdfPath = createSamplePdf(["fullName", "email"], true)

        when:
        ToolResult result = tool.execute(Map.of("path", pdfPath.toString()), context)

        then:
        result instanceof ToolResult.Success
        def json = mapper.readValue((result as ToolResult.Success).content(), List)
        json.size() == 3 // 2 text fields + 1 checkbox
        json[0].name == "fullName"
        json[0].type == "TEXT"
        json[1].name == "email"
        json[1].type == "TEXT"
        json[2].name == "agreedToTerms"
        json[2].type == "CHECKBOX"
    }

    def "returns error for non-existent file"() {
        when:
        ToolResult result = tool.execute(
                Map.of("path", tempDir.resolve("nonexistent.pdf").toString()), context)

        then:
        result instanceof ToolResult.Error
        (result as ToolResult.Error).message().contains("File not found")
    }

    def "returns message for PDF without form fields"() {
        given:
        Path pdfPath = createEmptyPdf()

        when:
        ToolResult result = tool.execute(Map.of("path", pdfPath.toString()), context)

        then:
        result instanceof ToolResult.Success
        (result as ToolResult.Success).content().contains("No fillable form fields")
    }

    def "returns error when path parameter is missing"() {
        when:
        ToolResult result = tool.execute(Map.of(), context)

        then:
        result instanceof ToolResult.Error
        (result as ToolResult.Error).message().contains("Missing required parameter")
    }

    private Path createSamplePdf(List<String> textFieldNames, boolean addCheckbox) {
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

        if (addCheckbox) {
            PDCheckBox cb = new PDCheckBox(acroForm)
            cb.setPartialName("agreedToTerms")
            acroForm.getFields().add(cb)
        }

        Path pdfPath = tempDir.resolve("test-form.pdf")
        doc.save(pdfPath.toFile())
        doc.close()
        return pdfPath
    }

    private Path createEmptyPdf() {
        PDDocument doc = new PDDocument()
        doc.addPage(new PDPage())
        Path pdfPath = tempDir.resolve("empty.pdf")
        doc.save(pdfPath.toFile())
        doc.close()
        return pdfPath
    }
}
