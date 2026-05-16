package org.app.musannif.model;
import org.app.musannif.util.Logger;

import org.app.musannif.model.core.category.Categories;
import org.app.musannif.model.core.category.ExtensionFileCategorizer;
import org.app.musannif.model.core.category.FileCategory;
import org.app.musannif.model.core.category.FileCategorizer;
import org.app.musannif.model.core.command.CommandHistory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Facade — hides the scan → categorize → organize pipeline behind a single
 * {@link #organize} call.
 *
 * The internal {@link FileOrganizer} is exposed via {@link #getOrganizer()} so
 * callers can access undo/redo and the Memento snapshot after organizing.
 */
public class FileOrganizerFacade {

    private final FileScanner scanner;
    private final FileCategorizer categorizer;
    private final FileOrganizer organizer;

    private FileOrganizerFacade(Builder builder) {
        this.scanner = new FileScanner.Builder()
                .skipHidden(builder.skipHidden)
                .maxDepth(builder.maxDepth)
                .build();

        if (builder.customCategorizer != null) {
            this.categorizer = builder.customCategorizer;
        } else {
            ExtensionFileCategorizer.Builder cb = new ExtensionFileCategorizer.Builder();
            for (FileCategory category : builder.categories) {
                cb.register(category);
            }
            this.categorizer = cb.build();
        }

        // Use the caller-supplied CommandHistory so the controller shares the
        // same undo/redo stack, or fall back to a private one.
        this.organizer = (builder.commandHistory != null)
                ? new FileOrganizer(builder.commandHistory)
                : new FileOrganizer();
    }

    public FileOrganizer.OrganizationResult organize(Path sourceDirectory, Path targetDirectory)
            throws IOException {
        Objects.requireNonNull(sourceDirectory, "sourceDirectory must not be null");
        Objects.requireNonNull(targetDirectory, "targetDirectory must not be null");

        Logger.getLogger().info("Organization pipeline started");
        List<ScannedFile> files = scanner.scan(sourceDirectory);
        Map<String, List<ScannedFile>> categorized = categorizer.categorize(files);
        return organizer.applyCategorization(categorized, targetDirectory);
    }

    /** Exposes the internal organizer so callers can reach getLastMemento(). */
    public FileOrganizer getOrganizer() { return organizer; }

    // -------------------------------------------------------------------------

    public static class Builder {

        private boolean skipHidden = false;
        private int maxDepth = -1;
        private final List<FileCategory> categories = new ArrayList<>();
        private FileCategorizer customCategorizer = null;
        private CommandHistory commandHistory = null;

        public Builder skipHidden(boolean skip)   { this.skipHidden = skip; return this; }

        public Builder maxDepth(int depth) {
            if (depth < -1) throw new IllegalArgumentException("maxDepth must be >= -1");
            this.maxDepth = depth;
            return this;
        }

        public Builder addCategory(FileCategory category) {
            Objects.requireNonNull(category, "category must not be null");
            this.categories.add(category);
            return this;
        }

        public Builder withCategorizer(FileCategorizer categorizer) {
            Objects.requireNonNull(categorizer, "categorizer must not be null");
            this.customCategorizer = categorizer;
            return this;
        }

        /** Shares an existing CommandHistory with the facade. */
        public Builder commandHistory(CommandHistory history) {
            this.commandHistory = history;
            return this;
        }

        public Builder withDefaultCategories() {
            this.categories.add(new Categories.Documents());
            this.categories.add(new Categories.Images());
            this.categories.add(new Categories.Videos());
            this.categories.add(new Categories.Audio());
            this.categories.add(new Categories.Archives());
            return this;
        }

        public FileOrganizerFacade build() { return new FileOrganizerFacade(this); }
    }
}
