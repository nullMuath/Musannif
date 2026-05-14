package org.app.musannif.model.state;

import org.app.musannif.controller.MainController;

/**
 * Idle state — the app has just launched; no folder has been selected yet.
 * Scan and Apply are disabled.  Browsing uses the default transition to
 * {@link FolderSelectedState} (inherited from {@link AppState}).
 */
public class IdleState implements AppState {

    @Override
    public void onEnter(MainController ctx) {
        ctx.setBtnScanDisabled(true);
        ctx.setBtnApplyDisabled(true);
        ctx.setBtnTogglePreviewDisabled(true);
        ctx.setStatus("Select a folder to begin.");
    }
}
