package com.blindwatermark.controller;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

public class CustomDialog {

    public enum Type { INFO, WARNING, ERROR }

    private static final String INFO_ICON =
            "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z";
    private static final String WARNING_ICON =
            "M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z";
    private static final String ERROR_ICON =
            "M12 2C6.47 2 2 6.47 2 12s4.47 10 10 10 10-4.47 10-10S17.53 2 12 2zm5 13.59L15.59 17 12 13.41 8.41 17 7 15.59 10.59 12 7 8.41 8.41 7 12 10.59 15.59 7 17 8.41 13.41 12 17 15.59z";

    public static void show(Type type, String message) {
        show(type, null, message);
    }

    public static void show(Type type, String title, String message) {
        show(type, title, message, null);
    }

    public static void show(Type type, String title, String message, Window owner) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.TRANSPARENT);
        if (owner != null) {
            dialog.initOwner(owner);
        }

        String accentColor;
        String iconPath;
        String resolvedTitle;
        switch (type) {
            case WARNING -> {
                accentColor = "#f0a020";
                iconPath = WARNING_ICON;
                resolvedTitle = title != null ? title : "提示";
            }
            case ERROR -> {
                accentColor = "#c42b1c";
                iconPath = ERROR_ICON;
                resolvedTitle = title != null ? title : "错误";
            }
            default -> {
                accentColor = "#2da44e";
                iconPath = INFO_ICON;
                resolvedTitle = title != null ? title : "提示";
            }
        }

        SVGPath iconShape = new SVGPath();
        iconShape.setContent(iconPath);
        iconShape.setFill(Color.web(accentColor));
        iconShape.setScaleX(0.9);
        iconShape.setScaleY(0.9);

        Label titleLabel = new Label(resolvedTitle);
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: -color-fg-default;");

        Label msgLabel = new Label(message);
        msgLabel.setWrapText(true);
        msgLabel.setMaxWidth(340);
        msgLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: -color-fg-muted;");

        Button closeBtn = createCloseButton(dialog);

        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);
        HBox titleBar = new HBox(titleLabel, titleSpacer, closeBtn);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setPadding(new Insets(14, 8, 14, 18));
        titleBar.setStyle("-fx-background-color: -color-bg-default; -fx-background-radius: 10 10 0 0;");
        makeDraggable(titleBar, dialog);

        Region accentLine = new Region();
        accentLine.setPrefHeight(3);
        accentLine.setPrefWidth(400);
        accentLine.setStyle(
                "-fx-background-color: " + accentColor + ";" +
                "-fx-background-radius: 10 10 0 0;");

        Button okBtn = new Button("确定");
        okBtn.setPrefWidth(80);
        okBtn.setDefaultButton(true);
        okBtn.getStyleClass().add("primary-button");
        okBtn.setOnAction(e -> dialog.close());

        HBox footer = new HBox(okBtn);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(0, 24, 20, 24));

        HBox body = new HBox(10, iconShape, msgLabel);
        body.setAlignment(Pos.TOP_LEFT);
        body.setPadding(new Insets(16, 24, 20, 24));

        VBox contentBox = new VBox(accentLine, titleBar, body, footer);
        contentBox.setPrefWidth(400);
        contentBox.setStyle(
                "-fx-background-color: -color-bg-default;" +
                "-fx-background-radius: 10;" +
                "-fx-border-radius: 10;" +
                "-fx-border-color: -color-border-muted;" +
                "-fx-border-width: 1;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 24, 0.06, 0, 6);");

        StackPane wrapper = new StackPane(contentBox);
        wrapper.setPadding(new Insets(20));
        wrapper.setStyle("-fx-background-color: transparent;");

        Scene scene = new Scene(wrapper);
        scene.setFill(Color.TRANSPARENT);
        dialog.setScene(scene);

        if (owner != null) {
            dialog.setOnShown(e -> {
                dialog.setX(owner.getX() + (owner.getWidth() - dialog.getWidth()) / 2);
                dialog.setY(owner.getY() + (owner.getHeight() - dialog.getHeight()) / 2);
            });
        }

        dialog.showAndWait();
    }

    private static Button createCloseButton(Stage dialog) {
        Button btn = new Button();
        SVGPath closeIcon = new SVGPath();
        closeIcon.setContent("M18 6L6 18M6 6l12 12");
        closeIcon.setStroke(Color.web("#888"));
        closeIcon.setStrokeWidth(1.5);
        closeIcon.setFill(Color.TRANSPARENT);
        btn.setGraphic(closeIcon);
        btn.setStyle(
                "-fx-background-color: transparent; -fx-padding: 4 6 4 6; -fx-cursor: hand;");
        btn.setOnMouseEntered(e ->
                btn.setStyle("-fx-background-color: -color-neutral-subtle; -fx-padding: 4 6 4 6; -fx-cursor: hand;"));
        btn.setOnMouseExited(e ->
                btn.setStyle("-fx-background-color: transparent; -fx-padding: 4 6 4 6; -fx-cursor: hand;"));
        btn.setOnAction(e -> dialog.close());
        return btn;
    }

    private static void makeDraggable(HBox titleBar, Stage stage) {
        final double[] offset = new double[2];
        titleBar.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                offset[0] = e.getScreenX() - stage.getX();
                offset[1] = e.getScreenY() - stage.getY();
            }
        });
        titleBar.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                stage.setX(e.getScreenX() - offset[0]);
                stage.setY(e.getScreenY() - offset[1]);
            }
        });
    }
}
