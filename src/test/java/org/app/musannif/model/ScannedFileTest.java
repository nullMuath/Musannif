package org.app.musannif.model;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class ScannedFileTest {

    @Test
    void constructor_storesAllFields() {
        Path path = Path.of("/some/file.pdf");
        Instant now = Instant.now();
        ScannedFile f = new ScannedFile(path, "pdf", 1024L, now);

        assertEquals(path, f.path());
        assertEquals("pdf", f.extension());
        assertEquals(1024L, f.sizeBytes());
        assertEquals(now, f.lastModified());
    }

    @Test
    void toString_containsRelevantInfo() {
        Path path = Path.of("/some/file.txt");
        Instant now = Instant.parse("2024-01-15T10:00:00Z");
        ScannedFile f = new ScannedFile(path, "txt", 512L, now);

        String s = f.toString();
        assertTrue(s.contains("txt"));
        assertTrue(s.contains("512"));
    }

    @Test
    void equality_sameFields_areEqual() {
        Path path = Path.of("/a/b.mp3");
        Instant now = Instant.now();
        ScannedFile f1 = new ScannedFile(path, "mp3", 100L, now);
        ScannedFile f2 = new ScannedFile(path, "mp3", 100L, now);
        assertEquals(f1, f2);
        assertEquals(f1.hashCode(), f2.hashCode());
    }

    @Test
    void emptyExtension_isAllowed() {
        ScannedFile f = new ScannedFile(Path.of("/no/ext"), "", 0L, Instant.EPOCH);
        assertEquals("", f.extension());
    }
}
