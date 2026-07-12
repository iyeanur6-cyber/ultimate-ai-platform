package ai.ultimate.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Memory Entity Tests")
class MemoryTest {

    @Test
    @DisplayName("create() should set default values")
    void createShouldSetDefaults() {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        Memory memory = Memory.create(
                userId,
                MemoryType.FACT,
                "User's name is Dravin",
                sessionId
        );

        assertThat(memory.id()).isNotNull();
        assertThat(memory.userId()).isEqualTo(userId);
        assertThat(memory.type()).isEqualTo(MemoryType.FACT);
        assertThat(memory.content())
                .isEqualTo("User's name is Dravin");
        assertThat(memory.sourceSession())
                .isEqualTo(sessionId);
        assertThat(memory.importance()).isEqualTo(0.50);
        assertThat(memory.accessCount()).isEqualTo(0);
        assertThat(memory.lastAccessed()).isNull();
        assertThat(memory.createdAt()).isNotNull();
        assertThat(memory.updatedAt()).isNotNull();
    }

    @Test
    @DisplayName("createManual() should have null session")
    void createManualShouldHaveNullSession() {
        Memory memory = Memory.createManual(
                UUID.randomUUID(),
                MemoryType.PREFERENCE,
                "Prefers dark mode"
        );

        assertThat(memory.sourceSession()).isNull();
        assertThat(memory.type())
                .isEqualTo(MemoryType.PREFERENCE);
    }

    @Test
    @DisplayName("withAccessed() should increment count")
    void withAccessedShouldIncrement() {
        Memory original = Memory.create(
                UUID.randomUUID(),
                MemoryType.GOAL,
                "Learn Spring AI",
                null
        );

        assertThat(original.accessCount()).isEqualTo(0);
        assertThat(original.lastAccessed()).isNull();

        Memory accessed = original.withAccessed();

        assertThat(accessed.accessCount()).isEqualTo(1);
        assertThat(accessed.lastAccessed()).isNotNull();
        // Original unchanged (immutable record)
        assertThat(original.accessCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("withImportance() should update score")
    void withImportanceShouldUpdate() {
        Memory original = Memory.create(
                UUID.randomUUID(),
                MemoryType.FACT,
                "Uses Java 21",
                null
        );

        assertThat(original.importance()).isEqualTo(0.50);

        Memory boosted = original.withImportance(0.95);

        assertThat(boosted.importance()).isEqualTo(0.95);
        // Original unchanged
        assertThat(original.importance()).isEqualTo(0.50);
    }

    @Test
    @DisplayName("All MemoryType values should exist")
    void allMemoryTypesShouldExist() {
        assertThat(MemoryType.values()).containsExactly(
                MemoryType.FACT,
                MemoryType.GOAL,
                MemoryType.PREFERENCE,
                MemoryType.CONTEXT,
                MemoryType.EVENT
        );
    }
}