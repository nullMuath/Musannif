package org.app.musannif.model.core.category;

import org.app.musannif.model.ScannedFile;

/**
 * Decorator pattern — adds a maximum-file-size filter to any {@link FileCategory}.
 *
 * A file is accepted only when the delegate already accepts its extension
 * <em>and</em> its size (in bytes) does not exceed {@code maxSizeBytes}.
 *
 * Usage example
 * {@code
 * // Only accept documents smaller than 10 MB
 * FileCategory smallDocs = new SizeFilterDecorator(new Categories.Documents(), 10 * 1024 * 1024L);
 * }
 *
 * NOTE: {@link FileCategory#accepts(String)} does not receive
 * file size — size filtering is applied at the categorizer level via
 * {@link #accepts(ScannedFile)}.  The extension-only overload delegates to the
 * wrapped category without size filtering so that the class can still be used
 * in {@link ExtensionFileCategorizer} while the richer overload is used when a
 * {@link ScannedFile} is available.
 */
public class SizeFilterDecorator extends FileCategoryDecorator {

    private final long maxSizeBytes;

    /**
     * @param delegate     the category to wrap
     * @param maxSizeBytes maximum file size in bytes (inclusive); files larger
     *                     than this value are rejected
     */
    public SizeFilterDecorator(FileCategory delegate, long maxSizeBytes) {
        super(delegate);
        if (maxSizeBytes < 0) throw new IllegalArgumentException("maxSizeBytes must be >= 0");
        this.maxSizeBytes = maxSizeBytes;
    }

    /**
     * Extension-only check (used by {@link ExtensionFileCategorizer}).
     * Delegates to the wrapped category; size is unknown at this level.
     */
    @Override
    public boolean accepts(String extension) {
        return delegate.accepts(extension);
    }

    /**
     * Full check including file size — use this when a {@link ScannedFile}
     * is available (e.g. in a custom categorization pipeline).
     *
     * @param file the file to test
     * @return {@code true} if the delegate accepts the extension <em>and</em>
     *         the file is within the size limit
     */
    public boolean accepts(ScannedFile file) {
        return delegate.accepts(file.extension()) && file.sizeBytes() <= maxSizeBytes;
    }

    public long getMaxSizeBytes() { return maxSizeBytes; }
}
