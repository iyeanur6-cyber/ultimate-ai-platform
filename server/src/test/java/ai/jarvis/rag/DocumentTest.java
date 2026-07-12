package ai.ultimate.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Document Entity Tests")
class DocumentTest {

    @Test
    @DisplayName("create() sets PENDING status by default")
    void createShouldSetPendingStatus() {
        Document doc = Document.create(
                UUID.randomUUID(),
                "contract.pdf",
                DocumentFileType.PDF,
                1024L,
                "My contract"
        );

        assertThat(doc.id()).isNotNull();
        assertThat(doc.status())
                .isEqualTo(DocumentStatus.PENDING);
        assertThat(doc.chunkCount()).isEqualTo(0);
        assertThat(doc.errorMessage()).isNull();
        assertThat(doc.filename())
                .isEqualTo("contract.pdf");
        assertThat(doc.fileType())
                .isEqualTo(DocumentFileType.PDF);
    }

    @Test
    @DisplayName("withProcessing() transitions to PROCESSING")
    void withProcessingShouldTransitionStatus() {
        Document doc = Document.create(
                UUID.randomUUID(), "notes.txt",
                DocumentFileType.TXT, 512L, null);

        Document processing = doc.withProcessing();

        assertThat(processing.status())
                .isEqualTo(DocumentStatus.PROCESSING);
        // Original unchanged (immutable)
        assertThat(doc.status())
                .isEqualTo(DocumentStatus.PENDING);
    }

    @Test
    @DisplayName("withReady() sets chunk count")
    void withReadyShouldSetChunkCount() {
        Document doc = Document.create(
                UUID.randomUUID(), "report.pdf",
                DocumentFileType.PDF, 50000L, null);

        Document ready = doc.withReady(47);

        assertThat(ready.status())
                .isEqualTo(DocumentStatus.READY);
        assertThat(ready.chunkCount()).isEqualTo(47);
        assertThat(ready.errorMessage()).isNull();
    }

    @Test
    @DisplayName("withFailed() stores error message")
    void withFailedShouldStoreError() {
        Document doc = Document.create(
                UUID.randomUUID(), "corrupt.pdf",
                DocumentFileType.PDF, 1L, null);

        Document failed = doc.withFailed(
                "PDF is password protected");

        assertThat(failed.status())
                .isEqualTo(DocumentStatus.FAILED);
        assertThat(failed.errorMessage())
                .isEqualTo("PDF is password protected");
        // Chunk count unchanged
        assertThat(failed.chunkCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("DocumentChunk.create() sets defaults")
    void chunkCreateShouldSetDefaults() {
        UUID documentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        DocumentChunk chunk = DocumentChunk.create(
                documentId,
                userId,
                "This is the content of chunk 1",
                0,
                1,
                75
        );

        assertThat(chunk.id()).isNotNull();
        assertThat(chunk.documentId())
                .isEqualTo(documentId);
        assertThat(chunk.userId())
                .isEqualTo(userId);
        assertThat(chunk.chunkIndex()).isEqualTo(0);
        assertThat(chunk.pageNumber()).isEqualTo(1);
        assertThat(chunk.tokenCount()).isEqualTo(75);
        assertThat(chunk.createdAt()).isNotNull();
    }
}