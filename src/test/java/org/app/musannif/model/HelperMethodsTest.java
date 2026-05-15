package org.app.musannif.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class HelperMethodsTest {

    // -----------------------------------------------------------------------
    // formatFileSize
    // -----------------------------------------------------------------------

    @Test
    void formatFileSize_bytes() {
        assertEquals("512 B", helperMethods.formatFileSize(512));
        assertEquals("0 B", helperMethods.formatFileSize(0));
        assertEquals("1023 B", helperMethods.formatFileSize(1023));
    }

    @Test
    void formatFileSize_kilobytes() {
        String result = helperMethods.formatFileSize(1024);
        assertTrue(result.endsWith("KB"), "Expected KB suffix but got: " + result);
        assertTrue(result.contains("1.00"));
    }

    @Test
    void formatFileSize_megabytes() {
        String result = helperMethods.formatFileSize(1024 * 1024);
        assertTrue(result.endsWith("MB"), "Expected MB suffix but got: " + result);
        assertTrue(result.contains("1.00"));
    }

    @Test
    void formatFileSize_gigabytes() {
        String result = helperMethods.formatFileSize(1024L * 1024 * 1024);
        assertTrue(result.endsWith("GB"), "Expected GB suffix but got: " + result);
        assertTrue(result.contains("1.00"));
    }

    @Test
    void formatFileSize_fractional_kb() {
        // 1536 bytes = 1.5 KB
        String result = helperMethods.formatFileSize(1536);
        assertTrue(result.contains("1.50"), "Expected 1.50 KB but got: " + result);
    }

    // -----------------------------------------------------------------------
    // formatDateTime
    // -----------------------------------------------------------------------

    @Test
    void formatDateTime_returnsNonNullString() {
        String result = helperMethods.formatDateTime(Instant.parse("2024-06-15T08:30:00Z"));
        assertNotNull(result);
        assertFalse(result.isBlank());
    }

    @Test
    void formatDateTime_containsDate() {
        // Timezone-agnostic: just verify basic format pattern yyyy-MM-dd HH:mm:ss
        String result = helperMethods.formatDateTime(Instant.now());
        assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"),
                "Unexpected format: " + result);
    }
}
