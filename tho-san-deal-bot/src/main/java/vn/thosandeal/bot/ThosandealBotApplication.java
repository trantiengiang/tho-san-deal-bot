package vn.thosandeal.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import vn.thosandeal.bot.config.MockPricingProperties;
import vn.thosandeal.bot.config.PricingProperties;
import vn.thosandeal.bot.config.TelegramProperties;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties({
        TelegramProperties.class,
        PricingProperties.class,
        MockPricingProperties.class
})
public class ThosandealBotApplication {

    public static void main(String[] args) {
        loadDotEnv();
        for (String arg : args) {
            if (arg != null && arg.contains("import-lazada-session")) {
                System.setProperty("spring.main.web-application-type", "none");
                System.setProperty("app.scheduling.enabled", "false");
                break;
            }
        }
        SpringApplication.run(ThosandealBotApplication.class, args);
    }

    private static void loadDotEnv() {
        java.nio.file.Path envPath = java.nio.file.Paths.get(".env");
        if (!java.nio.file.Files.exists(envPath)) {
            envPath = java.nio.file.Paths.get("../.env");
        }
        if (java.nio.file.Files.exists(envPath)) {
            try {
                java.util.List<String> lines = java.nio.file.Files.readAllLines(envPath);
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                        continue;
                    }
                    int idx = line.indexOf('=');
                    String key = line.substring(0, idx).trim();
                    String value = line.substring(idx + 1).trim();
                    if (System.getProperty(key) == null && System.getenv(key) == null) {
                        System.setProperty(key, value);
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }
}
