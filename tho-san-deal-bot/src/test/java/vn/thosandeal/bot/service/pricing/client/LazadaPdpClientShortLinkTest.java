package vn.thosandeal.bot.service.pricing.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import vn.thosandeal.bot.service.pricing.parser.LazadaPdpParser;
import vn.thosandeal.bot.service.pricing.parser.LazadaProductSnapshot;
import vn.thosandeal.bot.validator.LazadaUrlValidator;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LazadaPdpClientShortLinkTest {

    private LazadaPdpParser pdpParser;
    private LazadaUrlValidator urlValidator;

    @BeforeEach
    void setUp() {
        pdpParser = mock(LazadaPdpParser.class);
        urlValidator = new LazadaUrlValidator();
    }

    @Test
    void extractRedirectUrlIfBridgePage_extractsOriginLink() {
        LazadaPdpClient client = new LazadaPdpClient(WebClient.builder(), pdpParser, urlValidator, "test-agent");

        String bridgeHtml = """
                <!DOCTYPE HTML>
                <html>
                <head>
                    <link rel="origin" href="https://www.lazada.vn/products/aula-ban-phim-co-i13378541841-s116964266232.html?sbucket=y&amp;dsource=share"/>
                </head>
                </html>
                """;

        String extracted = client.extractRedirectUrlIfBridgePage(bridgeHtml);
        assertNotNull(extracted);
        assertEquals("https://www.lazada.vn/products/aula-ban-phim-co-i13378541841-s116964266232.html?sbucket=y&dsource=share", extracted);
        assertTrue(urlValidator.isValid(extracted));
    }

    @Test
    void extractRedirectUrlIfBridgePage_extractsMetaRefresh() {
        LazadaPdpClient client = new LazadaPdpClient(WebClient.builder(), pdpParser, urlValidator, "test-agent");

        String bridgeHtml = """
                <!DOCTYPE HTML>
                <html>
                <head>
                    <meta http-equiv="refresh" content="400;url=https://www.lazada.vn/products/test-product-i123.html"/>
                </head>
                </html>
                """;

        String extracted = client.extractRedirectUrlIfBridgePage(bridgeHtml);
        assertNotNull(extracted);
        assertEquals("https://www.lazada.vn/products/test-product-i123.html", extracted);
        assertTrue(urlValidator.isValid(extracted));
    }

    @Test
    void extractRedirectUrlIfBridgePage_extractsJsRedirect() {
        LazadaPdpClient client = new LazadaPdpClient(WebClient.builder(), pdpParser, urlValidator, "test-agent");

        String bridgeHtml = """
                <!DOCTYPE HTML>
                <html>
                <script>
                    window.location.href = "https://www.lazada.vn/products/test-product-i456.html";
                </script>
                </html>
                """;

        String extracted = client.extractRedirectUrlIfBridgePage(bridgeHtml);
        assertNotNull(extracted);
        assertEquals("https://www.lazada.vn/products/test-product-i456.html", extracted);
        assertTrue(urlValidator.isValid(extracted));
    }

    @Test
    void extractRedirectUrlIfBridgePage_returnsNullIfAlreadyPdp() {
        LazadaPdpClient client = new LazadaPdpClient(WebClient.builder(), pdpParser, urlValidator, "test-agent");

        String pdpHtml = """
                <!DOCTYPE HTML>
                <html>
                <script>
                    window.__moduleData__ = {"data": {}};
                </script>
                </html>
                """;

        String extracted = client.extractRedirectUrlIfBridgePage(pdpHtml);
        assertNull(extracted);
    }
}
