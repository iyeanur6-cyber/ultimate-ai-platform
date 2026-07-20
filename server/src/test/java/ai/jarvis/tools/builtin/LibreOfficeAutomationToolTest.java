package ai.ultimate.tools.builtin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LibreOfficeAutomationTool Tests")
class LibreOfficeAutomationToolTest {

    @TempDir
    Path tempDir;

    private String previousManagedRoot;
    private boolean managedRootCaptured;

    @AfterEach
    void restoreManagedRootProperty() {
        if (!managedRootCaptured) {
            return;
        }
        if (previousManagedRoot == null) {
            System.clearProperty(
                    LibreOfficeAutomationTool.MANAGED_ROOT_PROPERTY);
            return;
        }
        System.setProperty(
                LibreOfficeAutomationTool.MANAGED_ROOT_PROPERTY,
                previousManagedRoot);
    }

    @Test
    @DisplayName("rejects workflows over the hard compute budget")
    void shouldRejectOverBudgetWorkflow() {
        LibreOfficeAutomationTool tool =
                new LibreOfficeAutomationTool(
                        neverCalledRunner());

        String result = tool.processDocument(
                "",
                "input.txt",
                "hello",
                "text",
                "pdf",
                150.01);

        assertThat(result)
                .contains("$150 hard compute budget");
    }

    @Test
    @DisplayName("rejects UTF-8 payloads above 5MB")
    void shouldRejectOversizedPayload() {
        LibreOfficeAutomationTool tool =
                new LibreOfficeAutomationTool(
                        neverCalledRunner());

        String result = tool.processDocument(
                "",
                "input.txt",
                "x".repeat(
                        (int) LibreOfficeAutomationTool
                                .MAX_PAYLOAD_SIZE_BYTES + 1),
                "text",
                "pdf",
                1);

        assertThat(result)
                .contains("5MB UTF-8 boundary");
    }

    @Test
    @DisplayName("rejects workspaces outside managed root")
    void shouldRejectWorkspaceOutsideManagedRoot()
            throws Exception {
        LibreOfficeAutomationTool tool =
                new LibreOfficeAutomationTool(
                        neverCalledRunner());
        Path outside = Files.createTempDirectory(
                "ultimate-outside");

        String result = tool.processDocument(
                outside.toString(),
                "input.txt",
                "hello",
                "text",
                "pdf",
                1);

        assertThat(result)
                .contains("Operational boundaries exceeded");
    }

    @Test
    @DisplayName("rejects unsupported output formats")
    void shouldRejectUnsupportedOutputFormat() {
        LibreOfficeAutomationTool tool =
                new LibreOfficeAutomationTool(
                        neverCalledRunner());

        String result = tool.processDocument(
                "",
                "input.txt",
                "hello",
                "text",
                "exe",
                1);

        assertThat(result)
                .contains("Unsupported output format");
    }

    @Test
    @DisplayName("returns converted output and purges workspace")
    void shouldReturnConvertedOutputAndCleanup()
            throws Exception {
        LibreOfficeAutomationTool tool =
                new LibreOfficeAutomationTool(
                        new SuccessfulRunner("converted"));
        Path root = managedRoot();
        Path workspace = root.resolve("libreoffice-test");

        String result = tool.processDocument(
                workspace.toString(),
                "input.txt",
                "hello",
                "text",
                "pdf",
                1);

        assertThat(result)
                .contains("LibreOffice automation completed")
                .contains("Output format: pdf")
                .contains("Y29udmVydGVk");
        assertThat(workspace).doesNotExist();
    }

    @Test
    @DisplayName("resolves relative workspaces under the managed root")
    void shouldResolveRelativeWorkspaceUnderManagedRoot()
            throws Exception {
        LibreOfficeAutomationTool tool =
                new LibreOfficeAutomationTool(
                        new SuccessfulRunner("converted"));
        Path root = managedRoot();
        Path workspace = root.resolve("libreoffice-relative");

        String result = tool.processDocument(
                "libreoffice-relative",
                "input.txt",
                "hello",
                "text",
                "pdf",
                1);

        assertThat(result)
                .contains("LibreOffice automation completed");
        assertThat(workspace).doesNotExist();
    }

    @Test
    @DisplayName("decodes base64 payloads before conversion")
    void shouldDecodeBase64Payload()
            throws Exception {
        Path workspace = managedRoot()
                .resolve("libreoffice-base64");
        LibreOfficeAutomationTool tool =
                new LibreOfficeAutomationTool(
                        (command, timeout) -> {
                            Path inputFile = Path.of(
                                    command.get(command.size() - 1));
                            assertThat(Files.readString(inputFile))
                                    .isEqualTo("hello");
                            writeConvertedOutput(command, "converted");
                            return new LibreOfficeAutomationTool.CommandResult(
                                    0, "ok", "", false);
                        });

        String result = tool.processDocument(
                workspace.toString(),
                "input.txt",
                Base64.getEncoder()
                        .encodeToString("hello".getBytes(
                                StandardCharsets.UTF_8)),
                "base64",
                "pdf",
                1);

        assertThat(result)
                .contains("LibreOffice automation completed");
    }

    @Test
    @DisplayName("handles timeout and oversized output")
    void shouldHandleTimeoutAndOversizedOutput()
            throws Exception {
        Path root = managedRoot();
        LibreOfficeAutomationTool timedOutTool =
                new LibreOfficeAutomationTool(
                        (command, timeout) ->
                                new LibreOfficeAutomationTool.CommandResult(
                                        -1, "", "", true));

        assertThat(timedOutTool.processDocument(
                root.resolve("libreoffice-timeout").toString(),
                "input.txt",
                "hello",
                "text",
                "pdf",
                1)).contains("Watchdog Interdiction");

        LibreOfficeAutomationTool oversizedTool =
                new LibreOfficeAutomationTool(
                        (command, timeout) -> {
                            int outDirIndex = command.indexOf("--outdir");
                            Path outDir = Path.of(
                                    command.get(outDirIndex + 1));
                            Files.write(
                                    outDir.resolve("input.pdf"),
                                    new byte[(int) (1024L * 1024L + 1)]);
                            return new LibreOfficeAutomationTool.CommandResult(
                                    0, "ok", "", false);
                        });

        assertThat(oversizedTool.processDocument(
                root.resolve("libreoffice-large").toString(),
                "input.txt",
                "hello",
                "text",
                "pdf",
                1)).contains("exceeded the 1MB return boundary");
    }

    @Test
    @DisplayName("supports same-extension conversions in output directory")
    void shouldSupportSameExtensionOutput()
            throws Exception {
        Path workspace = managedRoot()
                .resolve("libreoffice-docx");
        LibreOfficeAutomationTool tool =
                new LibreOfficeAutomationTool(
                        new SuccessfulRunner("converted"));

        String result = tool.processDocument(
                workspace.toString(),
                "report.docx",
                "hello",
                "text",
                "docx",
                1);

        assertThat(result)
                .contains("Output format: docx")
                .contains("Y29udmVydGVk");
    }

    @Test
    @DisplayName("truncates process output on failures")
    void shouldTruncateProcessOutputOnFailures() {
        Path workspace = managedRoot()
                .resolve("libreoffice-failure");
        LibreOfficeAutomationTool tool =
                new LibreOfficeAutomationTool(
                        (command, timeout) ->
                                new LibreOfficeAutomationTool.CommandResult(
                                        2,
                                        "o".repeat(50_010),
                                        "e".repeat(50_010),
                                        false));

        String result = tool.processDocument(
                workspace.toString(),
                "input.txt",
                "hello",
                "text",
                "pdf",
                1);

        assertThat(result)
                .contains("exit code 2")
                .contains("truncated at 50000 characters");

        String stderrSection = result.substring(
                result.indexOf("[stderr]"),
                result.indexOf("[stdout]"));
        String stdoutSection = result.substring(
                result.indexOf("[stdout]"));
        assertThat(stderrSection)
                .contains("truncated at 50000 characters");
        assertThat(stdoutSection)
                .contains("truncated at 50000 characters");
    }

    @Test
    @DisplayName("rejects path traversal file names")
    void shouldRejectPathTraversalFileName() {
        LibreOfficeAutomationTool tool =
                new LibreOfficeAutomationTool(
                        neverCalledRunner());

        String result = tool.processDocument(
                "",
                "../input.txt",
                "hello",
                "text",
                "pdf",
                1);

        assertThat(result)
                .contains("plain file name");
    }

    private Path managedRoot() {
        if (!managedRootCaptured) {
            previousManagedRoot = System.getProperty(
                    LibreOfficeAutomationTool.MANAGED_ROOT_PROPERTY);
            managedRootCaptured = true;
        }
        Path root = tempDir.resolve("ultimate-managed-workspaces")
                .toAbsolutePath()
                .normalize();
        System.setProperty(
                LibreOfficeAutomationTool.MANAGED_ROOT_PROPERTY,
                root.toString());
        return root;
    }

    private static LibreOfficeAutomationTool.ProcessRunner neverCalledRunner() {
        return (command, timeout) -> {
            throw new AssertionError(
                    "Process runner should not be called");
        };
    }

    private static void writeConvertedOutput(
            List<String> command,
            String output) throws Exception {

        int outDirIndex = command.indexOf("--outdir");
        int formatIndex = command.indexOf("--convert-to");
        Path outDir = Path.of(command.get(outDirIndex + 1));
        Path inputFile = Path.of(
                command.get(command.size() - 1));
        String inputName = inputFile.getFileName().toString();
        String outputFormat = command.get(formatIndex + 1);
        int lastDot = inputName.lastIndexOf('.');
        String stem = lastDot > 0
                ? inputName.substring(0, lastDot)
                : inputName;
        Files.writeString(
                outDir.resolve(stem + "." + outputFormat),
                output,
                StandardCharsets.UTF_8);
    }

    private static class SuccessfulRunner
            implements LibreOfficeAutomationTool.ProcessRunner {

        private final String output;

        SuccessfulRunner(String output) {
            this.output = output;
        }

        @Override
        public LibreOfficeAutomationTool.CommandResult run(
                List<String> command,
                Duration timeout) throws Exception {

            assertThat(command)
                    .anyMatch(arg -> arg.startsWith(
                            "-env:UserInstallation=file:"));
            writeConvertedOutput(command, output);

            return new LibreOfficeAutomationTool.CommandResult(
                    0, "ok", "", false);
        }
    }
}
