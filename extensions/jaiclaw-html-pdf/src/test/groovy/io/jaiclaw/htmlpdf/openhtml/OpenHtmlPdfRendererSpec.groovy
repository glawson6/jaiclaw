package io.jaiclaw.htmlpdf.openhtml

import io.jaiclaw.htmlpdf.HtmlToPdfException
import io.jaiclaw.htmlpdf.RenderOptions
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import spock.lang.Specification

class OpenHtmlPdfRendererSpec extends Specification {

    def renderer = new OpenHtmlPdfRenderer()

    def "renders an inline-styled HTML fragment to a valid PDF"() {
        given:
        def html = '''
            <h1 style="color: #1a1a2e;">Threat Report</h1>
            <table style="width: 100%; border-collapse: collapse; font-size: 12px;">
              <tr style="background: #f8f9fa;">
                <th style="padding: 6px;">IP</th>
                <th style="padding: 6px;">Category</th>
              </tr>
              <tr>
                <td style="padding: 6px; font-family: monospace;">10.0.0.42</td>
                <td style="padding: 6px;">SCANNER</td>
              </tr>
            </table>
        '''

        when:
        def bytes = renderer.render(html, RenderOptions.defaults())

        then:
        bytes.length > 100
        new String(bytes, 0, 5) == '%PDF-'

        and: "PDFBox can extract the table content from the rendered PDF"
        def text = extractText(bytes)
        text.contains('Threat Report')
        text.contains('10.0.0.42')
        text.contains('SCANNER')
    }

    def "rejects blank input"() {
        when:
        renderer.render(input, RenderOptions.defaults())

        then:
        thrown(HtmlToPdfException)

        where:
        input << [null, '', '   ']
    }

    def "renderer reports its name"() {
        expect:
        renderer.name() == 'openhtml'
    }

    private String extractText(byte[] pdf) {
        def doc = PDDocument.load(pdf)
        try {
            new PDFTextStripper().getText(doc)
        } finally {
            doc.close()
        }
    }
}
