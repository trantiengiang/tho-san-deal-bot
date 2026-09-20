package vn.thosandeal.bot.service.pricing.parser;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Safely parses Vietnamese currency strings (e.g., "2.719.920 ₫", "738.600 ₫", "754.600")
 * into BigDecimal without precision loss. Never uses double.
 */
public final class LazadaMoneyParser {

    private LazadaMoneyParser() {}

    /**
     * Parses a money string into a BigDecimal.
     * Examples:
     * - "2.719.920 ₫" -> 2719920
     * - "738.600 ₫"   -> 738600
     * - "754.600"     -> 754600
     * - "1490000"     -> 1490000
     */
    public static BigDecimal parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        // Clean common currency symbols, spaces, non-breaking spaces
        String cleaned = raw.replace("₫", "")
                .replace("VND", "")
                .replace("vnd", "")
                .replace("\u00a0", "")
                .replace(" ", "")
                .trim();

        if (cleaned.isEmpty()) {
            return null;
        }

        // Check if negative
        boolean isNegative = cleaned.startsWith("-");
        if (isNegative) {
            cleaned = cleaned.substring(1).trim();
        }

        // Vietnamese currency uses dots as thousands separators: "2.719.920" -> "2719920"
        // If there's a comma, it might be decimal or thousands. For VND, all integer prices have dots as thousand separators.
        // If there are dots and no commas: remove dots
        String digitsOnly = cleaned.replace(".", "").replace(",", "");

        try {
            BigDecimal val = new BigDecimal(digitsOnly);
            return isNegative ? val.negate() : val;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Cannot parse money string: '" + raw + "'", e);
        }
    }

    /**
     * Normalizes BigDecimal to a canonical string for fingerprinting and hashing.
     * Ensures 738600 and 738600.00 produce identical strings ("738600").
     */
    public static String toCanonicalString(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            return "0";
        }
        return amount.stripTrailingZeros().toPlainString();
    }
}
