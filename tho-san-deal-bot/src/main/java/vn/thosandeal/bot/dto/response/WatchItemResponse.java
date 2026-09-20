package vn.thosandeal.bot.dto.response;

import vn.thosandeal.bot.entity.WatchItem;
import vn.thosandeal.bot.util.MoneyFormatter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response DTO for WatchItem — entities are never exposed directly to callers.
 */
public record WatchItemResponse(
        Long id,
        String originalUrl,
        String productName,
        BigDecimal targetPrice,
        String targetPriceFormatted,
        boolean active,
        String status,
        Instant createdAt
) {
    public static WatchItemResponse from(WatchItem item) {
        return new WatchItemResponse(
                item.getId(),
                item.getOriginalUrl(),
                item.getProductName(),
                item.getTargetPrice(),
                MoneyFormatter.format(item.getTargetPrice()),
                item.isActive(),
                item.getStatus().name(),
                item.getCreatedAt()
        );
    }
}
