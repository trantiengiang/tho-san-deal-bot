package vn.thosandeal.bot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import vn.thosandeal.bot.entity.TelegramUserEntity;
import vn.thosandeal.bot.entity.WatchItem;
import vn.thosandeal.bot.exception.DuplicateWatchItemException;
import vn.thosandeal.bot.exception.WatchItemNotFoundException;
import vn.thosandeal.bot.repository.WatchItemRepository;
import vn.thosandeal.bot.service.watch.WatchService;
import vn.thosandeal.bot.validator.LazadaUrlValidator;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WatchServiceTest {

    @Mock
    private WatchItemRepository watchItemRepository;

    @Mock
    private LazadaUrlValidator lazadaUrlValidator;

    private WatchService watchService;

    private TelegramUserEntity testUser;

    @BeforeEach
    void setUp() {
        watchService = new WatchService(watchItemRepository, lazadaUrlValidator);
        testUser = new TelegramUserEntity(123L, "testuser", "Test", "User");
        testUser.setId(1L);
    }

    // -----------------------------------------------------------------------
    // addWatchItem
    // -----------------------------------------------------------------------

    @Test
    void shouldAddWatchItemSuccessfully() {
        String url = "https://s.lazada.vn/abc";
        BigDecimal price = new BigDecimal("1500000");

        when(lazadaUrlValidator.normalize(url)).thenReturn(url);
        when(watchItemRepository.existsDuplicateActiveWatch(
                testUser.getTelegramUserId(), url, price)).thenReturn(false);

        WatchItem mockSaved = new WatchItem(testUser, url, url, price);
        mockSaved.setId(10L);
        when(watchItemRepository.save(any(WatchItem.class))).thenReturn(mockSaved);

        WatchItem result = watchService.addWatchItem(testUser, url, price);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getTargetPrice()).isEqualByComparingTo(price);
        verify(watchItemRepository).save(any(WatchItem.class));
    }

    @Test
    void shouldThrowDuplicateExceptionWhenDuplicate() {
        String url = "https://s.lazada.vn/abc";
        BigDecimal price = new BigDecimal("1500000");

        when(lazadaUrlValidator.normalize(url)).thenReturn(url);
        when(watchItemRepository.existsDuplicateActiveWatch(
                testUser.getTelegramUserId(), url, price)).thenReturn(true);

        assertThatThrownBy(() -> watchService.addWatchItem(testUser, url, price))
                .isInstanceOf(DuplicateWatchItemException.class);

        verify(watchItemRepository, never()).save(any());
    }

    @Test
    void shouldThrowDuplicateOnConstraintViolation() {
        // Race condition: existsDuplicateCheck passes but DB constraint fires
        String url = "https://s.lazada.vn/abc";
        BigDecimal price = new BigDecimal("1500000");

        when(lazadaUrlValidator.normalize(url)).thenReturn(url);
        when(watchItemRepository.existsDuplicateActiveWatch(anyLong(), anyString(), any()))
                .thenReturn(false);
        when(watchItemRepository.save(any()))
                .thenThrow(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> watchService.addWatchItem(testUser, url, price))
                .isInstanceOf(DuplicateWatchItemException.class);
    }

    // -----------------------------------------------------------------------
    // removeWatchItem — MandatoryFix #23
    // -----------------------------------------------------------------------

    @Test
    void shouldRemoveWatchItemWhenOwnerRequests() {
        Long itemId = 15L;
        Long telegramUserId = testUser.getTelegramUserId();

        WatchItem item = new WatchItem(testUser, "https://s.lazada.vn/abc",
                "https://s.lazada.vn/abc", new BigDecimal("1500000"));
        item.setId(itemId);
        item.setActive(true);

        when(watchItemRepository.findByIdAndUser_TelegramUserId(itemId, telegramUserId))
                .thenReturn(Optional.of(item));
        when(watchItemRepository.save(any())).thenReturn(item);

        assertThatCode(() -> watchService.removeWatchItem(itemId, telegramUserId))
                .doesNotThrowAnyException();

        assertThat(item.isActive()).isFalse();
        verify(watchItemRepository).save(item);
    }

    @Test
    void shouldThrowNotFoundWhenWrongUserTriesToRemove() {
        Long itemId = 15L;
        Long wrongUserId = 999L; // Different user

        when(watchItemRepository.findByIdAndUser_TelegramUserId(itemId, wrongUserId))
                .thenReturn(Optional.empty()); // Correctly returns empty for wrong user

        assertThatThrownBy(() -> watchService.removeWatchItem(itemId, wrongUserId))
                .isInstanceOf(WatchItemNotFoundException.class);

        verify(watchItemRepository, never()).save(any());
    }

    @Test
    void shouldThrowNotFoundForNonExistentItem() {
        Long itemId = 999L;
        Long telegramUserId = testUser.getTelegramUserId();

        when(watchItemRepository.findByIdAndUser_TelegramUserId(itemId, telegramUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> watchService.removeWatchItem(itemId, telegramUserId))
                .isInstanceOf(WatchItemNotFoundException.class);
    }

    // -----------------------------------------------------------------------
    // listByTelegramUserId
    // -----------------------------------------------------------------------

    @Test
    void shouldReturnEmptyListWhenNoItems() {
        when(watchItemRepository
                .findByUser_TelegramUserIdAndActiveTrueOrderByCreatedAtDesc(testUser.getTelegramUserId()))
                .thenReturn(List.of());

        var result = watchService.listByTelegramUserId(testUser.getTelegramUserId());
        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnMappedResponseDtos() {
        WatchItem item = new WatchItem(testUser,
                "https://s.lazada.vn/abc", "https://s.lazada.vn/abc", new BigDecimal("1500000"));
        item.setId(5L);

        when(watchItemRepository
                .findByUser_TelegramUserIdAndActiveTrueOrderByCreatedAtDesc(testUser.getTelegramUserId()))
                .thenReturn(List.of(item));

        var result = watchService.listByTelegramUserId(testUser.getTelegramUserId());
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(5L);
        assertThat(result.get(0).targetPrice()).isEqualByComparingTo("1500000");
    }
}
