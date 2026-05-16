package org.app.musannif.model.core.history;

import org.app.musannif.model.OrganizationMemento;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SnapshotManager.
 *
 * SnapshotManager always writes into "musannif-snapshots" relative to the
 * JVM's working directory — that path cannot be redirected via system
 * properties at runtime on Windows.  Instead, each test:
 *   1. Uses a unique far-future Instant so snap files never collide with
 *      each other or with real application data.
 *   2. Registers every Instant in usedTimestamps and deletes the
 *      corresponding .snap file in @AfterEach.
 *
 * The @TempDir is used only as a source of real, unique Path values that
 * appear inside snap file content (sourceFolder, src/dest paths).
 */
class SnapshotManagerTest {

    @TempDir
    Path tempDir;

    /** Timestamps used this test — cleaned up in @AfterEach. */
    private final List<Instant> usedTimestamps = new ArrayList<>();

    @BeforeEach
    void clearHistory() {
        OperationHistory.getInstance().clear();
    }

    @AfterEach
    void deleteUsedSnapshots() throws IOException {
        OperationHistory.getInstance().clear();
        for (Instant ts : usedTimestamps) {
            // deleteIfExists — safe even if the test never wrote the file
            SnapshotManager.delete(new OperationRecord(ts, tempDir, "cleanup", 0, 0));
        }
        usedTimestamps.clear();
    }

    /** Register a unique timestamp for cleanup and return it. */
    private Instant ts(String iso) {
        Instant instant = Instant.parse(iso);
        usedTimestamps.add(instant);
        return instant;
    }

    private static OrganizationMemento memento(Map<Path, Path> destToSrc) {
        return OrganizationMemento.fromMap(destToSrc);
    }

    // =========================================================================
    // filenameFor
    // =========================================================================

    @Test
    void filenameFor_returnsSnapExtension() {
        String name = SnapshotManager.filenameFor(Instant.parse("2024-06-15T12:00:00Z"));
        assertTrue(name.endsWith(".snap"), "Expected .snap extension, got: " + name);
        assertTrue(name.contains("2024-06-15"), "Expected date in filename, got: " + name);
    }

    @Test
    void filenameFor_differentInstants_differentNames() {
        assertNotEquals(
            SnapshotManager.filenameFor(Instant.parse("2024-01-01T00:00:00Z")),
            SnapshotManager.filenameFor(Instant.parse("2024-06-01T00:00:00Z")));
    }

    // =========================================================================
    // save + loadMemento round-trip
    // =========================================================================

    @Test
    void save_and_loadMemento_roundTrip() throws IOException {
        Path src  = tempDir.resolve("file.txt");
        Path dest = tempDir.resolve("moved").resolve("file.txt");
        Files.createDirectories(dest.getParent());
        Files.writeString(src, "content");

        Instant instant = ts("2030-03-01T10:00:00Z");
        SnapshotManager.save(instant, tempDir, "By File Type", 1, 0,
                memento(Map.of(dest, src)));

        OrganizationMemento loaded = SnapshotManager.loadMemento(
                new OperationRecord(instant, tempDir, "By File Type", 1, 0));
        assertNotNull(loaded, "loadMemento should return a non-null memento");
    }

    @Test
    void loadMemento_missingFile_returnsNull() throws IOException {
        Instant instant = ts("2000-01-01T00:00:00Z");
        OrganizationMemento result = SnapshotManager.loadMemento(
                new OperationRecord(instant, tempDir, "By Date", 0, 0));
        assertNull(result, "Should return null when snapshot file does not exist");
    }

    // =========================================================================
    // save + loadFileMappings
    // =========================================================================

    @Test
    void save_and_loadFileMappings_returnsMappings() throws IOException {
        Path src  = tempDir.resolve("original.pdf");
        Path dest = tempDir.resolve("Documents").resolve("original.pdf");
        Files.createDirectories(dest.getParent());
        Files.writeString(src, "dummy");

        Instant instant = ts("2030-04-01T08:30:00Z");
        SnapshotManager.save(instant, tempDir, "By Extension", 1, 0,
                memento(Map.of(dest, src)));

        List<String[]> mappings = SnapshotManager.loadFileMappings(
                new OperationRecord(instant, tempDir, "By Extension", 1, 0));
        assertFalse(mappings.isEmpty(), "Should load at least one mapping");
        assertEquals(2, mappings.get(0).length, "Each mapping should be a [dest, src] pair");
    }

    @Test
    void loadFileMappings_missingFile_returnsEmpty() throws IOException {
        Instant instant = ts("1999-01-01T00:00:00Z");
        List<String[]> result = SnapshotManager.loadFileMappings(
                new OperationRecord(instant, tempDir, "By Date", 0, 0));
        assertTrue(result.isEmpty(), "Should return empty list when snapshot does not exist");
    }

    @Test
    void save_multipleMappings_allLoaded() throws IOException {
        Path src1  = tempDir.resolve("a.pdf");
        Path src2  = tempDir.resolve("b.jpg");
        Path dest1 = tempDir.resolve("Documents").resolve("a.pdf");
        Path dest2 = tempDir.resolve("Images").resolve("b.jpg");
        Files.createDirectories(dest1.getParent());
        Files.createDirectories(dest2.getParent());
        Files.writeString(src1, "pdf");
        Files.writeString(src2, "jpg");

        Instant instant = ts("2030-10-01T12:00:00Z");
        SnapshotManager.save(instant, tempDir, "By File Type", 2, 0,
                memento(Map.of(dest1, src1, dest2, src2)));

        List<String[]> mappings = SnapshotManager.loadFileMappings(
                new OperationRecord(instant, tempDir, "By File Type", 2, 0));
        assertEquals(2, mappings.size(), "Should load exactly 2 file mappings");
    }

    // =========================================================================
    // loadAll — populates OperationHistory from disk
    // =========================================================================

    @Test
    void loadAll_populatesOperationHistory() throws IOException {
        Instant instant = ts("2030-05-01T09:00:00Z");
        Path src  = tempDir.resolve("a.txt");
        Path dest = tempDir.resolve("Other").resolve("a.txt");
        Files.createDirectories(dest.getParent());
        Files.writeString(src, "x");

        SnapshotManager.save(instant, tempDir, "By File Type", 1, 0,
                memento(Map.of(dest, src)));

        OperationHistory.getInstance().clear();
        SnapshotManager.loadAll();

        assertFalse(OperationHistory.getInstance().isEmpty(),
                "OperationHistory should be populated after loadAll");
    }

    @Test
    void loadAll_doesNotThrow() {
        assertDoesNotThrow(SnapshotManager::loadAll);
    }

    // =========================================================================
    // isRestorable
    // =========================================================================

    @Test
    void isRestorable_fileExistsAtDest_returnsTrue() throws IOException {
        Path src  = tempDir.resolve("report.pdf");
        Path dest = tempDir.resolve("Documents").resolve("report.pdf");
        Files.createDirectories(dest.getParent());
        Files.writeString(dest, "pdf-content"); // dest IS present on disk

        Instant instant = ts("2030-06-01T15:00:00Z");
        SnapshotManager.save(instant, tempDir, "By File Type", 1, 0,
                memento(Map.of(dest, src)));

        assertTrue(SnapshotManager.isRestorable(
                new OperationRecord(instant, tempDir, "By File Type", 1, 0)),
                "Should be restorable when destination file exists on disk");
    }

    @Test
    void isRestorable_noSnapshotFile_returnsFalse() {
        Instant instant = ts("1990-01-01T00:00:00Z");
        assertFalse(SnapshotManager.isRestorable(
                new OperationRecord(instant, tempDir, "By Date", 0, 0)),
                "Should return false when snapshot file does not exist");
    }

    @Test
    void isRestorable_destFileMissing_returnsFalse() throws IOException {
        Path src  = tempDir.resolve("gone_src.txt");
        Path dest = tempDir.resolve("Other").resolve("gone_dst.txt");
        // dest intentionally NOT created on disk

        Instant instant = ts("2030-07-01T00:00:00Z");
        SnapshotManager.save(instant, tempDir, "By File Type", 1, 0,
                memento(Map.of(dest, src)));

        assertFalse(SnapshotManager.isRestorable(
                new OperationRecord(instant, tempDir, "By File Type", 1, 0)),
                "Should return false when no destination file exists on disk");
    }

    // =========================================================================
    // delete
    // =========================================================================

    @Test
    void delete_removesSnapshotFile() throws IOException {
        Path src  = tempDir.resolve("x.txt");
        Path dest = tempDir.resolve("Out").resolve("x.txt");
        Files.createDirectories(dest.getParent());
        Files.writeString(src, "x");

        Instant instant = ts("2030-08-01T10:00:00Z");
        SnapshotManager.save(instant, tempDir, "By Date", 1, 0,
                memento(Map.of(dest, src)));

        OperationRecord rec = new OperationRecord(instant, tempDir, "By Date", 1, 0);
        SnapshotManager.delete(rec);

        assertNull(SnapshotManager.loadMemento(rec),
                "Snapshot should be gone after delete");
    }

    @Test
    void delete_nonexistentFile_doesNotThrow() {
        Instant instant = ts("1985-01-01T00:00:00Z");
        assertDoesNotThrow(() ->
                SnapshotManager.delete(new OperationRecord(instant, tempDir, "By Date", 0, 0)));
    }

    // =========================================================================
    // clearAll
    // =========================================================================

    @Test
    void clearAll_removesOurSnapshotFiles() throws IOException {
        // Use far-future timestamps unlikely to collide with anything else
        Instant instant1 = ts("2031-09-01T00:00:00Z");
        Instant instant2 = ts("2031-09-02T00:00:00Z");

        SnapshotManager.save(instant1, tempDir, "By File Type", 0, 0, memento(Map.of()));
        SnapshotManager.save(instant2, tempDir, "By Date",      0, 0, memento(Map.of()));

        // Both files must exist before clearAll
        Path snapDir = Path.of("musannif-snapshots");
        assertTrue(Files.exists(snapDir.resolve(SnapshotManager.filenameFor(instant1))),
                "Snap file 1 should exist before clearAll");
        assertTrue(Files.exists(snapDir.resolve(SnapshotManager.filenameFor(instant2))),
                "Snap file 2 should exist before clearAll");

        SnapshotManager.clearAll();

        // Both files must be gone
        assertFalse(Files.exists(snapDir.resolve(SnapshotManager.filenameFor(instant1))),
                "Snap file 1 should be deleted by clearAll");
        assertFalse(Files.exists(snapDir.resolve(SnapshotManager.filenameFor(instant2))),
                "Snap file 2 should be deleted by clearAll");

        // @AfterEach delete() calls are safe because deleteIfExists handles missing files
    }

    @Test
    void clearAll_doesNotThrow() {
        assertDoesNotThrow(SnapshotManager::clearAll);
    }
}
