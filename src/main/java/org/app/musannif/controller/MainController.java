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
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.event.ActionEvent;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class MainController {
    private ObservableList<ScannedFile> scannedFiles = FXCollections.observableArrayList();
    private Path selectedFolder;
    private double xOffset = 0;
    private double yOffset = 0;
    @FXML
    private TextField txtFolderPath;
    @FXML
    private Button btnBrowse;
    @FXML
    private Button btnScan;
    @FXML
    private Button btnApply;
    @FXML
    private Label lblStatus;
    @FXML
    private Button btnGenerateTestFiles;
    @FXML
    private Button btnHistory;
    @FXML
    private HBox titleBar;
    @FXML
    private Button btnMinimize;
    @FXML
    private Button btnMaximize;
    @FXML
    private Button btnClose;
    @FXML
    private ImageView sidebarLogo;
    @FXML
    private TableView<ScannedFile> fileTable;
    @FXML
    private TableColumn<ScannedFile, String> colName;
    @FXML
    private TableColumn<ScannedFile, String> colExt;
    @FXML
    private TableColumn<ScannedFile, String> colSize;
    @FXML
    private TableColumn<ScannedFile, String> colModified;
    @FXML
    private RadioButton rbType;
    @FXML
    private RadioButton rbIcon;
    @FXML
    private RadioButton rbDate;
    @FXML
    private RadioButton rbExt;
    @FXML
    private Label lblEmptyState;
    @FXML
    private VBox previewPanel;
    @FXML
    private Button btnClosePreview;
    @FXML
    private Button btnTogglePreview;
    @FXML
    private Label lblPreviewMethod;
    @FXML
    private Label lblDestPath;
    @FXML
    private TreeView<String> treePreview;
    @FXML
    private Label lblPreviewSummary;
    @FXML
    private Label lblTotalSize;
    @FXML
    private ProgressBar scanProgress;
    @FXML
    private HBox statusBar;

    @FXML
    private void initialize() {
        try {
            sidebarLogo.setImage(new Image(MainController.class.getResourceAsStream("/org/app/musannif/icons/app-icon.png")));
        } catch (Exception e) {
            System.err.println("Failed to load sidebar logo: " + e.getMessage());
        }

        fileTable.setItems(scannedFiles);
        fileTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        btnScan.setDisable(true);
        btnApply.setDisable(true);
        lblStatus.setText("Select a folder to begin.");
        colName.setCellValueFactory(cellData->
                new SimpleStringProperty(cellData.getValue().path().getFileName().toString()));
        colExt.setCellValueFactory(cellData->
                new SimpleStringProperty(cellData.getValue().extension()));
        colSize.setCellValueFactory(cellData ->
                new SimpleStringProperty(helperMethods.formatFileSize(cellData.getValue().sizeBytes())));
        colModified.setCellValueFactory(cellData ->
                new SimpleStringProperty(helperMethods.formatDateTime(cellData.getValue().lastModified())));

        setupTitleBarDrag();
        Logger.getLogger().info("Application Started");
    }

    private void setupTitleBarDrag() {
        titleBar.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });

        titleBar.setOnMouseDragged(event -> {
            Stage stage = (Stage) titleBar.getScene().getWindow();
            stage.setX(event.getScreenX() - xOffset);
            stage.setY(event.getScreenY() - yOffset);
        });
    }

    @FXML
    private void handleBrowse(ActionEvent event) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Folder to Organize");

        chooser.setInitialDirectory(new File(System.getProperty("user.home")+"/.musannif-test"));

        Stage stage = (Stage) btnBrowse.getScene().getWindow();
        File chosen = chooser.showDialog(stage);
        if (chosen != null) {
            selectedFolder = chosen.toPath();
            txtFolderPath.setText(chosen.getAbsolutePath());
            btnScan.setDisable(false);
            lblStatus.setText("Folder selected. Click Scan Folder to continue.");
            Logger.getLogger().info("Folder selected: " + chosen.getAbsolutePath());
            scannedFiles.clear();
        }
    }

    @FXML
    private void handleScan(ActionEvent event) {
        if (selectedFolder == null) {
            lblStatus.setText("Please select a folder first.");
            return;
        }
        // Reset state
        scannedFiles.clear();
        btnScan.setDisable(true);
        lblStatus.setText("Scanning...");
        Logger.getLogger().info("Folders scanning");


        // Run scan on background thread so the UI stays responsive
        Task<List<ScannedFile>> scanTask = new Task<>() {
            @Override
            protected List<ScannedFile> call() throws Exception {
                FileScanner scanner = new FileScanner.Builder()
                        .skipHidden(true)
                        .maxDepth(1)       // -1 for unlimited depth
                        .build();
                Logger.getLogger().info("FileScanner Object Created");
                return scanner.scan(selectedFolder);
            }
        };

        scanTask.setOnSucceeded(e -> {
            scannedFiles.addAll(scanTask.getValue());
            btnScan.setDisable(false);
            btnApply.setDisable(false);
            lblStatus.setText("Scan complete — " + scannedFiles.size() + " files found.");
            Logger.getLogger().info("Folder scanning Complete");

        });

        scanTask.setOnFailed(e -> {
            btnScan.setDisable(false);
            lblStatus.setText("Scan failed: " + scanTask.getException().getMessage());
            Logger.getLogger().info("Folder scanning Failed");
        });
        new Thread(scanTask).start();
    }

    private FileCategorizer buildCategorizer() {
        if (rbDate.isSelected()) {
            Logger.getLogger().info("Categorizing by: Date (by month)");
            return new DateFileCategorizer.Builder()
                    .period(new Periods.ByMonth())
                    .build();
        } else if (rbExt.isSelected()) {
            Logger.getLogger().info("Categorizing by: File extension");
            ExtensionFileCategorizer.Builder builder = new ExtensionFileCategorizer.Builder();
            Set<String> seenExtensions = new HashSet<>();
            for (ScannedFile file : scannedFiles) {
                String ext = file.extension();
                if (!ext.isEmpty() && seenExtensions.add(ext)) {
                    final String extension = ext;
                    builder.register(new ExtensionCategory(ext, ext) {});
                }
            }
            Logger.getLogger().info("Found " + seenExtensions.size() + " unique file extensions");
            return builder.build();
        } else {
            Logger.getLogger().info("Categorizing by: File type");
            ExtensionFileCategorizer.Builder builder = new ExtensionFileCategorizer.Builder();
            builder.register(new Categories.Documents());
            builder.register(new Categories.Images());
            builder.register(new Categories.Videos());
            builder.register(new Categories.Audio());
            builder.register(new Categories.Archives());
            return builder.build();
        }
    }

    @FXML
    private void handleApply(ActionEvent event) throws IOException {
        if (selectedFolder == null || scannedFiles.isEmpty()){
            lblStatus.setText("Nothing to Organize, Scan a folder first.");
            return;
        }

        btnApply.setDisable(true);
        btnScan.setDisable(true);

        lblStatus.setText("Organizing");
        Logger.getLogger().info("Starting organization of " + scannedFiles.size() + " files");

        Task<FileOrganizer.OrganizationResult> organizeTask =
                new Task<>() {
                    @Override
                    protected FileOrganizer.OrganizationResult call() throws Exception {
                        FileCategorizer categorizer = buildCategorizer();
                        FileOrganizerFacade facade = new FileOrganizerFacade.Builder()
                                .skipHidden(true)
                                .maxDepth(1)
                                .withCategorizer(categorizer)
                                .build();
                        return facade.organize(selectedFolder, selectedFolder);
                    }
                };


        organizeTask.setOnSucceeded(e -> {
            FileOrganizer.OrganizationResult result = organizeTask.getValue();
            if (rbIcon.isSelected())
                helperMethods.addFolderIcons(selectedFolder);
            scannedFiles.clear();
            btnScan.setDisable(false);
            btnApply.setDisable(false);
            lblStatus.setText("Done —" + result.movedFiles() + " files, " + result.skippedFiles() + " skipped.");
            Logger.getLogger().info("Organization Complete: "+ result.movedFiles() + " files moved, " + result.skippedFiles() + " skipped.");
        });

        organizeTask.setOnFailed(e -> {
            btnScan.setDisable(false);
            btnApply.setDisable(false);
            lblStatus.setText("Organization Failed: " + organizeTask.getException().getMessage());
            Logger.getLogger().info("Organization Failed: "+ organizeTask.getException().getMessage());
        });

        Desktop.getDesktop().open((selectedFolder).toFile());
        new Thread(organizeTask).start();



    }

    @FXML
    private void handleOrganize(ActionEvent event) {
        System.out.println("Organize button clicked!");
        // We will add the Organize code here later
    }

    @FXML
    private void handlePreview(ActionEvent event) {
        System.out.println("Preview button clicked!");
        // We will add the Preview code here later
    }

    @FXML
    private void handleHistory(ActionEvent event) {
        System.out.println("History button clicked!");
        // We will add the History code here later
    }

    @FXML
    private void handleSettings(ActionEvent event) {
        System.out.println("Settings button clicked!");
        // We will add the Settings code here later
    }

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
            ImageView logo = new ImageView(new Image(MainController.class.getResourceAsStream("/org/app/musannif/icons/logo-about.png")));
            logo.setFitHeight(100);
            logo.setFitWidth(100);
            logo.setPreserveRatio(true);
            HBox logoContainer = new HBox(logo);
            logoContainer.setAlignment(Pos.CENTER);
            logoContainer.setPrefHeight(120);
            contentBox.getChildren().add(logoContainer);
        } catch (Exception e) {
            Logger.getLogger().info("Could not load logo image: " + e.getMessage());
        }

        HBox authors = new HBox(2, authorLink1, new Label("&"), authorLink2);
        authors.setAlignment(Pos.CENTER);
        Label devLabel = new Label("Developed by");
        devLabel.setMaxWidth(Double.MAX_VALUE);
        devLabel.setAlignment(Pos.CENTER);

        contentBox.getChildren().addAll(
                new Label("a javafx desktop app that organizes and sorts your files."),
                new Label("Version 1.0.0"),
                devLabel,
                authors,
                new Separator(),
                new Label("Project Repository:"),
                repoLink
        );

        VBox content = contentBox;

        Alert alert = new Alert(Alert.AlertType.NONE);
        alert.setTitle("About Musannif");
        alert.setHeaderText("Musannif - مصنف");
        alert.getDialogPane().setContent(content);
        alert.getButtonTypes().add(ButtonType.OK);
        alert.showAndWait();
    }

    private void openURL(String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @FXML
    private void handleGenerateTestFiles(ActionEvent event) throws IOException, InterruptedException {
        Logger.getLogger().info("Generating test files");
        TestFilesGenerator.generate();
        lblStatus.setText("Test files generated!");
        Logger.getLogger().info("Test files generated successfully");
        Desktop.getDesktop().open(new File(System.getProperty("user.home")+"/.musannif-test"));
    }

    @FXML
    private void handleMinimize(ActionEvent event) {
        Stage stage = (Stage) btnMinimize.getScene().getWindow();
        stage.setIconified(true);
    }

    @FXML
    private void handleMaximize(ActionEvent event) {
        Stage stage = (Stage) btnMaximize.getScene().getWindow();
        stage.setMaximized(!stage.isMaximized());
    }

    @FXML
    private void handleClose(ActionEvent event) {
        Stage stage = (Stage) btnClose.getScene().getWindow();
        stage.close();
    }
}
