package org.app.musannif.model.core.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CommandHistoryTest {

    @TempDir
    Path tempDir;

    private Path newFile(String name) throws IOException {
        Path p = tempDir.resolve(name);
        Files.writeString(p, "content");
        return p;
    }

    @Test
    void execute_canUndo_canRedo_cycleWorks(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("a.txt");
        Path dst = dir.resolve("b.txt");
        Files.writeString(src, "hi");

        CommandHistory history = new CommandHistory();
        assertFalse(history.canUndo());
        assertFalse(history.canRedo());

        history.execute(new MoveFileCommand(src, dst));
        assertTrue(history.canUndo());
        assertFalse(history.canRedo());
        assertTrue(Files.exists(dst));

        history.undo();
        assertFalse(history.canUndo());
        assertTrue(history.canRedo());
        assertTrue(Files.exists(src));

        history.redo();
        assertTrue(history.canUndo());
        assertFalse(history.canRedo());
        assertTrue(Files.exists(dst));
    }

    @Test
    void undoStack_clearedByNewExecute() throws IOException {
        Path src = tempDir.resolve("src.txt");
        Path dst1 = tempDir.resolve("dst1.txt");
        Path dst2 = tempDir.resolve("dst2.txt");
        Files.writeString(src, "hi");

        CommandHistory history = new CommandHistory();
        history.execute(new MoveFileCommand(src, dst1));
        history.undo();
        assertTrue(history.canRedo());

        // After undo, file is back at src. A new execute should clear the redo stack.
        history.execute(new MoveFileCommand(src, dst2));  // src is the current location after undo
        assertFalse(history.canRedo());
    }

    @Test
    void undo_emptyStack_throwsIllegalState() {
        CommandHistory history = new CommandHistory();
        assertThrows(IllegalStateException.class, history::undo);
    }

    @Test
    void redo_emptyStack_throwsIllegalState() {
        CommandHistory history = new CommandHistory();
        assertThrows(IllegalStateException.class, history::redo);
    }

    @Test
    void undoSize_tracksStackDepth() throws IOException {
        Path src = newFile("s.txt");
        Path mid = tempDir.resolve("mid.txt");
        Path end = tempDir.resolve("end.txt");

        CommandHistory history = new CommandHistory();
        assertEquals(0, history.undoSize());

        history.execute(new MoveFileCommand(src, mid));
        assertEquals(1, history.undoSize());

        history.execute(new MoveFileCommand(mid, end));
        assertEquals(2, history.undoSize());

        history.undo();
        assertEquals(1, history.undoSize());
    }

    @Test
    void clear_resetsBothStacks() throws IOException {
        Path src = newFile("c.txt");
        Path dst = tempDir.resolve("cd.txt");

        CommandHistory history = new CommandHistory();
        history.execute(new MoveFileCommand(src, dst));
        history.undo();
        assertTrue(history.canRedo());

        history.clear();
        assertFalse(history.canUndo());
        assertFalse(history.canRedo());
        assertEquals(0, history.undoSize());
    }
}
