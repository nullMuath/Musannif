package org.app.musannif.model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Memento pattern — immutable snapshot of the file locations captured
 * before a bulk organize operation.
 *
 * Storing a memento before organizing allows the entire operation to be
 * rolled back at once (vs. relying on the per-command undo stack in
 * {@link org.app.musannif.model.command.CommandHistory}).
 *
 * Typical usage
 * {@code
 * // Before organizing:
 * OrganizationMemento snapshot = OrganizationMemento.capture(commandsAboutToExecute);
 * mementoStack.push(snapshot);
 *
 * // User hits "Undo All":
 * OrganizationMemento last = mementoStack.pop();
 * last.restore();   // moves every file back to its original location
 * }
 */
public final class OrganizationMemento {

    /**
     * Maps each destination path (where the file was moved <em>to</em>)
     * back to its original source path.
     */
    private final Map<Path, Path> destinationToSource;
    private final Instant capturedAt;

    private OrganizationMemento(Map<Path, Path> destinationToSource) {
        this.destinationToSource = Collections.unmodifiableMap(
                new LinkedHashMap<>(destinationToSource));
        this.capturedAt = Instant.now();
    }

    /**
     * Creates a memento from a list of (source, destination) pairs.
     *
     * @param moves list of {@link FileMoveRecord} objects — each records one
     *              file's source and destination before the move happens
     * @return a new, immutable memento
     */
    public static OrganizationMemento capture(List<FileMoveRecord> moves) {
        Map<Path, Path> map = new LinkedHashMap<>();
        for (FileMoveRecord move : moves) {
            map.put(move.destination(), move.source());
        }
        return new OrganizationMemento(map);
    }

    /**
     * Restores all files to their original locations by moving them back.
     * Files that have since been moved again or deleted are skipped with a
     * warning printed to stderr.
     *
     * @throws java.io.IOException if any move fails
     */
    public void restore() throws java.io.IOException {
        for (Map.Entry<Path, Path> entry : destinationToSource.entrySet()) {
            Path destination = entry.getKey();
            Path source      = entry.getValue();
            if (java.nio.file.Files.exists(destination)) {
                java.nio.file.Files.move(destination, source,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } else {
                System.err.println("Memento restore: file no longer at expected location — "
                        + destination);
            }
        }
        // Remove empty category directories that were created during organization
        destinationToSource.keySet().stream()
                .map(Path::getParent)
                .distinct()
                .forEach(dir -> { try { java.nio.file.Files.delete(dir); } catch (java.io.IOException ignored) {} });
    }

    /** @return when this snapshot was taken */
    public Instant getCapturedAt() { return capturedAt; }

    /** @return number of file moves recorded in this snapshot */
    public int size() { return destinationToSource.size(); }

    /**
     * Returns the underlying destination→source mapping.
     * The returned map is unmodifiable.
     */
    public Map<Path, Path> getDestinationToSource() { return destinationToSource; }

    /**
     * Reconstructs a memento from a pre-built destination→source map.
     * Used by {@link org.app.musannif.model.history.SnapshotManager}
     * when loading snapshots from disk.
     */
    public static OrganizationMemento fromMap(Map<Path, Path> map) {
        return new OrganizationMemento(map);
    }

    // -------------------------------------------------------------------------
    //  Nested record — used when capturing the memento
    // -------------------------------------------------------------------------

    /**
     * Simple value object carrying a (source, destination) pair for one file move.
     */
    public record FileMoveRecord(Path source, Path destination) {}
}
