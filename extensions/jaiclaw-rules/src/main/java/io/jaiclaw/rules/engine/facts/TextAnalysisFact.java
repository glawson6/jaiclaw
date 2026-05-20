package io.jaiclaw.rules.engine.facts;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Fact model for text analysis rules.
 * Holds input text and collects analysis results.
 */
@Data
@NoArgsConstructor
public class TextAnalysisFact {

    private String text;
    private String textLower;
    private String sentiment;
    private final List<String> keywords = new ArrayList<>();
    private final List<String> categories = new ArrayList<>();
    private String summary;

    @JsonCreator
    public TextAnalysisFact(@JsonProperty("text") String text) {
        this.text = text;
        this.textLower = text != null ? text.toLowerCase() : null;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
        this.textLower = text != null ? text.toLowerCase() : null;
    }

    public String getTextLower() {
        return textLower;
    }

    public static TextAnalysisFactBuilder builder() {
        return new TextAnalysisFactBuilder();
    }

    public static class TextAnalysisFactBuilder {
        private String text;
        private String sentiment;
        private String summary;

        public TextAnalysisFactBuilder text(String text) {
            this.text = text;
            return this;
        }

        public TextAnalysisFactBuilder sentiment(String sentiment) {
            this.sentiment = sentiment;
            return this;
        }

        public TextAnalysisFactBuilder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public TextAnalysisFact build() {
            TextAnalysisFact fact = new TextAnalysisFact(this.text);
            fact.setSentiment(this.sentiment);
            fact.setSummary(this.summary);
            return fact;
        }
    }

    public String getSentiment() {
        return sentiment;
    }

    public void setSentiment(String sentiment) {
        this.sentiment = sentiment;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void addKeyword(String keyword) {
        this.keywords.add(keyword);
    }

    public List<String> getCategories() {
        return categories;
    }

    public void addCategory(String category) {
        this.categories.add(category);
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    @Override
    public String toString() {
        return "TextAnalysisFact{" +
                "text='" + text + '\'' +
                ", textLower='" + textLower + '\'' +
                ", sentiment='" + sentiment + '\'' +
                ", keywords=" + keywords +
                ", categories=" + categories +
                ", summary='" + summary + '\'' +
                '}';
    }
}
