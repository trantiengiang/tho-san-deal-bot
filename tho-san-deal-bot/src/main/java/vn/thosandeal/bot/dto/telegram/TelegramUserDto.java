package vn.thosandeal.bot.dto.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Named TelegramUserDto to avoid clash with entity TelegramUserEntity.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUserDto(
        @JsonProperty("id") Long id,
        @JsonProperty("is_bot") Boolean isBot,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        @JsonProperty("username") String username
) {
}
