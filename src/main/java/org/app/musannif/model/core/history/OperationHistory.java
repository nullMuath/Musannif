package org.app.musannif.model.core.history;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Singleton that stores every completed organize operation for the current
 * application session.
 *
 * Usage
 * {@code
 * // After a successful organize:
 * OperationHistory.getInstance().add(
 *     Instant.now(), selectedFolder, "By File Type", movedFiles, skippedFiles);
 *
 * // In handleHistory():
 * List<OperationRecord> all = OperationHistory.getInstance().getAll();
 * }
 */
public class OperationHistory {

    private static final OperationHistory INSTANCE = new OperationHistory();

    private final List<OperationRecord> records = new ArrayList<>();

    private OperationHistory() {}

    public static OperationHistory getInstance() {
        return INSTANCE;
    }

    /**
     * Records a completed organize operation.
     *
     * @param timestamp    when the run finished (use {@link Instant#now()})
     * @param sourceFolder the folder that was organized
     * @param mode         categorization mode label ("By File Type", "By Date", "By Extension")
     * @param filesMoved   how many files were moved
     * @param filesSkipped how many files were skipped
     */
    public void add(Instant timestamp, Path sourceFolder, String mode,
                    int filesMoved, int filesSkipped) {
        records.add(new OperationRecord(timestamp, sourceFolder, mode, filesMoved, filesSkipped));
    }

    /** Returns an unmodifiable view of all recorded operations, oldest first. */
    public List<OperationRecord> getAll() {
        return Collections.unmodifiableList(records);
    }

    /** @return {@code true} if at least one operation has been recorded */
    public boolean isEmpty() {
        return records.isEmpty();
    }

    /** Clears all recorded history (useful for testing). */
    public void clear() {
        records.clear();
    }
}
