package ai.ultimate.tools.builtin;

import ai.ultimate.tools.UltimateTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ChromiumBrowsingTool implements UltimateTool {

    private static final int MAX_SESSION_CREDITS = 200;
    private static final int MAX_CAPTURED_OUTPUT_CHARS = 50_000;
    private static final Duration BROWSER_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_URL_LENGTH = 2_048;
    private static final int DEFAULT_WIDTH = 1280;
    private static final int DEFAULT_HEIGHT = 720;

    private final AtomicInteger sessionCreditsUsed = new AtomicInteger();
    private final ProcessExecutor processExecutor;
    private final BrowserBinaryLocator browserBinaryLocator;

    public ChromiumBrowsingTool() {
        this(new DefaultProcessExecutor(), ChromiumBrowsingTool::findChromiumBinary);
    }

    public ChromiumBrowsingTool(ProcessExecutor processExecutor, BrowserBinaryLocator browserBinaryLocator) {
        this.processExecutor = processExecutor;
        this.browserBinaryLocator = browserBinaryLocator;
    }

    @Tool(description =
            "Open a user-requested HTTP or HTTPS URL in an isolated Chrome/Chromium process. "
                    + "Use when the AI needs safe browser DOM inspection, viewport screenshot capture, "
                    + "or a lightweight URL audit. Parameters: url like 'https://example.com', "
                    + "captureMode one of 'dom', 'screenshot', or 'audit', processingCredits 1-200, "
                    + "headed true only when a visible browser window is explicitly needed. "
                    + "Returns bounded DOM/log/screenshot metadata and never throws to the model.")
    public String browseWithChromium(
            @ToolParam(description = "HTTP or HTTPS URL to inspect. Example: https://example.com") String url,
            @ToolParam(description = "Capture mode: dom, screenshot, or audit") String captureMode,
            @ToolParam(description = "Processing credits for this workflow call. Must keep session total at or below 200.") int processingCredits,
            @ToolParam(description = "Use false for secure headless mode. Use true only when a visible browser is required.") boolean headed) {

        String validationError = validateRequest(url, captureMode, processingCredits);
        if (validationError != null) {
            return validationError;
        }

        if (!reserveCredits(processingCredits)) {
            return "Budget Enforcement: Chromium browsing session refused because the 200 credit workflow cap would be exceeded.";
        }

        Optional<String> browserBinary = browserBinaryLocator.locate();
        if (browserBinary.isEmpty()) {
            return "Environment Error: No Chrome or Chromium executable was found on this host.";
        }

        Path runtimeDir = null;
        Process browserProcess = null;
        try {
            runtimeDir = Files.createTempDirectory("ultimate-chromium-" + UUID.randomUUID());
            Path profileDir = runtimeDir.resolve("profile");
            Path cacheDir = runtimeDir.resolve("cache");
            Path screenshotPath = runtimeDir.resolve("viewport.png");
            Files.createDirectories(profileDir);
            Files.createDirectories(cacheDir);

            List<String> command = buildCommand(
                    browserBinary.get(),
                    sanitizeCaptureMode(captureMode),
                    url.trim(),
                    headed,
                    profileDir,
                    cacheDir,
                    screenshotPath);

            Process runningProcess = processExecutor.start(command, Map.of(
                    "HOME", profileDir.toString(),
                    "TMPDIR", runtimeDir.toString()));
            browserProcess = runningProcess;

            BoundedOutput output = new BoundedOutput(MAX_CAPTURED_OUTPUT_CHARS);
            Thread stdoutReader = new Thread(
                    () -> copyStream(runningProcess.getInputStream(), output, "[stdout] "),
                    "chromium-stdout-reader");
            Thread stderrReader = new Thread(
                    () -> copyStream(runningProcess.getErrorStream(), output, "[stderr] "),
                    "chromium-stderr-reader");
            stdoutReader.start();
            stderrReader.start();

            boolean completed = runningProcess.waitFor(BROWSER_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!completed) {
                runningProcess.destroyForcibly();
                return "Watchdog Interdiction: Chromium browsing exceeded the 30 second runtime limit.";
            }

            stdoutReader.join(TimeUnit.SECONDS.toMillis(2));
            stderrReader.join(TimeUnit.SECONDS.toMillis(2));

            String screenshotSummary = summarizeScreenshot(screenshotPath, output.remainingCapacity());
            return formatResult(
                    runningProcess.exitValue(),
                    url,
                    sanitizeCaptureMode(captureMode),
                    output.value(),
                    output.truncated(),
                    screenshotSummary);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Infrastructure Process Lifecycle Failure: Chromium browsing was interrupted.";
        } catch (Exception e) {
            return "Infrastructure Process Lifecycle Failure: " + e.getMessage();
        } finally {
            if (browserProcess != null && browserProcess.isAlive()) {
                browserProcess.destroyForcibly();
            }
            cleanupRuntime(runtimeDir);
        }
    }

    void resetSessionBudgetForTests() {
        sessionCreditsUsed.set(0);
    }

    private String validateRequest(String url, String captureMode, int processingCredits) {
        if (url == null || url.isBlank()) {
            return "Validation Error: A URL is required.";
        }
        String trimmed = url.trim();
        if (trimmed.length() > MAX_URL_LENGTH) {
            return "Validation Error: URL exceeds the 2048 character safety limit.";
        }
        String lowerUrl = trimmed.toLowerCase(Locale.ROOT);
        if (!lowerUrl.startsWith("https://") && !lowerUrl.startsWith("http://")) {
            return "Security Restriction: Only http:// and https:// URLs are allowed.";
        }
        if (lowerUrl.startsWith("file:")
                || lowerUrl.startsWith("javascript:")
                || lowerUrl.startsWith("data:")
                || lowerUrl.startsWith("chrome:")
                || lowerUrl.startsWith("devtools:")) {
            return "Security Restriction: Local files, browser internals, data URLs, and script URLs are blocked.";
        }
        String mode = sanitizeCaptureMode(captureMode);
        if (!"dom".equals(mode) && !"screenshot".equals(mode) && !"audit".equals(mode)) {
            return "Validation Error: captureMode must be one of: dom, screenshot, audit.";
        }
        if (processingCredits < 1 || processingCredits > MAX_SESSION_CREDITS) {
            return "Budget Enforcement: processingCredits must be between 1 and 200.";
        }
        return null;
    }

    private boolean reserveCredits(int processingCredits) {
        while (true) {
            int used = sessionCreditsUsed.get();
            int next = used + processingCredits;
            if (next > MAX_SESSION_CREDITS) {
                return false;
            }
            if (sessionCreditsUsed.compareAndSet(used, next)) {
                return true;
            }
        }
    }

    private List<String> buildCommand(
            String browserBinary,
            String captureMode,
            String url,
            boolean headed,
            Path profileDir,
            Path cacheDir,
            Path screenshotPath) {

        List<String> command = new ArrayList<>();
        command.add(browserBinary);
        if (!headed) {
            command.add("--headless=new");
        }
        command.add("--disable-background-networking");
        command.add("--disable-component-update");
        command.add("--disable-default-apps");
        command.add("--disable-dev-shm-usage");
        command.add("--disable-extensions");
        command.add("--disable-gpu");
        command.add("--disable-sync");
        command.add("--disable-translate");
        command.add("--metrics-recording-only");
        command.add("--no-default-browser-check");
        command.add("--no-first-run");
        command.add("--password-store=basic");
        command.add("--use-mock-keychain");
        command.add("--user-data-dir=" + profileDir);
        command.add("--disk-cache-dir=" + cacheDir);
        command.add("--window-size=" + DEFAULT_WIDTH + "," + DEFAULT_HEIGHT);

        if ("dom".equals(captureMode) || "audit".equals(captureMode)) {
            command.add("--dump-dom");
        }
        if ("screenshot".equals(captureMode) || "audit".equals(captureMode)) {
            command.add("--screenshot=" + screenshotPath);
        }
        command.add(url);
        return command;
    }

    private String sanitizeCaptureMode(String captureMode) {
        if (captureMode == null || captureMode.isBlank()) {
            return "audit";
        }
        return captureMode.trim().toLowerCase(Locale.ROOT);
    }

    private void copyStream(InputStream stream, BoundedOutput output, String prefix) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(prefix).append(line).append("\n");
            }
        } catch (IOException e) {
            output.append("[stream-error] ").append(e.getMessage()).append("\n");
        }
    }

    private String summarizeScreenshot(Path screenshotPath, int remainingChars) throws IOException {
        if (!Files.exists(screenshotPath) || remainingChars <= 0) {
            return "";
        }
        byte[] bytes = Files.readAllBytes(screenshotPath);
        String encoded = Base64.getEncoder().encodeToString(bytes);
        String header = "\n[screenshot] base64 png bytes=" + bytes.length + "\n";
        int available = Math.max(0, remainingChars - header.length());
        if (encoded.length() > available) {
            return header + encoded.substring(0, available) + "\n[screenshot-truncated]\n";
        }
        return header + encoded + "\n";
    }

    private String formatResult(
            int exitCode,
            String url,
            String captureMode,
            String capturedOutput,
            boolean truncated,
            String screenshotSummary) {

        return "Chromium browsing completed."
                + "\nurl=" + url
                + "\nmode=" + captureMode
                + "\nexitCode=" + exitCode
                + "\noutputLimitChars=" + MAX_CAPTURED_OUTPUT_CHARS
                + "\ntruncated=" + truncated
                + "\n\n[captured-output]\n"
                + capturedOutput
                + screenshotSummary;
    }

    private static Optional<String> findChromiumBinary() {
        List<String> candidates = List.of(
                "chromium-browser",
                "chromium",
                "google-chrome",
                "google-chrome-stable",
                "chrome",
                "msedge",
                "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
                "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
                "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe");

        for (String candidate : candidates) {
            if (candidate.contains("\\") && Files.isExecutable(Path.of(candidate))) {
                return Optional.of(candidate);
            }
            if (!candidate.contains("\\")) {
                Optional<String> found = findOnPath(candidate);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<String> findOnPath(String binary) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String[] extensions = os.contains("win")
                ? new String[]{"", ".exe", ".cmd", ".bat"}
                : new String[]{""};
        for (String dir : path.split(java.io.File.pathSeparator)) {
            for (String extension : extensions) {
                Path candidate = Path.of(dir, binary + extension);
                if (Files.isExecutable(candidate)) {
                    return Optional.of(candidate.toString());
                }
            }
        }
        return Optional.empty();
    }

    private void cleanupRuntime(Path runtimeDir) {
        if (runtimeDir == null || !Files.exists(runtimeDir)) {
            return;
        }
        try (var stream = Files.walk(runtimeDir)) {
            stream.sorted((left, right) -> right.compareTo(left))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // Best-effort cleanup in finally.
                        }
                    });
        } catch (IOException ignored) {
            // Best-effort cleanup in finally.
        }
    }

    public interface ProcessExecutor {
        Process start(List<String> command, Map<String, String> environment) throws IOException;
    }

    public interface BrowserBinaryLocator {
        Optional<String> locate();
    }

    private static final class DefaultProcessExecutor implements ProcessExecutor {
        @Override
        public Process start(List<String> command, Map<String, String> environment) throws IOException {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.environment().putAll(environment);
            return processBuilder.start();
        }
    }

    private static final class BoundedOutput {
        private final int maxChars;
        private final StringBuilder value = new StringBuilder();
        private boolean truncated;

        private BoundedOutput(int maxChars) {
            this.maxChars = maxChars;
        }

        synchronized BoundedOutput append(String text) {
            if (text == null || text.isEmpty() || remainingCapacity() <= 0) {
                truncated = truncated || (text != null && !text.isEmpty());
                return this;
            }
            int available = remainingCapacity();
            if (text.length() > available) {
                value.append(text, 0, available);
                truncated = true;
            } else {
                value.append(text);
            }
            return this;
        }

        synchronized int remainingCapacity() {
            return Math.max(0, maxChars - value.length());
        }

        synchronized String value() {
            return value.toString();
        }

        synchronized boolean truncated() {
            return truncated;
        }
    }

}
