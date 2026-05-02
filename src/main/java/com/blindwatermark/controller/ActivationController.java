package com.blindwatermark.controller;

import com.blindwatermark.common.AppLogger;
import com.blindwatermark.config.AppProperties;
import com.blindwatermark.service.ActivationService;
import com.blindwatermark.util.KokuhuiUtil;
import com.blindwatermark.service.ActivationService.ActivationInfo;
import com.blindwatermark.util.MachineCodeUtil;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.stage.Popup;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * 激活管理页面控制器
 * 仅负责客户端激活，授权码管理在服务端Web后台完成
 *
 * 在线激活: 用户输入授权码 -> 调用远程API验证 -> 成功即写入本地
 * 离线激活: 用户提供机器码给管理员 -> 管理员绑定后 -> 客户端输入授权码验证
 */
@Component
public class ActivationController implements Initializable {

    private final ActivationService activationService;
    private final AppProperties appProperties;

    @FXML private Label lblMachineCode;
    @FXML private Button btnCopyMachineCode;
    @FXML private Label lblActivationStatus;
    @FXML private Label lblLicenseKey;
    @FXML private Label lblLicenseType;
    @FXML private Label lblExpireTime;

    @FXML private TextField txtOnlineLicenseKey;
    @FXML private Button btnOnlineActivate;

    @FXML private TextField txtOfflineLicenseKey;
    @FXML private Label lblOfflineMachineCode;
    @FXML private Button btnOfflineActivate;
    @FXML private TitledPane activationPane;
    @FXML private HBox copyrightBox;
    @FXML private Label lblAuthor;
    @FXML private Label lblVersion;

    public ActivationController(ActivationService activationService, AppProperties appProperties) {
        this.activationService = activationService;
        this.appProperties = appProperties;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        String machineCode = MachineCodeUtil.generate();
        lblMachineCode.setText(machineCode);
        lblOfflineMachineCode.setText(machineCode);
        refreshActivationStatus();
        initCopyright();
    }

    @FXML
    private void onCopyMachineCode() {
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
        cc.putString(lblMachineCode.getText());
        clipboard.setContent(cc);
        showInfo("机器码已复制到剪贴板");
    }

    @FXML
    private void onOnlineActivate() {
        String key = txtOnlineLicenseKey.getText();
        if (key == null || key.isBlank()) {
            showWarning("请输入授权码");
            return;
        }

        btnOnlineActivate.setDisable(true);
        new Thread(() -> {
            try {
                activationService.activateOnline(key.trim());
                javafx.application.Platform.runLater(() -> {
                    showInfo("在线激活成功！");
                    refreshActivationStatus();
                });
            } catch (Exception e) {
                AppLogger.error("在线激活失败", e);
                javafx.application.Platform.runLater(() ->
                        showWarning("激活失败: " + e.getMessage()));
            } finally {
                javafx.application.Platform.runLater(() -> btnOnlineActivate.setDisable(false));
            }
        }).start();
    }

    @FXML
    private void onOfflineActivate() {
        String key = txtOfflineLicenseKey.getText();
        if (key == null || key.isBlank()) {
            showWarning("请输入授权码");
            return;
        }

        String machineCode = lblOfflineMachineCode.getText();
        btnOfflineActivate.setDisable(true);

        new Thread(() -> {
            try {
                activationService.activateOffline(key.trim(), machineCode);
                javafx.application.Platform.runLater(() -> {
                    showInfo("离线激活成功！");
                    refreshActivationStatus();
                });
            } catch (Exception e) {
                AppLogger.error("离线激活失败", e);
                javafx.application.Platform.runLater(() ->
                        showWarning("激活失败: " + e.getMessage()));
            } finally {
                javafx.application.Platform.runLater(() -> btnOfflineActivate.setDisable(false));
            }
        }).start();
    }

    private void refreshActivationStatus() {
        ActivationInfo info = activationService.getActivationInfo();
        if (info.isActivated()) {
            lblActivationStatus.setText("已激活");
            lblActivationStatus.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
            lblLicenseKey.setText(info.getLicenseKey() != null ? info.getLicenseKey() : "-");
            lblLicenseType.setText("ONLINE".equals(info.getLicenseType()) ? "在线授权" : "离线授权");
            lblExpireTime.setText(info.getExpireTime() != null && !info.getExpireTime().isEmpty()
                    ? info.getExpireTime() : "永不过期");
            activationPane.setVisible(false);
            activationPane.setManaged(false);
        } else {
            lblActivationStatus.setText("未激活 (试用模式)");
            lblActivationStatus.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
            lblLicenseKey.setText("-");
            lblLicenseType.setText("-");
            lblExpireTime.setText("-");
            activationPane.setVisible(true);
            activationPane.setManaged(true);
        }
    }

    private void initCopyright() {
        String title = appProperties.getAppTitle();

        Label prefixLabel = new Label("隐水印工具 | ");
        prefixLabel.setStyle("-fx-font-size: 17px; -fx-font-weight: bold;");

        if (KokuhuiUtil.hui(title)) {
            Hyperlink kokuhuiLink = new Hyperlink(title);
            kokuhuiLink.setStyle("-fx-font-size: 17px; -fx-font-weight: bold;"
                    + "-fx-text-fill: linear-gradient(from 0% 0% to 100% 0%, #FF69B4, #87CEEB);"
                    + "-fx-border-color: transparent; -fx-padding: 0;"
                    + "-fx-cursor: hand; -fx-underline: false;");

            Popup popup = new Popup();
            popup.setAutoHide(false);
            Label extraLabel = new Label(" n ss!w !");
            extraLabel.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-background-color: transparent;"
                    + "-fx-text-fill: linear-gradient(from 0% 0% to 100% 0%,"
                    + "#FF0000, #FF7F00, #FFFF00, #00FF00, #00BFFF, #8B00FF, #FF00FF);");
            popup.getContent().add(extraLabel);

            HBox titleBox = new HBox(prefixLabel, kokuhuiLink);
            titleBox.setAlignment(Pos.CENTER);
            copyrightBox.getChildren().add(titleBox);

            kokuhuiLink.setOnMouseEntered(e -> {
                javafx.geometry.Bounds bounds = kokuhuiLink.localToScreen(kokuhuiLink.getBoundsInLocal());
                if (bounds != null) {
                    popup.show(kokuhuiLink.getScene().getWindow(), bounds.getMaxX() + 2, bounds.getMinY());
                }
            });
            kokuhuiLink.setOnMouseExited(e -> popup.hide());
        } else {
            Label titleLabel = new Label(title);
            titleLabel.setStyle("-fx-font-size: 17px; -fx-font-weight: bold;");

            HBox titleBox = new HBox(prefixLabel, titleLabel);
            titleBox.setAlignment(Pos.CENTER);
            copyrightBox.getChildren().add(titleBox);
        }

        lblAuthor.setText("作者: " + appProperties.getCopyrightAuthor());
        lblVersion.setText("版本: " + appProperties.getAppVersion());
    }

    private void showWarning(String message) {
        CustomDialog.show(CustomDialog.Type.WARNING, message);
    }

    private void showInfo(String message) {
        CustomDialog.show(CustomDialog.Type.INFO, message);
    }
}
