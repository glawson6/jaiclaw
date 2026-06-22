package io.jaiclaw.autoconfigure.htmlpdf;

import io.jaiclaw.htmlpdf.HtmlToPdfRenderer;
import io.jaiclaw.htmlpdf.itext.ItextPdfRenderer;
import io.jaiclaw.htmlpdf.openhtml.OpenHtmlPdfRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers a {@link HtmlToPdfRenderer} bean based on what's on the
 * classpath. Selection rules:
 *
 * <ul>
 *   <li>If {@code jaiclaw.html-pdf.renderer=openhtml} and
 *       OpenHTMLtoPDF is on the classpath → {@link OpenHtmlPdfRenderer}</li>
 *   <li>If {@code jaiclaw.html-pdf.renderer=itext} and iText html2pdf
 *       is on the classpath → {@link ItextPdfRenderer}</li>
 *   <li>If no property is set:
 *     <ol>
 *       <li>Prefer OpenHTMLtoPDF if available</li>
 *       <li>Else use iText html2pdf if available</li>
 *     </ol>
 *   </li>
 *   <li>{@link ConditionalOnMissingBean} means an application-supplied
 *       {@code HtmlToPdfRenderer} bean always wins.</li>
 * </ul>
 */
@AutoConfiguration
public class HtmlPdfAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(HtmlPdfAutoConfiguration.class);

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.openhtmltopdf.pdfboxout.PdfRendererBuilder")
    @ConditionalOnProperty(name = "jaiclaw.html-pdf.renderer",
            havingValue = "openhtml", matchIfMissing = true)
    static class OpenHtmlConfiguration {

        @Bean
        @ConditionalOnMissingBean(HtmlToPdfRenderer.class)
        public HtmlToPdfRenderer openHtmlPdfRenderer() {
            log.info("Registering HtmlToPdfRenderer: openhtml (OpenHTMLtoPDF + PDFBox)");
            return new OpenHtmlPdfRenderer();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.itextpdf.html2pdf.HtmlConverter")
    @ConditionalOnProperty(name = "jaiclaw.html-pdf.renderer", havingValue = "itext")
    static class ItextConfiguration {

        @Bean
        @ConditionalOnMissingBean(HtmlToPdfRenderer.class)
        public HtmlToPdfRenderer itextPdfRenderer() {
            log.info("Registering HtmlToPdfRenderer: itext (iText html2pdf)");
            return new ItextPdfRenderer();
        }
    }
}
