package vn.thosandeal.bot.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

class TelegramHtmlEscaperTest {

    @ParameterizedTest(name = "\"{0}\" → \"{1}\"")
    @CsvSource({
        "'Hello World',         'Hello World'",
        "'<b>bold</b>',         '&lt;b&gt;bold&lt;/b&gt;'",
        "'A & B',               'A &amp; B'",
        "'<script>alert(1)</script>', '&lt;script&gt;alert(1)&lt;/script&gt;'",
        "'Price > 1000',        'Price &gt; 1000'",
        "'a < b & c > d',       'a &lt; b &amp; c &gt; d'"
    })
    void shouldEscapeHtmlSpecialChars(String input, String expected) {
        assertThat(TelegramHtmlEscaper.escape(input)).isEqualTo(expected);
    }

    @Test
    void shouldReturnEmptyStringForNull() {
        assertThat(TelegramHtmlEscaper.escape(null)).isEqualTo("");
    }

    @Test
    void shouldReturnEmptyStringForNullTruncated() {
        assertThat(TelegramHtmlEscaper.escapeTruncated(null, 50)).isEqualTo("");
    }

    @Test
    void shouldTruncateLongStrings() {
        String longString = "a".repeat(200);
        String result = TelegramHtmlEscaper.escapeTruncated(longString, 50);
        // Should be 50 chars + "…" (U+2026 = 1 Java char) = 51 total
        assertThat(result).endsWith("…");
        assertThat(result.length()).isEqualTo(51); // 50 'a' chars + 1 '…' char
    }

    @Test
    void shouldNotTruncateShortStrings() {
        String shortString = "Hello";
        assertThat(TelegramHtmlEscaper.escapeTruncated(shortString, 50)).isEqualTo("Hello");
    }

    @Test
    void shouldEscapeAfterTruncation() {
        // Truncation happens before escaping
        // Input: 200 '<' chars, truncate to 5 → "<<<<<…"
        // Then escape: each '<' → '&lt;' = "&lt;&lt;&lt;&lt;&lt;…"
        String input = "<".repeat(200);
        String result = TelegramHtmlEscaper.escapeTruncated(input, 5);
        // 5 * "&lt;" + "…"
        assertThat(result).isEqualTo("&lt;&lt;&lt;&lt;&lt;…");
    }

    @Test
    void shouldHandlePlainTextWithoutEscaping() {
        assertThat(TelegramHtmlEscaper.escape("Hello, World! 123")).isEqualTo("Hello, World! 123");
    }

    @Test
    void shouldEscapeAmpersandFirst() {
        // Important: & must be escaped first to avoid double-escaping
        // "&lt;" should become "&amp;lt;" not "&&lt;lt;"
        assertThat(TelegramHtmlEscaper.escape("&lt;")).isEqualTo("&amp;lt;");
    }
}
