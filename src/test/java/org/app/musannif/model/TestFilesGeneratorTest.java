package org.app.musannif.model;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TestFilesGenerator.
 *
 * The generator always writes into {@code <user.home>/.musannif-test}.
 * We call it through its public API and verify the side-effects on disk,
 * then clean up afterwards so the test suite is idempotent.
 *
 * Branches covered:
 *   generate() — happy path: directory created, all files written with
 *                modified timestamps
 *   generate() — re-entrant path: existing directory deleted then re-created
 *                (the "if (Files.exists(testDir)) deleteRecursively" branch)
 *   deleteRecursively — recursive deletion of a nested structure
 *   deleteRecursively — DosFileAttributeView null branch (Linux: attrs == null,
 *                       so the if-body is skipped — but the line is still reached)
 *
 * Place this file at:
 *   src/test/java/org/app/musannif/model/TestFilesGeneratorTest.java
 */
class TestFilesGeneratorTest {

    /** Location the generator always uses. */
    private static final Path TEST_DIR =
            Path.of(System.getProperty("user.home"), ".musannif-test");

    @AfterEach
    void cleanup() throws IOException {
        deleteRecursively(TEST_DIR);
    }

    // =========================================================================
    // generate() — fresh run (directory does NOT exist yet)
    // =========================================================================

    @Test
    void generate_createsDirectoryAndFiles() {
        // Ensure clean slate
        silentDelete(TEST_DIR);

        TestFilesGenerator.generate();

        assertTrue(Files.exists(TEST_DIR), ".musannif-test should exist after generate()");
        assertTrue(Files.isDirectory(TEST_DIR));
    }

    @Test
    void generate_createsExpectedFileCount() throws IOException {
        silentDelete(TEST_DIR);
        TestFilesGenerator.generate();

        try (var entries = Files.list(TEST_DIR)) {
            long count = entries.filter(Files::isRegularFile).count();
            // The fileTypes list has 36 entries (18 pairs × 2)
            assertEquals(36, count, "Should create exactly 36 test files");
        }
    }

    @Test
    void generate_filesHaveLastModifiedSet() throws IOException {
        silentDelete(TEST_DIR);
        TestFilesGenerator.generate();

        try (var entries = Files.list(TEST_DIR)) {
            for (Path file : entries.toList()) {
                FileTime ft = Files.getLastModifiedTime(file);
                assertNotNull(ft);
                // All timestamps should be reasonably recent (within last year or future)
                assertTrue(ft.toInstant().isAfter(Instant.parse("2020-01-01T00:00:00Z")));
            }
        }
    }

    @Test
    void generate_fileTypesIncludeAllCategories() throws IOException {
        silentDelete(TEST_DIR);
        TestFilesGenerator.generate();

        try (var entries = Files.list(TEST_DIR)) {
            var names = entries.map(p -> p.getFileName().toString()).toList();
            // At least one file per category must be present
            assertTrue(names.stream().anyMatch(n -> n.endsWith(".pdf")),   "Missing PDF");
            assertTrue(names.stream().anyMatch(n -> n.endsWith(".png")),   "Missing PNG");
            assertTrue(names.stream().anyMatch(n -> n.endsWith(".mp4")),   "Missing MP4");
            assertTrue(names.stream().anyMatch(n -> n.endsWith(".mp3")),   "Missing MP3");
            assertTrue(names.stream().anyMatch(n -> n.endsWith(".zip")),   "Missing ZIP");
        }
    }

    // =========================================================================
    // generate() — re-entrant: directory already exists → deleteRecursively
    //              exercises the "if (Files.exists(testDir))" true branch
    // =========================================================================

    @Test
    void generate_calledTwice_replacesExistingDirectory() throws IOException {
        silentDelete(TEST_DIR);
        TestFilesGenerator.generate();   // first call: creates directory

        // Tamper: add an extra file that should not survive the second call
        Path extra = TEST_DIR.resolve("extra_file.tmp");
        Files.writeString(extra, "should be gone");

        TestFilesGenerator.generate();   // second call: deletes and recreates

        assertFalse(Files.exists(extra), "extra_file.tmp should have been deleted");
        assertTrue(Files.exists(TEST_DIR), "directory should still exist after re-run");
    }

    @Test
    void generate_calledTwice_fileCountIsStillCorrect() throws IOException {
        silentDelete(TEST_DIR);
        TestFilesGenerator.generate();
        TestFilesGenerator.generate();   // second call

        try (var entries = Files.list(TEST_DIR)) {
            long count = entries.filter(Files::isRegularFile).count();
            assertEquals(36, count);
        }
    }

    // =========================================================================
    // deleteRecursively — nested directory structure
    //   generate() calls deleteRecursively internally; here we create a
    //   manually nested structure inside the test dir and call generate() again
    //   to force deleteRecursively to traverse multiple levels.
    // =========================================================================

    @Test
    void generate_withNestedSubdirectory_deletesRecursively() throws IOException {
        silentDelete(TEST_DIR);
        Files.createDirectories(TEST_DIR);

        // Create a nested structure that deleteRecursively must handle
        Path sub = Files.createDirectory(TEST_DIR.resolve("subdir"));
        Files.writeString(sub.resolve("nested.txt"), "nested content");
        Path deeper = Files.createDirectory(sub.resolve("deeper"));
        Files.writeString(deeper.resolve("deep.txt"), "deep content");

        // generate() sees existing dir → calls deleteRecursively → recreates
        TestFilesGenerator.generate();

        assertFalse(Files.exists(sub), "subdir should have been removed");
        assertTrue(Files.exists(TEST_DIR), "root test dir recreated");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static void silentDelete(Path path) {
        try {
            deleteRecursively(path);
        } catch (IOException ignored) {
            // Does not exist — that is fine
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        if (Files.isDirectory(path)) {
            try (var entries = Files.list(path)) {
                for (Path entry : entries.toList()) {
                    deleteRecursively(entry);
                }
            }
        }
        Files.deleteIfExists(path);
    }
}
