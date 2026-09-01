package com.studysync.presentation.ui.components;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownTest {

    private static int count(String haystack, String needle) {
        int hits = 0;
        for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + 1)) {
            hits++;
        }
        return hits;
    }

    @Test
    void tableSkeletonRendersAsATableWithTheRequestedColumns() {
        String html = Markdown.toHtml(Markdown.tableSkeleton(6));

        assertTrue(html.contains("<table>"), html);
        assertEquals(6, count(html, "<th>"));
        assertEquals(2, count(html, "<tr>") - 1); // one header row plus two empty body rows
    }

    @Test
    void filledInTableSurvivesRoundTrip() {
        String entry = """
                Tracked the day:

                | Time  | HR | Anxiety |
                | ----- | -: | ------: |
                | 09:00 | 70 |    2/10 |
                | 14:00 | 92 |    5/10 |
                """;

        String html = Markdown.toHtml(entry);

        assertEquals(3, count(html, "</th>")); // "<th" would also match <thead>
        assertTrue(html.contains("09:00"), html);
        assertTrue(html.contains("92"), html);
    }

    @Test
    void entryHtmlIsEscapedRatherThanExecuted() {
        String html = Markdown.toHtml("<script>alert('x')</script>\n\n[go](javascript:alert('x'))");

        assertTrue(html.contains("&lt;script&gt;"), html);
        assertFalse(html.contains("<script>"), html);
        assertFalse(html.contains("href=\"javascript:"), html);
    }

    @Test
    void remoteImagesCannotTriggerRequestsFromTheReadingView() {
        String html = Markdown.toHtml("![tracking pixel](https://attacker.example/pixel?id=42)");

        assertFalse(html.contains("attacker.example"), html);
        assertTrue(html.contains("alt=\"tracking pixel\""), html);
    }

    @Test
    void previewLineSkipsTableRowsAndStripsMarkers() {
        assertEquals("Rough afternoon", Markdown.previewLine("## Rough afternoon\n\n| a | b |\n| - | - |"));
        assertEquals("bold start", Markdown.previewLine("**bold start**"));
        // Nothing but a table: better to show the first row than nothing at all.
        assertEquals("| Time | HR |", Markdown.previewLine("| Time | HR |\n| --- | --- |"));
        assertEquals("", Markdown.previewLine(null));
    }
}
