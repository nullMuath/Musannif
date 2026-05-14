package org.app.musannif.model;

import org.app.musannif.model.category.*;
import org.app.musannif.model.command.CommandHistory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileOrganizerFacadeTest {

    @TempDir
    Path sourceDir;

    @TempDir
    Path targetDir;

    private void createFile(Path dir, String name) throws IOException {
        Files.writeString(dir.resolve(name), "content");
    }

    // -----------------------------------------------------------------------
    // withDefaultCategories
    // -----------------------------------------------------------------------

    @Test
    void organize_withDefaultCategories_movesFiles() throws IOException {
        createFile(sourceDir, "report.pdf");
        createFile(sourceDir, "photo.jpg");
        createFile(sourceDir, "song.mp3");

        FileOrganizerFacade facade = new FileOrganizerFacade.Builder()
                .withDefaultCategories()
                .build();

        FileOrganizer.OrganizationResult result = facade.organize(sourceDir, targetDir);
        assertEquals(3, result.movedFiles());
        assertEquals(0, result.skippedFiles());
        assertTrue(Files.exists(targetDir.resolve("Documents").resolve("report.pdf")));
        assertTrue(Files.exists(targetDir.resolve("Images").resolve("photo.jpg")));
        assertTrue(Files.exists(targetDir.resolve("Audio").resolve("song.mp3")));
    }

    @Test
    void organize_unknownExtension_goesToOther() throws IOException {
        createFile(sourceDir, "weird.xyz");

        FileOrganizerFacade facade = new FileOrganizerFacade.Builder()
                .withDefaultCategories()
                .build();
        facade.organize(sourceDir, targetDir);

        assertTrue(Files.exists(targetDir.resolve("Other").resolve("weird.xyz")));
    }

    // -----------------------------------------------------------------------
    // Custom categorizer
    // -----------------------------------------------------------------------

    @Test
    void organize_withCustomCategorizer_usesIt() throws IOException {
        createFile(sourceDir, "a.txt");
        createFile(sourceDir, "b.jpg");

        DateFileCategorizer dateCat = new DateFileCategorizer.Builder()
                .period(new Periods.ByYear())
                .build();

        FileOrganizerFacade facade = new FileOrganizerFacade.Builder()
                .withCategorizer(dateCat)
                .build();

        FileOrganizer.OrganizationResult result = facade.organize(sourceDir, targetDir);
        assertEquals(2, result.movedFiles());
    }

    // -----------------------------------------------------------------------
    // addCategory
    // -----------------------------------------------------------------------

    @Test
    void organize_addCategory_onlyRegisteredCategoryUsed() throws IOException {
        createFile(sourceDir, "doc.pdf");
        createFile(sourceDir, "img.png");

        FileOrganizerFacade facade = new FileOrganizerFacade.Builder()
                .addCategory(new Categories.Documents())
                .build();
        facade.organize(sourceDir, targetDir);

        assertTrue(Files.exists(targetDir.resolve("Documents").resolve("doc.pdf")));
        // png is "Other"
        assertTrue(Files.exists(targetDir.resolve("Other").resolve("img.png")));
    }

    // -----------------------------------------------------------------------
    // skipHidden and maxDepth
    // -----------------------------------------------------------------------

    @Test
    void organize_maxDepth_limitsDepthOfScan() throws IOException {
        createFile(sourceDir, "root.txt");
        Path sub = Files.createDirectory(sourceDir.resolve("sub"));
        Files.writeString(sub.resolve("level1.txt"), "l1");
        Path deep = Files.createDirectory(sub.resolve("deep"));
        Files.writeString(deep.resolve("level2.txt"), "l2");

        // maxDepth(2) visits root + sub (depth 1 dir) but NOT sub/deep (depth 2 dir)
        // So: root.txt + sub/level1.txt = 2 files; sub/deep/level2.txt excluded
        FileOrganizerFacade facade = new FileOrganizerFacade.Builder()
                .addCategory(new Categories.Documents())
                .maxDepth(2)
                .build();

        FileOrganizer.OrganizationResult result = facade.organize(sourceDir, targetDir);
        assertEquals(2, result.movedFiles());
    }

    // -----------------------------------------------------------------------
    // getOrganizer
    // -----------------------------------------------------------------------

    @Test
    void getOrganizer_returnsNonNull() {
        FileOrganizerFacade facade = new FileOrganizerFacade.Builder()
                .withDefaultCategories()
                .build();
        assertNotNull(facade.getOrganizer());
    }

    @Test
    void sharedCommandHistory_reflectsInFacade() throws IOException {
        createFile(sourceDir, "cmd.pdf");

        CommandHistory shared = new CommandHistory();
        FileOrganizerFacade facade = new FileOrganizerFacade.Builder()
                .withDefaultCategories()
                .commandHistory(shared)
                .build();

        facade.organize(sourceDir, targetDir);
        assertTrue(shared.canUndo());
    }

    // -----------------------------------------------------------------------
    // Null checks
    // -----------------------------------------------------------------------

    @Test
    void organize_nullSource_throwsNPE() {
        FileOrganizerFacade facade = new FileOrganizerFacade.Builder()
                .withDefaultCategories().build();
        assertThrows(NullPointerException.class,
                () -> facade.organize(null, targetDir));
    }

    @Test
    void organize_nullTarget_throwsNPE() {
        FileOrganizerFacade facade = new FileOrganizerFacade.Builder()
                .withDefaultCategories().build();
        assertThrows(NullPointerException.class,
                () -> facade.organize(sourceDir, null));
    }

    @Test
    void builder_nullCategory_throwsNPE() {
        assertThrows(NullPointerException.class,
                () -> new FileOrganizerFacade.Builder().addCategory(null));
    }

    @Test
    void builder_nullCategorizer_throwsNPE() {
        assertThrows(NullPointerException.class,
                () -> new FileOrganizerFacade.Builder().withCategorizer(null));
    }

    @Test
    void builder_invalidMaxDepth_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> new FileOrganizerFacade.Builder().maxDepth(-2));
    }
}
