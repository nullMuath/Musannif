package org.app.musannif.model.core.state;

import org.app.musannif.controller.MainController;

/**
 * Categorized state — scan completed successfully; files are listed and the
 * user can now apply the organization.
 */
public class CategorizedState implements AppState {

    private final int fileCount;

    public CategorizedState(int fileCount) {
        this.fileCount = fileCount;
    }

    @Override
    public void onEnter(MainController ctx) {
        ctx.showTable();
        ctx.setBtnPreviewDisabled(false);
        ctx.setBtnApplyDisabled(false);
        ctx.setBtnRefreshDisabled(false);
        ctx.setStatus("Scan complete — " + fileCount + " files found.");
    }

    @Override
    public void onOrganize(MainController ctx) {
        ctx.transitionTo(new OrganizingState());
    }
}
