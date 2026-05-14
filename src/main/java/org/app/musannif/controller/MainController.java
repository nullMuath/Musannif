package org.app.musannif.controller;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.app.musannif.model.*;
import org.app.musannif.model.category.*;
import org.app.musannif.model.command.CommandHistory;
import org.app.musannif.model.state.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.event.ActionEvent;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Main controller for the Musannif JavaFX application.
 *
 * Design patterns wired here:
 *   State   — currentState delegates all button enable/disable decisions.
 *   Command — a single CommandHistory is shared with FileOrganizerFacade.
 *   Memento — after organizing, handleUndoAll() calls OrganizationMemento.restore().
 */
public class MainController {

    // -------------------------------------------------------------------------
    //  State pattern
    // -------------------------------------------------------------------------

    private AppState currentState;

    public void transitionTo(AppState newState) {
        this.currentState = newState;
        newState.onEnter(this);
    }

    public void setBtnScanDisabled(boolean disabled)  { btnScan.setDisable(disabled); }
    public void setBtnApplyDisabled(boolean disabled) { btnApply.setDisable(disabled); }
    public void setStatus(String text)                { lblStatus.setText(text); }

    // -------------------------------------------------------------------------
    //  Model
    // -------------------------------------------------------------------------

    private ObservableList<ScannedFile> scannedFiles = FXCollections.observableArrayList();
    private Path selectedFolder;

    /**
     * Shared CommandHistory — passed into every FileOrganizerFacade instance
     * so undo/redo in this controller always targets the right stack.
     */
    private final CommandHistory commandHistory = new CommandHistory();

    /**
     * Reference to the organizer from the most recent facade build,
     * so we can reach getLastMemento() after organizing.
     */
    private FileOrganizer lastOrganizer = null;

    // -------------------------------------------------------------------------
    //  FXML controls
    // -------------------------------------------------------------------------

    @FXML private TextField  txtFolderPath;
    @FXML private Button     btnBrowse;
    @FXML private Button     btnScan;
    @FXML private Button     btnApply;
    @FXML private Label      lblStatus;
    @FXML private Button     btnGenerateTestFiles;
    @FXML private Button     btnHistory;
    @FXML private HBox       titleBar;
    @FXML private Button     btnMinimize;
    @FXML private Button     btnMaximize;
    @FXML private Button     btnClose;
    @FXML private ImageView  sidebarLogo;
    @FXML private TableView<ScannedFile>           fileTable;
    @FXML private TableColumn<ScannedFile, String> colName;
    @FXML private TableColumn<ScannedFile, String> colExt;
    @FXML private TableColumn<ScannedFile, String> colSize;
    @FXML private TableColumn<ScannedFile, String> colModified;
    @FXML private RadioButton rbType;
    @FXML private RadioButton rbIcon;
    @FXML private RadioButton rbDate;
    @FXML private RadioButton rbExt;
    @FXML private Label       lblEmptyState;
    @FXML private VBox        previewPanel;
    @FXML private Button      btnClosePreview;
    @FXML private Button      btnTogglePreview;
    @FXML private Label       lblPreviewMethod;
    @FXML private Label       lblDestPath;
    @FXML private TreeView<String> treePreview;
    @FXML private Label       lblPreviewSummary;
    @FXML private Label       lblTotalSize;
    @FXML private ProgressBar scanProgress;
    @FXML private HBox        statusBar;

    private double xOffset = 0;
    private double yOffset = 0;

    // -------------------------------------------------------------------------
    //  Initialization
    // -------------------------------------------------------------------------

    @FXML
    private void initialize() {
        try {
            sidebarLogo.setImage(new Image(
                    MainController.class.getResourceAsStream("/org/app/musannif/icons/app-icon.png")));
        } catch (Exception e) {
            System.err.println("Failed to load sidebar logo: " + e.getMessage());
        }

        fileTable.setItems(scannedFiles);
        fileTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        colName.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().path().getFileName().toString()));
        colExt.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().extension()));
        colSize.setCellValueFactory(cd ->
                new SimpleStringProperty(helperMethods.formatFileSize(cd.getValue().sizeBytes())));
        colModified.setCellValueFactory(cd ->
                new SimpleStringProperty(helperMethods.formatDateTime(cd.getValue().lastModified())));

        setupTitleBarDrag();
        Logger.getLogger().info("Application Started");
        transitionTo(new IdleState());
    }

    // -------------------------------------------------------------------------
    //  Event handlers
    // -------------------------------------------------------------------------

    @FXML
    private void handleBrowse(ActionEvent event) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Folder to Organize");
        chooser.setInitialDirectory(new File(System.getProperty("user.home") + "/.musannif-test"));
        Logger.getLogger().info("Prompt user to select folder");

        Stage stage = (Stage) btnBrowse.getScene().getWindow();
        File chosen = chooser.showDialog(stage);
        if (chosen != null) {
            selectedFolder = chosen.toPath();
            txtFolderPath.setText(chosen.getAbsolutePath());
            scannedFiles.clear();
            Logger.getLogger().info("Folder Selected: " + selectedFolder);
            currentState.onBrowse(this);
        }
    }

    @FXML
    private void handleScan(ActionEvent event) {
        if (selectedFolder == null) {
            setStatus("Please select a folder first.");
            return;
        }
        scannedFiles.clear();
        currentState.onScan(this);
        Logger.getLogger().info("Scanning folder: " + selectedFolder);

        Task<List<ScannedFile>> scanTask = new Task<>() {
            @Override
            protected List<ScannedFile> call() throws Exception {
                FileScanner scanner = new FileScanner.Builder()
                        .skipHidden(true).maxDepth(1).build();
                return scanner.scan(selectedFolder);
            }
        };

        scanTask.setOnSucceeded(e -> {
            scannedFiles.addAll(scanTask.getValue());
            Logger.getLogger().info("Scan complete: " + scannedFiles.size() + " files found");
            transitionTo(new CategorizedState(scannedFiles.size()));
        });
        scanTask.setOnFailed(e -> {
            Logger.getLogger().info("Scan failed: " + scanTask.getException().getMessage());
            setStatus("Scan failed: " + scanTask.getException().getMessage());
            transitionTo(new FolderSelectedState());
        });
        new Thread(scanTask).start();
    }

    @FXML
    private void handleApply(ActionEvent event) throws IOException {
        if (selectedFolder == null || scannedFiles.isEmpty()) {
            setStatus("Nothing to organize — scan a folder first.");
            return;
        }
        currentState.onOrganize(this);
        Logger.getLogger().info("Organizing files in: " + selectedFolder);

        // Snapshot the radio-button selection on the FX thread before the task runs
        final boolean useDate = rbDate != null && rbDate.isSelected();
        final boolean useExt  = rbExt  != null && rbExt.isSelected();
        final boolean useIcon = rbIcon != null && rbIcon.isSelected();

        Task<FileOrganizer.OrganizationResult> organizeTask = new Task<>() {
            @Override
            protected FileOrganizer.OrganizationResult call() throws Exception {

                FileOrganizerFacade.Builder builder = new FileOrganizerFacade.Builder()
                        .skipHidden(true)
                        .maxDepth(1)
                        .commandHistory(commandHistory);   // ← shared history

                if (useDate) {
                    // By Date — group files by last-modified month
                    builder.withCategorizer(
                            new DateFileCategorizer.Builder()
                                    .period(new Periods.ByMonth())
                                    .build()
                    );
                    Logger.getLogger().info("Organize mode: By Date");
                } else if (useExt) {
                    // By Extension — each unique extension becomes its own folder
                    ExtensionFileCategorizer.Builder extBuilder = new ExtensionFileCategorizer.Builder();
                    scannedFiles.stream()
                            .map(ScannedFile::extension)
                            .filter(ext -> !ext.isBlank())
                            .distinct()
                            .forEach(ext -> extBuilder.register(new SingleExtensionCategory(ext)));
                    builder.withCategorizer(extBuilder.build());
                    Logger.getLogger().info("Organize mode: By Extension");
                } else {
                    // Default (rbType selected or nothing selected): By File Type
                    builder.withDefaultCategories();
                    Logger.getLogger().info("Organize mode: By File Type");
                }

                FileOrganizerFacade facade = builder.build();
                lastOrganizer = facade.getOrganizer();   // ← save for undo/memento
                return facade.organize(selectedFolder, selectedFolder);
            }
        };

        organizeTask.setOnSucceeded(e -> {
            FileOrganizer.OrganizationResult result = organizeTask.getValue();
            if (useIcon) helperMethods.addFolderIcons(selectedFolder);
            scannedFiles.clear();
            Logger.getLogger().info("Organization complete: "
                    + result.movedFiles() + " moved, " + result.skippedFiles() + " skipped");
            transitionTo(new DoneState(result.movedFiles(), result.skippedFiles()));
        });
        organizeTask.setOnFailed(e -> {
            Logger.getLogger().info("Organization failed: " + organizeTask.getException().getMessage());
            setStatus("Organization failed: " + organizeTask.getException().getMessage());
            transitionTo(new CategorizedState(scannedFiles.size()));
        });

        Desktop.getDesktop().open(selectedFolder.toFile());
        new Thread(organizeTask).start();
    }

    /** Undo the last individual file move (Command pattern). */
    @FXML
    private void handleUndo(ActionEvent event) {
        if (!commandHistory.canUndo()) { setStatus("Nothing to undo."); return; }
        try {
            commandHistory.undo();
            setStatus("Undo successful. (" + commandHistory.undoSize() + " remaining)");
            Logger.getLogger().info("Undo performed");
        } catch (IOException ex) {
            setStatus("Undo failed: " + ex.getMessage());
            Logger.getLogger().info("Undo failed: " + ex.getMessage());
        }
    }

    /** Redo the last undone file move (Command pattern). */
    @FXML
    private void handleRedo(ActionEvent event) {
        if (!commandHistory.canRedo()) { setStatus("Nothing to redo."); return; }
        try {
            commandHistory.redo();
            setStatus("Redo successful.");
            Logger.getLogger().info("Redo performed");
        } catch (IOException ex) {
            setStatus("Redo failed: " + ex.getMessage());
            Logger.getLogger().info("Redo failed: " + ex.getMessage());
        }
    }

    /** Undo the entire last organize operation (Memento pattern). */
    @FXML
    private void handleUndoAll(ActionEvent event) {
        if (lastOrganizer == null) { setStatus("No organize operation to undo."); return; }
        OrganizationMemento memento = lastOrganizer.getLastMemento();
        if (memento == null) { setStatus("No organize operation to undo."); return; }
        try {
            memento.restore();
            commandHistory.clear();   // stack is stale after bulk restore
            setStatus("All " + memento.size() + " file moves undone.");
            Logger.getLogger().info("Memento restore: " + memento.size() + " files restored");
        } catch (IOException ex) {
            setStatus("Undo-all failed: " + ex.getMessage());
            Logger.getLogger().info("Memento restore failed: " + ex.getMessage());
        }
    }

    // Show preview tree of what will happen before organizing
    @FXML
    private void handlePreview(ActionEvent event) {
        if (scannedFiles.isEmpty()) {
            setStatus("Scan a folder first to preview.");
            return;
        }

        final boolean useDate = rbDate != null && rbDate.isSelected();
        final boolean useExt  = rbExt  != null && rbExt.isSelected();

        FileCategorizer categorizer;
        String modeLabel;

        if (useDate) {
            categorizer = new DateFileCategorizer.Builder().period(new Periods.ByMonth()).build();
            modeLabel = "By Date";
        } else if (useExt) {
            ExtensionFileCategorizer.Builder eb = new ExtensionFileCategorizer.Builder();
            scannedFiles.stream()
                    .map(ScannedFile::extension)
                    .filter(ext -> !ext.isBlank())
                    .distinct()
                    .forEach(ext -> eb.register(new SingleExtensionCategory(ext)));
            categorizer = eb.build();
            modeLabel = "By Extension";
        } else {
            categorizer = new ExtensionFileCategorizer.Builder()
                    .register(new Categories.Documents())
                    .register(new Categories.Images())
                    .register(new Categories.Videos())
                    .register(new Categories.Audio())
                    .register(new Categories.Archives())
                    .build();
            modeLabel = "By File Type";
        }

        Map<String, List<ScannedFile>> categorized = categorizer.categorize(scannedFiles);

        // Build TreeView
        TreeItem<String> root = new TreeItem<>(selectedFolder.getFileName().toString());
        root.setExpanded(true);
        int totalFiles = 0;
        int otherFiles = 0;

        for (Map.Entry<String, List<ScannedFile>> entry : categorized.entrySet()) {
            List<ScannedFile> files = entry.getValue();
            if (files.isEmpty()) continue;
            TreeItem<String> folderNode = new TreeItem<>(entry.getKey() + "  (" + files.size() + ")");
            for (ScannedFile f : files) {
                folderNode.getChildren().add(new TreeItem<>(f.path().getFileName().toString()));
            }
            root.getChildren().add(folderNode);
            totalFiles += files.size();
            if (entry.getKey().equals(ExtensionFileCategorizer.FALLBACK_CATEGORY)) {
                otherFiles += files.size();
            }
        }

        // Wire into the existing FXML preview panel controls
        if (treePreview != null) {
            treePreview.setRoot(root);
            if (lblPreviewMethod != null) lblPreviewMethod.setText("Mode: " + modeLabel);
            if (lblPreviewSummary != null)
                lblPreviewSummary.setText(totalFiles + " files total, " + otherFiles + " uncategorized");
            if (previewPanel != null) previewPanel.setVisible(true);
        } else {
            // Fallback: show in an alert dialog
            TreeView<String> tree = new TreeView<>(root);
            tree.setPrefHeight(350);
            Label summary = new Label(totalFiles + " files total — " + otherFiles + " will go to Other");

            VBox content = new VBox(8, new Label("Mode: " + modeLabel), tree, summary);
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Preview");
            alert.setHeaderText("Proposed folder structure");
            alert.getDialogPane().setContent(content);
            alert.getDialogPane().setPrefWidth(500);
            alert.showAndWait();
        }
    }

    @FXML private void handleOrganize(ActionEvent event) { System.out.println("Organize clicked"); }
    @FXML private void handleHistory(ActionEvent event)  { System.out.println("History clicked"); }
    @FXML private void handleSettings(ActionEvent event) { System.out.println("Settings clicked"); }

    @FXML
    private void handleInfo(ActionEvent event) {
        Hyperlink authorLink1 = new Hyperlink("@nullMuath");
        authorLink1.setOnAction(e -> openURL("https://github.com/nullMuath"));
        Hyperlink authorLink2 = new Hyperlink("@MeCaveman");
        authorLink2.setOnAction(e -> openURL("https://github.com/MeCaveman"));
        Hyperlink repoLink = new Hyperlink("github.com/cpit252-spring-26-IT2/project-musannif");
        repoLink.setOnAction(e -> openURL("https://github.com/cpit252-spring-26-IT2/project-musannif"));

        VBox contentBox = new VBox(8);
        try {
            ImageView logo = new ImageView(new Image(
                    MainController.class.getResourceAsStream("/org/app/musannif/icons/app-icon.svg")));
            logo.setFitHeight(80); logo.setFitWidth(80); logo.setPreserveRatio(true);
            HBox logoContainer = new HBox(logo);
            logoContainer.setAlignment(Pos.CENTER);
            logoContainer.setPrefHeight(100);
            contentBox.getChildren().add(logoContainer);
        } catch (Exception ignored) {}

        HBox authors = new HBox(2, authorLink1, new Label("&"), authorLink2);
        authors.setAlignment(Pos.CENTER);
        Label devLabel = new Label("Developed by");
        devLabel.setMaxWidth(Double.MAX_VALUE);
        devLabel.setAlignment(Pos.CENTER);

        contentBox.getChildren().addAll(
                new Label("a javafx desktop app that organizes and sorts your files."),
                new Label("Version 0.2"), devLabel, authors,
                new Separator(), new Label("Project Repository:"), repoLink);

        Alert alert = new Alert(Alert.AlertType.NONE);
        alert.setTitle("About Musannif");
        alert.setHeaderText("Musannif - مصنف");
        alert.getDialogPane().setContent(contentBox);
        alert.getButtonTypes().add(ButtonType.OK);
        alert.showAndWait();
    }

    @FXML
    private void handleGenerateTestFiles(ActionEvent event) throws IOException, InterruptedException {
        Logger.getLogger().info("Generate Test Files Button Clicked");
        TestFilesGenerator.generate();
        setStatus("Files Generated!");
        Desktop.getDesktop().open(new File(System.getProperty("user.home") + "/.musannif-test"));
    }

    @FXML private void handleMinimize(ActionEvent event) {
        ((Stage) btnMinimize.getScene().getWindow()).setIconified(true);
    }
    @FXML private void handleMaximize(ActionEvent event) {
        Stage s = (Stage) btnMaximize.getScene().getWindow();
        s.setMaximized(!s.isMaximized());
    }
    @FXML private void handleClose(ActionEvent event) {
        ((Stage) btnClose.getScene().getWindow()).close();
    }

    // -------------------------------------------------------------------------

    private void setupTitleBarDrag() {
        titleBar.setOnMousePressed(e -> { xOffset = e.getSceneX(); yOffset = e.getSceneY(); });
        titleBar.setOnMouseDragged(e -> {
            Stage stage = (Stage) titleBar.getScene().getWindow();
            stage.setX(e.getScreenX() - xOffset);
            stage.setY(e.getScreenY() - yOffset);
        });
    }

    private void openURL(String url) {
        try { Desktop.getDesktop().browse(new URI(url)); }
        catch (Exception e) { e.printStackTrace(); }
    }
}
