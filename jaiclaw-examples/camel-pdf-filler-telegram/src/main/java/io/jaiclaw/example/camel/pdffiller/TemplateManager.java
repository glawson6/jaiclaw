package io.jaiclaw.example.camel.pdffiller;

import io.jaiclaw.documents.PdfFormField;
import io.jaiclaw.documents.PdfFormReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.List;

/**
 * Loads and caches the PDF template and its form field metadata.
 *
 * <p>The template path is configured via {@code APP_TEMPLATE} env var (or
 * {@code app.template} property). Supports Spring resource prefixes:
 * <ul>
 *   <li>{@code file:/path/to/form.pdf} — local filesystem (default)</li>
 *   <li>{@code classpath:templates/form.pdf} — classpath resource</li>
 * </ul>
 *
 * <p>If the template is a {@code file:} resource and does not exist,
 * {@link SampleFormGenerator} creates a sample PDF form with 8 fields
 * (fullName, email, phone, address, city, state, zipCode, agreedToTerms).
 */
@Configuration
public class TemplateManager {

    private static final Logger log = LoggerFactory.getLogger(TemplateManager.class);

    private final PdfFormReader pdfFormReader;
    private final SampleFormGenerator sampleFormGenerator;
    private final Resource templateResource;
    private byte[] templateBytes;
    private List<PdfFormField> fields;

    public TemplateManager(
            PdfFormReader pdfFormReader,
            SampleFormGenerator sampleFormGenerator,
            @Value("${app.template:file:target/data/templates/sample-form.pdf}") Resource templateResource) {
        this.pdfFormReader = pdfFormReader;
        this.sampleFormGenerator = sampleFormGenerator;
        this.templateResource = templateResource;
    }

    @PostConstruct
    void loadTemplate() throws IOException {
        // Ensure the sample template exists before trying to load it
        sampleFormGenerator.ensureTemplateExists();

        this.templateBytes = templateResource.getInputStream().readAllBytes();
        this.fields = pdfFormReader.readFields(templateBytes);
        log.info("Loaded PDF template ({} bytes, {} form fields): {}",
                templateBytes.length, fields.size(),
                fields.stream().map(PdfFormField::name).toList());
    }

    public byte[] getTemplateBytes() {
        return templateBytes;
    }

    public List<PdfFormField> getFields() {
        return fields;
    }

    /**
     * Returns a human-readable description of each form field, suitable for
     * including in the LLM prompt so the model knows what fields are available.
     *
     * <p>Fields are listed by their exact PDF AcroForm name. For forms with
     * duplicate-style field names (e.g. {@code City}, {@code City_2}, {@code City_3}),
     * the listing preserves the original order from the PDF which corresponds to
     * the visual top-to-bottom, left-to-right layout of the form.
     */
    public String getFieldDescriptions() {
        StringBuilder sb = new StringBuilder();
        for (PdfFormField field : fields) {
            sb.append("- \"").append(field.name()).append("\" (").append(field.type());
            if (!field.options().isEmpty()) {
                sb.append(", options: ").append(field.options());
            }
            if (field.currentValue() != null && !field.currentValue().isEmpty()) {
                sb.append(", current: \"").append(field.currentValue()).append("\"");
            }
            sb.append(")\n");
        }
        return sb.toString();
    }
}
