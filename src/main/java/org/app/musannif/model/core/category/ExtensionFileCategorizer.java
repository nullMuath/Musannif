package org.app.musannif.model.core.category;

import org.app.musannif.model.ScannedFile;
import org.app.musannif.util.Logger;

import java.util.*;
import java.util.stream.Stream;

/**
 * Categorizes a collection of ScannedFiles into a map keyed by category name.
 *
 * Design notes
 *   Categories are checked in registration order; first match wins.
 *   Files that no category claims land in {@value #FALLBACK_CATEGORY}.
 *   Decorator-aware: if a registered category is a {@link SizeFilterDecorator}
 *   or {@link DateFilterDecorator}, {@code accepts(ScannedFile)} is called so
 *   that size and date filters are actually enforced during categorization.
 */
public class ExtensionFileCategorizer implements FileCategorizer {

    public static final String FALLBACK_CATEGORY = "Other";

    private final List<FileCategory> categories;

    private ExtensionFileCategorizer(Builder builder) {
        this.categories = Collections.unmodifiableList(new ArrayList<>(builder.categories));
    }

    @Override
    public Map<String, List<ScannedFile>> categorize(Collection<ScannedFile> files) {
        Map<String, List<ScannedFile>> result = new LinkedHashMap<>();
        for (FileCategory cat : categories) {
            result.put(cat.getName(), new ArrayList<>());
        }
        result.put(FALLBACK_CATEGORY, new ArrayList<>());

        for (ScannedFile file : files) {
            String targetCategory = resolve(file);   // ← full ScannedFile, not just extension
            result.get(targetCategory).add(file);
        }

        Logger.getLogger().info("Categorization by type: grouped into " + categories.size() + " categories");
        return result;
    }

    public Map<String, List<ScannedFile>> categorize(Stream<ScannedFile> files) {
        return categorize(files.toList());
    }

    /**
     * Resolves the category for a file using the full ScannedFile so that
     * decorator filters (size, date) are applied when present.
     */
    private String resolve(ScannedFile file) {
        for (FileCategory cat : categories) {
            if (accepts(cat, file)) return cat.getName();
        }
        return FALLBACK_CATEGORY;
    }

    /**
     * Dispatches to the right accepts() overload.
     * SizeFilterDecorator and DateFilterDecorator expose accepts(ScannedFile);
     * everything else uses accepts(String extension).
     */
    private static boolean accepts(FileCategory cat, ScannedFile file) {
        if (cat instanceof SizeFilterDecorator sfd) return sfd.accepts(file);
        if (cat instanceof DateFilterDecorator  dfd) return dfd.accepts(file);
        return cat.accepts(file.extension());
    }

    // -------------------------------------------------------------------------

    public static class Builder {

        private final List<FileCategory> categories = new ArrayList<>();

        public Builder register(FileCategory category) {
            Objects.requireNonNull(category, "category must not be null");
            categories.add(category);
            return this;
        }

        public ExtensionFileCategorizer build() {
            return new ExtensionFileCategorizer(this);
        }
    }
}
