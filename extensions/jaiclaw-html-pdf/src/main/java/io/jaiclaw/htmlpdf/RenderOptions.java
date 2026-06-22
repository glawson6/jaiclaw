package io.jaiclaw.htmlpdf;

/**
 * Optional knobs for HTML-to-PDF rendering. {@link #baseUri} resolves
 * relative URLs (images, stylesheets) inside the HTML — leave null for
 * fully self-contained reports.
 */
public record RenderOptions(String baseUri) {

    public static RenderOptions defaults() {
        return new RenderOptions(null);
    }
}
