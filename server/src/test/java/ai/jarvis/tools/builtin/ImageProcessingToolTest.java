/* ABOUTME: Contract tests for safe, bounded ImageMagick batch processing. */
package ai.jarvis.tools.builtin;

import ai.ultimate.tools.builtin.ImageProcessingTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ImageProcessingTool Tests")
class ImageProcessingToolTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("processes a batch resize with argv-only commands and cleanup")
    void shouldProcessBatchResizeAndCleanRuntimeFiles() throws IOException {
        Path managedRoot = Files.createDirectory(temporaryDirectory.resolve("managed"));
        Path workspace = Files.createDirectory(managedRoot.resolve("workspace"));
        Files.writeString(workspace.resolve("first.png"), "first");
        Files.writeString(workspace.resolve("second.jpg"), "second");
        Files.createDirectory(workspace.resolve("results"));

        FakeProcessExecutor executor = new FakeProcessExecutor(true, 0);
        ImageProcessingTool tool = new ImageProcessingTool(
                managedRoot,
                executor,
                () -> Optional.of("magick"));

        String result = tool.processImages(
                workspace.toString(),
                "first.png,second.jpg",
                "results",
                "resize",
                "webp",
                320,
                240,
                "",
                82);

        assertThat(result)
                .contains("Image processing completed")
                .contains("2 file(s)");
        assertThat(executor.commands).hasSize(2);
        assertThat(executor.commands)
                .allSatisfy(command -> {
                    assertThat(command)
                            .startsWith("magick")
                            .containsSubsequence(
                                    "-limit", "memory", "256MiB",
                                    "-limit", "map", "512MiB",
                                    "-limit", "disk", "1GiB")
                            .contains("-resize", "320x240")
                            .doesNotContain("sh", "bash", "-c");
                });
        assertThat(workspace.resolve("results/01-first-resize.webp"))
                .hasContent("processed");
        assertThat(workspace.resolve("results/02-second-resize.webp"))
                .hasContent("processed");
        try (var files = Files.list(workspace)) {
            assertThat(files.map(path -> path.getFileName().toString()))
                    .noneMatch(name -> name.startsWith(".ultimate-image-"));
        }
    }

    @Test
    @DisplayName("rejects input paths that escape the managed workspace")
    void shouldRejectPathTraversal() throws IOException {
        Path managedRoot = Files.createDirectory(temporaryDirectory.resolve("managed"));
        Path workspace = Files.createDirectory(managedRoot.resolve("workspace"));
        Files.createDirectory(workspace.resolve("results"));
        Files.writeString(managedRoot.resolve("outside.png"), "outside");

        FakeProcessExecutor executor = new FakeProcessExecutor(true, 0);
        ImageProcessingTool tool = new ImageProcessingTool(
                managedRoot,
                executor,
                () -> Optional.of("magick"));

        String result = tool.processImages(
                workspace.toString(),
                "../outside.png",
                "results",
                "convert",
                "webp",
                0,
                0,
                "",
                82);

        assertThat(result).contains("Path Restriction");
        assertThat(executor.commands).isEmpty();
    }

    @Test
    @DisplayName("uses explicit metadata stripping arguments")
    void shouldBuildMetadataStripCommand() throws IOException {
        Path managedRoot = Files.createDirectory(temporaryDirectory.resolve("managed"));
        Path workspace = Files.createDirectory(managedRoot.resolve("workspace"));
        Files.writeString(workspace.resolve("photo.jpg"), "photo");
        Files.createDirectory(workspace.resolve("results"));

        FakeProcessExecutor executor = new FakeProcessExecutor(true, 0);
        ImageProcessingTool tool = new ImageProcessingTool(
                managedRoot,
                executor,
                () -> Optional.of("magick"));

        String result = tool.processImages(
                workspace.toString(),
                "photo.jpg",
                "results",
                "strip-metadata",
                "jpg",
                0,
                0,
                "",
                90);

        assertThat(result).contains("Image processing completed");
        assertThat(executor.commands.getFirst()).contains("-strip");
    }

    @Test
    @DisplayName("builds explicit crop, color-correction, and watermark arguments")
    void shouldBuildRemainingFeatureCommands() throws IOException {
        Path managedRoot = Files.createDirectory(temporaryDirectory.resolve("managed"));
        Path workspace = Files.createDirectory(managedRoot.resolve("workspace"));
        Files.writeString(workspace.resolve("photo.jpg"), "photo");
        Files.createDirectory(workspace.resolve("results"));

        FakeProcessExecutor executor = new FakeProcessExecutor(true, 0);
        ImageProcessingTool tool = new ImageProcessingTool(
                managedRoot,
                executor,
                () -> Optional.of("magick"));

        String crop = tool.processImages(
                workspace.toString(),
                "photo.jpg",
                "results",
                "crop",
                "png",
                640,
                480,
                "",
                90);
        String color = tool.processImages(
                workspace.toString(),
                "photo.jpg",
                "results",
                "color-correct",
                "webp",
                0,
                0,
                "",
                82);
        String watermark = tool.processImages(
                workspace.toString(),
                "photo.jpg",
                "results",
                "watermark",
                "jpg",
                0,
                0,
                "UltimateAI",
                90);

        assertThat(crop).contains("Image processing completed");
        assertThat(color).contains("Image processing completed");
        assertThat(watermark).contains("Image processing completed");
        assertThat(executor.commands.get(0))
                .contains("-gravity", "center", "-crop", "640x480+0+0", "+repage");
        assertThat(executor.commands.get(1))
                .contains("-colorspace", "sRGB", "-auto-level");
        assertThat(executor.commands.get(2))
                .contains("-annotate", "+12+12", "UltimateAI");
    }

    @Test
    @DisplayName("rejects watermark text that ImageMagick could treat as a file reference")
    void shouldRejectWatermarkFileReference() throws IOException {
        Path managedRoot = Files.createDirectory(temporaryDirectory.resolve("managed"));
        FakeProcessExecutor executor = new FakeProcessExecutor(true, 0);
        ImageProcessingTool tool = new ImageProcessingTool(
                managedRoot,
                executor,
                () -> Optional.of("magick"));

        String result = tool.processImages(
                managedRoot.resolve("workspace").toString(),
                "photo.jpg",
                "results",
                "watermark",
                "jpg",
                0,
                0,
                "@watermark.txt",
                90);

        assertThat(result)
                .contains("Validation Error")
                .contains("must not start with '@'");
        assertThat(executor.commands).isEmpty();
    }

    @Test
    @DisplayName("reports a missing ImageMagick binary without starting a process")
    void shouldReportMissingBinary() throws IOException {
        Path managedRoot = Files.createDirectory(temporaryDirectory.resolve("managed"));
        FakeProcessExecutor executor = new FakeProcessExecutor(true, 0);
        ImageProcessingTool tool = new ImageProcessingTool(
                managedRoot,
                executor,
                Optional::empty);

        String result = tool.processImages(
                managedRoot.resolve("workspace").toString(),
                "photo.jpg",
                "results",
                "convert",
                "webp",
                0,
                0,
                "",
                82);

        assertThat(result)
                .contains("Environment Error")
                .contains("ImageMagick");
        assertThat(executor.commands).isEmpty();
    }

    @Test
    @DisplayName("terminates timed-out processing and purges runtime files")
    void shouldTerminateTimeoutAndCleanRuntimeFiles() throws IOException {
        Path managedRoot = Files.createDirectory(temporaryDirectory.resolve("managed"));
        Path workspace = Files.createDirectory(managedRoot.resolve("workspace"));
        Files.writeString(workspace.resolve("photo.jpg"), "photo");
        Files.createDirectory(workspace.resolve("results"));

        FakeProcessExecutor executor = new FakeProcessExecutor(false, 0);
        ImageProcessingTool tool = new ImageProcessingTool(
                managedRoot,
                executor,
                () -> Optional.of("magick"));

        String result = tool.processImages(
                workspace.toString(),
                "photo.jpg",
                "results",
                "convert",
                "webp",
                0,
                0,
                "",
                82);

        assertThat(result).contains("runtime limit");
        assertThat(executor.process.destroyed).isTrue();
        try (var files = Files.list(workspace)) {
            assertThat(files.map(path -> path.getFileName().toString()))
                    .noneMatch(name -> name.startsWith(".ultimate-image-"));
        }
    }

    @Test
    @DisplayName("rolls back published files when a batch cannot commit completely")
    void shouldRollBackPartiallyPublishedBatch() throws IOException {
        Path managedRoot = Files.createDirectory(temporaryDirectory.resolve("managed"));
        Path workspace = Files.createDirectory(managedRoot.resolve("workspace"));
        Files.writeString(workspace.resolve("first.png"), "first");
        Files.writeString(workspace.resolve("second.jpg"), "second");
        Path outputDirectory = Files.createDirectory(workspace.resolve("results"));
        Path concurrentOutput = outputDirectory.resolve("02-second-resize.webp");
        AtomicInteger invocations = new AtomicInteger();

        ImageProcessingTool.ProcessExecutor executor = (command, environment) -> {
            Files.writeString(Path.of(command.getLast()), "processed");
            if (invocations.incrementAndGet() == 2) {
                Files.writeString(concurrentOutput, "concurrent");
            }
            return new FakeProcess(true, 0);
        };
        ImageProcessingTool tool = new ImageProcessingTool(
                managedRoot,
                executor,
                () -> Optional.of("magick"));

        String result = tool.processImages(
                workspace.toString(),
                "first.png,second.jpg",
                "results",
                "resize",
                "webp",
                320,
                240,
                "",
                82);

        assertThat(result).contains("Image Processing Failure");
        assertThat(outputDirectory.resolve("01-first-resize.webp")).doesNotExist();
        assertThat(concurrentOutput).hasContent("concurrent");
    }

    private static final class FakeProcessExecutor
            implements ImageProcessingTool.ProcessExecutor {
        private final boolean completes;
        private final int exitCode;
        private final List<List<String>> commands = new ArrayList<>();
        private FakeProcess process;

        private FakeProcessExecutor(boolean completes, int exitCode) {
            this.completes = completes;
            this.exitCode = exitCode;
        }

        @Override
        public Process start(List<String> command, Map<String, String> environment)
                throws IOException {
            commands.add(List.copyOf(command));
            Path output = Path.of(command.getLast());
            Files.writeString(output, "processed", StandardCharsets.UTF_8);
            process = new FakeProcess(completes, exitCode);
            return process;
        }
    }

    private static final class FakeProcess extends Process {
        private final boolean completes;
        private final int exitCode;
        private boolean destroyed;

        private FakeProcess(boolean completes, int exitCode) {
            this.completes = completes;
            this.exitCode = exitCode;
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int waitFor() {
            return exitCode;
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
