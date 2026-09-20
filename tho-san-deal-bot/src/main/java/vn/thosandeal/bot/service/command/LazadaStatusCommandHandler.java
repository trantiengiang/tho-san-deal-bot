package vn.thosandeal.bot.service.command;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.thosandeal.bot.config.TelegramProperties;
import vn.thosandeal.bot.dto.telegram.TelegramMessage;
import vn.thosandeal.bot.entity.LazadaSession;
import vn.thosandeal.bot.enums.CommandType;
import vn.thosandeal.bot.parser.ParsedCommand;
import vn.thosandeal.bot.service.pricing.session.LazadaSessionService;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Admin command handler for checking Lazada authenticated session status.
 *
 * <p>Restricted to telegram.allowed-user-ids.
 * Never prints raw cookies or secrets.
 */
@Component
public class LazadaStatusCommandHandler implements CommandHandler {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final LazadaSessionService sessionService;
    private final TelegramProperties telegramProperties;
    private final int maxPreviewsPerCycle;
    private final long minPreviewIntervalSeconds;
    private final long exactPriceTtlMinutes;

    public LazadaStatusCommandHandler(
            LazadaSessionService sessionService,
            TelegramProperties telegramProperties,
            @Value("${pricing.lazada.max-previews-per-cycle:3}") int maxPreviewsPerCycle,
            @Value("${pricing.lazada.min-preview-interval-seconds:5}") long minPreviewIntervalSeconds,
            @Value("${pricing.lazada.exact-price-ttl-minutes:15}") long exactPriceTtlMinutes) {
        this.sessionService = sessionService;
        this.telegramProperties = telegramProperties;
        this.maxPreviewsPerCycle = maxPreviewsPerCycle;
        this.minPreviewIntervalSeconds = minPreviewIntervalSeconds;
        this.exactPriceTtlMinutes = exactPriceTtlMinutes;
    }

    @Override
    public CommandType supportedCommand() {
        return CommandType.LAZADA_STATUS;
    }

    @Override
    public String handle(TelegramMessage message, ParsedCommand command) {
        Long senderId = message.from() != null ? message.from().id() : null;
        if (senderId == null || telegramProperties.allowedUserIds() == null
                || !telegramProperties.allowedUserIds().contains(senderId)) {
            return "⛔ Bạn không có quyền xem trạng thái hệ thống.";
        }

        Optional<LazadaSession> sessionOpt = sessionService.getLatestSession();
        if (sessionOpt.isEmpty()) {
            return "⚠️ Chưa có phiên đăng nhập Lazada nào được cấu hình trong cơ sở dữ liệu.";
        }

        LazadaSession session = sessionOpt.get();
        StringBuilder sb = new StringBuilder();
        sb.append("🔐 <b>TRẠNG THÁI PHIÊN LAZADA</b>\n\n");
        sb.append("• <b>Trạng thái:</b> ").append(session.getStatus().name()).append("\n");

        if (session.getAccountId() != null) {
            sb.append("• <b>Account ID:</b> ").append(session.getAccountId()).append("\n");
        }

        if (session.getLastValidatedAt() != null) {
            sb.append("• <b>Kiểm tra lần cuối:</b> ").append(FORMATTER.format(session.getLastValidatedAt())).append("\n");
        }

        if (session.getLastSuccessfulPreviewAt() != null) {
            sb.append("• <b>Preview thành công:</b> ").append(FORMATTER.format(session.getLastSuccessfulPreviewAt())).append("\n");
        }

        if (session.getChallengeDetectedAt() != null) {
            sb.append("• <b>Thử thách phát hiện:</b> ").append(FORMATTER.format(session.getChallengeDetectedAt())).append("\n");
        }

        if (session.getCooldownUntil() != null) {
            sb.append("• <b>Thời gian chờ (Cooldown until):</b> ").append(FORMATTER.format(session.getCooldownUntil())).append("\n");
        }

        if (session.getLastErrorSummary() != null && !session.getLastErrorSummary().isBlank()) {
            sb.append("• <b>Lỗi gần nhất:</b> ").append(session.getLastErrorSummary()).append("\n");
        }

        sb.append("\n⚙️ <b>CẤU HÌNH KIỂM SOÁT TẢI</b>\n");
        sb.append("• <b>Deep preview budget:</b> ").append(maxPreviewsPerCycle).append("/cycle\n");
        sb.append("• <b>Preview interval:</b> ").append(minPreviewIntervalSeconds).append("s\n");
        sb.append("• <b>Exact price TTL:</b> ").append(exactPriceTtlMinutes).append("m\n");

        return sb.toString();
    }
}
