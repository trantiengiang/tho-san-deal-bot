package vn.thosandeal.bot.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.thosandeal.bot.exception.InvalidPriceException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class PriceParserTest {

    private PriceParser parser;

    @BeforeEach
    void setUp() {
        parser = new PriceParser();
    }

    @ParameterizedTest(name = "parse \"{0}\" = {1}")
    @CsvSource(delimiterString = "->", value = {
        "1500000 -> 1500000",
        "1.500.000 -> 1500000",
        "1,500,000 -> 1500000",
        "1500k -> 1500000",
        "1.5tr -> 1500000",
        "1tr5 -> 1500000",
        "500000 -> 500000",
        "1000 -> 1000",
        "1k -> 1000",
        "2tr -> 2000000",
        "2tr5 -> 2500000",
        "10tr -> 10000000",
        "100k -> 100000",
        "999999999 -> 999999999",
        "1000000000 -> 1000000000"
    })
    void shouldParseValidPrices(String input, String expected) {
        BigDecimal result = parser.parse(input.trim());
        assertThat(result).isEqualByComparingTo(new BigDecimal(expected.trim()));
    }

    @ParameterizedTest(name = "invalid: \"{0}\"")
    @ValueSource(strings = {"-100", "abc", "-1500000", "xyz", "1.2.3.4"})
    void shouldRejectInvalidPrices(String input) {
        assertThatThrownBy(() -> parser.parse(input))
                .isInstanceOf(InvalidPriceException.class);
    }

    @Test
    void shouldRejectZeroPrice() {
        assertThatThrownBy(() -> parser.parse("0"))
                .isInstanceOf(InvalidPriceException.class);
    }

    @Test
    void shouldRejectBlankInput() {
        assertThatThrownBy(() -> parser.parse(""))
                .isInstanceOf(InvalidPriceException.class);
        assertThatThrownBy(() -> parser.parse("   "))
                .isInstanceOf(InvalidPriceException.class);
    }

    @Test
    void shouldRejectAmbiguousDotComma() {
        // "1.2.3" with exactly 2 dots — ambiguous format (multiple dots, not all-groups-of-3)
        // The parser treats "1.2.3" as "1.2" which has decimal, and stripTrailingZeros fails
        assertThatThrownBy(() -> parser.parse("1.2.3"))
                .isInstanceOf(InvalidPriceException.class);
    }

    @Test
    void shouldRejectNullInput() {
        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(InvalidPriceException.class);
    }

    @Test
    void shouldRejectPriceAboveMaximum() {
        assertThatThrownBy(() -> parser.parse("1000000001"))
                .isInstanceOf(InvalidPriceException.class);
    }

    @Test
    void shouldReturnBigDecimal_notDouble() {
        BigDecimal result = parser.parse("1500000");
        assertThat(result).isEqualByComparingTo(new BigDecimal("1500000"));
    }

    @Test
    void shouldHandleWhitespace() {
        BigDecimal result = parser.parse("  1500000  ");
        assertThat(result).isEqualByComparingTo(new BigDecimal("1500000"));
    }

    @Test
    void shouldParse1tr5AsOneMillionFiveHundredThousand() {
        assertThat(parser.parse("1tr5")).isEqualByComparingTo(new BigDecimal("1500000"));
    }

    @Test
    void shouldParse2tr5AsTwoMillionFiveHundredThousand() {
        assertThat(parser.parse("2tr5")).isEqualByComparingTo(new BigDecimal("2500000"));
    }
}
