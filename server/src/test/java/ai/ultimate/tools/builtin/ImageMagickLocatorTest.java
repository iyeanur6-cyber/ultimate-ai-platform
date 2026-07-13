/* ABOUTME: Lifecycle tests for ImageMagick executable discovery. */
package ai.ultimate.tools.builtin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ImageMagick executable locator")
class ImageMagickLocatorTest {

    @Test
    @DisplayName("terminates a lookup process that exceeds its timeout")
    void shouldTerminateTimedOutLookupProcess() {
        TimedOutProcess process = new TimedOutProcess();

        ImageProcessingTool.locateImageMagickBinary(
                "which",
                List.of("magick"),
                command -> process);

        assertThat(process.destroyed).isTrue();
        assertThat(process.forciblyDestroyed).isTrue();
    }

    private static final class TimedOutProcess extends Process {
        private boolean destroyed;
        private boolean forciblyDestroyed;

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
            return 0;
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            return false;
        }

        @Override
        public int exitValue() {
            throw new IllegalThreadStateException("Process is still running.");
        }

        @Override
        public void destroy() {
            // Simulate a child that ignores the soft termination request.
        }

        @Override
        public Process destroyForcibly() {
            destroyed = true;
            forciblyDestroyed = true;
            return this;
        }

        @Override
        public boolean isAlive() {
            return !destroyed;
        }
    }
}
