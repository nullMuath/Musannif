package org.app.musannif.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for util classes: Logger and helperMethods edge cases.
 */
class UtilExtendedTest {

    // =========================================================================
    // Logger — singleton, info method
    // =========================================================================

    @Test
    void logger_getInstance_returnsSameInstance() {
        Logger a = Logger.getLogger();
        Logger b = Logger.getLogger();
        assertSame(a, b, "Logger should be a singleton");
    }

    @Test
    void logger_info_doesNotThrow() {
        assertDoesNotThrow(() -> Logger.getLogger().info("test message from UtilExtendedTest"));
    }

    @Test
    void logger_info_multipleMessages_doesNotThrow() {
        Logger log = Logger.getLogger();
        assertDoesNotThrow(() -> {
            log.info("Message 1");
            log.info("Message 2 with special chars: <>&\"'");
            log.info("Message 3: " + Instant.now());
        });
    }

    // =========================================================================
    // helperMethods — formatFileSize boundary cases
    // =========================================================================

    @Test
    void formatFileSize_exactlyOneKb_returnsKb() {
        String result = helperMethods.formatFileSize(1024);
        assertTrue(result.endsWith("KB"), "Expected KB, got: " + result);
    }

    @Test
    void formatFileSize_exactlyOneMb_returnsMb() {
        String result = helperMethods.formatFileSize(1024L * 1024);
        assertTrue(result.endsWith("MB"), "Expected MB, got: " + result);
    }

    @Test
    void formatFileSize_exactlyOneGb_returnsGb() {
        String result = helperMethods.formatFileSize(1024L * 1024 * 1024);
        assertTrue(result.endsWith("GB"), "Expected GB, got: " + result);
    }

    @Test
    void formatFileSize_justBelowKb_returnsBytes() {
        assertEquals("1023 B", helperMethods.formatFileSize(1023));
    }

    @Test
    void formatFileSize_justBelowMb_returnsKb() {
        String result = helperMethods.formatFileSize(1024L * 1024 - 1);
        assertTrue(result.endsWith("KB"), "Expected KB just below MB, got: " + result);
    }

    @Test
    void formatFileSize_justBelowGb_returnsMb() {
        String result = helperMethods.formatFileSize(1024L * 1024 * 1024 - 1);
        assertTrue(result.endsWith("MB"), "Expected MB just below GB, got: " + result);
    }

    @Test
    void formatFileSize_largeValue_returnsGb() {
        String result = helperMethods.formatFileSize(5L * 1024 * 1024 * 1024);
        assertTrue(result.endsWith("GB"), "Expected GB for large value, got: " + result);
        assertTrue(result.contains("5.00"), "Expected 5.00 GB, got: " + result);
    }

    // =========================================================================
    // helperMethods — formatDateTime
    // =========================================================================

    @Test
    void formatDateTime_specificInstant_hasExpectedFormat() {
        String result = helperMethods.formatDateTime(Instant.parse("2024-01-15T00:00:00Z"));
        // yyyy-MM-dd HH:mm:ss pattern
        assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"),
                "Unexpected format: " + result);
    }

    @Test
    void formatDateTime_epochInstant_doesNotThrow() {
        assertDoesNotThrow(() -> helperMethods.formatDateTime(Instant.EPOCH));
    }

    @Test
    void formatDateTime_farFutureInstant_doesNotThrow() {
        Instant future = Instant.parse("2099-12-31T23:59:59Z");
        assertDoesNotThrow(() -> helperMethods.formatDateTime(future));
    }

    // =========================================================================
    // helperMethods — addFolderIcons (Linux: DosFileAttributeView returns null,
    //   so the try block runs but setHidden/setSystem calls fail silently or
    //   are skipped; we only verify it does not throw)
    // =========================================================================

    @Test
    void addFolderIcons_nonExistentSubfolders_doesNotThrow(@TempDir Path dir) {
        // None of the standard category folders exist, so the method just skips them
        assertDoesNotThrow(() -> helperMethods.addFolderIcons(dir));
    }

    @Test
    void addFolderIcons_withDocumentsFolder_doesNotThrow(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir.resolve("Documents"));
        // On Linux DosFileAttributeView is null — the catch block fires, but no exception escapes
        assertDoesNotThrow(() -> helperMethods.addFolderIcons(dir));
    }

    @Test
    void addFolderIcons_withAllCategoryFolders_doesNotThrow(@TempDir Path dir) throws Exception {
        for (String name : new String[]{"Documents","Images","Videos","Audio","Archives","Other"}) {
            Files.createDirectories(dir.resolve(name));
        }
        assertDoesNotThrow(() -> helperMethods.addFolderIcons(dir));
    }
}
