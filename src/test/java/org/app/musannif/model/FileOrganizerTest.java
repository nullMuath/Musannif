package org.app.musannif.model;

import org.app.musannif.model.command.CommandHistory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileOrganizerTest {

    @TempDir
    Path tempDir;

    private ScannedFile scannedFile(Path path, String ext) throws IOException {
        Files.writeString(path, "data");
        return new ScannedFile(path, ext, 4L, Instant.now());
    }

    // -----------------------------------------------------------------------
    // applyCategorization basics
    // -----------------------------------------------------------------------

    @Test
    void organize_movesFilesToCategoryFolders() throws IOException {
        Path src = tempDir.resolve("source");
        Path target = tempDir.resolve("target");
        Files.createDirectories(src);

        ScannedFile pdf = scannedFile(src.resolve("doc.pdf"), "pdf");
        ScannedFile jpg = scannedFile(src.resolve("img.jpg"), "jpg");

        Map<String, List<ScannedFile>> categorized = Map.of(
                "Documents", List.of(pdf),
                "Images", List.of(jpg)
        );

        FileOrganizer organizer = new FileOrganizer();
        FileOrganizer.OrganizationResult result = organizer.applyCategorization(categorized, target);

        assertEquals(2, result.movedFiles());
        assertEquals(0, result.skippedFiles());
        assertTrue(Files.exists(target.resolve("Documents").resolve("doc.pdf")));
        assertTrue(Files.exists(target.resolve("Images").resolve("img.jpg")));
    }

    @Test
    void organize_emptyCategory_skipped() throws IOException {
        Path target = tempDir.resolve("target");
        Map<String, List<ScannedFile>> categorized = Map.of(
                "Documents", List.of()
        );

        FileOrganizer organizer = new FileOrganizer();
        FileOrganizer.OrganizationResult result = organizer.applyCategorization(categorized, target);

        assertEquals(0, result.movedFiles());
    }

    @Test
    void organize_nullCategory_skipped() throws IOException {
        Path target = tempDir.resolve("target");
        Map<String, List<ScannedFile>> categorized = new java.util.HashMap<>();
        categorized.put("Documents", null);

        FileOrganizer organizer = new FileOrganizer();
        FileOrganizer.OrganizationResult result = organizer.applyCategorization(categorized, target);
        assertEquals(0, result.movedFiles());
    }

    @Test
    void organize_nonExistentSourceFile_skipped() throws IOException {
        Path target = tempDir.resolve("target");
        // File doesn't actually exist on disk
        ScannedFile ghost = new ScannedFile(tempDir.resolve("ghost.pdf"), "pdf", 0L, Instant.now());

        Map<String, List<ScannedFile>> categorized = Map.of("Documents", List.of(ghost));
        FileOrganizer organizer = new FileOrganizer();
        FileOrganizer.OrganizationResult result = organizer.applyCategorization(categorized, target);

        assertEquals(0, result.movedFiles());
    }

    @Test
    void organize_sanitizesFolderName() throws IOException {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Path target = tempDir.resolve("target");

        ScannedFile f = scannedFile(src.resolve("file.txt"), "txt");
        // Category name with illegal chars
        Map<String, List<ScannedFile>> categorized = Map.of("My:Folder*Name", List.of(f));

        FileOrganizer organizer = new FileOrganizer();
        organizer.applyCategorization(categorized, target);

        // sanitized folder should exist
        assertTrue(Files.exists(target.resolve("My_Folder_Name")));
    }

    @Test
    void organize_duplicateDestination_renamedWithCounter() throws IOException {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Path target = tempDir.resolve("target");

        Path p1 = src.resolve("doc1.pdf");
        Path p2 = src.resolve("doc2.pdf");
        // Make two files that will both try to land at Documents/doc.pdf by having same name
        Path dup1 = src.resolve("dup.pdf");
        Path dup2src = tempDir.resolve("extra");
        Files.createDirectories(dup2src);
        Path dup2 = dup2src.resolve("dup.pdf");

        Files.writeString(dup1, "first");
        Files.writeString(dup2, "second");

        ScannedFile sf1 = new ScannedFile(dup1, "pdf", 5L, Instant.now());
        ScannedFile sf2 = new ScannedFile(dup2, "pdf", 6L, Instant.now());

        Map<String, List<ScannedFile>> categorized = Map.of("Documents", List.of(sf1, sf2));
        FileOrganizer organizer = new FileOrganizer();
        FileOrganizer.OrganizationResult result = organizer.applyCategorization(categorized, target);

        assertEquals(2, result.movedFiles());
        // Both files moved; one renamed with counter
        assertTrue(Files.exists(target.resolve("Documents").resolve("dup.pdf")));
        assertTrue(Files.exists(target.resolve("Documents").resolve("dup (1).pdf")));
    }

    // -----------------------------------------------------------------------
    // Undo / Redo
    // -----------------------------------------------------------------------

    @Test
    void undoLastMove_revertsMove() throws IOException {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Path target = tempDir.resolve("target");
        ScannedFile f = scannedFile(src.resolve("f.txt"), "txt");

        FileOrganizer organizer = new FileOrganizer();
        organizer.applyCategorization(Map.of("Docs", List.of(f)), target);

        assertTrue(organizer.canUndo());
        organizer.undoLastMove();
        assertTrue(Files.exists(src.resolve("f.txt")));
    }

    @Test
    void redoLastMove_reappliesMove() throws IOException {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Path target = tempDir.resolve("target");
        ScannedFile f = scannedFile(src.resolve("g.txt"), "txt");

        FileOrganizer organizer = new FileOrganizer();
        organizer.applyCategorization(Map.of("Docs", List.of(f)), target);
        organizer.undoLastMove();

        assertTrue(organizer.canRedo());
        organizer.redoLastMove();
        assertTrue(Files.exists(target.resolve("Docs").resolve("g.txt")));
    }

    // -----------------------------------------------------------------------
    // Memento
    // -----------------------------------------------------------------------

    @Test
    void getLastMemento_isNullBeforeAnyOrganize() {
        FileOrganizer organizer = new FileOrganizer();
        assertNull(organizer.getLastMemento());
    }

    @Test
    void getLastMemento_capturedAfterOrganize() throws IOException {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Path target = tempDir.resolve("target");
        ScannedFile f = scannedFile(src.resolve("memo.txt"), "txt");

        FileOrganizer organizer = new FileOrganizer();
        organizer.applyCategorization(Map.of("Cat", List.of(f)), target);

        assertNotNull(organizer.getLastMemento());
        assertEquals(1, organizer.getLastMemento().size());
    }

    @Test
    void memento_restore_movesFilesBack() throws IOException {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Path target = tempDir.resolve("target");
        ScannedFile f = scannedFile(src.resolve("restore.txt"), "txt");

        FileOrganizer organizer = new FileOrganizer();
        organizer.applyCategorization(Map.of("Cat", List.of(f)), target);
        organizer.getLastMemento().restore();

        assertTrue(Files.exists(src.resolve("restore.txt")));
    }

    // -----------------------------------------------------------------------
    // Shared CommandHistory constructor
    // -----------------------------------------------------------------------

    @Test
    void sharedCommandHistory_constructor() throws IOException {
        CommandHistory shared = new CommandHistory();
        FileOrganizer organizer = new FileOrganizer(shared);

        Path src = tempDir.resolve("src2");
        Files.createDirectories(src);
        Path target = tempDir.resolve("target2");
        ScannedFile f = scannedFile(src.resolve("sh.txt"), "txt");

        organizer.applyCategorization(Map.of("Cat", List.of(f)), target);
        assertTrue(shared.canUndo());
    }

    @Test
    void nullCategorizedFiles_throwsNPE() {
        FileOrganizer organizer = new FileOrganizer();
        assertThrows(NullPointerException.class,
                () -> organizer.applyCategorization(null, tempDir));
    }

    @Test
    void nullTargetDirectory_throwsNPE() {
        FileOrganizer organizer = new FileOrganizer();
        assertThrows(NullPointerException.class,
                () -> organizer.applyCategorization(Map.of(), null));
    }
}
