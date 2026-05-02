package com.blindwatermark.controller;

import com.blindwatermark.common.AppLogger;
import com.blindwatermark.service.ActivationService;
import com.blindwatermark.service.BatchWatermarkService;
import com.blindwatermark.util.UserPreferences;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.BetweenFormatter;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * 批量处理页面控制器
 * 支持选择目录进行批量嵌入水印
 * 在表格中实时展示每个文件的处理结果
 */
@Component
public class BatchController implements Initializable {

    private final BatchWatermarkService batchService;
    private final ActivationService activationService;

    @FXML private TextField txtSourceDir;
    @FXML private Button btnChooseDir;
    @FXML private TextField txtOutputDir;
    @FXML private Button btnChooseOutputDir;
    @FXML private TextField txtWatermarkText;
    @FXML private TextField txtPassword;
    @FXML private Slider sliderStrength;
    @FXML private Label lblStrengthValue;
    @FXML private Button btnStart;
    @FXML private ProgressBar progressBar;
    @FXML private Label lblStatus;
    @FXML private TableView<BatchWatermarkService.FileResult> resultTable;
    @FXML private TableColumn<BatchWatermarkService.FileResult, String> colFileName;
    @FXML private TableColumn<BatchWatermarkService.FileResult, String> colStatus;
    @FXML private TableColumn<BatchWatermarkService.FileResult, String> colResult;
    @FXML private Label lblSummary;

    public BatchController(BatchWatermarkService batchService, ActivationService activationService) {
        this.batchService = batchService;
        this.activationService = activationService;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        sliderStrength.valueProperty().addListener((obs, oldVal, newVal) -> {
            lblStrengthValue.setText(String.valueOf(newVal.intValue()));
        });
        sliderStrength.setValue(50);

        applyTextLimit(txtWatermarkText, 200);
        applyTextLimit(txtPassword, 64);

        resultTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        resultTable.getStyleClass().add("bordered");

        colFileName.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getFileName()));
        colFileName.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setGraphic(null);
                setStyle("-fx-alignment: CENTER;");
            }
        });

        colStatus.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getStatus()));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("-fx-alignment: CENTER;");
                } else if ("SUCCESS".equals(item)) {
                    setText("成功");
                    setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold; -fx-alignment: CENTER;");
                } else {
                    setText("失败");
                    setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-alignment: CENTER;");
                }
            }
        });

        colResult.setCellValueFactory(data -> {
            BatchWatermarkService.FileResult r = data.getValue();
            String val = r.getOutputPath() != null ? new File(r.getOutputPath()).getName()
                    : r.getError() != null ? r.getError() : "";
            return new javafx.beans.property.SimpleStringProperty(val);
        });
        colResult.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setStyle("-fx-alignment: CENTER_LEFT;");
            }
        });

    }

    @FXML
    private void onChooseDir() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("选择图片目录");
        String lastDir = UserPreferences.getBatchSourceDir();
        if (lastDir != null) {
            File dir = new File(lastDir);
            if (dir.exists()) {
                dc.setInitialDirectory(dir);
            }
        }
        File dir = dc.showDialog(btnChooseDir.getScene().getWindow());
        if (dir != null) {
            txtSourceDir.setText(dir.getAbsolutePath());
            UserPreferences.saveBatchSourceDir(dir.getAbsolutePath());
        }
    }

    @FXML
    private void onChooseOutputDir() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("选择输出目录");
        String lastDir = UserPreferences.getBatchOutputDir();
        if (lastDir != null) {
            File dir = new File(lastDir);
            if (dir.exists()) {
                dc.setInitialDirectory(dir);
            }
        }
        File dir = dc.showDialog(btnChooseOutputDir.getScene().getWindow());
        if (dir != null) {
            txtOutputDir.setText(dir.getAbsolutePath());
            UserPreferences.saveBatchOutputDir(dir.getAbsolutePath());
        }
    }

    @FXML
    private void onStart() {
        if (!activationService.isActivated()) {
            showWarning("试用模式不支持批量处理，请先激活软件");
            return;
        }

        String sourceDir = txtSourceDir.getText();
        if (sourceDir == null || sourceDir.isBlank()) {
            showWarning("请选择源文件目录");
            return;
        }

        if (txtWatermarkText.getText() == null || txtWatermarkText.getText().isBlank()) {
            showWarning("请输入水印文本");
            return;
        }

        String outputDirRaw = txtOutputDir.getText();
        final String outputDir = (outputDirRaw != null && !outputDirRaw.isBlank()) ? outputDirRaw : null;
        String passwordRaw = txtPassword.getText();
        final String password = (passwordRaw != null && !passwordRaw.isEmpty()) ? passwordRaw : null;
        final String watermarkText = txtWatermarkText.getText();
        final int strength = (int) sliderStrength.getValue();

        btnStart.setDisable(true);
        progressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        lblStatus.setText("正在处理...");
        resultTable.getItems().clear();

        new Thread(() -> {
            try {
                BatchWatermarkService.BatchResult result =
                        batchService.batchEmbed(sourceDir, outputDir, watermarkText, strength, password);

                final var finalResult = result;
                ObservableList<BatchWatermarkService.FileResult> items = FXCollections.observableArrayList(finalResult.getResults());
                Platform.runLater(() -> {
                    resultTable.setItems(items);
                    progressBar.setProgress(1.0);
                    lblStatus.setText("处理完成");
                    String elapsed = DateUtil.formatBetween(finalResult.getElapsedMs(), BetweenFormatter.Level.SECOND);
                    lblSummary.setText(String.format("总计: %d  |  成功: %d  |  失败: %d  |  耗时: %s",
                            finalResult.getTotal(), finalResult.getSuccess(), finalResult.getFailed(), elapsed));
                });
            } catch (Exception e) {
                AppLogger.error("批量嵌入失败", e);
                Platform.runLater(() -> {
                    progressBar.setProgress(0);
                    lblStatus.setText("处理失败: " + e.getMessage());
                    showWarning("批量嵌入失败: " + e.getMessage());
                });
            } finally {
                Platform.runLater(() -> btnStart.setDisable(false));
            }
        }).start();
    }

    private void applyTextLimit(TextField field, int maxLen) {
        field.setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().length() <= maxLen ? change : null));
    }

    private void showWarning(String message) {
        CustomDialog.show(CustomDialog.Type.WARNING, message);
    }
}
