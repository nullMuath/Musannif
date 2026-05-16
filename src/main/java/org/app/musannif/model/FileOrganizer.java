package org.app.musannif.model;

import org.app.musannif.model.core.command.CommandHistory;
import org.app.musannif.model.core.command.MoveFileCommand;
import org.app.musannif.model.core.category.ExtensionFileCategorizer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Applies a categorization result by creating category folders in the target
 * directory and moving each file into its matching category folder.
 *
 * Design patterns integrated
 *
 *   Command — each file move is a {@link MoveFileCommand}
 *       executed via a shared {@link CommandHistory}.  This enables per-file
 *       undo/redo.
 *   Memento — before any file is moved a
 *       {@link OrganizationMemento} snapshot is captured and stored.  Callers
 *       can retrieve it via {@link #getLastMemento()} and call
 *       {@link OrganizationMemento#restore()} to undo the entire operation at
 *       once.
 *
 */
public class FileOrganizer {

    private final CommandHistory commandHistory;
    /** Snapshot captured just before the most recent organize run. */
    private OrganizationMemento lastMemento;

    /** Creates a FileOrganizer backed by the given shared command history. */
    public FileOrganizer(CommandHistory commandHistory) {
        this.commandHistory = Objects.requireNonNull(commandHistory);
    }

    /** Creates a FileOrganizer with its own private command history. */
    public FileOrganizer() {
        this(new CommandHistory());
    }

    // -------------------------------------------------------------------------
    //  Core API
    // -------------------------------------------------------------------------

    /**
     * Applies a categorization result: creates category folders and moves files.
     *
     * @param categorizedFiles map from category name to files in that category
     * @param targetDirectory  directory where category sub-folders are created
     * @return summary of the operation
     * @throws IOException if folders cannot be created or files cannot be moved
     */
    public OrganizationResult applyCategorization(
            Map<String, List<ScannedFile>> categorizedFiles,
            Path targetDirectory
    ) throws IOException {
        Objects.requireNonNull(categorizedFiles, "categorizedFiles must not be null");
        Objects.requireNonNull(targetDirectory, "targetDirectory must not be null");

        Files.createDirectories(targetDirectory);

        // --- Memento: collect all planned moves before executing any ---
        List<OrganizationMemento.FileMoveRecord> plannedMoves = new ArrayList<>();

        for (Map.Entry<String, List<ScannedFile>> entry : categorizedFiles.entrySet()) {
            String categoryName = sanitizeFolderName(entry.getKey());
            List<ScannedFile> files = entry.getValue();
            if (files == null || files.isEmpty()) continue;

            Path categoryDirectory = targetDirectory.resolve(categoryName);

            for (ScannedFile scannedFile : files) {
                if (scannedFile == null || scannedFile.path() == null
                        || !Files.exists(scannedFile.path())) continue;

                Path destination = resolveAvailableDestination(
                        categoryDirectory, scannedFile.path().getFileName());

                plannedMoves.add(new OrganizationMemento.FileMoveRecord(
                        scannedFile.path(), destination));
            }
        }

        // Capture memento BEFORE any file is actually moved
        lastMemento = OrganizationMemento.capture(plannedMoves);

        // --- Command: execute each move via CommandHistory ---
        int movedFiles   = 0;
        int skippedFiles = 0;

        for (OrganizationMemento.FileMoveRecord move : plannedMoves) {
            try {
                Files.createDirectories(move.destination().getParent());
                MoveFileCommand cmd = new MoveFileCommand(move.source(), move.destination());
                commandHistory.execute(cmd);
                movedFiles++;
            } catch (IOException e) {
                System.err.println("Skipping " + move.source() + ": " + e.getMessage());
                skippedFiles++;
            }
        }

        return new OrganizationResult(movedFiles, skippedFiles);
    }

    // -------------------------------------------------------------------------
    //  Undo / Redo (Command pattern)
    // -------------------------------------------------------------------------

    /** Undoes the last individual file move. */
    public void undoLastMove() throws IOException { commandHistory.undo(); }

    /** Re-applies the last undone file move. */
    public void redoLastMove() throws IOException { commandHistory.redo(); }

    public boolean canUndo() { return commandHistory.canUndo(); }
    public boolean canRedo() { return commandHistory.canRedo(); }

    // -------------------------------------------------------------------------
    //  Memento access
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link OrganizationMemento} captured before the most recent
     * {@link #applyCategorization} call, or {@code null} if never called.
     * Call {@link OrganizationMemento#restore()} to undo the entire batch.
     */
    public OrganizationMemento getLastMemento() { return lastMemento; }

    // -------------------------------------------------------------------------
    //  Private helpers
    // -------------------------------------------------------------------------

    private String sanitizeFolderName(String folderName) {
        if (folderName == null || folderName.isBlank()) {
            return ExtensionFileCategorizer.FALLBACK_CATEGORY;
        }
        return folderName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private Path resolveAvailableDestination(Path directory, Path fileName) {
        Path destination = directory.resolve(fileName);
        if (!Files.exists(destination)) return destination;

        String originalFileName = fileName.toString();
        String baseName  = getBaseName(originalFileName);
        String extension = getExtensionWithDot(originalFileName);

        int counter = 1;
        while (Files.exists(destination)) {
            destination = directory.resolve(baseName + " (" + counter + ")" + extension);
            counter++;
        }
        return destination;
    }

    private String getBaseName(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex <= 0) ? fileName : fileName.substring(0, dotIndex);
    }

    private String getExtensionWithDot(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex <= 0 || dotIndex == fileName.length() - 1)
                ? "" : fileName.substring(dotIndex);
    }

    // -------------------------------------------------------------------------

    public record OrganizationResult(int movedFiles, int skippedFiles) {}
}
