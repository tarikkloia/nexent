package com.kloia.jview;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

import me.friwi.jcefmaven.*;
import org.cef.CefApp;
import org.cef.CefApp.CefAppState;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefContextMenuParams;
import org.cef.callback.CefMenuModel;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefContextMenuHandlerAdapter;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.cef.network.CefCookieManager;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class MainApp extends Application {

    // JCEF components
    private CefApp cefApp_;
    private CefClient client_;
    private CefBrowser browser_;
    private Component browserUI_;

    // JavaFX UI components
    private Button acceptButton;
    private Button rejectButton;
    private Button hangupButton;
    private Button stateToggleButton;
    private Button testButton;

    // Customer card components
    private VBox customerCardPanel;
    private Label customerNameLabel;
    private Label customerPhoneLabel;

    // Splash screen
    private Stage splashStage;
    private Label statusLabel;

    // State tracking
    private boolean isFirstLogin = true;
    private String currentAgentState = "";
    private boolean isCallActive = false;

    // AWS Connect credentials
    private static final String AWS_USERNAME = "o_ozcan";
    private static final String AWS_PASSWORD = "326748.k_AWS";
    private static final String AWS_CONNECT_URL = "https://kloia-nexent.my.connect.aws/ccp-v2";

    @Override
    public void start(Stage primaryStage) {
        // Show splash first
        showSplashScreen(primaryStage);

        // Initialize JCEF in background
        new Thread(() -> {
            try {
                initializeCEF();
                Platform.runLater(() -> {
                    createMainUI(primaryStage);
                    primaryStage.show();
                });
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    updateSplashStatus("Error: " + e.getMessage());
                });
            }
        }).start();
    }

    private void showSplashScreen(Stage ownerStage) {
        splashStage = new Stage(StageStyle.UNDECORATED);

        VBox splashRoot = new VBox(20);
        splashRoot.setAlignment(Pos.CENTER);
        splashRoot.setPadding(new Insets(50));
        splashRoot.setStyle("-fx-background-color: #2C3E50; -fx-border-color: #0073BB; -fx-border-width: 2;");

        // Logo/Title
        Label titleLabel = new Label("KLOIA");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 48));
        titleLabel.setTextFill(Color.web("#0073BB"));

        // Subtitle
        Label subtitleLabel = new Label("AWS Connect Agent Desktop");
        subtitleLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 18));
        subtitleLabel.setTextFill(Color.WHITE);

        // Progress indicator
        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setPrefSize(50, 50);

        // Status label
        statusLabel = new Label("Initializing...");
        statusLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 14));
        statusLabel.setTextFill(Color.web("#969696"));

        splashRoot.getChildren().addAll(titleLabel, subtitleLabel, progressIndicator, statusLabel);

        Scene splashScene = new Scene(splashRoot, 500, 350);
        splashStage.setScene(splashScene);
        splashStage.centerOnScreen();
        splashStage.show();
    }

    private void updateSplashStatus(String message) {
        Platform.runLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText(message);
            }
        });
    }

    private void closeSplash() {
        Platform.runLater(() -> {
            if (splashStage != null) {
                splashStage.close();
                splashStage = null;
            }
        });
    }

    private void initializeCEF() throws Exception {
        updateSplashStatus("Initializing browser engine...");

        CefAppBuilder builder = new CefAppBuilder();
        builder.addJcefArgs("--use-fake-ui-for-media-stream");
        builder.addJcefArgs("--disable-popup-blocking");
        builder.addJcefArgs("--enable-features=NetworkService,NetworkServiceInProcess");
        builder.addJcefArgs("--disable-features=SameSiteByDefaultCookies,CookiesWithoutSameSiteMustBeSecure");

        builder.getCefSettings().windowless_rendering_enabled = false;
        builder.getCefSettings().persist_session_cookies = true;

        File cacheDir = new File("jcef_cache");
        if (!cacheDir.exists()) cacheDir.mkdirs();
        builder.getCefSettings().cache_path = cacheDir.getAbsolutePath();

        builder.setAppHandler(new MavenCefAppHandlerAdapter() {
            @Override
            public void stateHasChanged(CefAppState state) {
                if (state == CefAppState.TERMINATED) {
                    Platform.exit();
                    System.exit(0);
                }
            }
        });

        cefApp_ = builder.build();
        client_ = cefApp_.createClient();

        // Popup handler
        client_.addLifeSpanHandler(new org.cef.handler.CefLifeSpanHandlerAdapter() {
            @Override
            public boolean onBeforePopup(org.cef.browser.CefBrowser browser,
                                         org.cef.browser.CefFrame frame,
                                         String target_url,
                                         String target_frame_name) {
                System.out.println("Popup blocked, redirecting to: " + target_url);
                SwingUtilities.invokeLater(() -> browser.loadURL(target_url));
                return true;
            }
        });

        // Message router for CEF-JS communication
        CefMessageRouter msgRouter = CefMessageRouter.create();
        msgRouter.addHandler(new CefMessageRouterHandlerAdapter() {
            @Override
            public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request,
                                   boolean persistent, CefQueryCallback callback) {
                System.out.println("===========================================");
                System.out.println("EVENT FROM AWS CONNECT: " + request);
                System.out.println("===========================================");

                if (request.startsWith("INCOMING_CALL:")) {
                    String callData = request.substring("INCOMING_CALL:".length());
                    onIncomingCall(callData);
                } else if (request.startsWith("CALL_CONNECTED:")) {
                    String callData = request.substring("CALL_CONNECTED:".length());
                    onCallConnected(callData);
                } else if (request.startsWith("CALL_ENDED:")) {
                    String callData = request.substring("CALL_ENDED:".length());
                    onCallEnded(callData);
                } else if (request.startsWith("AGENT_STATE:")) {
                    String state = request.substring("AGENT_STATE:".length());
                    onAgentStateChange(state);
                } else if (request.equals("NUMPAD_READY")) {
                    onNumpadReady();
                }

                callback.success("Event received");
                return true;
            }
        }, true);
        client_.addMessageRouter(msgRouter);

        // Display handler
        client_.addDisplayHandler(new CefDisplayHandlerAdapter() {
            @Override
            public void onAddressChange(CefBrowser browser, CefFrame frame, String url) {
                System.out.println("Navigated to: " + url);
            }

            @Override
            public boolean onConsoleMessage(CefBrowser browser, org.cef.CefSettings.LogSeverity level,
                                            String message, String source, int line) {
                System.out.println("[JS Console] " + message);
                return false;
            }
        });

        // Auto-login handler
        client_.addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
                String url = browser.getURL();
                System.out.println("Page loaded: " + url);

                boolean isLoginPage = url.contains("signin") || url.contains("login") ||
                        url.contains("oauth") || url.contains("authenticate") ||
                        url.contains("awsapps.com/auth") || url.contains("/auth/") ||
                        url.contains("/auth?") || url.contains("sso");

                if (isLoginPage) {
                    System.out.println("Login page detected, executing auto-login script...");
                    updateSplashStatus("Signing in...");

                    String script = loadScript("scripts/autoLogin.js");
                    if (script != null) {
                        script = script.replace("{{USERNAME}}", AWS_USERNAME)
                                .replace("{{PASSWORD}}", AWS_PASSWORD);
                        browser.executeJavaScript(script, url, 0);
                    }
                }

                if (url.contains("ccp-v2") && !url.contains("login") && !url.contains("auth")) {
                    System.out.println("AWS Connect CCP loaded, adding event listeners...");
                    updateSplashStatus("Loading AWS Connect CCP...");

                    String connectEventsScript = loadScript("scripts/connectEventsScript.js");
                    if (connectEventsScript != null) {
                        browser.executeJavaScript(connectEventsScript, url, 0);
                    }
                }
            }
        });

        // Disable context menu
        client_.addContextMenuHandler(new CefContextMenuHandlerAdapter() {
            @Override
            public void onBeforeContextMenu(CefBrowser browser, CefFrame frame,
                                            CefContextMenuParams params, CefMenuModel model) {
                model.clear();
            }
        });

        updateSplashStatus("Connecting to AWS Connect...");
        browser_ = client_.createBrowser(AWS_CONNECT_URL, false, false);
        browserUI_ = browser_.getUIComponent();
    }

    private void createMainUI(Stage primaryStage) {
        primaryStage.setTitle("Kloia - AWS Connect Agent Desktop");

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #2C3E50;");

        // Top panel with buttons
        HBox topPanel = createTopPanel();
        root.setTop(topPanel);

        // Center content
        HBox centerContent = new HBox(20);
        centerContent.setPadding(new Insets(20));
        centerContent.setAlignment(Pos.CENTER);

        // Customer card on the left
        customerCardPanel = createCustomerCard();
        centerContent.getChildren().add(customerCardPanel);

        // CCP Browser in hidden JFrame (off-screen, JCEF requires visible window)
        SwingUtilities.invokeLater(() -> {
            JFrame browserFrame = new JFrame("AWS Connect CCP");
            browserFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            browserFrame.setSize(400, 400);
            browserFrame.setUndecorated(true); // No title bar
            browserFrame.setLocation(-2000, -2000); // Off-screen position

            JPanel browserPanel = new JPanel(new BorderLayout());
            browserPanel.setBackground(java.awt.Color.DARK_GRAY);

            if (browserUI_ != null) {
                browserPanel.add(browserUI_, BorderLayout.CENTER);
            }

            browserFrame.add(browserPanel);
            browserFrame.setVisible(true); // Must be visible for JCEF to work

            // Close browser frame when main stage closes
            primaryStage.setOnCloseRequest(e -> {
                browserFrame.dispose();
                Platform.exit();
                System.exit(0);
            });
        });

        // Dialpad panel
        VBox dialpadPanel = createDialpad();
        centerContent.getChildren().add(dialpadPanel);

        root.setCenter(centerContent);

        Scene scene = new Scene(root, 900, 650);

        // Add CSS styling
        scene.getStylesheets().add(getClass().getResource("/styles/main.css") != null ?
            getClass().getResource("/styles/main.css").toExternalForm() : "");

        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> {
            Platform.exit();
            System.exit(0);
        });
    }

    private HBox createTopPanel() {
        HBox topPanel = new HBox(15);
        topPanel.setPadding(new Insets(15, 20, 15, 20));
        topPanel.setAlignment(Pos.CENTER);
        topPanel.setStyle("-fx-background-color: #34495E;");

        // Call control buttons
        acceptButton = createModernButton("Accept", "#2ECC71", "#27AE60", "#1E9650");
        acceptButton.setDisable(true);
        acceptButton.setOnAction(e -> {
            System.out.println("Accept button clicked");
            executeScript("scripts/acceptCall.js");
        });

        rejectButton = createModernButton("Reject", "#E74C3C", "#C83C32", "#AA3228");
        rejectButton.setDisable(true);
        rejectButton.setOnAction(e -> {
            System.out.println("Reject button clicked");
            executeScript("scripts/rejectCall.js");
        });

        hangupButton = createModernButton("Hang Up", "#C0392B", "#A02D23", "#82251D");
        hangupButton.setDisable(true);
        hangupButton.setOnAction(e -> {
            System.out.println("Hangup button clicked");
            executeScript("scripts/hangupCall.js");
        });

        // State toggle button
        stateToggleButton = createModernButton("Available", "#27AE60", "#1E9650", "#198246");
        stateToggleButton.setDisable(true);
        stateToggleButton.setOnAction(e -> {
            if ("Available".equalsIgnoreCase(currentAgentState)) {
                System.out.println("Setting agent to Offline");
                executeScript("scripts/setAgentOffline.js");
            } else {
                System.out.println("Setting agent to Available");
                executeScript("scripts/setAgentAvailable.js");
            }
        });

        // Test button
        testButton = createModernButton("Test", "#9B59B6", "#82479B", "#6E3D82");
        testButton.setOnAction(e -> {
            String testCallData = "{\"customerName\":\"Test Müşteri\",\"phoneNumber\":\"+905551234567\"}";
            onIncomingCall(testCallData);
        });

        // Separator
        Region separator = new Region();
        separator.setPrefWidth(2);
        separator.setPrefHeight(30);
        separator.setStyle("-fx-background-color: #5D6D7E;");

        topPanel.getChildren().addAll(acceptButton, rejectButton, hangupButton, separator, stateToggleButton, testButton);

        return topPanel;
    }

    private Button createModernButton(String text, String bgColor, String hoverColor, String pressedColor) {
        Button button = new Button(text);
        button.setPrefHeight(40);
        button.setMinWidth(100);
        button.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
        button.setTextFill(Color.WHITE);

        String baseStyle = String.format(
            "-fx-background-color: %s; " +
            "-fx-background-radius: 8; " +
            "-fx-border-radius: 8; " +
            "-fx-cursor: hand; " +
            "-fx-padding: 10 20;",
            bgColor
        );

        button.setStyle(baseStyle);

        // Hover effect
        button.setOnMouseEntered(e -> {
            if (!button.isDisabled()) {
                button.setStyle(baseStyle.replace(bgColor, hoverColor));
            }
        });

        button.setOnMouseExited(e -> {
            if (!button.isDisabled()) {
                button.setStyle(baseStyle);
            }
        });

        button.setOnMousePressed(e -> {
            if (!button.isDisabled()) {
                button.setStyle(baseStyle.replace(bgColor, pressedColor));
            }
        });

        button.setOnMouseReleased(e -> {
            if (!button.isDisabled()) {
                button.setStyle(baseStyle.replace(bgColor, hoverColor));
            }
        });

        // Store colors for dynamic updates
        button.getProperties().put("bgColor", bgColor);
        button.getProperties().put("hoverColor", hoverColor);
        button.getProperties().put("pressedColor", pressedColor);

        // Disabled style
        button.disabledProperty().addListener((obs, wasDisabled, isNowDisabled) -> {
            if (isNowDisabled) {
                button.setStyle(baseStyle + "-fx-opacity: 0.5;");
            } else {
                button.setStyle(baseStyle);
            }
        });

        // Drop shadow effect
        DropShadow shadow = new DropShadow();
        shadow.setRadius(5);
        shadow.setOffsetY(2);
        shadow.setColor(Color.rgb(0, 0, 0, 0.3));
        button.setEffect(shadow);

        return button;
    }

    private void updateButtonStyle(Button button, String bgColor, String hoverColor, String pressedColor) {
        String baseStyle = String.format(
            "-fx-background-color: %s; " +
            "-fx-background-radius: 8; " +
            "-fx-border-radius: 8; " +
            "-fx-cursor: hand; " +
            "-fx-padding: 10 20;",
            bgColor
        );

        button.setStyle(baseStyle);
        button.getProperties().put("bgColor", bgColor);
        button.getProperties().put("hoverColor", hoverColor);
        button.getProperties().put("pressedColor", pressedColor);

        // Update hover handlers
        button.setOnMouseEntered(e -> {
            if (!button.isDisabled()) {
                button.setStyle(baseStyle.replace(bgColor, hoverColor));
            }
        });

        button.setOnMouseExited(e -> {
            if (!button.isDisabled()) {
                button.setStyle(baseStyle);
            }
        });

        button.setOnMousePressed(e -> {
            if (!button.isDisabled()) {
                button.setStyle(baseStyle.replace(bgColor, pressedColor));
            }
        });

        button.setOnMouseReleased(e -> {
            if (!button.isDisabled()) {
                button.setStyle(baseStyle.replace(bgColor, hoverColor));
            }
        });
    }

    private VBox createCustomerCard() {
        VBox card = new VBox(15);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPadding(new Insets(20));
        card.setPrefWidth(250);
        card.setPrefHeight(200);
        card.setStyle(
            "-fx-background-color: #34495E; " +
            "-fx-background-radius: 10; " +
            "-fx-border-color: #465C6E; " +
            "-fx-border-radius: 10; " +
            "-fx-border-width: 2;"
        );

        // Drop shadow
        DropShadow shadow = new DropShadow();
        shadow.setRadius(10);
        shadow.setOffsetY(3);
        shadow.setColor(Color.rgb(0, 0, 0, 0.3));
        card.setEffect(shadow);

        // Title
        Label titleLabel = new Label("Customer Info");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        titleLabel.setTextFill(Color.WHITE);

        // Customer name
        customerNameLabel = new Label("-");
        customerNameLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        customerNameLabel.setTextFill(Color.WHITE);

        // Phone number
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
        dialpad.setPadding(new Insets(20));
        dialpad.setStyle(
            "-fx-background-color: #232F3E; " +
            "-fx-background-radius: 10; " +
            "-fx-border-color: #0073BB; " +
            "-fx-border-radius: 10; " +
            "-fx-border-width: 2;"
        );

        // Drop shadow
        DropShadow shadow = new DropShadow();
        shadow.setRadius(10);
        shadow.setOffsetY(3);
        shadow.setColor(Color.rgb(0, 0, 0, 0.3));
        dialpad.setEffect(shadow);

        // Title
        Label titleLabel = new Label("Dial Pad");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        titleLabel.setTextFill(Color.WHITE);

        // Numpad grid (4x3)
        GridPane gridPane = new GridPane();
        gridPane.setHgap(8);
        gridPane.setVgap(8);
        gridPane.setAlignment(Pos.CENTER);

        // Phone keypad data: digit, letters
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
            btn.setOnAction(e -> {
                System.out.println("Dialpad pressed: " + digit);
                sendDtmfDigit(digit);
            });

            gridPane.add(btn, col, row);
            col++;
            if (col > 2) {
                col = 0;
                row++;
            }
        }

        dialpad.getChildren().addAll(titleLabel, gridPane);

        return dialpad;
    }

    private Button createDialpadButton(String digit, String letters) {
        VBox content = new VBox(2);
        content.setAlignment(Pos.CENTER);

        Label digitLabel = new Label(digit);
        digitLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 24));
        digitLabel.setTextFill(Color.WHITE);

        Label lettersLabel = new Label(letters);
        lettersLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 9));
        lettersLabel.setTextFill(Color.web("#969696"));

        content.getChildren().addAll(digitLabel, lettersLabel);

        Button btn = new Button();
        btn.setGraphic(content);
        btn.setPrefSize(70, 70);
        btn.setStyle(
            "-fx-background-color: #34495E; " +
            "-fx-background-radius: 8; " +
            "-fx-border-color: #465C6E; " +
            "-fx-border-radius: 8; " +
            "-fx-border-width: 1; " +
            "-fx-cursor: hand;"
        );

        // Hover effects
        btn.setOnMouseEntered(e -> btn.setStyle(
            "-fx-background-color: #0073BB; " +
            "-fx-background-radius: 8; " +
            "-fx-border-color: #0073BB; " +
            "-fx-border-radius: 8; " +
            "-fx-border-width: 1; " +
            "-fx-cursor: hand;"
        ));

        btn.setOnMouseExited(e -> btn.setStyle(
            "-fx-background-color: #34495E; " +
            "-fx-background-radius: 8; " +
            "-fx-border-color: #465C6E; " +
            "-fx-border-radius: 8; " +
            "-fx-border-width: 1; " +
            "-fx-cursor: hand;"
        ));

        btn.setOnMousePressed(e -> btn.setStyle(
            "-fx-background-color: #005A8E; " +
            "-fx-background-radius: 8; " +
            "-fx-border-color: #005A8E; " +
            "-fx-border-radius: 8; " +
            "-fx-border-width: 1; " +
            "-fx-cursor: hand;"
        ));

        return btn;
    }

    private void sendDtmfDigit(String digit) {
        System.out.println("Sending DTMF: " + digit);
        String script = loadScript("scripts/sendDtmf.js");
        if (script != null && browser_ != null) {
            script = script.replace("{{DIGIT}}", digit);
            browser_.executeJavaScript(script, browser_.getURL(), 0);
        }
    }

    // ==================== EVENT HANDLERS ====================

    private void onIncomingCall(String callData) {
        System.out.println("*** INCOMING CALL ***");
        System.out.println("Call Data: " + callData);

        String customerName = parseJsonField(callData, "customerName");
        String phoneNumber = parseJsonField(callData, "phoneNumber");

        Platform.runLater(() -> {
            if (!isCallActive) {
                isCallActive = true;
                updateCustomerCard(customerName, phoneNumber, "#27AE60"); // Green
            } else {
                updateCustomerCard(customerName, phoneNumber, "#E74C3C"); // Red

                PauseTransition pause = new PauseTransition(Duration.seconds(2));
                pause.setOnFinished(e -> resetCustomerCard());
                pause.play();
            }

            // Enable accept/reject only if agent is Available
            if ("Available".equalsIgnoreCase(currentAgentState)) {
                if (acceptButton != null) acceptButton.setDisable(false);
                if (rejectButton != null) rejectButton.setDisable(false);
            }
            if (hangupButton != null) hangupButton.setDisable(true);
        });
    }

    private void onCallConnected(String callData) {
        System.out.println("*** CALL CONNECTED ***");
        System.out.println("Call Data: " + callData);

        Platform.runLater(() -> {
            if (acceptButton != null) acceptButton.setDisable(true);
            if (rejectButton != null) rejectButton.setDisable(true);
            if (hangupButton != null) hangupButton.setDisable(false);
        });
    }

    private void onCallEnded(String callData) {
        System.out.println("*** CALL ENDED ***");
        System.out.println("Call Data: " + callData);

        Platform.runLater(() -> {
            if (acceptButton != null) acceptButton.setDisable(true);
            if (rejectButton != null) rejectButton.setDisable(true);
            if (hangupButton != null) hangupButton.setDisable(true);
        });
    }

    private void onAgentStateChange(String state) {
        System.out.println("*** AGENT STATE CHANGED: " + state + " ***");

        // Auto-available only on first login if offline
        if (isFirstLogin && "Offline".equalsIgnoreCase(state)) {
            System.out.println("First login detected as Offline, auto-setting to Available...");
            isFirstLogin = false;

            PauseTransition pause = new PauseTransition(Duration.millis(500));
            pause.setOnFinished(e -> executeScript("scripts/setAgentAvailable.js"));
            pause.play();
            return;
        }

        if (isFirstLogin && !"Offline".equalsIgnoreCase(state)) {
            isFirstLogin = false;
        }

        currentAgentState = state;

        Platform.runLater(() -> {
            if (stateToggleButton != null) {
                if ("Available".equalsIgnoreCase(state)) {
                    stateToggleButton.setText("Offline");
                    updateButtonStyle(stateToggleButton, "#95A5A6", "#829191", "#6E7B7B");
                } else {
                    stateToggleButton.setText("Available");
                    updateButtonStyle(stateToggleButton, "#27AE60", "#1E9650", "#198246");

                    // Disable accept/reject when Offline
                    if (acceptButton != null) acceptButton.setDisable(true);
                    if (rejectButton != null) rejectButton.setDisable(true);
                    if (hangupButton != null) hangupButton.setDisable(true);
                }
            }
        });
    }

    private void onNumpadReady() {
        System.out.println("*** NUMPAD READY ***");
        onCCPReady();
    }

    private void onCCPReady() {
        Platform.runLater(() -> {
            closeSplash();

            if (stateToggleButton != null) stateToggleButton.setDisable(false);

            System.out.println("*** CCP READY - UI ENABLED ***");
        });
    }

    private void updateCustomerCard(String customerName, String phoneNumber, String backgroundColor) {
        Platform.runLater(() -> {
            if (customerCardPanel != null) {
                customerCardPanel.setStyle(
                    "-fx-background-color: " + backgroundColor + "; " +
                    "-fx-background-radius: 10; " +
                    "-fx-border-color: " + backgroundColor + "; " +
                    "-fx-border-radius: 10; " +
                    "-fx-border-width: 2;"
                );
            }
            if (customerNameLabel != null) {
                customerNameLabel.setText(customerName != null ? customerName : "-");
            }
            if (customerPhoneLabel != null) {
                customerPhoneLabel.setText(phoneNumber != null ? phoneNumber : "-");
            }
        });
    }

    private void resetCustomerCard() {
        Platform.runLater(() -> {
            if (customerCardPanel != null) {
                customerCardPanel.setStyle(
                    "-fx-background-color: #34495E; " +
                    "-fx-background-radius: 10; " +
                    "-fx-border-color: #465C6E; " +
                    "-fx-border-radius: 10; " +
                    "-fx-border-width: 2;"
                );
            }
            if (customerNameLabel != null) {
                customerNameLabel.setText("-");
            }
            if (customerPhoneLabel != null) {
                customerPhoneLabel.setText("-");
            }
            isCallActive = false;
        });
    }

    // ==================== UTILITY METHODS ====================

    private String parseJsonField(String json, String fieldName) {
        try {
            String searchKey = "\"" + fieldName + "\":\"";
            int startIndex = json.indexOf(searchKey);
            if (startIndex == -1) return null;

            startIndex += searchKey.length();
            int endIndex = json.indexOf("\"", startIndex);
            if (endIndex == -1) return null;

            return json.substring(startIndex, endIndex);
        } catch (Exception e) {
            System.err.println("Error parsing JSON field " + fieldName + ": " + e.getMessage());
            return null;
        }
    }

    private String loadScript(String resourcePath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                System.err.println("Script not found: " + resourcePath);
                return null;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            System.err.println("Error loading script: " + resourcePath + " - " + e.getMessage());
            return null;
        }
    }

    private void executeScript(String resourcePath) {
        String script = loadScript(resourcePath);
        if (script != null && browser_ != null) {
            browser_.executeJavaScript(script, browser_.getURL(), 0);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
