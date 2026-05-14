package org.app.musannif.model.category;

import org.app.musannif.model.ScannedFile;

import java.util.Optional;

/**
 * Chain of Responsibility pattern — abstract handler in a file-filter pipeline.
 *
 * Each concrete handler either accepts a file (returns it wrapped in an
 * {@link Optional}), rejects it (returns {@link Optional#empty()}), or passes
 * it to the next handler in the chain.
 *
 * Building a chain
 * {@code
 * FileHandler chain = new ExtensionHandler(Set.of("pdf", "docx"))
 *     .setNext(new SizeHandler(10 * 1024 * 1024L))
 *     .setNext(new DateHandler(Instant.parse("2024-01-01T00:00:00Z"), null));
 *
 * Optional<ScannedFile> result = chain.handle(file);
 * }
 */
public abstract class FileHandler {

    private FileHandler next;

    /**
     * Appends {@code next} at the end of this handler's chain.
     *
     * @param next the handler to invoke when this one passes
     * @return {@code next} (for fluent chaining)
     */
    public FileHandler setNext(FileHandler next) {
        // Walk to the end of the current chain before attaching
        FileHandler tail = this;
        while (tail.next != null) {
            tail = tail.next;
        }
        tail.next = next;
        return next;   // return next so callers can chain: a.setNext(b).setNext(c)
    }

    /**
     * Processes {@code file}.  Implementations should either:
     *
     *   Return {@link Optional#empty()} to reject the file.
     *   Return {@code passToNext(file)} to let the next handler decide.
     *   Return {@link Optional#of(Object)} to accept the (possibly modified) file.
     *
     */
    public abstract Optional<ScannedFile> handle(ScannedFile file);

    /**
     * Forwards {@code file} to the next handler, or accepts it if this is
     * the last link in the chain.
     */
    protected Optional<ScannedFile> passToNext(ScannedFile file) {
        if (next == null) return Optional.of(file);   // end of chain → accept
        return next.handle(file);
    }
}
