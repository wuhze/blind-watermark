package com.blindwatermark.controller;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import com.blindwatermark.config.AppProperties;
import com.blindwatermark.service.ActivationService;
import com.blindwatermark.util.KokuhuiUtil;
import com.blindwatermark.util.UserPreferences;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.shape.*;
import javafx.stage.Stage;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.time.Year;
import java.util.*;

@Component
public class MainController implements Initializable {

    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");
    private static final double BORDER = 6.0;

    private static final String THEME_NIGHT = "夜间";
    private static final String THEME_DAY = "白日";
    private static final String THEME_EYECARE = "护眼";
    private static final String THEME_SAKURA = "汇崽";
    private static final String THEME_CSS_NIGHT = "/css/theme-night.css";
    private static final String THEME_CSS_EYECARE = "/css/theme-eyecare.css";
    private static final String THEME_CSS_SAKURA = "/css/theme-sakura.css";

    private final ActivationService activationService;
    private final AppProperties appProperties;

    @FXML private StackPane rootPane;
    @FXML private BorderPane mainPane;
    @FXML private VBox sidebarBox;
    @FXML private VBox contentBox;
    @FXML private HBox topBar;
    @FXML private Label statusLabel;
    @FXML private Button btnEmbed;
    @FXML private Button btnExtract;
    @FXML private Button btnBatch;
    @FXML private Button btnActivation;
    @FXML private StackPane contentArea;
    @FXML private StackPane catLogoContainer;
    @FXML private Button btnMinimize;
    @FXML private Button btnMaximize;
    @FXML private Button btnClose;
    @FXML private Region maximizeIcon;
    @FXML private Hyperlink linkXuanling;
    @FXML private Hyperlink linkCopyrightAuthor;
    @FXML private Label appTitleLabel;
    @FXML private Label copyrightLabel;
    @FXML private Label copyrightSuffixLabel;
    @FXML private Label versionLabel;
    @FXML private Label holidayLabel;
    @FXML private ComboBox<String> themeComboBox;

    private double dragOffsetX;
    private double dragOffsetY;
    private boolean sidebarVisible = true;
    private final Map<String, Parent> pageCache = new java.util.HashMap<>();

    private boolean resizing = false;
    private double resizeStartX, resizeStartY;
    private double resizeStartW, resizeStartH, resizeStartStageX, resizeStartStageY;
    private int resizeEdge = 0;

    public MainController(ActivationService activationService, AppProperties appProperties) {
        this.activationService = activationService;
        this.appProperties = appProperties;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (appTitleLabel != null && appProperties.getAppTitle() != null) {
            String title = appProperties.getAppTitle();
            appTitleLabel.setText(title);
            if (KokuhuiUtil.hui(title)) {
                appTitleLabel.setStyle("-fx-text-fill: linear-gradient(from 0% 0% to 100% 0%, #FF69B4, #87CEEB);");
            }
        }
        if (copyrightLabel != null) {
            int startYear = appProperties.getCopyrightStartYear();
            int currentYear = Year.now().getValue();
            String yearText = startYear >= currentYear ? String.valueOf(startYear) : startYear + "~" + currentYear;
            copyrightLabel.setText("Copyright © " + yearText + " ");
        }
        if (linkCopyrightAuthor != null && appProperties.getCopyrightAuthor() != null) {
            linkCopyrightAuthor.setText(appProperties.getCopyrightAuthor());
        }
        if (versionLabel != null && appProperties.getAppVersion() != null) {
            versionLabel.setText(appProperties.getAppVersion());
        }
        buildCatLogo();
        initThemeSelector();
        rootPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                String savedTheme = UserPreferences.getLastTheme();
                if (savedTheme != null) {
                    applyTheme(savedTheme);
                }
            }
        });
        switchPage("embed");
        updateStatusBar();
        showHolidayGreeting();
    }

    @FXML
    private void onEmbedClick() { switchPage("embed"); }

    @FXML
    private void onExtractClick() { switchPage("extract"); }

    @FXML
    private void onBatchClick() { switchPage("batch"); }

    @FXML
    private void onActivationClick() { switchPage("activation"); }

    @FXML
    private void onShowSidebar() {
        sidebarVisible = !sidebarVisible;
        sidebarBox.setVisible(sidebarVisible);
        sidebarBox.setManaged(sidebarVisible);
    }

    @FXML
    private void onTopBarPress(MouseEvent event) {
        Stage stage = getStage();
        if (stage == null || event.getButton() != MouseButton.PRIMARY) return;
        dragOffsetX = event.getScreenX() - stage.getX();
        dragOffsetY = event.getScreenY() - stage.getY();
    }

    @FXML
    private void onTopBarDrag(MouseEvent event) {
        Stage stage = getStage();
        if (stage == null || event.getButton() != MouseButton.PRIMARY) return;
        if (stage.isMaximized()) {
            double ratio = dragOffsetX / stage.getWidth();
            stage.setMaximized(false);
            dragOffsetX = stage.getWidth() * ratio;
            dragOffsetY = event.getY();
            updateMaximizeIcon(false);
        }
        stage.setX(event.getScreenX() - dragOffsetX);
        stage.setY(event.getScreenY() - dragOffsetY);
    }

    @FXML
    private void onTopBarMove(MouseEvent event) {
        if (rootPane != null) rootPane.setCursor(Cursor.DEFAULT);
    }

    @FXML
    private void onTopBarDoubleClick(MouseEvent event) {
        if (event.getClickCount() == 2 && event.getButton() == MouseButton.PRIMARY) {
            onMaximize();
        }
    }

    @FXML
    private void onMinimize() {
        Stage stage = getStage();
        if (stage != null) stage.setIconified(true);
    }

    @FXML
    private void onMaximize() {
        Stage stage = getStage();
        if (stage == null) return;
        boolean wasMax = stage.isMaximized();
        stage.setMaximized(!wasMax);
        updateMaximizeIcon(!wasMax);
    }

    @FXML
    private void onClose() {
        Stage stage = getStage();
        if (stage != null) stage.close();
    }

    @FXML
    private void onXuanlingClick() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("cmd", "/c", "start", "https://gitee.com/wuhze").start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", "https://gitee.com/wuhze").start();
            } else {
                new ProcessBuilder("xdg-open", "https://gitee.com/wuhze").start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateMaximizeIcon(boolean maximized) {
        if (maximizeIcon == null) return;
        maximizeIcon.getStyleClass().removeAll("icon-maximize", "icon-restore");
        maximizeIcon.getStyleClass().add(maximized ? "icon-restore" : "icon-maximize");
    }

    // ---- Edge resize ----

    @FXML
    private void onRootMouseMove(MouseEvent event) {
        if (rootPane == null) return;
        Stage stage = getStage();
        if (stage == null || stage.isMaximized() || resizing) {
            rootPane.setCursor(Cursor.DEFAULT);
            return;
        }
        rootPane.setCursor(getEdgeCursor(event));
    }

    @FXML
    private void onRootMousePress(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) return;
        Stage stage = getStage();
        if (stage == null || stage.isMaximized()) return;
        int edge = getEdge(event);
        if (edge == 0) return;
        resizing = true;
        resizeEdge = edge;
        resizeStartX = event.getScreenX();
        resizeStartY = event.getScreenY();
        resizeStartW = stage.getWidth();
        resizeStartH = stage.getHeight();
        resizeStartStageX = stage.getX();
        resizeStartStageY = stage.getY();
        event.consume();
    }

    @FXML
    private void onRootMouseDrag(MouseEvent event) {
        if (!resizing) return;
        Stage stage = getStage();
        if (stage == null) return;
        double dx = event.getScreenX() - resizeStartX;
        double dy = event.getScreenY() - resizeStartY;
        double minW = stage.getMinWidth();
        double minH = stage.getMinHeight();
        if ((resizeEdge & 2) != 0) {
            stage.setWidth(Math.max(minW, resizeStartW + dx));
        }
        if ((resizeEdge & 8) != 0) {
            stage.setHeight(Math.max(minH, resizeStartH + dy));
        }
        if ((resizeEdge & 1) != 0) {
            double newW = Math.max(minW, resizeStartW - dx);
            if (newW > minW) {
                stage.setX(resizeStartStageX + resizeStartW - newW);
                stage.setWidth(newW);
            }
        }
        if ((resizeEdge & 4) != 0) {
            double newH = Math.max(minH, resizeStartH - dy);
            if (newH > minH) {
                stage.setY(resizeStartStageY + resizeStartH - newH);
                stage.setHeight(newH);
            }
        }
        event.consume();
    }

    @FXML
    private void onRootMouseRelease(MouseEvent event) {
        if (resizing) {
            resizing = false;
            resizeEdge = 0;
            if (rootPane != null) rootPane.setCursor(Cursor.DEFAULT);
            event.consume();
        }
    }

    private int getEdge(MouseEvent e) {
        if (rootPane == null) return 0;
        double w = rootPane.getWidth();
        double h = rootPane.getHeight();
        double x = e.getX();
        double y = e.getY();
        boolean left = x < BORDER;
        boolean right = x > w - BORDER;
        boolean top = y < BORDER;
        boolean bottom = y > h - BORDER;
        if (!left && !right && !top && !bottom) return 0;
        int edge = 0;
        if (left) edge |= 1;
        if (right) edge |= 2;
        if (top) edge |= 4;
        if (bottom) edge |= 8;
        return edge;
    }

    private Cursor getEdgeCursor(MouseEvent e) {
        int edge = getEdge(e);
        return switch (edge) {
            case 1, 2 -> Cursor.H_RESIZE;
            case 4, 8 -> Cursor.V_RESIZE;
            case 5 -> Cursor.NW_RESIZE;
            case 6 -> Cursor.NE_RESIZE;
            case 9 -> Cursor.SW_RESIZE;
            case 10 -> Cursor.SE_RESIZE;
            default -> Cursor.DEFAULT;
        };
    }

    private Stage getStage() {
        if (rootPane == null || rootPane.getScene() == null) return null;
        return (Stage) rootPane.getScene().getWindow();
    }

    private void initThemeSelector() {
        if (themeComboBox == null) return;

        boolean showSakura = KokuhuiUtil.hui(appProperties.getAppTitle());
        List<String> themes = showSakura
                ? List.of(THEME_DAY, THEME_NIGHT, THEME_EYECARE, THEME_SAKURA)
                : List.of(THEME_DAY, THEME_NIGHT, THEME_EYECARE);

        themeComboBox.setItems(FXCollections.observableArrayList(themes));
        themeComboBox.valueProperty().addListener((obs, oldVal, newVal) -> applyTheme(newVal));

        String savedTheme = UserPreferences.getLastTheme();
        if (savedTheme != null && themes.contains(savedTheme)) {
            themeComboBox.setValue(savedTheme);
        } else {
            themeComboBox.setValue(THEME_DAY);
        }
    }

    private void applyTheme(String themeName) {
        Scene scene = rootPane == null ? null : rootPane.getScene();
        if (scene == null) return;

        UserPreferences.saveLastTheme(themeName);

        List<String> stylesheets = scene.getStylesheets();
        stylesheets.removeIf(s -> s.contains("theme-night.css")
                || s.contains("theme-eyecare.css")
                || s.contains("theme-sakura.css"));

        if (THEME_NIGHT.equals(themeName)) {
            Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
            try {
                String url = getClass().getResource(THEME_CSS_NIGHT).toExternalForm();
                if (!stylesheets.contains(url)) stylesheets.add(url);
            } catch (Exception ignored) {}
        } else {
            Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
            String res = switch (themeName) {
                case THEME_EYECARE -> THEME_CSS_EYECARE;
                case THEME_SAKURA -> THEME_CSS_SAKURA;
                default -> null;
            };
            if (res != null) {
                try {
                    String url = getClass().getResource(res).toExternalForm();
                    if (!stylesheets.contains(url)) stylesheets.add(url);
                } catch (Exception ignored) {}
            }
        }
    }

    private void buildCatLogo() {
        if (catLogoContainer == null) return;

        Image img = new Image(getClass().getResourceAsStream("/icons/cat.jpg"));
        ImageView iv = new ImageView(img);
        iv.setFitWidth(36);
        iv.setFitHeight(36);
        iv.setPreserveRatio(false);

        Rectangle clip = new Rectangle(36, 36);
        clip.setArcWidth(10);
        clip.setArcHeight(10);
        iv.setClip(clip);

        catLogoContainer.getChildren().add(iv);
    }

    private static final java.util.Set<String> NO_CACHE_PAGES = java.util.Set.of("embed", "extract", "batch");

    private void switchPage(String pageName) {
        contentArea.getChildren().removeIf(node -> !pageCache.containsValue(node));
        contentArea.getChildren().forEach(node -> node.setVisible(false));

        boolean noCache = NO_CACHE_PAGES.contains(pageName);
        Parent page = noCache ? null : pageCache.get(pageName);
        if (page == null) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/" + pageName + ".fxml"));
                loader.setControllerFactory(com.blindwatermark.SpringContextHolder::getBean);
                page = loader.load();
                contentArea.getChildren().add(page);
                if (!noCache) {
                    pageCache.put(pageName, page);
                }
            } catch (Exception e) {
                e.printStackTrace();
                Throwable cause = e;
                while (cause.getCause() != null) {
                    cause = cause.getCause();
                }
                showError("页面加载失败: " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
                return;
            }
        }
        page.setVisible(true);

        btnEmbed.pseudoClassStateChanged(ACTIVE, false);
        btnExtract.pseudoClassStateChanged(ACTIVE, false);
        btnBatch.pseudoClassStateChanged(ACTIVE, false);
        btnActivation.pseudoClassStateChanged(ACTIVE, false);

        switch (pageName) {
            case "embed" -> btnEmbed.pseudoClassStateChanged(ACTIVE, true);
            case "extract" -> btnExtract.pseudoClassStateChanged(ACTIVE, true);
            case "batch" -> btnBatch.pseudoClassStateChanged(ACTIVE, true);
            case "activation" -> btnActivation.pseudoClassStateChanged(ACTIVE, true);
        }
    }

    private void updateStatusBar() {
        if (activationService.isActivated()) {
            String title = appProperties.getAppTitle();
            if (KokuhuiUtil.hui(title)) {
                statusLabel.setText("汇崽版");
                statusLabel.setStyle("-fx-text-fill: linear-gradient(from 0% 0% to 100% 0%, #FF69B4, #87CEEB); -fx-font-weight: bold;");
            } else {
                statusLabel.setText("正式版");
                statusLabel.getStyleClass().add("status-success");
            }
        } else {
            statusLabel.setText("免费版");
            statusLabel.getStyleClass().add("status-error");
        }
    }

    private void showHolidayGreeting() {
        String title = appProperties.getAppTitle();
        if (holidayLabel == null || KokuhuiUtil.noneGreeting(title)) return;
        holidayLabel.setText(KokuhuiUtil.randomGreeting());
        holidayLabel.setVisible(true);
        holidayLabel.setManaged(true);
    }

    private void showError(String message) {
        CustomDialog.show(CustomDialog.Type.ERROR, "错误", message);
    }
}
