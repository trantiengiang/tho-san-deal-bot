package vn.thosandeal.bot.dto.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramMessage(
        @JsonProperty("message_id") Long messageId,
        @JsonProperty("from") TelegramUserDto from,
        @JsonProperty("chat") TelegramChat chat,
        @JsonProperty("date") Long date,
        @JsonProperty("text") String text
) {
}
