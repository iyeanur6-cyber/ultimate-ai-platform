/* ABOUTME: Bounded SoX audio processing for managed workspaces. */
package ai.ultimate.tools.builtin;

import ai.ultimate.tools.UltimateTool;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class AudioProcessingTool implements UltimateTool {

    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration STREAM_DRAIN_TIMEOUT = Duration.ofSeconds(2);
    private static final int MAX_CAPTURED_OUTPUT_CHARS = 20_000;
    private static final int MAX_INPUT_FILES = 10;
    private static final long MAX_INPUT_BYTES = 100L * 1024L * 1024L;
    private static final Set<String> OPERATIONS = Set.of(
            "noise-profile",
            "noise-reduce",
            "normalize",
            "pitch",
            "tempo",
            "split",
            "concatenate");
    private static final Set<String> OUTPUT_FORMATS = Set.of(
            "wav",
            "flac",
            "mp3",
            "ogg",
            "aiff");

    private final Path managedWorkspaceRoot;
    private final ProcessExecutor processExecutor;
    private final SoxLocator soxLocator;

    public AudioProcessingTool() {
        this(
                Path.of(
                        System.getProperty("user.home"),
                        "ultimate-managed-workspaces"),
                new DefaultProcessExecutor(),
                AudioProcessingTool::findSoxBinary);
    }

    public AudioProcessingTool(
            Path managedWorkspaceRoot,
            ProcessExecutor processExecutor,
            SoxLocator soxLocator) {
        this.managedWorkspaceRoot = managedWorkspaceRoot
                .toAbsolutePath()
                .normalize();
        this.processExecutor = processExecutor;
        this.soxLocator = soxLocator;
    }

    @Tool(description =
            "Process local audio with SoX inside a managed workspace. "
                    + "Supported operations are noise-profile, noise-reduce, normalize, "
                    + "pitch, tempo, split, and concatenate. inputPaths is a comma-separated "
                    + "list of relative files; concatenate requires two or more inputs and all "
                    + "other operations require one. effectValue is reduction strength 0-1, "
                    + "normalization dB -30 to 0, pitch cents -1200 to 1200, or tempo factor "
                    + "0.5 to 2. split uses startSeconds and durationSeconds. Returns the output "
                    + "path or a bounded validation/runtime error and never throws to the model.")
    public String processAudio(
            @ToolParam(description =
                    "Absolute workspace below ~/ultimate-managed-workspaces")
            String workspacePath,
            @ToolParam(description =
                    "Comma-separated relative audio input paths")
            String inputPaths,
            @ToolParam(description =
                    "Existing relative output directory")
            String outputDirectory,
            @ToolParam(description =
                    "Safe output basename without an extension")
            String outputName,
            @ToolParam(description =
                    "noise-profile, noise-reduce, normalize, pitch, tempo, split, or concatenate")
            String operation,
            @ToolParam(description =
                    "Output format: wav, flac, mp3, ogg, or aiff")
            String outputFormat,
            @ToolParam(description =
                    "Relative noise profile path for noise-reduce; empty otherwise")
            String noiseProfilePath,
            @ToolParam(description =
                    "Operation value: reduction strength, dB, pitch cents, or tempo factor")
            double effectValue,
            @ToolParam(description =
                    "Split start in seconds; zero for other operations")
            double startSeconds,
            @ToolParam(description =
                    "Split duration in seconds; zero for other operations")
            double durationSeconds) {

        Request request;
        try {
            request = validateRequest(
                    workspacePath,
                    inputPaths,
                    outputDirectory,
                    outputName,
                    operation,
                    outputFormat,
                    noiseProfilePath,
                    effectValue,
                    startSeconds,
                    durationSeconds);
        } catch (IllegalArgumentException e) {
            return "Validation Error: " + e.getMessage();
        }

        Optional<String> soxBinary = soxLocator.locate();
        if (soxBinary.isEmpty()) {
            return "Environment Error: SoX executable 'sox' was not found on this host.";
        }

        Path runtimeDirectory = null;
        try {
            Files.createDirectories(managedWorkspaceRoot);
            Path allowedRoot = managedWorkspaceRoot.toRealPath();
            Path workspace = Path.of(request.workspacePath()).toRealPath();
            if (!workspace.startsWith(allowedRoot)) {
                return "Path Restriction: Workspace must be inside the managed workspace root.";
            }

            Path outputRoot = resolveOutputDirectory(
                    workspace,
                    request.outputDirectory());
            List<Path> inputs = resolveInputs(workspace, request.inputPaths());
            Path noiseProfile = resolveNoiseProfile(
                    workspace,
                    request.operation(),
                    request.noiseProfilePath());

            runtimeDirectory = Files.createTempDirectory(
                    workspace,
                    ".ultimate-audio-");
            List<Path> stagedInputs = stageInputs(inputs, runtimeDirectory);
            Path stagedProfile = stageNoiseProfile(noiseProfile, runtimeDirectory);

            String outputSuffix = "noise-profile".equals(request.operation())
                    ? ".noise-profile"
                    : "." + request.outputFormat();
            String finalName = request.outputName() + outputSuffix;
            Path finalOutput = outputRoot.resolve(finalName).normalize();
            if (!finalOutput.startsWith(outputRoot) || Files.exists(finalOutput)) {
                return "Path Restriction: Refusing to overwrite existing output "
                        + finalName + ".";
            }

            Path stagedOutput = runtimeDirectory.resolve(
                    "output-01" + outputSuffix);
            Files.createFile(stagedOutput);
            Path canonicalStagedOutput = stagedOutput.toRealPath();

            List<String> command = buildCommand(
                    soxBinary.get(),
                    stagedInputs,
                    canonicalStagedOutput,
                    stagedProfile,
                    request);
            ExecutionResult execution = execute(command, runtimeDirectory);
            if (execution.timedOut()) {
                return "Watchdog Interdiction: Audio processing exceeded the 60 second "
                        + "runtime limit.";
            }
            if (execution.exitCode() != 0) {
                return "Audio Processing Failure: SoX exited with code "
                        + execution.exitCode()
                        + ".\n"
                        + execution.output();
            }
            if (Files.size(canonicalStagedOutput) == 0) {
                return "Audio Processing Failure: SoX produced an empty output.";
            }

            String relativeOutput = workspace.relativize(finalOutput).toString();
            String diagnostics = execution.output().isBlank()
                    ? ""
                    : " Diagnostics: " + execution.output();
            moveWithoutOverwrite(canonicalStagedOutput, finalOutput);
            return "Audio processing completed: "
                    + relativeOutput
                    + "."
                    + diagnostics;
        } catch (PathRestrictionException e) {
            return "Path Restriction: " + safeMessage(e);
        } catch (IOException e) {
            return "Audio Processing Failure: " + safeMessage(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Audio Processing Failure: Processing was interrupted.";
        } finally {
            cleanupRuntime(runtimeDirectory);
        }
    }

    private Request validateRequest(
            String workspacePath,
            String inputPaths,
            String outputDirectory,
            String outputName,
            String operation,
            String outputFormat,
            String noiseProfilePath,
            double effectValue,
            double startSeconds,
            double durationSeconds) {
        if (workspacePath == null || workspacePath.isBlank()) {
            throw new IllegalArgumentException("workspacePath is required.");
        }
        if (inputPaths == null || inputPaths.isBlank()) {
            throw new IllegalArgumentException("At least one input audio file is required.");
        }
        if (outputDirectory == null || outputDirectory.isBlank()) {
            throw new IllegalArgumentException("outputDirectory is required.");
        }
        Path outputPath = Path.of(outputDirectory.trim());
        if (outputPath.isAbsolute()) {
            throw new IllegalArgumentException("outputDirectory must be relative.");
        }
        if (!validOutputName(outputName)) {
            throw new IllegalArgumentException(
                    "outputName must be 1-80 letters, numbers, dots, dashes, or underscores.");
        }

        String normalizedOperation = normalize(operation);
        if (!OPERATIONS.contains(normalizedOperation)) {
            throw new IllegalArgumentException(
                    "operation must be noise-profile, noise-reduce, normalize, pitch, "
                            + "tempo, split, or concatenate.");
        }
        String normalizedFormat = normalize(outputFormat);
        if (!OUTPUT_FORMATS.contains(normalizedFormat)) {
            throw new IllegalArgumentException(
                    "outputFormat must be wav, flac, mp3, ogg, or aiff.");
        }

        List<String> normalizedInputs = normalizeInputPaths(inputPaths);
        if ("concatenate".equals(normalizedOperation)) {
            if (normalizedInputs.size() < 2) {
                throw new IllegalArgumentException(
                        "concatenate requires at least two input files.");
            }
        } else if (normalizedInputs.size() != 1) {
            throw new IllegalArgumentException(
                    "This operation requires exactly one input file.");
        }

        String normalizedProfile = noiseProfilePath == null
                ? ""
                : noiseProfilePath.trim();
        if ("noise-reduce".equals(normalizedOperation)
                && normalizedProfile.isBlank()) {
            throw new IllegalArgumentException(
                    "noiseProfilePath is required for noise-reduce.");
        }
        validateOperationValues(
                normalizedOperation,
                effectValue,
                startSeconds,
                durationSeconds);

        return new Request(
                workspacePath.trim(),
                List.copyOf(normalizedInputs),
                outputDirectory.trim(),
                outputName.trim(),
                normalizedOperation,
                normalizedFormat,
                normalizedProfile,
                effectValue,
                startSeconds,
                durationSeconds);
    }

    private List<String> normalizeInputPaths(String inputPaths) {
        List<String> inputs = new ArrayList<>();
        Set<String> uniqueInputs = new HashSet<>();
        for (String rawInput : inputPaths.split(",", -1)) {
            String input = rawInput.trim();
            if (input.isBlank()) {
                throw new IllegalArgumentException(
                        "inputPaths contains an empty entry.");
            }
            Path inputPath = Path.of(input);
            if (inputPath.isAbsolute()) {
                throw new IllegalArgumentException("Input paths must be relative.");
            }
            if (!uniqueInputs.add(input)) {
                throw new IllegalArgumentException(
                        "Duplicate input path: " + input + ".");
            }
            inputs.add(input);
        }
        if (inputs.size() > MAX_INPUT_FILES) {
            throw new IllegalArgumentException(
                    "A request may contain at most 10 audio files.");
        }
        return inputs;
    }

    private void validateOperationValues(
            String operation,
            double effectValue,
            double startSeconds,
            double durationSeconds) {
        if (!Double.isFinite(effectValue)
                || !Double.isFinite(startSeconds)
                || !Double.isFinite(durationSeconds)) {
            throw new IllegalArgumentException("Numeric values must be finite.");
        }
        switch (operation) {
            case "noise-reduce" -> requireRange(
                    effectValue,
                    0.0,
                    1.0,
                    "noise-reduce strength");
            case "normalize" -> requireRange(
                    effectValue,
                    -30.0,
                    0.0,
                    "normalize dB");
            case "pitch" -> requireRange(
                    effectValue,
                    -1_200.0,
                    1_200.0,
                    "pitch cents");
            case "tempo" -> requireRange(
                    effectValue,
                    0.5,
                    2.0,
                    "tempo factor");
            case "split" -> {
                requireRange(startSeconds, 0.0, 86_400.0, "split start");
                requireRange(durationSeconds, 0.1, 3_600.0, "split duration");
            }
            default -> {
                // Operations without an effect value need no additional range.
            }
        }
    }

    private void requireRange(
            double value,
            double minimum,
            double maximum,
            String label) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    label + " must be between "
                            + number(minimum)
                            + " and "
                            + number(maximum)
                            + ".");
        }
    }

    private boolean validOutputName(String outputName) {
        if (outputName == null) {
            return false;
        }
        String normalized = outputName.trim();
        return normalized.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,79}")
                && !normalized.contains("..");
    }

    private Path resolveOutputDirectory(
            Path workspace,
            String outputDirectory) throws IOException {
        Path candidate = workspace.resolve(outputDirectory).normalize();
        if (!candidate.startsWith(workspace) || Files.isSymbolicLink(candidate)) {
            throw new PathRestrictionException(
                    "Output directory escapes the managed workspace.");
        }
        Path outputRoot = candidate.toRealPath();
        if (!outputRoot.startsWith(workspace) || !Files.isDirectory(outputRoot)) {
            throw new PathRestrictionException(
                    "Output directory must be an existing managed directory.");
        }
        return outputRoot;
    }

    private List<Path> resolveInputs(
            Path workspace,
            List<String> inputPaths) throws IOException {
        List<Path> inputs = new ArrayList<>();
        Set<Path> uniqueInputs = new HashSet<>();
        for (String inputPath : inputPaths) {
            Path candidate = workspace.resolve(inputPath).normalize();
            if (!candidate.startsWith(workspace)) {
                throw new PathRestrictionException(
                        "Input path escapes the managed workspace.");
            }
            Path input = candidate.toRealPath();
            if (!input.startsWith(workspace)
                    || !Files.isRegularFile(input)
                    || Files.size(input) > MAX_INPUT_BYTES) {
                throw new PathRestrictionException(
                        "Input must be a managed file no larger than 100 MiB.");
            }
            if (!uniqueInputs.add(input)) {
                throw new PathRestrictionException(
                        "Input files must resolve to unique paths.");
            }
            inputs.add(input);
        }
        return inputs;
    }

    private Path resolveNoiseProfile(
            Path workspace,
            String operation,
            String noiseProfilePath) throws IOException {
        if (!"noise-reduce".equals(operation)) {
            return null;
        }
        Path candidate = workspace.resolve(noiseProfilePath).normalize();
        if (!candidate.startsWith(workspace)) {
            throw new PathRestrictionException(
                    "Noise profile path escapes the managed workspace.");
        }
        Path profile = candidate.toRealPath();
        if (!profile.startsWith(workspace)
                || !Files.isRegularFile(profile)
                || Files.size(profile) > MAX_INPUT_BYTES) {
            throw new PathRestrictionException(
                    "Noise profile must be a managed file no larger than 100 MiB.");
        }
        return profile;
    }

    private List<Path> stageInputs(
            List<Path> inputs,
            Path runtimeDirectory) throws IOException {
        List<Path> staged = new ArrayList<>();
        for (int index = 0; index < inputs.size(); index++) {
            Path input = inputs.get(index);
            Path stagedInput = runtimeDirectory.resolve(
                    String.format(
                            Locale.ROOT,
                            "input-%02d%s",
                            index + 1,
                            suffixOf(input.getFileName().toString())));
            Files.copy(input, stagedInput);
            staged.add(stagedInput.toRealPath());
        }
        return staged;
    }

    private Path stageNoiseProfile(
            Path profile,
            Path runtimeDirectory) throws IOException {
        if (profile == null) {
            return null;
        }
        Path stagedProfile = runtimeDirectory.resolve("profile.noise-profile");
        Files.copy(profile, stagedProfile);
        return stagedProfile.toRealPath();
    }

    private List<String> buildCommand(
            String binary,
            List<Path> inputs,
            Path output,
            Path noiseProfile,
            Request request) {
        List<String> command = new ArrayList<>();
        command.add(binary);
        if ("concatenate".equals(request.operation())) {
            inputs.forEach(input -> command.add(input.toString()));
            command.add(output.toString());
            return List.copyOf(command);
        }

        command.add(inputs.getFirst().toString());
        if ("noise-profile".equals(request.operation())) {
            command.add("-n");
            command.add("noiseprof");
            command.add(output.toString());
            return List.copyOf(command);
        }

        command.add(output.toString());
        switch (request.operation()) {
            case "noise-reduce" -> {
                command.add("noisered");
                command.add(noiseProfile.toString());
                command.add(number(request.effectValue()));
            }
            case "normalize" -> {
                command.add("gain");
                command.add("-n");
                command.add(number(request.effectValue()));
            }
            case "pitch" -> {
                command.add("pitch");
                command.add(number(request.effectValue()));
            }
            case "tempo" -> {
                command.add("tempo");
                command.add("-s");
                command.add(number(request.effectValue()));
            }
            case "split" -> {
                command.add("trim");
                command.add(number(request.startSeconds()));
                command.add(number(request.durationSeconds()));
            }
            default -> throw new IllegalStateException(
                    "Unsupported operation after validation: " + request.operation());
        }
        return List.copyOf(command);
    }

    private ExecutionResult execute(
            List<String> command,
            Path runtimeDirectory) throws IOException, InterruptedException {
        Process process = processExecutor.start(
                command,
                Map.of(
                        "HOME", runtimeDirectory.toString(),
                        "TMPDIR", runtimeDirectory.toString()));
        BoundedOutput output = new BoundedOutput(MAX_CAPTURED_OUTPUT_CHARS);
        ExecutorService streamExecutor = Executors.newVirtualThreadPerTaskExecutor();
        Future<?> stdout = streamExecutor.submit(() -> drainStream(
                process.getInputStream(),
                output,
                "[stdout] "));
        Future<?> stderr = streamExecutor.submit(() -> drainStream(
                process.getErrorStream(),
                output,
                "[stderr] "));

        try {
            boolean finished = process.waitFor(
                    PROCESS_TIMEOUT.toSeconds(),
                    TimeUnit.SECONDS);
            if (!finished) {
                terminateProcess(process);
                awaitDrain(stdout);
                awaitDrain(stderr);
                return new ExecutionResult(-1, output.value(), true);
            }

            awaitDrain(stdout);
            awaitDrain(stderr);
            return new ExecutionResult(
                    process.exitValue(),
                    output.value(),
                    false);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            closeProcessStreams(process);
            stdout.cancel(true);
            stderr.cancel(true);
            streamExecutor.shutdownNow();
        }
    }

    private void drainStream(
            InputStream stream,
            BoundedOutput output,
            String prefix) {
        try (stream) {
            byte[] buffer = new byte[4_096];
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                if (read > 0) {
                    output.append(
                            prefix,
                            new String(
                                    buffer,
                                    0,
                                    read,
                                    StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            output.append(prefix, "stream closed");
        }
    }

    private void awaitDrain(Future<?> drain) throws InterruptedException {
        try {
            drain.get(STREAM_DRAIN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException | TimeoutException ignored) {
            drain.cancel(true);
        }
    }

    private void terminateProcess(Process process) throws InterruptedException {
        process.destroy();
        if (!process.waitFor(200, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            process.waitFor(200, TimeUnit.MILLISECONDS);
        }
    }

    private static void closeProcessStreams(Process process) {
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
            // Best-effort close.
        }
        try {
            process.getInputStream().close();
        } catch (IOException ignored) {
            // Best-effort close.
        }
        try {
            process.getErrorStream().close();
        } catch (IOException ignored) {
            // Best-effort close.
        }
    }

    private void moveWithoutOverwrite(Path source, Path target) throws IOException {
        Files.move(source, target);
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

    private static Optional<String> findSoxBinary() {
        String command = System.getProperty("os.name")
                .toLowerCase(Locale.ROOT)
                .contains("win") ? "where" : "which";
        Process process = null;
        try {
            process = new ProcessBuilder(command, "sox").start();
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
                process.destroyForcibly();
            }
            if (process != null) {
                closeProcessStreams(process);
            }
        }
        return Optional.empty();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String suffixOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return ".audio";
        }
        return name.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static String number(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    public interface ProcessExecutor {
        Process start(List<String> command, Map<String, String> environment)
                throws IOException;
    }

    public interface SoxLocator {
        Optional<String> locate();
    }

    private static final class DefaultProcessExecutor implements ProcessExecutor {
        @Override
        public Process start(
                List<String> command,
                Map<String, String> environment) throws IOException {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.environment().putAll(environment);
            return builder.start();
        }
    }

    private static final class BoundedOutput {
        private final int maximumCharacters;
        private final StringBuilder value = new StringBuilder();

        private BoundedOutput(int maximumCharacters) {
            this.maximumCharacters = maximumCharacters;
        }

        private synchronized void append(String prefix, String text) {
            if (value.length() >= maximumCharacters) {
                return;
            }
            String addition = prefix + text;
            int remaining = maximumCharacters - value.length();
            value.append(addition, 0, Math.min(addition.length(), remaining));
        }

        private synchronized String value() {
            return value.toString().trim();
        }
    }

    private record Request(
            String workspacePath,
            List<String> inputPaths,
            String outputDirectory,
            String outputName,
            String operation,
            String outputFormat,
            String noiseProfilePath,
            double effectValue,
            double startSeconds,
            double durationSeconds) {
    }

    private record ExecutionResult(int exitCode, String output, boolean timedOut) {
    }

    private static final class PathRestrictionException extends IOException {
        private PathRestrictionException(String message) {
            super(message);
        }
    }
}
