package org.app.musannif.model.category;

import org.app.musannif.model.ScannedFile;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DateFileCategorizerTest {

    private ScannedFile file(Instant modified) {
        return new ScannedFile(Path.of("/f.txt"), "txt", 10L, modified);
    }

    // -----------------------------------------------------------------------
    // ByMonth
    // -----------------------------------------------------------------------

    @Test
    void byMonth_groupsFilesCorrectly() {
        DateFileCategorizer cat = new DateFileCategorizer.Builder()
                .period(new Periods.ByMonth())
                .build();

        ScannedFile f1 = file(Instant.parse("2024-03-15T00:00:00Z"));
        ScannedFile f2 = file(Instant.parse("2024-03-20T00:00:00Z"));
        ScannedFile f3 = file(Instant.parse("2024-11-01T00:00:00Z"));

        Map<String, List<ScannedFile>> result = cat.categorize(List.of(f1, f2, f3));
        assertEquals(2, result.size());
        assertEquals(2, result.get("2024-03").size());
        assertEquals(1, result.get("2024-11").size());
    }

    @Test
    void byMonth_labelFormat() {
        Periods.ByMonth period = new Periods.ByMonth();
        String label = period.label(Instant.parse("2025-04-07T12:00:00Z"));
        assertTrue(label.matches("\\d{4}-\\d{2}"), "Label should be YYYY-MM but was: " + label);
        assertEquals("Month", period.getName());
    }

    // -----------------------------------------------------------------------
    // ByDay
    // -----------------------------------------------------------------------

    @Test
    void byDay_groupsFilesCorrectly() {
        DateFileCategorizer cat = new DateFileCategorizer.Builder()
                .period(new Periods.ByDay())
                .build();

        ScannedFile f1 = file(Instant.parse("2024-03-15T01:00:00Z"));
        ScannedFile f2 = file(Instant.parse("2024-03-15T22:00:00Z"));
        ScannedFile f3 = file(Instant.parse("2024-03-16T00:00:00Z"));

        Map<String, List<ScannedFile>> result = cat.categorize(List.of(f1, f2, f3));
        // day grouping; count of buckets depends on system timezone — just verify totals
        int totalFiles = result.values().stream().mapToInt(List::size).sum();
        assertEquals(3, totalFiles);
    }

    @Test
    void byDay_labelFormat() {
        Periods.ByDay period = new Periods.ByDay();
        String label = period.label(Instant.parse("2025-04-07T12:00:00Z"));
        assertTrue(label.matches("\\d{4}-\\d{2}-\\d{2}"), "Label should be YYYY-MM-DD but was: " + label);
        assertEquals("Day", period.getName());
    }

    // -----------------------------------------------------------------------
    // ByYear
    // -----------------------------------------------------------------------

    @Test
    void byYear_groupsFilesCorrectly() {
        DateFileCategorizer cat = new DateFileCategorizer.Builder()
                .period(new Periods.ByYear())
                .build();

        ScannedFile f2023 = file(Instant.parse("2023-06-01T00:00:00Z"));
        ScannedFile f2024a = file(Instant.parse("2024-02-01T00:00:00Z"));
        ScannedFile f2024b = file(Instant.parse("2024-11-01T00:00:00Z"));

        Map<String, List<ScannedFile>> result = cat.categorize(List.of(f2023, f2024a, f2024b));
        assertEquals(1, result.get("2023").size());
        assertEquals(2, result.get("2024").size());
    }

    @Test
    void byYear_labelFormat() {
        Periods.ByYear period = new Periods.ByYear();
        String label = period.label(Instant.parse("2025-04-07T12:00:00Z"));
        assertTrue(label.matches("\\d{4}"), "Label should be YYYY but was: " + label);
        assertEquals("Year", period.getName());
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    @Test
    void builder_noPeriod_throwsOnBuild() {
        assertThrows(NullPointerException.class,
                () -> new DateFileCategorizer.Builder().build());
    }

    @Test
    void builder_nullPeriod_throwsNPE() {
        assertThrows(NullPointerException.class,
                () -> new DateFileCategorizer.Builder().period(null));
    }

    @Test
    void categorize_emptyList_returnsEmptyMap() {
        DateFileCategorizer cat = new DateFileCategorizer.Builder()
                .period(new Periods.ByMonth())
                .build();
        Map<String, List<ScannedFile>> result = cat.categorize(List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void categorize_sortedChronologically() {
        DateFileCategorizer cat = new DateFileCategorizer.Builder()
                .period(new Periods.ByMonth())
                .build();

        // Add out-of-order
        List<ScannedFile> files = List.of(
                file(Instant.parse("2024-12-01T00:00:00Z")),
                file(Instant.parse("2024-01-01T00:00:00Z")),
                file(Instant.parse("2024-06-01T00:00:00Z"))
        );
        Map<String, List<ScannedFile>> result = cat.categorize(files);
        List<String> keys = List.copyOf(result.keySet());
        // TreeMap: keys should be sorted lexicographically = chronologically for ISO labels
        assertTrue(keys.get(0).compareTo(keys.get(1)) < 0);
        assertTrue(keys.get(1).compareTo(keys.get(2)) < 0);
    }
}
