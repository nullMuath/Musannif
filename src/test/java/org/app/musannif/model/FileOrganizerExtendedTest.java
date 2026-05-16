package org.app.musannif.model;

import org.app.musannif.model.core.command.CommandHistory;
import org.app.musannif.model.core.command.MoveFileCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Targeted FileOrganizer tests to push Line % from 72% → 90%+ and
 * Branch % from 63% → 90%+.
 *
 * Gaps targeted (read from coverage report):
 *
 *  sanitizeFolderName
 *    ✓ null key         → "Other"
 *    ✓ blank key        → "Other"
 *    ✓ special chars    → underscores
 *
 *  resolveAvailableDestination
 *    ✓ no collision     → returns original path
 *    ✓ one collision    → counter = 1
 *    ✓ two collisions   → counter = 2  (while loop 2nd iteration)
 *    ✓ file has no dot  → baseName = whole name, ext = ""
 *    ✓ leading dot only → treated as no extension
 *    ✓ trailing dot     → ext = ""
 *
 *  applyCategorization edge branches
 *    ✓ null list value  → skipped
 *    ✓ empty list       → skipped
 *    ✓ null ScannedFile → skipped
 *    ✓ ScannedFile with null path → skipped
 *    ✓ file does not exist on disk → skipped
 *    ✓ IOException during move → skippedFiles++
 *
 *  getBaseName / getExtensionWithDot (via reflection for direct branch coverage)
 *    ✓ dotIndex == -1   → full name / ""
 *    ✓ dotIndex == 0    → full name / ""  (leading dot, e.g. ".hidden")
 *    ✓ dotIndex == length-1 → full name / ""  (trailing dot)
 *    ✓ normal case      → base / ".ext"
 *
 * Place this file at:
 *   src/test/java/org/app/musannif/model/FileOrganizerExtendedTest.java
 */
class FileOrganizerExtendedTest {

    @TempDir Path tempDir;

    private ScannedFile realFile(Path dir, String name) throws IOException {
        Path p = dir.resolve(name);
        Files.writeString(p, "data");
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
        return new ScannedFile(p, ext, 4L, Instant.now());
    }

    // =========================================================================
    // sanitizeFolderName — all branches
    // =========================================================================

    @Test
    void sanitize_nullKey_movesToOther() throws IOException {
        Path src  = Files.createDirectory(tempDir.resolve("s1"));
        Path tgt  = tempDir.resolve("t1");
        ScannedFile sf = realFile(src, "file.txt");

        Map<String, List<ScannedFile>> map = new HashMap<>();
        map.put(null, List.of(sf));

        FileOrganizer org = new FileOrganizer();
        FileOrganizer.OrganizationResult r = org.applyCategorization(map, tgt);

        assertEquals(1, r.movedFiles());
        assertTrue(Files.exists(tgt.resolve("Other").resolve("file.txt")));
    }

    @Test
    void sanitize_blankKey_movesToOther() throws IOException {
        Path src  = Files.createDirectory(tempDir.resolve("s2"));
        Path tgt  = tempDir.resolve("t2");
        ScannedFile sf = realFile(src, "note.txt");

        FileOrganizer org = new FileOrganizer();
        org.applyCategorization(Map.of("   ", List.of(sf)), tgt);

        assertTrue(Files.exists(tgt.resolve("Other").resolve("note.txt")));
    }

    @Test
    void sanitize_emptyKey_movesToOther() throws IOException {
        Path src  = Files.createDirectory(tempDir.resolve("s3"));
        Path tgt  = tempDir.resolve("t3");
        ScannedFile sf = realFile(src, "a.pdf");

        FileOrganizer org = new FileOrganizer();
        org.applyCategorization(Map.of("", List.of(sf)), tgt);

        assertTrue(Files.exists(tgt.resolve("Other").resolve("a.pdf")));
    }

    @Test
    void sanitize_specialChars_replacedWithUnderscores() throws IOException {
        Path src  = Files.createDirectory(tempDir.resolve("s4"));
        Path tgt  = tempDir.resolve("t4");
        ScannedFile sf = realFile(src, "data.csv");

        FileOrganizer org = new FileOrganizer();
        org.applyCategorization(Map.of("My:Folder*Name?", List.of(sf)), tgt);

        // illegal chars replaced: "My_Folder_Name_"  (trimmed if trailing space)
        assertTrue(Files.list(tgt).findFirst().isPresent(),
                "Target should contain at least one folder");
        // the sanitized folder name must exist
        long matchCount = Files.list(tgt)
                .filter(p -> p.getFileName().toString().startsWith("My_"))
                .count();
        assertEquals(1, matchCount);
    }

    @Test
    void sanitize_backslashAndSlash_replacedWithUnderscores() throws IOException {
        Path src = Files.createDirectory(tempDir.resolve("s5"));
        Path tgt = tempDir.resolve("t5");
        ScannedFile sf = realFile(src, "img.png");

        FileOrganizer org = new FileOrganizer();
        org.applyCategorization(Map.of("a/b\\c", List.of(sf)), tgt);

        // "a_b_c" should be the folder name
        assertTrue(Files.exists(tgt.resolve("a_b_c").resolve("img.png")));
    }

    // =========================================================================
    // resolveAvailableDestination — collision counter branches
    // =========================================================================

    @Test
    void duplicate_noCollision_usesOriginalName() throws IOException {
        Path src = Files.createDirectory(tempDir.resolve("nc"));
        Path tgt = tempDir.resolve("nct");
        ScannedFile sf = realFile(src, "unique.pdf");

        FileOrganizer org = new FileOrganizer();
        FileOrganizer.OrganizationResult r = org.applyCategorization(
                Map.of("Cat", List.of(sf)), tgt);

        assertEquals(1, r.movedFiles());
        assertTrue(Files.exists(tgt.resolve("Cat").resolve("unique.pdf")));
    }

    @Test
    void duplicate_oneCollision_renamedWith1() throws IOException {
        Path src1 = Files.createDirectory(tempDir.resolve("oc1"));
        Path src2 = Files.createDirectory(tempDir.resolve("oc2"));
        Path tgt  = tempDir.resolve("oct");

        ScannedFile sf1 = realFile(src1, "report.pdf");
        ScannedFile sf2 = realFile(src2, "report.pdf");

        FileOrganizer org = new FileOrganizer();
        FileOrganizer.OrganizationResult r = org.applyCategorization(
                Map.of("Docs", List.of(sf1, sf2)), tgt);

        assertEquals(2, r.movedFiles());
        assertTrue(Files.exists(tgt.resolve("Docs").resolve("report.pdf")));
        assertTrue(Files.exists(tgt.resolve("Docs").resolve("report (1).pdf")));
    }

    @Test
    void duplicate_twoCollisions_renamedWith1and2() throws IOException {
        Path src1 = Files.createDirectory(tempDir.resolve("tc1"));
        Path src2 = Files.createDirectory(tempDir.resolve("tc2"));
        Path src3 = Files.createDirectory(tempDir.resolve("tc3"));
        Path tgt  = tempDir.resolve("tct");

        ScannedFile sf1 = realFile(src1, "data.xlsx");
        ScannedFile sf2 = realFile(src2, "data.xlsx");
        ScannedFile sf3 = realFile(src3, "data.xlsx");

        FileOrganizer org = new FileOrganizer();
        org.applyCategorization(Map.of("Sheet", List.of(sf1, sf2, sf3)), tgt);

        assertTrue(Files.exists(tgt.resolve("Sheet").resolve("data.xlsx")));
        assertTrue(Files.exists(tgt.resolve("Sheet").resolve("data (1).xlsx")));
        assertTrue(Files.exists(tgt.resolve("Sheet").resolve("data (2).xlsx")));
    }

    @Test
    void duplicate_fileWithNoDot_renamedWithoutExtension() throws IOException {
        Path src1 = Files.createDirectory(tempDir.resolve("nd1"));
        Path src2 = Files.createDirectory(tempDir.resolve("nd2"));
        Path tgt  = tempDir.resolve("ndt");

        // Files with no extension
        Path p1 = src1.resolve("Makefile");
        Path p2 = src2.resolve("Makefile");
        Files.writeString(p1, "a");
        Files.writeString(p2, "b");

        ScannedFile sf1 = new ScannedFile(p1, "", 1L, Instant.now());
        ScannedFile sf2 = new ScannedFile(p2, "", 1L, Instant.now());

        FileOrganizer org = new FileOrganizer();
        org.applyCategorization(Map.of("Build", List.of(sf1, sf2)), tgt);

        assertTrue(Files.exists(tgt.resolve("Build").resolve("Makefile")));
        // renamed: "Makefile (1)" — no extension appended
        assertTrue(Files.exists(tgt.resolve("Build").resolve("Makefile (1)")));
    }

    @Test
    void duplicate_fileWithTrailingDot_extensionIsEmpty() throws IOException {
        Path src1 = Files.createDirectory(tempDir.resolve("td1"));
        Path src2 = Files.createDirectory(tempDir.resolve("td2"));
        Path tgt  = tempDir.resolve("tdt");

        Path p1 = src1.resolve("file.");
        Path p2 = src2.resolve("file.");
        Files.writeString(p1, "a");
        Files.writeString(p2, "b");

        ScannedFile sf1 = new ScannedFile(p1, "", 1L, Instant.now());
        ScannedFile sf2 = new ScannedFile(p2, "", 1L, Instant.now());

        FileOrganizer org = new FileOrganizer();
        FileOrganizer.OrganizationResult r = org.applyCategorization(
                Map.of("TDot", List.of(sf1, sf2)), tgt);

        assertEquals(2, r.movedFiles());
    }

    // =========================================================================
    // applyCategorization — null / missing file guards
    // =========================================================================

    @Test
    void nullListValue_skipsSilently() throws IOException {
        Path tgt = tempDir.resolve("nullList");
        Map<String, List<ScannedFile>> map = new HashMap<>();
        map.put("Docs", null);

        FileOrganizer org = new FileOrganizer();
        FileOrganizer.OrganizationResult r = org.applyCategorization(map, tgt);
        assertEquals(0, r.movedFiles());
        assertEquals(0, r.skippedFiles());
    }

    @Test
    void emptyListValue_skipsSilently() throws IOException {
        Path tgt = tempDir.resolve("emptyList");
        FileOrganizer org = new FileOrganizer();
        FileOrganizer.OrganizationResult r = org.applyCategorization(
                Map.of("Docs", List.of()), tgt);
        assertEquals(0, r.movedFiles());
    }

    @Test
    void nullScannedFile_inList_skipped() throws IOException {
        Path tgt = tempDir.resolve("nullSF");
        Map<String, List<ScannedFile>> map = new HashMap<>();
        List<ScannedFile> list = new ArrayList<>();
        list.add(null);
        map.put("Cat", list);

        FileOrganizer org = new FileOrganizer();
        FileOrganizer.OrganizationResult r = org.applyCategorization(map, tgt);
        assertEquals(0, r.movedFiles());
    }

    @Test
    void scannedFile_withNullPath_skipped() throws IOException {
        Path tgt = tempDir.resolve("nullPath");
        ScannedFile ghost = new ScannedFile(null, "pdf", 0L, Instant.now());
        Map<String, List<ScannedFile>> map = new HashMap<>();
        map.put("Docs", List.of(ghost));

        FileOrganizer org = new FileOrganizer();
        // Should not throw; the null path check guards it
        assertDoesNotThrow(() -> org.applyCategorization(map, tgt));
    }

    @Test
    void nonExistentSourceFile_isSkippedFromMoves() throws IOException {
        Path tgt = tempDir.resolve("ghost");
        // File does not exist on disk
        ScannedFile ghost = new ScannedFile(
                tempDir.resolve("doesNotExist.pdf"), "pdf", 0L, Instant.now());

        FileOrganizer org = new FileOrganizer();
        FileOrganizer.OrganizationResult r = org.applyCategorization(
                Map.of("Docs", List.of(ghost)), tgt);

        assertEquals(0, r.movedFiles());
        assertEquals(0, r.skippedFiles()); // not counted as skipped; just not planned
    }

    // =========================================================================
    // IOException path → skippedFiles++
    // Achieved by making the destination parent an existing FILE (not a dir),
    // so Files.createDirectories throws.
    // =========================================================================

    @Test
    void ioException_duringMove_incrementsSkipped() throws IOException {
        // Create a file where the category sub-folder should go,
        // so createDirectories("target/Blocker/...") throws because
        // "target/Blocker" exists as a plain file.
        Path target = tempDir.resolve("ioTarget");
        Files.createDirectory(target);

        // Pre-create "target/Docs" as a FILE (not a directory)
        Path blocker = target.resolve("Docs");
        Files.writeString(blocker, "I am a file");

        Path src = Files.createDirectory(tempDir.resolve("ioSrc"));
        Path p = src.resolve("blocked.pdf");
        Files.writeString(p, "content");
        ScannedFile sf = new ScannedFile(p, "pdf", 7L, Instant.now());

        FileOrganizer org = new FileOrganizer();
        FileOrganizer.OrganizationResult r = org.applyCategorization(
                Map.of("Docs", List.of(sf)), target);

        // The move fails because target/Docs is a file, not a directory
        assertEquals(0, r.movedFiles());
        assertEquals(1, r.skippedFiles());
    }

    // =========================================================================
    // getBaseName / getExtensionWithDot — direct via reflection
    // (covers every branch of the two-line ternary expressions)
    // =========================================================================

    private String callGetBaseName(String fileName) throws Exception {
        Method m = FileOrganizer.class.getDeclaredMethod("getBaseName", String.class);
        m.setAccessible(true);
        return (String) m.invoke(new FileOrganizer(), fileName);
    }

    private String callGetExtensionWithDot(String fileName) throws Exception {
        Method m = FileOrganizer.class.getDeclaredMethod("getExtensionWithDot", String.class);
        m.setAccessible(true);
        return (String) m.invoke(new FileOrganizer(), fileName);
    }

    @Test
    void getBaseName_noDot_returnsFullName() throws Exception {
        assertEquals("Makefile", callGetBaseName("Makefile"));
    }

    @Test
    void getBaseName_leadingDot_returnsFullName() throws Exception {
        // dotIndex == 0  → treated as "no extension" → return full name
        assertEquals(".hidden", callGetBaseName(".hidden"));
    }

    @Test
    void getBaseName_normalFile_returnsNameWithoutExtension() throws Exception {
        assertEquals("report", callGetBaseName("report.pdf"));
    }

    @Test
    void getExtensionWithDot_noDot_returnsEmpty() throws Exception {
        assertEquals("", callGetExtensionWithDot("Makefile"));
    }

    @Test
    void getExtensionWithDot_leadingDot_returnsEmpty() throws Exception {
        // dotIndex == 0  → return ""
        assertEquals("", callGetExtensionWithDot(".hidden"));
    }

    @Test
    void getExtensionWithDot_trailingDot_returnsEmpty() throws Exception {
        // dotIndex == length - 1  → return ""
        assertEquals("", callGetExtensionWithDot("file."));
    }

    @Test
    void getExtensionWithDot_normalFile_returnsDotPlusExtension() throws Exception {
        assertEquals(".pdf", callGetExtensionWithDot("report.pdf"));
        assertEquals(".xlsx", callGetExtensionWithDot("data.xlsx"));
    }

    // =========================================================================
    // Shared CommandHistory constructor + canUndo / canRedo after organize
    // =========================================================================

    @Test
    void sharedHistory_canUndoAfterOrganize() throws IOException {
        CommandHistory shared = new CommandHistory();
        FileOrganizer org = new FileOrganizer(shared);

        Path src = Files.createDirectory(tempDir.resolve("shSrc"));
        Path tgt = tempDir.resolve("shTgt");
        ScannedFile sf = realFile(src, "x.txt");

        org.applyCategorization(Map.of("T", List.of(sf)), tgt);
        assertTrue(shared.canUndo());
        assertFalse(shared.canRedo());
    }

    @Test
    void canRedo_afterUndoLastMove() throws IOException {
        Path src = Files.createDirectory(tempDir.resolve("rdSrc"));
        Path tgt = tempDir.resolve("rdTgt");
        ScannedFile sf = realFile(src, "y.txt");

        FileOrganizer org = new FileOrganizer();
        org.applyCategorization(Map.of("T", List.of(sf)), tgt);

        assertTrue(org.canUndo());
        assertFalse(org.canRedo());

        org.undoLastMove();
        assertFalse(org.canUndo());
        assertTrue(org.canRedo());

        org.redoLastMove();
        assertTrue(org.canUndo());
        assertFalse(org.canRedo());
    }

    // =========================================================================
    // Multiple files across multiple categories in one call
    // =========================================================================

    @Test
    void multipleCategories_allMoved_correctCounts() throws IOException {
        Path src = Files.createDirectory(tempDir.resolve("multi"));
        Path tgt = tempDir.resolve("multiTgt");

        ScannedFile pdf  = realFile(src, "doc.pdf");
        ScannedFile jpg  = realFile(src, "img.jpg");
        ScannedFile mp3  = realFile(src, "song.mp3");

        Map<String, List<ScannedFile>> cats = new LinkedHashMap<>();
        cats.put("Documents", List.of(pdf));
        cats.put("Images",    List.of(jpg));
        cats.put("Audio",     List.of(mp3));

        FileOrganizer org = new FileOrganizer();
        FileOrganizer.OrganizationResult r = org.applyCategorization(cats, tgt);

        assertEquals(3, r.movedFiles());
        assertEquals(0, r.skippedFiles());
        assertTrue(Files.exists(tgt.resolve("Documents").resolve("doc.pdf")));
        assertTrue(Files.exists(tgt.resolve("Images").resolve("img.jpg")));
        assertTrue(Files.exists(tgt.resolve("Audio").resolve("song.mp3")));
    }
}
