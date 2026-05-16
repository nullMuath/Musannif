package org.app.musannif.model.core.state;

import org.app.musannif.controller.MainController;

/**
 * Done state — organization finished.  The user can scan a new folder (browse)
 * or re-scan the same one.  Apply is disabled until a fresh scan is run.
 */
public class DoneState implements AppState {

    private final int movedFiles;
    private final int skippedFiles;

    public DoneState(int movedFiles, int skippedFiles) {
        this.movedFiles = movedFiles;
        this.skippedFiles = skippedFiles;
    }

    @Override
    public void onEnter(MainController ctx) {
        ctx.setBtnPreviewDisabled(true);
        ctx.setBtnApplyDisabled(true);
        ctx.setBtnRefreshDisabled(true);
        ctx.setStatus("✓ Done — " + movedFiles + " files moved, " + skippedFiles + " skipped.");
        ctx.markSuccess();
    }
}
