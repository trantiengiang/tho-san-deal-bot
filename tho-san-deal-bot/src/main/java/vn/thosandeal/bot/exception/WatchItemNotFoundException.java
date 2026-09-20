package vn.thosandeal.bot.exception;

public class WatchItemNotFoundException extends RuntimeException {
    public WatchItemNotFoundException(Long id) {
        super("WatchItem not found or access denied: id=" + id);
    }
}
