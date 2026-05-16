package org.app.musannif.model.core.state;

import org.app.musannif.controller.MainController;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for all AppState implementations.
 *
 * MainController is a JavaFX class and cannot be instantiated in a headless
 * test environment, so we use a plain anonymous stub that records the calls
 * made by each state's onEnter / onScan / onOrganize / onBrowse method.
 *
 * Place this file at:
 *   src/test/java/org/app/musannif/model/state/AppStateTest.java
 */
class AppStateTest {

    // -------------------------------------------------------------------------
    // Minimal stub — records every call without touching JavaFX
    // -------------------------------------------------------------------------

    static class StubController extends MainController {

        boolean scanDisabled;
        boolean applyDisabled;
        String  status;
        AppState lastTransition;

        // Override every method the states call
        public void setBtnScanDisabled(boolean v)  { scanDisabled  = v; }
        public void setBtnApplyDisabled(boolean v) { applyDisabled = v; }
        public void setStatus(String s)            { status = s; }
        public void transitionTo(AppState next)    { lastTransition = next; }
    }

    // =========================================================================
    // IdleState
    // =========================================================================

    @Test
    void idleState_onEnter_disablesBothButtons() {
        StubController ctx = new StubController();
        new IdleState().onEnter(ctx);

        assertTrue(ctx.scanDisabled);
        assertTrue(ctx.applyDisabled);
        assertNotNull(ctx.status);
        assertFalse(ctx.status.isBlank());
    }

    @Test
    void idleState_onBrowse_transitionsToFolderSelected() {
        StubController ctx = new StubController();
        IdleState state = new IdleState();
        state.onBrowse(ctx);   // default implementation in AppState

        assertNotNull(ctx.lastTransition);
        assertInstanceOf(FolderSelectedState.class, ctx.lastTransition);
    }

    @Test
    void idleState_onScan_doesNothing() {
        // default onScan is a no-op; must not throw
        StubController ctx = new StubController();
        assertDoesNotThrow(() -> new IdleState().onScan(ctx));
    }

    @Test
    void idleState_onOrganize_doesNothing() {
        StubController ctx = new StubController();
        assertDoesNotThrow(() -> new IdleState().onOrganize(ctx));
    }

    // =========================================================================
    // FolderSelectedState
    // =========================================================================

    @Test
    void folderSelectedState_onEnter_enablesScanDisablesApply() {
        StubController ctx = new StubController();
        new FolderSelectedState().onEnter(ctx);

        assertFalse(ctx.scanDisabled);
        assertTrue(ctx.applyDisabled);
        assertNotNull(ctx.status);
    }

    @Test
    void folderSelectedState_onScan_transitionsToScanning() {
        StubController ctx = new StubController();
        new FolderSelectedState().onScan(ctx);

        assertNotNull(ctx.lastTransition);
        assertInstanceOf(ScanningState.class, ctx.lastTransition);
    }

    @Test
    void folderSelectedState_onOrganize_doesNothing() {
        StubController ctx = new StubController();
        assertDoesNotThrow(() -> new FolderSelectedState().onOrganize(ctx));
    }

    @Test
    void folderSelectedState_onBrowse_transitionsToFolderSelected() {
        StubController ctx = new StubController();
        new FolderSelectedState().onBrowse(ctx);

        assertInstanceOf(FolderSelectedState.class, ctx.lastTransition);
    }

    // =========================================================================
    // ScanningState
    // =========================================================================

    @Test
    void scanningState_onEnter_disablesBothButtons() {
        StubController ctx = new StubController();
        new ScanningState().onEnter(ctx);

        assertTrue(ctx.scanDisabled);
        assertTrue(ctx.applyDisabled);
        assertNotNull(ctx.status);
    }

    @Test
    void scanningState_onScan_isNoOp() {
        // overridden to do nothing — must not throw or transition
        StubController ctx = new StubController();
        new ScanningState().onScan(ctx);

        assertNull(ctx.lastTransition);
    }

    @Test
    void scanningState_onOrganize_doesNothing() {
        StubController ctx = new StubController();
        assertDoesNotThrow(() -> new ScanningState().onOrganize(ctx));
    }

    // =========================================================================
    // CategorizedState
    // =========================================================================

    @Test
    void categorizedState_onEnter_enablesBothButtons_andShowsFileCount() {
        StubController ctx = new StubController();
        new CategorizedState(42).onEnter(ctx);

        assertFalse(ctx.scanDisabled);
        assertFalse(ctx.applyDisabled);
        assertTrue(ctx.status.contains("42"), "Status should contain file count");
    }

    @Test
    void categorizedState_onEnter_zeroFiles() {
        StubController ctx = new StubController();
        new CategorizedState(0).onEnter(ctx);
        assertTrue(ctx.status.contains("0"));
    }

    @Test
    void categorizedState_onOrganize_transitionsToOrganizing() {
        StubController ctx = new StubController();
        new CategorizedState(5).onOrganize(ctx);

        assertNotNull(ctx.lastTransition);
        assertInstanceOf(OrganizingState.class, ctx.lastTransition);
    }

    @Test
    void categorizedState_onBrowse_transitionsToFolderSelected() {
        StubController ctx = new StubController();
        new CategorizedState(3).onBrowse(ctx);

        assertInstanceOf(FolderSelectedState.class, ctx.lastTransition);
    }

    @Test
    void categorizedState_onScan_doesNothing() {
        // default onScan is no-op
        StubController ctx = new StubController();
        assertDoesNotThrow(() -> new CategorizedState(1).onScan(ctx));
    }

    // =========================================================================
    // OrganizingState
    // =========================================================================

    @Test
    void organizingState_onEnter_disablesBothButtons() {
        StubController ctx = new StubController();
        new OrganizingState().onEnter(ctx);

        assertTrue(ctx.scanDisabled);
        assertTrue(ctx.applyDisabled);
        assertNotNull(ctx.status);
    }

    @Test
    void organizingState_onScan_doesNothing() {
        StubController ctx = new StubController();
        assertDoesNotThrow(() -> new OrganizingState().onScan(ctx));
    }

    @Test
    void organizingState_onOrganize_doesNothing() {
        StubController ctx = new StubController();
        assertDoesNotThrow(() -> new OrganizingState().onOrganize(ctx));
    }

    @Test
    void organizingState_onBrowse_transitionsToFolderSelected() {
        StubController ctx = new StubController();
        new OrganizingState().onBrowse(ctx);
        assertInstanceOf(FolderSelectedState.class, ctx.lastTransition);
    }

    // =========================================================================
    // DoneState
    // =========================================================================

    @Test
    void doneState_onEnter_enablesScanDisablesApply_andShowsCounts() {
        StubController ctx = new StubController();
        new DoneState(10, 2).onEnter(ctx);

        assertFalse(ctx.scanDisabled);
        assertTrue(ctx.applyDisabled);
        assertTrue(ctx.status.contains("10"), "Status should contain movedFiles");
        assertTrue(ctx.status.contains("2"),  "Status should contain skippedFiles");
    }

    @Test
    void doneState_onEnter_zeroSkipped() {
        StubController ctx = new StubController();
        new DoneState(5, 0).onEnter(ctx);
        assertTrue(ctx.status.contains("5"));
        assertTrue(ctx.status.contains("0"));
    }

    @Test
    void doneState_onBrowse_transitionsToFolderSelected() {
        StubController ctx = new StubController();
        new DoneState(3, 0).onBrowse(ctx);
        assertInstanceOf(FolderSelectedState.class, ctx.lastTransition);
    }

    @Test
    void doneState_onScan_doesNothing() {
        StubController ctx = new StubController();
        assertDoesNotThrow(() -> new DoneState(1, 0).onScan(ctx));
    }

    @Test
    void doneState_onOrganize_doesNothing() {
        StubController ctx = new StubController();
        assertDoesNotThrow(() -> new DoneState(1, 0).onOrganize(ctx));
    }
}
