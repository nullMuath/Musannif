package org.app.musannif;

// ─────────────────────────────────────────────────────────────────────────────
// Standard library
// ─────────────────────────────────────────────────────────────────────────────
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

// ─────────────────────────────────────────────────────────────────────────────
// JUnit 5
// ─────────────────────────────────────────────────────────────────────────────
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

// ─────────────────────────────────────────────────────────────────────────────
// Project – model
// ─────────────────────────────────────────────────────────────────────────────
import org.app.musannif.model.*;
import org.app.musannif.model.category.*;
import org.app.musannif.model.command.*;
import org.app.musannif.model.history.*;
import org.app.musannif.model.state.*;

// ─────────────────────────────────────────────────────────────────────────────
// Project – controller
// ─────────────────────────────────────────────────────────────────────────────
import org.app.musannif.controller.MainController;

/**
 * MusannifAllTests — single-file test suite for the entire Musannif project.
 *
 * HOW TO RUN WITH COVERAGE IN INTELLIJ IDEA
 * ──────────────────────────────────────────
 * Place this file at:
 *   src/test/java/org/app/musannif/MusannifAllTests.java
 *
 * Right-click → "Run 'MusannifAllTests' with Coverage"
 * (or use the green ▶ gutter → "Run with Coverage").
 *
 * Each inner class below corresponds to one original test file.
 * JUnit 5 discovers @Nested classes automatically — no configuration needed.
 *
 * CONTENTS (in order)
 * ───────────────────
 *  1.  ScannedFileTests
 *  2.  HelperMethodsTests
 *  3.  OrganizationMementoTests
 *  4.  FileScannerTests
 *  5.  FileOrganizerTests
 *  6.  FileOrganizerExtendedTests
 *  7.  FileOrganizerFacadeTests
 *  8.  CoverageBoosterTests
 *  9.  CategoriesTests
 * 10.  CategoriesClassTests
 * 11.  SingleExtensionCategoryTests
 * 12.  SizeFilterDecoratorTests
 * 13.  DateFilterDecoratorTests
 * 14.  ExtensionFileCategorizerTests
 * 15.  DateFileCategorizerTests
 * 16.  FileHandlerChainTests
 * 17.  CommandHistoryTests
 * 18.  FileCommandsTests
 * 19.  OperationHistoryTests
 * 20.  AppStateTests
 * 21.  MainControllerTests
 * 22.  TestFilesGeneratorTests
 */
public class MusannifAllTests {

    // =========================================================================
    // 1. ScannedFileTests
    // =========================================================================
    @Nested
    class ScannedFileTests {

        @Test
        void constructor_storesAllFields() {
            Path path = Path.of("/some/file.pdf");
            Instant now = Instant.now();
            ScannedFile f = new ScannedFile(path, "pdf", 1024L, now);

            assertEquals(path, f.path());
            assertEquals("pdf", f.extension());
            assertEquals(1024L, f.sizeBytes());
            assertEquals(now, f.lastModified());
        }

        @Test
        void toString_containsRelevantInfo() {
            Path path = Path.of("/some/file.txt");
            Instant now = Instant.parse("2024-01-15T10:00:00Z");
            ScannedFile f = new ScannedFile(path, "txt", 512L, now);

            String s = f.toString();
            assertTrue(s.contains("txt"));
            assertTrue(s.contains("512"));
        }

        @Test
        void equality_sameFields_areEqual() {
            Path path = Path.of("/a/b.mp3");
            Instant now = Instant.now();
            ScannedFile f1 = new ScannedFile(path, "mp3", 100L, now);
            ScannedFile f2 = new ScannedFile(path, "mp3", 100L, now);
            assertEquals(f1, f2);
            assertEquals(f1.hashCode(), f2.hashCode());
        }

        @Test
        void emptyExtension_isAllowed() {
            ScannedFile f = new ScannedFile(Path.of("/no/ext"), "", 0L, Instant.EPOCH);
            assertEquals("", f.extension());
        }
    }

    // =========================================================================
    // 2. HelperMethodsTests
    // =========================================================================
    @Nested
    class HelperMethodsTests {

        @TempDir Path tempDir;

        // formatFileSize -------------------------------------------------------

        @Test
        void formatFileSize_bytes() {
            assertEquals("512 B", helperMethods.formatFileSize(512));
            assertEquals("0 B",   helperMethods.formatFileSize(0));
            assertEquals("1023 B", helperMethods.formatFileSize(1023));
        }

        @Test
        void formatFileSize_kilobytes() {
            String result = helperMethods.formatFileSize(1024);
            assertTrue(result.endsWith("KB"));
            assertTrue(result.contains("1.00"));
        }

        @Test
        void formatFileSize_megabytes() {
            String result = helperMethods.formatFileSize(1024 * 1024);
            assertTrue(result.endsWith("MB"));
            assertTrue(result.contains("1.00"));
        }

        @Test
        void formatFileSize_gigabytes() {
            String result = helperMethods.formatFileSize(1024L * 1024 * 1024);
            assertTrue(result.endsWith("GB"));
            assertTrue(result.contains("1.00"));
        }

        @Test
        void formatFileSize_fractional_kb() {
            String result = helperMethods.formatFileSize(1536);
            assertTrue(result.contains("1.50"));
        }

        // formatDateTime -------------------------------------------------------

        @Test
        void formatDateTime_returnsNonNullString() {
            String result = helperMethods.formatDateTime(Instant.parse("2024-06-15T08:30:00Z"));
            assertNotNull(result);
            assertFalse(result.isBlank());
        }

        @Test
        void formatDateTime_containsDate() {
            String result = helperMethods.formatDateTime(Instant.now());
            assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"),
                    "Unexpected format: " + result);
        }

        // addFolderIcons -------------------------------------------------------

        @Test
        void addFolderIcons_noMatchingSubfolders_doesNotThrow() {
            assertDoesNotThrow(() -> helperMethods.addFolderIcons(tempDir));
        }

        @Test
        void addFolderIcons_withMatchingSubfolders_doesNotThrow() throws IOException {
            for (String name : new String[]{"Documents", "Images", "Videos", "Audio", "Archives", "Other"})
                Files.createDirectory(tempDir.resolve(name));
            assertDoesNotThrow(() -> helperMethods.addFolderIcons(tempDir));
        }

        @Test
        void addFolderIcons_partialSubfolders_doesNotThrow() throws IOException {
            Files.createDirectory(tempDir.resolve("Documents"));
            Files.createDirectory(tempDir.resolve("Images"));
            assertDoesNotThrow(() -> helperMethods.addFolderIcons(tempDir));
        }
    }

    // =========================================================================
    // 3. OrganizationMementoTests
    // =========================================================================
    @Nested
    class OrganizationMementoTests {

        @TempDir Path tempDir;

        @Test
        void capture_recordsCorrectSize() {
            Path src = tempDir.resolve("a.txt");
            Path dst = tempDir.resolve("b.txt");
            OrganizationMemento memento = OrganizationMemento.capture(
                    List.of(new OrganizationMemento.FileMoveRecord(src, dst)));
            assertEquals(1, memento.size());
        }

        @Test
        void capture_emptyList_sizeIsZero() {
            assertEquals(0, OrganizationMemento.capture(List.of()).size());
        }

        @Test
        void getCapturedAt_isNotNull() {
            assertNotNull(OrganizationMemento.capture(List.of()).getCapturedAt());
        }

        @Test
        void restore_movesFilesBack() throws IOException {
            Path src = tempDir.resolve("orig.txt");
            Path dst = tempDir.resolve("moved.txt");
            Files.writeString(src, "hello");
            Files.move(src, dst);

            OrganizationMemento.capture(
                    List.of(new OrganizationMemento.FileMoveRecord(src, dst))).restore();

            assertTrue(Files.exists(src));
            assertFalse(Files.exists(dst));
        }

        @Test
        void restore_fileAlreadyGone_doesNotThrow() {
            Path src = tempDir.resolve("gone_src.txt");
            Path dst = tempDir.resolve("gone_dst.txt");
            assertDoesNotThrow(() ->
                    OrganizationMemento.capture(
                            List.of(new OrganizationMemento.FileMoveRecord(src, dst))).restore());
        }

        @Test
        void fileMoveRecord_getters() {
            Path src = tempDir.resolve("s");
            Path dst = tempDir.resolve("d");
            OrganizationMemento.FileMoveRecord record = new OrganizationMemento.FileMoveRecord(src, dst);
            assertEquals(src, record.source());
            assertEquals(dst, record.destination());
        }
    }

    // =========================================================================
    // 4. FileScannerTests
    // =========================================================================
    @Nested
    class FileScannerTests {

        @TempDir Path tempDir;

        private Path createFile(Path dir, String name) throws IOException {
            Path f = dir.resolve(name);
            Files.writeString(f, "test content");
            return f;
        }

        @Test
        void scan_returnsAllFiles_inFlatDirectory() throws IOException {
            createFile(tempDir, "a.pdf");
            createFile(tempDir, "b.jpg");
            createFile(tempDir, "c.mp3");
            assertEquals(3, new FileScanner.Builder().build().scan(tempDir).size());
        }

        @Test
        void scan_emptyDirectory_returnsEmpty() throws IOException {
            assertTrue(new FileScanner.Builder().build().scan(tempDir).isEmpty());
        }

        @Test
        void scan_populatesExtension() throws IOException {
            createFile(tempDir, "report.pdf");
            assertEquals("pdf", new FileScanner.Builder().build().scan(tempDir).get(0).extension());
        }

        @Test
        void scan_populatesSize() throws IOException {
            Files.writeString(tempDir.resolve("sized.txt"), "12345");
            assertTrue(new FileScanner.Builder().build().scan(tempDir).get(0).sizeBytes() > 0);
        }

        @Test
        void scan_fileWithNoExtension_hasEmptyExtension() throws IOException {
            Files.writeString(tempDir.resolve("noextension"), "data");
            assertEquals("", new FileScanner.Builder().build().scan(tempDir).get(0).extension());
        }

        @Test
        void scan_filterByExtension_returnsOnlyMatching() throws IOException {
            createFile(tempDir, "doc.pdf");
            createFile(tempDir, "image.jpg");
            createFile(tempDir, "video.mp4");
            List<ScannedFile> results = new FileScanner.Builder().filterByExtensions("pdf").build().scan(tempDir);
            assertEquals(1, results.size());
            assertEquals("pdf", results.get(0).extension());
        }

        @Test
        void scan_filterByExtension_stripsLeadingDot() throws IOException {
            createFile(tempDir, "file.docx");
            createFile(tempDir, "other.pdf");
            List<ScannedFile> results = new FileScanner.Builder().filterByExtensions(".docx").build().scan(tempDir);
            assertEquals(1, results.size());
            assertEquals("docx", results.get(0).extension());
        }

        @Test
        void scan_filterByExtension_caseInsensitive() throws IOException {
            createFile(tempDir, "file.PDF");
            assertEquals(1, new FileScanner.Builder().filterByExtensions("pdf").build().scan(tempDir).size());
        }

        @Test
        void scan_maxDepth_doesNotDescendIntoDeepSubdirectories() throws IOException {
            createFile(tempDir, "root.txt");
            Path subDir = Files.createDirectory(tempDir.resolve("sub"));
            createFile(subDir, "level1.txt");
            Path deepDir = Files.createDirectory(subDir.resolve("deep"));
            createFile(deepDir, "level2.txt");
            assertEquals(2, new FileScanner.Builder().maxDepth(2).build().scan(tempDir).size());
        }

        @Test
        void scan_unlimitedDepth_findsNestedFiles() throws IOException {
            Path deep = Files.createDirectories(tempDir.resolve("a").resolve("b").resolve("c"));
            createFile(deep, "deep.txt");
            assertEquals(1, new FileScanner.Builder().maxDepth(-1).build().scan(tempDir).size());
        }

        @Test
        void builder_invalidMaxDepth_throwsIllegalArgument() {
            assertThrows(IllegalArgumentException.class, () -> new FileScanner.Builder().maxDepth(-2));
        }

        @Test
        void scan_skipHiddenFalse_includesDotFiles() throws IOException {
            Files.writeString(tempDir.resolve(".hidden.txt"), "invisible");
            assertFalse(new FileScanner.Builder().skipHidden(false).build().scan(tempDir).isEmpty());
        }

        @Test
        void scanAsStream_returnsCorrectCount() throws IOException {
            createFile(tempDir, "x.pdf");
            createFile(tempDir, "y.pdf");
            assertEquals(2, new FileScanner.Builder().build().scanAsStream(tempDir).count());
        }

        @Test
        void scan_scannedFile_pathIsAbsolute() throws IOException {
            createFile(tempDir, "abs.txt");
            assertTrue(new FileScanner.Builder().build().scan(tempDir).get(0).path().isAbsolute());
        }

        @Test
        void scan_lastModified_isNotNull() throws IOException {
            createFile(tempDir, "lm.txt");
            assertNotNull(new FileScanner.Builder().build().scan(tempDir).get(0).lastModified());
        }

        @Test
        void scan_skipHiddenTrue_skipsHiddenDirectory() throws IOException {
            Files.writeString(tempDir.resolve("visible.txt"), "hello");
            Path hiddenDir = Files.createDirectory(tempDir.resolve(".hiddenDir"));
            Files.writeString(hiddenDir.resolve("secret.txt"), "secret");

            List<ScannedFile> results = new FileScanner.Builder().skipHidden(true).build().scan(tempDir);
            assertTrue(results.stream().noneMatch(sf -> sf.path().toString().contains("hidden")));
            assertEquals(1, results.size());
        }

        @Test
        void scan_skipHiddenTrue_skipsHiddenFiles() throws IOException {
            Files.writeString(tempDir.resolve("normal.txt"), "hi");
            Files.writeString(tempDir.resolve(".dotfile"), "hidden");
            List<ScannedFile> results = new FileScanner.Builder().skipHidden(true).build().scan(tempDir);
            assertTrue(results.stream().allMatch(sf -> !sf.path().getFileName().toString().startsWith(".")));
        }

        @Test
        void scan_maxDepthZero_returnsNoFiles() throws IOException {
            Files.writeString(tempDir.resolve("root.txt"), "data");
            assertTrue(new FileScanner.Builder().maxDepth(0).build().scan(tempDir).isEmpty());
        }

        @Test
        void scan_multipleExtensionFilters() throws IOException {
            Files.writeString(tempDir.resolve("a.pdf"), "x");
            Files.writeString(tempDir.resolve("b.jpg"), "x");
            Files.writeString(tempDir.resolve("c.mp3"), "x");
            Files.writeString(tempDir.resolve("d.txt"), "x");
            List<ScannedFile> results = new FileScanner.Builder().filterByExtensions("pdf", "jpg").build().scan(tempDir);
            assertEquals(2, results.size());
            assertTrue(results.stream().allMatch(sf -> sf.extension().equals("pdf") || sf.extension().equals("jpg")));
        }
    }

    // =========================================================================
    // 5. FileOrganizerTests
    // =========================================================================
    @Nested
    class FileOrganizerTests {

        @TempDir Path tempDir;

        private ScannedFile scannedFile(Path path, String ext) throws IOException {
            Files.writeString(path, "data");
            return new ScannedFile(path, ext, 4L, Instant.now());
        }

        @Test
        void organize_movesFilesToCategoryFolders() throws IOException {
            Path src = Files.createDirectories(tempDir.resolve("source"));
            Path target = tempDir.resolve("target");

            ScannedFile pdf = scannedFile(src.resolve("doc.pdf"), "pdf");
            ScannedFile jpg = scannedFile(src.resolve("img.jpg"), "jpg");

            FileOrganizer.OrganizationResult result = new FileOrganizer()
                    .applyCategorization(Map.of("Documents", List.of(pdf), "Images", List.of(jpg)), target);

            assertEquals(2, result.movedFiles());
            assertEquals(0, result.skippedFiles());
            assertTrue(Files.exists(target.resolve("Documents").resolve("doc.pdf")));
            assertTrue(Files.exists(target.resolve("Images").resolve("img.jpg")));
        }

        @Test
        void organize_emptyCategory_skipped() throws IOException {
            FileOrganizer.OrganizationResult result = new FileOrganizer()
                    .applyCategorization(Map.of("Documents", List.of()), tempDir.resolve("target"));
            assertEquals(0, result.movedFiles());
        }

        @Test
        void organize_nullCategory_skipped() throws IOException {
            Map<String, List<ScannedFile>> map = new HashMap<>();
            map.put("Documents", null);
            assertEquals(0, new FileOrganizer().applyCategorization(map, tempDir.resolve("t")).movedFiles());
        }

        @Test
        void organize_nonExistentSourceFile_skipped() throws IOException {
            ScannedFile ghost = new ScannedFile(tempDir.resolve("ghost.pdf"), "pdf", 0L, Instant.now());
            assertEquals(0, new FileOrganizer()
                    .applyCategorization(Map.of("Documents", List.of(ghost)), tempDir.resolve("t")).movedFiles());
        }

        @Test
        void organize_sanitizesFolderName() throws IOException {
            Path src = Files.createDirectories(tempDir.resolve("src"));
            ScannedFile f = scannedFile(src.resolve("file.txt"), "txt");
            new FileOrganizer().applyCategorization(Map.of("My:Folder*Name", List.of(f)), tempDir.resolve("t"));
            assertTrue(Files.exists(tempDir.resolve("t").resolve("My_Folder_Name")));
        }

        @Test
        void organize_duplicateDestination_renamedWithCounter() throws IOException {
            Path src    = Files.createDirectories(tempDir.resolve("src"));
            Path extra  = Files.createDirectories(tempDir.resolve("extra"));
            Path dup1   = src.resolve("dup.pdf");
            Path dup2   = extra.resolve("dup.pdf");
            Files.writeString(dup1, "first");
            Files.writeString(dup2, "second");

            Path target = tempDir.resolve("target");
            new FileOrganizer().applyCategorization(Map.of("Documents",
                    List.of(new ScannedFile(dup1, "pdf", 5L, Instant.now()),
                            new ScannedFile(dup2, "pdf", 6L, Instant.now()))), target);

            assertTrue(Files.exists(target.resolve("Documents").resolve("dup.pdf")));
            assertTrue(Files.exists(target.resolve("Documents").resolve("dup (1).pdf")));
        }

        @Test
        void undoLastMove_revertsMove() throws IOException {
            Path src = Files.createDirectories(tempDir.resolve("src"));
            ScannedFile f = scannedFile(src.resolve("f.txt"), "txt");
            FileOrganizer org = new FileOrganizer();
            org.applyCategorization(Map.of("Docs", List.of(f)), tempDir.resolve("target"));
            assertTrue(org.canUndo());
            org.undoLastMove();
            assertTrue(Files.exists(src.resolve("f.txt")));
        }

        @Test
        void redoLastMove_reappliesMove() throws IOException {
            Path src = Files.createDirectories(tempDir.resolve("src"));
            Path target = tempDir.resolve("target");
            ScannedFile f = scannedFile(src.resolve("g.txt"), "txt");
            FileOrganizer org = new FileOrganizer();
            org.applyCategorization(Map.of("Docs", List.of(f)), target);
            org.undoLastMove();
            assertTrue(org.canRedo());
            org.redoLastMove();
            assertTrue(Files.exists(target.resolve("Docs").resolve("g.txt")));
        }

        @Test
        void getLastMemento_isNullBeforeAnyOrganize() {
            assertNull(new FileOrganizer().getLastMemento());
        }

        @Test
        void getLastMemento_capturedAfterOrganize() throws IOException {
            Path src = Files.createDirectories(tempDir.resolve("src"));
            ScannedFile f = scannedFile(src.resolve("memo.txt"), "txt");
            FileOrganizer org = new FileOrganizer();
            org.applyCategorization(Map.of("Cat", List.of(f)), tempDir.resolve("target"));
            assertNotNull(org.getLastMemento());
            assertEquals(1, org.getLastMemento().size());
        }

        @Test
        void memento_restore_movesFilesBack() throws IOException {
            Path src = Files.createDirectories(tempDir.resolve("src"));
            ScannedFile f = scannedFile(src.resolve("restore.txt"), "txt");
            FileOrganizer org = new FileOrganizer();
            org.applyCategorization(Map.of("Cat", List.of(f)), tempDir.resolve("target"));
            org.getLastMemento().restore();
            assertTrue(Files.exists(src.resolve("restore.txt")));
        }

        @Test
        void sharedCommandHistory_constructor() throws IOException {
            CommandHistory shared = new CommandHistory();
            Path src = Files.createDirectories(tempDir.resolve("src2"));
            ScannedFile f = scannedFile(src.resolve("sh.txt"), "txt");
            new FileOrganizer(shared).applyCategorization(Map.of("Cat", List.of(f)), tempDir.resolve("t2"));
            assertTrue(shared.canUndo());
        }

        @Test
        void nullCategorizedFiles_throwsNPE() {
            assertThrows(NullPointerException.class,
                    () -> new FileOrganizer().applyCategorization(null, tempDir));
        }

        @Test
        void nullTargetDirectory_throwsNPE() {
            assertThrows(NullPointerException.class,
                    () -> new FileOrganizer().applyCategorization(Map.of(), null));
        }
    }

    // =========================================================================
    // 6. FileOrganizerExtendedTests
    // =========================================================================
    @Nested
    class FileOrganizerExtendedTests {

        @TempDir Path tempDir;

        private ScannedFile realFile(Path dir, String name) throws IOException {
            Path p = dir.resolve(name);
            Files.writeString(p, "data");
            String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
            return new ScannedFile(p, ext, 4L, Instant.now());
        }

        private String callGetBaseName(String fn) throws Exception {
            Method m = FileOrganizer.class.getDeclaredMethod("getBaseName", String.class);
            m.setAccessible(true);
            return (String) m.invoke(new FileOrganizer(), fn);
        }

        private String callGetExtensionWithDot(String fn) throws Exception {
            Method m = FileOrganizer.class.getDeclaredMethod("getExtensionWithDot", String.class);
            m.setAccessible(true);
            return (String) m.invoke(new FileOrganizer(), fn);
        }

        // sanitizeFolderName ---------------------------------------------------

        @Test
        void sanitize_nullKey_movesToOther() throws IOException {
            Path src = Files.createDirectory(tempDir.resolve("s1"));
            ScannedFile sf = realFile(src, "file.txt");
            Map<String, List<ScannedFile>> map = new HashMap<>();
            map.put(null, List.of(sf));
            FileOrganizer.OrganizationResult r = new FileOrganizer().applyCategorization(map, tempDir.resolve("t1"));
            assertEquals(1, r.movedFiles());
            assertTrue(Files.exists(tempDir.resolve("t1").resolve("Other").resolve("file.txt")));
        }

        @Test
        void sanitize_blankKey_movesToOther() throws IOException {
            Path src = Files.createDirectory(tempDir.resolve("s2"));
            ScannedFile sf = realFile(src, "note.txt");
            new FileOrganizer().applyCategorization(Map.of("   ", List.of(sf)), tempDir.resolve("t2"));
            assertTrue(Files.exists(tempDir.resolve("t2").resolve("Other").resolve("note.txt")));
        }

        @Test
        void sanitize_emptyKey_movesToOther() throws IOException {
            Path src = Files.createDirectory(tempDir.resolve("s3"));
            ScannedFile sf = realFile(src, "a.pdf");
            new FileOrganizer().applyCategorization(Map.of("", List.of(sf)), tempDir.resolve("t3"));
            assertTrue(Files.exists(tempDir.resolve("t3").resolve("Other").resolve("a.pdf")));
        }

        @Test
        void sanitize_specialChars_replacedWithUnderscores() throws IOException {
            Path src = Files.createDirectory(tempDir.resolve("s4"));
            ScannedFile sf = realFile(src, "data.csv");
            new FileOrganizer().applyCategorization(Map.of("My:Folder*Name?", List.of(sf)), tempDir.resolve("t4"));
            assertEquals(1, Files.list(tempDir.resolve("t4"))
                    .filter(p -> p.getFileName().toString().startsWith("My_")).count());
        }

        @Test
        void sanitize_backslashAndSlash_replacedWithUnderscores() throws IOException {
            Path src = Files.createDirectory(tempDir.resolve("s5"));
            ScannedFile sf = realFile(src, "img.png");
            new FileOrganizer().applyCategorization(Map.of("a/b\\c", List.of(sf)), tempDir.resolve("t5"));
            assertTrue(Files.exists(tempDir.resolve("t5").resolve("a_b_c").resolve("img.png")));
        }

        // Collision counter ----------------------------------------------------

        @Test
        void duplicate_noCollision_usesOriginalName() throws IOException {
            Path src = Files.createDirectory(tempDir.resolve("nc"));
            ScannedFile sf = realFile(src, "unique.pdf");
            new FileOrganizer().applyCategorization(Map.of("Cat", List.of(sf)), tempDir.resolve("nct"));
            assertTrue(Files.exists(tempDir.resolve("nct").resolve("Cat").resolve("unique.pdf")));
        }

        @Test
        void duplicate_oneCollision_renamedWith1() throws IOException {
            Path src1 = Files.createDirectory(tempDir.resolve("oc1"));
            Path src2 = Files.createDirectory(tempDir.resolve("oc2"));
            ScannedFile sf1 = realFile(src1, "report.pdf");
            ScannedFile sf2 = realFile(src2, "report.pdf");
            new FileOrganizer().applyCategorization(Map.of("Docs", List.of(sf1, sf2)), tempDir.resolve("oct"));
            assertTrue(Files.exists(tempDir.resolve("oct").resolve("Docs").resolve("report.pdf")));
            assertTrue(Files.exists(tempDir.resolve("oct").resolve("Docs").resolve("report (1).pdf")));
        }

        @Test
        void duplicate_twoCollisions_renamedWith1and2() throws IOException {
            Path src1 = Files.createDirectory(tempDir.resolve("tc1"));
            Path src2 = Files.createDirectory(tempDir.resolve("tc2"));
            Path src3 = Files.createDirectory(tempDir.resolve("tc3"));
            ScannedFile sf1 = realFile(src1, "data.xlsx");
            ScannedFile sf2 = realFile(src2, "data.xlsx");
            ScannedFile sf3 = realFile(src3, "data.xlsx");
            new FileOrganizer().applyCategorization(Map.of("Sheet", List.of(sf1, sf2, sf3)), tempDir.resolve("tct"));
            assertTrue(Files.exists(tempDir.resolve("tct").resolve("Sheet").resolve("data.xlsx")));
            assertTrue(Files.exists(tempDir.resolve("tct").resolve("Sheet").resolve("data (1).xlsx")));
            assertTrue(Files.exists(tempDir.resolve("tct").resolve("Sheet").resolve("data (2).xlsx")));
        }

        @Test
        void duplicate_fileWithNoDot_renamedWithoutExtension() throws IOException {
            Path src1 = Files.createDirectory(tempDir.resolve("nd1"));
            Path src2 = Files.createDirectory(tempDir.resolve("nd2"));
            Path p1 = src1.resolve("Makefile"); Files.writeString(p1, "a");
            Path p2 = src2.resolve("Makefile"); Files.writeString(p2, "b");
            new FileOrganizer().applyCategorization(Map.of("Build",
                    List.of(new ScannedFile(p1, "", 1L, Instant.now()),
                            new ScannedFile(p2, "", 1L, Instant.now()))), tempDir.resolve("ndt"));
            assertTrue(Files.exists(tempDir.resolve("ndt").resolve("Build").resolve("Makefile")));
            assertTrue(Files.exists(tempDir.resolve("ndt").resolve("Build").resolve("Makefile (1)")));
        }

        @Test
        void duplicate_fileWithTrailingDot_extensionIsEmpty() throws IOException {
            Path src1 = Files.createDirectory(tempDir.resolve("td1"));
            Path src2 = Files.createDirectory(tempDir.resolve("td2"));
            Path p1 = src1.resolve("file."); Files.writeString(p1, "a");
            Path p2 = src2.resolve("file."); Files.writeString(p2, "b");
            FileOrganizer.OrganizationResult r = new FileOrganizer().applyCategorization(Map.of("TDot",
                    List.of(new ScannedFile(p1, "", 1L, Instant.now()),
                            new ScannedFile(p2, "", 1L, Instant.now()))), tempDir.resolve("tdt"));
            assertEquals(2, r.movedFiles());
        }

        // null / missing guards ------------------------------------------------

        @Test
        void nullListValue_skipsSilently() throws IOException {
            Map<String, List<ScannedFile>> map = new HashMap<>();
            map.put("Docs", null);
            FileOrganizer.OrganizationResult r = new FileOrganizer().applyCategorization(map, tempDir.resolve("nl"));
            assertEquals(0, r.movedFiles());
        }

        @Test
        void emptyListValue_skipsSilently() throws IOException {
            assertEquals(0, new FileOrganizer().applyCategorization(
                    Map.of("Docs", List.of()), tempDir.resolve("el")).movedFiles());
        }

        @Test
        void nullScannedFile_inList_skipped() throws IOException {
            Map<String, List<ScannedFile>> map = new HashMap<>();
            List<ScannedFile> list = new ArrayList<>();
            list.add(null);
            map.put("Cat", list);
            assertEquals(0, new FileOrganizer().applyCategorization(map, tempDir.resolve("nsf")).movedFiles());
        }

        @Test
        void scannedFile_withNullPath_skipped() throws IOException {
            Map<String, List<ScannedFile>> map = new HashMap<>();
            map.put("Docs", List.of(new ScannedFile(null, "pdf", 0L, Instant.now())));
            assertDoesNotThrow(() -> new FileOrganizer().applyCategorization(map, tempDir.resolve("np")));
        }

        @Test
        void nonExistentSourceFile_isSkippedFromMoves() throws IOException {
            ScannedFile ghost = new ScannedFile(tempDir.resolve("doesNotExist.pdf"), "pdf", 0L, Instant.now());
            FileOrganizer.OrganizationResult r = new FileOrganizer()
                    .applyCategorization(Map.of("Docs", List.of(ghost)), tempDir.resolve("ghost"));
            assertEquals(0, r.movedFiles());
        }

        @Test
        void ioException_duringMove_incrementsSkipped() throws IOException {
            Path target = Files.createDirectory(tempDir.resolve("ioTarget"));
            Files.writeString(target.resolve("Docs"), "I am a file"); // blocker
            Path src = Files.createDirectory(tempDir.resolve("ioSrc"));
            Path p = src.resolve("blocked.pdf");
            Files.writeString(p, "content");
            FileOrganizer.OrganizationResult r = new FileOrganizer().applyCategorization(
                    Map.of("Docs", List.of(new ScannedFile(p, "pdf", 7L, Instant.now()))), target);
            assertEquals(0, r.movedFiles());
            assertEquals(1, r.skippedFiles());
        }

        // getBaseName / getExtensionWithDot ------------------------------------

        @Test void getBaseName_noDot()       throws Exception { assertEquals("Makefile", callGetBaseName("Makefile")); }
        @Test void getBaseName_leadingDot()  throws Exception { assertEquals(".hidden",  callGetBaseName(".hidden")); }
        @Test void getBaseName_normalFile()  throws Exception { assertEquals("report",   callGetBaseName("report.pdf")); }

        @Test void getExtension_noDot()      throws Exception { assertEquals("",     callGetExtensionWithDot("Makefile")); }
        @Test void getExtension_leadingDot() throws Exception { assertEquals("",     callGetExtensionWithDot(".hidden")); }
        @Test void getExtension_trailingDot()throws Exception { assertEquals("",     callGetExtensionWithDot("file.")); }
        @Test void getExtension_normalFile() throws Exception { assertEquals(".pdf", callGetExtensionWithDot("report.pdf")); }

        // undo/redo + multi-category -------------------------------------------

        @Test
        void sharedHistory_canUndoAfterOrganize() throws IOException {
            CommandHistory shared = new CommandHistory();
            Path src = Files.createDirectory(tempDir.resolve("shSrc"));
            ScannedFile sf = realFile(src, "x.txt");
            new FileOrganizer(shared).applyCategorization(Map.of("T", List.of(sf)), tempDir.resolve("shTgt"));
            assertTrue(shared.canUndo());
            assertFalse(shared.canRedo());
        }

        @Test
        void canRedo_afterUndoLastMove() throws IOException {
            Path src = Files.createDirectory(tempDir.resolve("rdSrc"));
            ScannedFile sf = realFile(src, "y.txt");
            FileOrganizer org = new FileOrganizer();
            org.applyCategorization(Map.of("T", List.of(sf)), tempDir.resolve("rdTgt"));
            org.undoLastMove();
            assertTrue(org.canRedo());
            org.redoLastMove();
            assertTrue(org.canUndo());
        }

        @Test
        void multipleCategories_allMoved_correctCounts() throws IOException {
            Path src = Files.createDirectory(tempDir.resolve("multi"));
            Map<String, List<ScannedFile>> cats = new LinkedHashMap<>();
            cats.put("Documents", List.of(realFile(src, "doc.pdf")));
            cats.put("Images",    List.of(realFile(src, "img.jpg")));
            cats.put("Audio",     List.of(realFile(src, "song.mp3")));
            FileOrganizer.OrganizationResult r = new FileOrganizer()
                    .applyCategorization(cats, tempDir.resolve("multiTgt"));
            assertEquals(3, r.movedFiles());
            assertEquals(0, r.skippedFiles());
        }
    }

    // =========================================================================
    // 7. FileOrganizerFacadeTests
    // =========================================================================
    @Nested
    class FileOrganizerFacadeTests {

        @TempDir Path sourceDir;
        @TempDir Path targetDir;

        private void createFile(Path dir, String name) throws IOException {
            Files.writeString(dir.resolve(name), "content");
        }

        @Test
        void organize_withDefaultCategories_movesFiles() throws IOException {
            createFile(sourceDir, "report.pdf");
            createFile(sourceDir, "photo.jpg");
            createFile(sourceDir, "song.mp3");

            FileOrganizer.OrganizationResult result = new FileOrganizerFacade.Builder()
                    .withDefaultCategories().build().organize(sourceDir, targetDir);

            assertEquals(3, result.movedFiles());
            assertEquals(0, result.skippedFiles());
            assertTrue(Files.exists(targetDir.resolve("Documents").resolve("report.pdf")));
            assertTrue(Files.exists(targetDir.resolve("Images").resolve("photo.jpg")));
            assertTrue(Files.exists(targetDir.resolve("Audio").resolve("song.mp3")));
        }

        @Test
        void organize_unknownExtension_goesToOther() throws IOException {
            createFile(sourceDir, "weird.xyz");
            new FileOrganizerFacade.Builder().withDefaultCategories().build().organize(sourceDir, targetDir);
            assertTrue(Files.exists(targetDir.resolve("Other").resolve("weird.xyz")));
        }

        @Test
        void organize_withCustomCategorizer_usesIt() throws IOException {
            createFile(sourceDir, "a.txt");
            createFile(sourceDir, "b.jpg");
            FileOrganizer.OrganizationResult result = new FileOrganizerFacade.Builder()
                    .withCategorizer(new DateFileCategorizer.Builder().period(new Periods.ByYear()).build())
                    .build().organize(sourceDir, targetDir);
            assertEquals(2, result.movedFiles());
        }

        @Test
        void organize_addCategory_onlyRegisteredCategoryUsed() throws IOException {
            createFile(sourceDir, "doc.pdf");
            createFile(sourceDir, "img.png");
            new FileOrganizerFacade.Builder().addCategory(new Categories.Documents()).build()
                    .organize(sourceDir, targetDir);
            assertTrue(Files.exists(targetDir.resolve("Documents").resolve("doc.pdf")));
            assertTrue(Files.exists(targetDir.resolve("Other").resolve("img.png")));
        }

        @Test
        void organize_maxDepth_limitsDepthOfScan() throws IOException {
            createFile(sourceDir, "root.txt");
            Path sub = Files.createDirectory(sourceDir.resolve("sub"));
            Files.writeString(sub.resolve("level1.txt"), "l1");
            Files.writeString(Files.createDirectory(sub.resolve("deep")).resolve("level2.txt"), "l2");

            FileOrganizer.OrganizationResult result = new FileOrganizerFacade.Builder()
                    .addCategory(new Categories.Documents()).maxDepth(2).build()
                    .organize(sourceDir, targetDir);
            assertEquals(2, result.movedFiles());
        }

        @Test
        void getOrganizer_returnsNonNull() {
            assertNotNull(new FileOrganizerFacade.Builder().withDefaultCategories().build().getOrganizer());
        }

        @Test
        void sharedCommandHistory_reflectsInFacade() throws IOException {
            createFile(sourceDir, "cmd.pdf");
            CommandHistory shared = new CommandHistory();
            new FileOrganizerFacade.Builder().withDefaultCategories().commandHistory(shared).build()
                    .organize(sourceDir, targetDir);
            assertTrue(shared.canUndo());
        }

        @Test
        void facade_skipHidden_buildsAndOrganizes() throws IOException {
            Files.writeString(sourceDir.resolve("doc.pdf"), "data");
            Files.writeString(sourceDir.resolve(".hidden.pdf"), "secret");
            FileOrganizer.OrganizationResult result = new FileOrganizerFacade.Builder()
                    .withDefaultCategories().skipHidden(true).build().organize(sourceDir, targetDir);
            assertEquals(1, result.movedFiles());
        }

        @Test
        void organize_nullSource_throwsNPE() {
            assertThrows(NullPointerException.class,
                    () -> new FileOrganizerFacade.Builder().withDefaultCategories().build().organize(null, targetDir));
        }

        @Test
        void organize_nullTarget_throwsNPE() {
            assertThrows(NullPointerException.class,
                    () -> new FileOrganizerFacade.Builder().withDefaultCategories().build().organize(sourceDir, null));
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

    // =========================================================================
    // 8. CoverageBoosterTests  (addFolderIcons + miscellaneous edge cases)
    // =========================================================================
    @Nested
    class CoverageBoosterTests {

        @TempDir Path tempDir;

        @Test
        void copyCommand_undo_destinationAlreadyGone_doesNotThrow() throws IOException {
            Path src = tempDir.resolve("orig.txt"); Files.writeString(src, "content");
            Path dst = tempDir.resolve("copy.txt");
            CopyFileCommand cmd = new CopyFileCommand(src, dst);
            cmd.execute();
            Files.delete(dst);
            assertDoesNotThrow(cmd::undo);
            assertTrue(Files.exists(src));
        }

        @Test
        void moveCommand_destinationParentExists_movesFile() throws IOException {
            Path src = tempDir.resolve("moveMe.txt"); Files.writeString(src, "data");
            Path dst = tempDir.resolve("movedFile.txt");
            new MoveFileCommand(src, dst).execute();
            assertTrue(Files.exists(dst));
            assertFalse(Files.exists(src));
        }

        @Test
        void fileHandlerChain_threeDeep_allPass() {
            FileHandler head = new ExtensionHandler("pdf");
            head.setNext(new SizeHandler(10_000L));
            head.setNext(new DateHandler(Instant.parse("2024-01-01T00:00:00Z"),
                    Instant.parse("2024-12-31T00:00:00Z")));
            assertTrue(head.handle(new ScannedFile(Path.of("/f.pdf"), "pdf", 500L,
                    Instant.parse("2024-06-01T00:00:00Z"))).isPresent());
        }

        @Test
        void fileHandlerChain_threeDeep_middleRejects() {
            FileHandler head = new ExtensionHandler("pdf");
            head.setNext(new SizeHandler(100L));
            head.setNext(new DateHandler(Instant.parse("2024-01-01T00:00:00Z"),
                    Instant.parse("2024-12-31T00:00:00Z")));
            assertTrue(head.handle(new ScannedFile(Path.of("/big.pdf"), "pdf", 9999L,
                    Instant.parse("2024-06-01T00:00:00Z"))).isEmpty());
        }

        @Test
        void extensionCategorizer_dateDecoratorBranch() {
            Instant cutoff = Instant.parse("2024-01-01T00:00:00Z");
            ExtensionFileCategorizer cat = new ExtensionFileCategorizer.Builder()
                    .register(new DateFilterDecorator(new Categories.Images(), cutoff)).build();
            Map<String, List<ScannedFile>> result = cat.categorize(List.of(
                    new ScannedFile(Path.of("/old.jpg"), "jpg", 100L, Instant.parse("2023-01-01T00:00:00Z")),
                    new ScannedFile(Path.of("/new.jpg"), "jpg", 100L, Instant.parse("2024-06-01T00:00:00Z")),
                    new ScannedFile(Path.of("/f.pdf"),   "pdf", 100L, Instant.parse("2024-06-01T00:00:00Z"))));
            assertEquals(1, result.get("Images").size());
            assertEquals(2, result.get(ExtensionFileCategorizer.FALLBACK_CATEGORY).size());
        }

        @Test
        void organizer_tripleDuplicate_countsTo2() throws IOException {
            Path src1 = Files.createDirectory(tempDir.resolve("td1"));
            Path src2 = Files.createDirectory(tempDir.resolve("td2"));
            Path src3 = Files.createDirectory(tempDir.resolve("td3"));
            Path target = tempDir.resolve("tdTarget");
            Path p1 = src1.resolve("report.pdf"); Files.writeString(p1, "one");
            Path p2 = src2.resolve("report.pdf"); Files.writeString(p2, "two");
            Path p3 = src3.resolve("report.pdf"); Files.writeString(p3, "three");
            FileOrganizer.OrganizationResult r = new FileOrganizer().applyCategorization(Map.of("Reports",
                    List.of(new ScannedFile(p1, "pdf", 3L, Instant.now()),
                            new ScannedFile(p2, "pdf", 3L, Instant.now()),
                            new ScannedFile(p3, "pdf", 3L, Instant.now()))), target);
            assertEquals(3, r.movedFiles());
            assertTrue(Files.exists(target.resolve("Reports").resolve("report (2).pdf")));
        }
    }

    // =========================================================================
    // 9. CategoriesTests
    // =========================================================================
    @Nested
    class CategoriesTests {

        @Test
        void documents_acceptsKnownExtensions() {
            FileCategory cat = new Categories.Documents();
            for (String ext : new String[]{"pdf","doc","docx","odt","rtf","txt","tex","md","csv","xls","xlsx","ods","ppt","pptx","odp"})
                assertTrue(cat.accepts(ext));
        }
        @Test void documents_rejectsUnknown() { assertFalse(new Categories.Documents().accepts("mp4")); }
        @Test void documents_name()           { assertEquals("Documents", new Categories.Documents().getName()); }

        @Test
        void images_acceptsKnownExtensions() {
            FileCategory cat = new Categories.Images();
            for (String ext : new String[]{"jpg","jpeg","png","gif","bmp","webp","svg","tiff","tif","ico","heic","heif","raw","cr2","nef","avif"})
                assertTrue(cat.accepts(ext));
        }
        @Test void images_rejectsOther() { assertFalse(new Categories.Images().accepts("pdf")); }
        @Test void images_name()         { assertEquals("Images", new Categories.Images().getName()); }

        @Test
        void videos_acceptsKnownExtensions() {
            FileCategory cat = new Categories.Videos();
            for (String ext : new String[]{"mp4","mkv","avi","mov","wmv","flv","webm","m4v","mpeg","mpg","3gp","ogv","ts","vob"})
                assertTrue(cat.accepts(ext));
        }
        @Test void videos_rejectsAudio() { assertFalse(new Categories.Videos().accepts("mp3")); }
        @Test void videos_name()         { assertEquals("Videos", new Categories.Videos().getName()); }

        @Test
        void audio_acceptsKnownExtensions() {
            FileCategory cat = new Categories.Audio();
            for (String ext : new String[]{"mp3","wav","flac","aac","ogg","wma","m4a","opus","aiff","alac","mid","midi","amr"})
                assertTrue(cat.accepts(ext));
        }
        @Test void audio_name() { assertEquals("Audio", new Categories.Audio().getName()); }

        @Test
        void archives_acceptsKnownExtensions() {
            FileCategory cat = new Categories.Archives();
            for (String ext : new String[]{"zip","tar","gz","bz2","xz","7z","rar","iso","tgz","cab","deb","rpm","jar","war"})
                assertTrue(cat.accepts(ext));
        }
        @Test void archives_name() { assertEquals("Archives", new Categories.Archives().getName()); }

        @Test void extensionCategory_uppercase_noMatch() { assertFalse(new Categories.Images().accepts("JPG")); }
    }

    // =========================================================================
    // 10. CategoriesClassTests
    // =========================================================================
    @Nested
    class CategoriesClassTests {

        @Test void documents_ctor() { assertEquals("Documents", new Categories.Documents().getName()); }
        @Test void images_ctor()    { assertEquals("Images",    new Categories.Images().getName()); }
        @Test void videos_ctor()    { assertEquals("Videos",    new Categories.Videos().getName()); }
        @Test void audio_ctor()     { assertEquals("Audio",     new Categories.Audio().getName()); }
        @Test void archives_ctor()  { assertEquals("Archives",  new Categories.Archives().getName()); }

        @Test
        void each_category_accepts_representative_extension() {
            assertTrue(new Categories.Documents().accepts("pdf"));
            assertTrue(new Categories.Images().accepts("png"));
            assertTrue(new Categories.Videos().accepts("mp4"));
            assertTrue(new Categories.Audio().accepts("mp3"));
            assertTrue(new Categories.Archives().accepts("zip"));
        }

        @Test
        void each_category_rejects_other_extension() {
            assertFalse(new Categories.Documents().accepts("mp4"));
            assertFalse(new Categories.Images().accepts("mp3"));
            assertFalse(new Categories.Videos().accepts("pdf"));
            assertFalse(new Categories.Audio().accepts("jpg"));
            assertFalse(new Categories.Archives().accepts("docx"));
        }
    }

    // =========================================================================
    // 11. SingleExtensionCategoryTests
    // =========================================================================
    @Nested
    class SingleExtensionCategoryTests {

        @Test void accepts_matching()     { assertTrue(new SingleExtensionCategory("pdf").accepts("pdf")); }
        @Test void rejects_nonMatching()  { assertFalse(new SingleExtensionCategory("pdf").accepts("docx")); }
        @Test void getName_uppercases()   { assertEquals("PDF", new SingleExtensionCategory("pdf").getName()); }
        @Test void getName_blank_other()  { assertEquals("Other", new SingleExtensionCategory("").getName()); }
        @Test void getName_spaces_other() { assertEquals("Other", new SingleExtensionCategory("   ").getName()); }
        @Test void normalizes_lowercase() {
            SingleExtensionCategory cat = new SingleExtensionCategory("PDF");
            assertTrue(cat.accepts("pdf"));
            assertFalse(cat.accepts("PDF"));
        }
    }

    // =========================================================================
    // 12. SizeFilterDecoratorTests
    // =========================================================================
    @Nested
    class SizeFilterDecoratorTests {

        private ScannedFile file(String ext, long size) {
            return new ScannedFile(Path.of("/f." + ext), ext, size, Instant.now());
        }

        @Test void accepts_withinLimit()    { assertTrue(new SizeFilterDecorator(new Categories.Documents(), 1000L).accepts(file("pdf", 999L))); }
        @Test void accepts_atBoundary()     { assertTrue(new SizeFilterDecorator(new Categories.Documents(), 1000L).accepts(file("pdf", 1000L))); }
        @Test void rejects_exceeds()        { assertFalse(new SizeFilterDecorator(new Categories.Documents(), 1000L).accepts(file("pdf", 1001L))); }
        @Test void rejects_wrongExtension() { assertFalse(new SizeFilterDecorator(new Categories.Documents(), 9999L).accepts(file("mp4", 100L))); }
        @Test void accepts_extensionOnly()  { assertTrue(new SizeFilterDecorator(new Categories.Documents(), 1000L).accepts("pdf")); }
        @Test void getName_delegates()      { assertEquals("Images", new SizeFilterDecorator(new Categories.Images(), 1000L).getName()); }
        @Test void getMaxSizeBytes()        { assertEquals(5000L, new SizeFilterDecorator(new Categories.Documents(), 5000L).getMaxSizeBytes()); }
        @Test void negativeMax_throws()     { assertThrows(IllegalArgumentException.class, () -> new SizeFilterDecorator(new Categories.Documents(), -1L)); }
        @Test void zeroMax_acceptsZeroByte()  { assertTrue(new SizeFilterDecorator(new Categories.Documents(), 0L).accepts(file("pdf", 0L))); }
        @Test void zeroMax_rejects1Byte()     { assertFalse(new SizeFilterDecorator(new Categories.Documents(), 0L).accepts(file("pdf", 1L))); }
    }

    // =========================================================================
    // 13. DateFilterDecoratorTests
    // =========================================================================
    @Nested
    class DateFilterDecoratorTests {

        private static final Instant JAN = Instant.parse("2024-01-01T00:00:00Z");
        private static final Instant JUN = Instant.parse("2024-06-01T00:00:00Z");
        private static final Instant DEC = Instant.parse("2024-12-31T23:59:59Z");

        private ScannedFile file(String ext, Instant ts) {
            return new ScannedFile(Path.of("/f." + ext), ext, 100L, ts);
        }

        @Test void accepts_withinBounds()       { assertTrue(new DateFilterDecorator(new Categories.Images(), JAN, DEC).accepts(file("jpg", JUN))); }
        @Test void rejects_tooEarly()           { assertFalse(new DateFilterDecorator(new Categories.Images(), JUN, DEC).accepts(file("jpg", JAN))); }
        @Test void rejects_tooLate()            { assertFalse(new DateFilterDecorator(new Categories.Images(), JAN, JUN).accepts(file("jpg", DEC))); }
        @Test void accepts_exactAfterBound()    { assertTrue(new DateFilterDecorator(new Categories.Images(), JAN, DEC).accepts(file("jpg", JAN))); }
        @Test void noUpperBound_acceptsFuture() { assertTrue(new DateFilterDecorator(new Categories.Images(), JAN).accepts(file("jpg", DEC))); }
        @Test void nullAfter_noLowerBound()     { assertTrue(new DateFilterDecorator(new Categories.Images(), null, JUN).accepts(file("jpg", JAN))); }
        @Test void rejects_wrongExtension()     { assertFalse(new DateFilterDecorator(new Categories.Images(), JAN, DEC).accepts(file("pdf", JUN))); }
        @Test void accepts_extensionOnly()      { assertTrue(new DateFilterDecorator(new Categories.Images(), JAN, DEC).accepts("jpg")); }
        @Test void getters_returnValues()       {
            DateFilterDecorator dec = new DateFilterDecorator(new Categories.Images(), JAN, DEC);
            assertEquals(JAN, dec.getAfter()); assertEquals(DEC, dec.getBefore());
        }
        @Test void singleArgCtor_requiresNonNull() {
            assertThrows(NullPointerException.class,
                    () -> new DateFilterDecorator(new Categories.Images(), (Instant) null));
        }
        @Test void getName_delegates() { assertEquals("Images", new DateFilterDecorator(new Categories.Images(), JAN).getName()); }
    }

    // =========================================================================
    // 14. ExtensionFileCategorizerTests
    // =========================================================================
    @Nested
    class ExtensionFileCategorizerTests {

        private ScannedFile file(String ext) {
            return new ScannedFile(Path.of("/f." + ext), ext, 100L, Instant.now());
        }

        private ExtensionFileCategorizer defaultCat() {
            return new ExtensionFileCategorizer.Builder()
                    .register(new Categories.Documents()).register(new Categories.Images())
                    .register(new Categories.Videos()).register(new Categories.Audio())
                    .register(new Categories.Archives()).build();
        }

        @Test void knownExtension_correctBucket() {
            Map<String, List<ScannedFile>> r = defaultCat().categorize(List.of(file("pdf"), file("jpg")));
            assertEquals(1, r.get("Documents").size());
            assertEquals(1, r.get("Images").size());
            assertEquals(0, r.get("Videos").size());
        }
        @Test void unknownExtension_fallback() {
            assertEquals(1, defaultCat().categorize(List.of(file("xyz")))
                    .get(ExtensionFileCategorizer.FALLBACK_CATEGORY).size());
        }
        @Test void emptyInput_allEmpty() { defaultCat().categorize(List.of()).values()
                .forEach(l -> assertEquals(0, l.size())); }
        @Test void stream_sameAsCollection() {
            Map<String,List<ScannedFile>> c = defaultCat().categorize(List.of(file("pdf"), file("mp3"), file("unknown")));
            Map<String,List<ScannedFile>> s = defaultCat().categorize(Stream.of(file("pdf"), file("mp3"), file("unknown")));
            assertEquals(c.get("Documents").size(), s.get("Documents").size());
        }
        @Test void fallbackCategory_constant() { assertEquals("Other", ExtensionFileCategorizer.FALLBACK_CATEGORY); }
        @Test void builder_nullCategory_throws() { assertThrows(NullPointerException.class,
                () -> new ExtensionFileCategorizer.Builder().register(null)); }
        @Test void sizeDecorator_filtersLarge() {
            ExtensionFileCategorizer cat = new ExtensionFileCategorizer.Builder()
                    .register(new SizeFilterDecorator(new Categories.Documents(), 500L)).build();
            Map<String,List<ScannedFile>> r = cat.categorize(List.of(
                    new ScannedFile(Path.of("/s.pdf"), "pdf", 400L, Instant.now()),
                    new ScannedFile(Path.of("/l.pdf"), "pdf", 600L, Instant.now())));
            assertEquals(1, r.get("Documents").size());
        }
        @Test void firstMatchWins_overlapping() {
            ExtensionFileCategorizer cat = new ExtensionFileCategorizer.Builder()
                    .register(new Categories.Documents()).register(new SingleExtensionCategory("pdf")).build();
            assertEquals(1, cat.categorize(List.of(file("pdf"))).get("Documents").size());
            assertEquals(0, cat.categorize(List.of(file("pdf"))).get("PDF").size());
        }
    }

    // =========================================================================
    // 15. DateFileCategorizerTests
    // =========================================================================
    @Nested
    class DateFileCategorizerTests {

        private ScannedFile file(Instant ts) {
            return new ScannedFile(Path.of("/f.txt"), "txt", 10L, ts);
        }

        @Test void byMonth_groupsCorrectly() {
            DateFileCategorizer cat = new DateFileCategorizer.Builder().period(new Periods.ByMonth()).build();
            Map<String,List<ScannedFile>> r = cat.categorize(List.of(
                    file(Instant.parse("2024-03-15T00:00:00Z")),
                    file(Instant.parse("2024-03-20T00:00:00Z")),
                    file(Instant.parse("2024-11-01T00:00:00Z"))));
            assertEquals(2, r.size());
            assertEquals(2, r.get("2024-03").size());
        }
        @Test void byMonth_labelFormat() {
            String label = new Periods.ByMonth().label(Instant.parse("2025-04-07T12:00:00Z"));
            assertTrue(label.matches("\\d{4}-\\d{2}"));
            assertEquals("Month", new Periods.ByMonth().getName());
        }
        @Test void byDay_groupsCorrectly() {
            DateFileCategorizer cat = new DateFileCategorizer.Builder().period(new Periods.ByDay()).build();
            int total = cat.categorize(List.of(
                    file(Instant.parse("2024-03-15T01:00:00Z")),
                    file(Instant.parse("2024-03-15T22:00:00Z")),
                    file(Instant.parse("2024-03-16T00:00:00Z"))))
                    .values().stream().mapToInt(List::size).sum();
            assertEquals(3, total);
        }
        @Test void byDay_labelFormat() {
            assertTrue(new Periods.ByDay().label(Instant.parse("2025-04-07T12:00:00Z")).matches("\\d{4}-\\d{2}-\\d{2}"));
            assertEquals("Day", new Periods.ByDay().getName());
        }
        @Test void byYear_groupsCorrectly() {
            DateFileCategorizer cat = new DateFileCategorizer.Builder().period(new Periods.ByYear()).build();
            Map<String,List<ScannedFile>> r = cat.categorize(List.of(
                    file(Instant.parse("2023-06-01T00:00:00Z")),
                    file(Instant.parse("2024-02-01T00:00:00Z")),
                    file(Instant.parse("2024-11-01T00:00:00Z"))));
            assertEquals(1, r.get("2023").size());
            assertEquals(2, r.get("2024").size());
        }
        @Test void byYear_labelFormat() {
            assertTrue(new Periods.ByYear().label(Instant.parse("2025-04-07T12:00:00Z")).matches("\\d{4}"));
            assertEquals("Year", new Periods.ByYear().getName());
        }
        @Test void builder_noPeriod_throws()   { assertThrows(NullPointerException.class, () -> new DateFileCategorizer.Builder().build()); }
        @Test void builder_nullPeriod_throws() { assertThrows(NullPointerException.class, () -> new DateFileCategorizer.Builder().period(null)); }
        @Test void emptyList_returnsEmpty() {
            assertTrue(new DateFileCategorizer.Builder().period(new Periods.ByMonth()).build().categorize(List.of()).isEmpty());
        }
        @Test void categorize_sortedChronologically() {
            DateFileCategorizer cat = new DateFileCategorizer.Builder().period(new Periods.ByMonth()).build();
            Map<String,List<ScannedFile>> r = cat.categorize(List.of(
                    file(Instant.parse("2024-12-01T00:00:00Z")),
                    file(Instant.parse("2024-01-01T00:00:00Z")),
                    file(Instant.parse("2024-06-01T00:00:00Z"))));
            List<String> keys = List.copyOf(r.keySet());
            assertTrue(keys.get(0).compareTo(keys.get(1)) < 0);
        }
    }

    // =========================================================================
    // 16. FileHandlerChainTests
    // =========================================================================
    @Nested
    class FileHandlerChainTests {

        private static final Instant JAN = Instant.parse("2024-01-01T00:00:00Z");
        private static final Instant JUN = Instant.parse("2024-06-01T00:00:00Z");
        private static final Instant DEC = Instant.parse("2024-12-31T00:00:00Z");

        private ScannedFile file(String ext, long size, Instant ts) {
            return new ScannedFile(Path.of("/f." + ext), ext, size, ts);
        }

        @Test void extensionHandler_allowed()      { assertTrue(new ExtensionHandler("pdf","docx").handle(file("pdf",100L,Instant.now())).isPresent()); }
        @Test void extensionHandler_disallowed()   { assertTrue(new ExtensionHandler("pdf").handle(file("mp4",100L,Instant.now())).isEmpty()); }
        @Test void extensionHandler_emptyPassesAll(){ assertTrue(new ExtensionHandler().handle(file("anything",100L,Instant.now())).isPresent()); }

        @Test void sizeHandler_within()    { assertTrue(new SizeHandler(1000L).handle(file("pdf", 999L,Instant.now())).isPresent()); }
        @Test void sizeHandler_atBound()   { assertTrue(new SizeHandler(1000L).handle(file("pdf",1000L,Instant.now())).isPresent()); }
        @Test void sizeHandler_exceeds()   { assertTrue(new SizeHandler(1000L).handle(file("pdf",1001L,Instant.now())).isEmpty()); }
        @Test void sizeHandler_negative_throws() { assertThrows(IllegalArgumentException.class, () -> new SizeHandler(-1L)); }

        @Test void dateHandler_within()    { assertTrue(new DateHandler(JAN,DEC).handle(file("txt",10L,JUN)).isPresent()); }
        @Test void dateHandler_beforeAfter(){ assertTrue(new DateHandler(JUN,DEC).handle(file("txt",10L,JAN)).isEmpty()); }
        @Test void dateHandler_afterBefore(){ assertTrue(new DateHandler(JAN,JUN).handle(file("txt",10L,DEC)).isEmpty()); }
        @Test void dateHandler_noUpper()   { assertTrue(new DateHandler(JAN).handle(file("txt",10L,DEC)).isPresent()); }
        @Test void dateHandler_nullBounds(){ assertTrue(new DateHandler(null,null).handle(file("txt",10L,Instant.now())).isPresent()); }

        @Test void chain_extAndSize() {
            FileHandler chain = new ExtensionHandler("pdf");
            chain.setNext(new SizeHandler(500L));
            assertTrue(chain.handle(file("pdf",400L,Instant.now())).isPresent());
            assertTrue(chain.handle(file("jpg",400L,Instant.now())).isEmpty());
            assertTrue(chain.handle(file("pdf",600L,Instant.now())).isEmpty());
        }
        @Test void chain_extSizeDate() {
            FileHandler chain = new ExtensionHandler("jpg");
            chain.setNext(new SizeHandler(1000L));
            chain.setNext(new DateHandler(JAN,DEC));
            assertTrue(chain.handle(file("jpg",500L,JUN)).isPresent());
            assertTrue(chain.handle(file("png",500L,JUN)).isEmpty());
            assertTrue(chain.handle(file("jpg",1001L,JUN)).isEmpty());
            assertTrue(chain.handle(file("jpg",500L,Instant.parse("2023-01-01T00:00:00Z"))).isEmpty());
        }
        @Test void setNext_returnsNext() {
            ExtensionHandler ext = new ExtensionHandler("pdf");
            SizeHandler size = new SizeHandler(100L);
            assertSame(size, ext.setNext(size));
        }
    }

    // =========================================================================
    // 17. CommandHistoryTests
    // =========================================================================
    @Nested
    class CommandHistoryTests {

        @TempDir Path tempDir;

        private Path newFile(String name) throws IOException {
            Path p = tempDir.resolve(name); Files.writeString(p, "content"); return p;
        }

        @Test
        void execute_canUndo_canRedo_cycle(@TempDir Path dir) throws IOException {
            Path src = dir.resolve("a.txt"); Files.writeString(src, "hi");
            Path dst = dir.resolve("b.txt");
            CommandHistory h = new CommandHistory();
            assertFalse(h.canUndo()); assertFalse(h.canRedo());
            h.execute(new MoveFileCommand(src, dst));
            assertTrue(h.canUndo()); assertFalse(h.canRedo()); assertTrue(Files.exists(dst));
            h.undo();
            assertFalse(h.canUndo()); assertTrue(h.canRedo()); assertTrue(Files.exists(src));
            h.redo();
            assertTrue(h.canUndo()); assertFalse(h.canRedo()); assertTrue(Files.exists(dst));
        }

        @Test
        void undoStack_clearedByNewExecute() throws IOException {
            Path src = tempDir.resolve("src.txt"); Files.writeString(src, "hi");
            Path dst1 = tempDir.resolve("dst1.txt"); Path dst2 = tempDir.resolve("dst2.txt");
            CommandHistory h = new CommandHistory();
            h.execute(new MoveFileCommand(src, dst1)); h.undo();
            assertTrue(h.canRedo());
            h.execute(new MoveFileCommand(src, dst2));
            assertFalse(h.canRedo());
        }

        @Test void undo_emptyStack_throws()  { assertThrows(IllegalStateException.class, new CommandHistory()::undo); }
        @Test void redo_emptyStack_throws()  { assertThrows(IllegalStateException.class, new CommandHistory()::redo); }

        @Test
        void undoSize_tracksDepth() throws IOException {
            Path src = newFile("s.txt"); Path mid = tempDir.resolve("mid.txt"); Path end = tempDir.resolve("end.txt");
            CommandHistory h = new CommandHistory();
            assertEquals(0, h.undoSize());
            h.execute(new MoveFileCommand(src, mid)); assertEquals(1, h.undoSize());
            h.execute(new MoveFileCommand(mid, end)); assertEquals(2, h.undoSize());
            h.undo(); assertEquals(1, h.undoSize());
        }

        @Test
        void clear_resetsBothStacks() throws IOException {
            Path src = newFile("c.txt"); Path dst = tempDir.resolve("cd.txt");
            CommandHistory h = new CommandHistory();
            h.execute(new MoveFileCommand(src, dst)); h.undo(); assertTrue(h.canRedo());
            h.clear();
            assertFalse(h.canUndo()); assertFalse(h.canRedo()); assertEquals(0, h.undoSize());
        }
    }

    // =========================================================================
    // 18. FileCommandsTests
    // =========================================================================
    @Nested
    class FileCommandsTests {

        @TempDir Path tempDir;

        // MoveFileCommand ------------------------------------------------------
        @Test void move_execute() throws IOException {
            Path src = tempDir.resolve("src.txt"); Files.writeString(src, "hello");
            Path dst = tempDir.resolve("dst.txt");
            new MoveFileCommand(src, dst).execute();
            assertFalse(Files.exists(src)); assertTrue(Files.exists(dst));
            assertEquals("hello", Files.readString(dst));
        }
        @Test void move_createsParentDirs() throws IOException {
            Path src = tempDir.resolve("file.txt"); Files.writeString(src, "data");
            Path dst = tempDir.resolve("subdir").resolve("nested").resolve("file.txt");
            new MoveFileCommand(src, dst).execute();
            assertTrue(Files.exists(dst));
        }
        @Test void move_undo() throws IOException {
            Path src = tempDir.resolve("orig.txt"); Files.writeString(src, "content");
            Path dst = tempDir.resolve("moved.txt");
            MoveFileCommand cmd = new MoveFileCommand(src, dst);
            cmd.execute(); cmd.undo();
            assertTrue(Files.exists(src)); assertFalse(Files.exists(dst));
        }
        @Test void move_undo_beforeExecute_noop() throws IOException {
            Path src = tempDir.resolve("x.txt"); Files.writeString(src, "hi");
            new MoveFileCommand(src, tempDir.resolve("y.txt")).undo();
            assertTrue(Files.exists(src));
        }
        @Test void move_getters() {
            Path src = tempDir.resolve("a.txt"); Path dst = tempDir.resolve("b.txt");
            MoveFileCommand cmd = new MoveFileCommand(src, dst);
            assertEquals(src, cmd.getSource()); assertEquals(dst, cmd.getDestination());
        }

        // CopyFileCommand ------------------------------------------------------
        @Test void copy_execute() throws IOException {
            Path src = tempDir.resolve("orig.txt"); Files.writeString(src, "value");
            Path dst = tempDir.resolve("copy.txt");
            new CopyFileCommand(src, dst).execute();
            assertTrue(Files.exists(src)); assertTrue(Files.exists(dst));
            assertEquals("value", Files.readString(dst));
        }
        @Test void copy_createsParentDirs() throws IOException {
            Path src = tempDir.resolve("src.txt"); Files.writeString(src, "data");
            new CopyFileCommand(src, tempDir.resolve("a").resolve("b").resolve("copy.txt")).execute();
            assertTrue(Files.exists(tempDir.resolve("a").resolve("b").resolve("copy.txt")));
        }
        @Test void copy_undo_deletesCopy() throws IOException {
            Path src = tempDir.resolve("s.txt"); Files.writeString(src, "hello");
            Path dst = tempDir.resolve("d.txt");
            CopyFileCommand cmd = new CopyFileCommand(src, dst);
            cmd.execute(); cmd.undo();
            assertTrue(Files.exists(src)); assertFalse(Files.exists(dst));
        }
        @Test void copy_undo_beforeExecute_noop() throws IOException {
            Path src = tempDir.resolve("ns.txt"); Files.writeString(src, "hi");
            new CopyFileCommand(src, tempDir.resolve("nd.txt")).undo();
            assertFalse(Files.exists(tempDir.resolve("nd.txt")));
        }
        @Test void copy_getters() {
            Path src = tempDir.resolve("src"); Path dst = tempDir.resolve("dst");
            CopyFileCommand cmd = new CopyFileCommand(src, dst);
            assertEquals(src, cmd.getSource()); assertEquals(dst, cmd.getDestination());
        }

        // RenameFileCommand ----------------------------------------------------
        @Test void rename_execute() throws IOException {
            Path src = tempDir.resolve("old.txt"); Files.writeString(src, "rename me");
            Path dst = tempDir.resolve("new.txt");
            new RenameFileCommand(src, dst).execute();
            assertFalse(Files.exists(src)); assertTrue(Files.exists(dst));
        }
        @Test void rename_undo() throws IOException {
            Path src = tempDir.resolve("before.txt"); Files.writeString(src, "data");
            Path dst = tempDir.resolve("after.txt");
            RenameFileCommand cmd = new RenameFileCommand(src, dst);
            cmd.execute(); cmd.undo();
            assertTrue(Files.exists(src)); assertFalse(Files.exists(dst));
        }
        @Test void rename_undo_beforeExecute_noop() throws IOException {
            Path src = tempDir.resolve("r.txt"); Files.writeString(src, "hi");
            new RenameFileCommand(src, tempDir.resolve("rr.txt")).undo();
            assertTrue(Files.exists(src));
        }
        @Test void rename_getters() {
            Path src = tempDir.resolve("a"); Path dst = tempDir.resolve("b");
            RenameFileCommand cmd = new RenameFileCommand(src, dst);
            assertEquals(src, cmd.getSource()); assertEquals(dst, cmd.getDestination());
        }
    }

    // =========================================================================
    // 19. OperationHistoryTests
    // =========================================================================
    @Nested
    class OperationHistoryTests {

        @BeforeEach
        void clear() { OperationHistory.getInstance().clear(); }

        @Test void getInstance_sameInstance() { assertSame(OperationHistory.getInstance(), OperationHistory.getInstance()); }
        @Test void isEmpty_afterClear()       { assertTrue(OperationHistory.getInstance().isEmpty()); }

        @Test void add_recordsInOrder() {
            OperationHistory h = OperationHistory.getInstance();
            h.add(Instant.parse("2024-01-01T00:00:00Z"), Path.of("/folder"), "By File Type", 10, 0);
            h.add(Instant.parse("2024-06-01T00:00:00Z"), Path.of("/folder"), "By Date",      5,  2);
            assertEquals(2, h.getAll().size());
            assertEquals("By File Type", h.getAll().get(0).mode());
        }

        @Test void getAll_unmodifiable() {
            OperationHistory.getInstance().add(Instant.now(), Path.of("/tmp"), "X", 1, 0);
            assertThrows(UnsupportedOperationException.class, () -> OperationHistory.getInstance().getAll().add(null));
        }

        @Test void clear_removesAll() {
            OperationHistory.getInstance().add(Instant.now(), Path.of("/tmp"), "mode", 1, 0);
            OperationHistory.getInstance().clear();
            assertTrue(OperationHistory.getInstance().isEmpty());
        }

        // OperationRecord
        @Test void formattedTimestamp_nonNull() {
            assertFalse(new OperationRecord(Instant.parse("2024-03-15T10:30:00Z"),
                    Path.of("/home/user/docs"), "By File Type", 5, 1).formattedTimestamp().isBlank());
        }
        @Test void summary_containsKeyInfo() {
            String s = new OperationRecord(Instant.parse("2024-03-15T10:30:00Z"),
                    Path.of("/home/user/downloads"), "By File Type", 7, 2).summary();
            assertTrue(s.contains("downloads"));
            assertTrue(s.contains("By File Type"));
            assertTrue(s.contains("7 moved"));
            assertTrue(s.contains("2 skipped"));
        }
        @Test void summary_noSkipped_notMentioned() {
            assertFalse(new OperationRecord(Instant.now(), Path.of("/tmp/src"), "By Date", 3, 0).summary().contains("skipped"));
        }
        @Test void record_getters() {
            Instant ts = Instant.now(); Path folder = Path.of("/folder");
            OperationRecord r = new OperationRecord(ts, folder, "mode", 10, 2);
            assertEquals(ts, r.timestamp()); assertEquals(folder, r.sourceFolder());
            assertEquals("mode", r.mode()); assertEquals(10, r.filesMoved()); assertEquals(2, r.filesSkipped());
        }
    }

    // =========================================================================
    // 20. AppStateTests
    // =========================================================================
    @Nested
    class AppStateTests {

        /** Stub that records calls without touching any JavaFX node. */
        static class StubController extends MainController {
            boolean scanDisabled, applyDisabled;
            String  status;
            AppState lastTransition;
            @Override public void setBtnScanDisabled(boolean v)  { scanDisabled  = v; }
            @Override public void setBtnApplyDisabled(boolean v) { applyDisabled = v; }
            @Override public void setStatus(String s)            { status = s; }
            @Override public void transitionTo(AppState next)    { lastTransition = next; }
        }

        // IdleState ------------------------------------------------------------
        @Test void idle_onEnter_disablesBoth() {
            StubController c = new StubController(); new IdleState().onEnter(c);
            assertTrue(c.scanDisabled); assertTrue(c.applyDisabled); assertFalse(c.status.isBlank());
        }
        @Test void idle_onBrowse_toFolderSelected() {
            StubController c = new StubController(); new IdleState().onBrowse(c);
            assertInstanceOf(FolderSelectedState.class, c.lastTransition);
        }
        @Test void idle_onScan_noop()     { assertDoesNotThrow(() -> new IdleState().onScan(new StubController())); }
        @Test void idle_onOrganize_noop() { assertDoesNotThrow(() -> new IdleState().onOrganize(new StubController())); }

        // FolderSelectedState --------------------------------------------------
        @Test void folderSelected_onEnter() {
            StubController c = new StubController(); new FolderSelectedState().onEnter(c);
            assertFalse(c.scanDisabled); assertTrue(c.applyDisabled);
        }
        @Test void folderSelected_onScan_toScanning() {
            StubController c = new StubController(); new FolderSelectedState().onScan(c);
            assertInstanceOf(ScanningState.class, c.lastTransition);
        }
        @Test void folderSelected_onOrganize_noop() { assertDoesNotThrow(() -> new FolderSelectedState().onOrganize(new StubController())); }
        @Test void folderSelected_onBrowse_stays()  {
            StubController c = new StubController(); new FolderSelectedState().onBrowse(c);
            assertInstanceOf(FolderSelectedState.class, c.lastTransition);
        }

        // ScanningState --------------------------------------------------------
        @Test void scanning_onEnter_disablesBoth() {
            StubController c = new StubController(); new ScanningState().onEnter(c);
            assertTrue(c.scanDisabled); assertTrue(c.applyDisabled);
        }
        @Test void scanning_onScan_noop_noTransition() {
            StubController c = new StubController(); new ScanningState().onScan(c);
            assertNull(c.lastTransition);
        }
        @Test void scanning_onOrganize_noop() { assertDoesNotThrow(() -> new ScanningState().onOrganize(new StubController())); }

        // CategorizedState -----------------------------------------------------
        @Test void categorized_onEnter_enablesBoth() {
            StubController c = new StubController(); new CategorizedState(42).onEnter(c);
            assertFalse(c.scanDisabled); assertFalse(c.applyDisabled); assertTrue(c.status.contains("42"));
        }
        @Test void categorized_zero_files() {
            StubController c = new StubController(); new CategorizedState(0).onEnter(c); assertTrue(c.status.contains("0"));
        }
        @Test void categorized_onOrganize_toOrganizing() {
            StubController c = new StubController(); new CategorizedState(5).onOrganize(c);
            assertInstanceOf(OrganizingState.class, c.lastTransition);
        }
        @Test void categorized_onBrowse_toFolderSelected() {
            StubController c = new StubController(); new CategorizedState(3).onBrowse(c);
            assertInstanceOf(FolderSelectedState.class, c.lastTransition);
        }
        @Test void categorized_onScan_noop() { assertDoesNotThrow(() -> new CategorizedState(1).onScan(new StubController())); }

        // OrganizingState ------------------------------------------------------
        @Test void organizing_onEnter_disablesBoth() {
            StubController c = new StubController(); new OrganizingState().onEnter(c);
            assertTrue(c.scanDisabled); assertTrue(c.applyDisabled);
        }
        @Test void organizing_onScan_noop()     { assertDoesNotThrow(() -> new OrganizingState().onScan(new StubController())); }
        @Test void organizing_onOrganize_noop() { assertDoesNotThrow(() -> new OrganizingState().onOrganize(new StubController())); }
        @Test void organizing_onBrowse_toFolderSelected() {
            StubController c = new StubController(); new OrganizingState().onBrowse(c);
            assertInstanceOf(FolderSelectedState.class, c.lastTransition);
        }

        // DoneState ------------------------------------------------------------
        @Test void done_onEnter_enablesScanDisablesApply() {
            StubController c = new StubController(); new DoneState(10, 2).onEnter(c);
            assertFalse(c.scanDisabled); assertTrue(c.applyDisabled);
            assertTrue(c.status.contains("10")); assertTrue(c.status.contains("2"));
        }
        @Test void done_zeroSkipped() {
            StubController c = new StubController(); new DoneState(5, 0).onEnter(c);
            assertTrue(c.status.contains("5"));
        }
        @Test void done_onBrowse_toFolderSelected() {
            StubController c = new StubController(); new DoneState(3, 0).onBrowse(c);
            assertInstanceOf(FolderSelectedState.class, c.lastTransition);
        }
        @Test void done_onScan_noop()     { assertDoesNotThrow(() -> new DoneState(1, 0).onScan(new StubController())); }
        @Test void done_onOrganize_noop() { assertDoesNotThrow(() -> new DoneState(1, 0).onOrganize(new StubController())); }
    }

    // =========================================================================
    // 21. MainControllerTests
    // =========================================================================
    @Nested
    class MainControllerTests {

        static class TestableController extends MainController {
            boolean scanDisabled, applyDisabled;
            String  lastStatus;
            AppState lastTransition;

            @Override public void setBtnScanDisabled(boolean v)  { scanDisabled  = v; }
            @Override public void setBtnApplyDisabled(boolean v) { applyDisabled = v; }
            @Override public void setStatus(String s)            { lastStatus = s; }
            @Override public void transitionTo(AppState next) {
                lastTransition = next; next.onEnter(this);
            }

            @SuppressWarnings("unchecked")
            <T> T getField(String name) throws Exception {
                Field f = MainController.class.getDeclaredField(name); f.setAccessible(true); return (T) f.get(this);
            }
            void setField(String name, Object value) throws Exception {
                Field f = MainController.class.getDeclaredField(name); f.setAccessible(true); f.set(this, value);
            }
            void invoke(String methodName) throws Exception {
                Method m = MainController.class.getDeclaredMethod(methodName, javafx.event.ActionEvent.class);
                m.setAccessible(true); m.invoke(this, (Object) null);
            }
        }

        @TempDir Path tempDir;
        TestableController ctrl;

        @BeforeEach void setUp() { ctrl = new TestableController(); }

        // transitionTo ---------------------------------------------------------
        @Test void transitionTo_idle()          { ctrl.transitionTo(new IdleState());           assertTrue(ctrl.scanDisabled);  assertTrue(ctrl.applyDisabled); }
        @Test void transitionTo_folderSelected(){ ctrl.transitionTo(new FolderSelectedState()); assertFalse(ctrl.scanDisabled); assertTrue(ctrl.applyDisabled); }
        @Test void transitionTo_categorized()   { ctrl.transitionTo(new CategorizedState(5));   assertFalse(ctrl.scanDisabled); assertFalse(ctrl.applyDisabled); assertTrue(ctrl.lastStatus.contains("5")); }
        @Test void transitionTo_done()          { ctrl.transitionTo(new DoneState(10, 2));       assertFalse(ctrl.scanDisabled); assertTrue(ctrl.applyDisabled);  assertTrue(ctrl.lastStatus.contains("10")); }
        @Test void transitionTo_organizing()    { ctrl.transitionTo(new OrganizingState());      assertTrue(ctrl.scanDisabled);  assertTrue(ctrl.applyDisabled); }
        @Test void transitionTo_scanning()      { ctrl.transitionTo(new ScanningState());        assertTrue(ctrl.scanDisabled);  assertTrue(ctrl.applyDisabled); }
        @Test void transitionTo_updatesField() throws Exception {
            ctrl.transitionTo(new IdleState()); assertInstanceOf(IdleState.class, ctrl.getField("currentState"));
            ctrl.transitionTo(new FolderSelectedState()); assertInstanceOf(FolderSelectedState.class, ctrl.getField("currentState"));
        }

        // handleUndo -----------------------------------------------------------
        @Test void handleUndo_empty()    throws Exception { ctrl.invoke("handleUndo"); assertTrue(ctrl.lastStatus.contains("Nothing to undo")); }
        @Test void handleUndo_performs() throws Exception {
            Path src = tempDir.resolve("a.txt"); Files.writeString(src, "hi");
            Path dst = tempDir.resolve("b.txt");
            ctrl.<CommandHistory>getField("commandHistory").execute(new MoveFileCommand(src, dst));
            ctrl.invoke("handleUndo");
            assertTrue(Files.exists(src)); assertFalse(ctrl.lastStatus.contains("failed"));
        }

        // handleRedo -----------------------------------------------------------
        @Test void handleRedo_empty()    throws Exception { ctrl.invoke("handleRedo"); assertTrue(ctrl.lastStatus.contains("Nothing to redo")); }
        @Test void handleRedo_performs() throws Exception {
            Path src = tempDir.resolve("r.txt"); Files.writeString(src, "data");
            Path dst = tempDir.resolve("rd.txt");
            CommandHistory h = ctrl.getField("commandHistory");
            h.execute(new MoveFileCommand(src, dst)); h.undo();
            ctrl.invoke("handleRedo");
            assertTrue(Files.exists(dst)); assertFalse(ctrl.lastStatus.contains("failed"));
        }

        // handleUndoAll --------------------------------------------------------
        @Test void handleUndoAll_noOrganizer()         throws Exception { ctrl.invoke("handleUndoAll"); assertTrue(ctrl.lastStatus.contains("No organize operation")); }
        @Test void handleUndoAll_nullMemento()          throws Exception {
            ctrl.setField("lastOrganizer", new FileOrganizer()); ctrl.invoke("handleUndoAll");
            assertTrue(ctrl.lastStatus.contains("No organize operation"));
        }
        @Test void handleUndoAll_restores()             throws Exception {
            Path src = Files.createDirectory(tempDir.resolve("src"));
            Path file = src.resolve("doc.pdf"); Files.writeString(file, "content");
            FileOrganizer org = new FileOrganizer();
            org.applyCategorization(Map.of("Docs", List.of(new ScannedFile(file, "pdf", 7L, Instant.now()))),
                    tempDir.resolve("target"));
            ctrl.setField("lastOrganizer", org);
            ctrl.invoke("handleUndoAll");
            assertTrue(Files.exists(file)); assertFalse(ctrl.lastStatus.toLowerCase().contains("failed"));
        }

        // handleScan / handleApply guards --------------------------------------
        @Test void handleScan_nullFolder()   throws Exception { ctrl.invoke("handleScan"); assertTrue(ctrl.lastStatus.contains("folder")); }
        @Test void handleApply_nullFolder()  throws Exception { ctrl.invoke("handleApply"); assertNotNull(ctrl.lastStatus); }
        @Test void handleApply_emptyFiles()  throws Exception {
            ctrl.setField("selectedFolder", tempDir); ctrl.invoke("handleApply");
            assertTrue(ctrl.lastStatus.contains("Nothing to organize"));
        }

        // trivial handlers -----------------------------------------------------
        @Test void handleOrganize_doesNotThrow() { assertDoesNotThrow(() -> ctrl.invoke("handleOrganize")); }
        @Test void handleSettings_doesNotThrow() { assertDoesNotThrow(() -> ctrl.invoke("handleSettings")); }

        // state callbacks ------------------------------------------------------
        @Test void onBrowse_fromIdle_toFolderSelected() {
            ctrl.transitionTo(new IdleState()); new IdleState().onBrowse(ctrl);
            assertInstanceOf(FolderSelectedState.class, ctrl.lastTransition);
        }
        @Test void onScan_fromFolderSelected_toScanning() {
            ctrl.transitionTo(new FolderSelectedState()); new FolderSelectedState().onScan(ctrl);
            assertInstanceOf(ScanningState.class, ctrl.lastTransition);
        }
        @Test void onOrganize_fromCategorized_toOrganizing() {
            ctrl.transitionTo(new CategorizedState(3)); new CategorizedState(3).onOrganize(ctrl);
            assertInstanceOf(OrganizingState.class, ctrl.lastTransition);
        }
    }

    // =========================================================================
    // 22. TestFilesGeneratorTests
    // =========================================================================
    @Nested
    class TestFilesGeneratorTests {

        private static final Path TEST_DIR =
                Path.of(System.getProperty("user.home"), ".musannif-test");

        @AfterEach
        void cleanup() throws IOException { deleteRecursively(TEST_DIR); }

        @Test void generate_createsDirectory() {
            silentDelete(TEST_DIR);
            TestFilesGenerator.generate();
            assertTrue(Files.exists(TEST_DIR)); assertTrue(Files.isDirectory(TEST_DIR));
        }

        @Test void generate_createsExpectedFileCount() throws IOException {
            silentDelete(TEST_DIR); TestFilesGenerator.generate();
            try (var e = Files.list(TEST_DIR)) { assertEquals(36, e.filter(Files::isRegularFile).count()); }
        }

        @Test void generate_filesHaveLastModified() throws IOException {
            silentDelete(TEST_DIR); TestFilesGenerator.generate();
            try (var e = Files.list(TEST_DIR)) {
                for (Path p : e.toList()) assertTrue(Files.getLastModifiedTime(p).toInstant()
                        .isAfter(Instant.parse("2020-01-01T00:00:00Z")));
            }
        }

        @Test void generate_allCategoriesPresent() throws IOException {
            silentDelete(TEST_DIR); TestFilesGenerator.generate();
            try (var e = Files.list(TEST_DIR)) {
                var names = e.map(p -> p.getFileName().toString()).toList();
                assertTrue(names.stream().anyMatch(n -> n.endsWith(".pdf")));
                assertTrue(names.stream().anyMatch(n -> n.endsWith(".png")));
                assertTrue(names.stream().anyMatch(n -> n.endsWith(".mp4")));
                assertTrue(names.stream().anyMatch(n -> n.endsWith(".mp3")));
                assertTrue(names.stream().anyMatch(n -> n.endsWith(".zip")));
            }
        }

        @Test void generate_calledTwice_replaces() throws IOException {
            silentDelete(TEST_DIR); TestFilesGenerator.generate();
            Path extra = TEST_DIR.resolve("extra_file.tmp"); Files.writeString(extra, "gone");
            TestFilesGenerator.generate();
            assertFalse(Files.exists(extra)); assertTrue(Files.exists(TEST_DIR));
        }

        @Test void generate_calledTwice_fileCountCorrect() throws IOException {
            silentDelete(TEST_DIR); TestFilesGenerator.generate(); TestFilesGenerator.generate();
            try (var e = Files.list(TEST_DIR)) { assertEquals(36, e.filter(Files::isRegularFile).count()); }
        }

        @Test void generate_withNestedSubdir_deletesRecursively() throws IOException {
            silentDelete(TEST_DIR); Files.createDirectories(TEST_DIR);
            Path sub = Files.createDirectory(TEST_DIR.resolve("subdir"));
            Files.writeString(sub.resolve("nested.txt"), "nested");
            Files.writeString(Files.createDirectory(sub.resolve("deeper")).resolve("deep.txt"), "deep");
            TestFilesGenerator.generate();
            assertFalse(Files.exists(sub)); assertTrue(Files.exists(TEST_DIR));
        }

        // helpers
        private static void silentDelete(Path p) { try { deleteRecursively(p); } catch (IOException ignored) {} }
        private static void deleteRecursively(Path path) throws IOException {
            if (!Files.exists(path)) return;
            if (Files.isDirectory(path))
                try (var e = Files.list(path)) { for (Path entry : e.toList()) deleteRecursively(entry); }
            Files.deleteIfExists(path);
        }
    }
}
