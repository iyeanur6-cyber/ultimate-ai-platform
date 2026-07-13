/* ABOUTME: Verifies Spring auto-discovery for the SoX audio processing tool. */
package ai.ultimate.tools;

import ai.ultimate.tools.builtin.AudioProcessingTool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AudioProcessingTool registry integration")
class AudioProcessingToolRegistryTest {

    @Test
    @DisplayName("auto-discovers the audio processing tool")
    void shouldAutoDiscoverAudioProcessingTool() {
        try (var context = new AnnotationConfigApplicationContext(
                AudioProcessingTool.class)) {
            assertThat(context.getBean(AudioProcessingTool.class)).isNotNull();
        }
    }
}
