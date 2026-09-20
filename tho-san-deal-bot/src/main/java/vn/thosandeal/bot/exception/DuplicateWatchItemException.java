package vn.thosandeal.bot.exception;

public class DuplicateWatchItemException extends RuntimeException {
    public DuplicateWatchItemException() {
        super("Duplicate active watch item");
    }
}
