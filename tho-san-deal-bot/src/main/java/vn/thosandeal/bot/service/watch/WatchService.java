package vn.thosandeal.bot.service.watch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.thosandeal.bot.dto.response.WatchItemResponse;
import vn.thosandeal.bot.entity.TelegramUserEntity;
import vn.thosandeal.bot.entity.WatchItem;
import vn.thosandeal.bot.enums.WatchStatus;
import vn.thosandeal.bot.exception.DuplicateWatchItemException;
import vn.thosandeal.bot.exception.WatchItemNotFoundException;
import vn.thosandeal.bot.repository.WatchItemRepository;
import vn.thosandeal.bot.validator.LazadaUrlValidator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Core watchlist business logic.
 *
 * <p>Transaction boundary: all public methods are @Transactional at service layer.
 * Controller is NOT @Transactional.
 *
 * <p>Duplicate prevention (MandatoryFix #6):
 * 1. Application check with JPQL query
 * 2. DB partial unique index as safety net
 * 3. DataIntegrityViolationException caught and re-thrown as DuplicateWatchItemException
 */
@Service
public class WatchService {

    private static final Logger log = LoggerFactory.getLogger(WatchService.class);

    private final WatchItemRepository watchItemRepository;
    private final LazadaUrlValidator lazadaUrlValidator;

    public WatchService(WatchItemRepository watchItemRepository,
                        LazadaUrlValidator lazadaUrlValidator) {
        this.watchItemRepository = watchItemRepository;
        this.lazadaUrlValidator = lazadaUrlValidator;
    }

    /**
     * Adds a new watch item for the user.
     *
     * @param user        the persisted user entity
     * @param originalUrl the URL as provided by the user
     * @param targetPrice the desired price threshold
     * @return the saved WatchItem
     * @throws DuplicateWatchItemException if the same item is already being watched
     */
    @Transactional
    public WatchItem addWatchItem(TelegramUserEntity user, String originalUrl, BigDecimal targetPrice) {
        String normalizedUrl = lazadaUrlValidator.normalize(originalUrl);

        // Application-level duplicate check for friendly error message
        if (watchItemRepository.existsDuplicateActiveWatch(
                user.getTelegramUserId(), normalizedUrl, targetPrice)) {
            throw new DuplicateWatchItemException();
        }

        WatchItem item = new WatchItem(user, originalUrl, normalizedUrl, targetPrice);
        try {
            WatchItem saved = watchItemRepository.save(item);
            log.info("Added watch item id={} for telegramUserId={} url={}",
                    saved.getId(), user.getTelegramUserId(), normalizedUrl);
            return saved;
        } catch (DataIntegrityViolationException e) {
            // DB partial unique index caught a race-condition duplicate
            log.warn("Race condition duplicate watch for telegramUserId={} url={}", 
                    user.getTelegramUserId(), normalizedUrl);
            throw new DuplicateWatchItemException();
        }
    }

    /**
     * Lists all active watch items for a user.
     */
    @Transactional(readOnly = true)
    public List<WatchItemResponse> listByTelegramUserId(Long telegramUserId) {
        return watchItemRepository
                .findByUser_TelegramUserIdAndActiveTrueOrderByCreatedAtDesc(telegramUserId)
                .stream()
                .map(WatchItemResponse::from)
                .toList();
    }

    /**
     * Removes a watch item if it belongs to the requesting user.
     * MandatoryFix #23: query includes telegramUserId to prevent cross-user deletion.
     *
     * @throws WatchItemNotFoundException if item not found or does not belong to user
     */
    @Transactional
    public void removeWatchItem(Long watchItemId, Long telegramUserId) {
        WatchItem item = watchItemRepository
                .findByIdAndUser_TelegramUserId(watchItemId, telegramUserId)
                .orElseThrow(() -> new WatchItemNotFoundException(watchItemId));

        item.setActive(false);
        item.setStatus(WatchStatus.TRIGGERED); // soft delete
        watchItemRepository.save(item);
        log.info("Removed watch item id={} for telegramUserId={}", watchItemId, telegramUserId);
    }

    /**
     * Finds all active watch items across all users for scheduler use.
     */
    @Transactional(readOnly = true)
    public List<WatchItem> findActiveWatchItems() {
        return watchItemRepository.findByActiveTrue();
    }

    /**
     * Updates the last checked price of a watch item.
     * Called by scheduler after each price check.
     */
    @Transactional
    public void updateLastCheckedPrice(Long watchItemId, BigDecimal price) {
        watchItemRepository.findById(watchItemId).ifPresent(item -> {
            item.setLastCheckedPrice(price);
            item.setLastCheckedAt(Instant.now());
            watchItemRepository.save(item);
        });
    }

    /**
     * Updates the last notified price and timestamp.
     * Called after successfully creating a notification outbox entry.
     */
    @Transactional
    public void updateLastNotifiedPrice(Long watchItemId, BigDecimal price) {
        watchItemRepository.findById(watchItemId).ifPresent(item -> {
            item.setLastNotifiedPrice(price);
            item.setLastNotifiedAt(Instant.now());
            watchItemRepository.save(item);
        });
    }
}
