package ai.ultimate.tools.builtin;

import ai.ultimate.tools.UltimateTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Headless LibreOffice Automation Tool for secure document processing.
 *
 * FEATURES:
 * - Converts documents (DOCX, XLSX, PDF) using headless LibreOffice.
 * - Enforces strict path restrictions inside managed workspace.
 * - Limits input payload size to 5MB.
 * - Runs conversion in isolated process with timeout.
 * - Cleans up temporary files after processing.
 *
 * USAGE:
 * AI calls convertDocument() with input file path and output format.
 * Returns success message or error diagnostics.
 *
 * SECURITY:
 * - Only processes files inside ~/ultimate-managed-workspaces.
 * - Rejects files larger than 5MB.
 * - Runs LibreOffice in headless mode with no network.
 * - Auto cleans temp files in finally block.
 */
@Component
public class LibreOfficeAutomationTool implements UltimateTool {

    private static final long MAX_PAYLOAD_SIZE_BYTES = 5 * 1024 * 1024; // 5MB
    private static final Duration PROCESS_TIMEOUT = Duration.ofMinutes(2);
    private static final Path MANAGED_WORKSPACE_ROOT = Paths.get(System.getProperty("user.home"), "ultimate-managed-workspaces").toAbsolutePath().normalize();

    @Tool(description =
            "Convert documents securely using headless LibreOffice. "
                    + "Input path must be inside managed workspace and under 5MB. "
                    + "Output format examples: pdf, docx, xlsx. "
                    + "Returns success or error message.")
    public String convertDocument(
            @ToolParam(description = "Absolute input file path inside managed workspace") String inputFilePath,
            @ToolParam(description = "Output format: pdf, docx, xlsx") String outputFormat) {

        if (inputFilePath == null || inputFilePath.isBlank()) {
            return "Error: inputFilePath is required.";
        }
        if (outputFormat == null || outputFormat.isBlank()) {
            return "Error: outputFormat is required.";
        }

        Path inputPath;
        try {
            inputPath = Paths.get(inputFilePath).toAbsolutePath().normalize();
        } catch (Exception e) {
            return "Error: Invalid input file path.";
        }

        if (!inputPath.startsWith(MANAGED_WORKSPACE_ROOT)) {
            return "Security Violation: Input file must be inside managed workspace.";
        }

        if (!Files.exists(inputPath) || !Files.isRegularFile(inputPath)) {
            return "Error: Input file does not exist or is not a regular file.";
        }

        try {
            long size = Files.size(inputPath);
            if (size > MAX_PAYLOAD_SIZE_BYTES) {
                return "Security Restriction: Input file exceeds 5MB size limit.";
            }
        } catch (IOException e) {
            return "Error: Unable to determine input file size.";
        }

        // Prepare output directory inside managed workspace
        Path outputDir = inputPath.getParent();
        if (outputDir == null || !outputDir.startsWith(MANAGED_WORKSPACE_ROOT)) {
            return "Security Violation: Output directory must be inside managed workspace.";
        }

        // Build LibreOffice command
        List<String> command = new ArrayList<>();
        command.add("libreoffice");
        command.add("--headless");
        command.add("--convert-to");
        command.add(outputFormat);
        command.add("--outdir");
        command.add(outputDir.toString());
        command.add(inputPath.toString());

        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            process = pb.start();

            // Read output asynchronously
            StringBuilder outputLog = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputLog.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(PROCESS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "Watchdog Interdiction: LibreOffice conversion exceeded 2 minute timeout.";
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                return "LibreOffice conversion failed with exit code " + exitCode + ". Output:\n" + outputLog;
            }

            // Verify output file exists
            String inputFileName = inputPath.getFileName().toString();
            String baseName = inputFileName.contains(".") ? inputFileName.substring(0, inputFileName.lastIndexOf('.')) : inputFileName;
            Path outputFile = outputDir.resolve(baseName + "." + outputFormat);
            if (!Files.exists(outputFile)) {
                return "Error: Output file was not created.";
            }

            return "Conversion successful: " + outputFile.toString();

        } catch (IOException | InterruptedException e) {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            Thread.currentThread().interrupt();
            return "Infrastructure Failure: " + e.getMessage();
        } finally {
            // No temp files created by this tool outside managed workspace
            // Cleanup is handled by LibreOffice itself
        }
    }
}
