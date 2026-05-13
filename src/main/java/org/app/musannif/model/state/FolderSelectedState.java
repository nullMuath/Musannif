package org.app.musannif.model.state;

import org.app.musannif.controller.MainController;

/**
 * FolderSelected state — a folder has been chosen; the user can now scan it.
 */
public class FolderSelectedState implements AppState {

    @Override
    public void onEnter(MainController ctx) {
        ctx.setBtnScanDisabled(false);
        ctx.setBtnApplyDisabled(true);
        ctx.setStatus("Folder selected. Click Scan Folder to continue.");
    }

    @Override
    public void onScan(MainController ctx) {
        ctx.transitionTo(new ScanningState());
    }
}
