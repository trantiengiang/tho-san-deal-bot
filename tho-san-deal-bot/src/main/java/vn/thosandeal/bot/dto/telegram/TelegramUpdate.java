package vn.thosandeal.bot.dto.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Maps to the Telegram Update object.
 * https://core.telegram.org/bots/api#update
 *
 * Only fields needed for Phase 1 are mapped.
 * @JsonIgnoreProperties(ignoreUnknown = true) ensures forward compatibility.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdate(
        @JsonProperty("update_id") Long updateId,
        @JsonProperty("message") TelegramMessage message
) {
}
