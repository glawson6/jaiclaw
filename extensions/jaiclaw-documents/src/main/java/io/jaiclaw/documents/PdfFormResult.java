package io.jaiclaw.documents;

import java.util.List;

public sealed interface PdfFormResult {
    record Success(byte[] pdfBytes, int fieldsSet, List<String> skippedFields) implements PdfFormResult {
        public Success(byte[] pdfBytes, int fieldsSet) {
            this(pdfBytes, fieldsSet, List.of());
        }

        public Success {
            if (skippedFields == null) skippedFields = List.of();
        }
    }
    record Failure(String reason) implements PdfFormResult {}
}
