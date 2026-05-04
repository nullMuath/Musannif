package org.app.musannif.model.category;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 *  {@link DatePeriod} implementations.
 *
 * Each inner class is intentionally thin: just a name and a label formula.
 * All ISO-8601 labels are zero-padded so folders sort chronologically in any
 * file manager without extra tooling.
 *
 * Adding a new granularity
 * Add a new {@code static class} here that implements {@link DatePeriod}.
 * That is the only change required — {@link DateFileCategorizer} accepts any
 * {@link DatePeriod} and needs no modification.
 *
 * Usage
 * {@code
 * DateFileCategorizer categorizer = new DateFileCategorizer.Builder()
 *         .period(new Periods.ByMonth())
 *         .build();
 *
 * Map<String, List<ScannedFile>> result = categorizer.categorize(files);
 * // Keys look like: "2025-04", "2024-11", …
 * }
 */
public final class Periods {

    private Periods() { /* utility class — not instantiable */ }

    /**
     * Groups files by the exact calendar day of their last-modified timestamp.
     *
     * <p>Label format: {@code YYYY-MM-DD}  (e.g. {@code 2025-04-21})
     */
    public static class ByDay implements DatePeriod {

        @Override
        public String getName() { return "Day"; }

        @Override
        public String label(Instant instant) {
            ZonedDateTime zdt = instant.atZone(ZoneId.systemDefault());
            return String.format("%04d-%02d-%02d",
                    zdt.getYear(),
                    zdt.getMonthValue(),
                    zdt.getDayOfMonth());
        }
    }

    /**
     * Groups files by the calendar month of their last-modified timestamp.
     *
     * <p>Label format: {@code YYYY-MM}  (e.g. {@code 2025-04})
     */
    public static class ByMonth implements DatePeriod {

        @Override
        public String getName() { return "Month"; }


        @Override
        public String label(Instant instant) {
            ZonedDateTime zdt = instant.atZone(ZoneId.systemDefault());
            return String.format("%04d-%02d",
                    zdt.getYear(),
                    zdt.getMonthValue());
        }
    }

    /**
     * Groups files by the calendar year of their last-modified timestamp.
     *
     * <p>Label format: {@code YYYY}  (e.g. {@code 2025})
     */
    public static class ByYear implements DatePeriod {

        @Override
        public String getName() { return "Year"; }

        @Override
        public String label(Instant instant) {
            ZonedDateTime zdt = instant.atZone(ZoneId.systemDefault());
            return String.format("%04d", zdt.getYear());
        }
    }
}
