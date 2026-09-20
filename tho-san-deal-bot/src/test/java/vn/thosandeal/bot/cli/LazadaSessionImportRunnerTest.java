package vn.thosandeal.bot.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;
import vn.thosandeal.bot.entity.LazadaSession;
import vn.thosandeal.bot.enums.LazadaSessionStatus;
import vn.thosandeal.bot.service.pricing.session.LazadaSessionService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LazadaSessionImportRunnerTest {

    private LazadaSessionService sessionService;
    private ApplicationContext applicationContext;
    private LazadaSessionImportRunner runner;

    @BeforeEach
    void setUp() {
        sessionService = mock(LazadaSessionService.class);
        applicationContext = mock(ApplicationContext.class);
        runner = new LazadaSessionImportRunner(sessionService, applicationContext);
    }

    @Test
    @DisplayName("Runner ignores execution when --import-lazada-session option is absent")
    void testIgnoresWhenOptionAbsent() {
        ApplicationArguments args = new DefaultApplicationArguments();
        runner.run(args);
        verifyNoInteractions(sessionService);
    }

    @Test
    @DisplayName("Path traversal ('..') is strictly rejected")
    void testPathTraversalRejected() {
        Path result = runner.validatePath("../secrets/session.json");
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Non-existent file path is rejected")
    void testNonExistentFileRejected() {
        Path result = runner.validatePath("non_existent_folder/missing.json");
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Valid existing file path is accepted")
    void testValidFilePathAccepted(@TempDir Path tempDir) throws IOException {
        Path validFile = tempDir.resolve("test-session.json");
        Files.writeString(validFile, "{}");

        Path result = runner.validatePath(validFile.toString());
        assertThat(result).isNotNull();
        assertThat(result.toAbsolutePath()).isEqualTo(validFile.toAbsolutePath());
    }
}
