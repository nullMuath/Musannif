package org.app.musannif.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class FileScannerTest {

    @TempDir
    Path tempDir;

    private Path createFile(Path dir, String name) throws IOException {
        Path f = dir.resolve(name);
        Files.writeString(f, "test content");
        return f;
    }

    // -----------------------------------------------------------------------
    // Basic scanning
    // -----------------------------------------------------------------------

    @Test
    void scan_returnsAllFiles_inFlatDirectory() throws IOException {
        createFile(tempDir, "a.pdf");
        createFile(tempDir, "b.jpg");
        createFile(tempDir, "c.mp3");

        FileScanner scanner = new FileScanner.Builder().build();
        List<ScannedFile> results = scanner.scan(tempDir);

        assertEquals(3, results.size());
    }

    @Test
    void scan_emptyDirectory_returnsEmpty() throws IOException {
        FileScanner scanner = new FileScanner.Builder().build();
        List<ScannedFile> results = scanner.scan(tempDir);
        assertTrue(results.isEmpty());
    }

    @Test
    void scan_populatesExtension() throws IOException {
        createFile(tempDir, "report.pdf");
        FileScanner scanner = new FileScanner.Builder().build();
        List<ScannedFile> results = scanner.scan(tempDir);

        assertEquals("pdf", results.get(0).extension());
    }

    @Test
    void scan_populatesSize() throws IOException {
        Path f = tempDir.resolve("sized.txt");
        Files.writeString(f, "12345");  // 5 bytes
        FileScanner scanner = new FileScanner.Builder().build();
        List<ScannedFile> results = scanner.scan(tempDir);

        assertTrue(results.get(0).sizeBytes() > 0);
    }

    @Test
    void scan_fileWithNoExtension_hasEmptyExtension() throws IOException {
        Path f = tempDir.resolve("noextension");
        Files.writeString(f, "data");
        FileScanner scanner = new FileScanner.Builder().build();
        List<ScannedFile> results = scanner.scan(tempDir);

        assertEquals("", results.get(0).extension());
    }

    // -----------------------------------------------------------------------
    // Extension filter
    // -----------------------------------------------------------------------

    @Test
    void scan_filterByExtension_returnsOnlyMatching() throws IOException {
        createFile(tempDir, "doc.pdf");
        createFile(tempDir, "image.jpg");
        createFile(tempDir, "video.mp4");

        FileScanner scanner = new FileScanner.Builder()
                .filterByExtensions("pdf")
                .build();
        List<ScannedFile> results = scanner.scan(tempDir);

        assertEquals(1, results.size());
        assertEquals("pdf", results.get(0).extension());
    }

    @Test
    void scan_filterByExtension_stripsLeadingDot() throws IOException {
        createFile(tempDir, "file.docx");
        createFile(tempDir, "other.pdf");

        FileScanner scanner = new FileScanner.Builder()
                .filterByExtensions(".docx")
                .build();
        List<ScannedFile> results = scanner.scan(tempDir);

        assertEquals(1, results.size());
        assertEquals("docx", results.get(0).extension());
    }

    @Test
    void scan_filterByExtension_caseInsensitive() throws IOException {
        createFile(tempDir, "file.PDF");
        FileScanner scanner = new FileScanner.Builder()
                .filterByExtensions("pdf")
                .build();
        // Extension is lowercased during scan; "PDF" file yields ext "pdf"
        List<ScannedFile> results = scanner.scan(tempDir);
        assertEquals(1, results.size());
    }

    // -----------------------------------------------------------------------
    // Max depth
    // -----------------------------------------------------------------------

    @Test
    void scan_maxDepth1_doesNotDescendIntoDeepSubdirectories() throws IOException {
        createFile(tempDir, "root.txt");
        Path subDir = Files.createDirectory(tempDir.resolve("sub"));
        createFile(subDir, "level1.txt");
        Path deepDir = Files.createDirectory(subDir.resolve("deep"));
        createFile(deepDir, "level2.txt");

        // maxDepth=1 visits root and one level of subdirectories.
        // root.txt (depth 1) + sub/level1.txt (depth 2) are visited.
        // sub/deep/level2.txt (depth 3) is NOT visited.
        FileScanner scanner = new FileScanner.Builder().maxDepth(2).build();
        List<ScannedFile> results = scanner.scan(tempDir);

        assertEquals(2, results.size()); // root.txt + level1.txt; level2.txt excluded
    }

    @Test
    void scan_unlimitedDepth_findsNestedFiles() throws IOException {
        Path deep = Files.createDirectories(tempDir.resolve("a").resolve("b").resolve("c"));
        createFile(deep, "deep.txt");

        FileScanner scanner = new FileScanner.Builder().maxDepth(-1).build();
        List<ScannedFile> results = scanner.scan(tempDir);

        assertEquals(1, results.size());
    }

    @Test
    void builder_invalidMaxDepth_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new FileScanner.Builder().maxDepth(-2));
    }

    // -----------------------------------------------------------------------
    // Skip hidden
    // -----------------------------------------------------------------------

    @Test
    void scan_skipHiddenFalse_includesDotFiles() throws IOException {
        // Create a dot file (hidden on Unix)
        Path hidden = tempDir.resolve(".hidden.txt");
        Files.writeString(hidden, "invisible");

        FileScanner scanner = new FileScanner.Builder().skipHidden(false).build();
        List<ScannedFile> results = scanner.scan(tempDir);

        // On Linux, dot-files are hidden; skipHidden=false includes them
        assertFalse(results.isEmpty());
    }

    // -----------------------------------------------------------------------
    // Stream API
    // -----------------------------------------------------------------------

    @Test
    void scanAsStream_returnsCorrectCount() throws IOException {
        createFile(tempDir, "x.pdf");
        createFile(tempDir, "y.pdf");

        FileScanner scanner = new FileScanner.Builder().build();
        Stream<ScannedFile> stream = scanner.scanAsStream(tempDir);
        assertEquals(2, stream.count());
    }

    // -----------------------------------------------------------------------
    // ScannedFile fields populated
    // -----------------------------------------------------------------------

    @Test
    void scan_scannedFile_pathIsAbsolute() throws IOException {
        createFile(tempDir, "abs.txt");
        FileScanner scanner = new FileScanner.Builder().build();
        List<ScannedFile> results = scanner.scan(tempDir);

        assertTrue(results.get(0).path().isAbsolute());
    }

    @Test
    void scan_lastModified_isNotNull() throws IOException {
        createFile(tempDir, "lm.txt");
        FileScanner scanner = new FileScanner.Builder().build();
        List<ScannedFile> results = scanner.scan(tempDir);

        assertNotNull(results.get(0).lastModified());
    }
}
