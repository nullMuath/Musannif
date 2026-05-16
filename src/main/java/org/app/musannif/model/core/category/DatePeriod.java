package org.app.musannif.model.core.category;

import java.time.Instant;

/**
 * Strategy interface for grouping a file's last-modified timestamp into a
 * human-readable bucket name (= folder name).
 *
 * Contract
 *
 *   {@link #label(Instant)} must be deterministic — the same instant must
 *       always produce the same label.
 *   Labels must be valid folder-name fragments (no path separators).
 *   {@link #getName()} is a display/debug identifier for the period itself,
 *       e.g. {@code "Day"}, {@code "Month"}, {@code "Year"}.
 */

public interface DatePeriod {

    /**
     * A short, human-readable identifier for this granularity.
     * Used for logging and display — not used as a folder name.
     * Examples: {@code "Day"}, {@code "Month"}, {@code "Year"}.
     */
    String getName();

    /**
     * Converts a file's last-modified timestamp into a folder-name label.
     *
     * The returned string must be a valid folder-name fragment.
     * Use ISO-8601 ordering so folders sort chronologically in any file
     * manager (e.g. {@code "2025-04"} before {@code "2025-12"}).
     *
     * @param instant the file's last-modified time; never {@code null}
     * @return a non-null, non-blank folder label
     */
    String label(Instant instant);
}
