package org.app.musannif.model.core.state;

import org.app.musannif.controller.MainController;

/**
 * State pattern — represents one phase of the application lifecycle.
 *
 * Each concrete state implementation decides which UI controls are
 * enabled and what actions are legal.  {@link MainController} holds the
 * current state and delegates UI-enable logic to it, eliminating the
 * scattered {@code button.setDisable(true/false)} calls.
 *
 * Lifecycle
 *
 *   Idle ──scan──▶ Scanning ──done──▶ Categorized ──organize──▶ Organizing ──done──▶ Done
 *    ▲                                     │                                           │
 *    └─────────────────────────────────────┴───────────────────────────────────────────┘
 *                                      (reset / browse new folder)
 *
 */
public interface AppState {

    /**
     * Called once when the controller transitions *into* this state.
     * Implementations should update button enable/disable and status label here.
     *
     * @param ctx the controller that owns this state machine
     */
    void onEnter(MainController ctx);

    /**
     * Triggered when the user clicks "Scan".
     * The default implementation does nothing (invalid action for that state).
     */
    default void onScan(MainController ctx) { /* not valid in this state */ }

    /**
     * Triggered when the user clicks "Apply / Organize".
     * The default implementation does nothing (invalid action for that state).
     */
    default void onOrganize(MainController ctx) { /* not valid in this state */ }

    /**
     * Triggered when the user browses a new folder (a folder has already been chosen
     * by the time this is called).  The default transitions to
     * {@link FolderSelectedState} so Scan is immediately re-enabled.
     * Override only where a different follow-on state is needed.
     */
    default void onBrowse(MainController ctx) {
        ctx.transitionTo(new FolderSelectedState());
    }
}
