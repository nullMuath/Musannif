package org.app.musannif.model.core.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileCommandsTest {

    @TempDir
    Path tempDir;

    // -----------------------------------------------------------------------
    // MoveFileCommand
    // -----------------------------------------------------------------------

    @Test
    void move_execute_movesFile() throws IOException {
        Path src = tempDir.resolve("src.txt");
        Path dst = tempDir.resolve("dst.txt");
        Files.writeString(src, "hello");

        MoveFileCommand cmd = new MoveFileCommand(src, dst);
        cmd.execute();

        assertFalse(Files.exists(src));
        assertTrue(Files.exists(dst));
        assertEquals("hello", Files.readString(dst));
    }

    @Test
    void move_execute_createsParentDirectories() throws IOException {
        Path src = tempDir.resolve("file.txt");
        Path dst = tempDir.resolve("subdir").resolve("nested").resolve("file.txt");
        Files.writeString(src, "data");

        new MoveFileCommand(src, dst).execute();
        assertTrue(Files.exists(dst));
    }

    @Test
    void move_undo_movesBack() throws IOException {
        Path src = tempDir.resolve("orig.txt");
        Path dst = tempDir.resolve("moved.txt");
        Files.writeString(src, "content");

        MoveFileCommand cmd = new MoveFileCommand(src, dst);
        cmd.execute();
        cmd.undo();

        assertTrue(Files.exists(src));
        assertFalse(Files.exists(dst));
    }

    @Test
    void move_undo_beforeExecute_doesNothing() throws IOException {
        Path src = tempDir.resolve("x.txt");
        Path dst = tempDir.resolve("y.txt");
        Files.writeString(src, "hi");

        MoveFileCommand cmd = new MoveFileCommand(src, dst);
        cmd.undo(); // not executed yet — should be no-op (executed=false guard)
        assertTrue(Files.exists(src));
    }

    @Test
    void move_getters_returnConfiguredPaths() throws IOException {
        Path src = tempDir.resolve("a.txt");
        Path dst = tempDir.resolve("b.txt");
        MoveFileCommand cmd = new MoveFileCommand(src, dst);
        assertEquals(src, cmd.getSource());
        assertEquals(dst, cmd.getDestination());
    }

    // -----------------------------------------------------------------------
    // CopyFileCommand
    // -----------------------------------------------------------------------

    @Test
    void copy_execute_copyExists_sourceRemains() throws IOException {
        Path src = tempDir.resolve("orig.txt");
        Path dst = tempDir.resolve("copy.txt");
        Files.writeString(src, "value");

        CopyFileCommand cmd = new CopyFileCommand(src, dst);
        cmd.execute();

        assertTrue(Files.exists(src));
        assertTrue(Files.exists(dst));
        assertEquals("value", Files.readString(dst));
    }

    @Test
    void copy_execute_createsParentDirectories() throws IOException {
        Path src = tempDir.resolve("src.txt");
        Files.writeString(src, "data");
        Path dst = tempDir.resolve("a").resolve("b").resolve("copy.txt");

        new CopyFileCommand(src, dst).execute();
        assertTrue(Files.exists(dst));
    }

    @Test
    void copy_undo_deletesCopy() throws IOException {
        Path src = tempDir.resolve("s.txt");
        Path dst = tempDir.resolve("d.txt");
        Files.writeString(src, "hello");

        CopyFileCommand cmd = new CopyFileCommand(src, dst);
        cmd.execute();
        cmd.undo();

        assertTrue(Files.exists(src));
        assertFalse(Files.exists(dst));
    }

    @Test
    void copy_undo_beforeExecute_doesNothing() throws IOException {
        Path src = tempDir.resolve("ns.txt");
        Path dst = tempDir.resolve("nd.txt");
        Files.writeString(src, "hi");

        CopyFileCommand cmd = new CopyFileCommand(src, dst);
        cmd.undo(); // no-op
        assertFalse(Files.exists(dst));
    }

    @Test
    void copy_getters_returnPaths() {
        Path src = tempDir.resolve("src");
        Path dst = tempDir.resolve("dst");
        CopyFileCommand cmd = new CopyFileCommand(src, dst);
        assertEquals(src, cmd.getSource());
        assertEquals(dst, cmd.getDestination());
    }

    // -----------------------------------------------------------------------
    // RenameFileCommand
    // -----------------------------------------------------------------------

    @Test
    void rename_execute_fileRenamedInPlace() throws IOException {
        Path src = tempDir.resolve("old.txt");
        Path dst = tempDir.resolve("new.txt");
        Files.writeString(src, "rename me");

        RenameFileCommand cmd = new RenameFileCommand(src, dst);
        cmd.execute();

        assertFalse(Files.exists(src));
        assertTrue(Files.exists(dst));
    }

    @Test
    void rename_undo_revertsName() throws IOException {
        Path src = tempDir.resolve("before.txt");
        Path dst = tempDir.resolve("after.txt");
        Files.writeString(src, "data");

        RenameFileCommand cmd = new RenameFileCommand(src, dst);
        cmd.execute();
        cmd.undo();

        assertTrue(Files.exists(src));
        assertFalse(Files.exists(dst));
    }

    @Test
    void rename_undo_beforeExecute_doesNothing() throws IOException {
        Path src = tempDir.resolve("r.txt");
        Path dst = tempDir.resolve("rr.txt");
        Files.writeString(src, "hi");

        RenameFileCommand cmd = new RenameFileCommand(src, dst);
        cmd.undo(); // guard: executed=false
        assertTrue(Files.exists(src));
    }

    @Test
    void rename_getters() {
        Path src = tempDir.resolve("a");
        Path dst = tempDir.resolve("b");
        RenameFileCommand cmd = new RenameFileCommand(src, dst);
        assertEquals(src, cmd.getSource());
        assertEquals(dst, cmd.getDestination());
    }
}
