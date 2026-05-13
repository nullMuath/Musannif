package org.app.musannif.model.category;

import org.app.musannif.model.ScannedFile;

import java.time.Instant;
import java.util.Objects;

/**
 * Decorator pattern — adds a last-modified date filter to any {@link FileCategory}.
 *
 * A file is accepted only when the delegate already accepts its extension
 * and its {@code lastModified} timestamp is within the configured
 * range.
 *
 * Usage example
 * {@code
 * Instant cutoff = Instant.parse("2024-01-01T00:00:00Z");
 *
 * // Only images modified after 2024
 * FileCategory recentImages = new DateFilterDecorator(new Categories.Images(), cutoff, null);
 *
 * // Only documents modified before 2024
 * FileCategory oldDocs = new DateFilterDecorator(new Categories.Documents(), null, cutoff);
 * }
 */
public class DateFilterDecorator extends FileCategoryDecorator {

    /** Inclusive lower bound; {@code null} means no lower bound. */
    private final Instant after;
    /** Inclusive upper bound; {@code null} means no upper bound. */
    private final Instant before;

    /**
     * @param delegate the category to wrap
     * @param after    only accept files modified at or after this instant
     *                 (pass {@code null} for no lower bound)
     * @param before   only accept files modified at or before this instant
     *                 (pass {@code null} for no upper bound)
     */
    public DateFilterDecorator(FileCategory delegate, Instant after, Instant before) {
        super(delegate);
        this.after  = after;
        this.before = before;
    }

    /** Convenience constructor: lower bound only (files modified after {@code after}). */
    public DateFilterDecorator(FileCategory delegate, Instant after) {
        this(delegate, Objects.requireNonNull(after), null);
    }

    /** Extension-only check — delegates to the wrapped category (date unknown). */
    @Override
    public boolean accepts(String extension) {
        return delegate.accepts(extension);
    }

    /**
     * Full check including the last-modified timestamp.
     *
     * @param file the file to test
     * @return {@code true} if the delegate accepts the extension <em>and</em>
     *         the file's timestamp falls within the configured range
     */
    public boolean accepts(ScannedFile file) {
        if (!delegate.accepts(file.extension())) return false;
        Instant ts = file.lastModified();
        if (after  != null && ts.isBefore(after))  return false;
        if (before != null && ts.isAfter(before))  return false;
        return true;
    }

    public Instant getAfter()  { return after; }
    public Instant getBefore() { return before; }
}
