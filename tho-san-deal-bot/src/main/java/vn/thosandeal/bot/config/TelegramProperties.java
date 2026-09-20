package vn.thosandeal.bot.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@ConfigurationProperties(prefix = "telegram")
@Validated
public record TelegramProperties(
        @NotBlank String botToken,
        @NotBlank String webhookSecret,
        @NotBlank String notificationChannelId,
        List<Long> allowedUserIds
) {
}
