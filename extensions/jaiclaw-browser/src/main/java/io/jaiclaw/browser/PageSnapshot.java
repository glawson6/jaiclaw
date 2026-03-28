package io.jaiclaw.browser;

import java.util.List;

/**
 * Serialized representation of a web page as an accessibility tree.
 * This is what the agent "sees" when interacting with a page.
 */
public record PageSnapshot(
        String url,
        String title,
        List<PageElement> elements
) {
    public record PageElement(int index, String role, String name, String value, String href) {
        public PageElement(int index, String role, String name) {
            this(index, role, name, null, null);
        }
    

        public static Builder builder() { return new Builder(); }

        public static final class Builder {
            private int index;
            private String role;
            private String name;
            private String value;
            private String href;

            public Builder index(int index) { this.index = index; return this; }
            public Builder role(String role) { this.role = role; return this; }
            public Builder name(String name) { this.name = name; return this; }
            public Builder value(String value) { this.value = value; return this; }
            public Builder href(String href) { this.href = href; return this; }

            public PageElement build() {
                return new PageElement(index, role, name, value, href);
            }
        }
}

    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append("URL: ").append(url).append("\n");
        sb.append("Title: ").append(title).append("\n\n");
        for (PageElement element : elements) {
            sb.append("[").append(element.index()).append("] ")
              .append(element.role());
            if (element.name() != null && !element.name().isEmpty()) {
                sb.append(" \"").append(element.name()).append("\"");
            }
            if (element.value() != null && !element.value().isEmpty()) {
                sb.append(" value=\"").append(element.value()).append("\"");
            }
            if (element.href() != null && !element.href().isEmpty()) {
                sb.append(" href=\"").append(element.href()).append("\"");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
