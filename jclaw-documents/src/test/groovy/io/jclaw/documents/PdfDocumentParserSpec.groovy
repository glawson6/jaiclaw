package io.jclaw.documents

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import spock.lang.Specification

class PdfDocumentParserSpec extends Specification {

    def parser = new PdfDocumentParser()

    def "supports application/pdf only"() {
        expect:
        parser.supports("application/pdf")
        !parser.supports("text/html")
        !parser.supports(null)
    }

    def "parse extracts text from a simple PDF"() {
        given:
        def pdfBytes = createSimplePdf("Hello from PDF. This is test content.")

        when:
        def result = parser.parse(pdfBytes, "application/pdf")

        then:
        result.text().contains("Hello from PDF")
        result.text().contains("This is test content")
        result.metadata().get("pageCount") == "1"
        result.hasContent()
    }

    def "parse throws DocumentParseException for invalid PDF"() {
        when:
        parser.parse("not a pdf".bytes, "application/pdf")

        then:
        thrown(DocumentParseException)
    }

    // Helper to create a real PDF in memory
    private byte[] createSimplePdf(String text) {
        def baos = new ByteArrayOutputStream()
        def doc = new PDDocument()
        try {
            def page = new PDPage()
            doc.addPage(page)
            def stream = new PDPageContentStream(doc, page)
            stream.beginText()
            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12)
            stream.newLineAtOffset(50, 700)
            stream.showText(text)
            stream.endText()
            stream.close()
            doc.save(baos)
        } finally {
            doc.close()
        }
        return baos.toByteArray()
    }
}
