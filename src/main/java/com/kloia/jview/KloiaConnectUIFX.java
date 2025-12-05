package com.kloia.jview;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Pure JavaFX UI Component with embedded JCEF browser via SwingNode
 *
 * This version embeds the JCEF browser directly in the JavaFX layout using SwingNode.
 * Note: The browser area will appear as a black screen due to JCEF/SwingNode
 * rendering limitations, but it functions correctly in the background.
 *
 * Usage:
 * <pre>
 * KloiaConnectServiceFX service = new KloiaConnectServiceFX();
 * KloiaConnectUIFX ui = new KloiaConnectUIFX(service);
 *
 * // Add to your JavaFX layout
 * yourPane.getChildren().add(ui.getRoot());
 *
 * // Start the service
 * service.start(success -> {
 *     if (success) ui.setReady();
 * });
 * </pre>
 */
public class KloiaConnectUIFX {

    private final KloiaConnectServiceFX service;

    // Main container
    private BorderPane rootPane;

    // UI components
    private Button acceptButton;
    private Button rejectButton;
    private Button hangupButton;
    private Button stateToggleButton;

    // Customer card components
    private VBox customerCardPanel;
    private Label customerNameLabel;
    private Label customerPhoneLabel;

    // Status components
    private StackPane contentPane;
    private VBox loadingPane;
    private HBox mainContentPane;
    private Label statusLabel;

    // State
    private boolean isReady = false;

    public KloiaConnectUIFX(KloiaConnectServiceFX service) {
        this.service = service;
        createUI();
        bindServiceCallbacks();
    }

    /**
     * Get the root JavaFX Parent to embed in your application
     */
    public BorderPane getRoot() {
        return rootPane;
    }

    /**
     * Called when service is ready - switches from loading to main UI
     */
    public void setReady() {
        isReady = true;
        showMainContent();
        if (stateToggleButton != null) {
            stateToggleButton.setDisable(false);
        }
    }

    /**
     * Update status label
     */
    public void updateStatus(String status) {
        if (statusLabel != null) {
            statusLabel.setText(status);
        }
    }

    // ==================== UI CREATION ====================

    private void createUI() {
        rootPane = new BorderPane();
        rootPane.setStyle("-fx-background-color: #2C3E50;");
        rootPane.setPrefSize(900, 600);

        // Top panel with buttons
        HBox topPanel = createTopPanel();
        rootPane.setTop(topPanel);

        // Content pane (switches between loading and main content)
        contentPane = new StackPane();
        contentPane.setStyle("-fx-background-color: #2C3E50;");

        // Loading pane
        loadingPane = createLoadingPane();

        // Main content pane
        mainContentPane = createMainContent();

        // Show loading initially
        contentPane.getChildren().add(loadingPane);

        rootPane.setCenter(contentPane);

        // Bottom status bar
        HBox bottomBar = createBottomBar();
        rootPane.setBottom(bottomBar);
    }

    private HBox createTopPanel() {
        HBox topPanel = new HBox(15);
        topPanel.setPadding(new Insets(15, 20, 15, 20));
        topPanel.setAlignment(Pos.CENTER);
        topPanel.setStyle("-fx-background-color: #34495E;");

        acceptButton = createModernButton("Accept", "#2ECC71", "#27AE60", "#1E9650");
        acceptButton.setDisable(true);
        acceptButton.setOnAction(e -> service.acceptCall());

        rejectButton = createModernButton("Reject", "#E74C3C", "#C83C32", "#AA3228");
        rejectButton.setDisable(true);
        rejectButton.setOnAction(e -> service.rejectCall());

        hangupButton = createModernButton("Hang Up", "#C0392B", "#A02D23", "#82251D");
        hangupButton.setDisable(true);
        hangupButton.setOnAction(e -> service.hangupCall());

        stateToggleButton = createModernButton("Available", "#27AE60", "#1E9650", "#198246");
        stateToggleButton.setDisable(true);
        stateToggleButton.setOnAction(e -> {
            if ("Available".equalsIgnoreCase(service.getCurrentAgentState())) {
                service.setAgentOffline();
            } else {
                service.setAgentAvailable();
            }
        });

        Region separator = new Region();
        separator.setPrefWidth(2);
        separator.setPrefHeight(30);
        separator.setStyle("-fx-background-color: #5D6D7E;");

        topPanel.getChildren().addAll(acceptButton, rejectButton, hangupButton, separator, stateToggleButton);

        return topPanel;
    }

    private VBox createLoadingPane() {
        VBox loadingBox = new VBox(20);
        loadingBox.setAlignment(Pos.CENTER);
        loadingBox.setStyle("-fx-background-color: #2C3E50;");

        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(60, 60);

        Label loadingLabel = new Label("Connecting to AWS Connect...");
        loadingLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 16));
        loadingLabel.setTextFill(Color.WHITE);

        loadingBox.getChildren().addAll(progress, loadingLabel);
        return loadingBox;
    }

    private HBox createMainContent() {
        HBox content = new HBox(20);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(20));

        // Left side: Customer card + Dialpad
        VBox leftSide = new VBox(20);
        leftSide.setAlignment(Pos.TOP_CENTER);

        // Customer card
        customerCardPanel = createCustomerCard();

        // Dialpad
        VBox dialpad = createDialpad();

        leftSide.getChildren().addAll(customerCardPanel, dialpad);

        // Right side: JCEF Browser (SwingNode - will be black but functional)
        VBox browserContainer = new VBox(10);
        browserContainer.setAlignment(Pos.TOP_CENTER);

        Label browserLabel = new Label("AWS Connect CCP (Background)");
        browserLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        browserLabel.setTextFill(Color.WHITE);

        Label noteLabel = new Label("Browser runs in background - visual is black");
        noteLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 10));
        noteLabel.setTextFill(Color.web("#7F8C8D"));

        // Browser SwingNode wrapper
        StackPane browserWrapper = new StackPane();
        browserWrapper.setPrefSize(400, 400);
        browserWrapper.setMinSize(400, 400);
        browserWrapper.setStyle("-fx-background-color: #1A1A1A; -fx-border-color: #0073BB; -fx-border-width: 2; -fx-border-radius: 5;");

        // Add the SwingNode from service
        browserWrapper.getChildren().add(service.getBrowserNode());

        browserContainer.getChildren().addAll(browserLabel, noteLabel, browserWrapper);

        content.getChildren().addAll(leftSide, browserContainer);

        return content;
    }

    private VBox createCustomerCard() {
        VBox card = new VBox(15);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPadding(new Insets(20));
        card.setPrefSize(250, 180);
        card.setStyle("-fx-background-color: #34495E; -fx-background-radius: 10; -fx-border-color: #465C6E; -fx-border-radius: 10; -fx-border-width: 2;");

        DropShadow shadow = new DropShadow();
        shadow.setRadius(10);
        shadow.setOffsetY(3);
        shadow.setColor(Color.rgb(0, 0, 0, 0.3));
        card.setEffect(shadow);

        Label titleLabel = new Label("Customer Info");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        titleLabel.setTextFill(Color.WHITE);

        customerNameLabel = new Label("-");
        customerNameLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        customerNameLabel.setTextFill(Color.WHITE);

        customerPhoneLabel = new Label("-");
        customerPhoneLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 14));
        customerPhoneLabel.setTextFill(Color.web("#BDC3C7"));

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        card.getChildren().addAll(titleLabel, spacer, customerNameLabel, customerPhoneLabel);
        return card;
    }

    private VBox createDialpad() {
        VBox dialpad = new VBox(15);
        dialpad.setAlignment(Pos.TOP_CENTER);
        dialpad.setPadding(new Insets(15));
        dialpad.setStyle("-fx-background-color: #232F3E; -fx-background-radius: 10; -fx-border-color: #0073BB; -fx-border-radius: 10; -fx-border-width: 2;");

        DropShadow shadow = new DropShadow();
        shadow.setRadius(10);
        shadow.setOffsetY(3);
        shadow.setColor(Color.rgb(0, 0, 0, 0.3));
        dialpad.setEffect(shadow);

        Label titleLabel = new Label("Dial Pad");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        titleLabel.setTextFill(Color.WHITE);

        GridPane gridPane = new GridPane();
        gridPane.setHgap(6);
        gridPane.setVgap(6);
        gridPane.setAlignment(Pos.CENTER);

        String[][] buttons = {
            {"1", ""}, {"2", "ABC"}, {"3", "DEF"},
            {"4", "GHI"}, {"5", "JKL"}, {"6", "MNO"},
            {"7", "PQRS"}, {"8", "TUV"}, {"9", "WXYZ"},
            {"*", ""}, {"0", "+"}, {"#", ""}
        };

        int row = 0, col = 0;
        for (String[] btnData : buttons) {
            String digit = btnData[0];
            String letters = btnData[1];

            Button btn = createDialpadButton(digit, letters);
            btn.setOnAction(e -> service.sendDtmf(digit));

            gridPane.add(btn, col, row);
            col++;
            if (col > 2) { col = 0; row++; }
        }

        dialpad.getChildren().addAll(titleLabel, gridPane);
        return dialpad;
    }

    private HBox createBottomBar() {
        HBox bottomBar = new HBox(20);
        bottomBar.setPadding(new Insets(10, 20, 10, 20));
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.setStyle("-fx-background-color: #1A252F;");

        statusLabel = new Label("Initializing...");
        statusLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 12));
        statusLabel.setTextFill(Color.web("#7F8C8D"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label infoLabel = new Label("Kloia AWS Connect (Embedded FX)");
        infoLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 11));
        infoLabel.setTextFill(Color.web("#5D6D7E"));

        bottomBar.getChildren().addAll(statusLabel, spacer, infoLabel);
        return bottomBar;
    }

    private Button createModernButton(String text, String bgColor, String hoverColor, String pressedColor) {
        Button button = new Button(text);
        button.setPrefHeight(40);
        button.setMinWidth(100);
        button.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        button.setTextFill(Color.WHITE);

        String baseStyle = String.format(
            "-fx-background-color: %s; -fx-background-radius: 8; -fx-border-radius: 8; -fx-cursor: hand; -fx-padding: 10 20;",
            bgColor
        );

        button.setStyle(baseStyle);
        button.getProperties().put("bgColor", bgColor);
        button.getProperties().put("baseStyle", baseStyle);

        button.setOnMouseEntered(e -> {
            if (!button.isDisabled()) button.setStyle(baseStyle.replace(bgColor, hoverColor));
        });
        button.setOnMouseExited(e -> {
            if (!button.isDisabled()) button.setStyle(baseStyle);
        });
        button.setOnMousePressed(e -> {
            if (!button.isDisabled()) button.setStyle(baseStyle.replace(bgColor, pressedColor));
        });

        button.disabledProperty().addListener((obs, was, is) -> {
            button.setStyle(is ? baseStyle + "-fx-opacity: 0.5;" : baseStyle);
        });

        DropShadow shadow = new DropShadow();
        shadow.setRadius(5);
        shadow.setOffsetY(2);
        shadow.setColor(Color.rgb(0, 0, 0, 0.3));
        button.setEffect(shadow);

        return button;
    }

    private void updateButtonStyle(Button button, String bgColor, String hoverColor, String pressedColor) {
        String baseStyle = String.format(
            "-fx-background-color: %s; -fx-background-radius: 8; -fx-border-radius: 8; -fx-cursor: hand; -fx-padding: 10 20;",
            bgColor
        );
        button.setStyle(baseStyle);
        button.getProperties().put("bgColor", bgColor);
        button.getProperties().put("baseStyle", baseStyle);

        button.setOnMouseEntered(e -> {
            if (!button.isDisabled()) button.setStyle(baseStyle.replace(bgColor, hoverColor));
        });
        button.setOnMouseExited(e -> {
            if (!button.isDisabled()) button.setStyle(baseStyle);
        });
    }

    private Button createDialpadButton(String digit, String letters) {
        VBox content = new VBox(1);
        content.setAlignment(Pos.CENTER);

        Label digitLabel = new Label(digit);
        digitLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        digitLabel.setTextFill(Color.WHITE);

        Label lettersLabel = new Label(letters);
        lettersLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 8));
        lettersLabel.setTextFill(Color.web("#969696"));

        content.getChildren().addAll(digitLabel, lettersLabel);

        Button btn = new Button();
        btn.setGraphic(content);
        btn.setPrefSize(60, 55);

        String normalStyle = "-fx-background-color: #34495E; -fx-background-radius: 8; -fx-border-color: #465C6E; -fx-border-radius: 8; -fx-border-width: 1; -fx-cursor: hand;";
        String hoverStyle = "-fx-background-color: #0073BB; -fx-background-radius: 8; -fx-border-color: #0073BB; -fx-border-radius: 8; -fx-border-width: 1; -fx-cursor: hand;";
        String pressedStyle = "-fx-background-color: #005A8E; -fx-background-radius: 8; -fx-border-color: #005A8E; -fx-border-radius: 8; -fx-border-width: 1; -fx-cursor: hand;";

        btn.setStyle(normalStyle);
        btn.setOnMouseEntered(e -> btn.setStyle(hoverStyle));
        btn.setOnMouseExited(e -> btn.setStyle(normalStyle));
        btn.setOnMousePressed(e -> btn.setStyle(pressedStyle));

        return btn;
    }

    // ==================== SERVICE BINDING ====================

    private void bindServiceCallbacks() {
        service.setOnStatusChange(this::updateStatus);

        service.setOnReady(() -> {
            setReady();
        });

        service.setOnAgentStateChange(state -> {
            updateStatus("Agent: " + state);

            if ("Available".equalsIgnoreCase(state)) {
                stateToggleButton.setText("Offline");
                updateButtonStyle(stateToggleButton, "#95A5A6", "#829191", "#6E7B7B");
            } else {
                stateToggleButton.setText("Available");
                updateButtonStyle(stateToggleButton, "#27AE60", "#1E9650", "#198246");
                acceptButton.setDisable(true);
                rejectButton.setDisable(true);
                hangupButton.setDisable(true);
            }
        });

        service.setOnIncomingCall(callData -> {
            String customerName = parseJsonField(callData, "customerName");
            String phoneNumber = parseJsonField(callData, "phoneNumber");

            updateCustomerCard(customerName, phoneNumber, "#27AE60");

            if ("Available".equalsIgnoreCase(service.getCurrentAgentState())) {
                acceptButton.setDisable(false);
                rejectButton.setDisable(false);
            }
            hangupButton.setDisable(true);
        });

        service.setOnCallConnected(callData -> {
            acceptButton.setDisable(true);
            rejectButton.setDisable(true);
            hangupButton.setDisable(false);
        });

        service.setOnCallEnded(callData -> {
            acceptButton.setDisable(true);
            rejectButton.setDisable(true);
            hangupButton.setDisable(true);
            resetCustomerCard();
        });
    }

    private void showMainContent() {
        contentPane.getChildren().clear();
        contentPane.getChildren().add(mainContentPane);
    }

    private void updateCustomerCard(String name, String phone, String bgColor) {
        if (customerCardPanel != null) {
            customerCardPanel.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 10; -fx-border-color: " + bgColor + "; -fx-border-radius: 10; -fx-border-width: 2;");
        }
        if (customerNameLabel != null) customerNameLabel.setText(name != null ? name : "-");
        if (customerPhoneLabel != null) customerPhoneLabel.setText(phone != null ? phone : "-");
    }

    private void resetCustomerCard() {
        if (customerCardPanel != null) {
            customerCardPanel.setStyle("-fx-background-color: #34495E; -fx-background-radius: 10; -fx-border-color: #465C6E; -fx-border-radius: 10; -fx-border-width: 2;");
        }
        if (customerNameLabel != null) customerNameLabel.setText("-");
        if (customerPhoneLabel != null) customerPhoneLabel.setText("-");
    }

    private String parseJsonField(String json, String fieldName) {
        try {
            String key = "\"" + fieldName + "\":\"";
            int start = json.indexOf(key);
            if (start == -1) return null;
            start += key.length();
            int end = json.indexOf("\"", start);
            return end == -1 ? null : json.substring(start, end);
        } catch (Exception e) {
            return null;
        }
    }
}
