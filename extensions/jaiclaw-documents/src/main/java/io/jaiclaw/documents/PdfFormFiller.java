package io.jaiclaw.documents;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PdfFormFiller {

    private static final Logger log = LoggerFactory.getLogger(PdfFormFiller.class);

    private final boolean flatten;

    public PdfFormFiller() {
        this(true);
    }

    public PdfFormFiller(boolean flatten) {
        this.flatten = flatten;
    }

    public PdfFormResult fill(byte[] templateBytes, Map<String, String> fieldValues) {
        try (PDDocument doc = Loader.loadPDF(templateBytes)) {
            PDAcroForm acroForm = doc.getDocumentCatalog().getAcroForm();
            if (acroForm == null) {
                return new PdfFormResult.Failure("PDF has no AcroForm (no fillable fields)");
            }

            int fieldsSet = 0;
            List<String> skipped = new ArrayList<>();
            for (Map.Entry<String, String> entry : fieldValues.entrySet()) {
                PDField field = acroForm.getField(entry.getKey());
                if (field != null) {
                    try {
                        field.setValue(entry.getValue());
                        fieldsSet++;
                    } catch (IllegalArgumentException e) {
                        // Invalid value for field (e.g., wrong checkbox/radio option)
                        log.warn("Skipping field '{}': {}", entry.getKey(), e.getMessage());
                        skipped.add(entry.getKey() + ": " + e.getMessage());
                    }
                }
            }

            if (flatten) {
                acroForm.flatten();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return new PdfFormResult.Success(out.toByteArray(), fieldsSet, skipped);
        } catch (IOException e) {
            return new PdfFormResult.Failure("Failed to fill PDF: " + e.getMessage());
        }
    }
}
