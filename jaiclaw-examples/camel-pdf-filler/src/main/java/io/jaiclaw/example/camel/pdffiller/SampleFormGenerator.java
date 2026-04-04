package io.jaiclaw.example.camel.pdffiller;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates a sample PDF form template on demand if the configured template path
 * points to a file that does not yet exist.
 *
 * <p>Called by {@link TemplateManager} during initialization — not via ApplicationRunner,
 * so the template is guaranteed to exist before TemplateManager reads it.
 */
@Configuration
public class SampleFormGenerator {

    private static final Logger log = LoggerFactory.getLogger(SampleFormGenerator.class);

    @Value("${app.template:file:target/data/templates/sample-form.pdf}")
    private String templatePath;

    /**
     * Ensures the template file exists. If the configured path is a {@code file:} resource
     * and the file doesn't exist yet, generates a sample PDF form.
     */
    public void ensureTemplateExists() {
        if (!templatePath.startsWith("file:")) {
            return;
        }
        Path path = Path.of(templatePath.substring("file:".length()));
        if (Files.exists(path)) {
            return;
        }
        try {
            Files.createDirectories(path.getParent());
            generateSampleForm(path);
        } catch (IOException e) {
            log.warn("Failed to generate sample PDF template at {}: {}", path, e.getMessage());
        }
    }

    /**
     * Generates a sample PDF form as bytes (for testing or programmatic use).
     */
    public byte[] generateSampleFormBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument doc = createSampleDocument()) {
            doc.save(baos);
        }
        return baos.toByteArray();
    }

    private void generateSampleForm(Path outputPath) throws IOException {
        try (PDDocument doc = createSampleDocument();
             OutputStream out = Files.newOutputStream(outputPath)) {
            doc.save(out);
        }
        log.info("Generated sample PDF form template at {}", outputPath);
    }

    private PDDocument createSampleDocument() throws IOException {
        PDDocument doc = new PDDocument();
        PDPage page = new PDPage();
        doc.addPage(page);

        PDAcroForm acroForm = new PDAcroForm(doc);
        doc.getDocumentCatalog().setAcroForm(acroForm);

        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        acroForm.setDefaultResources(createDefaultResources(font));

        String[] textFields = {"fullName", "email", "phone", "address", "city", "state", "zipCode"};
        for (String fieldName : textFields) {
            PDTextField tf = new PDTextField(acroForm);
            tf.setPartialName(fieldName);
            acroForm.getFields().add(tf);
        }

        PDCheckBox cb = new PDCheckBox(acroForm);
        cb.setPartialName("agreedToTerms");
        acroForm.getFields().add(cb);

        try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
            stream.beginText();
            stream.setFont(font, 16);
            stream.newLineAtOffset(50, 750);
            stream.showText("Sample Registration Form");
            stream.endText();
        }

        return doc;
    }

    private org.apache.pdfbox.pdmodel.PDResources createDefaultResources(PDType1Font font) {
        org.apache.pdfbox.pdmodel.PDResources resources = new org.apache.pdfbox.pdmodel.PDResources();
        resources.put(org.apache.pdfbox.cos.COSName.getPDFName("Helv"), font);
        return resources;
    }
}
