package org.app.musannif.model.command;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Command pattern — encapsulates a single file-copy operation.
 *
 * <p>{@link #execute()} copies {@code source → destination} (parent directories
 * are created if they don't exist).
 * {@link #undo()} deletes the copy at {@code destination}.</p>
 */
public class CopyFileCommand implements FileCommand {

    private final Path source;
    private final Path destination;
    private boolean executed = false;

    public CopyFileCommand(Path source, Path destination) {
        this.source = source;
        this.destination = destination;
    }

    @Override
    public void execute() throws IOException {
        if (destination.getParent() != null) {
            Files.createDirectories(destination.getParent());
        }
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        executed = true;
    }

    @Override
    public void undo() throws IOException {
        if (!executed) return;
        if (Files.exists(destination)) {
            Files.delete(destination);
        }
        executed = false;
    }

    public Path getSource()      { return source; }
    public Path getDestination() { return destination; }
}