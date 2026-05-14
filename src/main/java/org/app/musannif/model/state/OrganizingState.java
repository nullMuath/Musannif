package org.app.musannif.model.state;

import org.app.musannif.controller.MainController;

/**
 * Organizing state — the background organize task is running.
 * All action buttons are disabled.
 */
public class OrganizingState implements AppState {

    @Override
    public void onEnter(MainController ctx) {
        ctx.setBtnPreviewDisabled(true);
        ctx.setBtnApplyDisabled(true);
        ctx.setBtnRefreshDisabled(true);
        ctx.setStatus("Organizing…");
    }
}
