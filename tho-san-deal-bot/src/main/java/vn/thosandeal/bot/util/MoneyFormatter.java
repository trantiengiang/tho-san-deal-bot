package vn.thosandeal.bot.util;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Formats BigDecimal money values for display to the user.
 * Used ONLY at presentation layer — never in business logic or persistence.
 * Example: 1500000 → "1.500.000đ"
 */
public final class MoneyFormatter {

    private MoneyFormatter() {
        // Utility class — no instantiation
    }

    /**
     * Formats a BigDecimal as Vietnamese dong (VND) with dot separators.
     * Example: 1500000 → "1.500.000đ"
     */
    public static String format(BigDecimal amount) {
        if (amount == null) {
            return "N/A";
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        DecimalFormat formatter = new DecimalFormat("#,##0", symbols);
        return formatter.format(amount) + "đ";
    }

    /**
     * Formats the positive difference between two prices.
     * Example: target=1500000, current=1490000 → "10.000đ"
     */
    public static String formatDifference(BigDecimal target, BigDecimal current) {
        if (target == null || current == null) {
            return "N/A";
        }
        BigDecimal diff = target.subtract(current).abs();
        return format(diff);
    }
}
