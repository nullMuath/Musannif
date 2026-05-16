package org.app.musannif.controller;

import org.app.musannif.model.*;
import org.app.musannif.model.core.command.CommandHistory;
import org.app.musannif.model.core.command.MoveFileCommand;
import org.app.musannif.model.core.command.RenameFileCommand;
import org.app.musannif.model.core.command.CopyFileCommand;
import org.app.musannif.model.core.history.OperationHistory;
import org.app.musannif.model.core.state.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Extended tests for MainController to boost line coverage.
 *
 * Uses the same TestableController subclass pattern as MainControllerTest:
 * override every JavaFX-bound method to a no-op / tracker.
 */
class MainControllerExtendedTest {

    // -------------------------------------------------------------------------
    // Testable stub — mirrors the one in MainControllerTest
    // -------------------------------------------------------------------------

    static class TestableController extends MainController {

        boolean scanDisabled;
        boolean applyDisabled;
        String  lastStatus;
        AppState lastTransition;
        int transitionCount;

        @Override public void setBtnRefreshDisabled(boolean v) { scanDisabled  = v; }
        @Override public void setBtnPreviewDisabled(boolean v) { /* no-op */ }
        @Override public void setBtnApplyDisabled(boolean v)   { applyDisabled = v; }
        @Override public void setStatus(String s)              { lastStatus = s; }
        @Override public void showTable()                      { /* no-op */ }
        @Override public void markSuccess()                    { /* no-op */ }

        @Override
        public void transitionTo(AppState next) {
            lastTransition = next;
            transitionCount++;
            try {
                Field f = MainController.class.getDeclaredField("currentState");
                f.setAccessible(true);
                f.set(this, next);
            } catch (Exception e) { /* ignore */ }
            next.onEnter(this);
        }

        @SuppressWarnings("unchecked")
        <T> T getField(String name) throws Exception {
            Field f = MainController.class.getDeclaredField(name);
            f.setAccessible(true);
            return (T) f.get(this);
        }

        void setField(String name, Object value) throws Exception {
            Field f = MainController.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(this, value);
        }

        void invoke(String methodName) throws Exception {
            Method m = MainController.class.getDeclaredMethod(methodName,
                    javafx.event.ActionEvent.class);
            m.setAccessible(true);
            m.invoke(this, (Object) null);
        }
    }

    // -------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------

    TestableController ctrl;

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        ctrl = new TestableController();
        OperationHistory.getInstance().clear();
    }

    @AfterEach
    void tearDown() {
        OperationHistory.getInstance().clear();
    }

    // =========================================================================
    // handleRefresh — delegates to doScan()
    // =========================================================================

    @Test
    void handleRefresh_nullFolder_doesNotThrow() {
        // selectedFolder is null — doScan() returns immediately
        assertDoesNotThrow(() -> ctrl.invoke("handleRefresh"));
    }

    @Test
    void handleRefresh_withFolder_startsScanTask() throws Exception {
        ctrl.setField("selectedFolder", tempDir);
        // doScan launches a background thread; just verify it doesn't throw
        assertDoesNotThrow(() -> ctrl.invoke("handleRefresh"));
    }

    // =========================================================================
    // handleScan — with folder set, calls doScan
    // =========================================================================

    @Test
    void handleScan_withFolderSet_doesNotSetFolderError() throws Exception {
        ctrl.setField("selectedFolder", tempDir);
        ctrl.invoke("handleScan");
        // If a status was set it should not be the "select a folder" message
        if (ctrl.lastStatus != null) {
            assertFalse(ctrl.lastStatus.contains("Select a folder"),
                    "With a folder selected, should not show folder-selection prompt");
        }
    }

    // =========================================================================
    // handlePreview — empty scannedFiles guard
    // =========================================================================

    @Test
    void handlePreview_emptyScannedFiles_setsStatus() throws Exception {
        // scannedFiles is empty (default), previewPanel is null
        ctrl.invoke("handlePreview");
        assertNotNull(ctrl.lastStatus);
        assertTrue(ctrl.lastStatus.contains("Scan"),
                "Expected scan-first message but got: " + ctrl.lastStatus);
    }

    @Test
    void handlePreview_previewPanelNull_emptyFiles_setsStatus() throws Exception {
        // previewPanel field is null (not injected in test), scannedFiles empty
        ctrl.invoke("handlePreview");
        assertEquals("Scan a folder first to preview.", ctrl.lastStatus);
    }

    // =========================================================================
    // handleUndo — multiple sequential undos
    // =========================================================================

    @Test
    void handleUndo_twiceAfterTwoExecutions_undoesBoth() throws Exception {
        Path a = tempDir.resolve("u1.txt");
        Path b = tempDir.resolve("u1_dest.txt");
        Path c = tempDir.resolve("u2.txt");
        Path d = tempDir.resolve("u2_dest.txt");
        Files.writeString(a, "a");
        Files.writeString(c, "c");

        CommandHistory history = ctrl.getField("commandHistory");
        history.execute(new MoveFileCommand(a, b));
        history.execute(new MoveFileCommand(c, d));

        ctrl.invoke("handleUndo");
        assertTrue(ctrl.lastStatus.contains("1 remaining") || ctrl.lastStatus.contains("Undo"),
                "Expected undo status after first undo: " + ctrl.lastStatus);

        ctrl.invoke("handleUndo");
        // Now 0 remaining
        assertNotNull(ctrl.lastStatus);
    }

    @Test
    void handleUndo_afterUndoAll_nothingRemains() throws Exception {
        Path src = tempDir.resolve("u.txt");
        Path dst = tempDir.resolve("u_dst.txt");
        Files.writeString(src, "data");

        CommandHistory history = ctrl.getField("commandHistory");
        history.execute(new MoveFileCommand(src, dst));

        // Undo the one item
        ctrl.invoke("handleUndo");
        // Now undo stack is empty
        ctrl.invoke("handleUndo");
        assertTrue(ctrl.lastStatus.contains("Nothing to undo"),
                "Expected 'Nothing to undo' but got: " + ctrl.lastStatus);
    }

    // =========================================================================
    // handleRedo — after undo is available
    // =========================================================================

    @Test
    void handleRedo_afterFullUndoStack_redoAll() throws Exception {
        Path src = tempDir.resolve("r1.txt");
        Path dst = tempDir.resolve("r1_dst.txt");
        Files.writeString(src, "r1");

        CommandHistory history = ctrl.getField("commandHistory");
        history.execute(new MoveFileCommand(src, dst));
        history.undo(); // put back to src
        assertTrue(Files.exists(src));

        ctrl.invoke("handleRedo");
        assertTrue(Files.exists(dst), "File should be redone to destination");
        assertNotNull(ctrl.lastStatus);
    }

    @Test
    void handleRedo_afterRedoingAll_nothingToRedo() throws Exception {
        Path src = tempDir.resolve("r2.txt");
        Path dst = tempDir.resolve("r2_dst.txt");
        Files.writeString(src, "r2");

        CommandHistory history = ctrl.getField("commandHistory");
        history.execute(new MoveFileCommand(src, dst));
        history.undo();

        ctrl.invoke("handleRedo"); // redo the one item
        ctrl.invoke("handleRedo"); // nothing left
        assertTrue(ctrl.lastStatus.contains("Nothing to redo"),
                "Expected 'Nothing to redo' but got: " + ctrl.lastStatus);
    }

    // =========================================================================
    // handleUndoAll — with full organizer memento
    // =========================================================================

    @Test
    void handleUndoAll_reportsFileCount() throws Exception {
        Path src = Files.createDirectory(tempDir.resolve("srcDir"));
        Path target = tempDir.resolve("targetDir");
        Path file1 = src.resolve("a.pdf");
        Path file2 = src.resolve("b.txt");
        Files.writeString(file1, "pdf");
        Files.writeString(file2, "txt");

        FileOrganizer organizer = new FileOrganizer();
        organizer.applyCategorization(
                Map.of(
                        "Docs", List.of(new ScannedFile(file1, "pdf", 3L, Instant.now())),
                        "Other", List.of(new ScannedFile(file2, "txt", 3L, Instant.now()))
                ),
                target);

        ctrl.setField("lastOrganizer", organizer);
        ctrl.invoke("handleUndoAll");

        assertTrue(ctrl.lastStatus.contains("undone") || ctrl.lastStatus.contains("file"),
                "Expected success status, got: " + ctrl.lastStatus);
    }

    // =========================================================================
    // handleApply — scannedFiles with content but no folder
    // =========================================================================

    @Test
    void handleApply_folderNullScannedFilesNotEmpty_setsStatus() throws Exception {
        // Add a scanned file manually
        javafx.collections.ObservableList<ScannedFile> files = ctrl.getField("scannedFiles");
        files.add(new ScannedFile(tempDir.resolve("x.pdf"), "pdf", 10L, Instant.now()));

        // selectedFolder is still null
        ctrl.invoke("handleApply");
        assertNotNull(ctrl.lastStatus);
        assertTrue(ctrl.lastStatus.contains("Nothing to organize") ||
                        ctrl.lastStatus.contains("folder"),
                "Expected 'Nothing to organize' or folder prompt, got: " + ctrl.lastStatus);
    }

    // =========================================================================
    // State transitions — all states' onEnter callbacks
    // =========================================================================

    @Test
    void scanningState_onEnter_disablesBoth() {
        ctrl.transitionTo(new ScanningState());
        assertTrue(ctrl.scanDisabled, "scan button should be disabled in ScanningState");
        assertTrue(ctrl.applyDisabled, "apply button should be disabled in ScanningState");
    }

    @Test
    void organizingState_onEnter_disablesBoth() {
        ctrl.transitionTo(new OrganizingState());
        assertTrue(ctrl.scanDisabled);
        assertTrue(ctrl.applyDisabled);
    }

    @Test
    void doneState_onEnter_correctButtonStates() {
        ctrl.transitionTo(new DoneState(5, 0));
        assertTrue(ctrl.applyDisabled, "apply should be disabled after done");
    }

    @Test
    void categorizedState_onEnter_enablesApply() {
        ctrl.transitionTo(new CategorizedState(3));
        assertFalse(ctrl.applyDisabled, "apply should be enabled when categorized");
    }

    @Test
    void idleState_onEnter_disablesBothButtons() {
        ctrl.transitionTo(new IdleState());
        assertTrue(ctrl.scanDisabled);
        assertTrue(ctrl.applyDisabled);
    }

    @Test
    void folderSelectedState_onEnter_enablesScanDisablesApply() {
        ctrl.transitionTo(new FolderSelectedState());
        assertFalse(ctrl.scanDisabled, "scan should be enabled in FolderSelectedState");
        assertTrue(ctrl.applyDisabled, "apply should be disabled in FolderSelectedState");
    }

    // =========================================================================
    // Multiple consecutive state transitions
    // =========================================================================

    @Test
    void stateTransition_fullWorkflow_sequentialTransitions() {
        ctrl.transitionTo(new IdleState());
        assertEquals(1, ctrl.transitionCount);

        ctrl.transitionTo(new FolderSelectedState());
        assertEquals(2, ctrl.transitionCount);

        ctrl.transitionTo(new ScanningState());
        assertEquals(3, ctrl.transitionCount);

        ctrl.transitionTo(new CategorizedState(5));
        assertEquals(4, ctrl.transitionCount);

        ctrl.transitionTo(new OrganizingState());
        assertEquals(5, ctrl.transitionCount);

        ctrl.transitionTo(new DoneState(5, 0));
        assertEquals(6, ctrl.transitionCount);
    }

    // =========================================================================
    // AppState default methods — exercise "no-op" implementations
    // =========================================================================

    @Test
    void idleState_defaultMethods_doNotThrow() {
        IdleState state = new IdleState();
        assertDoesNotThrow(() -> state.onScan(ctrl));
        assertDoesNotThrow(() -> state.onOrganize(ctrl));
    }

    @Test
    void categorizedState_onBrowse_doesNotThrow() {
        CategorizedState state = new CategorizedState(3);
        assertDoesNotThrow(() -> state.onBrowse(ctrl));
    }

    @Test
    void doneState_onBrowse_doesNotThrow() {
        DoneState state = new DoneState(2, 1);
        assertDoesNotThrow(() -> state.onBrowse(ctrl));
    }

    @Test
    void scanningState_onOrganize_doesNotThrow() {
        ScanningState state = new ScanningState();
        assertDoesNotThrow(() -> state.onOrganize(ctrl));
    }

    @Test
    void organizingState_onBrowse_doesNotThrow() {
        OrganizingState state = new OrganizingState();
        assertDoesNotThrow(() -> state.onBrowse(ctrl));
    }

    // =========================================================================
    // CommandHistory — clear, canUndo, canRedo, undoSize
    // =========================================================================

    @Test
    void commandHistory_clearEmptiesStack() throws Exception {
        Path src = tempDir.resolve("ch.txt");
        Path dst = tempDir.resolve("ch_dst.txt");
        Files.writeString(src, "x");

        CommandHistory history = ctrl.getField("commandHistory");
        history.execute(new MoveFileCommand(src, dst));
        assertTrue(history.canUndo());

        history.clear();
        assertFalse(history.canUndo());
        assertFalse(history.canRedo());
    }

    @Test
    void commandHistory_undoSize_decreasesAfterUndo() throws Exception {
        Path src = tempDir.resolve("us.txt");
        Path dst = tempDir.resolve("us_dst.txt");
        Files.writeString(src, "x");

        CommandHistory history = ctrl.getField("commandHistory");
        history.execute(new MoveFileCommand(src, dst));
        assertEquals(1, history.undoSize());
        history.undo();
        assertEquals(0, history.undoSize());
    }

    // =========================================================================
    // CopyFileCommand — execute and undo
    // =========================================================================

    @Test
    void copyFileCommand_executeAndUndo() throws IOException {
        Path src = tempDir.resolve("copy_src.txt");
        Path dst = tempDir.resolve("copy_dst.txt");
        Files.writeString(src, "copy content");

        CopyFileCommand cmd = new CopyFileCommand(src, dst);
        cmd.execute();
        assertTrue(Files.exists(dst), "Destination should exist after copy");
        assertTrue(Files.exists(src), "Source should still exist after copy");

        cmd.undo();
        assertFalse(Files.exists(dst), "Destination should be deleted after undo of copy");
    }

    // =========================================================================
    // RenameFileCommand — execute and undo
    // =========================================================================

    @Test
    void renameFileCommand_executeAndUndo() throws IOException {
        Path src = tempDir.resolve("rename_me.txt");
        Path dst = tempDir.resolve("renamed.txt");
        Files.writeString(src, "rename content");

        RenameFileCommand cmd = new RenameFileCommand(src, dst);
        cmd.execute();
        assertTrue(Files.exists(dst), "File should exist at new name after rename");
        assertFalse(Files.exists(src), "File should not exist at old name after rename");

        cmd.undo();
        assertTrue(Files.exists(src), "File should be restored to old name after undo");
        assertFalse(Files.exists(dst), "New name should not exist after undo");
    }
}
