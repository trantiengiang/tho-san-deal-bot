package vn.thosandeal.bot.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.thosandeal.bot.config.TelegramProperties;
import vn.thosandeal.bot.entity.NotificationOutbox;
import vn.thosandeal.bot.entity.WatchItem;
import vn.thosandeal.bot.repository.NotificationOutboxRepository;
import vn.thosandeal.bot.service.pricing.PriceCheckResult;
import vn.thosandeal.bot.util.MoneyFormatter;
import vn.thosandeal.bot.util.TelegramHtmlEscaper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Creates notification outbox entries for deal alerts.
 *
 * <p>This service does NOT call Telegram directly.
 * It only writes to the notification_outbox table.
 * The actual HTTP call is made by NotificationOutboxWorker.
 *
 * <p>Design: Called inside the same DB transaction as WatchItem state update.
 * If the transaction rolls back, the outbox entry is also rolled back — no orphaned notifications.
 * MandatoryFix #9, #10.
 */
@Service
public class DealNotificationService {

    private static final Logger log = LoggerFactory.getLogger(DealNotificationService.class);
    private static final int MAX_PRODUCT_NAME_LENGTH = 200;

    private final NotificationOutboxRepository outboxRepository;
    private final TelegramProperties telegramProperties;

    public DealNotificationService(NotificationOutboxRepository outboxRepository,
                                   TelegramProperties telegramProperties) {
        this.outboxRepository = outboxRepository;
        this.telegramProperties = telegramProperties;
    }

    /**
     * Creates a notification outbox entry for a triggered deal.
     * Must be called within a DB transaction (MandatoryFix #9).
     *
     * @param watchItem   the watch item that triggered
     * @param result      the price check result
     * @param triggerReason a short label for the trigger (A, B, or C) for idempotency key
     */
    @Transactional
    public boolean queueDealNotification(WatchItem watchItem, PriceCheckResult result, String triggerReason) {
        String fingerprint = computeFingerprint(watchItem.getId(), null, result.finalPrice(), triggerReason);

        if (outboxRepository.existsByTriggerFingerprint(fingerprint)) {
            log.info("Duplicate notification outbox entry (fingerprint={}), skipping. watchItemId={}",
                    fingerprint, watchItem.getId());
            return false;
        }

        String message = buildNotificationMessage(watchItem, result);

        NotificationOutbox outbox = NotificationOutbox.create(
                watchItem,
                resolveTargetChatId(watchItem),
                message,
                fingerprint
        );

        return saveOutboxEntry(outbox, fingerprint, watchItem.getId());
    }

    /**
     * Creates a notification outbox entry for an individual SKU deal trigger.
     * MandatoryFix #3, #5: Fingerprint includes skuId and canonical BigDecimal price.
     */
    @Transactional
    public boolean queueSkuDealNotification(WatchItem watchItem, vn.thosandeal.bot.entity.WatchSku sku, BigDecimal finalPrice, String triggerReason) {
        String skuId = sku != null ? sku.getSkuId() : null;
        String fingerprint = computeFingerprint(watchItem.getId(), skuId, finalPrice, triggerReason);

        if (outboxRepository.existsByTriggerFingerprint(fingerprint)) {
            log.info("Duplicate notification outbox entry (fingerprint={}), skipping. watchItemId={} skuId={}",
                    fingerprint, watchItem.getId(), skuId);
            return false;
        }

        String message = buildSkuNotificationMessage(watchItem, sku, finalPrice);

        NotificationOutbox outbox = NotificationOutbox.createSku(
                watchItem,
                sku,
                resolveTargetChatId(watchItem),
                message,
                fingerprint
        );

        return saveOutboxEntry(outbox, fingerprint, watchItem.getId());
    }

    private String resolveTargetChatId(WatchItem watchItem) {
        try {
            if (watchItem != null && watchItem.getUser() != null && watchItem.getUser().getTelegramUserId() != null) {
                return String.valueOf(watchItem.getUser().getTelegramUserId());
            }
        } catch (Exception e) {
            log.warn("Could not lazily resolve user telegramUserId for watchItemId={}: {}",
                    watchItem != null ? watchItem.getId() : null, e.getMessage());
        }
        return telegramProperties.notificationChannelId();
    }

    private boolean saveOutboxEntry(NotificationOutbox outbox, String fingerprint, Long watchItemId) {
        try {
            outboxRepository.saveAndFlush(outbox);
            log.info("Queued deal notification for watchItemId={} fingerprint={}", watchItemId, fingerprint);
            return true;
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.info("Duplicate notification outbox entry on concurrent insert (fingerprint={}), skipping. watchItemId={}",
                    fingerprint, watchItemId);
            return false;
        } catch (Exception e) {
            log.warn("Error saving notification outbox (fingerprint={}): {}", fingerprint, e.getMessage());
            return false;
        }
    }

    private String buildSkuNotificationMessage(WatchItem watchItem, vn.thosandeal.bot.entity.WatchSku sku, BigDecimal finalPrice) {
        BigDecimal targetPrice = watchItem.getTargetPrice();
        String productName = resolveProductName(null, watchItem);
        String variantName = sku != null ? sku.getVariantName() : null;

        String priceFormatted = MoneyFormatter.format(finalPrice);
        String targetFormatted = MoneyFormatter.format(targetPrice);
        String diffFormatted = MoneyFormatter.formatDifference(targetPrice, finalPrice);
        String url = TelegramHtmlEscaper.escape(watchItem.getOriginalUrl());

        StringBuilder sb = new StringBuilder();
        sb.append("🔥 <b>GIÁ ĐÃ CHẠM MỤC TIÊU!</b>\n\n");
        sb.append("📦 ").append(TelegramHtmlEscaper.escapeTruncated(productName, MAX_PRODUCT_NAME_LENGTH)).append("\n");

        if (variantName != null && !variantName.isBlank()) {
            sb.append("🎨 <b>Phân loại:</b> ").append(TelegramHtmlEscaper.escapeTruncated(variantName, 100)).append("\n");
        }

        sb.append("\n💰 <b>Giá ưu đãi:</b> ").append(priceFormatted).append("\n");
        sb.append("🎯 <b>Giá đang canh:</b> ").append(targetFormatted).append("\n");
        sb.append("📉 <b>Thấp hơn mục tiêu:</b> ").append(diffFormatted).append("\n");
        sb.append("\n🛒 <b>Mua ngay:</b>\n").append(url);

        return sb.toString();
    }

    private String buildNotificationMessage(WatchItem watchItem, PriceCheckResult result) {
        BigDecimal finalPrice = result.finalPrice();
        BigDecimal targetPrice = watchItem.getTargetPrice();

        String productName = resolveProductName(result.productName(), watchItem);
        String priceFormatted = MoneyFormatter.format(finalPrice);
        String targetFormatted = MoneyFormatter.format(targetPrice);
        String diffFormatted = MoneyFormatter.formatDifference(targetPrice, finalPrice);
        String url = TelegramHtmlEscaper.escape(watchItem.getOriginalUrl());

        StringBuilder sb = new StringBuilder();
        sb.append("🔥 <b>GIÁ ĐÃ CHẠM MỤC TIÊU!</b>\n\n");
        sb.append("📦 ").append(TelegramHtmlEscaper.escapeTruncated(productName, MAX_PRODUCT_NAME_LENGTH)).append("\n");

        if (result.variantName() != null && !result.variantName().isBlank()) {
            sb.append("🎨 ").append(TelegramHtmlEscaper.escapeTruncated(result.variantName(), 100)).append("\n");
        }

        sb.append("\n💰 <b>Giá hiện tại:</b>\n");
        sb.append(priceFormatted).append("\n");
        sb.append("\n🎯 <b>Giá đang canh:</b>\n");
        sb.append(targetFormatted).append("\n");
        sb.append("\n📉 <b>Thấp hơn mục tiêu:</b>\n");
        sb.append(diffFormatted).append("\n");
        sb.append("\n🛒 <b>Mua ngay:</b>\n");
        sb.append(url);

        return sb.toString();
    }

    private String resolveProductName(String resultName, WatchItem watchItem) {
        if (resultName != null && !resultName.isBlank()) {
            return resultName;
        }
        if (watchItem.getProductName() != null && !watchItem.getProductName().isBlank()) {
            return watchItem.getProductName();
        }
        return "Sản phẩm Lazada";
    }

    /**
     * Computes a SHA-256 fingerprint for deduplication.
     * Format: SHA256(watchItemId:skuId:canonicalPrice:triggerReason)
     * MandatoryFix #5: Canonical representation ensures 738600 and 738600.00 match.
     */
    public String computeFingerprint(Long watchItemId, String skuId, BigDecimal finalPrice, String triggerReason) {
        String canonicalPrice = vn.thosandeal.bot.service.pricing.parser.LazadaMoneyParser.toCanonicalString(finalPrice);
        String input = watchItemId + ":" + (skuId != null ? skuId : "") + ":" + canonicalPrice + ":" + (triggerReason != null ? triggerReason : "");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Queues an admin notification when a CAPTCHA/WAF challenge is encountered.
     * Uses dedicated fingerprint namespace: SHA256("ADMIN_CHALLENGE|" + sessionId + "|" + challengeGeneration).
     */
    @Transactional
    public boolean queueAdminChallengeNotification(Long sessionId, Long challengeGeneration, java.time.Instant cooldownUntil) {
        String fingerprint = computeAdminChallengeFingerprint(sessionId, challengeGeneration);
        if (outboxRepository.existsByTriggerFingerprint(fingerprint)) {
            log.info("Duplicate admin challenge notification (fingerprint={}), skipping", fingerprint);
            return false;
        }

        String formattedTime = cooldownUntil != null
                ? java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        .withZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).format(cooldownUntil)
                : "30 phút";

        String message = "⚠️ <b>Lazada yêu cầu xác minh.</b>\n\n"
                + "Bot đã tạm dừng kiểm tra giá theo tài khoản để bảo vệ phiên đăng nhập.\n\n"
                + "• <b>Trạng thái:</b> CHALLENGED\n"
                + "• <b>Thời gian thử lại sớm nhất:</b> " + formattedTime;

        NotificationOutbox outbox = NotificationOutbox.createAdminAlert(
                telegramProperties.notificationChannelId(),
                message,
                fingerprint
        );
        outboxRepository.saveAndFlush(outbox);
        log.warn("Queued admin challenge notification outbox entry for sessionId={} generation={}",
                sessionId, challengeGeneration);
        return true;
    }

    public String computeAdminChallengeFingerprint(Long sessionId, Long challengeGeneration) {
        String input = "ADMIN_CHALLENGE|" + (sessionId != null ? sessionId : "0") + "|" + (challengeGeneration != null ? challengeGeneration : "0");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
