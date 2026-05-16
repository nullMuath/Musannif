package org.app.musannif.controller;

import org.app.musannif.model.*;
import org.app.musannif.model.core.command.CommandHistory;
import org.app.musannif.model.core.state.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MainController.
 *
 * Strategy: subclass MainController and override every FXML-bound method
 * (setBtnScanDisabled, setBtnApplyDisabled, setStatus, transitionTo) so they
 * record calls instead of touching JavaFX nodes.  Private @FXML fields stay
 * null — they are never reached because the overrides intercept all calls.
 *
 * For private event handlers (handleUndo, handleRedo, handleUndoAll,
 * handleScan, handleApply) we use reflection to invoke them directly,
 * injecting the necessary model state beforehand via the same reflection.
 *
 * Place this file at:
 *   src/test/java/org/app/musannif/controller/MainControllerTest.java
 */
class MainControllerTest {

    // -------------------------------------------------------------------------
    // Stub
    // -------------------------------------------------------------------------

    static class TestableController extends MainController {

        boolean scanDisabled;
        boolean applyDisabled;
        String  lastStatus;
        AppState lastTransition;
        int transitionCount;

        public void setBtnRefreshDisabled(boolean v) { scanDisabled  = v; }
        public void setBtnPreviewDisabled(boolean v) { /* preview — not tracked in this test */ }
        public void setBtnApplyDisabled(boolean v)   { applyDisabled = v; }
        public void setStatus(String s)              { lastStatus = s; }
        public void showTable()                      { /* no-op */ }
        public void markSuccess()                    { /* no-op */ }

        public void transitionTo(AppState next) {
            lastTransition = next;
            transitionCount++;
            // store field for transitionTo_updatesCurrentState test
            try {
                java.lang.reflect.Field f = MainController.class.getDeclaredField("currentState");
                f.setAccessible(true);
                f.set(this, next);
            } catch (Exception e) { /* ignore */ }
            next.onEnter(this);   // keep state machine ticking
        }

        // --- reflection helpers ---

        /** Read any declared field by name (walks up to MainController). */
        <T> T getField(String name) throws Exception {
            Class<?> cls = MainController.class;
            Field f = cls.getDeclaredField(name);
            f.setAccessible(true);
            //noinspection unchecked
            return (T) f.get(this);
        }

        /** Write any declared field by name. */
        void setField(String name, Object value) throws Exception {
            Field f = MainController.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(this, value);
        }

        /** Invoke a private void method with an ActionEvent-compatible null arg. */
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
    }

    // =========================================================================
    // transitionTo — state machine wiring
    // =========================================================================

    @Test
    void transitionTo_idleState_disablesBoth() {
        ctrl.transitionTo(new IdleState());
        assertTrue(ctrl.scanDisabled);
        assertTrue(ctrl.applyDisabled);
    }

    @Test
    void transitionTo_folderSelectedState_enablesScan() {
        ctrl.transitionTo(new FolderSelectedState());
        assertFalse(ctrl.scanDisabled);
        assertTrue(ctrl.applyDisabled);
    }

    @Test
    void transitionTo_categorizedState_enablesBoth() {
        ctrl.transitionTo(new CategorizedState(5));
        assertFalse(ctrl.scanDisabled);
        assertFalse(ctrl.applyDisabled);
        assertTrue(ctrl.lastStatus.contains("5"));
    }

    @Test
    void transitionTo_doneState_enablesScanDisablesApply() {
        ctrl.transitionTo(new DoneState(10, 2));
        assertFalse(ctrl.scanDisabled);
        assertTrue(ctrl.applyDisabled);
        assertTrue(ctrl.lastStatus.contains("10"));
    }

    @Test
    void transitionTo_organizingState_disablesBoth() {
        ctrl.transitionTo(new OrganizingState());
        assertTrue(ctrl.scanDisabled);
        assertTrue(ctrl.applyDisabled);
    }

    @Test
    void transitionTo_scanningState_disablesBoth() {
        ctrl.transitionTo(new ScanningState());
        assertTrue(ctrl.scanDisabled);
        assertTrue(ctrl.applyDisabled);
    }

    @Test
    void transitionTo_updatesCurrentState() throws Exception {
        ctrl.transitionTo(new IdleState());
        AppState stored = ctrl.getField("currentState");
        assertInstanceOf(IdleState.class, stored);

        ctrl.transitionTo(new FolderSelectedState());
        stored = ctrl.getField("currentState");
        assertInstanceOf(FolderSelectedState.class, stored);
    }

    // =========================================================================
    // handleUndo — via reflection
    // =========================================================================

    @Test
    void handleUndo_nothingToUndo_setsStatus() throws Exception {
        // commandHistory starts empty
        ctrl.invoke("handleUndo");
        assertTrue(ctrl.lastStatus.contains("Nothing to undo"),
                "Expected 'Nothing to undo' but got: " + ctrl.lastStatus);
    }

    @Test
    void handleUndo_withPendingUndo_undoesAndSetsStatus() throws Exception {
        // Push a real move onto the command history so canUndo() is true
        Path src = tempDir.resolve("a.txt");
        Path dst = tempDir.resolve("b.txt");
        Files.writeString(src, "hi");

        CommandHistory history = ctrl.getField("commandHistory");
        history.execute(new org.app.musannif.model.core.command.MoveFileCommand(src, dst));
        assertTrue(Files.exists(dst));

        ctrl.invoke("handleUndo");

        assertTrue(Files.exists(src), "File should be back at source after undo");
        assertNotNull(ctrl.lastStatus);
        assertFalse(ctrl.lastStatus.contains("failed"),
                "Status should not indicate failure: " + ctrl.lastStatus);
    }

    // =========================================================================
    // handleRedo — via reflection
    // =========================================================================

    @Test
    void handleRedo_nothingToRedo_setsStatus() throws Exception {
        ctrl.invoke("handleRedo");
        assertTrue(ctrl.lastStatus.contains("Nothing to redo"),
                "Expected 'Nothing to redo' but got: " + ctrl.lastStatus);
    }

    @Test
    void handleRedo_withPendingRedo_redoesAndSetsStatus() throws Exception {
        Path src = tempDir.resolve("r.txt");
        Path dst = tempDir.resolve("rd.txt");
        Files.writeString(src, "data");

        CommandHistory history = ctrl.getField("commandHistory");
        history.execute(new org.app.musannif.model.core.command.MoveFileCommand(src, dst));
        history.undo();
        assertTrue(Files.exists(src));

        ctrl.invoke("handleRedo");

        assertTrue(Files.exists(dst), "File should be at destination after redo");
        assertNotNull(ctrl.lastStatus);
        assertFalse(ctrl.lastStatus.contains("failed"),
                "Status should not indicate failure: " + ctrl.lastStatus);
    }

    // =========================================================================
    // handleUndoAll — via reflection
    // =========================================================================

    @Test
    void handleUndoAll_noOrganizer_setsStatus() throws Exception {
        // lastOrganizer is null by default
        ctrl.invoke("handleUndoAll");
        assertTrue(ctrl.lastStatus.contains("No organize operation"),
                "Expected 'No organize operation' but got: " + ctrl.lastStatus);
    }

    @Test
    void handleUndoAll_organizerWithNullMemento_setsStatus() throws Exception {
        // Set lastOrganizer to a fresh FileOrganizer (no organize run yet → memento is null)
        FileOrganizer organizer = new FileOrganizer();
        ctrl.setField("lastOrganizer", organizer);

        ctrl.invoke("handleUndoAll");
        assertTrue(ctrl.lastStatus.contains("No organize operation"),
                "Expected 'No organize operation' but got: " + ctrl.lastStatus);
    }

    @Test
    void handleUndoAll_withValidMemento_restoresFiles() throws Exception {
        // Run a real organize so the organizer has a valid memento
        Path src = Files.createDirectory(tempDir.resolve("src"));
        Path target = tempDir.resolve("target");
        Path file = src.resolve("doc.pdf");
        Files.writeString(file, "content");

        FileOrganizer organizer = new FileOrganizer();
        organizer.applyCategorization(
                java.util.Map.of("Docs", java.util.List.of(
                        new ScannedFile(file, "pdf", 7L, Instant.now()))),
                target);

        // File is now at target/Docs/doc.pdf
        assertTrue(Files.exists(target.resolve("Docs").resolve("doc.pdf")));

        ctrl.setField("lastOrganizer", organizer);
        CommandHistory history = ctrl.getField("commandHistory");
        // Sync the shared history: push the same command so undoSize > 0
        // (handleUndoAll uses memento.restore(), not commandHistory, so this
        // just exercises the clear() call)
        ctrl.invoke("handleUndoAll");

        // File should be restored to original location
        assertTrue(Files.exists(file), "File should be restored to src after undoAll");
        assertNotNull(ctrl.lastStatus);
        assertFalse(ctrl.lastStatus.toLowerCase().contains("failed"),
                "Status should not indicate failure: " + ctrl.lastStatus);
    }

    // =========================================================================
    // handleScan — null selectedFolder guard
    // =========================================================================

    @Test
    void handleScan_nullFolder_setsStatusAndReturns() throws Exception {
        // selectedFolder is null by default — should not throw, just set status
        ctrl.invoke("handleScan");
        assertNotNull(ctrl.lastStatus);
        assertTrue(ctrl.lastStatus.contains("folder"),
                "Expected folder-selection prompt but got: " + ctrl.lastStatus);
    }

    // =========================================================================
    // handleApply — null/empty guard
    // =========================================================================

    @Test
    void handleApply_nullFolder_setsStatus() throws Exception {
        ctrl.invoke("handleApply");
        assertNotNull(ctrl.lastStatus);
        assertTrue(ctrl.lastStatus.contains("Nothing to organize") ||
                        ctrl.lastStatus.contains("folder"),
                "Unexpected status: " + ctrl.lastStatus);
    }

    @Test
    void handleApply_folderSetButEmptyScannedFiles_setsStatus() throws Exception {
        ctrl.setField("selectedFolder", tempDir);
        // scannedFiles is an ObservableList initialised to empty in the controller
        ctrl.invoke("handleApply");
        assertTrue(ctrl.lastStatus.contains("Nothing to organize"),
                "Expected 'Nothing to organize' but got: " + ctrl.lastStatus);
    }

    // =========================================================================
    // handleOrganize / handleSettings — trivial stubs that just print
    // =========================================================================

    @Test
    void handleOrganize_doesNotThrow() {
        assertDoesNotThrow(() -> ctrl.invoke("handleOrganize"));
    }

    @Test
    void handleSettings_doesNotThrow() {
        assertDoesNotThrow(() -> ctrl.invoke("handleSettings"));
    }

    // =========================================================================
    // State transitions triggered by onBrowse / onScan / onOrganize
    // (exercised through the state objects calling back on the stub controller)
    // =========================================================================

    @Test
    void onBrowse_fromIdle_transitionsToFolderSelected() {
        ctrl.transitionTo(new IdleState());
        AppState idle = new IdleState();
        idle.onBrowse(ctrl);
        assertInstanceOf(FolderSelectedState.class, ctrl.lastTransition);
    }

    @Test
    void onScan_fromFolderSelected_transitionsToScanning() {
        ctrl.transitionTo(new FolderSelectedState());
        AppState fs = new FolderSelectedState();
        fs.onScan(ctrl);
        assertInstanceOf(ScanningState.class, ctrl.lastTransition);
    }

    @Test
    void onOrganize_fromCategorized_transitionsToOrganizing() {
        ctrl.transitionTo(new CategorizedState(3));
        AppState cat = new CategorizedState(3);
        cat.onOrganize(ctrl);
        assertInstanceOf(OrganizingState.class, ctrl.lastTransition);
    }
}
