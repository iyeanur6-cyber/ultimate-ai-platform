package ai.ultimate.ai;

import ai.ultimate.ai.prompt.WorkingMemoryBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WorkingMemoryBuilder Tests")
class WorkingMemoryBuilderTest {

    private WorkingMemoryBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new WorkingMemoryBuilder();
    }

    @Test
    @DisplayName("Should include username")
    void shouldIncludeUsername() {
        String memory = builder.build(
                "dravin", "ADMIN",
                "session-123", "llama3.1:8b");

        assertThat(memory).contains("dravin");
    }

    @Test
    @DisplayName("Should include role")
    void shouldIncludeRole() {
        String memory = builder.build(
                "dravin", "ADMIN",
                "session-123", "llama3.1:8b");

        assertThat(memory).contains("ADMIN");
    }

    @Test
    @DisplayName("Should include model name")
    void shouldIncludeModelName() {
        String memory = builder.build(
                "dravin", "ADMIN",
                "session-123", "llama3.1:8b");

        assertThat(memory).contains("llama3.1:8b");
    }

    @Test
    @DisplayName("Should include session ID")
    void shouldIncludeSessionId() {
        String sessionId = "test-session-456";
        String memory = builder.build(
                "dravin", "ADMIN",
                sessionId, "llama3.1:8b");

        assertThat(memory).contains(sessionId);
    }

    @Test
    @DisplayName("Should include date/time")
    void shouldIncludeDatetime() {
        String memory = builder.build(
                "dravin", "ADMIN",
                "session-123", "llama3.1:8b");

        // Should contain year 2026
        assertThat(memory).contains("2026");
    }

    @Test
    @DisplayName("Should not be empty")
    void shouldNotBeEmpty() {
        String memory = builder.build(
                "user", "USER",
                "session-1", "phi3:mini");

        assertThat(memory).isNotBlank();
        assertThat(memory.length())
                .isGreaterThan(50);
    }
}