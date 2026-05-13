package org.app.musannif.model.category;

import java.util.Objects;

/**
 * Decorator pattern — abstract base for all {@link FileCategory} decorators.
 *
 * Wraps a delegate category and forwards {@link #getName()} unchanged.
 * Subclasses override {@link #accepts(String)} to add their filter on top.
 *
 * Usage example
 * {@code
 * FileCategory filtered = new SizeFilterDecorator(
 *     new DateFilterDecorator(new Categories.Images(), afterDate),
 *     maxSizeBytes
 * );
 * categorizer.register(filtered);
 * }
 */
public abstract class FileCategoryDecorator implements FileCategory {

    protected final FileCategory delegate;

    protected FileCategoryDecorator(FileCategory delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    /** Delegates the name to the wrapped category unchanged. */
    @Override
    public String getName() {
        return delegate.getName();
    }
}
