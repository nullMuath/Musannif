# ─────────────────────────────────────────────────────────────
#  Musannif — JavaFX Desktop App in Docker (VNC access)
#  Works on Windows, macOS, and Linux — no display needed on host
# ─────────────────────────────────────────────────────────────

FROM eclipse-temurin:21-jdk-jammy

# ── System dependencies ────────────────────────────────────────
RUN apt-get update && apt-get install -y --no-install-recommends \
    x11vnc xvfb openbox novnc websockify \
    libgtk-3-0 libglib2.0-0 libxtst6 libxxf86vm1 libgl1 libx11-dev \
    maven curl git python3 \
    && rm -rf /var/lib/apt/lists/*

# ── Clone the app ──────────────────────────────────────────────
WORKDIR /app
RUN git clone --depth=1 https://github.com/cpit252-spring-26-IT2/project-musannif.git .

# ── Patch 1: Fix TestFilesGenerator
#    - Remove Windows-only DosFileAttributeView
#    - Write test files to /home/user/files (the mounted my-files folder)
RUN cat > src/main/java/org/app/musannif/util/TestFilesGenerator.java << 'EOF'
package org.app.musannif.util;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class TestFilesGenerator {
    private static final List<String> fileTypes = List.of(
            "report.pdf", "final_report.pdf",
            "notes.docx", "meeting_notes.docx",
            "readme.txt", "changelog.txt",
            "data.xlsx", "summary_data.xlsx",
            "slides.pptx", "presentation.pptx",
            "photo.png", "profile_photo.png",
            "banner.jpg", "header_banner.jpg",
            "icon.gif", "loading_icon.gif",
            "logo.svg", "dark_logo.svg",
            "thumbnail.webp", "preview_thumbnail.webp",
            "clip.mp4", "intro_clip.mp4",
            "movie.mkv", "backup_movie.mkv",
            "recording.avi", "screen_recording.avi",
            "song.mp3", "acoustic_song.mp3",
            "voice.wav", "voice_memo.wav",
            "backup.zip", "project_backup.zip",
            "assets.rar", "old_assets.rar",
            "bundle.7z", "release_bundle.7z");

    public static void generate() {
        Path testDir = Path.of("/home/user/files/musannif-test-files");
        try {
            if (Files.exists(testDir)) {
                deleteRecursively(testDir);
            }
            Files.createDirectories(testDir);
            for (int i = 0; i < fileTypes.size(); i++) {
                String file = fileTypes.get(i);
                Path createdFile = Files.createFile(testDir.resolve(file));
                Instant modifiedTime = Instant.now().plus(i * 3, ChronoUnit.DAYS);
                Files.setLastModifiedTime(createdFile, FileTime.from(modifiedTime));
                Logger.getLogger().info("File: [" + file + "] has been created in " + testDir);
            }
        } catch (IOException e) {
            Logger.getLogger().info("Failed To Generate Test Files: " + e.getMessage());
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var entries = Files.list(path)) {
                for (Path entry : entries.toList()) {
                    deleteRecursively(entry);
                }
            }
        }
        Files.delete(path);
    }
}
EOF

# ── Patch 2: Fix MainController
#    - Remove Desktop.open() crash
#    - Fix Browse initial directory (was pointing to non-existent .musannif-test)
#    - Auto-populate folder path on startup; auto-scan if test files already exist
RUN python3 - << 'PYEOF'
with open("src/main/java/org/app/musannif/controller/MainController.java", "r") as f:
    content = f.read()

# 2a: Remove Desktop.open after generate (crashes on Linux)
old = '        Desktop.getDesktop().open(new File(System.getProperty("user.home") + File.separator + ".musannif-test"));'
new = '        Logger.getLogger().info("Test files generated at /home/user/files/musannif-test-files");'
content = content.replace(old, new)

# 2b: Fix Browse initial directory — was pointing to ~/.musannif-test which doesn't exist,
#     causing DirectoryChooser to silently do nothing
old = '        chooser.setInitialDirectory(new File(System.getProperty("user.home") + File.separator + ".musannif-test"));'
new = '        java.io.File initDir = new java.io.File("/home/user/files"); if (initDir.exists()) chooser.setInitialDirectory(initDir);'
content = content.replace(old, new)

# 2c: Auto-populate folder field on startup and auto-scan if test files exist
old = '    private void initialize() {'
new = '''    private void initialize() {
        // Docker: auto-load test files folder on startup if it exists
        javafx.application.Platform.runLater(() -> {
            java.io.File defaultDir = new java.io.File("/home/user/files/musannif-test-files");
            java.io.File fallbackDir = new java.io.File("/home/user/files");
            if (defaultDir.exists()) {
                selectedFolder = defaultDir.toPath();
                txtFolderPath.setText(defaultDir.getAbsolutePath());
                btnOpenFolder.setVisible(true);
                doneOverlay.setManaged(false);
                doneOverlay.setVisible(false);
                tablePreviewContainer.setManaged(true);
                tablePreviewContainer.setVisible(true);
                currentState.onBrowse(this);
                doScan();
            } else {
                txtFolderPath.setText(fallbackDir.getAbsolutePath());
            }
        });'''
content = content.replace(old, new, 1)

with open("src/main/java/org/app/musannif/controller/MainController.java", "w") as f:
    f.write(content)
print("Patched MainController.java OK")
PYEOF

# ── Patch 3: Fix window size — bump VNC screen so 1400x900 app fits ──
RUN python3 - << 'PYEOF'
with open("src/main/java/org/app/musannif/MainApplication.java", "r") as f:
    content = f.read()
old = 'Scene scene = new Scene(fxmlLoader.load(), 1400, 900);'
new = 'Scene scene = new Scene(fxmlLoader.load(), 1400, 900); stage.setResizable(true);'
content = content.replace(old, new)
with open("src/main/java/org/app/musannif/MainApplication.java", "w") as f:
    f.write(content)
print("Patched MainApplication.java OK")
PYEOF

# ── Build the app ──────────────────────────────────────────────
RUN mvn package -DskipTests -q

# ── Startup script ────────────────────────────────────────────
COPY start.sh /start.sh
RUN chmod +x /start.sh

EXPOSE 5900 6080

ENTRYPOINT ["/start.sh"]
