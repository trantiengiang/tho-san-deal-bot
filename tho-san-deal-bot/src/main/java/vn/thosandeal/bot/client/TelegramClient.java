package vn.thosandeal.bot.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import vn.thosandeal.bot.config.TelegramProperties;
import vn.thosandeal.bot.dto.request.SendMessageRequest;
import vn.thosandeal.bot.dto.telegram.TelegramApiResponse;

import java.time.Duration;
import java.util.Map;

/**
 * HTTP client for Telegram Bot API.
 *
 * <p>Security: The bot token is embedded in the base URL.
 * We NEVER log the base URL or the full request URL.
 * Only method name and response status are logged.
 * MandatoryFix #17, #28.
 *
 * <p>Timeouts: set at WebClient builder level in WebClientConfig.
 */
@Component
public class TelegramClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramClient.class);

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public TelegramClient(WebClient.Builder webClientBuilder,
                          TelegramProperties telegramProperties,
                          ObjectMapper objectMapper) {
        // Base URL contains token — never log this
        String baseUrl = "https://api.telegram.org/bot" + telegramProperties.botToken();
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    /**
     * Sends a text message to a chat.
     */
    public void sendMessage(String chatId, String text) {
        sendMessage(SendMessageRequest.html(chatId, text));
    }

    /**
     * Sends a message with full request control.
     */
    public void sendMessage(SendMessageRequest request) {
        post("/sendMessage", request);
    }

    /**
     * Calls getMe to verify bot token validity.
     */
    public Map<String, Object> getMe() {
        return postForMap("/getMe", Map.of());
    }

    /**
     * Deletes a message.
     */
    public void deleteMessage(String chatId, Long messageId) {
        post("/deleteMessage", Map.of("chat_id", chatId, "message_id", messageId));
    }

    /**
     * Edits an existing message text.
     */
    public void editMessageText(String chatId, Long messageId, String newText) {
        post("/editMessageText", Map.of(
                "chat_id", chatId,
                "message_id", messageId,
                "text", newText,
                "parse_mode", "HTML"
        ));
    }

    /**
     * Configures the webhook URL.
     *
     * @param webhookUrl  the HTTPS URL Telegram should call
     * @param secretToken the secret token for header validation
     */
    public void setWebhook(String webhookUrl, String secretToken) {
        post("/setWebhook", Map.of(
                "url", webhookUrl,
                "secret_token", secretToken,
                "allowed_updates", new String[]{"message"}
        ));
    }

    // -----------------------------------------------------------------------
    // Internal HTTP helpers
    // -----------------------------------------------------------------------

    private void post(String method, Object body) {
        try {
            webClient.post()
                    .uri(method)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response ->
                            response.bodyToMono(String.class).map(errorBody -> {
                                log.error("Telegram API error: method={} status={} body={}",
                                        method, response.statusCode().value(), errorBody);
                                return TelegramApiException.fromHttpStatus(
                                        response.statusCode().value(), errorBody);
                            })
                    )
                    .bodyToMono(String.class)
                    .timeout(DEFAULT_TIMEOUT)
                    .block();
        } catch (TelegramApiException e) {
            throw e;
        } catch (WebClientResponseException e) {
            log.error("Telegram API WebClient error: method={} status={}", method, e.getStatusCode().value(), e);
            throw TelegramApiException.fromHttpStatus(e.getStatusCode().value(), e.getMessage());
        } catch (Exception e) {
            log.error("Telegram API unexpected error: method={}", method, e);
            throw new TelegramApiException("Unexpected error calling Telegram API: " + method, e, true);
        }
    }

    private Map<String, Object> postForMap(String method, Object body) {
        try {
            String responseStr = webClient.post()
                    .uri(method)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(DEFAULT_TIMEOUT)
                    .block();

            TypeReference<TelegramApiResponse<Map<String, Object>>> typeRef =
                    new TypeReference<>() {};
            TelegramApiResponse<Map<String, Object>> response =
                    objectMapper.readValue(responseStr, typeRef);
            return response.result();
        } catch (Exception e) {
            log.error("Telegram API getMe error: method={}", method, e);
            throw new TelegramApiException("Failed to call " + method, e, true);
        }
    }
}
