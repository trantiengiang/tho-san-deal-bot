package vn.thosandeal.bot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pricing")
public record PricingProperties(
        long checkIntervalMs,
        long outboxIntervalMs
) {
    public PricingProperties {
        if (checkIntervalMs <= 0) {
            checkIntervalMs = 60_000L;
        }
        if (outboxIntervalMs <= 0) {
            outboxIntervalMs = 15_000L;
        }
    }
}
