package io.jaiclaw.htmlpdf;

/**
 * Renders an HTML string to a PDF byte array. Implementations wrap different
 * underlying engines (OpenHTMLtoPDF, iText html2pdf, ...) — pick one by
 * placing its dependency on the classpath, or by setting
 * {@code jaiclaw.html-pdf.renderer=openhtml|itext} when more than one is
 * available.
 */
public interface HtmlToPdfRenderer {

    byte[] render(String html, RenderOptions options);

    default byte[] render(String html) {
        return render(html, RenderOptions.defaults());
    }

    String name();
}
