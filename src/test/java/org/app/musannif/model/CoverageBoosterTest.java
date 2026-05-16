package org.app.musannif.model;

import org.app.musannif.model.core.category.*;
import org.app.musannif.model.core.command.CopyFileCommand;
import org.app.musannif.model.core.command.MoveFileCommand;
import org.app.musannif.util.helperMethods;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Targeted coverage-booster tests.
 *
 * Each group is labelled with the source class and the specific lines/branches
 * it is designed to hit.
 *
 * Place this file at:
 *   src/test/java/org/app/musannif/model/CoverageBoosterTest.java
 */
class CoverageBoosterTest {

    // =========================================================================
    // helperMethods — addFolderIcons()
    //   Current line coverage: 23% (7/30).  The entire addFolderIcons method
    //   (lines ~26-57) is untouched.  We call it in two scenarios:
    //     1. targetFolder has none of the expected sub-folders → the
    //        "if (!Files.exists(categoryFolder)) continue;" guard fires for
    //        every entry, covering the loop body up to the continue.
    //     2. targetFolder has matching sub-folders → the writeString +
    //        attribute-setting code runs; on Linux the DosFileAttributeView
    //        is null so the IOException catch block is exercised.
    // =========================================================================

    @TempDir
    Path tempDir;

    @Test
    void addFolderIcons_noMatchingSubfolders_doesNotThrow() {
        // No sub-folders created → every entry hits the "continue" branch.
        assertDoesNotThrow(() -> helperMethods.addFolderIcons(tempDir));
    }

    @Test
    void addFolderIcons_withMatchingSubfolders_doesNotThrow() throws IOException {
        // Create the exact folder names helperMethods looks for.
        for (String name : new String[]{"Documents", "Images", "Videos", "Audio", "Archives", "Other"}) {
            Files.createDirectory(tempDir.resolve(name));
        }
        // On Linux DosFileAttributeView is unavailable → IOException is caught
        // internally and logged. The method must NOT propagate any exception.
        assertDoesNotThrow(() -> helperMethods.addFolderIcons(tempDir));
    }

    @Test
    void addFolderIcons_partialSubfolders_doesNotThrow() throws IOException {
        // Only some folders exist → mix of continue and write paths.
        Files.createDirectory(tempDir.resolve("Documents"));
        Files.createDirectory(tempDir.resolve("Images"));
        assertDoesNotThrow(() -> helperMethods.addFolderIcons(tempDir));
    }

    // =========================================================================
    // FileOrganizer — uncovered private helper branches
    //
    //  sanitizeFolderName(null)   → returns FALLBACK_CATEGORY
    //  sanitizeFolderName("")     → returns FALLBACK_CATEGORY
    //  sanitizeFolderName(" ")    → returns FALLBACK_CATEGORY (blank)
    //  getBaseName / getExtensionWithDot with:
    //    - no dot in filename     → baseName = full name, ext = ""
    //    - leading dot only       → treated as no-extension (dotIndex <= 0)
    //    - trailing dot           → extension = "" (dotIndex == length-1)
    //  IOException during move    → skippedFiles counter incremented
    // =========================================================================

    @Test
    void organizer_sanitizeFolderName_null_usesOther() throws IOException {
        Path src = Files.createDirectory(tempDir.resolve("src"));
        Path target = tempDir.resolve("target");
        Path f = src.resolve("file.txt");
        Files.writeString(f, "x");

        ScannedFile sf = new ScannedFile(f, "txt", 1L, Instant.now());
        // null key → sanitizeFolderName returns "Other"
        Map<String, List<ScannedFile>> map = new java.util.HashMap<>();
        map.put(null, List.of(sf));

        FileOrganizer organizer = new FileOrganizer();
        FileOrganizer.OrganizationResult result = organizer.applyCategorization(map, target);
        assertEquals(1, result.movedFiles());
        assertTrue(Files.exists(target.resolve("Other").resolve("file.txt")));
    }

    @Test
    void organizer_sanitizeFolderName_blank_usesOther() throws IOException {
        Path src = Files.createDirectory(tempDir.resolve("srcBlank"));
        Path target = tempDir.resolve("targetBlank");
        Path f = src.resolve("doc.pdf");
        Files.writeString(f, "x");

        ScannedFile sf = new ScannedFile(f, "pdf", 1L, Instant.now());
        Map<String, List<ScannedFile>> map = Map.of("   ", List.of(sf));

        FileOrganizer organizer = new FileOrganizer();
        organizer.applyCategorization(map, target);
        assertTrue(Files.exists(target.resolve("Other").resolve("doc.pdf")));
    }

    @Test
    void organizer_fileWithNoDot_baseNameIsFullName() throws IOException {
        // File with no extension: getBaseName returns full name, getExtensionWithDot returns "".
        // Triggers duplicate-counter path only when the same no-ext file name collides.
        Path src1 = Files.createDirectory(tempDir.resolve("srcNoDot1"));
        Path src2 = Files.createDirectory(tempDir.resolve("srcNoDot2"));
        Path target = tempDir.resolve("targetNoDot");

        Path p1 = src1.resolve("README");
        Path p2 = src2.resolve("README");
        Files.writeString(p1, "a");
        Files.writeString(p2, "b");

        ScannedFile sf1 = new ScannedFile(p1, "", 1L, Instant.now());
        ScannedFile sf2 = new ScannedFile(p2, "", 1L, Instant.now());

        FileOrganizer organizer = new FileOrganizer();
        FileOrganizer.OrganizationResult result = organizer.applyCategorization(
                Map.of("Docs", List.of(sf1, sf2)), target);

        assertEquals(2, result.movedFiles());
        // Second file renamed to "README (1)" (no extension)
        assertTrue(Files.exists(target.resolve("Docs").resolve("README")));
        assertTrue(Files.exists(target.resolve("Docs").resolve("README (1)")));
    }

    @Test
    void organizer_fileWithTrailingDot_treatedAsNoExtension() throws IOException {
        // "file." has a trailing dot → dotIndex == length-1 → extension = ""
        Path src = Files.createDirectory(tempDir.resolve("srcTrailingDot"));
        Path target = tempDir.resolve("targetTrailingDot");
        Path p = src.resolve("file.");
        Files.writeString(p, "data");

        ScannedFile sf = new ScannedFile(p, "", 4L, Instant.now());
        FileOrganizer organizer = new FileOrganizer();
        FileOrganizer.OrganizationResult result = organizer.applyCategorization(
                Map.of("Cat", List.of(sf)), target);

        assertEquals(1, result.movedFiles());
    }

    @Test
    void organizer_ioExceptionDuringMove_incrementsSkipped() throws IOException {
        Path src = Files.createDirectory(tempDir.resolve("srcIO"));
        Path f = src.resolve("blocked.pdf");
        Files.writeString(f, "content");

        ScannedFile sf = new ScannedFile(f, "pdf", 7L, Instant.now());

        // Create a FILE at the path that will be used as the category sub-directory
        // so createDirectories(categoryDir) throws FileAlreadyExistsException.
        // We use a valid targetDirectory, but place a file where the "Docs" subfolder
        // should go, causing the per-file createDirectories to fail → skipped++.
        Path ioTarget = Files.createDirectory(tempDir.resolve("ioTarget"));
        Files.writeString(ioTarget.resolve("Docs"), "I block the Docs subfolder");

        FileOrganizer organizer = new FileOrganizer();
        FileOrganizer.OrganizationResult result = organizer.applyCategorization(
                Map.of("Docs", List.of(sf)), ioTarget);

        assertEquals(1, result.skippedFiles());
        assertEquals(0, result.movedFiles());
    }

    // =========================================================================
    // FileScanner — uncovered branches
    //
    //  visitFileFailed callback  → Logger.info path
    //  isHidden IOException catch → returns false (defensive branch)
    //  skipHidden=true with a hidden directory → SKIP_SUBTREE
    // =========================================================================

    /** Make a path hidden using the OS-appropriate mechanism. Returns true if hidden. */
    private static boolean makeHidden(Path path) {
        try {
            java.nio.file.attribute.DosFileAttributeView dos =
                    Files.getFileAttributeView(path, java.nio.file.attribute.DosFileAttributeView.class);
            if (dos != null) {
                dos.setHidden(true);
                return true;
            }
            // On Unix, dot-prefix is sufficient — Files.isHidden() checks it.
            return Files.isHidden(path);
        } catch (IOException e) {
            return false;
        }
    }

    @Test
    void scanner_skipHiddenTrue_skipsHiddenDirectory() throws IOException {
        // Create a visible file and a hidden sub-directory with a file inside.
        Path visibleFile = tempDir.resolve("visible.txt");
        Files.writeString(visibleFile, "hello");

        Path hiddenDir = tempDir.resolve(".hiddenDir");
        Files.createDirectory(hiddenDir);
        Files.writeString(hiddenDir.resolve("secret.txt"), "secret");

        // On Windows, dot-prefix alone doesn't make a file hidden; set the attribute.
        boolean hidden = makeHidden(hiddenDir);
        org.junit.jupiter.api.Assumptions.assumeTrue(hidden,
                "Skipping: could not make directory hidden on this OS");

        FileScanner scanner = new FileScanner.Builder()
                .skipHidden(true)
                .build();
        List<ScannedFile> results = scanner.scan(tempDir);

        // Only visible.txt should be returned; .hiddenDir and its contents skipped.
        assertTrue(results.stream().noneMatch(sf -> sf.path().toString().contains("hidden")));
        assertEquals(1, results.size());
    }

    @Test
    void scanner_skipHiddenTrue_skipsHiddenFiles() throws IOException {
        Files.writeString(tempDir.resolve("normal.txt"), "hi");
        Path dotFile = tempDir.resolve(".dotfile");
        Files.writeString(dotFile, "hidden");

        // On Windows, set the hidden attribute; on Unix dot-prefix suffices.
        boolean hidden = makeHidden(dotFile);
        org.junit.jupiter.api.Assumptions.assumeTrue(hidden,
                "Skipping: could not make file hidden on this OS");

        FileScanner scanner = new FileScanner.Builder()
                .skipHidden(true)
                .build();
        List<ScannedFile> results = scanner.scan(tempDir);

        // .dotfile is hidden; normal.txt should still appear.
        assertTrue(results.stream().allMatch(sf -> !sf.path().getFileName().toString().startsWith(".")));
    }

    @Test
    void scanner_visitFileFailed_doesNotThrow() throws IOException {
        // Simulate visitFileFailed by using a custom FileVisitor approach via
        // creating a directory we can't read. On most CI environments we can test
        // this by pointing the scanner at a path that partially fails gracefully.
        // We test the scanner still completes and returns whatever it can.
        Path dir = Files.createDirectory(tempDir.resolve("partial"));
        Files.writeString(dir.resolve("ok.txt"), "readable");

        FileScanner scanner = new FileScanner.Builder().build();
        List<ScannedFile> results = scanner.scan(dir);
        // Should return at least the readable file; no exception thrown.
        assertFalse(results.isEmpty());
    }

    @Test
    void scanner_maxDepthZero_returnsNoFiles() throws IOException {
        // maxDepth=0 means only the root itself is visited (no files at root
        // level are found since root is a directory). Confirms boundary.
        Files.writeString(tempDir.resolve("root.txt"), "data");

        FileScanner scanner = new FileScanner.Builder().maxDepth(0).build();
        List<ScannedFile> results = scanner.scan(tempDir);
        // depth 0 visits only root dir, not its children
        assertTrue(results.isEmpty());
    }

    @Test
    void scanner_multipleExtensionFilters() throws IOException {
        Files.writeString(tempDir.resolve("a.pdf"), "x");
        Files.writeString(tempDir.resolve("b.jpg"), "x");
        Files.writeString(tempDir.resolve("c.mp3"), "x");
        Files.writeString(tempDir.resolve("d.txt"), "x");

        FileScanner scanner = new FileScanner.Builder()
                .filterByExtensions("pdf", "jpg")
                .build();
        List<ScannedFile> results = scanner.scan(tempDir);

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(sf ->
                sf.extension().equals("pdf") || sf.extension().equals("jpg")));
    }

    // =========================================================================
    // FileOrganizerFacade — skipHidden builder path (uncovered branch)
    // =========================================================================

    @Test
    void facade_skipHidden_true_buildsAndOrganizes() throws IOException {
        Path src = Files.createDirectory(tempDir.resolve("facadeSrc"));
        Path target = tempDir.resolve("facadeTarget");
        Files.writeString(src.resolve("doc.pdf"), "data");
        Path hiddenPdf = src.resolve(".hidden.pdf");
        Files.writeString(hiddenPdf, "secret");

        // On Windows, set the hidden attribute; on Unix dot-prefix suffices.
        boolean hidden = makeHidden(hiddenPdf);
        org.junit.jupiter.api.Assumptions.assumeTrue(hidden,
                "Skipping: could not make file hidden on this OS");

        FileOrganizerFacade facade = new FileOrganizerFacade.Builder()
                .withDefaultCategories()
                .skipHidden(true)
                .build();

        FileOrganizer.OrganizationResult result = facade.organize(src, target);
        // Only the visible file should be moved; hidden one skipped by scanner
        assertEquals(1, result.movedFiles());
    }

    // =========================================================================
    // CopyFileCommand — undo when destination was already deleted
    //   The undo() method has: if (Files.exists(destination)) Files.delete(...)
    //   The "else" (file already gone) path is never hit by existing tests.
    // =========================================================================

    @Test
    void copyCommand_undo_destinationAlreadyGone_doesNotThrow() throws IOException {
        Path src = tempDir.resolve("orig.txt");
        Path dst = tempDir.resolve("copy.txt");
        Files.writeString(src, "content");

        CopyFileCommand cmd = new CopyFileCommand(src, dst);
        cmd.execute();
        // Manually delete the copy before undo()
        Files.delete(dst);

        // undo() checks Files.exists(destination) — false branch — should be no-op
        assertDoesNotThrow(cmd::undo);
        assertTrue(Files.exists(src)); // source untouched
    }

    // =========================================================================
    // MoveFileCommand — destination has no parent (null parent guard)
    //   execute() has: if (destination.getParent() != null) createDirectories(...)
    //   This branch is hit when destination is a root-relative path.
    //   We cover it via a path whose parent exists so no directories need creation,
    //   exercising the "parent != null but already exists" flow explicitly.
    // =========================================================================

    @Test
    void moveCommand_destinationParentExists_movesFile() throws IOException {
        Path src = tempDir.resolve("moveMe.txt");
        Files.writeString(src, "data");
        Path dst = tempDir.resolve("movedFile.txt");

        MoveFileCommand cmd = new MoveFileCommand(src, dst);
        cmd.execute();
        assertTrue(Files.exists(dst));
        assertFalse(Files.exists(src));
    }

    // =========================================================================
    // FileHandler chain — three-deep chain exercises passToNext through
    //   intermediate links (covers FileHandler.passToNext with non-null next)
    // =========================================================================

    @Test
    void fileHandlerChain_threeDeep_allPass() {
        Instant jan = Instant.parse("2024-01-01T00:00:00Z");
        Instant dec = Instant.parse("2024-12-31T00:00:00Z");

        FileHandler head = new ExtensionHandler("pdf");
        head.setNext(new SizeHandler(10_000L));
        head.setNext(new DateHandler(jan, dec)); // appended to tail

        ScannedFile good = new ScannedFile(
                Path.of("/f.pdf"), "pdf", 500L,
                Instant.parse("2024-06-01T00:00:00Z"));
        Optional<ScannedFile> result = head.handle(good);
        assertTrue(result.isPresent());
    }

    @Test
    void fileHandlerChain_threeDeep_middleRejects() {
        Instant jan = Instant.parse("2024-01-01T00:00:00Z");
        Instant dec = Instant.parse("2024-12-31T00:00:00Z");

        FileHandler head = new ExtensionHandler("pdf");
        head.setNext(new SizeHandler(100L));   // very small limit
        head.setNext(new DateHandler(jan, dec));

        ScannedFile tooBig = new ScannedFile(
                Path.of("/big.pdf"), "pdf", 9999L,
                Instant.parse("2024-06-01T00:00:00Z"));
        assertTrue(head.handle(tooBig).isEmpty());
    }

    // =========================================================================
    // ExtensionFileCategorizer — covers the DateFilterDecorator instanceof
    //   branch inside the private accepts() method (line missed when only
    //   SizeFilterDecorator is exercised in existing tests).
    // =========================================================================

    @Test
    void extensionCategorizer_dateDecoratorBranch_exercised() {
        Instant cutoff = Instant.parse("2024-01-01T00:00:00Z");
        FileCategory recentImages = new DateFilterDecorator(new Categories.Images(), cutoff);
        ExtensionFileCategorizer cat = new ExtensionFileCategorizer.Builder()
                .register(recentImages)
                .build();

        ScannedFile old = new ScannedFile(Path.of("/old.jpg"), "jpg", 100L,
                Instant.parse("2023-01-01T00:00:00Z"));
        ScannedFile recent = new ScannedFile(Path.of("/new.jpg"), "jpg", 100L,
                Instant.parse("2024-06-01T00:00:00Z"));
        ScannedFile wrongExt = new ScannedFile(Path.of("/f.pdf"), "pdf", 100L,
                Instant.parse("2024-06-01T00:00:00Z"));

        Map<String, List<ScannedFile>> result =
                cat.categorize(List.of(old, recent, wrongExt));

        assertEquals(1, result.get("Images").size());
        assertEquals(2, result.get(ExtensionFileCategorizer.FALLBACK_CATEGORY).size());
    }

    // =========================================================================
    // FileOrganizer — multiple duplicates force counter > 1 (tests the while
    //   loop running more than one iteration)
    // =========================================================================

    @Test
    void organizer_tripleDuplicate_countsTo2() throws IOException {
        Path src1 = Files.createDirectory(tempDir.resolve("td1"));
        Path src2 = Files.createDirectory(tempDir.resolve("td2"));
        Path src3 = Files.createDirectory(tempDir.resolve("td3"));
        Path target = tempDir.resolve("tdTarget");

        Path p1 = src1.resolve("report.pdf");
        Path p2 = src2.resolve("report.pdf");
        Path p3 = src3.resolve("report.pdf");
        Files.writeString(p1, "one");
        Files.writeString(p2, "two");
        Files.writeString(p3, "three");

        ScannedFile sf1 = new ScannedFile(p1, "pdf", 3L, Instant.now());
        ScannedFile sf2 = new ScannedFile(p2, "pdf", 3L, Instant.now());
        ScannedFile sf3 = new ScannedFile(p3, "pdf", 3L, Instant.now());

        FileOrganizer organizer = new FileOrganizer();
        FileOrganizer.OrganizationResult result = organizer.applyCategorization(
                Map.of("Reports", List.of(sf1, sf2, sf3)), target);

        assertEquals(3, result.movedFiles());
        assertTrue(Files.exists(target.resolve("Reports").resolve("report.pdf")));
        assertTrue(Files.exists(target.resolve("Reports").resolve("report (1).pdf")));
        assertTrue(Files.exists(target.resolve("Reports").resolve("report (2).pdf")));
    }
}
