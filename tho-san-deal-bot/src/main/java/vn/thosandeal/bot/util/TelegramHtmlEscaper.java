package vn.thosandeal.bot.util;

/**
 * Escapes user-provided or product data for safe inclusion in Telegram HTML messages.
 * MandatoryFix #21.
 *
 * <p>Telegram HTML mode supports: &lt;b&gt;, &lt;i&gt;, &lt;a&gt;, &lt;code&gt;, &lt;pre&gt;.
 * Any raw user data must be escaped before embedding in these tags.
 *
 * <p>Specifically, only three characters need escaping in Telegram HTML:
 * &amp; → &amp;amp;
 * &lt; → &amp;lt;
 * &gt; → &amp;gt;
 */
public final class TelegramHtmlEscaper {

    private TelegramHtmlEscaper() {
        // Utility class
    }

    /**
     * Escapes a string for safe inclusion in a Telegram HTML message.
     * Returns empty string if input is null.
     */
    public static String escape(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /**
     * Escapes and truncates a string to the given max length.
     * Truncation happens before escaping to avoid counting escape sequences.
     */
    public static String escapeTruncated(String input, int maxLength) {
        if (input == null) {
            return "";
        }
        String truncated = input.length() > maxLength
                ? input.substring(0, maxLength) + "…"
                : input;
        return escape(truncated);
    }
}
