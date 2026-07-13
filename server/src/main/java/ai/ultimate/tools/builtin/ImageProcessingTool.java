/* ABOUTME: Safe, bounded ImageMagick batch processing for managed workspaces. */
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
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class ImageProcessingTool implements UltimateTool {

    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_CAPTURED_OUTPUT_CHARS = 20_000;
    private static final int MAX_INPUT_FILES = 20;
    private static final long MAX_INPUT_BYTES = 50L * 1024L * 1024L;
    private static final int MAX_DIMENSION = 8_192;
    private static final int MAX_WATERMARK_CHARS = 200;
    private static final Set<String> OPERATIONS = Set.of(
            "convert",
            "resize",
            "crop",
            "color-correct",
            "watermark",
            "strip-metadata");
    private static final Set<String> OUTPUT_FORMATS = Set.of(
            "png",
            "jpg",
            "jpeg",
            "webp",
            "gif",
            "tiff");

    private final Path managedWorkspaceRoot;
    private final ProcessExecutor processExecutor;
    private final ImageMagickLocator imageMagickLocator;

    public ImageProcessingTool() {
        this(
                Path.of(System.getProperty("user.home"),
                        "ultimate-managed-workspaces"),
                new DefaultProcessExecutor(),
                ImageProcessingTool::findImageMagickBinary);
    }

    public ImageProcessingTool(
            Path managedWorkspaceRoot,
            ProcessExecutor processExecutor,
            ImageMagickLocator imageMagickLocator) {
        this.managedWorkspaceRoot = managedWorkspaceRoot
                .toAbsolutePath()
                .normalize();
        this.processExecutor = processExecutor;
        this.imageMagickLocator = imageMagickLocator;
    }

    @Tool(description =
            "Process one or more local images with ImageMagick inside a managed workspace. "
                    + "Use for conversion, resizing, cropping, automatic sRGB color correction, "
                    + "watermarking, or EXIF/metadata stripping. inputPaths is a comma-separated "
                    + "list of relative files; outputDirectory is an existing relative directory; "
                    + "operation is convert, resize, crop, color-correct, watermark, or "
                    + "strip-metadata; outputFormat is png, jpg, jpeg, webp, gif, or tiff. "
                    + "Returns generated relative paths or a validation/runtime error and never "
                    + "throws to the model.")
    public String processImages(
            @ToolParam(description =
                    "Absolute workspace below ~/ultimate-managed-workspaces")
            String workspacePath,
            @ToolParam(description =
                    "Comma-separated relative input files, for example assets/a.png,assets/b.jpg")
            String inputPaths,
            @ToolParam(description =
                    "Existing relative output directory, for example generated")
            String outputDirectory,
            @ToolParam(description =
                    "convert, resize, crop, color-correct, watermark, or strip-metadata")
            String operation,
            @ToolParam(description =
                    "Output format: png, jpg, jpeg, webp, gif, or tiff")
            String outputFormat,
            @ToolParam(description =
                    "Target width from 1 to 8192 for resize/crop; use 0 otherwise")
            int width,
            @ToolParam(description =
                    "Target height from 1 to 8192 for resize/crop; use 0 otherwise")
            int height,
            @ToolParam(description =
                    "Watermark text up to 200 characters; use an empty string otherwise")
            String watermarkText,
            @ToolParam(description =
                    "Output quality from 1 to 100")
            int quality) {

        Request request;
        try {
            request = validateRequest(
                    workspacePath,
                    inputPaths,
                    outputDirectory,
                    operation,
                    outputFormat,
                    width,
                    height,
                    watermarkText,
                    quality);
        } catch (IllegalArgumentException e) {
            return "Validation Error: " + e.getMessage();
        }

        Optional<String> imageMagickBinary = imageMagickLocator.locate();
        if (imageMagickBinary.isEmpty()) {
            return "Environment Error: ImageMagick executable 'magick' or 'convert' "
                    + "was not found on this host.";
        }

        Path runtimeDirectory = null;
        List<Path> publishedOutputs = new ArrayList<>();
        boolean batchCompleted = false;
        try {
            Files.createDirectories(managedWorkspaceRoot);
            Path allowedRoot = managedWorkspaceRoot.toRealPath();
            Path workspace = Path.of(request.workspacePath()).toRealPath();
            if (!workspace.startsWith(allowedRoot)) {
                return "Path Restriction: Workspace must be inside the managed workspace root.";
            }

            Path outputCandidate = workspace.resolve(request.outputDirectory()).normalize();
            if (!outputCandidate.startsWith(workspace)
                    || Files.isSymbolicLink(outputCandidate)) {
                return "Path Restriction: Output directory escapes the managed workspace.";
            }
            Path outputRoot = outputCandidate.toRealPath();
            if (!outputRoot.startsWith(workspace) || !Files.isDirectory(outputRoot)) {
                return "Path Restriction: Output directory must be an existing directory "
                        + "inside the managed workspace.";
            }

            List<Path> inputs = resolveInputs(workspace, request.inputPaths());
            runtimeDirectory = Files.createTempDirectory(
                    workspace,
                    ".ultimate-image-");

            List<Path> stagedOutputs = new ArrayList<>();
            List<Path> finalOutputs = new ArrayList<>();
            List<String> relativeOutputs = new ArrayList<>();

            for (int index = 0; index < inputs.size(); index++) {
                Path input = inputs.get(index);
                String inputSuffix = suffixOf(input.getFileName().toString());
                Path stagedInput = runtimeDirectory.resolve(
                        String.format(Locale.ROOT, "input-%02d%s", index + 1, inputSuffix));
                Files.copy(input, stagedInput);
                Path canonicalStagedInput = stagedInput.toRealPath();

                String outputName = outputName(
                        input,
                        request.operation(),
                        request.outputFormat(),
                        index + 1);
                Path finalOutput = outputRoot.resolve(outputName).normalize();
                if (!finalOutput.startsWith(outputRoot) || Files.exists(finalOutput)) {
                    return "Path Restriction: Refusing to overwrite existing output "
                            + outputName + ".";
                }

                Path stagedOutput = runtimeDirectory.resolve(
                        String.format(
                                Locale.ROOT,
                                "output-%02d.%s",
                                index + 1,
                                request.outputFormat()));
                Files.createFile(stagedOutput);
                Path canonicalStagedOutput = stagedOutput.toRealPath();

                List<String> command = buildCommand(
                        imageMagickBinary.get(),
                        canonicalStagedInput,
                        canonicalStagedOutput,
                        request);
                ExecutionResult execution = execute(command, runtimeDirectory);
                if (execution.timedOut()) {
                    return "Watchdog Interdiction: Image processing exceeded the 30 second "
                            + "runtime limit.";
                }
                if (execution.exitCode() != 0) {
                    return "Image Processing Failure: ImageMagick exited with code "
                            + execution.exitCode()
                            + ".\n"
                            + execution.output();
                }
                if (Files.size(canonicalStagedOutput) == 0) {
                    return "Image Processing Failure: ImageMagick produced an empty output.";
                }

                stagedOutputs.add(canonicalStagedOutput);
                finalOutputs.add(finalOutput);
                relativeOutputs.add(workspace.relativize(finalOutput).toString());
            }

            for (int index = 0; index < stagedOutputs.size(); index++) {
                moveWithoutOverwrite(stagedOutputs.get(index), finalOutputs.get(index));
                publishedOutputs.add(finalOutputs.get(index));
            }
            batchCompleted = true;

            return "Image processing completed: "
                    + relativeOutputs.size()
                    + " file(s). Outputs: "
                    + String.join(", ", relativeOutputs);
        } catch (IOException e) {
            return "Image Processing Failure: " + safeMessage(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Image Processing Failure: Processing was interrupted.";
        } finally {
            cleanupRuntime(runtimeDirectory);
            if (!batchCompleted) {
                cleanupPublishedOutputs(publishedOutputs);
            }
        }
    }

    private Request validateRequest(
            String workspacePath,
            String inputPaths,
            String outputDirectory,
            String operation,
            String outputFormat,
            int width,
            int height,
            String watermarkText,
            int quality) {
        if (workspacePath == null || workspacePath.isBlank()) {
            throw new IllegalArgumentException("workspacePath is required.");
        }
        if (inputPaths == null || inputPaths.isBlank()) {
            throw new IllegalArgumentException("At least one input image is required.");
        }
        if (outputDirectory == null || outputDirectory.isBlank()) {
            throw new IllegalArgumentException("outputDirectory is required.");
        }
        Path outputPath = Path.of(outputDirectory.trim());
        if (outputPath.isAbsolute()) {
            throw new IllegalArgumentException("outputDirectory must be relative.");
        }

        String normalizedOperation = normalize(operation);
        if (!OPERATIONS.contains(normalizedOperation)) {
            throw new IllegalArgumentException(
                    "operation must be convert, resize, crop, color-correct, watermark, "
                            + "or strip-metadata.");
        }
        String normalizedFormat = normalize(outputFormat);
        if (!OUTPUT_FORMATS.contains(normalizedFormat)) {
            throw new IllegalArgumentException(
                    "outputFormat must be png, jpg, jpeg, webp, gif, or tiff.");
        }
        if (("resize".equals(normalizedOperation) || "crop".equals(normalizedOperation))
                && (!validDimension(width) || !validDimension(height))) {
            throw new IllegalArgumentException(
                    "width and height must be between 1 and 8192 for resize/crop.");
        }
        String normalizedWatermark = watermarkText == null ? "" : watermarkText.trim();
        if ("watermark".equals(normalizedOperation)) {
            if (normalizedWatermark.isBlank()) {
                throw new IllegalArgumentException("watermarkText is required for watermark.");
            }
            if (normalizedWatermark.length() > MAX_WATERMARK_CHARS
                    || normalizedWatermark.codePoints().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException(
                        "watermarkText must contain at most 200 printable characters.");
            }
            if (normalizedWatermark.startsWith("@")) {
                throw new IllegalArgumentException(
                        "watermarkText must not start with '@'.");
            }
        }
        if (quality < 1 || quality > 100) {
            throw new IllegalArgumentException("quality must be between 1 and 100.");
        }

        List<String> normalizedInputs = new ArrayList<>();
        Set<String> uniqueInputs = new HashSet<>();
        for (String rawInput : inputPaths.split(",", -1)) {
            String input = rawInput.trim();
            if (input.isBlank()) {
                throw new IllegalArgumentException("inputPaths contains an empty entry.");
            }
            Path inputPath = Path.of(input);
            if (inputPath.isAbsolute()) {
                throw new IllegalArgumentException("Input paths must be relative.");
            }
            if (!uniqueInputs.add(input)) {
                throw new IllegalArgumentException("Duplicate input path: " + input + ".");
            }
            normalizedInputs.add(input);
        }
        if (normalizedInputs.size() > MAX_INPUT_FILES) {
            throw new IllegalArgumentException("A batch may contain at most 20 images.");
        }

        return new Request(
                workspacePath.trim(),
                List.copyOf(normalizedInputs),
                outputDirectory.trim(),
                normalizedOperation,
                normalizedFormat,
                width,
                height,
                normalizedWatermark,
                quality);
    }

    private List<Path> resolveInputs(Path workspace, List<String> inputPaths)
            throws IOException {
        List<Path> inputs = new ArrayList<>();
        for (String inputPath : inputPaths) {
            Path candidate = workspace.resolve(inputPath).normalize();
            if (!candidate.startsWith(workspace) || Files.isSymbolicLink(candidate)) {
                throw new IOException("Path Restriction: Input escapes the managed workspace.");
            }
            Path canonicalInput = candidate.toRealPath();
            if (!canonicalInput.startsWith(workspace)
                    || !Files.isRegularFile(canonicalInput, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException(
                        "Path Restriction: Input must be a regular workspace file.");
            }
            if (Files.size(canonicalInput) > MAX_INPUT_BYTES) {
                throw new IOException("Validation Error: Input exceeds the 50 MiB limit.");
            }
            inputs.add(canonicalInput);
        }
        return inputs;
    }

    private List<String> buildCommand(
            String binary,
            Path input,
            Path output,
        Request request) {
        List<String> command = new ArrayList<>();
        command.add(binary);
        command.addAll(List.of(
                "-limit", "memory", "256MiB",
                "-limit", "map", "512MiB",
                "-limit", "disk", "1GiB"));
        command.add(input.toString());

        switch (request.operation()) {
            case "convert" -> {
                // Output extension and quality perform the conversion/compression.
            }
            case "resize" -> {
                command.add("-resize");
                command.add(request.width() + "x" + request.height());
            }
            case "crop" -> {
                command.add("-gravity");
                command.add("center");
                command.add("-crop");
                command.add(request.width() + "x" + request.height() + "+0+0");
                command.add("+repage");
            }
            case "color-correct" -> {
                command.add("-colorspace");
                command.add("sRGB");
                command.add("-auto-level");
            }
            case "watermark" -> {
                command.add("-gravity");
                command.add("southeast");
                command.add("-fill");
                command.add("rgba(255,255,255,0.65)");
                command.add("-stroke");
                command.add("rgba(0,0,0,0.35)");
                command.add("-strokewidth");
                command.add("1");
                command.add("-pointsize");
                command.add("24");
                command.add("-annotate");
                command.add("+12+12");
                command.add(request.watermarkText());
            }
            case "strip-metadata" -> command.add("-strip");
            default -> throw new IllegalStateException(
                    "Unsupported validated operation: " + request.operation());
        }

        command.add("-quality");
        command.add(Integer.toString(request.quality()));
        command.add(output.toString());
        return command;
    }

    private ExecutionResult execute(List<String> command, Path runtimeDirectory)
            throws IOException, InterruptedException {
        Process process = processExecutor.start(command, Map.of(
                "HOME", runtimeDirectory.toString(),
                "TMPDIR", runtimeDirectory.toString(),
                "MAGICK_TEMPORARY_PATH", runtimeDirectory.toString()));
        BoundedOutput output = new BoundedOutput(MAX_CAPTURED_OUTPUT_CHARS);
        Thread stdoutReader = new Thread(
                () -> drain(process.getInputStream(), output, "[stdout] "),
                "imagemagick-stdout-reader");
        Thread stderrReader = new Thread(
                () -> drain(process.getErrorStream(), output, "[stderr] "),
                "imagemagick-stderr-reader");
        stdoutReader.start();
        stderrReader.start();

        boolean completed;
        try {
            completed = process.waitFor(
                    PROCESS_TIMEOUT.toSeconds(),
                    TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
            }
            stdoutReader.join(TimeUnit.SECONDS.toMillis(2));
            stderrReader.join(TimeUnit.SECONDS.toMillis(2));
            return new ExecutionResult(
                    completed ? process.exitValue() : -1,
                    output.value(),
                    !completed);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private void drain(InputStream stream, BoundedOutput output, String prefix) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(prefix).append(line).append("\n");
            }
        } catch (IOException e) {
            output.append("[stream-error] ").append(safeMessage(e)).append("\n");
        }
    }

    private void moveWithoutOverwrite(Path source, Path destination) throws IOException {
        Files.move(source, destination);
    }

    private String outputName(
            Path input,
            String operation,
            String outputFormat,
            int index) {
        String filename = input.getFileName().toString();
        int extensionIndex = filename.lastIndexOf('.');
        String stem = extensionIndex > 0 ? filename.substring(0, extensionIndex) : filename;
        String safeStem = stem.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safeStem.isBlank()) {
            safeStem = "image";
        }
        return String.format(
                Locale.ROOT,
                "%02d-%s-%s.%s",
                index,
                safeStem,
                operation,
                outputFormat);
    }

    private String suffixOf(String filename) {
        int extensionIndex = filename.lastIndexOf('.');
        if (extensionIndex < 0 || extensionIndex == filename.length() - 1) {
            return ".img";
        }
        String suffix = filename.substring(extensionIndex)
                .replaceAll("[^A-Za-z0-9.]", "");
        return suffix.isBlank() ? ".img" : suffix;
    }

    private boolean validDimension(int value) {
        return value >= 1 && value <= MAX_DIMENSION;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String safeMessage(Exception exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    private void cleanupRuntime(Path runtimeDirectory) {
        if (runtimeDirectory == null || !Files.exists(runtimeDirectory)) {
            return;
        }
        try (var paths = Files.walk(runtimeDirectory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort cleanup must not hide the processing result.
                }
            });
        } catch (IOException ignored) {
            // Best-effort cleanup must not hide the processing result.
        }
    }

    private void cleanupPublishedOutputs(List<Path> publishedOutputs) {
        for (Path output : publishedOutputs.reversed()) {
            try {
                Files.deleteIfExists(output);
            } catch (IOException ignored) {
                // Best-effort rollback must not hide the original batch failure.
            }
        }
    }

    private static Optional<String> findImageMagickBinary() {
        String command = System.getProperty("os.name")
                .toLowerCase(Locale.ROOT)
                .contains("win") ? "where" : "which";
        return locateImageMagickBinary(
                command,
                List.of("magick", "convert"),
                lookupCommand -> new ProcessBuilder(lookupCommand).start());
    }

    static Optional<String> locateImageMagickBinary(
            String command,
            List<String> candidates,
            LookupProcessStarter processStarter) {
        for (String candidate : candidates) {
            Process process = null;
            try {
                process = processStarter.start(List.of(command, candidate));
                if (process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0) {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(
                                    process.getInputStream(),
                                    StandardCharsets.UTF_8))) {
                        String path = reader.readLine();
                        if (path != null && !path.isBlank()) {
                            return Optional.of(path.trim());
                        }
                    }
                }
            } catch (IOException e) {
                return Optional.empty();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            } finally {
                if (process != null && process.isAlive()) {
                    process.destroy();
                    if (process.isAlive()) {
                        process.destroyForcibly();
                    }
                }
            }
        }
        return Optional.empty();
    }

    public interface ProcessExecutor {
        Process start(List<String> command, Map<String, String> environment)
                throws IOException;
    }

    public interface ImageMagickLocator {
        Optional<String> locate();
    }

    @FunctionalInterface
    interface LookupProcessStarter {
        Process start(List<String> command) throws IOException;
    }

    private record Request(
            String workspacePath,
            List<String> inputPaths,
            String outputDirectory,
            String operation,
            String outputFormat,
            int width,
            int height,
            String watermarkText,
            int quality) {
    }

    private record ExecutionResult(int exitCode, String output, boolean timedOut) {
    }

    private static final class DefaultProcessExecutor implements ProcessExecutor {
        @Override
        public Process start(List<String> command, Map<String, String> environment)
                throws IOException {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.environment().putAll(environment);
            return builder.start();
        }
    }

    private static final class BoundedOutput {
        private final int limit;
        private final StringBuilder value = new StringBuilder();

        private BoundedOutput(int limit) {
            this.limit = limit;
        }

        private synchronized BoundedOutput append(String text) {
            if (text == null || value.length() >= limit) {
                return this;
            }
            int remaining = limit - value.length();
            value.append(text, 0, Math.min(remaining, text.length()));
            return this;
        }

        private synchronized String value() {
            return value.toString();
        }
    }
}
