package vn.thosandeal.bot.client;

/**
 * Thrown when a Telegram API call fails.
 * Contains the HTTP status and Telegram error description.
 */
public class TelegramApiException extends RuntimeException {

    private final int httpStatus;
    private final String telegramDescription;
    private final boolean retryable;

    public TelegramApiException(String message, int httpStatus, String telegramDescription, boolean retryable) {
        super(message);
        this.httpStatus = httpStatus;
        this.telegramDescription = telegramDescription;
        this.retryable = retryable;
    }

    public TelegramApiException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.httpStatus = -1;
        this.telegramDescription = null;
        this.retryable = retryable;
    }

    public int getHttpStatus() { return httpStatus; }
    public String getTelegramDescription() { return telegramDescription; }
    public boolean isRetryable() { return retryable; }

    /**
     * 4xx errors (except 429) are permanent — should not retry.
     * 429 and 5xx are retryable.
     */
    public static TelegramApiException fromHttpStatus(int status, String description) {
        boolean retryable = (status == 429 || status >= 500);
        return new TelegramApiException(
                "Telegram API error: status=" + status + " desc=" + description,
                status,
                description,
                retryable
        );
    }
}
