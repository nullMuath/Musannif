package org.app.musannif.model.history;

import org.app.musannif.model.OrganizationMemento;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class SnapshotManager {

    private static final String SNAPSHOT_DIR = "musannif-snapshots";
    private static final DateTimeFormatter FILENAME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ssX")
                    .withZone(ZoneId.of("Z"));

    private SnapshotManager() {}

    private static Path getSnapshotDir() {
        Path dir = Path.of(SNAPSHOT_DIR);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            System.err.println("SnapshotManager: failed to create dir – " + e.getMessage());
        }
        return dir;
    }

    static String filenameFor(Instant timestamp) {
        return FILENAME_FMT.format(timestamp) + ".snap";
    }

    private static Path pathFor(Instant timestamp) {
        return getSnapshotDir().resolve(filenameFor(timestamp));
    }

    // -------------------------------------------------------------------------
    //  Save
    // -------------------------------------------------------------------------

    public static void save(Instant timestamp, Path sourceFolder, String mode,
                            int filesMoved, int filesSkipped,
                            OrganizationMemento memento) throws IOException {
        Path file = pathFor(timestamp);
        List<String> lines = new ArrayList<>();
        lines.add("@timestamp=" + timestamp.toString());
        lines.add("@sourceFolder=" + sourceFolder.toAbsolutePath().normalize().toString());
        lines.add("@mode=" + mode);
        lines.add("@filesMoved=" + filesMoved);
        lines.add("@filesSkipped=" + filesSkipped);
        lines.add("--");
        for (Map.Entry<Path, Path> entry : memento.getDestinationToSource().entrySet()) {
            lines.add(sanitizePath(entry.getKey()) + " -> " + sanitizePath(entry.getValue()));
        }
        Files.write(file, lines);
    }

    // -------------------------------------------------------------------------
    //  Load all (populate OperationHistory from disk snapshots)
    // -------------------------------------------------------------------------

    public static void loadAll() {
        Path dir = getSnapshotDir();
        if (!Files.exists(dir)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.snap")) {
            for (Path file : stream) {
                OperationRecord record = parseMetadata(file);
                if (record != null) {
                    OperationHistory.getInstance().add(
                            record.timestamp(), record.sourceFolder(), record.mode(),
                            record.filesMoved(), record.filesSkipped());
                }
            }
        } catch (IOException e) {
            System.err.println("SnapshotManager: failed to load – " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    //  Load a single memento (for restore)
    // -------------------------------------------------------------------------

    public static OrganizationMemento loadMemento(OperationRecord record) throws IOException {
        Path file = pathFor(record.timestamp());
        if (!Files.exists(file)) return null;
        List<String> lines = Files.readAllLines(file);
        Map<Path, Path> map = new LinkedHashMap<>();
        boolean inMappings = false;
        for (String line : lines) {
            if (line.equals("--")) { inMappings = true; continue; }
            if (!inMappings) continue;
            if (line.isBlank()) continue;
            int sep = line.lastIndexOf(" -> ");
            if (sep < 0) continue;
            Path dest = Path.of(line.substring(0, sep));
            Path src  = Path.of(line.substring(sep + 4));
            map.put(dest, src);
        }
        return OrganizationMemento.fromMap(map);
    }

    // -------------------------------------------------------------------------
    //  Load file mappings (for detail popup)
    // -------------------------------------------------------------------------

    public static List<String[]> loadFileMappings(OperationRecord record) throws IOException {
        Path file = pathFor(record.timestamp());
        if (!Files.exists(file)) return List.of();
        List<String> lines = Files.readAllLines(file);
        List<String[]> result = new ArrayList<>();
        boolean inMappings = false;
        for (String line : lines) {
            if (line.equals("--")) { inMappings = true; continue; }
            if (!inMappings || line.isBlank()) continue;
            int sep = line.lastIndexOf(" -> ");
            if (sep < 0) continue;
            result.add(new String[]{line.substring(0, sep), line.substring(sep + 4)});
        }
        return result;
    }

    // -------------------------------------------------------------------------
    //  Restorable check
    // -------------------------------------------------------------------------

    /**
     * Returns true if this operation can still be restored
     * (snapshot file exists AND at least one destination file still exists).
     */
    public static boolean isRestorable(OperationRecord record) {
        try {
            Path file = pathFor(record.timestamp());
            if (!Files.exists(file)) return false;
            List<String> lines = Files.readAllLines(file);
            boolean inMappings = false;
            int checked = 0;
            for (String line : lines) {
                if (line.equals("--")) { inMappings = true; continue; }
                if (!inMappings || line.isBlank()) continue;
                int sep = line.lastIndexOf(" -> ");
                if (sep < 0) continue;
                if (Files.exists(Path.of(line.substring(0, sep)))) return true;
                if (++checked >= 5) break;
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    //  Delete one / clear all
    // -------------------------------------------------------------------------

    public static void delete(OperationRecord record) throws IOException {
        Path file = pathFor(record.timestamp());
        Files.deleteIfExists(file);
    }

    public static void clearAll() {
        Path dir = getSnapshotDir();
        if (!Files.exists(dir)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.snap")) {
            for (Path file : stream) {
                Files.delete(file);
            }
        } catch (IOException e) {
            System.err.println("SnapshotManager: failed to clear – " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    //  Helpers
    // -------------------------------------------------------------------------

    private static String sanitizePath(Path p) {
        return p.toAbsolutePath().normalize().toString();
    }

    private static OperationRecord parseMetadata(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            Instant ts = null;
            Path folder = null;
            String mode = null;
            int moved = 0, skipped = 0;
            for (String line : lines) {
                if (!line.startsWith("@")) break;
                if (line.startsWith("@timestamp=")) ts = Instant.parse(line.substring(11));
                else if (line.startsWith("@sourceFolder=")) folder = Path.of(line.substring(14));
                else if (line.startsWith("@mode=")) mode = line.substring(6);
                else if (line.startsWith("@filesMoved=")) moved = Integer.parseInt(line.substring(12));
                else if (line.startsWith("@filesSkipped=")) skipped = Integer.parseInt(line.substring(14));
            }
            if (ts == null || folder == null || mode == null) {
                System.err.println("SnapshotManager: skipping malformed " + file);
                return null;
            }
            return new OperationRecord(ts, folder, mode, moved, skipped);
        } catch (IOException e) {
            System.err.println("SnapshotManager: error reading " + file + " – " + e.getMessage());
            return null;
        }
    }
}
