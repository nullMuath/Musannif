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

/**
 * Main controller for the Musannif JavaFX application.
 *
 * Design patterns wired here
 * 
 *   State — {@link #currentState} delegates all button
 *       enable/disable decisions; this controller just calls
 *       {@link #transitionTo(AppState)}.
 *   Command + Memento — a single {@link CommandHistory} is
 *       shared with {@link FileOrganizerFacade}.  After organizing,
 *       {@link #handleUndoAll} calls {@link OrganizationMemento#restore()} for
 *       bulk undo; {@link #handleUndo}/{@link #handleRedo} undo single moves.
 *
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

    // Setters called by state objects
    public void setBtnScanDisabled(boolean disabled)  { btnScan.setDisable(disabled); }
    public void setBtnApplyDisabled(boolean disabled) { btnApply.setDisable(disabled); }
    public void setStatus(String text)                { lblStatus.setText(text); }

    // -------------------------------------------------------------------------
    //  Model — ONE shared CommandHistory so Facade and controller use the same stack
    // -------------------------------------------------------------------------

    private ObservableList<ScannedFile> scannedFiles = FXCollections.observableArrayList();
    private Path selectedFolder;

    /**
     * Shared command history.  Passed into every {@link FileOrganizerFacade}
     * instance so undo/redo in this controller always targets the right stack.
     */
    private final CommandHistory commandHistory = new CommandHistory();

    /**
     * Holds a reference to the organizer created by the most recent facade
     * build, so we can reach {@code getLastMemento()} after organizing.
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

        // Enter the initial state — sets button enable/disable via State pattern
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
            Logger.getLogger().info("Folder Selected");
            // Delegate to the current state — it knows the right next state
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
        currentState.onScan(this);   // → ScanningState (disables buttons)
        Logger.getLogger().info("Folders scanning");

        Task<List<ScannedFile>> scanTask = new Task<>() {
            @Override
            protected List<ScannedFile> call() throws Exception {
                FileScanner scanner = new FileScanner.Builder()
                        .skipHidden(true).maxDepth(1).build();
                Logger.getLogger().info("FileScanner Object Created");
                return scanner.scan(selectedFolder);
            }
        };

        scanTask.setOnSucceeded(e -> {
            scannedFiles.addAll(scanTask.getValue());
            Logger.getLogger().info("Folder scanning Complete");
            transitionTo(new CategorizedState(scannedFiles.size()));
        });
        scanTask.setOnFailed(e -> {
            Logger.getLogger().info("Folder scanning Failed");
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
        currentState.onOrganize(this);   // → OrganizingState
        Logger.getLogger().info("Organizing Files in: " + selectedFolder);

        Task<FileOrganizer.OrganizationResult> organizeTask = new Task<>() {
            @Override
            protected FileOrganizer.OrganizationResult call() throws Exception {
                // Build facade with the SHARED CommandHistory so undo/redo works
                FileOrganizerFacade facade = new FileOrganizerFacade.Builder()
                        .skipHidden(true)
                        .maxDepth(1)
                        .withDefaultCategories()
                        .commandHistory(commandHistory)   // ← shared history
                        .build();

                // Keep a reference so we can reach getLastMemento() later
                lastOrganizer = facade.getOrganizer();

                return facade.organize(selectedFolder, selectedFolder);
            }
        };

        organizeTask.setOnSucceeded(e -> {
            FileOrganizer.OrganizationResult result = organizeTask.getValue();
            if (rbIcon.isSelected()) helperMethods.addFolderIcons(selectedFolder);
            scannedFiles.clear();
            Logger.getLogger().info("Organization Complete: " + result.movedFiles()
                    + " files moved, " + result.skippedFiles() + " skipped.");
            transitionTo(new DoneState(result.movedFiles(), result.skippedFiles()));
        });
        organizeTask.setOnFailed(e -> {
            Logger.getLogger().info("Organization Failed: " + organizeTask.getException().getMessage());
            setStatus("Organization Failed: " + organizeTask.getException().getMessage());
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
        } catch (IOException ex) {
            setStatus("Redo failed: " + ex.getMessage());
            Logger.getLogger().info("Redo failed: " + ex.getMessage());
        }
    }

    /** Undo the entire last organize operation using the Memento snapshot. */
    @FXML
    private void handleUndoAll(ActionEvent event) {
        if (lastOrganizer == null) { setStatus("No organize operation to undo."); return; }
        OrganizationMemento memento = lastOrganizer.getLastMemento();
        if (memento == null) { setStatus("No organize operation to undo."); return; }
        try {
            memento.restore();
            commandHistory.clear();   // stack is now stale after bulk restore
            setStatus("All " + memento.size() + " file moves undone.");
            Logger.getLogger().info("Memento restore: " + memento.size() + " files restored.");
        } catch (IOException ex) {
            setStatus("Undo-all failed: " + ex.getMessage());
            Logger.getLogger().info("Memento restore failed: " + ex.getMessage());
        }
    }

    @FXML private void handleOrganize(ActionEvent event) { System.out.println("Organize clicked"); }
    @FXML private void handlePreview(ActionEvent event)  { System.out.println("Preview clicked"); }
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
    //  Private helpers
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
