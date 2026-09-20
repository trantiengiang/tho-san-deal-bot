package vn.thosandeal.bot.parser;

import org.springframework.stereotype.Component;
import vn.thosandeal.bot.exception.InvalidPriceException;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Vietnamese price input formats into BigDecimal.
 *
 * <p>Supported formats:
 * <ul>
 *   <li>1500000 → 1,500,000</li>
 *   <li>1.500.000 → 1,500,000</li>
 *   <li>1,500,000 → 1,500,000</li>
 *   <li>1500k → 1,500,000</li>
 *   <li>1.5tr → 1,500,000</li>
 *   <li>1tr5 → 1,500,000</li>
 * </ul>
 *
 * <p>Invalid: 0, negative, > 1,000,000,000, non-numeric.
 * Money is always BigDecimal — never double/float.
 */
@Component
public class PriceParser {

    private static final BigDecimal MAX_PRICE = new BigDecimal("1000000000");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    // Matches "1500k" or "1.5k"
    private static final Pattern K_PATTERN = Pattern.compile("^([\\d.,]+)k$", Pattern.CASE_INSENSITIVE);
    // Matches "1.5tr" or "1tr" (no suffix digit)
    private static final Pattern TR_PATTERN = Pattern.compile("^([\\d.,]+)tr$", Pattern.CASE_INSENSITIVE);
    // Matches "1tr5" → 1,500,000
    private static final Pattern TR_SUFFIX_PATTERN = Pattern.compile("^(\\d+)tr(\\d+)$", Pattern.CASE_INSENSITIVE);

    /**
     * Parses a price string and returns a BigDecimal.
     *
     * @param input the raw price string from the user
     * @return parsed BigDecimal price
     * @throws InvalidPriceException if the format is invalid or out of range
     */
    public BigDecimal parse(String input) {
        if (input == null || input.isBlank()) {
            throw new InvalidPriceException("Giá không được để trống");
        }
        String trimmed = input.trim().toLowerCase();

        BigDecimal result = tryParseKFormat(trimmed);
        if (result == null) result = tryParseTrSuffixFormat(trimmed);
        if (result == null) result = tryParseTrFormat(trimmed);
        if (result == null) result = tryParseNumeric(trimmed);
        if (result == null) {
            throw new InvalidPriceException("Định dạng giá không hợp lệ: " + input);
        }

        validate(result, input);
        return result;
    }

    private BigDecimal tryParseKFormat(String input) {
        Matcher m = K_PATTERN.matcher(input);
        if (!m.matches()) return null;
        BigDecimal base = parseNumericPart(m.group(1));
        return base.multiply(new BigDecimal("1000"));
    }

    private BigDecimal tryParseTrFormat(String input) {
        Matcher m = TR_PATTERN.matcher(input);
        if (!m.matches()) return null;
        BigDecimal base = parseNumericPart(m.group(1));
        return base.multiply(new BigDecimal("1000000"));
    }

    private BigDecimal tryParseTrSuffixFormat(String input) {
        Matcher m = TR_SUFFIX_PATTERN.matcher(input);
        if (!m.matches()) return null;
        // e.g. "1tr5" → 1 * 1,000,000 + 5 * 100,000
        long trPart = Long.parseLong(m.group(1));
        long suffix = Long.parseLong(m.group(2));
        // "1tr5" means 1.5 triệu = 1,500,000
        // suffix digit multiplier: 100,000 per digit (so "2" = 200,000)
        long suffixValue = suffix * 100_000L;
        return BigDecimal.valueOf(trPart * 1_000_000L + suffixValue);
    }

    private BigDecimal tryParseNumeric(String input) {
        try {
            return parseNumericPart(input);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parses a numeric string that may contain dots or commas as thousand separators.
     * E.g. "1.500.000" or "1,500,000" → 1500000
     * Rejects ambiguous decimals like "1.2.3".
     */
    private BigDecimal parseNumericPart(String input) {
        // Count dots and commas to detect format
        long dotCount = input.chars().filter(c -> c == '.').count();
        long commaCount = input.chars().filter(c -> c == ',').count();

        String cleaned;
        if (dotCount > 1 && commaCount == 0) {
            // "1.500.000" — dots as thousand separators
            // Validate: all groups after the first must be exactly 3 digits
            // "1.2.3" → groups are "1","2","3" — "2" is only 1 digit → invalid
            String[] dotGroups = input.split("\\.");
            for (int i = 1; i < dotGroups.length; i++) {
                if (dotGroups[i].length() != 3) {
                    throw new NumberFormatException("Invalid thousand-separator group: " + dotGroups[i]);
                }
            }
            cleaned = input.replace(".", "");
        } else if (commaCount > 1 && dotCount == 0) {
            // "1,500,000" — commas as thousand separators
            cleaned = input.replace(",", "");
        } else if (dotCount == 1 && commaCount == 0) {
            // "1.5" — treat as decimal (e.g. 1.5tr)
            cleaned = input;
        } else if (commaCount == 1 && dotCount == 0) {
            // "1,5" — treat as decimal with comma separator
            cleaned = input.replace(",", ".");
        } else if (dotCount == 0 && commaCount == 0) {
            // Plain integer "1500000"
            cleaned = input;
        } else {
            // Ambiguous like "1,500.000" or "1.2.3,4"
            throw new NumberFormatException("Ambiguous: " + input);
        }

        return new BigDecimal(cleaned);
    }

    private void validate(BigDecimal price, String original) {
        if (price.compareTo(ZERO) <= 0) {
            throw new InvalidPriceException("Giá phải lớn hơn 0. Bạn đã nhập: " + original);
        }
        if (price.compareTo(MAX_PRICE) > 0) {
            throw new InvalidPriceException("Giá không được vượt quá 1.000.000.000đ. Bạn đã nhập: " + original);
        }
        // Reject non-integer amounts (VND has no subunit in practice)
        if (price.stripTrailingZeros().scale() > 0) {
            throw new InvalidPriceException("Giá phải là số nguyên (VND không có phần lẻ): " + original);
        }
    }
}
