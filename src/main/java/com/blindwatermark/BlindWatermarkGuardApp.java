package com.blindwatermark;

import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class BlindWatermarkGuardApp extends Application {

    private static File dataDir;
    private static File startupLog;
    private static File installDir;
    private static final String APP_NAME = "BlindWatermark";
    private ConfigurableApplicationContext springContext;

    public static void main(String[] args) {
        try {
            initDirectories();
        } catch (Throwable t) {
            showErrorDialog("初始化目录失败", t);
            return;
        }

        try {
            launch(args);
        } catch (Throwable t) {
            logStackTrace(t);
            showErrorDialog("应用启动失败", t);
        }
    }

    private static void initDirectories() {
        installDir = detectInstallDir();

        dataDir = detectDataDir();
        if (!dataDir.exists()) {
            boolean ok = dataDir.mkdirs();
            if (!ok && !dataDir.exists()) {
                File tempFallback = new File(System.getProperty("java.io.tmpdir"), APP_NAME);
                tempFallback.mkdirs();
                dataDir = tempFallback;
            }
        }

        System.setProperty("app.data.dir", dataDir.getAbsolutePath());
        startupLog = new File(dataDir, "startup.log");
        try {
            if (!startupLog.exists()) {
                startupLog.createNewFile();
            }
        } catch (IOException e) {
            File tempFallback = new File(System.getProperty("java.io.tmpdir"), APP_NAME);
            tempFallback.mkdirs();
            dataDir = tempFallback;
            System.setProperty("app.data.dir", dataDir.getAbsolutePath());
            startupLog = new File(dataDir, "startup.log");
        }
    }

    private static File detectInstallDir() {
        try {
            File codeLoc = new File(BlindWatermarkGuardApp.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return codeLoc.isFile() ? codeLoc.getParentFile().getParentFile() : codeLoc.getParentFile();
        } catch (Exception e) {
            return new File(System.getProperty("user.dir"));
        }
    }

    private static File detectDataDir() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isEmpty()) {
            return new File(appData, APP_NAME);
        }
        return new File(System.getProperty("user.home"), "." + APP_NAME.toLowerCase());
    }

    @Override
    public void init() {
        try {
            springContext = new SpringApplicationBuilder(BlindWatermarkGuardConfig.class)
                    .headless(true)
                    .run();
        } catch (Exception e) {
            logStackTrace(e);
            showErrorDialog("Spring容器初始化失败", e);
            throw e;
        }
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        try {
            Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
            primaryStage.initStyle(StageStyle.UNDECORATED);

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
            loader.setControllerFactory(springContext::getBean);
            Parent root = loader.load();

            Scene scene = new Scene(root, 1100, 720);
            scene.setFill(Color.TRANSPARENT);
            scene.getStylesheets().add(getClass().getResource("/css/theme.css").toExternalForm());

            primaryStage.setScene(scene);
            primaryStage.setMinWidth(900);
            primaryStage.setMinHeight(600);

            try {
                primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/icons/app.png")));
            } catch (Exception ignored) {
            }

            primaryStage.show();
        } catch (Exception e) {
            log("UI构建失败: " + e.getMessage());
            logStackTrace(e);
            throw e;
        }
    }

    @Override
    public void stop() {
        log("应用关闭");
        if (springContext != null) {
            springContext.close();
        }
    }

    public static void log(String msg) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
        String line = "[" + ts + "] " + msg;
        System.out.println(line);
        if (startupLog != null) {
            try {
                if (!startupLog.getParentFile().exists()) {
                    startupLog.getParentFile().mkdirs();
                }
                Files.write(startupLog.toPath(), (line + "\n").getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                System.err.println("日志写入失败: " + e.getMessage());
            }
        }
    }

    public static void logStackTrace(Throwable t) {
        log(stackTraceToString(t));
    }

    private static String stackTraceToString(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private static void showErrorDialog(String title, Throwable t) {
        try {
            javax.swing.JOptionPane.showMessageDialog(null,
                    title + "\n\n" + t.getMessage() + "\n\n详细日志请查看:\n" + startupLog,
                    APP_NAME, javax.swing.JOptionPane.ERROR_MESSAGE);
        } catch (Throwable ignored) {
        }
    }
}
