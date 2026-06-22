package io.jaiclaw.htmlpdf.openhtml;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import io.jaiclaw.htmlpdf.HtmlToPdfException;
import io.jaiclaw.htmlpdf.HtmlToPdfRenderer;
import io.jaiclaw.htmlpdf.RenderOptions;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;

/**
 * Renders HTML to PDF using the OpenHTMLtoPDF + PDFBox engine.
 *
 * <p>OpenHTMLtoPDF requires well-formed XHTML. Input is normalized through
 * jsoup so that fragments (no {@code <html>}/{@code <head>}) and slightly
 * malformed markup still produce a valid PDF.
 */
public class OpenHtmlPdfRenderer implements HtmlToPdfRenderer {

    private static final Logger log = LoggerFactory.getLogger(OpenHtmlPdfRenderer.class);

    @Override
    public byte[] render(String html, RenderOptions options) {
        if (html == null || html.isBlank()) {
            throw new HtmlToPdfException("html input is empty");
        }
        String xhtml = toXhtml(html);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(xhtml, options.baseUri());
            builder.toStream(out);
            builder.run();
            byte[] pdf = out.toByteArray();
            log.debug("OpenHTMLtoPDF rendered {} bytes of HTML to {} bytes of PDF",
                    xhtml.length(), pdf.length);
            return pdf;
        } catch (Exception e) {
            throw new HtmlToPdfException("OpenHTMLtoPDF render failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String name() {
        return "openhtml";
    }

    private String toXhtml(String html) {
        Document doc = Jsoup.parse(html);
        doc.outputSettings()
                .syntax(Document.OutputSettings.Syntax.xml)
                .escapeMode(Entities.EscapeMode.xhtml)
                .prettyPrint(false);
        return doc.outerHtml();
    }
}
