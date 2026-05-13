package org.app.musannif.model.command;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Invoker for the Command pattern.
 *
 * Executes {@link FileCommand} objects and keeps a history stack so that
 * any executed command can be undone in LIFO order.  A redo stack is also
 * maintained so that undone commands can be re-applied.
 *
 * Usage example
 * {@code
 * CommandHistory history = new CommandHistory();
 * history.execute(new MoveFileCommand(src, dst));
 * history.undo();   // moves the file back
 * history.redo();   // moves it again
 * }
 */
public class CommandHistory {

    private final Deque<FileCommand> undoStack = new ArrayDeque<>();
    private final Deque<FileCommand> redoStack = new ArrayDeque<>();

    /**
     * Executes the given command and pushes it onto the undo stack.
     * Executing a new command clears the redo stack (standard UX behaviour).
     *
     * @param command the command to execute
     * @throws IOException if the underlying file operation fails
     */
    public void execute(FileCommand command) throws IOException {
        command.execute();
        undoStack.push(command);
        redoStack.clear();     // branching history: new action discards redo stack
    }

    /**
     * Undoes the most recently executed command.
     *
     * @throws IOException              if the undo operation fails
     * @throws IllegalStateException    if there is nothing to undo
     */
    public void undo() throws IOException {
        if (undoStack.isEmpty()) {
            throw new IllegalStateException("Nothing to undo");
        }
        FileCommand command = undoStack.pop();
        command.undo();
        redoStack.push(command);
    }

    /**
     * Re-applies the most recently undone command.
     *
     * @throws IOException              if the redo operation fails
     * @throws IllegalStateException    if there is nothing to redo
     */
    public void redo() throws IOException {
        if (redoStack.isEmpty()) {
            throw new IllegalStateException("Nothing to redo");
        }
        FileCommand command = redoStack.pop();
        command.execute();
        undoStack.push(command);
    }

    /** @return {@code true} if there is at least one command that can be undone */
    public boolean canUndo() { return !undoStack.isEmpty(); }

    /** @return {@code true} if there is at least one command that can be redone */
    public boolean canRedo() { return !redoStack.isEmpty(); }

    /** Returns the number of commands currently on the undo stack. */
    public int undoSize() { return undoStack.size(); }

    /** Clears both undo and redo stacks (e.g. after saving a snapshot). */
    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }
}
