package vn.thosandeal.bot.service.pricing.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutInitDataParserTest {

    private CheckoutInitDataParser parser;

    @BeforeEach
    void setUp() {
        parser = new CheckoutInitDataParser(new ObjectMapper());
    }

    @Test
    @DisplayName("1. Valid initData wins over static /user/login link (not SESSION_EXPIRED)")
    void testValidInitDataWithStaticLoginLink() {
        String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Checkout</title>
                    <script>
                    window.__initData__ = {
                        "success": true,
                        "module": {
                            "data": {
                                "item_123": {
                                    "fields": {
                                        "itemId": "13378541841",
                                        "sku": { "skuId": "116964266241", "skuText": "Blue / White" },
                                        "price": { "currentPrice": "2.125.000 ₫" },
                                        "discountPrice": { "price": "738.600 ₫" }
                                    }
                                }
                            }
                        }
                    };
                    </script>
                </head>
                <body>
                    <header>
                        <a href="//member.lazada.vn/user/login">Đăng nhập</a>
                    </header>
                </body>
                </html>
                """;

        CheckoutParsedResult result = parser.parse(html, "13378541841", "116964266241");
        assertThat(result.status()).isEqualTo(CheckoutParsedResult.Status.SUCCESS);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isFatalSessionError()).isFalse();
        assertThat(result.productPayable()).isEqualByComparingTo(new BigDecimal("738600"));
    }

    @Test
    @DisplayName("2. Valid initData with loginByToken text in client script is NOT SESSION_EXPIRED")
    void testValidInitDataWithLoginByTokenScript() {
        String html = """
                <html>
                <head>
                    <script>var loginConfig = { loginByToken: true };</script>
                    <script>
                    window.__initData__ = {
                        "success": true,
                        "module": {
                            "data": {
                                "item_123": {
                                    "fields": {
                                        "itemId": "13378541841",
                                        "sku": { "skuId": "116964266241", "skuText": "Blue / White" },
                                        "price": { "currentPrice": "2.125.000 ₫" },
                                        "discountPrice": { "price": "738.600 ₫" }
                                    }
                                }
                            }
                        }
                    };
                    </script>
                </head>
                <body></body>
                </html>
                """;

        CheckoutParsedResult result = parser.parse(html, "13378541841", "116964266241");
        assertThat(result.status()).isEqualTo(CheckoutParsedResult.Status.SUCCESS);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("3. No initData + real login page is classified as SESSION_EXPIRED")
    void testNoInitDataLoginPageClassifiedAsSessionExpired() {
        String loginHtml = """
                <!DOCTYPE html>
                <html>
                <head><title>Lazada Login</title></head>
                <body>
                    <div class="login-box">
                        <form action="/user/login" method="post">
                            <input name="loginByToken" value="false"/>
                            <p>Phiên đăng nhập hết hạn. Vui lòng đăng nhập để tiếp tục</p>
                        </form>
                    </div>
                </body>
                </html>
                """;

        CheckoutParsedResult result = parser.parse(loginHtml, "12345", "67890");
        assertThat(result.status()).isEqualTo(CheckoutParsedResult.Status.SESSION_EXPIRED);
        assertThat(result.isFatalSessionError()).isTrue();
    }

    @Test
    @DisplayName("4. InitData with explicit session/auth failure is classified as SESSION_EXPIRED")
    void testInitDataExplicitAuthFailure() {
        String authErrorHtml = """
                <html>
                <head>
                <script>
                window.__initData__ = {
                    "success": false,
                    "errorCode": {
                        "key": "USER_SESSION_EXPIRED",
                        "logMessage": "User auth token expired, login required"
                    }
                };
                </script>
                </head>
                <body></body>
                </html>
                """;

        CheckoutParsedResult result = parser.parse(authErrorHtml, "12345", "67890");
        assertThat(result.status()).isEqualTo(CheckoutParsedResult.Status.SESSION_EXPIRED);
        assertThat(result.isFatalSessionError()).isTrue();
    }

    @Test
    @DisplayName("5. SKU render success=false / LZD_BUY_RENDER_PC_000 is SKU-level failure, not session failure")
    void testSkuRenderFailureIsNotFatalSessionError() {
        String renderFailHtml = """
                <html>
                <head>
                <script>
                window.__initData__ = {
                    "success": false,
                    "errorCode": {
                        "key": "LZD_BUY_RENDER_PC_000",
                        "logMessage": "ErrorCode: <LZD_BUY_RENDER_PC_000> , ErrorCode message: <Invoke pc render failed.>",
                        "displayMessage": "Xin lỗi, đã có lỗi hệ thống xảy ra. Bạn vui lòng quay lại giỏ hàng và tiến hành thành toán."
                    },
                    "notSuccess": true
                };
                </script>
                </head>
                <body></body>
                </html>
                """;

        CheckoutParsedResult result = parser.parse(renderFailHtml, "13378541841", "116964266229");
        assertThat(result.status()).isEqualTo(CheckoutParsedResult.Status.INVALID_PREVIEW_RESPONSE);
        assertThat(result.isFatalSessionError()).isFalse(); // MUST NOT HALT BATCH
    }

    @Test
    @DisplayName("6. Hard CAPTCHA page is classified as CAPTCHA_REQUIRED (fatal)")
    void testCaptchaPageDetected() {
        String captchaHtml = """
                <html>
                <body>
                    <div id="punish-box">
                        <h1>Xác minh bảo mật</h1>
                        <p>Vui lòng nhập mã xác nhận (Captcha) bên dưới</p>
                    </div>
                </body>
                </html>
                """;

        CheckoutParsedResult result = parser.parse(captchaHtml, "12345", "67890");
        assertThat(result.status()).isEqualTo(CheckoutParsedResult.Status.CAPTCHA_REQUIRED);
        assertThat(result.isFatalSessionError()).isTrue();
    }

    @Test
    @DisplayName("7. Hard WAF blocked page is classified as WAF_BLOCKED (fatal)")
    void testWafBlockedPageDetected() {
        String wafHtml = """
                <html>
                <head><title>403 Forbidden</title></head>
                <body>
                    <h1>Access Denied</h1>
                    <p>WAF has blocked this request</p>
                </body>
                </html>
                """;

        CheckoutParsedResult result = parser.parse(wafHtml, "12345", "67890");
        assertThat(result.status()).isEqualTo(CheckoutParsedResult.Status.WAF_BLOCKED);
        assertThat(result.isFatalSessionError()).isTrue();
    }

    @Test
    @DisplayName("8. Requested SKU differs from returned SKU is classified as SKU_MISMATCH")
    void testSkuMismatch() {
        String sampleHtml = """
                <html>
                <head>
                <script>
                window.__initData__ = {
                    "success": true,
                    "module": {
                        "data": {
                            "item_abc123": {
                                "fields": {
                                    "itemId": "2498224535",
                                    "sku": {
                                        "skuId": "12224747754",
                                        "skuText": "Màu Xanh / 16GB"
                                    },
                                    "price": { "currentPrice": "759.700 ₫" },
                                    "discountPrice": { "price": "738.600 ₫" }
                                }
                            }
                        }
                    }
                };
                </script>
                </head>
                <body></body>
                </html>
                """;

        CheckoutParsedResult result = parser.parse(sampleHtml, "2498224535", "9999999999");
        assertThat(result.status()).isEqualTo(CheckoutParsedResult.Status.SKU_MISMATCH);
        assertThat(result.isFatalSessionError()).isFalse();
    }

    @Test
    @DisplayName("9. Balanced brace algorithm handles braces inside strings and escaped quotes")
    void testBalancedBraceAlgorithm() {
        String script = """
                var x = 1;
                window.__initData__ = {
                    "keyWithBrace": "hello {world} and \\"escaped quotes\\"",
                    "nested": {
                        "inner": { "val": 42 }
                    }
                };
                var y = 2;
                """;

        String json = parser.extractInitDataJson(script);
        assertThat(json).isNotNull();
        assertThat(json).contains("\"hello {world} and \\\"escaped quotes\\\"\"");
        assertThat(json).endsWith("}");
    }
}
