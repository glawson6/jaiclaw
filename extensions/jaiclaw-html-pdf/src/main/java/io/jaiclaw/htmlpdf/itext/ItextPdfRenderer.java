package io.jaiclaw.htmlpdf.itext;

import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import io.jaiclaw.htmlpdf.HtmlToPdfException;
import io.jaiclaw.htmlpdf.HtmlToPdfRenderer;
import io.jaiclaw.htmlpdf.RenderOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Renders HTML to PDF using iText's html2pdf module. Tolerates HTML
 * fragments out of the box (no manual XHTML normalization required) and
 * generally produces tighter table layouts than OpenHTMLtoPDF.
 */
public class ItextPdfRenderer implements HtmlToPdfRenderer {

    private static final Logger log = LoggerFactory.getLogger(ItextPdfRenderer.class);

    @Override
    public byte[] render(String html, RenderOptions options) {
        if (html == null || html.isBlank()) {
            throw new HtmlToPdfException("html input is empty");
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ByteArrayInputStream in = new ByteArrayInputStream(
                     html.getBytes(StandardCharsets.UTF_8))) {
            ConverterProperties props = new ConverterProperties();
            if (options.baseUri() != null && !options.baseUri().isBlank()) {
                props.setBaseUri(options.baseUri());
            }
            HtmlConverter.convertToPdf(in, out, props);
            byte[] pdf = out.toByteArray();
            log.debug("iText html2pdf rendered {} bytes of HTML to {} bytes of PDF",
                    html.length(), pdf.length);
            return pdf;
        } catch (Exception e) {
            throw new HtmlToPdfException("iText html2pdf render failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String name() {
        return "itext";
    }
}
