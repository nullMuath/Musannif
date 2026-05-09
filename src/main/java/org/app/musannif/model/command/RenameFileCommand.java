package org.app.musannif.model.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Command pattern — encapsulates a single file-rename (or same-directory move).
 *
 * <p>{@link #execute()} renames {@code source} to {@code destination}.
 * {@link #undo()} renames it back.</p>
 */
public class RenameFileCommand implements FileCommand {

    private final Path source;
    private final Path destination;
    private boolean executed = false;

    public RenameFileCommand(Path source, Path destination) {
        this.source = source;
        this.destination = destination;
    }

    @Override
    public void execute() throws IOException {
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