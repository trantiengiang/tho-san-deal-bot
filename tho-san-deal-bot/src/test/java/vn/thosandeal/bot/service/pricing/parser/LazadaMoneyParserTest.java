package vn.thosandeal.bot.service.pricing.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class LazadaMoneyParserTest {

    @Test
    @DisplayName("Parse various Vietnamese currency representations into BigDecimal")
    void testParseVietnameseCurrency() {
        assertThat(LazadaMoneyParser.parse("2.719.920 ₫"))
                .isEqualByComparingTo(new BigDecimal("2719920"));

        assertThat(LazadaMoneyParser.parse("738.600 ₫"))
                .isEqualByComparingTo(new BigDecimal("738600"));

        assertThat(LazadaMoneyParser.parse("754.600"))
                .isEqualByComparingTo(new BigDecimal("754600"));

        assertThat(LazadaMoneyParser.parse("1490000 VND"))
                .isEqualByComparingTo(new BigDecimal("1490000"));

        assertThat(LazadaMoneyParser.parse("0 ₫"))
                .isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(LazadaMoneyParser.parse(null)).isNull();
        assertThat(LazadaMoneyParser.parse("   ")).isNull();
    }

    @Test
    @DisplayName("Canonical representation strips trailing zeros and formats plainly")
    void testCanonicalString() {
        assertThat(LazadaMoneyParser.toCanonicalString(new BigDecimal("738600"))).isEqualTo("738600");
        assertThat(LazadaMoneyParser.toCanonicalString(new BigDecimal("738600.00"))).isEqualTo("738600");
        assertThat(LazadaMoneyParser.toCanonicalString(new BigDecimal("738600.0000"))).isEqualTo("738600");
        assertThat(LazadaMoneyParser.toCanonicalString(new BigDecimal("0.00"))).isEqualTo("0");
        assertThat(LazadaMoneyParser.toCanonicalString(null)).isEqualTo("");
    }
}
