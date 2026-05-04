package org.app.musannif.model.category;

import org.app.musannif.model.ScannedFile;

import java.util.*;
import java.util.stream.Stream;

/**
 * Categorizes a collection of {@link ScannedFile}s into a map keyed by a
 * date-period label (= the destination folder name).
 *
 * Design notes
 *       A single {@link DatePeriod} is used per categorizer instance; date
 *       grouping is mutually exclusive so no priority list is needed.
 *       The returned map uses {@link LinkedHashMap} with keys sorted
 *       chronologically, so callers get a predictable, time-ordered view.
 *
 * Usage example
 * {@code
 * DateFileCategorizer categorizer = new DateFileCategorizer.Builder()
 *         .period(new Periods.ByMonth())
 *         .build();
 *
 * List<ScannedFile> files = scanner.scan(somePath);
 * Map<String, List<ScannedFile>> result = categorizer.categorize(files);
 * // Keys: "2024-11", "2025-01", "2025-04", …
 *
 * // Plug straight into FileOrganizer — same signature as ExtensionFileCategorizer:
 * organizer.applyCategorization(result, targetDirectory);
 * }
 */
public class DateFileCategorizer {

    private final DatePeriod period;

    private DateFileCategorizer(Builder builder) {
        this.period = builder.period;
    }

    // -------------------------------------------------------------------------
    //  Core API
    // -------------------------------------------------------------------------

    /**
     * Categorizes the given files by their last-modified timestamp.
     *
     * @param files any collection of {@link ScannedFile} objects
     * @return a map from ISO date label → files last modified in that period,
     *         sorted chronologically by label (lexicographic order on ISO
     *         strings equals time order)
     */
    public Map<String, List<ScannedFile>> categorize(Collection<ScannedFile> files) {
        // Use a TreeMap so keys (ISO date strings) are always in chronological order.
        Map<String, List<ScannedFile>> result = new TreeMap<>();

        for (ScannedFile file : files) {
            String label = period.label(file.lastModified());
            result.computeIfAbsent(label, k -> new ArrayList<>()).add(file);
        }

        return result;
    }
    

    // -------------------------------------------------------------------------
    //  Builder
    // -------------------------------------------------------------------------

    public static class Builder {

        private DatePeriod period;

        /**
         * Sets the time granularity used to derive folder names.
         *
         * @param period any {@link DatePeriod} implementation, e.g.
         *               {@code new Periods.ByDay()}
         */
        public Builder period(DatePeriod period) {
            Objects.requireNonNull(period, "period must not be null");
            this.period = period;
            return this;
        }

        public DateFileCategorizer build() {
            Objects.requireNonNull(period, "A DatePeriod must be set before building");
            return new DateFileCategorizer(this);
        }
    }
}
