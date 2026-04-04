package io.jaiclaw.documents;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDChoice;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDNonTerminalField;
import org.apache.pdfbox.pdmodel.interactive.form.PDRadioButton;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PdfFormReader {

    public List<PdfFormField> readFields(byte[] pdfBytes) {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDAcroForm acroForm = doc.getDocumentCatalog().getAcroForm();
            if (acroForm == null) return List.of();

            List<PdfFormField> fields = new ArrayList<>();
            for (PDField field : acroForm.getFields()) {
                collectFields(field, fields);
            }
            return List.copyOf(fields);
        } catch (IOException e) {
            throw new DocumentParseException("Failed to read PDF form fields", e);
        }
    }

    private void collectFields(PDField field, List<PdfFormField> result) {
        if (field instanceof PDNonTerminalField ntf) {
            for (PDField child : ntf.getChildren()) {
                collectFields(child, result);
            }
        } else {
            PdfFormField.FieldType type = mapFieldType(field);
            List<String> options;
            if (field instanceof PDChoice choice) {
                options = choice.getOptions();
            } else if (field instanceof PDCheckBox checkbox) {
                String onValue = checkbox.getOnValue();
                options = List.of(onValue != null ? onValue : "Yes", "Off");
            } else if (field instanceof PDRadioButton radio) {
                options = radio.getExportValues();
                // Fallback: if export values empty, extract on-values from widgets
                if (options.isEmpty()) {
                    List<String> onValues = new ArrayList<>();
                    for (org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget widget : radio.getWidgets()) {
                        if (widget.getAppearance() != null
                                && widget.getAppearance().getNormalAppearance() != null) {
                            for (org.apache.pdfbox.cos.COSName key : widget.getAppearance()
                                    .getNormalAppearance().getSubDictionary().keySet()) {
                                String name = key.getName();
                                if (!"Off".equals(name) && !onValues.contains(name)) {
                                    onValues.add(name);
                                }
                            }
                        }
                    }
                    if (!onValues.isEmpty()) {
                        onValues.add("Off");
                        options = onValues;
                    }
                }
            } else {
                options = List.of();
            }
            result.add(new PdfFormField(
                    field.getFullyQualifiedName(), type,
                    field.getValueAsString(), options));
        }
    }

    private PdfFormField.FieldType mapFieldType(PDField field) {
        if (field instanceof PDTextField) return PdfFormField.FieldType.TEXT;
        if (field instanceof PDCheckBox) return PdfFormField.FieldType.CHECKBOX;
        if (field instanceof PDRadioButton) return PdfFormField.FieldType.RADIO;
        if (field instanceof PDChoice) return PdfFormField.FieldType.CHOICE;
        return PdfFormField.FieldType.TEXT;
    }
}
