package ai.ultimate.rag;

/**
 * Supported document file types for RAG processing.
 *
 * PDF:      Adobe PDF format.
 *           Requires Apache PDFBox for text extraction.
 *           Most common document format.
 *
 * TXT:      Plain text files.
 *           No special parsing needed.
 *           Fastest to process.
 *
 * MARKDOWN: Markdown formatted text.
 *           Strip markdown syntax before chunking.
 *           Common for developer notes/docs.
 */
public enum DocumentFileType {
    PDF,
    TXT,
    MARKDOWN
}