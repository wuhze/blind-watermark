package com.blindwatermark.controller;

import com.blindwatermark.common.AppLogger;
import com.blindwatermark.common.BusinessException;
import com.blindwatermark.config.AppProperties;
import com.blindwatermark.service.ActivationService;
import com.blindwatermark.service.BlindWatermarkService;
import com.blindwatermark.service.FileService;
import com.blindwatermark.util.UserPreferences;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * 嵌入水印页面控制器
 * 支持文字水印嵌入，支持密码保护
 * 未激活时限制: 图片尺寸不超过1920px
 */
@Component
public class EmbedController implements Initializable {

    private final BlindWatermarkService watermarkService;
    private final FileService fileService;
    private final ActivationService activationService;
    private final AppProperties appProperties;

    @FXML private TextField txtWatermarkText;
    @FXML private TextField txtPassword;
    @FXML private Slider sliderStrength;
    @FXML private Label lblStrengthValue;
    @FXML private TextField txtSourcePath;
    @FXML private Button btnChooseSource;
    @FXML private TextField txtOutputPath;
    @FXML private Button btnChooseOutput;
    @FXML private Label lblOutputPath;
    @FXML private Button btnEmbed;
    @FXML private ProgressBar progressBar;
    @FXML private Label lblStatus;
    @FXML private ImageView previewImageView;

    private File sourceFile;
    private File customOutputDir;

    public EmbedController(BlindWatermarkService watermarkService, FileService fileService,
                           ActivationService activationService,
                           AppProperties appProperties) {
        this.watermarkService = watermarkService;
        this.fileService = fileService;
        this.activationService = activationService;
        this.appProperties = appProperties;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        sliderStrength.valueProperty().addListener((obs, oldVal, newVal) -> {
            lblStrengthValue.setText(String.valueOf(newVal.intValue()));
        });
        sliderStrength.setValue(50);

        applyTextLimit(txtWatermarkText, 200);
        applyTextLimit(txtPassword, 64);
    }

    private void applyTextLimit(TextField field, int maxLen) {
        field.setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().length() <= maxLen ? change : null));
    }

    @FXML
    private void onChooseSource() {
        FileChooser fc = new FileChooser();
        fc.setTitle("选择载体图片");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("图片文件", "*.jpg", "*.jpeg", "*.png"));
        String lastDir = UserPreferences.getEmbedSourceDir();
        if (lastDir != null) {
            File dir = new File(lastDir);
            if (dir.exists()) {
                fc.setInitialDirectory(dir);
            }
        }
        File file = fc.showOpenDialog(btnChooseSource.getScene().getWindow());
        if (file != null) {
            sourceFile = file;
            txtSourcePath.setText(file.getAbsolutePath());
            UserPreferences.saveEmbedSourceDir(file.getParentFile().getAbsolutePath());
            try {
                Image fxImage = new Image(file.toURI().toString());
                previewImageView.setImage(fxImage);
            } catch (Exception e) {
                AppLogger.warn("预览加载失败: {}", e.getMessage());
            }
        }
    }

    @FXML
    private void onChooseOutputPath() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("选择输出目录");
        String lastDir = UserPreferences.getEmbedOutputDir();
        if (lastDir != null) {
            File dir = new File(lastDir);
            if (dir.exists()) {
                dc.setInitialDirectory(dir);
            }
        }
        File dir = dc.showDialog(btnChooseOutput.getScene().getWindow());
        if (dir != null) {
            customOutputDir = dir;
            txtOutputPath.setText(dir.getAbsolutePath());
            UserPreferences.saveEmbedOutputDir(dir.getAbsolutePath());
        }
    }

    @FXML
    private void onEmbed() {
        if (sourceFile == null) {
            showWarning("请先选择载体图片");
            return;
        }

        if (txtWatermarkText.getText() == null || txtWatermarkText.getText().isBlank()) {
            showWarning("请输入水印文本");
            return;
        }

        int strength = (int) sliderStrength.getValue();
        String passwordRaw = txtPassword.getText();
        final String password = (passwordRaw != null && !passwordRaw.isEmpty()) ? passwordRaw : null;
        final String watermarkText = txtWatermarkText.getText();

        progressBar.setProgress(0);
        btnEmbed.setDisable(true);
        lblStatus.setText("正在嵌入水印...");

        new Thread(() -> {
            try {
                BufferedImage sourceImage = fileService.readImage(sourceFile);

                if (!activationService.isActivated()) {
                    if (sourceImage.getWidth() > 1920 || sourceImage.getHeight() > 1920) {
                        throw new BusinessException(403, "试用模式不支持超过1920px的图片，请激活后使用");
                    }
                }

                BufferedImage result = watermarkService.embedTextWatermark(sourceImage, watermarkText, strength, password);

                String outDir = customOutputDir != null ? customOutputDir.getAbsolutePath() : null;
                File outputFile = fileService.saveImageAs(result, sourceFile.getName(), "images", outDir);

                Platform.runLater(() -> {
                    progressBar.setProgress(1.0);
                    lblStatus.setText("水印嵌入成功！输出: " + outputFile.getName());
                    lblOutputPath.setText(outputFile.getAbsolutePath());
                });

            } catch (Exception e) {
                AppLogger.error("嵌入水印失败", e);
                Platform.runLater(() -> {
                    progressBar.setProgress(0);
                    lblStatus.setText("嵌入失败: " + e.getMessage());
                    showWarning("嵌入水印失败: " + e.getMessage());
                });
            } finally {
                Platform.runLater(() -> btnEmbed.setDisable(false));
            }
        }).start();
    }

    private void showWarning(String message) {
        CustomDialog.show(CustomDialog.Type.WARNING, message);
    }
}
