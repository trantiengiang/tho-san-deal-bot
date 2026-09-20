package vn.thosandeal.bot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.thosandeal.bot.entity.WatchItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface WatchItemRepository extends JpaRepository<WatchItem, Long> {

    /**
     * Find all active items for a given user, ordered by creation time.
     */
    List<WatchItem> findByUser_TelegramUserIdAndActiveTrueOrderByCreatedAtDesc(Long telegramUserId);

    /**
     * Find all globally active items for scheduler processing with user eagerly loaded.
     */
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"user"})
    List<WatchItem> findByActiveTrue();

    /**
     * Ownership-safe remove query: only matches if both id and user ownership match.
     * MandatoryFix #23.
     */
    Optional<WatchItem> findByIdAndUser_TelegramUserId(Long id, Long telegramUserId);

    /**
     * Check duplicate: same user, same normalizedUrl, same targetPrice, still active.
     * Application-level duplicate check (MandatoryFix #6).
     * DB partial index provides the ultimate constraint.
     */
    @Query("""
            SELECT COUNT(w) > 0
            FROM WatchItem w
            WHERE w.user.telegramUserId = :telegramUserId
              AND w.normalizedUrl = :normalizedUrl
              AND w.targetPrice = :targetPrice
              AND w.active = true
            """)
    boolean existsDuplicateActiveWatch(
            @Param("telegramUserId") Long telegramUserId,
            @Param("normalizedUrl") String normalizedUrl,
            @Param("targetPrice") BigDecimal targetPrice);
}
