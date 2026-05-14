package org.app.musannif.model.history;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OperationHistoryTest {

    @BeforeEach
    void clearSingleton() {
        OperationHistory.getInstance().clear();
    }

    @Test
    void getInstance_returnsSameInstance() {
        assertSame(OperationHistory.getInstance(), OperationHistory.getInstance());
    }

    @Test
    void isEmpty_afterClear() {
        assertTrue(OperationHistory.getInstance().isEmpty());
    }

    @Test
    void add_recordStoredInOrder() {
        OperationHistory history = OperationHistory.getInstance();
        Instant t1 = Instant.parse("2024-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2024-06-01T00:00:00Z");
        Path folder = Path.of("/home/user/downloads");

        history.add(t1, folder, "By File Type", 10, 0);
        history.add(t2, folder, "By Date", 5, 2);

        assertFalse(history.isEmpty());
        List<OperationRecord> all = history.getAll();
        assertEquals(2, all.size());
        assertEquals("By File Type", all.get(0).mode());
        assertEquals("By Date", all.get(1).mode());
    }

    @Test
    void getAll_returnsUnmodifiableView() {
        OperationHistory history = OperationHistory.getInstance();
        history.add(Instant.now(), Path.of("/tmp"), "By Extension", 3, 0);

        List<OperationRecord> all = history.getAll();
        assertThrows(UnsupportedOperationException.class, () -> all.add(null));
    }

    @Test
    void clear_removesAllRecords() {
        OperationHistory history = OperationHistory.getInstance();
        history.add(Instant.now(), Path.of("/tmp"), "mode", 1, 0);
        history.clear();

        assertTrue(history.isEmpty());
        assertEquals(0, history.getAll().size());
    }
}

class OperationRecordTest {

    @Test
    void formattedTimestamp_nonNull() {
        OperationRecord r = new OperationRecord(
                Instant.parse("2024-03-15T10:30:00Z"),
                Path.of("/home/user/docs"),
                "By File Type", 5, 1);

        assertNotNull(r.formattedTimestamp());
        assertFalse(r.formattedTimestamp().isBlank());
    }

    @Test
    void summary_containsKeyInfo() {
        Path folder = Path.of("/home/user/downloads");
        OperationRecord r = new OperationRecord(
                Instant.parse("2024-03-15T10:30:00Z"),
                folder, "By File Type", 7, 2);

        String summary = r.summary();
        assertTrue(summary.contains("downloads"));
        assertTrue(summary.contains("By File Type"));
        assertTrue(summary.contains("7 moved"));
        assertTrue(summary.contains("2 skipped"));
    }

    @Test
    void summary_noSkipped_skippedNotMentioned() {
        OperationRecord r = new OperationRecord(
                Instant.now(), Path.of("/tmp/src"), "By Date", 3, 0);

        assertFalse(r.summary().contains("skipped"));
    }

    @Test
    void record_getters_returnConstructorValues() {
        Instant ts = Instant.now();
        Path folder = Path.of("/folder");
        OperationRecord r = new OperationRecord(ts, folder, "mode", 10, 2);

        assertEquals(ts, r.timestamp());
        assertEquals(folder, r.sourceFolder());
        assertEquals("mode", r.mode());
        assertEquals(10, r.filesMoved());
        assertEquals(2, r.filesSkipped());
    }
}
