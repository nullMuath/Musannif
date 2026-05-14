package org.app.musannif.model.history;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Immutable value object that records a single completed organize operation.
 *
 * @param timestamp   when the organize run finished
 * @param sourceFolder the folder that was organized
 * @param mode        the categorization mode used (e.g. "By File Type", "By Date")
 * @param filesMoved  number of files successfully moved
 * @param filesSkipped number of files that were skipped
 */
public record OperationRecord(
        Instant timestamp,
        Path    sourceFolder,
        String  mode,
        int     filesMoved,
        int     filesSkipped
) {
    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm:ss")
                             .withZone(ZoneId.systemDefault());

    /** Human-readable timestamp for display in the history dialog. */
    public String formattedTimestamp() {
        return DISPLAY_FORMAT.format(timestamp);
    }

    /** One-line summary for a list cell. */
    public String summary() {
        return formattedTimestamp()
                + "   |   " + sourceFolder.getFileName()
                + "   |   " + mode
                + "   |   " + filesMoved + " moved"
                + (filesSkipped > 0 ? ", " + filesSkipped + " skipped" : "");
    }
}
