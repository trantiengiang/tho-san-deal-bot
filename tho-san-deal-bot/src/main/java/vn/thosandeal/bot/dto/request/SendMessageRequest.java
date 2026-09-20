package vn.thosandeal.bot.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for Telegram sendMessage API call.
 * https://core.telegram.org/bots/api#sendmessage
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SendMessageRequest(
        @JsonProperty("chat_id") String chatId,
        @JsonProperty("text") String text,
        @JsonProperty("parse_mode") String parseMode,
        @JsonProperty("disable_web_page_preview") Boolean disableWebPagePreview,
        @JsonProperty("reply_to_message_id") Long replyToMessageId
) {
    public static SendMessageRequest html(String chatId, String text) {
        return new SendMessageRequest(chatId, text, "HTML", true, null);
    }

    public static SendMessageRequest plain(String chatId, String text) {
        return new SendMessageRequest(chatId, text, null, null, null);
    }
}
