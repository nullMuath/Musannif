package org.app.musannif.model.core.category;

/**
 * Concrete FileCategory that accepts exactly one file extension.
 * Used when organizing by extension — each unique extension gets its own folder.
 */
public class SingleExtensionCategory implements FileCategory {

    private final String extension;

    public SingleExtensionCategory(String extension) {
        this.extension = extension.toLowerCase();
    }

    @Override
    public String getName() {
        return extension.isBlank() ? "Other" : extension.toUpperCase();
    }

    @Override
    public boolean accepts(String ext) {
        return this.extension.equals(ext);
    }
}
