package vn.thosandeal.bot.dto.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Generic Telegram API response wrapper.
 * All Telegram API endpoints return { "ok": bool, "result": T }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramApiResponse<T>(
        @JsonProperty("ok") boolean ok,
        @JsonProperty("result") T result,
        @JsonProperty("description") String description,
        @JsonProperty("error_code") Integer errorCode
) {
}
