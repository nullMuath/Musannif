package org.app.musannif.model.category;

import org.app.musannif.model.ScannedFile;

import java.time.Instant;
import java.util.Optional;

/**
 * Chain of Responsibility — rejects files whose {@code lastModified} timestamp
 * falls outside the configured range.
 *
 * {@code
 * Instant jan2024 = Instant.parse("2024-01-01T00:00:00Z");
 * new ExtensionHandler("jpg", "png")
 *     .setNext(new SizeHandler(20 * 1024 * 1024L))
 *     .setNext(new DateHandler(jan2024, null));    // only files from 2024 onwards
 * }
 */
public class DateHandler extends FileHandler {

    /** Inclusive lower bound; {@code null} means no lower bound. */
    private final Instant after;
    /** Inclusive upper bound; {@code null} means no upper bound. */
    private final Instant before;

    /**
     * @param after  only pass files modified at or after this instant
     *               ({@code null} for no lower bound)
     * @param before only pass files modified at or before this instant
     *               ({@code null} for no upper bound)
     */
    public DateHandler(Instant after, Instant before) {
        this.after  = after;
        this.before = before;
    }

    /** Convenience: lower bound only. */
    public DateHandler(Instant after) {
        this(after, null);
    }

    @Override
    public Optional<ScannedFile> handle(ScannedFile file) {
        Instant ts = file.lastModified();
        if (after  != null && ts.isBefore(after))  return Optional.empty();
        if (before != null && ts.isAfter(before))  return Optional.empty();
        return passToNext(file);
    }
}
