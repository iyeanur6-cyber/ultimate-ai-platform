/* ABOUTME: Bounded SoX audio processing for managed workspaces. */
package ai.ultimate.tools.builtin;

import ai.ultimate.tools.UltimateTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
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
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
public class AudioProcessingTool implements UltimateTool {

    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration STREAM_DRAIN_TIMEOUT = Duration.ofSeconds(2);
    private static final int MAX_CAPTURED_OUTPUT_CHARS = 20_000;
    private static final int MAX_MODEL_DIAGNOSTIC_CHARS = 1_000;
    private static final int MAX_INPUT_FILES = 10;
    private static final long MAX_INPUT_BYTES = 100L * 1024L * 1024L;
    private static final long MAX_TOTAL_INPUT_BYTES = 250L * 1024L * 1024L;
    private static final long MAX_GENERATED_OUTPUT_BYTES = 512L * 1024L * 1024L;
    private static final long OUTPUT_SIZE_POLL_MILLIS = 100L;
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
            return "Validation Error: " + safeMessage(e);
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
            List<ResolvedInput> inputs = resolveInputs(
                    workspace,
                    request.inputPaths());
            ResolvedInput noiseProfile = resolveNoiseProfile(
                    workspace,
                    request.operation(),
                    request.noiseProfilePath());
            enforceAggregateInputBudget(inputs, noiseProfile);

            try (SecureDirectoryStream<Path> workspaceDirectory =
                         openSecureDirectory(workspace, "managed workspace");
                 SecureDirectoryStream<Path> secureOutputDirectory =
                         openSecureDirectory(
                                 workspaceDirectory,
                                 Path.of(request.outputDirectory()),
                                 "output directory")) {
                DirectoryIdentity workspaceIdentity = directoryIdentity(
                        workspaceDirectory);
                DirectoryIdentity outputIdentity = directoryIdentity(
                        secureOutputDirectory);

                runtimeDirectory = Files.createTempDirectory(
                        allowedRoot,
                        ".ultimate-audio-");
                try (SecureDirectoryStream<Path> runtime = openSecureDirectory(
                        runtimeDirectory,
                        "runtime directory")) {
                    CopyBudget copyBudget = new CopyBudget(MAX_TOTAL_INPUT_BYTES);
                    List<Path> stagedInputs = stageInputs(
                            inputs,
                            workspaceDirectory,
                            runtimeDirectory,
                            runtime,
                            copyBudget);
                    Path stagedProfile = stageNoiseProfile(
                            noiseProfile,
                            workspaceDirectory,
                            runtimeDirectory,
                            runtime,
                            copyBudget);

                    String outputSuffix = "noise-profile".equals(request.operation())
                            ? ".noise-profile"
                            : "." + request.outputFormat();
                    String finalName = request.outputName() + outputSuffix;
                    Path finalOutput = outputRoot.resolve(finalName).normalize();
                    Path finalEntry = Path.of(finalName);
                    if (!finalOutput.startsWith(outputRoot)
                            || secureEntryExists(
                                    secureOutputDirectory,
                                    finalEntry)) {
                        return "Path Restriction: Refusing to overwrite existing output "
                                + finalName + ".";
                    }

                    Path stagedEntry = Path.of("output-01" + outputSuffix);
                    createSecureFile(runtime, stagedEntry);
                    Path canonicalStagedOutput = runtimeDirectory
                            .resolve(stagedEntry)
                            .toRealPath();

                    List<String> command = buildCommand(
                            soxBinary.get(),
                            stagedInputs,
                            canonicalStagedOutput,
                            stagedProfile,
                            request);
                    ExecutionResult execution = execute(
                            command,
                            runtimeDirectory,
                            canonicalStagedOutput);
                    if (execution.outputLimitExceeded()) {
                        return "Resource Limit: Audio processing exceeded the generated "
                                + "output limit.";
                    }
                    if (execution.timedOut()) {
                        return "Watchdog Interdiction: Audio processing exceeded the 60 second "
                                + "runtime limit.";
                    }
                    if (execution.exitCode() != 0) {
                        log.warn(
                                "SoX exited with code {}. Output: {}",
                                execution.exitCode(),
                                execution.output());
                        return "Audio Processing Failure: SoX exited with code "
                                + execution.exitCode()
                                + ". "
                                + sanitizeDiagnostic(
                                        execution.output(),
                                        workspace,
                                        runtimeDirectory);
                    }
                    long outputBytes = Files.size(canonicalStagedOutput);
                    if (outputBytes == 0) {
                        return "Audio Processing Failure: SoX produced an empty output.";
                    }
                    if (outputBytes > MAX_GENERATED_OUTPUT_BYTES) {
                        return "Resource Limit: Audio processing exceeded the generated "
                                + "output limit.";
                    }

                    String relativeOutput = workspace
                            .relativize(finalOutput)
                            .toString();
                    String sanitizedOutput = sanitizeDiagnostic(
                            execution.output(),
                            workspace,
                            runtimeDirectory);
                    String diagnostics = sanitizedOutput.isBlank()
                            ? ""
                            : " Diagnostics: " + sanitizedOutput;
                    verifyDirectoryIdentity(
                            workspace,
                            workspaceIdentity,
                            "managed workspace");
                    verifyDirectoryIdentity(
                            outputRoot,
                            outputIdentity,
                            "output directory");
                    moveWithoutOverwrite(
                            runtime,
                            stagedEntry,
                            secureOutputDirectory,
                            finalEntry);
                    try {
                        verifyDirectoryIdentity(
                                workspace,
                                workspaceIdentity,
                                "managed workspace");
                        verifyDirectoryIdentity(
                                outputRoot,
                                outputIdentity,
                                "output directory");
                    } catch (PathRestrictionException e) {
                        deleteSecureFile(secureOutputDirectory, finalEntry);
                        throw e;
                    }
                    return "Audio processing completed: "
                            + relativeOutput
                            + "."
                            + diagnostics;
                }
            }
        } catch (PathRestrictionException e) {
            log.warn("Audio processing path restriction: {}", e.getMessage());
            return "Path Restriction: "
                    + safeMessage(e, managedWorkspaceRoot, runtimeDirectory);
        } catch (IOException e) {
            log.warn("Audio processing I/O failure", e);
            return "Audio Processing Failure: "
                    + safeMessage(e, managedWorkspaceRoot, runtimeDirectory);
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
                throw new IllegalArgumentException("Duplicate input path.");
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

    private List<ResolvedInput> resolveInputs(
            Path workspace,
            List<String> inputPaths) throws IOException {
        List<ResolvedInput> inputs = new ArrayList<>();
        Set<Path> uniqueInputs = new HashSet<>();
        for (String inputPath : inputPaths) {
            Path relativePath = Path.of(inputPath).normalize();
            Path candidate = workspace.resolve(relativePath).normalize();
            if (!candidate.startsWith(workspace)) {
                throw new PathRestrictionException(
                        "Input path escapes the managed workspace.");
            }
            Path input = candidate.toRealPath();
            long inputBytes = Files.size(input);
            if (!input.startsWith(workspace)
                    || !Files.isRegularFile(input)
                    || inputBytes > MAX_INPUT_BYTES) {
                throw new PathRestrictionException(
                        "Input must be a managed file no larger than 100 MiB.");
            }
            if (!uniqueInputs.add(input)) {
                throw new PathRestrictionException(
                        "Input files must resolve to unique paths.");
            }
            inputs.add(new ResolvedInput(relativePath, input, inputBytes));
        }
        return inputs;
    }

    private ResolvedInput resolveNoiseProfile(
            Path workspace,
            String operation,
            String noiseProfilePath) throws IOException {
        if (!"noise-reduce".equals(operation)) {
            return null;
        }
        Path relativePath = Path.of(noiseProfilePath).normalize();
        Path candidate = workspace.resolve(relativePath).normalize();
        if (!candidate.startsWith(workspace)) {
            throw new PathRestrictionException(
                    "Noise profile path escapes the managed workspace.");
        }
        Path profile = candidate.toRealPath();
        long profileBytes = Files.size(profile);
        if (!profile.startsWith(workspace)
                || !Files.isRegularFile(profile)
                || profileBytes > MAX_INPUT_BYTES) {
            throw new PathRestrictionException(
                    "Noise profile must be a managed file no larger than 100 MiB.");
        }
        return new ResolvedInput(relativePath, profile, profileBytes);
    }

    private void enforceAggregateInputBudget(
            List<ResolvedInput> inputs,
            ResolvedInput profile) throws PathRestrictionException {
        long totalBytes = 0L;
        for (ResolvedInput input : inputs) {
            totalBytes = addInputBytes(totalBytes, input.size());
        }
        if (profile != null) {
            totalBytes = addInputBytes(totalBytes, profile.size());
        }
        if (totalBytes > MAX_TOTAL_INPUT_BYTES) {
            throw new PathRestrictionException(
                    "Audio files exceed the aggregate input limit of 250 MiB.");
        }
    }

    private long addInputBytes(long totalBytes, long inputBytes)
            throws PathRestrictionException {
        try {
            return Math.addExact(totalBytes, inputBytes);
        } catch (ArithmeticException e) {
            throw new PathRestrictionException(
                    "Audio files exceed the aggregate input limit of 250 MiB.");
        }
    }

    private List<Path> stageInputs(
            List<ResolvedInput> inputs,
            SecureDirectoryStream<Path> workspaceDirectory,
            Path runtimeDirectory,
            SecureDirectoryStream<Path> runtime,
            CopyBudget copyBudget) throws IOException {
        List<Path> staged = new ArrayList<>();
        for (int index = 0; index < inputs.size(); index++) {
            ResolvedInput input = inputs.get(index);
            Path stagedName = Path.of(String.format(
                    Locale.ROOT,
                    "input-%02d%s",
                    index + 1,
                    suffixOf(input.canonicalPath().getFileName().toString())));
            copyManagedFile(
                    workspaceDirectory,
                    input.relativePath(),
                    runtime,
                    stagedName,
                    copyBudget);
            Path stagedInput = runtimeDirectory.resolve(stagedName);
            staged.add(stagedInput.toRealPath());
        }
        return staged;
    }

    private Path stageNoiseProfile(
            ResolvedInput profile,
            SecureDirectoryStream<Path> workspaceDirectory,
            Path runtimeDirectory,
            SecureDirectoryStream<Path> runtime,
            CopyBudget copyBudget) throws IOException {
        if (profile == null) {
            return null;
        }
        Path stagedName = Path.of("profile.noise-profile");
        copyManagedFile(
                workspaceDirectory,
                profile.relativePath(),
                runtime,
                stagedName,
                copyBudget);
        Path stagedProfile = runtimeDirectory.resolve(stagedName);
        return stagedProfile.toRealPath();
    }

    private void copyManagedFile(
            SecureDirectoryStream<Path> workspaceDirectory,
            Path sourcePath,
            SecureDirectoryStream<Path> runtime,
            Path targetName,
            CopyBudget copyBudget) throws IOException {
        try (SecureFileParent source = openSecureFileParent(
                     workspaceDirectory,
                     sourcePath,
                     "input file");
             SeekableByteChannel input = source.directory().newByteChannel(
                     source.fileName(),
                     Set.of(
                             StandardOpenOption.READ,
                             LinkOption.NOFOLLOW_LINKS));
             SeekableByteChannel output = runtime.newByteChannel(
                     targetName,
                     Set.of(
                             StandardOpenOption.CREATE_NEW,
                             StandardOpenOption.WRITE,
                             LinkOption.NOFOLLOW_LINKS))) {
            if (input.size() > MAX_INPUT_BYTES) {
                throw new PathRestrictionException(
                        "Input must be a managed file no larger than 100 MiB.");
            }
            ByteBuffer buffer = ByteBuffer.allocate(64 * 1024);
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                copyBudget.add(read);
                buffer.flip();
                while (buffer.hasRemaining()) {
                    output.write(buffer);
                }
                buffer.clear();
            }
        }
    }

    private SecureFileParent openSecureFileParent(
            SecureDirectoryStream<Path> root,
            Path relativePath,
            String label) throws IOException {
        Path normalized = relativePath.normalize();
        if (normalized.isAbsolute()
                || normalized.getFileName() == null
                || normalized.startsWith("..")) {
            throw new PathRestrictionException(
                    label + " escapes the managed workspace.");
        }
        Path parent = normalized.getParent() == null
                ? Path.of(".")
                : normalized.getParent();
        SecureDirectoryStream<Path> directory = openSecureDirectory(
                root,
                parent,
                label + " parent");
        return new SecureFileParent(directory, normalized.getFileName());
    }

    private SecureDirectoryStream<Path> openSecureDirectory(
            Path directory,
            String label) throws IOException {
        DirectoryStream<Path> stream = Files.newDirectoryStream(directory);
        if (stream instanceof SecureDirectoryStream<?>) {
            return requireSecureDirectoryStream(stream, label);
        }
        return new IdentityBoundDirectoryStream(directory, stream, label);
    }

    private SecureDirectoryStream<Path> openSecureDirectory(
            SecureDirectoryStream<Path> root,
            Path relativePath,
            String label) throws IOException {
        Path normalized = relativePath.normalize();
        if (normalized.isAbsolute() || normalized.startsWith("..")) {
            throw new PathRestrictionException(
                    label + " escapes the managed workspace.");
        }

        SecureDirectoryStream<Path> current = requireSecureDirectoryStream(
                root.newDirectoryStream(
                        Path.of("."),
                        LinkOption.NOFOLLOW_LINKS),
                label);
        try {
            for (Path segment : normalized) {
                if (".".equals(segment.toString())) {
                    continue;
                }
                SecureDirectoryStream<Path> next = requireSecureDirectoryStream(
                        current.newDirectoryStream(
                                segment,
                                LinkOption.NOFOLLOW_LINKS),
                        label);
                current.close();
                current = next;
            }
            return current;
        } catch (IOException | RuntimeException e) {
            current.close();
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private SecureDirectoryStream<Path> requireSecureDirectoryStream(
            DirectoryStream<Path> directory,
            String label) throws IOException {
        if (directory instanceof SecureDirectoryStream<?>) {
            return (SecureDirectoryStream<Path>) directory;
        }
        directory.close();
        throw new PathRestrictionException("Could not bind the " + label + ".");
    }

    private void createSecureFile(
            SecureDirectoryStream<Path> directory,
            Path fileName) throws IOException {
        try (SeekableByteChannel ignored = directory.newByteChannel(
                fileName,
                Set.of(
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS))) {
            // Creating through the directory handle binds the file to that directory.
        }
    }

    private boolean secureEntryExists(
            SecureDirectoryStream<Path> directory,
            Path fileName) throws IOException {
        BasicFileAttributeView view = directory.getFileAttributeView(
                fileName,
                BasicFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS);
        if (view == null) {
            throw new IOException("Basic file attributes are unavailable.");
        }
        try {
            view.readAttributes();
            return true;
        } catch (NoSuchFileException e) {
            return false;
        }
    }

    private DirectoryIdentity directoryIdentity(
            SecureDirectoryStream<Path> directory) throws IOException {
        BasicFileAttributeView view = directory.getFileAttributeView(
                BasicFileAttributeView.class);
        if (view == null) {
            throw new IOException("Basic directory attributes are unavailable.");
        }
        BasicFileAttributes attributes = view.readAttributes();
        return new DirectoryIdentity(
                attributes.fileKey(),
                attributes.creationTime());
    }

    private void verifyDirectoryIdentity(
            Path directory,
            DirectoryIdentity expected,
            String label) throws IOException {
        BasicFileAttributes current = Files.readAttributes(
                directory,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        if (!current.isDirectory() || !expected.matches(current)) {
            throw new PathRestrictionException(
                    "The " + label + " changed during audio processing.");
        }
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
            Path runtimeDirectory,
            Path generatedOutput) throws IOException, InterruptedException {
        Process process = processExecutor.start(
                command,
                Map.of(
                        "HOME", runtimeDirectory.toString(),
                        "TMPDIR", runtimeDirectory.toString()));
        BoundedOutput output = new BoundedOutput(MAX_CAPTURED_OUTPUT_CHARS);
        AtomicBoolean outputLimitExceeded = new AtomicBoolean(false);
        ExecutorService streamExecutor = Executors.newVirtualThreadPerTaskExecutor();
        Future<?> stdout = streamExecutor.submit(() -> drainStream(
                process.getInputStream(),
                output,
                "[stdout] "));
        Future<?> stderr = streamExecutor.submit(() -> drainStream(
                process.getErrorStream(),
                output,
                "[stderr] "));
        Future<?> outputWatcher = streamExecutor.submit(() -> monitorGeneratedOutput(
                process,
                generatedOutput,
                outputLimitExceeded));

        try {
            boolean finished = process.waitFor(
                    PROCESS_TIMEOUT.toSeconds(),
                    TimeUnit.SECONDS);
            if (outputLimitExceeded.get()
                    || generatedOutputExceedsLimit(generatedOutput)) {
                outputLimitExceeded.set(true);
                if (process.isAlive()) {
                    terminateProcess(process);
                }
                awaitDrain(stdout);
                awaitDrain(stderr);
                return new ExecutionResult(-1, output.value(), false, true);
            }
            if (!finished) {
                terminateProcess(process);
                awaitDrain(stdout);
                awaitDrain(stderr);
                return new ExecutionResult(-1, output.value(), true, false);
            }

            awaitDrain(stdout);
            awaitDrain(stderr);
            return new ExecutionResult(
                    process.exitValue(),
                    output.value(),
                    false,
                    false);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            closeProcessStreams(process);
            stdout.cancel(true);
            stderr.cancel(true);
            outputWatcher.cancel(true);
            streamExecutor.shutdownNow();
        }
    }

    private void monitorGeneratedOutput(
            Process process,
            Path generatedOutput,
            AtomicBoolean outputLimitExceeded) {
        try {
            while (!Thread.currentThread().isInterrupted() && process.isAlive()) {
                if (generatedOutputExceedsLimit(generatedOutput)) {
                    outputLimitExceeded.set(true);
                    process.destroyForcibly();
                    return;
                }
                Thread.sleep(OUTPUT_SIZE_POLL_MILLIS);
            }
            if (generatedOutputExceedsLimit(generatedOutput)) {
                outputLimitExceeded.set(true);
            }
        } catch (IOException e) {
            log.warn("Could not monitor generated audio size", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean generatedOutputExceedsLimit(Path generatedOutput)
            throws IOException {
        return Files.size(generatedOutput) > MAX_GENERATED_OUTPUT_BYTES;
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

    private void moveWithoutOverwrite(
            SecureDirectoryStream<Path> sourceDirectory,
            Path source,
            SecureDirectoryStream<Path> targetDirectory,
            Path target) throws IOException {
        if (secureEntryExists(targetDirectory, target)) {
            throw new IOException(
                    "Refusing to overwrite an output created concurrently.");
        }
        sourceDirectory.move(source, targetDirectory, target);
    }

    private void deleteSecureFile(
            SecureDirectoryStream<Path> directory,
            Path fileName) {
        try {
            directory.deleteFile(fileName);
        } catch (NoSuchFileException ignored) {
            // The file was already absent.
        } catch (IOException e) {
            log.warn("Could not remove output after directory identity changed", e);
        }
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

    private String safeMessage(
            Exception exception,
            Path... sensitivePaths) {
        String message = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
        return sanitizeDiagnostic(message, sensitivePaths);
    }

    private String sanitizeDiagnostic(
            String diagnostic,
            Path... sensitivePaths) {
        if (diagnostic == null || diagnostic.isBlank()) {
            return "";
        }

        String sanitized = diagnostic;
        List<String> redactions = new ArrayList<>();
        redactions.add(managedWorkspaceRoot.toString());
        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isBlank()) {
            redactions.add(Path.of(userHome).toAbsolutePath().normalize().toString());
        }
        for (Path sensitivePath : sensitivePaths) {
            if (sensitivePath != null) {
                redactions.add(sensitivePath.toAbsolutePath().normalize().toString());
            }
        }
        redactions.sort(Comparator.comparingInt(String::length).reversed());
        for (String redaction : redactions) {
            if (!redaction.isBlank()) {
                sanitized = sanitized.replace(redaction, "<managed-path>");
                sanitized = sanitized.replace(
                        redaction.replace('\\', '/'),
                        "<managed-path>");
            }
        }

        StringBuilder printable = new StringBuilder(sanitized.length());
        sanitized.codePoints().forEach(codePoint -> {
            if (Character.isISOControl(codePoint)) {
                printable.append(' ');
            } else {
                printable.appendCodePoint(codePoint);
            }
        });
        String compact = printable.toString().replaceAll("\\s+", " ").trim();
        if (compact.length() <= MAX_MODEL_DIAGNOSTIC_CHARS) {
            return compact;
        }
        return compact.substring(0, MAX_MODEL_DIAGNOSTIC_CHARS - 1) + "…";
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

    private record ResolvedInput(
            Path relativePath,
            Path canonicalPath,
            long size) {
    }

    private record DirectoryIdentity(Object fileKey, FileTime creationTime) {
        private boolean matches(BasicFileAttributes attributes) {
            if (fileKey != null || attributes.fileKey() != null) {
                return fileKey != null && fileKey.equals(attributes.fileKey());
            }
            return creationTime.equals(attributes.creationTime());
        }
    }

    private record SecureFileParent(
            SecureDirectoryStream<Path> directory,
            Path fileName) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            directory.close();
        }
    }

    private static final class IdentityBoundDirectoryStream
            implements SecureDirectoryStream<Path> {
        private final Path directory;
        private final DirectoryStream<Path> delegate;
        private final String label;
        private final DirectoryIdentity identity;

        private IdentityBoundDirectoryStream(
                Path directory,
                DirectoryStream<Path> delegate,
                String label) throws IOException {
            this.directory = directory.toAbsolutePath().normalize();
            this.delegate = delegate;
            this.label = label;
            BasicFileAttributes attributes = Files.readAttributes(
                    this.directory,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isDirectory()) {
                delegate.close();
                throw new PathRestrictionException(
                        "The " + label + " is not a stable directory.");
            }
            this.identity = new DirectoryIdentity(
                    attributes.fileKey(),
                    attributes.creationTime());
        }

        @Override
        public SecureDirectoryStream<Path> newDirectoryStream(
                Path path,
                LinkOption... options) throws IOException {
            verifyCurrent();
            Path child = resolve(path);
            requireNoSymlinkComponents(path);
            DirectoryStream<Path> childStream = Files.newDirectoryStream(child);
            verifyCurrent();
            if (childStream instanceof SecureDirectoryStream<?>) {
                return castSecureDirectoryStream(childStream);
            }
            return new IdentityBoundDirectoryStream(child, childStream, label);
        }

        @Override
        public SeekableByteChannel newByteChannel(
                Path path,
                Set<? extends OpenOption> options,
                FileAttribute<?>... attributes) throws IOException {
            verifyCurrent();
            Path file = resolve(path);
            requireNoSymlinkComponents(path);
            SeekableByteChannel channel = Files.newByteChannel(
                    file,
                    options,
                    attributes);
            try {
                verifyCurrent();
                return channel;
            } catch (IOException e) {
                channel.close();
                throw e;
            }
        }

        @Override
        public void deleteFile(Path path) throws IOException {
            verifyCurrent();
            Files.delete(resolve(path));
            verifyCurrent();
        }

        @Override
        public void deleteDirectory(Path path) throws IOException {
            verifyCurrent();
            Files.delete(resolve(path));
            verifyCurrent();
        }

        @Override
        public void move(
                Path sourcePath,
                SecureDirectoryStream<Path> targetDirectory,
                Path targetPath) throws IOException {
            if (!(targetDirectory instanceof IdentityBoundDirectoryStream target)) {
                throw new IOException(
                        "Cannot bind a move across incompatible directory providers.");
            }
            verifyCurrent();
            target.verifyCurrent();
            Path source = resolve(sourcePath);
            Path targetFile = target.resolve(targetPath);
            requireNoSymlinkComponents(sourcePath);
            target.requireNoSymlinkComponents(targetPath);
            Files.move(source, targetFile);
            verifyCurrent();
            target.verifyCurrent();
        }

        @Override
        public <V extends FileAttributeView> V getFileAttributeView(
                Class<V> type) {
            return Files.getFileAttributeView(
                    directory,
                    type,
                    LinkOption.NOFOLLOW_LINKS);
        }

        @Override
        public <V extends FileAttributeView> V getFileAttributeView(
                Path path,
                Class<V> type,
                LinkOption... options) {
            return Files.getFileAttributeView(
                    resolveUnchecked(path),
                    type,
                    LinkOption.NOFOLLOW_LINKS);
        }

        @Override
        public Iterator<Path> iterator() {
            return delegate.iterator();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        private void verifyCurrent() throws IOException {
            BasicFileAttributes current = Files.readAttributes(
                    directory,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!current.isDirectory() || !identity.matches(current)) {
                throw new PathRestrictionException(
                        "The " + label + " changed during audio processing.");
            }
        }

        private Path resolve(Path relativePath) throws PathRestrictionException {
            Path normalized = relativePath.normalize();
            if (normalized.isAbsolute() || normalized.startsWith("..")) {
                throw new PathRestrictionException(
                        "A filesystem operation escaped the " + label + ".");
            }
            Path resolved = directory.resolve(normalized).normalize();
            if (!resolved.startsWith(directory)) {
                throw new PathRestrictionException(
                        "A filesystem operation escaped the " + label + ".");
            }
            return resolved;
        }

        private Path resolveUnchecked(Path relativePath) {
            try {
                return resolve(relativePath);
            } catch (PathRestrictionException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        }

        private void requireNoSymlinkComponents(Path relativePath)
                throws IOException {
            Path normalized = relativePath.normalize();
            Path current = directory;
            for (Path segment : normalized) {
                if (".".equals(segment.toString())) {
                    continue;
                }
                current = current.resolve(segment);
                if (Files.isSymbolicLink(current)) {
                    throw new PathRestrictionException(
                            "A symlink cannot be used inside the " + label + ".");
                }
            }
        }

        @SuppressWarnings("unchecked")
        private static SecureDirectoryStream<Path> castSecureDirectoryStream(
                DirectoryStream<Path> stream) {
            return (SecureDirectoryStream<Path>) stream;
        }
    }

    private static final class CopyBudget {
        private final long maximumBytes;
        private long copiedBytes;

        private CopyBudget(long maximumBytes) {
            this.maximumBytes = maximumBytes;
        }

        private void add(long bytes) throws PathRestrictionException {
            try {
                copiedBytes = Math.addExact(copiedBytes, bytes);
            } catch (ArithmeticException e) {
                throw new PathRestrictionException(
                        "Audio files exceed the aggregate input limit of 250 MiB.");
            }
            if (copiedBytes > maximumBytes) {
                throw new PathRestrictionException(
                        "Audio files exceed the aggregate input limit of 250 MiB.");
            }
        }
    }

    private record ExecutionResult(
            int exitCode,
            String output,
            boolean timedOut,
            boolean outputLimitExceeded) {
    }

    private static final class PathRestrictionException extends IOException {
        private PathRestrictionException(String message) {
            super(message);
        }
    }
}
