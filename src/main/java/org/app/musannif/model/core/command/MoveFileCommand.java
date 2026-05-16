package org.app.musannif.model.core.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Command pattern — encapsulates a single file-move operation.
 *
 * {@link #execute()} moves {@code source → destination}.
 * {@link #undo()} moves it back {@code destination → source}.
 */
public class MoveFileCommand implements FileCommand {

    private final Path source;
    private final Path destination;
    /** Flipped to true once execute() has successfully moved the file. */
    private boolean executed = false;

    public MoveFileCommand(Path source, Path destination) {
        this.source = source;
        this.destination = destination;
    }

    @Override
    public void execute() throws IOException {
        if (destination.getParent() != null) {
            Files.createDirectories(destination.getParent());
        }
        Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        executed = true;
    }

    @Override
    public void undo() throws IOException {
        if (!executed) return;
        Files.move(destination, source, StandardCopyOption.ATOMIC_MOVE);
        executed = false;
    }

    public Path getSource()      { return source; }
    public Path getDestination() { return destination; }
}