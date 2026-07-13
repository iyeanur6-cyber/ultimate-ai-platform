/* ABOUTME: Contract tests for bounded, concurrent SoX audio processing. */
package ai.jarvis.tools.builtin;

import ai.ultimate.tools.builtin.AudioProcessingTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AudioProcessingTool Tests")
class AudioProcessingToolTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("normalizes audio with argv-only execution and bounded stream drains")
    void shouldNormalizeAudioAndDrainProcessStreams() throws IOException {
        Fixture fixture = fixture("voice.wav");

        String result = fixture.tool.processAudio(
                fixture.workspace.toString(),
                "voice.wav",
                "results",
                "normalized",
                "normalize",
                "wav",
                "",
                -3.0,
                0.0,
                0.0);

        assertThat(result)
                .contains("Audio processing completed")
                .contains("stdout ready")
                .contains("stderr ready");
        assertThat(fixture.executor.commands.getFirst())
                .startsWith("sox")
                .containsSubsequence("gain", "-n", "-3")
                .doesNotContain("sh", "bash", "-c");
        assertThat(fixture.workspace.resolve("results/normalized.wav"))
                .hasContent("processed");
        assertNoRuntimeDirectories(fixture.workspace);
    }

    @Test
    @DisplayName("builds noise profile and reduction pipelines")
    void shouldBuildNoisePipelines() throws IOException {
        Fixture fixture = fixture("voice.wav", "profile.noise-profile");

        String profile = fixture.tool.processAudio(
                fixture.workspace.toString(),
                "voice.wav",
                "results",
                "room",
                "noise-profile",
                "wav",
                "",
                0.0,
                0.0,
                0.0);
        String reduce = fixture.tool.processAudio(
                fixture.workspace.toString(),
                "voice.wav",
                "results",
                "clean",
                "noise-reduce",
                "wav",
                "profile.noise-profile",
                0.21,
                0.0,
                0.0);

        assertThat(profile).contains("results/room.noise-profile");
        assertThat(reduce).contains("results/clean.wav");
        assertThat(fixture.executor.commands.get(0))
                .containsSubsequence("-n", "noiseprof");
        assertThat(fixture.executor.commands.get(1))
                .containsSubsequence("noisered")
                .contains("0.21");
    }

    @Test
    @DisplayName("builds pitch, tempo, split, and concatenate pipelines")
    void shouldBuildRemainingFeaturePipelines() throws IOException {
        Fixture fixture = fixture("first.wav", "second.wav");

        fixture.tool.processAudio(
                fixture.workspace.toString(), "first.wav", "results", "pitched",
                "pitch", "wav", "", 200.0, 0.0, 0.0);
        fixture.tool.processAudio(
                fixture.workspace.toString(), "first.wav", "results", "faster",
                "tempo", "wav", "", 1.25, 0.0, 0.0);
        fixture.tool.processAudio(
                fixture.workspace.toString(), "first.wav", "results", "clip",
                "split", "wav", "", 0.0, 5.0, 12.5);
        fixture.tool.processAudio(
                fixture.workspace.toString(), "first.wav,second.wav", "results", "joined",
                "concatenate", "wav", "", 0.0, 0.0, 0.0);

        assertThat(fixture.executor.commands.get(0))
                .containsSubsequence("pitch", "200");
        assertThat(fixture.executor.commands.get(1))
                .containsSubsequence("tempo", "-s", "1.25");
        assertThat(fixture.executor.commands.get(2))
                .containsSubsequence("trim", "5", "12.5");
        assertThat(fixture.executor.commands.get(3))
                .satisfies(command -> {
                    assertThat(command.stream().filter(value -> value.endsWith("input-01.wav")))
                            .hasSize(1);
                    assertThat(command.stream().filter(value -> value.endsWith("input-02.wav")))
                            .hasSize(1);
                });
    }

    @Test
    @DisplayName("rejects paths that escape the managed workspace")
    void shouldRejectPathTraversal() throws IOException {
        Fixture fixture = fixture("voice.wav");
        Files.writeString(fixture.managedRoot.resolve("outside.wav"), "outside");

        String result = fixture.tool.processAudio(
                fixture.workspace.toString(),
                "../outside.wav",
                "results",
                "escaped",
                "normalize",
                "wav",
                "",
                -3.0,
                0.0,
                0.0);

        assertThat(result).contains("Path Restriction");
        assertThat(fixture.executor.commands).isEmpty();
    }

    @Test
    @DisplayName("terminates timed-out SoX processing and purges runtime files")
    void shouldTerminateTimeoutAndCleanRuntimeFiles() throws IOException {
        Fixture fixture = fixture(false, "voice.wav");

        String result = fixture.tool.processAudio(
                fixture.workspace.toString(),
                "voice.wav",
                "results",
                "normalized",
                "normalize",
                "wav",
                "",
                -3.0,
                0.0,
                0.0);

        assertThat(result).contains("runtime limit");
        assertThat(fixture.executor.process.destroyed).isTrue();
        assertNoRuntimeDirectories(fixture.workspace);
    }

    @Test
    @DisplayName("keeps concurrent calls isolated")
    void shouldProcessConcurrentCallsIndependently() throws Exception {
        Fixture fixture = fixture("first.wav", "second.wav");

        try (var calls = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = calls.submit(() -> fixture.tool.processAudio(
                    fixture.workspace.toString(), "first.wav", "results", "first-out",
                    "normalize", "wav", "", -3.0, 0.0, 0.0));
            var second = calls.submit(() -> fixture.tool.processAudio(
                    fixture.workspace.toString(), "second.wav", "results", "second-out",
                    "normalize", "wav", "", -3.0, 0.0, 0.0));

            assertThat(first.get(5, TimeUnit.SECONDS))
                    .contains("Audio processing completed");
            assertThat(second.get(5, TimeUnit.SECONDS))
                    .contains("Audio processing completed");
        }

        assertThat(fixture.workspace.resolve("results/first-out.wav")).exists();
        assertThat(fixture.workspace.resolve("results/second-out.wav")).exists();
        assertThat(fixture.executor.commands).hasSize(2);
    }

    @Test
    @DisplayName("preserves an output created concurrently before publication")
    void shouldPreserveConcurrentlyCreatedOutput() throws IOException {
        Path managedRoot = Files.createDirectory(
                temporaryDirectory.resolve("managed-race"));
        Path workspace = Files.createDirectory(managedRoot.resolve("workspace"));
        Files.writeString(workspace.resolve("voice.wav"), "voice");
        Path outputDirectory = Files.createDirectory(workspace.resolve("results"));
        Path concurrentOutput = outputDirectory.resolve("normalized.wav");

        AudioProcessingTool.ProcessExecutor executor = (command, environment) -> {
            for (String argument : command) {
                Path candidate;
                try {
                    candidate = Path.of(argument);
                } catch (RuntimeException ignored) {
                    continue;
                }
                if (candidate.getFileName() != null
                        && candidate.getFileName().toString().startsWith("output-")) {
                    Files.writeString(candidate, "processed");
                }
            }
            Files.writeString(concurrentOutput, "concurrent");
            return new FakeProcess(true);
        };
        AudioProcessingTool tool = new AudioProcessingTool(
                managedRoot,
                executor,
                () -> Optional.of("sox"));

        String result = tool.processAudio(
                workspace.toString(), "voice.wav", "results", "normalized",
                "normalize", "wav", "", -3.0, 0.0, 0.0);

        assertThat(result).contains("Audio Processing Failure");
        assertThat(concurrentOutput).hasContent("concurrent");
    }

    @Test
    @DisplayName("validates operation-specific values before locating SoX")
    void shouldRejectInvalidOperationValues() throws IOException {
        Fixture fixture = fixture("voice.wav");

        String result = fixture.tool.processAudio(
                fixture.workspace.toString(),
                "voice.wav",
                "results",
                "too-fast",
                "tempo",
                "wav",
                "",
                5.0,
                0.0,
                0.0);

        assertThat(result)
                .contains("Validation Error")
                .contains("tempo");
        assertThat(fixture.executor.commands).isEmpty();
    }

    @Test
    @DisplayName("rejects aggregate input above the request budget")
    void shouldRejectAggregateInputAboveRequestBudget() throws IOException {
        Fixture fixture = fixture("first.wav", "second.wav", "third.wav");
        makeSparse(fixture.workspace.resolve("first.wav"), 90L * 1024L * 1024L);
        makeSparse(fixture.workspace.resolve("second.wav"), 90L * 1024L * 1024L);
        makeSparse(fixture.workspace.resolve("third.wav"), 90L * 1024L * 1024L);

        String result = fixture.tool.processAudio(
                fixture.workspace.toString(),
                "first.wav,second.wav,third.wav",
                "results",
                "joined",
                "concatenate",
                "wav",
                "",
                0.0,
                0.0,
                0.0);

        assertThat(result).contains("aggregate input limit");
        assertThat(fixture.executor.commands).isEmpty();
    }

    @Test
    @DisplayName("terminates processing when generated output exceeds its byte budget")
    void shouldTerminateOversizedGeneratedOutput() throws IOException {
        Path managedRoot = Files.createDirectory(
                temporaryDirectory.resolve("managed-output-limit"));
        Path workspace = Files.createDirectory(managedRoot.resolve("workspace"));
        Files.createDirectory(workspace.resolve("results"));
        Files.writeString(workspace.resolve("voice.wav"), "voice");
        FakeProcess oversizedProcess = new FakeProcess(false);
        AudioProcessingTool.ProcessExecutor executor = (command, environment) -> {
            Path output = outputPathFrom(command);
            makeSparse(output, 513L * 1024L * 1024L);
            return oversizedProcess;
        };
        AudioProcessingTool tool = new AudioProcessingTool(
                managedRoot,
                executor,
                () -> Optional.of("sox"));

        String result = tool.processAudio(
                workspace.toString(), "voice.wav", "results", "normalized",
                "normalize", "wav", "", -3.0, 0.0, 0.0);

        assertThat(result).contains("generated output limit");
        assertThat(oversizedProcess.destroyed).isTrue();
        assertThat(workspace.resolve("results/normalized.wav")).doesNotExist();
    }

    @Test
    @DisplayName("sanitizes and caps process diagnostics returned to the model")
    void shouldSanitizeProcessDiagnostics() throws IOException {
        Path managedRoot = Files.createDirectory(
                temporaryDirectory.resolve("managed-diagnostics"));
        Path workspace = Files.createDirectory(managedRoot.resolve("workspace"));
        Files.createDirectory(workspace.resolve("results"));
        Files.writeString(workspace.resolve("voice.wav"), "voice");
        String diagnostic = "failed at "
                + workspace.resolve("private.wav")
                + "\n\u0000"
                + "x".repeat(2_000);
        AudioProcessingTool.ProcessExecutor executor = (command, environment) ->
                new FakeProcess(true, 7, "", diagnostic);
        AudioProcessingTool tool = new AudioProcessingTool(
                managedRoot,
                executor,
                () -> Optional.of("sox"));

        String result = tool.processAudio(
                workspace.toString(), "voice.wav", "results", "normalized",
                "normalize", "wav", "", -3.0, 0.0, 0.0);

        assertThat(result)
                .contains("SoX exited with code 7")
                .doesNotContain(workspace.toString())
                .doesNotContain("\n", "\r", "\u0000");
        assertThat(result.length()).isLessThan(1_100);
    }

    @Test
    @DisplayName("caps caller-controlled duplicate path diagnostics")
    void shouldCapDuplicatePathDiagnostic() throws IOException {
        Fixture fixture = fixture("voice.wav");
        String callerPath = "a".repeat(2_000) + ".wav";

        String result = fixture.tool.processAudio(
                fixture.workspace.toString(),
                callerPath + "," + callerPath,
                "results",
                "normalized",
                "normalize",
                "wav",
                "",
                -3.0,
                0.0,
                0.0);

        assertThat(result)
                .contains("Duplicate input path")
                .doesNotContain(callerPath);
        assertThat(result.length()).isLessThan(200);
    }

    @Test
    @DisplayName("does not publish through an output directory replaced by a symlink")
    void shouldRejectOutputDirectorySwapBeforePublication() throws IOException {
        Path managedRoot = Files.createDirectory(
                temporaryDirectory.resolve("managed-output-swap"));
        Path workspace = Files.createDirectory(managedRoot.resolve("workspace"));
        Files.writeString(workspace.resolve("voice.wav"), "voice");
        Path outputDirectory = Files.createDirectory(workspace.resolve("results"));
        Path outsideDirectory = Files.createDirectory(
                temporaryDirectory.resolve("outside-output-swap"));

        AudioProcessingTool.ProcessExecutor executor = (command, environment) -> {
            Files.writeString(outputPathFrom(command), "processed");
            Files.delete(outputDirectory);
            Files.createSymbolicLink(outputDirectory, outsideDirectory);
            return new FakeProcess(true);
        };
        AudioProcessingTool tool = new AudioProcessingTool(
                managedRoot,
                executor,
                () -> Optional.of("sox"));

        String result = tool.processAudio(
                workspace.toString(), "voice.wav", "results", "normalized",
                "normalize", "wav", "", -3.0, 0.0, 0.0);

        assertThat(result).contains("Path Restriction");
        assertThat(outsideDirectory.resolve("normalized.wav")).doesNotExist();
    }

    private static Path outputPathFrom(List<String> command) {
        return command.stream()
                .map(value -> {
                    try {
                        return Path.of(value);
                    } catch (RuntimeException ignored) {
                        return null;
                    }
                })
                .filter(path -> path != null && path.getFileName() != null)
                .filter(path -> path.getFileName().toString().startsWith("output-"))
                .findFirst()
                .orElseThrow();
    }

    private static void makeSparse(Path path, long size) throws IOException {
        try (SeekableByteChannel channel = Files.newByteChannel(
                path,
                StandardOpenOption.WRITE)) {
            channel.position(size - 1);
            channel.write(ByteBuffer.wrap(new byte[] {0}));
        }
    }

    private Fixture fixture(String... inputNames) throws IOException {
        return fixture(true, inputNames);
    }

    private Fixture fixture(boolean completes, String... inputNames) throws IOException {
        Path managedRoot = Files.createDirectory(temporaryDirectory.resolve(
                "managed-" + System.nanoTime()));
        Path workspace = Files.createDirectory(managedRoot.resolve("workspace"));
        Files.createDirectory(workspace.resolve("results"));
        for (String inputName : inputNames) {
            Files.writeString(workspace.resolve(inputName), inputName);
        }
        FakeProcessExecutor executor = new FakeProcessExecutor(completes);
        AudioProcessingTool tool = new AudioProcessingTool(
                managedRoot,
                executor,
                () -> Optional.of("sox"));
        return new Fixture(managedRoot, workspace, executor, tool);
    }

    private void assertNoRuntimeDirectories(Path workspace) throws IOException {
        try (var paths = Files.list(workspace)) {
            assertThat(paths.map(path -> path.getFileName().toString()))
                    .noneMatch(name -> name.startsWith(".ultimate-audio-"));
        }
    }

    private record Fixture(
            Path managedRoot,
            Path workspace,
            FakeProcessExecutor executor,
            AudioProcessingTool tool) {
    }

    private static final class FakeProcessExecutor
            implements AudioProcessingTool.ProcessExecutor {
        private final boolean completes;
        private final List<List<String>> commands =
                Collections.synchronizedList(new ArrayList<>());
        private FakeProcess process;

        private FakeProcessExecutor(boolean completes) {
            this.completes = completes;
        }

        @Override
        public Process start(List<String> command, Map<String, String> environment)
                throws IOException {
            commands.add(List.copyOf(command));
            for (String argument : command) {
                Path candidate;
                try {
                    candidate = Path.of(argument);
                } catch (RuntimeException ignored) {
                    continue;
                }
                if (candidate.getFileName() != null
                        && candidate.getFileName().toString().startsWith("output-")) {
                    Files.writeString(candidate, "processed", StandardCharsets.UTF_8);
                }
            }
            process = new FakeProcess(completes);
            return process;
        }
    }

    private static final class FakeProcess extends Process {
        private final boolean completes;
        private final int exitCode;
        private final byte[] stdout;
        private final byte[] stderr;
        private boolean destroyed;

        private FakeProcess(boolean completes) {
            this(completes, 0, "stdout ready", "stderr ready");
        }

        private FakeProcess(
                boolean completes,
                int exitCode,
                String stdout,
                String stderr) {
            this.completes = completes;
            this.exitCode = exitCode;
            this.stdout = stdout.getBytes(StandardCharsets.UTF_8);
            this.stderr = stderr.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(stdout);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(stderr);
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            return completes;
        }

        @Override
        public int exitValue() {
            return exitCode;
        }

        @Override
        public void destroy() {
            destroyed = true;
        }

        @Override
        public Process destroyForcibly() {
            destroyed = true;
            return this;
        }

        @Override
        public boolean isAlive() {
            return !destroyed && !completes;
        }
    }
}
