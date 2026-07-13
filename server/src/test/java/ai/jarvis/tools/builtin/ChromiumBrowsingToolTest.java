package ai.jarvis.tools.builtin;

import ai.ultimate.tools.builtin.ChromiumBrowsingTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ChromiumBrowsingTool Tests")
class ChromiumBrowsingToolTest {

    @Test
    @DisplayName("rejects non-http URLs")
    void shouldRejectUnsafeUrlSchemes() {
        ChromiumBrowsingTool tool = new ChromiumBrowsingTool(
                new FakeProcessExecutor("<html></html>", ""),
                () -> Optional.of("chrome"));

        String result = tool.browseWithChromium(
                "file:///etc/passwd",
                "dom",
                1,
                false);

        assertThat(result)
                .contains("Security Restriction")
                .contains("Local files, browser internals, data URLs, and script URLs are blocked");
    }

    @Test
    @DisplayName("rejects local network destinations")
    void shouldRejectLocalNetworkDestinations() {
        ChromiumBrowsingTool tool = new ChromiumBrowsingTool(
                new FakeProcessExecutor("<html></html>", ""),
                () -> Optional.of("chrome"));

        String result = tool.browseWithChromium(
                "http://127.0.0.1:8080",
                "dom",
                1,
                false);

        assertThat(result)
                .contains("Security Restriction")
                .contains("Localhost, private, link-local, multicast, and metadata IP destinations are blocked");
    }

    @Test
    @DisplayName("enforces per-session budget cap")
    void shouldEnforceSessionBudgetCap() {
        ChromiumBrowsingTool tool = new ChromiumBrowsingTool(
                new FakeProcessExecutor("<html></html>", ""),
                () -> Optional.of("chrome"));

        String first = tool.browseWithChromium(
                "https://example.com",
                "dom",
                150,
                false);
        String second = tool.browseWithChromium(
                "https://example.com",
                "dom",
                51,
                false);

        assertThat(first).contains("Chromium browsing completed");
        assertThat(second).contains("200 credit workflow cap");
    }

    @Test
    @DisplayName("builds a sandboxed headless Chromium command")
    void shouldBuildSandboxedHeadlessCommand() {
        FakeProcessExecutor executor =
                new FakeProcessExecutor("<html>ok</html>", "");
        ChromiumBrowsingTool tool = new ChromiumBrowsingTool(
                executor,
                () -> Optional.of("chrome"));

        String result = tool.browseWithChromium(
                "https://example.com",
                "audit",
                1,
                false);

        assertThat(result)
                .contains("Chromium browsing completed")
                .contains("<html>ok</html>")
                .contains("[screenshot] base64 png bytes=");
        assertThat(executor.command)
                .contains("--headless=new")
                .contains("--disable-extensions")
                .contains("--no-first-run")
                .contains("--dump-dom");
        assertThat(executor.command)
                .noneMatch("--no-sandbox"::equals);
        assertThat(executor.environment)
                .containsKeys("HOME", "TMPDIR");
    }

    @Test
    @DisplayName("truncates large process output")
    void shouldTruncateLargeOutput() {
        String largeDom = "x".repeat(60_000);
        ChromiumBrowsingTool tool = new ChromiumBrowsingTool(
                new FakeProcessExecutor(largeDom, ""),
                () -> Optional.of("chrome"));

        String result = tool.browseWithChromium(
                "https://example.com",
                "dom",
                1,
                false);

        assertThat(result)
                .contains("outputLimitChars=50000")
                .contains("truncated=true");
        assertThat(result.length()).isLessThan(51_000);
    }

    @Test
    @DisplayName("reports missing browser binary without throwing")
    void shouldReportMissingBrowserBinary() {
        ChromiumBrowsingTool tool = new ChromiumBrowsingTool(
                new FakeProcessExecutor("<html></html>", ""),
                Optional::empty);

        String result = tool.browseWithChromium(
                "https://example.com",
                "dom",
                1,
                false);

        assertThat(result)
                .contains("Environment Error")
                .contains("No Chrome or Chromium executable");
    }

    private static final class FakeProcessExecutor
            implements ChromiumBrowsingTool.ProcessExecutor {
        private final String stdout;
        private final String stderr;
        private List<String> command;
        private Map<String, String> environment;

        private FakeProcessExecutor(String stdout, String stderr) {
            this.stdout = stdout;
            this.stderr = stderr;
        }

        @Override
        public Process start(List<String> command, Map<String, String> environment)
                throws IOException {
            this.command = command;
            this.environment = environment;
            writeFakeScreenshot(command);
            return new FakeProcess(stdout, stderr);
        }

        private void writeFakeScreenshot(List<String> command) throws IOException {
            for (String value : command) {
                if (value.startsWith("--screenshot=")) {
                    Path screenshotPath = Path.of(value.substring("--screenshot=".length()));
                    Files.write(screenshotPath, "fake-png".getBytes(StandardCharsets.UTF_8));
                }
            }
        }
    }

    private static final class FakeProcess extends Process {
        private final InputStream stdout;
        private final InputStream stderr;

        private FakeProcess(String stdout, String stderr) {
            this.stdout = new ByteArrayInputStream(
                    stdout.getBytes(StandardCharsets.UTF_8));
            this.stderr = new ByteArrayInputStream(
                    stderr.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return stdout;
        }

        @Override
        public InputStream getErrorStream() {
            return stderr;
        }

        @Override
        public int waitFor() throws InterruptedException {
            return 0;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            return true;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
        }

        @Override
        public Process destroyForcibly() {
            return this;
        }

        @Override
        public boolean isAlive() {
            return false;
        }
    }
}
