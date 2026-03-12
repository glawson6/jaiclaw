package io.jclaw.documents;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses PDF documents using Apache PDFBox.
 * Extracts all text content and document metadata (title, author, page count).
 */
public class PdfDocumentParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(PdfDocumentParser.class);

    @Override
    public boolean supports(String mimeType) {
        return "application/pdf".equalsIgnoreCase(mimeType);
    }

    @Override
    public ParsedDocument parse(byte[] bytes, String mimeType) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            Map<String, String> metadata = new HashMap<>();
            metadata.put("pageCount", String.valueOf(document.getNumberOfPages()));

            PDDocumentInformation info = document.getDocumentInformation();
            if (info != null) {
                if (info.getTitle() != null) metadata.put("title", info.getTitle());
                if (info.getAuthor() != null) metadata.put("author", info.getAuthor());
                if (info.getSubject() != null) metadata.put("subject", info.getSubject());
            }

            return new ParsedDocument(text.strip(), Map.copyOf(metadata));
        } catch (IOException e) {
            throw new DocumentParseException("Failed to parse PDF document", e);
        }
    }
}
