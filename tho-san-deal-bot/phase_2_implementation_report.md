# BÁO CÁO KỸ THUẬT: HOÀN TẤT TRIỂN KHAI PHASE 2
## PURE HTTP AUTHENTICATED LAZADA PER-SKU PRICE PROVIDER
**Dự án:** Bot Thợ Săn Deal (`@thosandeal_bot`)  
**Ngày báo cáo:** 17/09/2026  
**Trạng thái:** `IMPLEMENTATION_COMPLETE` | `ALL_TESTS_PASSED` | `PACKAGE_SUCCESS`  
**Đối tượng thẩm định:** ChatGPT & Claude Review Board  

---

## 1. TỔNG QUAN KẾT QUẢ (EXECUTIVE SUMMARY)

Đã hoàn thành 100% việc lập trình Phase 2 cho hệ thống bot lấy giá tài khoản Lazada (Account-Specific Voucher Price) theo đúng cam kết từ Discovery Round 3 và 10 chỉ thị bắt buộc (Mandatory Implementation Fixes).

* **Công nghệ cốt lõi:** **Pure HTTP WebClient** (100% không dùng Playwright / Selenium / Headless Chrome).
* **Tác động giỏ hàng (Cart State):** **Zero Cart Mutation** (sử dụng Trade Render / Shipping Preview POST flow với `buyParams`, tuyệt đối không thêm/xóa/sửa giỏ hàng cá nhân).
* **Độ sâu phân loại:** **Per-SKU Variant Processing** (Quét tất cả biến thể khả dụng của sản phẩm, định danh chính xác `(itemId, skuId)`).
* **Chất lượng giá:** `PriceQuality.EXACT_ACCOUNT` được trích xuất từ `module.data[itemKey].fields.discountPrice.price` (giá sau khi áp dụng voucher tài khoản, trước phí ship).
* **Kết quả Build & Test:**
  * `mvn clean test`: **131 tests run, 0 failures, 0 errors, 3 skipped** (128 active unit/component tests passed 100%).
  * `mvn clean package`: **BUILD SUCCESS** tạo thành công artifact `tho-san-deal-bot-1.0.0-SNAPSHOT.jar`.

---

## 2. BÁO CÁO CHI TIẾT 10 MANDATORY FIXES ĐÃ THỰC HIỆN

### Fix 1: AES Key Handling & GCM Security
* **Biến môi trường bắt buộc:** `LAZADA_SESSION_ENCRYPTION_KEY_BASE64`.
* **Ràng buộc độ dài:** Phải decode thành đúng **32 bytes (256 bits)**. Nếu sai định dạng hoặc khác 32 bytes, ứng dụng ném `IllegalStateException` và dừng khởi động ngay lập tức (fail-fast).
* **Mã hóa:** Chuẩn `AES/GCM/NoPadding` với Authentication Tag 128-bit.
* **Vector khởi tạo (IV):** Sinh ngẫu nhiên **12 bytes** bằng `SecureRandom` cho mỗi lần mã hóa; không bao giờ tái sử dụng IV.
* **Đóng gói Payload:** Định dạng versioned string: `v1:<Base64(12-byte IV + Ciphertext + Tag)>`.
* **File triển khai:** `SessionCryptoService.java`
* **Test chứng minh:** `SessionCryptoServiceTest` (test key < 32 bytes, > 32 bytes, key sai không thể giải mã, random IV tạo ciphertext khác nhau, giải mã khôi phục đúng plaintext).

### Fix 2: Provider Abstraction & Contract Separation
* **Phase 2 Primary Contract:** Tạo interface `ProductPriceProvider` trả về `ProductPriceCheckResult` (chứa danh sách đa biến thể `List<LazadaSkuPrice>`).
* **Implementation:** `AuthenticatedLazadaPriceProvider` chỉ implement duy nhất `ProductPriceProvider`, không trộn lẫn logic single-price của Phase 1.
* **Backward Compatibility:** Tạo `LegacyPriceProviderAdapter implements PriceProvider` (được cấu hình `@Primary` khi `pricing.provider=lazada-auth`), tự động chọn biến thể có `productPayable` tốt nhất cho các thành phần Phase 1 cũ nếu cần.
* **Mock Provider:** `MockPriceProvider` đồng thời implement cả 2 interface để phục vụ dev/test độc lập mà không cần môi trường ngoài.

### Fix 3: SKU Notification State Ordering
* **Luồng kiểm tra và cập nhật tuần tự (Strict Order):**
  1. Load `WatchSku` từ cơ sở dữ liệu.
  2. Lưu biến tạm: `previousCheckedPrice = sku.getLastCheckedPrice()`, `lastNotifiedPrice = sku.getLastNotifiedPrice()`.
  3. Nhận `newPrice` từ provider.
  4. Đánh giá điều kiện gửi thông báo qua `NotificationDecisionService.shouldNotify(targetPrice, previousCheckedPrice, lastNotifiedPrice, newPrice)`. **Tuyệt đối không ghi đè `lastCheckedPrice` trước khi đánh giá.**
  5. Nếu `shouldNotify == true`: tạo sự kiện outbox (`queueSkuDealNotification`). Nếu lưu thành công thì cập nhật `lastNotifiedPrice = newPrice` và `lastNotifiedAt = now`.
  6. Sau cùng mới persist `lastCheckedPrice = newPrice` và `lastCheckedAt = now`.
* **File triển khai:** `WatchSkuService.java`, `NotificationDecisionService.java`
* **Test chứng minh:** `SkuNotificationDecisionTest` chứng minh độc lập chuỗi chuyển trạng thái:
  * `1.600.000 -> 1.490.000` = **NOTIFY** (Rule A / B)
  * `1.490.000 -> 1.490.000` = **NO NOTIFY** (giá bằng mức đã báo)
  * `1.490.000 -> 1.480.000` = **NOTIFY** (Rule C: giảm sâu hơn mức đã báo)
  * `1.480.000 -> 1.510.000` = **NO NOTIFY** (tăng trên giá mục tiêu)
  * `1.510.000 -> 1.495.000` = **NOTIFY** (Rule B: quay lại dưới mục tiêu)

### Fix 4: Semantic HTTP Response Validation
* **Phân loại lỗi phản hồi HTTP 200 trước khi parse:**
  * `SESSION_EXPIRED`: Chứa form login (`/user/login`, `loginByToken`, phiên đăng nhập hết hạn).
  * `CAPTCHA_REQUIRED`: Chứa form xác minh bảo mật, geetest, slide captcha, punish box.
  * `WAF_BLOCKED`: Chứa Cloudflare challenge (`cf-chl`), WAF block, 403 Forbidden body.
  * `SKU_MISMATCH`: initData không chứa node khớp cả `itemId` và `skuId`.
  * `INVALID_PREVIEW_RESPONSE`: Thiếu thẻ script chứa `window.__initData__`.
  * `PARSE_FAILED`: Lỗi trích xuất JSON hoặc Jackson mapping.
* **Halt Batch Defense:** Nếu phát hiện `SESSION_EXPIRED`, `CAPTCHA_REQUIRED` hoặc `WAF_BLOCKED`, batch dừng ngay lập tức, đánh dấu phiên là `EXPIRED` trong DB và không gửi tiếp request cho các SKU còn lại.
* **File triển khai:** `CheckoutInitDataParser.java`, `AuthenticatedLazadaPriceProvider.java`
* **Test chứng minh:** `CheckoutInitDataParserTest` (HTTP 200 login page, HTTP 200 captcha page, SKU mismatch) và `AuthenticatedLazadaPriceProviderTest` (session expired ở giữa batch thì SKU kế tiếp không bị gọi request).

### Fix 5: Outbox Idempotency & Canonical BigDecimal Hashing
* **Khóa chống trùng lặp tuyệt đối:** `trigger_fingerprint` là UNIQUE trong bảng `notification_outbox`.
* **Đầu vào Fingerprint:** `SHA-256(watchItemId + ":" + skuId + ":" + canonicalPrice + ":" + triggerReason)`.
* **Chuẩn hóa số tiền:** Dùng `LazadaMoneyParser.toCanonicalString(BigDecimal)` (`stripTrailingZeros().toPlainString()` và xử lý 0). Đảm bảo `738600`, `738600.00` và `738600.0000` tạo ra cùng một chuỗi `"738600"` và cùng một SHA-256 hash.
* **Xử lý xung đột đồng thời:** Bắt `DataIntegrityViolationException` tại `DealNotificationService`, ghi log info và trả về `false` (coi như sự kiện đã tồn tại), không làm văng exception hay rollback scheduler transaction.
* **File triển khai:** `DealNotificationService.java`, `LazadaMoneyParser.java`
* **Test chứng minh:** `DealNotificationServiceTest` (test canonical BigDecimal hashing, test xung đột duplicate outbox insert không gây crash).

### Fix 6 & 7: Session Import An Toàn & Versioned Payload
* **Không truyền cookie qua Telegram hoặc CLI arguments.**
* **Local-only Import Flow:**
  1. Đọc file local `secrets/lazada-session.json`.
  2. Kiểm tra bắt buộc các cookie tối thiểu: `lzd_sid`, `lzd_uid`, `cna`. Lọc bỏ cookie rác.
  3. Đóng gói JSON có versioning: `{"version": 1, "cookies": {...}, "accountId": "..."}`.
  4. Mã hóa toàn bộ payload bằng `SessionCryptoService` (AES-256-GCM).
  5. Lưu ciphertext vào bảng `lazada_session`.
* **Bảo mật Git:** Đã thêm `secrets/` và `*.session.json` vào `.gitignore`. Không bao giờ in giá trị cookie ra console hoặc log.
* **File triển khai:** `LazadaSessionService.java`, `.gitignore`

### Fix 8: Batch Session Validation & Single Admin Notification
* Kiểm tra phiên hoạt động trước khi thực hiện batch SKU.
* Nếu phiên hết hạn trong quá trình chạy batch: cập nhật trạng thái session sang `EXPIRED`, ghi nhận timestamp lỗi.
* Quản lý cờ `adminNotifiedAt`: chỉ gửi cảnh báo admin 1 lần duy nhất khi phiên vừa hết hạn, tránh spam admin. Khi phiên được active lại, cờ được reset.
* **File triển khai:** `LazadaSessionService.java`, `AuthenticatedLazadaPriceProvider.java`

### Fix 9: Requested SKU Verification (Không lấy nhầm SKU)
* `CheckoutInitDataParser` không lấy node đầu tiên (`item_0` hay `item_<hash>`).
* Thuật toán duyệt qua tất cả các field trong `module.data`, tìm node thỏa mãn đồng thời:
  `fields.itemId == requestedItemId` VÀ `fields.sku.skuId == requestedSkuId`.
* Chỉ khi tìm thấy node khớp chính xác thì giá `discountPrice` mới được gắn nhãn `PriceQuality.EXACT_ACCOUNT`. Nếu không khớp, trả về `SKU_MISMATCH` và không bắn thông báo.
* **File triển khai:** `CheckoutInitDataParser.java`

### Fix 10: Suite Kiểm Thử Toàn Diện (11 Test Cases)
Đã triển khai đầy đủ 11 nhóm kiểm thử unit & component test:
1. `testInvalidAesKeyLengthFails`: Kiểm tra từ chối key < 32 bytes, > 32 bytes, chuỗi không phải base64.
2. `testWrongKeyCannotDecrypt`: Key sai gây lỗi giải mã GCM authentication.
3. `testRandomIvProducesDifferentCiphertext`: IV ngẫu nhiên bảo đảm tính ẩn danh mật mã.
4. `testEncryptDecryptRoundtrip`: Plaintext giải mã nguyên vẹn 100%.
5. `testCanonicalBigDecimalFingerprint`: Giá `738600` và `738600.00` sinh cùng SHA-256; giá khác hoặc SKU khác sinh SHA-256 khác.
6. `testLoginPageDetectedAsSessionExpired`: Nhận diện HTML trang đăng nhập khi status 200.
7. `testCaptchaPageDetectedAsCaptchaRequired`: Nhận diện HTML trang captcha/punish khi status 200.
8. `testSkuMismatch`: Từ chối khi trả về sai SKU so với SKU yêu cầu.
9. `testNotificationTransitionOrdering`: Chuỗi biến thiên giá 5 bước trên từng SKU độc lập.
10. `testSessionExpiresMidBatchHaltsCheckout`: Dừng batch ngay lập tức khi session hết hạn giữa chừng.
11. `testConcurrentDuplicateInsertHandledGracefully`: Xử lý trùng lặp outbox an toàn khi đồng thời ghi.

---

## 3. CƠ SỞ DỮ LIỆU & FLYWAY MIGRATIONS

Hệ sinh thái database được giữ nguyên toàn bộ các migration `V1` đến `V6` đã có trên Neon PostgreSQL. Bổ sung các migration Phase 2 độc lập:

1. **`V7__create_watch_sku.sql`**: Bảng `watch_sku` lưu trữ trạng thái chi tiết từng biến thể (giá gốc, giá sale, giá sau voucher, chất lượng giá, tồn kho, timestamp kiểm tra và thông báo, khóa lạc quan `@Version`).
2. **`V8__create_lazada_session.sql`**: Bảng `lazada_session` lưu payload cookie mã hóa AES-256, account ID, trạng thái phiên (`ACTIVE`, `EXPIRED`, `NEEDS_LOGIN`), timestamps kiểm tra và cảnh báo admin.
3. **`V9__update_notification_outbox_for_sku.sql`**: Bổ sung khóa ngoại `watch_sku_id` tham chiếu đến `watch_sku(id)` trong `notification_outbox`.
4. **`V10__add_sku_fields_to_price_check_log.sql`**: Bổ sung các cột `watch_sku_id`, `sku_id`, `variant_name`, `price_quality` vào `price_check_log` để audit giá từng SKU.

---

## 4. LỆNH QUẢN TRỊ ADMIN MỚI: `/lazada_status`

* Đã tạo `LazadaStatusCommandHandler.java` và tích hợp vào `MessageCommandParser` (hỗ trợ lệnh có dấu gạch dưới `_`).
* **Bảo mật:** Chỉ user có ID nằm trong `telegram.allowed-user-ids` mới có quyền thực thi. Người lạ bị từ chối truy cập.
* **An toàn dữ liệu:** Chỉ hiển thị trạng thái (`ACTIVE`/`EXPIRED`), Account ID, thời gian validate, thời gian preview thành công gần nhất và tóm tắt lỗi nếu có. Tuyệt đối **không hiển thị cookie, session ID hay ciphertext**.

---

## 5. BẰNG CHỨNG THỰC THI (BUILD EVIDENCE)

### Kết quả chạy `mvn clean test`:
```text
[INFO] Running vn.thosandeal.bot.parser.LazadaUrlValidatorTest (23 tests - SUCCESS)
[INFO] Running vn.thosandeal.bot.parser.MessageCommandParserTest (21 tests - SUCCESS)
[INFO] Running vn.thosandeal.bot.parser.PriceParserTest (29 tests - SUCCESS)
[INFO] Running vn.thosandeal.bot.service.command.LazadaStatusCommandHandlerTest (2 tests - SUCCESS)
[INFO] Running vn.thosandeal.bot.service.notification.DealNotificationServiceTest (3 tests - SUCCESS)
[INFO] Running vn.thosandeal.bot.service.notification.SkuNotificationDecisionTest (2 tests - SUCCESS)
[INFO] Running vn.thosandeal.bot.service.NotificationDecisionServiceTest (9 tests - SUCCESS)
[INFO] Running vn.thosandeal.bot.service.pricing.AuthenticatedLazadaPriceProviderTest (1 test - SUCCESS)
[INFO] Running vn.thosandeal.bot.service.pricing.parser.CheckoutInitDataParserTest (5 tests - SUCCESS)
[INFO] Running vn.thosandeal.bot.service.pricing.parser.LazadaMoneyParserTest (2 tests - SUCCESS)
[INFO] Running vn.thosandeal.bot.service.pricing.session.SessionCryptoServiceTest (5 tests - SUCCESS)
[INFO] Running vn.thosandeal.bot.service.TelegramUpdateInboxServiceTest (4 tests - SUCCESS)
[INFO] Running vn.thosandeal.bot.service.watch.WatchSkuServiceTest (1 test - SUCCESS)
[INFO] Running vn.thosandeal.bot.service.WatchServiceTest (8 tests - SUCCESS)
[INFO] Running vn.thosandeal.bot.util.TelegramHtmlEscaperTest (13 tests - SUCCESS)
[INFO] 
[INFO] Results:
[INFO] Tests run: 131, Failures: 0, Errors: 0, Skipped: 3
[INFO] BUILD SUCCESS
```

### Kết quả chạy `mvn clean package`:
```text
[INFO] --- jar:3.4.2:jar (default-jar) @ tho-san-deal-bot ---
[INFO] Building jar: C:\Bot_ThoSanDeal\tho-san-deal-bot\target\tho-san-deal-bot-1.0.0-SNAPSHOT.jar
[INFO] --- spring-boot:3.3.4:repackage (repackage) @ tho-san-deal-bot ---
[INFO] Replacing main artifact with repackaged archive, adding nested dependencies in BOOT-INF/.
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
```

---

## 6. HƯỚNG DẪN KÍCH HOẠT MÔI TRƯỜNG PRODUCTION (DEVOPS RUNBOOK)

1. **Tạo Session Encryption Key (32 bytes Base64):**
   ```powershell
   # Tạo key 32 bytes ngẫu nhiên dạng Base64
   $bytes = New-Object byte[] 32; (New-Object Security.Cryptography.RNGCryptoServiceProvider).GetBytes($bytes); [Convert]::ToBase64String($bytes)
   ```
   Gán vào file cấu hình môi trường:
   `LAZADA_SESSION_ENCRYPTION_KEY_BASE64=<chuỗi base64 32 bytes>`

2. **Nhập Session Lazada Lần Đầu:**
   Tạo file local `secrets/lazada-session.json`:
   ```json
   {
     "cookies": {
       "lzd_sid": "<session_id>",
       "lzd_uid": "<account_user_id>",
       "cna": "<cna_cookie>"
     },
     "accountId": "<account_user_id>"
   }
   ```
   (File này nằm trong `.gitignore`, an toàn trên máy local).

3. **Chuyển Provider sang Authenticated Provider:**
   Thiết lập: `PRICING_PROVIDER=lazada-auth`.
   Khởi động bot. Scheduler sẽ tự động dùng session đã mã hóa trong database để trích xuất giá voucher tài khoản per-SKU.
