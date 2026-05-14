package org.app.musannif.model.state;

import org.app.musannif.controller.MainController;

/**
 * Scanning state — a background scan task is running.
 * All action buttons are disabled to prevent concurrent operations.
 * {@code onScan} is a no-op (buttons are already disabled, but guards against
 * programmatic calls while scanning is in progress).
 */
public class ScanningState implements AppState {

    @Override
    public void onEnter(MainController ctx) {
        ctx.setBtnPreviewDisabled(true);
        ctx.setBtnApplyDisabled(true);
        ctx.setBtnRefreshDisabled(true);
        ctx.setStatus("Scanning…");
    }
}
