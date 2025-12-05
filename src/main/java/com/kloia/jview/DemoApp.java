package com.kloia.jview;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

/**
 * Demo JavaFX Application that uses KloiaConnectComponent
 *
 * This is a pure JavaFX application. The KloiaConnectComponent opens
 * its own Swing JFrame window (with JCEF + JFXPanel for UI).
 *
 * Architecture:
 * - DemoApp: Pure JavaFX (this file)
 * - KloiaConnectComponent: Swing JFrame + JFXPanel + JCEF (separate window)
 */
public class DemoApp extends Application {

    private KloiaConnectComponent connectComponent;
    private Label statusLabel;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Kloia Connect - JavaFX Demo");

        // Root layout
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #2C3E50;");

        // Header
        HBox header = createHeader();
        root.setTop(header);

        // Center content
        VBox centerContent = createCenterContent(primaryStage);
        root.setCenter(centerContent);

        // Footer with status
        HBox footer = createFooter();
        root.setBottom(footer);

        Scene scene = new Scene(root, 500, 400);
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);

        // Handle close - also close the component
        primaryStage.setOnCloseRequest(e -> {
            if (connectComponent != null) {
                connectComponent.dispose();
            }
            Platform.exit();
            System.exit(0);
        });

        primaryStage.show();
    }

    private HBox createHeader() {
        HBox header = new HBox(20);
        header.setPadding(new Insets(20, 25, 20, 25));
        header.setAlignment(Pos.CENTER);
        header.setStyle("-fx-background-color: #1A252F;");

        Label titleLabel = new Label("Kloia Connect");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 28));
        titleLabel.setTextFill(Color.WHITE);

        header.getChildren().add(titleLabel);
        return header;
    }

    private VBox createCenterContent(Stage primaryStage) {
        VBox content = new VBox(30);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(40));

        Label infoLabel = new Label("AWS Connect CCP Component Demo");
        infoLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 16));
        infoLabel.setTextFill(Color.web("#BDC3C7"));

        Label descLabel = new Label("Click the button below to open the\nAWS Connect Agent Desktop window.");
        descLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 14));
        descLabel.setTextFill(Color.web("#95A5A6"));
        descLabel.setWrapText(true);
        descLabel.setStyle("-fx-text-alignment: center;");

        // Launch button
        Button launchButton = createLaunchButton(primaryStage);

        content.getChildren().addAll(infoLabel, descLabel, launchButton);
        return content;
    }

    private Button createLaunchButton(Stage primaryStage) {
        Button button = new Button("Open AWS Connect");
        button.setPrefHeight(50);
        button.setPrefWidth(220);
        button.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        button.setTextFill(Color.WHITE);

        String bgColor = "#0073BB";
        String hoverColor = "#005A8E";
        String pressedColor = "#004A7A";

        String baseStyle = String.format(
            "-fx-background-color: %s; -fx-background-radius: 10; -fx-border-radius: 10; -fx-cursor: hand;",
            bgColor
        );

        button.setStyle(baseStyle);

        button.setOnMouseEntered(e -> button.setStyle(baseStyle.replace(bgColor, hoverColor)));
        button.setOnMouseExited(e -> button.setStyle(baseStyle));
        button.setOnMousePressed(e -> button.setStyle(baseStyle.replace(bgColor, pressedColor)));

        DropShadow shadow = new DropShadow();
        shadow.setRadius(8);
        shadow.setOffsetY(3);
        shadow.setColor(Color.rgb(0, 0, 0, 0.4));
        button.setEffect(shadow);

        button.setOnAction(e -> {
            button.setDisable(true);
            button.setText("Opening...");
            updateStatus("Launching component...");

            // Create and show the component
            connectComponent = new KloiaConnectComponent();
            connectComponent.setCcpVisible(true); // Show CCP browser

            // Set callbacks
            connectComponent.setOnReady(() -> {
                Platform.runLater(() -> updateStatus("Component ready!"));
            });

            connectComponent.setOnAgentStateChange(state -> {
                Platform.runLater(() -> updateStatus("Agent: " + state));
            });

            connectComponent.setOnIncomingCall(callData -> {
                Platform.runLater(() -> updateStatus("Incoming call!"));
            });

            connectComponent.setOnCallConnected(callData -> {
                Platform.runLater(() -> updateStatus("Call connected"));
            });

            connectComponent.setOnCallEnded(callData -> {
                Platform.runLater(() -> updateStatus("Call ended"));
            });

            connectComponent.setOnClose(() -> {
                Platform.runLater(() -> {
                    button.setDisable(false);
                    button.setText("Open AWS Connect");
                    updateStatus("Component closed");
                    connectComponent = null;
                });
            });

            // Show the component window
            connectComponent.show();

            Platform.runLater(() -> {
                button.setText("AWS Connect Running");
                updateStatus("Component window opened");
            });
        });

        return button;
    }

    private HBox createFooter() {
        HBox footer = new HBox(20);
        footer.setPadding(new Insets(15, 25, 15, 25));
        footer.setAlignment(Pos.CENTER);
        footer.setStyle("-fx-background-color: #1A252F;");

        statusLabel = new Label("Status: Ready");
        statusLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 12));
        statusLabel.setTextFill(Color.web("#7F8C8D"));

        footer.getChildren().add(statusLabel);
        return footer;
    }

    private void updateStatus(String status) {
        if (statusLabel != null) {
            statusLabel.setText("Status: " + status);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
