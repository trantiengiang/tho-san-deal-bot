package vn.thosandeal.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "mock")
public record MockPricingProperties(
        BigDecimal finalPrice
) {
    public MockPricingProperties {
        if (finalPrice == null) {
            finalPrice = new BigDecimal("1490000");
        }
    }
}
