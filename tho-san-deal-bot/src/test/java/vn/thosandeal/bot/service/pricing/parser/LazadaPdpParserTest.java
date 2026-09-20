package vn.thosandeal.bot.service.pricing.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LazadaPdpParserTest {

    private LazadaPdpParser parser;

    @BeforeEach
    void setUp() {
        parser = new LazadaPdpParser(new ObjectMapper());
    }

    private String createSamplePdpHtml(String moduleDataJson) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Bàn Phím Cơ AULA F108 Pro | Lazada.vn</title>
                    <link rel="canonical" href="https://www.lazada.vn/products/aula-f108-i2498224535.html"/>
                </head>
                <body>
                    <script>
                    var __moduleData__ = %s;
                    </script>
                </body>
                </html>
                """.formatted(moduleDataJson);
    }

    @Test
    @DisplayName("Parse multi-SKU PDP HTML extracting itemId, productName, full dimensions, availability and stock")
    void testParseMultiSkuProduct() {
        String moduleDataJson = """
                {
                    "data": {
                        "root": {
                            "fields": {
                                "product": {
                                    "itemId": "2498224535",
                                    "title": "Bàn Phím Cơ Không Dây AULA F108 Pro"
                                },
                                "tracking": {
                                    "pdt_price": "759.700 ₫"
                                },
                                "productOption": {
                                    "skuBase": {
                                        "properties": [
                                            {
                                                "pid": "100",
                                                "values": [
                                                    { "vid": "1", "name": "Đen" },
                                                    { "vid": "2", "name": "Trắng" }
                                                ]
                                            },
                                            {
                                                "pid": "200",
                                                "values": [
                                                    { "vid": "10", "name": "Caramel Latte Switch" },
                                                    { "vid": "20", "name": "Linear Grey Switch" }
                                                ]
                                            }
                                        ],
                                        "skus": [
                                            { "skuId": "116964266230", "propPath": "100:1;200:10" },
                                            { "skuId": "116964266231", "propPath": "100:2;200:20" },
                                            { "skuId": "116964266232", "propPath": "100:1;200:20" }
                                        ]
                                    }
                                },
                                "skuInfos": {
                                    "116964266230": {
                                        "stock": 15,
                                        "operation": { "disable": false }
                                    },
                                    "116964266231": {
                                        "stock": 0,
                                        "operation": { "disable": false }
                                    },
                                    "116964266232": {
                                        "stock": 5,
                                        "operation": { "disable": true }
                                    }
                                }
                            }
                        }
                    }
                }
                """;

        String html = createSamplePdpHtml(moduleDataJson);
        LazadaProductSnapshot snapshot = parser.parse(html);

        assertThat(snapshot.itemId()).isEqualTo("2498224535");
        assertThat(snapshot.productName()).isEqualTo("Bàn Phím Cơ Không Dây AULA F108 Pro");
        assertThat(snapshot.canonicalUrl()).isEqualTo("https://www.lazada.vn/products/aula-f108-i2498224535.html");
        assertThat(snapshot.skus()).hasSize(3);

        // SKU 1: In stock, available, multi-dimension name
        LazadaSkuSnapshot sku1 = snapshot.skus().get(0);
        assertThat(sku1.skuId()).isEqualTo("116964266230");
        assertThat(sku1.variantName()).isEqualTo("Đen / Caramel Latte Switch");
        assertThat(sku1.available()).isTrue();
        assertThat(sku1.stock()).isEqualTo(15);
        assertThat(sku1.salePrice()).isEqualByComparingTo(new BigDecimal("759700"));

        // SKU 2: stock = 0 -> available = false
        LazadaSkuSnapshot sku2 = snapshot.skus().get(1);
        assertThat(sku2.skuId()).isEqualTo("116964266231");
        assertThat(sku2.variantName()).isEqualTo("Trắng / Linear Grey Switch");
        assertThat(sku2.available()).isFalse();
        assertThat(sku2.stock()).isEqualTo(0);

        // SKU 3: operation.disable = true -> available = false
        LazadaSkuSnapshot sku3 = snapshot.skus().get(2);
        assertThat(sku3.skuId()).isEqualTo("116964266232");
        assertThat(sku3.variantName()).isEqualTo("Đen / Linear Grey Switch");
        assertThat(sku3.available()).isFalse();
        assertThat(sku3.stock()).isEqualTo(5);
    }

    @Test
    @DisplayName("Empty or null PDP HTML throws IllegalArgumentException")
    void testEmptyHtmlThrows() {
        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Malformed PDP HTML missing bootstrap data falls back safely")
    void testMalformedHtmlFallback() {
        String html = "<html><head><title>Sản phẩm lỗi</title></head><body>No module data</body></html>";
        LazadaProductSnapshot snapshot = parser.parse(html);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.productName()).isEqualTo("Sản phẩm lỗi");
        assertThat(snapshot.skus()).isEmpty();
    }
}
