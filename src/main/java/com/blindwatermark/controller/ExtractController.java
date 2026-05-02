package com.blindwatermark.controller;

import com.blindwatermark.common.AppLogger;
import com.blindwatermark.service.BlindWatermarkService;
import com.blindwatermark.service.FileService;
import com.blindwatermark.util.UserPreferences;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

@Component
public class ExtractController implements Initializable {

    private final BlindWatermarkService watermarkService;
    private final FileService fileService;

    @FXML private TextField txtPassword;
    @FXML private TextField txtSourcePath;
    @FXML private Button btnChooseSource;
    @FXML private Button btnExtract;
    @FXML private ProgressBar progressBar;
    @FXML private Label lblStatus;
    @FXML private Label lblResult;
    @FXML private TextArea txtResultText;

    private File sourceFile;

    public ExtractController(BlindWatermarkService watermarkService, FileService fileService) {
        this.watermarkService = watermarkService;
        this.fileService = fileService;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        txtPassword.setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().length() <= 64 ? change : null));
    }

    @FXML
    private void onChooseSource() {
        FileChooser fc = new FileChooser();
        fc.setTitle("选择含水印的图片");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("图片文件", "*.jpg", "*.jpeg", "*.png"));
        String lastDir = UserPreferences.getExtractSourceDir();
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
            UserPreferences.saveExtractSourceDir(file.getParentFile().getAbsolutePath());
        }
    }

    @FXML
    private void onExtract() {
        if (sourceFile == null) {
            showWarning("请先选择含水印的图片");
            return;
        }

        String passwordRaw = txtPassword.getText();
        final String password = (passwordRaw != null && !passwordRaw.isEmpty()) ? passwordRaw : null;

        progressBar.setProgress(0);
        btnExtract.setDisable(true);
        lblStatus.setText("正在提取水印...");
        lblResult.setText("");
        txtResultText.setText("");

        new Thread(() -> {
            try {
                BufferedImage image = fileService.readImage(sourceFile);

                String text = watermarkService.extractTextWatermark(image, password);
                if (text == null || text.isEmpty() || isGarbledText(text)) {
                    Platform.runLater(() -> {
                        lblStatus.setText("未检测到水印");
                        lblResult.setText("");
                        if (password != null && !password.isEmpty()) {
                            showWarning("未检测到有效水印，请检查密码是否正确");
                        } else {
                            showWarning("该图片未嵌入水印，或需要输入密码");
                        }
                    });
                } else {
                    Platform.runLater(() -> {
                        progressBar.setProgress(1.0);
                        lblStatus.setText("水印提取成功");
                        txtResultText.setText(text);
                        lblResult.setText("提取到的文字水印:");
                    });
                }
            } catch (Exception e) {
                AppLogger.error("提取水印失败", e);
                Platform.runLater(() -> {
                    progressBar.setProgress(0);
                    if (e.getMessage() != null && e.getMessage().contains("password")) {
                        lblStatus.setText("密码错误");
                        lblResult.setText("提取失败: 密码不正确，请使用嵌入时相同的密码");
                    } else {
                        lblStatus.setText("提取失败");
                        lblResult.setText("错误: " + e.getMessage());
                    }
                });
            } finally {
                Platform.runLater(() -> btnExtract.setDisable(false));
            }
        }).start();
    }

    private boolean isGarbledText(String text) {
        if (text.length() == 0) return true;
        int controlCount = 0;
        int nonPrintableCount = 0;
        for (char c : text.toCharArray()) {
            if (c < 0x20 && c != '\n' && c != '\r' && c != '\t') {
                controlCount++;
            }
            if ((c >= 0x7F && c <= 0x9F) || c == 0xFFFD) {
                nonPrintableCount++;
            }
        }
        double badRatio = (double) (controlCount + nonPrintableCount) / text.length();
        if (badRatio > 0.3) return true;
        long cjkCount = text.chars().filter(ch -> Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN).count();
        long alphaCount = text.chars().filter(Character::isLetter).count();
        long printableCount = text.chars().filter(ch -> ch >= 0x20 || ch == '\n' || ch == '\r' || ch == '\t').count();
        if (alphaCount + cjkCount == 0 && text.length() > 0) return true;
        if (printableCount < text.length() * 0.7) return true;
        return false;
    }

    private void showWarning(String message) {
        CustomDialog.show(CustomDialog.Type.WARNING, message);
    }
}
