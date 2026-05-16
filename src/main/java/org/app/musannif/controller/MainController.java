package org.app.musannif.controller;
import org.app.musannif.util.Logger;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.app.musannif.model.*;
import org.app.musannif.model.core.category.*;
import org.app.musannif.model.core.command.CommandHistory;
import org.app.musannif.model.core.state.*;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.input.MouseEvent;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.scene.Scene;

import org.app.musannif.model.core.history.OperationHistory;
import org.app.musannif.model.core.history.OperationRecord;
import org.app.musannif.model.core.history.SnapshotManager;
import org.app.musannif.util.FileIconCache;
import org.app.musannif.util.helperMethods;
import org.app.musannif.util.TestFilesGenerator;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
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

    private static final Map<String, String> EXT_COLOR = Map.ofEntries(
        Map.entry("pdf",  "ext-red"),   Map.entry("exe",  "ext-red"),
        Map.entry("xlsx", "ext-green"), Map.entry("csv",  "ext-green"), Map.entry("json", "ext-green"),
        Map.entry("docx", "ext-blue"),  Map.entry("doc",  "ext-blue"),  Map.entry("png",  "ext-blue"),
        Map.entry("txt",  "ext-gray"),
        Map.entry("pptx", "ext-orange"),Map.entry("ppt",  "ext-orange"),Map.entry("jpg",  "ext-orange"), Map.entry("jpeg","ext-orange"),
        Map.entry("jar",  "ext-yellow"),Map.entry("zip",  "ext-yellow"),Map.entry("rar",  "ext-yellow"),
        Map.entry("mp4",  "ext-purple"),Map.entry("mkv",  "ext-purple"),Map.entry("mov",  "ext-purple"),
        Map.entry("svg",  "ext-teal"),  Map.entry("gif",  "ext-teal")
    );

    private static final Map<String, String> CAT_ICON = Map.of(
        "Documents",  "\u25C7",
        "Images",     "\u25CB",
        "Videos",     "\u25B6",
        "Audio",      "\u266A",
        "Archives",   "\u25A4",
        "Executables","\u2699"
    );

    private static final Map<String, String> CAT_DOT_COLOR = Map.of(
        "Documents",  "#60A5FA",
        "Images",     "#4ADE80",
        "Videos",     "#A78BFA",
        "Audio",      "#FB923C",
        "Archives",   "#FBBF24",
        "Executables","#F87171"
    );

    // -------------------------------------------------------------------------
    //  State pattern
    // -------------------------------------------------------------------------

    private AppState currentState;

    public void transitionTo(AppState newState) {
        this.currentState = newState;
        newState.onEnter(this);
    }

    public void setBtnPreviewDisabled(boolean disabled)  { btnPreview.setDisable(disabled); }
    public void setBtnApplyDisabled(boolean disabled) { btnApply.setDisable(disabled); }
    public void setBtnRefreshDisabled(boolean disabled) { btnRefresh.setDisable(disabled); }
    public void setStatus(String text)                { lblStatus.setText(text); }

    public void showTable() {
        doneOverlay.setManaged(false);
        doneOverlay.setVisible(false);
        tablePreviewContainer.setManaged(true);
        tablePreviewContainer.setVisible(true);
    }

    public void markSuccess() {
        doneOverlay.setManaged(true);
        doneOverlay.setVisible(true);
        tablePreviewContainer.setManaged(false);
        tablePreviewContainer.setVisible(false);
        doneOverlay.setOpacity(0);
        FadeTransition ft = new FadeTransition(javafx.util.Duration.millis(350), doneOverlay);
        ft.setFromValue(0); ft.setToValue(1);
        ft.setInterpolator(Interpolator.EASE_OUT);
        ScaleTransition st = new ScaleTransition(javafx.util.Duration.millis(500), doneOverlay);
        st.setFromX(0.85); st.setFromY(0.85);
        st.setToX(1.0); st.setToY(1.0);
        st.setInterpolator(Interpolator.EASE_OUT);
        ParallelTransition pt = new ParallelTransition(ft, st);
        pt.play();
    }

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
    @FXML private Button     btnRefresh;
    @FXML private Button     btnPreview;
    @FXML private Button     btnApply;
    @FXML private Label      lblStatus;
    @FXML private Button     btnGenerateTestFiles;
    @FXML private HBox       titleBar;
    @FXML private Button     btnMinimize;
    @FXML private Button     btnMaximize;
    @FXML private Button     btnClose;
    @FXML private ImageView  sidebarLogo;
    @FXML private TableView<ScannedFile>           fileTable;
    @FXML private TableColumn<ScannedFile, ScannedFile> colName;
    @FXML private TableColumn<ScannedFile, String> colExt;
    @FXML private TableColumn<ScannedFile, String> colSize;
    @FXML private TableColumn<ScannedFile, String> colModified;
    @FXML private RadioButton rbType;
    @FXML private RadioButton rbIcon;
    @FXML private RadioButton rbDate;
    @FXML private RadioButton rbExt;
    @FXML private Label       lblEmptyState;
    @FXML private HBox        tablePreviewContainer;
    @FXML private VBox        previewPanel;
    @FXML private Separator   previewSeparator;
    @FXML private Button      btnOpenFolder;
    @FXML private VBox        doneOverlay;
    @FXML private Label       lblDoneIcon;
    @FXML private Label       lblDoneText;
    @FXML private Label       lblDoneSummary;

    // History page
    @FXML private TableView<OperationRecord> historyTable;
    @FXML private TableColumn<OperationRecord, String> colHistTime;
    @FXML private TableColumn<OperationRecord, String> colHistFolder;
    @FXML private TableColumn<OperationRecord, String> colHistMode;
    @FXML private TableColumn<OperationRecord, String> colHistMoved;
    @FXML private TableColumn<OperationRecord, String> colHistSkipped;
    @FXML private Button btnRestoreOperation;

    // Sidebar navigation
    @FXML private HBox navOrganize;
    @FXML private HBox navHistory;
    @FXML private Label navOrganizeIcon;
    @FXML private Label navOrganizeLabel;
    @FXML private Label navHistoryIcon;
    @FXML private Label navHistoryLabel;

    // Page containers
    @FXML private VBox organizePage;
    @FXML private VBox historyPage;

    // Preview panel
    @FXML private TreeView<String> treePreview;
    @FXML private Label lblPreviewMethod;
    @FXML private Label lblPreviewSummary;
    @FXML private Label lblTotalSize;
    @FXML private Label lblDestPath;
    @FXML private FlowPane legendBar;

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

        colName.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()));
        colName.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(ScannedFile item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Image icon = FileIconCache.getIcon(item.path());
                    ImageView iv = new ImageView(icon);
                    Label name = new Label(item.path().getFileName().toString());
                    name.setStyle("-fx-text-fill:#E8EAF0;-fx-font-size:16px;");
                    HBox box = new HBox(8, iv, name);
                    box.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(box);
                    setText(null);
                }
            }
        });
        colExt.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().extension()));
        colSize.setCellValueFactory(cd ->
                new SimpleStringProperty(helperMethods.formatFileSize(cd.getValue().sizeBytes())));
        colModified.setCellValueFactory(cd ->
                new SimpleStringProperty(helperMethods.formatDateTime(cd.getValue().lastModified())));

        fileTable.setRowFactory(tv -> {
            TableRow<ScannedFile> row = new TableRow<>();
            ContextMenu ctx = new ContextMenu();
            javafx.scene.control.MenuItem openItem = new javafx.scene.control.MenuItem("Open in Explorer");
            openItem.setOnAction(e -> {
                ScannedFile sf = row.getItem();
                if (sf != null) {
                    try {
                        Desktop.getDesktop().open(sf.path().getParent().toFile());
                    } catch (Exception ex) {
                        setStatus("Failed to open file manager: " + ex.getMessage());
                    }
                }
            });
            ctx.getItems().add(openItem);
            row.setContextMenu(ctx);
            return row;
        });
 
        historyTable.setRowFactory(tv -> {
            TableRow<OperationRecord> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    handleShowDetails(row.getItem());
                }
            });
            row.itemProperty().addListener((obs, old, item) -> {
                if (item == null) return;
                boolean restorable = SnapshotManager.isRestorable(item);
                row.getStyleClass().removeAll("history-row-spent");
                if (!restorable) row.getStyleClass().add("history-row-spent");
            });
            return row;
        });

        setupTitleBarDrag();
        setupPreviewResize();

        // Auto-refresh preview when organize mode changes (only if panel already open)
        javafx.beans.value.ChangeListener<Boolean> modeListener = (obs, o, selected) -> {
            if (selected && previewPanel != null && previewPanel.isVisible() && !scannedFiles.isEmpty())
                rebuildPreview();
        };
        rbType.selectedProperty().addListener(modeListener);
        rbDate.selectedProperty().addListener(modeListener);
        rbExt.selectedProperty().addListener(modeListener);

        setupHistoryTable();
        SnapshotManager.loadAll();
        btnRestoreOperation.setDisable(true);
        historyTable.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            boolean restorable = selected != null && SnapshotManager.isRestorable(selected);
            btnRestoreOperation.setDisable(!restorable);
            if (selected != null && !restorable) Platform.runLater(() -> historyTable.getSelectionModel().clearSelection());
        });
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
        chooser.setInitialDirectory(new File(System.getProperty("user.home") + File.separator + ".musannif-test"));
        Logger.getLogger().info("Prompt user to select folder");

        Stage stage = (Stage) btnBrowse.getScene().getWindow();
        File chosen = chooser.showDialog(stage);
        if (chosen != null) {
            selectedFolder = chosen.toPath();
            txtFolderPath.setText(chosen.getAbsolutePath());
            scannedFiles.clear();
            btnOpenFolder.setVisible(true);
            doneOverlay.setManaged(false);
            doneOverlay.setVisible(false);
            tablePreviewContainer.setManaged(true);
            tablePreviewContainer.setVisible(true);
            Logger.getLogger().info("Folder Selected: " + selectedFolder);
            currentState.onBrowse(this);
            doScan();
        }
    }

    private void doScan() {
        if (selectedFolder == null) return;
        showTable();
        scannedFiles.clear();
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
            btnPreview.setDisable(false);
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
    private void handleRefresh(ActionEvent event) {
        doScan();
    }

    @FXML
    private void handleScan(ActionEvent event) {
        if (selectedFolder == null) {
            setStatus("Select a folder first.");
            return;
        }
        doScan();
    }

    @FXML
    private void handleApply(ActionEvent event) throws IOException {
        if (selectedFolder == null || scannedFiles.isEmpty()) {
            setStatus("Nothing to organize — scan a folder first.");
            return;
        }

        Stage confirmStage = new Stage();
        confirmStage.initStyle(StageStyle.TRANSPARENT);
        confirmStage.initModality(Modality.APPLICATION_MODAL);
        HBox titleBar = createDialogTitleBar("Confirm Organization", confirmStage);

        Label warningIcon = new Label("\u26A0");
        warningIcon.setStyle("-fx-text-fill:#FBBF24;-fx-font-size:40px;");
        Label caution = new Label("Caution");
        caution.setStyle("-fx-text-fill:#E8EAF0;-fx-font-size:16px;-fx-font-weight:bold;");
        Label body = new Label("This will move files into categorized folders.\nYou can undo this operation later from the History tab.");
        body.setStyle("-fx-text-fill:#9DA3BA;-fx-font-size:12.5px;-fx-line-spacing:4;");
        body.setWrapText(false);
        VBox textBlock = new VBox(6, caution, body);

        Button applyBtn = new Button("Apply Changes");
        applyBtn.setStyle("-fx-background-color:#22c55e;-fx-background-radius:6;-fx-text-fill:black;-fx-font-size:12px;-fx-font-weight:600;-fx-padding:8 24 8 24;-fx-cursor:hand;");
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-background-color:transparent;-fx-border-color:#2E3244;-fx-border-width:1;-fx-border-radius:6;-fx-background-radius:6;-fx-text-fill:#7B82A0;-fx-font-size:12px;-fx-padding:8 20 8 20;-fx-cursor:hand;");
        cancelBtn.setOnMouseEntered(e -> cancelBtn.setStyle("-fx-background-color:#262A3A;-fx-border-color:#4ADE80;-fx-border-width:1;-fx-border-radius:6;-fx-background-radius:6;-fx-text-fill:#E8EAF0;-fx-font-size:12px;-fx-padding:8 20 8 20;-fx-cursor:hand;"));
        cancelBtn.setOnMouseExited(e -> cancelBtn.setStyle("-fx-background-color:transparent;-fx-border-color:#2E3244;-fx-border-width:1;-fx-border-radius:6;-fx-background-radius:6;-fx-text-fill:#7B82A0;-fx-font-size:12px;-fx-padding:8 20 8 20;-fx-cursor:hand;"));

        Region btnSpacer = new Region();
        HBox.setHgrow(btnSpacer, Priority.ALWAYS);
        HBox btnRow = new HBox(8, btnSpacer, cancelBtn, applyBtn);
        btnRow.setStyle("-fx-background-color:#1A1D27;-fx-border-color:#2E3244 transparent transparent transparent;-fx-border-width:1 0 0 0;");
        btnRow.setPadding(new javafx.geometry.Insets(10, 14, 10, 14));

        HBox contentRow = new HBox(14, warningIcon, textBlock);
        contentRow.setAlignment(Pos.TOP_LEFT);
        VBox content = new VBox(contentRow);
        content.setPadding(new javafx.geometry.Insets(20, 24, 16, 24));
        content.setStyle("-fx-background-color:#0F1117;");

        VBox root = new VBox(titleBar, content, btnRow);
        root.setStyle("-fx-background-color:#0F1117;-fx-border-color:#2E3244;-fx-border-width:1;");
        root.setOpacity(0);
        root.setScaleX(0.95);
        root.setScaleY(0.95);

        final boolean[] confirmed = {false};
        applyBtn.setOnAction(e -> { confirmed[0] = true; confirmStage.close(); });
        cancelBtn.setOnAction(e -> confirmStage.close());

        Scene confirmScene = new Scene(root, 400, 210);
        confirmScene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        confirmStage.setScene(confirmScene);
        confirmStage.setResizable(false);
        confirmStage.setOnShown(e -> {
            FadeTransition ft = new FadeTransition(javafx.util.Duration.millis(200), root);
            ft.setFromValue(0); ft.setToValue(1);
            ft.setInterpolator(Interpolator.EASE_OUT);
            ScaleTransition st = new ScaleTransition(javafx.util.Duration.millis(200), root);
            st.setFromX(0.95); st.setFromY(0.95);
            st.setToX(1.0); st.setToY(1.0);
            st.setInterpolator(Interpolator.EASE_OUT);
            new ParallelTransition(ft, st).play();
        });
        confirmStage.showAndWait();
        if (!confirmed[0]) return;

        currentState.onOrganize(this);
        Logger.getLogger().info("Organizing files in: " + selectedFolder);

        // Snapshot the radio-button selection on the FX thread before the task runs
        final boolean useDate = rbDate != null && rbDate.isSelected();
        final boolean useExt  = rbExt  != null && rbExt.isSelected();
        final boolean useIcon = rbIcon != null && rbIcon.isSelected();
        final String  organizeMode = useDate ? "By Date" : useExt ? "By Extension" : "By File Type";

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

            // record this operation in session history
            Instant now = Instant.now();
            OperationHistory.getInstance().add(
                    now, selectedFolder, organizeMode,
                    result.movedFiles(), result.skippedFiles());
            try {
                SnapshotManager.save(now, selectedFolder, organizeMode,
                        result.movedFiles(), result.skippedFiles(),
                        lastOrganizer.getLastMemento());
            } catch (IOException ex) {
                Logger.getLogger().info("Failed to save snapshot: " + ex.getMessage());
            }

            lblDoneSummary.setText(result.movedFiles() + " files moved, " + result.skippedFiles() + " skipped");
            transitionTo(new DoneState(result.movedFiles(), result.skippedFiles()));
        });
        organizeTask.setOnFailed(e -> {
            Logger.getLogger().info("Organization failed: " + organizeTask.getException().getMessage());
            setStatus("Organization failed: " + organizeTask.getException().getMessage());
            transitionTo(new CategorizedState(scannedFiles.size()));
        });

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

    @FXML
    private void handlePreview(ActionEvent event) {
        if (previewPanel != null && previewPanel.isVisible()) {
            previewPanel.setManaged(false);
            previewPanel.setVisible(false);
            if (previewSeparator != null) {
                previewSeparator.setManaged(false);
                previewSeparator.setVisible(false);
            }
            return;
        }

        if (scannedFiles.isEmpty()) {
            setStatus("Scan a folder first to preview.");
            return;
        }

        rebuildPreview();
    }

    private void rebuildPreview() {
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

        // Build TreeView with structured labels: FOLDER:name:count  FILE:name:size
        TreeItem<String> root = new TreeItem<>("ROOT");
        root.setExpanded(true);
        int totalFiles = 0;
        int otherFiles = 0;
        long totalBytes = 0;

        for (Map.Entry<String, List<ScannedFile>> entry : categorized.entrySet()) {
            List<ScannedFile> files = entry.getValue();
            if (files.isEmpty()) continue;
            long folderBytes = files.stream().mapToLong(ScannedFile::sizeBytes).sum();
            TreeItem<String> folderNode = new TreeItem<>(
                    "FOLDER:" + entry.getKey() + ":" + files.size() + ":" + helperMethods.formatFileSize(folderBytes));
            folderNode.setExpanded(true);
            for (ScannedFile f : files) {
                folderNode.getChildren().add(new TreeItem<>(
                        "FILE:" + f.path().getFileName().toString() + ":" + helperMethods.formatFileSize(f.sizeBytes())
                ));
                totalBytes += f.sizeBytes();
            }
            root.getChildren().add(folderNode);
            totalFiles += files.size();
            if (entry.getKey().equals(ExtensionFileCategorizer.FALLBACK_CATEGORY)) {
                otherFiles += files.size();
            }
        }

        final long finalTotalBytes = totalBytes;
        final int  finalTotal      = totalFiles;
        final int  finalOther      = otherFiles;

        // Wire into the existing FXML preview panel controls
        if (treePreview != null) {
            treePreview.setRoot(root);
            treePreview.setShowRoot(false);
            treePreview.setCellFactory(tv -> new TreeCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) { setGraphic(null); setText(null); return; }

                    if (item.startsWith("FOLDER:")) {
                        String[] p = item.split(":", 4);
                        String catName = p[1];

                        Label arrowLabel = new Label(getTreeItem().isExpanded() ? "▾" : "▸");
                        arrowLabel.setStyle("-fx-text-fill:#4B5063;-fx-font-size:11px;");
                        getTreeItem().expandedProperty().addListener((obs, o, exp) ->
                                arrowLabel.setText(exp ? "▾" : "▸"));

                        String catColor = CAT_DOT_COLOR.getOrDefault(catName, "#7B82A0");

                        Label iconLabel = new Label(CAT_ICON.getOrDefault(catName, "\u25A1") + " ");
                        iconLabel.setStyle("-fx-text-fill:" + catColor + ";-fx-font-size:13px;");

                        Label nameLabel = new Label(catName);
                        nameLabel.setStyle("-fx-text-fill:#E8EAF0;-fx-font-weight:bold;-fx-font-size:11.5px;");

                        Region leftAccent = new Region();
                        leftAccent.setStyle("-fx-min-width:3px;-fx-min-height:18px;-fx-max-width:3px;-fx-max-height:18px;-fx-background-color:" + catColor + ";-fx-background-radius:2;");

                        Region spacer = new Region();
                        HBox.setHgrow(spacer, Priority.ALWAYS);

                        Label countLabel = new Label(p[2] + " files");
                        countLabel.setStyle("-fx-text-fill:#7B82A0;-fx-font-size:10px;");
                        Label sizeLabel = new Label("  " + p[3]);
                        sizeLabel.setStyle("-fx-text-fill:#7B82A0;-fx-font-size:10px;");

                        HBox row = new HBox(6, leftAccent, arrowLabel, iconLabel, nameLabel, spacer, countLabel, sizeLabel);
                        row.setAlignment(Pos.CENTER_LEFT);
                        setGraphic(row);

                    } else if (item.startsWith("FILE:")) {
                        String[] p = item.split(":", 3);
                        String filename = p[1];
                        String ext = filename.contains(".")
                                ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase() : "";
                        String colorClass = EXT_COLOR.getOrDefault(ext, "ext-gray");

                        Label badge = new Label(ext.isEmpty() ? "···" : ext.toUpperCase());
                        badge.getStyleClass().addAll("ext-badge", colorClass);

                        Label nameLabel = new Label(filename);
                        nameLabel.setStyle("-fx-text-fill:#9DA3BA;-fx-font-size:10.5px;");

                        Region spacer = new Region();
                        HBox.setHgrow(spacer, Priority.ALWAYS);

                        Label sizeLabel = new Label(p[2]);
                        sizeLabel.setStyle("-fx-text-fill:#4B5063;-fx-font-size:10px;");

                        HBox row = new HBox(6, badge, nameLabel, spacer, sizeLabel);
                        row.setAlignment(Pos.CENTER_LEFT);
                        setGraphic(row);
                    }
                    setText(null);
                }
            });

            int folderCount = root.getChildren().size();

            if (lblPreviewMethod != null) lblPreviewMethod.setText("by " + modeLabel);
            if (lblPreviewSummary != null)
                lblPreviewSummary.setText(finalTotal + " files · " + folderCount + " folders");
            if (lblTotalSize != null)
                lblTotalSize.setText(helperMethods.formatFileSize(finalTotalBytes));
            if (lblDestPath != null && selectedFolder != null)
                lblDestPath.setText(selectedFolder.toString());

            if (legendBar != null) {
                legendBar.getChildren().clear();
                for (Map.Entry<String, List<ScannedFile>> e : categorized.entrySet()) {
                    if (e.getValue().isEmpty()) continue;
                    String dotColor = CAT_DOT_COLOR.getOrDefault(e.getKey(), "#7B82A0");
                    Region dot = new Region();
                    dot.getStyleClass().add("legend-dot");
                    dot.setStyle("-fx-background-color:" + dotColor + ";");
                    Label lbl = new Label(e.getKey());
                    lbl.getStyleClass().add("legend-label");
                    HBox entry = new HBox(4, dot, lbl);
                    entry.setAlignment(Pos.CENTER_LEFT);
                    legendBar.getChildren().add(entry);
                }
            }
            if (previewPanel != null) {
                previewPanel.setManaged(true);
                previewPanel.setVisible(true);
            }
            if (previewSeparator != null) {
                previewSeparator.setManaged(true);
                previewSeparator.setVisible(true);
            }
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

    @FXML
    private void handleOpenFolder(ActionEvent event) {
        try {
            Desktop.getDesktop().open(selectedFolder.toFile());
        } catch (IOException ex) {
            setStatus("Failed to open folder: " + ex.getMessage());
        }
    }

    @FXML
    private void handleNavOrganize(MouseEvent event) {
        organizePage.setManaged(true);
        organizePage.setVisible(true);
        historyPage.setManaged(false);
        historyPage.setVisible(false);

        navOrganize.getStyleClass().add("nav-item-active");
        navHistory.getStyleClass().remove("nav-item-active");
        navOrganizeIcon.getStyleClass().setAll("nav-icon-active");
        navOrganizeLabel.getStyleClass().setAll("nav-label-active");
        navHistoryIcon.getStyleClass().setAll("nav-icon");
        navHistoryLabel.getStyleClass().setAll("nav-label");
    }

    @FXML
    private void handleNavHistory(MouseEvent event) {
        historyPage.setManaged(true);
        historyPage.setVisible(true);
        organizePage.setManaged(false);
        organizePage.setVisible(false);

        navHistory.getStyleClass().add("nav-item-active");
        navOrganize.getStyleClass().remove("nav-item-active");
        navHistoryIcon.getStyleClass().setAll("nav-icon-active");
        navHistoryLabel.getStyleClass().setAll("nav-label-active");
        navOrganizeIcon.getStyleClass().setAll("nav-icon");
        navOrganizeLabel.getStyleClass().setAll("nav-label");

        refreshHistoryTable();
    }

    @FXML
    private void handleRestoreOperation(ActionEvent event) {
        OperationRecord selected = historyTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            setStatus("Select an operation to restore.");
            return;
        }
        try {
            OrganizationMemento memento = SnapshotManager.loadMemento(selected);
            if (memento == null) {
                setStatus("Snapshot file not found for this operation.");
                return;
            }

            memento.restore();

            refreshHistoryTable();
            setStatus("Operation undone.");
        } catch (IOException ex) {
            setStatus("Restore failed: " + ex.getMessage());
            Logger.getLogger().info("Restore failed: " + ex.getMessage());
        }
    }

    private void handleShowDetails(OperationRecord record) {
        List<String[]> mappings = new java.util.ArrayList<>();
        boolean mappingsFailed = false;
        try {
            mappings = SnapshotManager.loadFileMappings(record);
        } catch (IOException e) {
            mappingsFailed = true;
        }

        long folderCount = mappings.stream()
                .map(m -> Path.of(m[1]).getParent())
                .distinct().count();
        Map<String, Long> extCount = mappings.stream()
                .map(m -> {
                    String name = Path.of(m[1]).getFileName().toString();
                    int dot = name.lastIndexOf('.');
                    return dot > 0 ? name.substring(dot + 1).toLowerCase() : "(none)";
                })
                .collect(java.util.stream.Collectors.groupingBy(
                        e -> e, java.util.stream.Collectors.counting()));
        String extSummary = extCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> e.getKey().toUpperCase() + " \u00D7" + e.getValue())
                .collect(java.util.stream.Collectors.joining("  \u2022  "));
        if (extSummary.isBlank()) {
            extSummary = "Details unavailable";
        }

        boolean restorable = SnapshotManager.isRestorable(record);

        // ── Header ────────────────────────────────────────────────
        Label title = new Label(record.mode());
        title.setStyle("-fx-text-fill:#E8EAF0;-fx-font-size:15px;-fx-font-weight:bold;");
        Label badge = new Label(restorable ? "RESTORABLE" : "UNRESTORABLE");
        badge.setStyle(restorable
                ? "-fx-background-color:#143D20;-fx-text-fill:#4ADE80;-fx-font-size:10px;-fx-font-weight:bold;-fx-padding:2 8 2 8;-fx-background-radius:3;"
                : "-fx-background-color:#3D1515;-fx-text-fill:#F87171;-fx-font-size:10px;-fx-font-weight:bold;-fx-padding:2 8 2 8;-fx-background-radius:3;");
        HBox headerRow = new HBox(10, title, badge);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        // ── Metadata card ─────────────────────────────────────────
        Label tsLabel = new Label(record.formattedTimestamp());
        tsLabel.setStyle("-fx-text-fill:#7B82A0;-fx-font-size:11.5px;");
        Label folderLabel = new Label(record.sourceFolder().toAbsolutePath().normalize().toString());
        folderLabel.setStyle("-fx-text-fill:#7B82A0;-fx-font-size:11.5px;");

        HBox stats = new HBox(20);
        stats.setAlignment(Pos.CENTER_LEFT);
        String[] statData = {
            record.filesMoved() + " moved",  "#60A5FA",
            record.filesSkipped() + " skipped", "#FBBF24",
            mappings.size() + " total",   "#E8EAF0",
            folderCount + " folders",     "#7B82A0"
        };
        for (int i = 0; i < statData.length; i += 2) {
            Label l = new Label(statData[i]);
            l.setStyle("-fx-text-fill:" + statData[i + 1] + ";-fx-font-size:12px;");
            stats.getChildren().add(l);
        }

        Label extLine = new Label(extSummary);
        extLine.setStyle("-fx-text-fill:#A78BFA;-fx-font-size:11px;");

        VBox meta = new VBox(4, tsLabel, folderLabel, stats, extLine);
        meta.setStyle("-fx-background-color:#1A1D27;-fx-background-radius:6;-fx-padding:12 14 12 14;");
        meta.setPrefWidth(700);

        // ── File table ────────────────────────────────────────────
        TableView<String[]> fileTable = new TableView<>();
        fileTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        fileTable.setPrefHeight(280);
        fileTable.getStyleClass().add("file-table");

        TableColumn<String[], String> colOrig = new TableColumn<>("ORIGINAL LOCATION");
        colOrig.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue()[1]));
        TableColumn<String[], String> colNew = new TableColumn<>("NEW LOCATION");
        colNew.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue()[0]));
        fileTable.getColumns().addAll(colOrig, colNew);

        if (mappingsFailed || mappings.isEmpty()) {
            Label noData = new Label(mappingsFailed ? "Details unavailable (snapshot not found)" : "No files in this operation");
            noData.setStyle("-fx-text-fill:#7B82A0;-fx-font-size:12px;");
            fileTable.setPlaceholder(noData);
        } else {
            fileTable.getItems().addAll(mappings);
        }

        // ── Assemble ──────────────────────────────────────────────
        VBox body = new VBox(10, headerRow, meta, fileTable);
        body.setStyle("-fx-background-color:#0F1117;");
        body.setPadding(new javafx.geometry.Insets(16, 20, 16, 20));

        Stage detailsStage = new Stage();
        detailsStage.initStyle(StageStyle.TRANSPARENT);
        detailsStage.initModality(Modality.APPLICATION_MODAL);
        HBox titleBar = createDialogTitleBar("Operation Details", detailsStage);

        VBox root = new VBox(titleBar, body);
        root.setStyle("-fx-background-color:#0F1117;-fx-border-color:#2E3244;-fx-border-width:1;");
        root.setOpacity(0);
        root.setScaleX(0.95);
        root.setScaleY(0.95);

        Scene scene = new Scene(root, 720, 520);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        detailsStage.setScene(scene);
        detailsStage.setResizable(false);
        detailsStage.setOnShown(e -> {
            FadeTransition ft = new FadeTransition(javafx.util.Duration.millis(200), root);
            ft.setFromValue(0); ft.setToValue(1);
            ft.setInterpolator(Interpolator.EASE_OUT);
            ScaleTransition st = new ScaleTransition(javafx.util.Duration.millis(200), root);
            st.setFromX(0.95); st.setFromY(0.95);
            st.setToX(1.0); st.setToY(1.0);
            st.setInterpolator(Interpolator.EASE_OUT);
            new ParallelTransition(ft, st).play();
        });
        detailsStage.showAndWait();
    }

    @FXML private void handleSettings(ActionEvent event) { System.out.println("Settings clicked"); }

    @FXML private void handleOrganize(ActionEvent event) { /* reserved for future use */ }

    @FXML
    private void handleInfo(ActionEvent event) {
        Stage stage = new Stage();
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("About Musannif");

        // ── Custom title bar ──────────────────────────────────────
        HBox titleBar = createDialogTitleBar("About Musannif", stage);

        // ── Content ───────────────────────────────────────────────
        ImageView logo = new ImageView(new Image(
                MainController.class.getResourceAsStream("/org/app/musannif/icons/Document.png")));
        logo.setFitHeight(72); logo.setFitWidth(72);
        logo.setPreserveRatio(true);

        Hyperlink authorLink1 = new Hyperlink("@nullMuath");
        authorLink1.setStyle("-fx-text-fill:#4ADE80;-fx-font-size:12px;-fx-border-color:transparent;");
        authorLink1.setOnAction(e -> openURL("https://github.com/nullMuath"));
        Hyperlink authorLink2 = new Hyperlink("@MeCaveman");
        authorLink2.setStyle("-fx-text-fill:#4ADE80;-fx-font-size:12px;-fx-border-color:transparent;");
        authorLink2.setOnAction(e -> openURL("https://github.com/MeCaveman"));
        Hyperlink repoLink = new Hyperlink("github.com/cpit252-spring-26-IT2/project-musannif");
        repoLink.setStyle("-fx-text-fill:#4ADE80;-fx-font-size:12px;-fx-border-color:transparent;");
        repoLink.setOnAction(e -> openURL("https://github.com/cpit252-spring-26-IT2/project-musannif"));

        Label appTitle = new Label("Musannif - \u0645\u0635\u0646\u0641");
        appTitle.setStyle("-fx-font-family:'IBM Plex Sans Arabic';-fx-text-fill:#E8EAF0;-fx-font-size:20px;-fx-font-weight:bold;");

        Label desc = new Label("Organizes your files into neat folders\nby type, date, or extension.");
        desc.setStyle("-fx-text-fill:#9DA3BA;-fx-font-size:13px;-fx-alignment:CENTER;-fx-line-spacing:4;");
        desc.setAlignment(Pos.CENTER);
        desc.setPrefHeight(48);

        Label version = new Label("Version 1.1.0");
        version.setStyle("-fx-text-fill:#7B82A0;-fx-font-size:11px;");

        Label devLabel = new Label("Developed by");
        devLabel.setStyle("-fx-text-fill:#7B82A0;-fx-font-size:11px;");
        devLabel.setAlignment(Pos.CENTER);
        devLabel.setMaxWidth(Double.MAX_VALUE);

        HBox authors = new HBox(4, authorLink1, new Label("&"), authorLink2);
        authors.setAlignment(Pos.CENTER);
        ((Label)authors.getChildren().get(1)).setStyle("-fx-text-fill:#4B5063;-fx-font-size:12px;");

        Separator sep = new Separator();
        sep.setStyle("-fx-background-color:#2E3244;");

        Label repoLabel = new Label("Project Repository:");
        repoLabel.setStyle("-fx-text-fill:#7B82A0;-fx-font-size:11px;-fx-alignment:CENTER;");
        repoLabel.setMaxWidth(Double.MAX_VALUE);
        repoLabel.setAlignment(Pos.CENTER);

        VBox content = new VBox(6, logo, appTitle, desc, version, devLabel, authors, sep, repoLabel, repoLink);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new javafx.geometry.Insets(20, 30, 24, 30));

        VBox root = new VBox(titleBar, content);
        root.setStyle("-fx-background-color:#0F1117;-fx-border-color:#2E3244;-fx-border-width:1;");
        root.setOpacity(0);
        root.setScaleX(0.95);
        root.setScaleY(0.95);

        Scene scene = new Scene(root, 380, 370);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        stage.setScene(scene);
        stage.setResizable(false);
        stage.setOnShown(e -> {
            FadeTransition ft = new FadeTransition(javafx.util.Duration.millis(200), root);
            ft.setFromValue(0); ft.setToValue(1);
            ft.setInterpolator(Interpolator.EASE_OUT);
            ScaleTransition st = new ScaleTransition(javafx.util.Duration.millis(200), root);
            st.setFromX(0.95); st.setFromY(0.95);
            st.setToX(1.0); st.setToY(1.0);
            st.setInterpolator(Interpolator.EASE_OUT);
            new ParallelTransition(ft, st).play();
        });
        stage.showAndWait();
    }

    @FXML
    private void handleGenerateTestFiles(ActionEvent event) throws IOException, InterruptedException {
        Logger.getLogger().info("Generate Test Files Button Clicked");
        TestFilesGenerator.generate();
        setStatus("Files Generated!");
        Desktop.getDesktop().open(new File(System.getProperty("user.home") + File.separator + ".musannif-test"));
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

    private void setupHistoryTable() {
        colHistTime.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().formattedTimestamp()));
        colHistFolder.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().sourceFolder().getFileName().toString()));
        colHistMode.setCellValueFactory(cd ->
                new SimpleStringProperty(cd.getValue().mode()));
        colHistMoved.setCellValueFactory(cd ->
                new SimpleStringProperty(String.valueOf(cd.getValue().filesMoved())));
        colHistSkipped.setCellValueFactory(cd ->
                new SimpleStringProperty(String.valueOf(cd.getValue().filesSkipped())));
    }

    private void refreshHistoryTable() {
        OperationHistory history = OperationHistory.getInstance();
        ObservableList<OperationRecord> items = historyTable.getItems();
        items.setAll(history.getAll());
        FXCollections.sort(items, (a, b) -> b.timestamp().compareTo(a.timestamp()));
        btnRestoreOperation.setDisable(true);
    }

    private void setupTitleBarDrag() {
        titleBar.setOnMousePressed(e -> { xOffset = e.getSceneX(); yOffset = e.getSceneY(); });
        titleBar.setOnMouseDragged(e -> {
            Stage stage = (Stage) titleBar.getScene().getWindow();
            stage.setX(e.getScreenX() - xOffset);
            stage.setY(e.getScreenY() - yOffset);
        });
    }

    private void setupPreviewResize() {
        if (previewSeparator == null) return;
        previewSeparator.setStyle("-fx-cursor: col-resize;");
        previewSeparator.setOnMousePressed(e -> xOffset = e.getScreenX());
        previewSeparator.setOnMouseDragged(e -> {
            double delta = e.getScreenX() - xOffset;
            double newWidth = previewPanel.getPrefWidth() - delta;
            if (newWidth >= previewPanel.getMinWidth() && newWidth <= previewPanel.getMaxWidth()) {
                previewPanel.setPrefWidth(newWidth);
                xOffset = e.getScreenX();
            }
        });
    }

    private HBox createDialogTitleBar(String titleText, Stage stage) {
        Label title = new Label(titleText);
        title.setStyle("-fx-text-fill:#E8EAF0;-fx-font-size:13px;-fx-font-weight:600;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button closeBtn = new Button("\u2715");
        closeBtn.setStyle("-fx-background-color:transparent;-fx-text-fill:#8B92AA;-fx-font-size:14px;-fx-padding:6 10 6 10;-fx-cursor:hand;-fx-border-radius:4;-fx-background-radius:4;");
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle("-fx-background-color:#DC3545;-fx-text-fill:#FFFFFF;-fx-font-size:14px;-fx-padding:6 10 6 10;-fx-cursor:hand;-fx-border-radius:4;-fx-background-radius:4;"));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle("-fx-background-color:transparent;-fx-text-fill:#8B92AA;-fx-font-size:14px;-fx-padding:6 10 6 10;-fx-cursor:hand;-fx-border-radius:4;-fx-background-radius:4;"));
        closeBtn.setOnAction(e -> stage.close());
        HBox bar = new HBox(12, title, spacer, closeBtn);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color:#1A1D27;-fx-border-color:#2E3244;-fx-border-width:0 0 1 0;");
        bar.setPrefHeight(40);
        bar.setPadding(new javafx.geometry.Insets(0, 12, 0, 12));
        final double[] dragXY = new double[2];
        bar.setOnMousePressed(e -> { dragXY[0] = e.getSceneX(); dragXY[1] = e.getSceneY(); });
        bar.setOnMouseDragged(e -> { stage.setX(e.getScreenX() - dragXY[0]); stage.setY(e.getScreenY() - dragXY[1]); });
        return bar;
    }

    private void openURL(String url) {
        try { Desktop.getDesktop().browse(new URI(url)); }
        catch (Exception e) { e.printStackTrace(); }
    }
}
