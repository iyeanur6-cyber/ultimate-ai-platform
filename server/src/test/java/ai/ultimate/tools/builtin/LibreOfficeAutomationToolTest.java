package ai.ultimate.tools.builtin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LibreOfficeAutomationTool.
 *
 * Tests basic conversion flow and security restrictions.
 */
class LibreOfficeAutomationToolTest {

    private LibreOfficeAutomationTool tool;
    private Path managedWorkspace;
    private Path testInputFile;

    @BeforeEach
    void setUp() throws IOException {
        tool = new LibreOfficeAutomationTool();
        managedWorkspace = Path.of(System.getProperty("user.home"), "ultimate-managed-workspaces").toAbsolutePath().normalize();
        Files.createDirectories(managedWorkspace);
        testInputFile = managedWorkspace.resolve("test-document.txt");
        Files.writeString(testInputFile, "Hello World");
    }

    @AfterEach
    void tearDown() throws IOException {
        if (Files.exists(testInputFile)) {
            Files.delete(testInputFile);
        }
        Path outputFile = managedWorkspace.resolve("test-document.pdf");
        if (Files.exists(outputFile)) {
            Files.delete(outputFile);
        }
    }

    @Test
    void testConvertDocumentSuccess() {
        String result = tool.convertDocument(testInputFile.toString(), "pdf");
        assertTrue(result.startsWith("Conversion successful:"));
    }

    @Test
    void testConvertDocumentInputOutsideWorkspace() {
        String result = tool.convertDocument("/tmp/test.txt", "pdf");
        assertEquals("Security Violation: Input file must be inside managed workspace.", result);
    }

    @Test
    void testConvertDocumentInputTooLarge() throws IOException {
        Path largeFile = managedWorkspace.resolve("large-file.txt");
        byte[] largeContent = new byte[6 * 1024 * 1024]; // 6MB
        Files.write(largeFile, largeContent);
        String result = tool.convertDocument(largeFile.toString(), "pdf");
        assertEquals("Security Restriction: Input file exceeds 5MB size limit.", result);
        Files.deleteIfExists(largeFile);
    }

    @Test
    void testConvertDocumentInvalidInputPath() {
        String result = tool.convertDocument("::invalid::path", "pdf");
        assertEquals("Error: Invalid input file path.", result);
    }

    @Test
    void testConvertDocumentMissingInputFile() {
        String result = tool.convertDocument(managedWorkspace.resolve("missing.txt").toString(), "pdf");
        assertEquals("Error: Input file does not exist or is not a regular file.", result);
    }

    @Test
    void testConvertDocumentMissingOutputFormat() {
        String result = tool.convertDocument(testInputFile.toString(), "");
        assertEquals("Error: outputFormat is required.", result);
    }

    @Test
    void testConvertDocumentMissingInputPath() {
        String result = tool.convertDocument("", "pdf");
        assertEquals("Error: inputFilePath is required.", result);
    }
}
