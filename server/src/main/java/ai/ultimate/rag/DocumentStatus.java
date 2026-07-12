package ai.ultimate.rag;

/**
 * Processing status of an uploaded document.
 *
 * LIFECYCLE:
 * PENDING → PROCESSING → READY
 *                     ↘ FAILED
 *
 * PENDING:    Document uploaded, waiting to process.
 *             Chunks not yet created.
 *
 * PROCESSING: Chunking and embedding in progress.
 *             Do not search yet (incomplete).
 *
 * READY:      All chunks embedded successfully.
 *             Available for semantic search.
 *             chunk_count reflects actual chunks.
 *
 * FAILED:     Processing error occurred.
 *             See documents.error_message for details.
 *             User can retry by re-uploading.
 */
public enum DocumentStatus {
    PENDING,
    PROCESSING,
    READY,
    FAILED
}