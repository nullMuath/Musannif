package org.app.musannif.model.core.category;

import org.app.musannif.model.ScannedFile;

import java.util.*;

/**
 * Chain of Responsibility — filters files by their extension.
 *
 * If the file's extension is in the allowed set it passes to the next
 * handler; otherwise it is rejected ({@link Optional#empty()}).
 *
 * {@code
 * new ExtensionHandler(Set.of("pdf", "docx", "xlsx"))
 *     .setNext(new SizeHandler(5 * 1024 * 1024L));
 * }
 */
public class ExtensionHandler extends FileHandler {

    private final Set<String> allowedExtensions;

    /**
     * @param allowedExtensions lowercase extensions without a leading dot
     *                          (e.g. {@code "pdf"}, {@code "mp4"})
     */
    public ExtensionHandler(Collection<String> allowedExtensions) {
        Objects.requireNonNull(allowedExtensions, "allowedExtensions must not be null");
        this.allowedExtensions = Collections.unmodifiableSet(
                new HashSet<>(allowedExtensions));
    }

    /** Varargs convenience constructor. */
    public ExtensionHandler(String... extensions) {
        this(Arrays.asList(extensions));
    }

    @Override
    public Optional<ScannedFile> handle(ScannedFile file) {
        if (allowedExtensions.isEmpty() || allowedExtensions.contains(file.extension())) {
            return passToNext(file);
        }
        return Optional.empty();   // extension not allowed → reject
    }
}
