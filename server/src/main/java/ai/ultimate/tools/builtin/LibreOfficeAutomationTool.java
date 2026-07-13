package ai.ultimate.tools.builtin;

import ai.ultimate.tools.UltimateTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@Component
public class LibreOfficeAutomationTool implements UltimateTool {

    static final long MAX_PAYLOAD_SIZE_BYTES =
            5L * 1024L * 1024L;
    static final double MAX_COMPUTE_CREDITS = 150.0;
    static final String MANAGED_ROOT_PROPERTY =
            "ultimate.managed-workspaces-root";
    private static final int MAX_PROCESS_LOG_CHARS = 50_000;
    private static final long MAX_RETURN_FILE_BYTES =
            1L * 1024L * 1024L;
    private static final Duration PROCESS_TIMEOUT =
            Duration.ofMinutes(10);
    private static final Set<String> ALLOWED_OUTPUT_FORMATS =
            Set.of("pdf", "docx", "xlsx", "pptx",
                    "odt", "ods", "odp", "txt", "html");

    private final ProcessRunner processRunner;

    public LibreOfficeAutomationTool() {
        this(new DefaultProcessRunner());
    }

    LibreOfficeAutomationTool(ProcessRunner processRunner) {
        this.processRunner = processRunner;
    }

    @Tool(description =
            "Run a guarded headless LibreOffice document conversion "
                    + "inside an Ultimate-managed workspace. Enforces "
                    + "a $150 compute cap, a 5MB UTF-8 payload cap, "
                    + "path containment, parallel stdout/stderr "
                    + "harvesting, and volatile workspace cleanup.")
    public String processDocument(
            @ToolParam(description =
                    "Workspace directory under ~/ultimate-managed-workspaces. "
                            + "Blank creates a fresh volatile session.")
            String workspacePath,
            @ToolParam(description =
                    "Input file name such as report.docx or source.txt. "
                            + "Path separators are not allowed.")
            String inputFileName,
            @ToolParam(description =
                    "Document payload. Use UTF-8 text by default, or "
                            + "base64 when payloadEncoding is 'base64'.")
            String documentPayload,
            @ToolParam(description =
                    "Payload encoding: 'text' or 'base64'.")
            String payloadEncoding,
            @ToolParam(description =
                    "Output format: pdf, docx, xlsx, pptx, odt, ods, "
                            + "odp, txt, or html.")
            String outputFormat,
            @ToolParam(description =
                    "Requested compute credits for this session. "
                            + "Must be less than or equal to 150.")
            double computeCredits) {

        if (computeCredits > MAX_COMPUTE_CREDITS) {
            return "Security Restriction: LibreOffice automation "
                    + "request rejected. The workflow exceeds the "
                    + "$150 hard compute budget.";
        }

        if (documentPayload == null) {
            return "Input Error: Document payload is required.";
        }

        if (exceedsUtf8Limit(documentPayload)) {
            return "Security Restriction: Document payload exceeds "
                    + "the 5MB UTF-8 boundary.";
        }

        String safeOutputFormat =
                normalizeOutputFormat(outputFormat);
        if (safeOutputFormat == null) {
            return "Input Error: Unsupported output format. "
                    + "Allowed formats: "
                    + String.join(", ", ALLOWED_OUTPUT_FORMATS);
        }

        String safeFileName = normalizeFileName(inputFileName);
        if (safeFileName == null) {
            return "Input Error: Input file name must be a plain "
                    + "file name without path separators.";
        }

        Path workspace = null;
        try {
            workspace = resolveManagedWorkspace(workspacePath);
            Files.createDirectories(workspace);
            workspace = requireRealContainedWorkspace(workspace);
        } catch (Exception e) {
            return "Security Access Violation: Operational "
                    + "boundaries exceeded. Document workspaces "
                    + "must strictly reside inside "
                    + "~/ultimate-managed-workspaces.";
        }

        try {
            Path inputDir = workspace.resolve("input").normalize();
            Path outputDir = workspace.resolve("output").normalize();
            if (!inputDir.startsWith(workspace)
                    || !outputDir.startsWith(workspace)) {
                return "Security Access Violation: Document "
                        + "workspace paths escaped the managed "
                        + "workspace.";
            }
            Files.createDirectories(inputDir);
            Files.createDirectories(outputDir);

            Path inputFile = inputDir.resolve(safeFileName)
                    .normalize();
            if (!inputFile.startsWith(workspace)) {
                return "Security Access Violation: Input file "
                        + "escaped the managed workspace.";
            }

            writePayload(inputFile, documentPayload, payloadEncoding);

            List<String> command = buildLibreOfficeCommand(
                    workspace, outputDir, inputFile, safeOutputFormat);
            CommandResult result =
                    processRunner.run(command, PROCESS_TIMEOUT);

            String stdout = truncate(result.stdout());
            String stderr = truncate(result.stderr());

            if (result.timedOut()) {
                return "Watchdog Interdiction: LibreOffice "
                        + "automation exceeded the 10 minute "
                        + "runtime limit.";
            }

            Path outputFile = resolveConvertedFile(
                    outputDir, inputFile, safeOutputFormat);

            if (result.exitCode() != 0) {
                return "LibreOffice automation failed with exit "
                        + "code " + result.exitCode()
                        + ".\n\n[stderr]\n" + stderr
                        + "\n[stdout]\n" + stdout;
            }

            if (!Files.exists(outputFile)) {
                return "LibreOffice automation completed, but "
                        + "the expected output file was not found."
                        + "\n\n[stdout]\n" + stdout
                        + "\n[stderr]\n" + stderr;
            }

            long outputBytes = Files.size(outputFile);
            if (outputBytes > MAX_RETURN_FILE_BYTES) {
                return "LibreOffice automation completed. Output "
                        + "file exceeded the 1MB return boundary "
                        + "and was purged from volatile storage.";
            }

            String encodedOutput = encodeFileToBase64(outputFile);
            return "LibreOffice automation completed.\n"
                    + "Output format: " + safeOutputFormat + "\n"
                    + "Output bytes: " + outputBytes + "\n"
                    + "Output base64: " + encodedOutput;

        } catch (IllegalArgumentException e) {
            return "Input Error: " + e.getMessage();
        } catch (Exception e) {
            log.warn("LibreOffice automation failed: {}",
                    e.getMessage());
            return "Infrastructure Process Lifecycle Failure: "
                    + e.getMessage();
        } finally {
            cleanWorkspaceSafely(workspace);
        }
    }

    private Path resolveManagedWorkspace(String workspacePath)
            throws IOException {
        Path managedRoot = managedWorkspaceRoot();

        Path candidate;
        if (workspacePath == null || workspacePath.isBlank()) {
            candidate = managedRoot.resolve(
                    "libreoffice-session-"
                            + UUID.randomUUID());
        } else {
            Path requested = Paths.get(workspacePath);
            candidate = requested.isAbsolute()
                    ? requested.toAbsolutePath().normalize()
                    : managedRoot.resolve(requested).normalize();
        }

        if (!candidate.startsWith(managedRoot)
                || candidate.equals(managedRoot)) {
            throw new IllegalArgumentException(
                    "Workspace outside managed root");
        }

        validateExistingWorkspaceAncestor(candidate, managedRoot);
        return candidate;
    }

    private void validateExistingWorkspaceAncestor(
            Path candidate,
            Path managedRoot) throws IOException {

        Files.createDirectories(managedRoot);
        Path realRoot = managedRoot.toRealPath();
        Path existing = candidate;
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }

        if (existing == null
                || !existing.toRealPath().startsWith(realRoot)) {
            throw new IllegalArgumentException(
                    "Workspace outside managed root");
        }
    }

    private Path requireRealContainedWorkspace(Path workspace)
            throws IOException {
        Path managedRoot = managedWorkspaceRoot();
        Files.createDirectories(managedRoot);
        Path realRoot = managedRoot.toRealPath();
        Path realWorkspace = workspace.toRealPath();
        if (!realWorkspace.startsWith(realRoot)
                || realWorkspace.equals(realRoot)) {
            throw new IllegalArgumentException(
                    "Workspace outside managed root");
        }
        return realWorkspace;
    }

    private Path managedWorkspaceRoot() {
        String override =
                System.getProperty(MANAGED_ROOT_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Paths.get(override)
                    .toAbsolutePath()
                    .normalize();
        }

        return Paths.get(
                        System.getProperty("user.home"),
                        "ultimate-managed-workspaces")
                .toAbsolutePath()
                .normalize();
    }

    private String normalizeFileName(String inputFileName) {
        if (inputFileName == null || inputFileName.isBlank()) {
            return null;
        }

        Path fileNamePath = Paths.get(inputFileName);
        if (fileNamePath.getNameCount() != 1) {
            return null;
        }

        String fileName = fileNamePath.getFileName().toString();
        if (fileName.equals(".") || fileName.equals("..")
                || fileName.contains("/") || fileName.contains("\\")) {
            return null;
        }

        return fileName;
    }

    private String normalizeOutputFormat(String outputFormat) {
        if (outputFormat == null || outputFormat.isBlank()) {
            return null;
        }

        String normalized =
                outputFormat.trim().toLowerCase(Locale.ROOT);
        return ALLOWED_OUTPUT_FORMATS.contains(normalized)
                ? normalized : null;
    }

    private void writePayload(
            Path inputFile,
            String documentPayload,
            String payloadEncoding) throws IOException {

        Files.createDirectories(inputFile.getParent());

        if (payloadEncoding != null
                && payloadEncoding.equalsIgnoreCase("base64")) {
            try {
                Files.write(inputFile,
                        Base64.getDecoder()
                                .decode(documentPayload));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Document payload is not valid base64.");
            }
            return;
        }

        Files.writeString(
                inputFile,
                documentPayload,
                StandardCharsets.UTF_8);
    }

    private List<String> buildLibreOfficeCommand(
            Path workspace,
            Path outputDir,
            Path inputFile,
            String outputFormat) {

        List<String> command = new ArrayList<>();
        command.add(resolveLibreOfficeBinary());
        command.add("--headless");
        command.add("--nologo");
        command.add("--nofirststartwizard");
        command.add("-env:UserInstallation="
                + workspace.resolve("lo-profile").toUri());
        command.add("--convert-to");
        command.add(outputFormat);
        command.add("--outdir");
        command.add(outputDir.toString());
        command.add(inputFile.toString());
        return command;
    }

    private String resolveLibreOfficeBinary() {
        String configured =
                System.getenv("LIBREOFFICE_BINARY");
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return "soffice";
    }

    private Path resolveConvertedFile(
            Path outputDir,
            Path inputFile,
            String outputFormat) {

        String fileName = inputFile.getFileName().toString();
        int lastDot = fileName.lastIndexOf('.');
        String stem = lastDot > 0
                ? fileName.substring(0, lastDot)
                : fileName;
        return outputDir.resolve(stem + "." + outputFormat)
                .normalize();
    }

    private boolean exceedsUtf8Limit(String value) {
        long bytes = 0;
        for (int index = 0; index < value.length(); ) {
            int codePoint = value.codePointAt(index);
            if (codePoint <= 0x7f) {
                bytes += 1;
            } else if (codePoint <= 0x7ff) {
                bytes += 2;
            } else if (codePoint <= 0xffff) {
                bytes += 3;
            } else {
                bytes += 4;
            }
            if (bytes > MAX_PAYLOAD_SIZE_BYTES) {
                return true;
            }
            index += Character.charCount(codePoint);
        }
        return false;
    }

    private String encodeFileToBase64(Path path) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (OutputStream encoded =
                     Base64.getEncoder().wrap(buffer);
             var input = Files.newInputStream(path)) {
            input.transferTo(encoded);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        if (value.length() <= MAX_PROCESS_LOG_CHARS) {
            return value;
        }
        return value.substring(0, MAX_PROCESS_LOG_CHARS)
                + "\n[truncated at " + MAX_PROCESS_LOG_CHARS
                + " characters]";
    }

    private void cleanWorkspaceSafely(Path workspace) {
        if (workspace == null || !Files.exists(workspace)) {
            return;
        }

        try {
            try (Stream<Path> paths = Files.walk(workspace)) {
                paths.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            } catch (IOException e) {
                                log.debug(
                                        "Unable to delete LibreOffice "
                                                + "workspace path {}",
                                        path,
                                        e);
                            }
                        });
            }
        } catch (IOException | UncheckedIOException ignored) {
            log.debug("Unable to fully clean LibreOffice workspace");
        }
    }

    interface ProcessRunner {
        CommandResult run(
                List<String> command,
                Duration timeout) throws Exception;
    }

    record CommandResult(
            int exitCode,
            String stdout,
            String stderr,
            boolean timedOut) {}

    private static class DefaultProcessRunner
            implements ProcessRunner {

        @Override
        public CommandResult run(
                List<String> command,
                Duration timeout) throws Exception {

            ProcessBuilder processBuilder =
                    new ProcessBuilder(command);
            restrictEnvironment(processBuilder.environment());
            Process process = processBuilder.start();

            StringBuilder stdout = new StringBuilder();
            StringBuilder stderr = new StringBuilder();

            Thread stdoutThread = new Thread(
                    new StreamCollector(
                            process.getInputStream(), stdout));
            Thread stderrThread = new Thread(
                    new StreamCollector(
                            process.getErrorStream(), stderr));

            stdoutThread.start();
            stderrThread.start();

            boolean completed = process.waitFor(
                    timeout.toMillis(),
                    java.util.concurrent.TimeUnit.MILLISECONDS);

            if (!completed) {
                destroyProcessTree(process);
                closeProcessStreams(process);
            }

            long joinMillis = Math.max(1_000L,
                    Math.min(timeout.toMillis(), 5_000L));
            stdoutThread.join(joinMillis);
            stderrThread.join(joinMillis);

            if (stdoutThread.isAlive()) {
                stdoutThread.interrupt();
            }
            if (stderrThread.isAlive()) {
                stderrThread.interrupt();
            }

            return new CommandResult(
                    completed ? process.exitValue() : -1,
                    stdout.toString(),
                    stderr.toString(),
                    !completed);
        }

        private void restrictEnvironment(
                Map<String, String> environment) {

            Map<String, String> inherited =
                    Map.copyOf(environment);
            environment.clear();
            for (String key : List.of(
                    "PATH", "Path", "HOME", "USERPROFILE",
                    "LOCALAPPDATA", "APPDATA", "PROGRAMFILES",
                    "ProgramFiles", "PROGRAMFILES(X86)",
                    "ProgramFiles(x86)", "SYSTEMROOT",
                    "SystemRoot", "WINDIR", "TEMP", "TMP",
                    "TMPDIR", "XDG_RUNTIME_DIR",
                    "LD_LIBRARY_PATH", "LANG")) {
                String value = inherited.get(key);
                if (value != null && !value.isBlank()) {
                    environment.put(key, value);
                }
            }
        }

        private void destroyProcessTree(Process process) {
            process.descendants()
                    .sorted(Comparator
                            .comparingLong(ProcessHandle::pid)
                            .reversed())
                    .forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }

        private void closeProcessStreams(Process process) {
            try {
                process.getInputStream().close();
            } catch (IOException ignored) {
                // Closing timeout streams is best effort.
            }
            try {
                process.getErrorStream().close();
            } catch (IOException ignored) {
                // Closing timeout streams is best effort.
            }
            try {
                process.getOutputStream().close();
            } catch (IOException ignored) {
                // Closing timeout streams is best effort.
            }
        }
    }

    private static class StreamCollector implements Runnable {

        private final java.io.InputStream stream;
        private final StringBuilder buffer;

        StreamCollector(
                java.io.InputStream stream,
                StringBuilder buffer) {
            this.stream = stream;
            this.buffer = buffer;
        }

        @Override
        public void run() {
            try (InputStreamReader reader =
                         new InputStreamReader(
                                 stream,
                                 StandardCharsets.UTF_8)) {
                char[] chunk = new char[4096];
                int charsRead;
                while ((charsRead = reader.read(chunk)) != -1) {
                    appendWithinLimit(chunk, charsRead);
                }
            } catch (IOException ignored) {
                // Process stream collection is best effort.
            }
        }

        private void appendWithinLimit(
                char[] chunk,
                int charsRead) {
            int remaining =
                    MAX_PROCESS_LOG_CHARS - buffer.length();
            if (remaining <= 0) {
                return;
            }

            buffer.append(
                    chunk,
                    0,
                    Math.min(charsRead, remaining));
        }
    }
}
