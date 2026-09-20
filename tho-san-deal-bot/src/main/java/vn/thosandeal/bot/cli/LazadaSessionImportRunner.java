package vn.thosandeal.bot.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import vn.thosandeal.bot.entity.LazadaSession;
import vn.thosandeal.bot.service.pricing.session.LazadaSessionService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * One-shot CLI runner for importing Lazada authenticated sessions.
 * Usage:
 *   java -jar app.jar --import-lazada-session=secrets/lazada-session.json
 *
 * MandatoryFix #2:
 * - Runs only when --import-lazada-session argument is supplied
 * - Validates input path and rejects path traversal
 * - Never prints or logs cookie values, keys, or ciphertext
 * - Exits cleanly with code 0 on success, code 1 on failure
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LazadaSessionImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LazadaSessionImportRunner.class);
    public static final String OPTION_NAME = "import-lazada-session";

    private final LazadaSessionService sessionService;
    private final ApplicationContext applicationContext;

    public LazadaSessionImportRunner(
            LazadaSessionService sessionService,
            ApplicationContext applicationContext) {
        this.sessionService = sessionService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        String optionName = null;
        if (args.containsOption(OPTION_NAME)) {
            optionName = OPTION_NAME;
        } else if (args.containsOption("import-session")) {
            optionName = "import-session";
        } else {
            return;
        }

        List<String> values = args.getOptionValues(optionName);
        if (values == null || values.isEmpty() || values.get(0).isBlank()) {
            System.err.println("Error: Missing file path for --" + optionName);
            exitWithCode(1);
            return;
        }

        String pathStr = values.get(0).trim();
        Path path = validatePath(pathStr);
        if (path == null) {
            exitWithCode(1);
            return;
        }

        try {
            LazadaSession session = sessionService.importSessionFromFile(path);
            System.out.println("Lazada session imported successfully.");
            System.out.println("Session status: " + session.getStatus().name());
            exitWithCode(0);
        } catch (Exception e) {
            System.err.println("Error importing Lazada session: " + e.getMessage());
            exitWithCode(1);
        }
    }

    Path validatePath(String pathStr) {
        if (pathStr == null || pathStr.isBlank()) {
            System.err.println("Error: File path cannot be empty.");
            return null;
        }

        if (pathStr.contains("..")) {
            System.err.println("Error: Path traversal ('..') is not permitted.");
            return null;
        }

        Path path = Paths.get(pathStr).normalize();

        if (!Files.exists(path)) {
            System.err.println("Error: Session file does not exist: " + path);
            return null;
        }

        if (!Files.isRegularFile(path)) {
            System.err.println("Error: Specified path is not a file: " + path);
            return null;
        }

        return path;
    }

    private void exitWithCode(int code) {
        int exitCode = SpringApplication.exit(applicationContext, () -> code);
        System.exit(exitCode);
    }
}
