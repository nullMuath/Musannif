package org.app.musannif.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrganizationMementoTest {

    @TempDir
    Path tempDir;

    @Test
    void capture_recordsCorrectSize() {
        Path src = tempDir.resolve("a.txt");
        Path dst = tempDir.resolve("b.txt");
        List<OrganizationMemento.FileMoveRecord> moves =
                List.of(new OrganizationMemento.FileMoveRecord(src, dst));

        OrganizationMemento memento = OrganizationMemento.capture(moves);
        assertEquals(1, memento.size());
    }

    @Test
    void capture_emptyList_sizeIsZero() {
        OrganizationMemento memento = OrganizationMemento.capture(List.of());
        assertEquals(0, memento.size());
    }

    @Test
    void getCapturedAt_isNotNull() {
        OrganizationMemento memento = OrganizationMemento.capture(List.of());
        assertNotNull(memento.getCapturedAt());
    }

    @Test
    void restore_movesFilesBack() throws IOException {
        Path src = tempDir.resolve("orig.txt");
        Path dst = tempDir.resolve("moved.txt");
        Files.writeString(src, "hello");
        Files.move(src, dst);

        List<OrganizationMemento.FileMoveRecord> moves =
                List.of(new OrganizationMemento.FileMoveRecord(src, dst));
        OrganizationMemento memento = OrganizationMemento.capture(moves);
        memento.restore();

        assertTrue(Files.exists(src));
        assertFalse(Files.exists(dst));
    }

    @Test
    void restore_fileAlreadyGone_doesNotThrow() throws IOException {
        Path src = tempDir.resolve("gone_src.txt");
        Path dst = tempDir.resolve("gone_dst.txt"); // dst doesn't exist

        List<OrganizationMemento.FileMoveRecord> moves =
                List.of(new OrganizationMemento.FileMoveRecord(src, dst));
        OrganizationMemento memento = OrganizationMemento.capture(moves);

        // Should not throw; prints warning to stderr
        assertDoesNotThrow(memento::restore);
    }

    @Test
    void fileMoveRecord_getters() {
        Path src = tempDir.resolve("s");
        Path dst = tempDir.resolve("d");
        OrganizationMemento.FileMoveRecord record =
                new OrganizationMemento.FileMoveRecord(src, dst);

        assertEquals(src, record.source());
        assertEquals(dst, record.destination());
    }
}
