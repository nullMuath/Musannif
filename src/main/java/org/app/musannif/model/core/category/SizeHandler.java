package org.app.musannif.model.core.category;

import org.app.musannif.model.ScannedFile;

import java.util.Optional;

/**
 * Chain of Responsibility — rejects files whose size exceeds {@code maxSizeBytes}.
 *
 * {@code
 * new ExtensionHandler("pdf", "docx")
 *     .setNext(new SizeHandler(10 * 1024 * 1024L));   // max 10 MB
 * }
 */
public class SizeHandler extends FileHandler {

    private final long maxSizeBytes;

    /**
     * @param maxSizeBytes maximum allowed file size in bytes (inclusive)
     */
    public SizeHandler(long maxSizeBytes) {
        if (maxSizeBytes < 0) throw new IllegalArgumentException("maxSizeBytes must be >= 0");
        this.maxSizeBytes = maxSizeBytes;
    }

    @Override
    public Optional<ScannedFile> handle(ScannedFile file) {
        if (file.sizeBytes() <= maxSizeBytes) {
            return passToNext(file);
        }
        return Optional.empty();   // file too large → reject
    }
}
