package com.studysync.presentation.ui.components;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.DefaultUrlSanitizer;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.UrlSanitizer;

import java.util.List;

/**
 * Markdown for reflection entries: entries are stored as plain markdown text
 * and rendered for reading, the way a note in Obsidian would be.
 */
final class Markdown {

    private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create());
    private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();
    private static final UrlSanitizer URL_SANITIZER = new UrlSanitizer() {
        private final UrlSanitizer links = new DefaultUrlSanitizer();

        @Override
        public String sanitizeLinkUrl(String url) {
            return links.sanitizeLinkUrl(url);
        }

        @Override
        public String sanitizeImageUrl(String url) {
            // WebView would fetch HTTP(S) images as soon as the reading view opens.
            return "";
        }
    };
    /**
     * Entry text is rendered into a JavaScript-enabled document holding every
     * other entry, so raw HTML in an entry is escaped rather than executed and
     * link URLs are sanitised — an entry that arrived over Drive sync is not
     * trusted markup.
     */
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder()
            .extensions(EXTENSIONS)
            .escapeHtml(true)
            .sanitizeUrls(true)
            .urlSanitizer(URL_SANITIZER)
            .build();

    /**
     * Keeps the view on the diary: the generated date links do their own work
     * through {@code onclick}, and every other link is inert rather than
     * navigating this WebView onto a page that would inherit the Java bridge.
     */
    private static final String NO_NAVIGATION = """
            <script>
            document.addEventListener('click', function (e) {
              var node = e.target;
              while (node && node.tagName !== 'A') { node = node.parentNode; }
              if (!node) { return; }
              var href = node.getAttribute('href') || '';
              if (href.charAt(0) === '#') { return; }
              e.preventDefault();
            }, true);
            </script>
            """;

    private static final String STYLE = """
            body { font-family: Georgia, 'Times New Roman', serif; font-size: 14px;
                   color: #2c3e50; line-height: 1.6; margin: 0; padding: 4px 18px 24px 4px;
                   background: transparent; }
            h1, h2, h3, h4 { font-family: Roboto, sans-serif; color: #2c3e50; }
            article { margin-bottom: 4px; }
            article > h2 { font-size: 14px; margin: 0 0 6px 0; }
            article > h2 a { color: #3498db; text-decoration: none; }
            article > h2 a:hover { text-decoration: underline; }
            article > :nth-child(2) { margin-top: 0; }
            hr { border: 0; border-top: 1px solid #ecf0f1; margin: 22px 0; }
            table { border-collapse: collapse; margin: 12px 0; }
            th, td { border: 1px solid #dfe4e8; padding: 5px 10px; text-align: left; }
            th { background: #f8f9fa; font-family: Roboto, sans-serif; font-size: 13px; }
            blockquote { border-left: 3px solid #dfe4e8; margin-left: 0; padding-left: 14px;
                         color: #7f8c8d; }
            code { background: #f4f6f8; padding: 1px 4px; border-radius: 3px;
                   font-size: 13px; }
            pre code { display: block; padding: 8px 10px; overflow-x: auto; }
            img { max-width: 100%; }
            .empty { color: #7f8c8d; font-style: italic; }
            """;

    private Markdown() { /* utility class */ }

    /** Renders one entry's markdown to an HTML fragment. */
    static String toHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        return RENDERER.render(PARSER.parse(markdown));
    }

    /** Wraps rendered entries in the styled document the diary's reading view loads. */
    static String page(String bodyHtml) {
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
                + STYLE + "</style></head><body>" + bodyHtml + NO_NAVIGATION + "</body></html>";
    }

    /**
     * A GitHub-flavoured table skeleton: placeholder headers and two empty
     * rows, padded so the source stays readable while it is being filled in.
     */
    static String tableSkeleton(int columns) {
        int cols = Math.max(1, columns);
        StringBuilder md = new StringBuilder("\n");
        for (int c = 1; c <= cols; c++) {
            md.append("| Column ").append(c).append(' ');
        }
        md.append("|\n");
        for (int c = 1; c <= cols; c++) {
            md.append("| --- ");
        }
        md.append("|\n");
        for (int row = 0; row < 2; row++) {
            md.append("|     ".repeat(cols)).append("|\n");
        }
        return md.toString();
    }

    /**
     * The first line worth putting in a list: heading markers and inline
     * emphasis are stripped, table rules and quote markers skipped, so an
     * entry that opens with a table still shows something readable.
     */
    static String previewLine(String markdown) {
        if (markdown == null) {
            return "";
        }
        for (String line : markdown.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith(">") || trimmed.startsWith("|")
                    || trimmed.startsWith("---") || trimmed.startsWith("```")) {
                continue;
            }
            return trimmed.replaceAll("^#+\\s*", "").replaceAll("[*_`]", "").trim();
        }
        // Nothing but tables or quotes — fall back to the first non-empty line.
        for (String line : markdown.split("\\R")) {
            if (!line.isBlank()) {
                return line.trim();
            }
        }
        return "";
    }
}
