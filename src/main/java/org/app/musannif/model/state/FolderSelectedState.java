package org.app.musannif.model.state;

import org.app.musannif.controller.MainController;

/**
 * FolderSelected state — a folder has been chosen; the user can now scan it.
 */
public class FolderSelectedState implements AppState {

    @Override
    public void onEnter(MainController ctx) {
        ctx.setBtnPreviewDisabled(false);
        ctx.setBtnApplyDisabled(true);
        ctx.setBtnRefreshDisabled(false);
        ctx.setStatus("Folder selected.");
    }
}
