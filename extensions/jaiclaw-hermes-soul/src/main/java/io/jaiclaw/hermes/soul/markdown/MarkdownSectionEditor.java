package io.jaiclaw.hermes.soul.markdown;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Splits a markdown document into top-level sections delimited by H1 headings
 * ({@code # Identity}, {@code # Style}, {@code # Avoid}, {@code # Defaults},
 * etc.) and supports add / replace / remove by heading.
 *
 * <p>Heading matching is case-sensitive, anchor-trimmed, and tolerates trailing
 * whitespace on the line. The body of a section is everything between its
 * heading and the next H1 heading (or end of document).
 *
 * <p>Used by {@code SoulAgentTool} to honour hermes' free-form section
 * convention without forcing a structured schema. Plan §5 task 1.7.
 */
public final class MarkdownSectionEditor {

    private MarkdownSectionEditor() {}

    /** A parsed top-level section. {@code body} excludes the heading line. */
    public record Section(String heading, String body) {
        public Section {
            heading = heading == null ? "" : heading;
            body = body == null ? "" : body;
        }

        public String render() {
            if (heading.isEmpty()) return body;
            StringBuilder sb = new StringBuilder();
            sb.append("# ").append(heading).append('\n');
            sb.append(body);
            return sb.toString();
        }
    }

    /** Parse a markdown document into ordered top-level sections. */
    public static List<Section> parse(String markdown) {
        List<Section> sections = new ArrayList<>();
        if (markdown == null || markdown.isEmpty()) return sections;
        String currentHeading = "";
        StringBuilder currentBody = new StringBuilder();
        for (String line : markdown.split("\n", -1)) {
            String stripped = line.stripTrailing();
            if (stripped.startsWith("# ") && !stripped.startsWith("## ")) {
                if (!currentHeading.isEmpty() || currentBody.length() > 0) {
                    sections.add(new Section(currentHeading, currentBody.toString()));
                }
                currentHeading = stripped.substring(2).strip();
                currentBody.setLength(0);
            } else {
                currentBody.append(line).append('\n');
            }
        }
        sections.add(new Section(currentHeading, currentBody.toString()));
        return sections;
    }

    /** Render a section list back to markdown. */
    public static String render(List<Section> sections) {
        StringBuilder sb = new StringBuilder();
        for (Section s : sections) {
            sb.append(s.render());
            if (!s.body().endsWith("\n")) sb.append('\n');
        }
        // Trim leading blank lines from the prelude-only case.
        while (sb.length() > 0 && sb.charAt(0) == '\n') sb.deleteCharAt(0);
        return sb.toString();
    }

    /**
     * Add or replace the section named {@code heading}. If the heading already
     * exists its body is replaced in place; otherwise a new section is appended.
     */
    public static String add(String markdown, String heading, String body) {
        Objects.requireNonNull(heading, "heading");
        Objects.requireNonNull(body, "body");
        String normalized = body.endsWith("\n") ? body : body + "\n";
        List<Section> sections = new ArrayList<>(parse(markdown));
        boolean replaced = false;
        for (int i = 0; i < sections.size(); i++) {
            if (heading.equals(sections.get(i).heading())) {
                sections.set(i, new Section(heading, normalized));
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            sections.add(new Section(heading, normalized));
        }
        return render(sections);
    }

    /**
     * Replace an existing section's body. Throws {@link UnknownSectionException}
     * if the section is not present.
     */
    public static String replace(String markdown, String heading, String body) {
        Objects.requireNonNull(heading, "heading");
        Objects.requireNonNull(body, "body");
        List<Section> sections = new ArrayList<>(parse(markdown));
        for (int i = 0; i < sections.size(); i++) {
            if (heading.equals(sections.get(i).heading())) {
                String normalized = body.endsWith("\n") ? body : body + "\n";
                sections.set(i, new Section(heading, normalized));
                return render(sections);
            }
        }
        throw new UnknownSectionException(heading);
    }

    /**
     * Remove the section named {@code heading}. Returns the original markdown
     * unchanged if the section is not present (idempotent).
     */
    public static String remove(String markdown, String heading) {
        Objects.requireNonNull(heading, "heading");
        List<Section> sections = parse(markdown);
        List<Section> kept = new ArrayList<>(sections.size());
        for (Section s : sections) {
            if (!heading.equals(s.heading())) kept.add(s);
        }
        return render(kept);
    }

    /** Thrown by {@link #replace} when the requested heading does not exist. */
    public static final class UnknownSectionException extends RuntimeException {
        public UnknownSectionException(String heading) {
            super("No section with heading '" + heading + "'");
        }
    }
}
