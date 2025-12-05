package com.kloia.jview;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
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

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Kloia AWS Connect Component - Opens its own Swing JFrame window
 *
 * Architecture: Swing JFrame + JFXPanel (for JavaFX UI) + JCEF (native browser)
 * This ensures JCEF renders properly while having modern JavaFX buttons.
 *
 * Usage in JavaFX Application:
 * <pre>
 * KloiaConnectComponent component = new KloiaConnectComponent();
 * component.setCcpVisible(true);
 * component.setOnAgentStateChange(state -> System.out.println("State: " + state));
 * component.show(); // Opens the Swing window
 * </pre>
 */
public class KloiaConnectComponent {

    // Swing JFrame window
    private JFrame frame;

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

    // Splash components
    private JDialog splashDialog;
    private Label splashStatusLabel;

    // State tracking
    private boolean isFirstLogin = true;
    private String currentAgentState = "";
    private boolean isCallActive = false;
    private boolean ccpVisible = false;
    private boolean initialized = false;

    // Callbacks
    private Consumer<String> onIncomingCallCallback;
    private Consumer<String> onCallConnectedCallback;
    private Consumer<String> onCallEndedCallback;
    private Consumer<String> onAgentStateChangeCallback;
    private Runnable onReadyCallback;
    private Runnable onCloseCallback;

    // AWS Connect credentials
    private String awsUsername = "o_ozcan";
    private String awsPassword = "326748.k_AWS";
    private String awsConnectUrl = "https://kloia-nexent.my.connect.aws/ccp-v2";

    public KloiaConnectComponent() {
        // Initialize JavaFX toolkit
        new JFXPanel();
    }

    // ==================== PUBLIC API ====================

    /**
     * Set AWS credentials before showing
     */
    public void setCredentials(String username, String password, String connectUrl) {
        this.awsUsername = username;
        this.awsPassword = password;
        this.awsConnectUrl = connectUrl;
    }

    /**
     * Set CCP browser visibility within the window
     */
    public void setCcpVisible(boolean visible) {
        this.ccpVisible = visible;
    }

    /**
     * Set callback for incoming calls
     */
    public void setOnIncomingCall(Consumer<String> callback) {
        this.onIncomingCallCallback = callback;
    }

    /**
     * Set callback for call connected
     */
    public void setOnCallConnected(Consumer<String> callback) {
        this.onCallConnectedCallback = callback;
    }

    /**
     * Set callback for call ended
     */
    public void setOnCallEnded(Consumer<String> callback) {
        this.onCallEndedCallback = callback;
    }

    /**
     * Set callback for agent state changes
     */
    public void setOnAgentStateChange(Consumer<String> callback) {
        this.onAgentStateChangeCallback = callback;
    }

    /**
     * Set callback for when component is ready
     */
    public void setOnReady(Runnable callback) {
        this.onReadyCallback = callback;
    }

    /**
     * Set callback for when window is closed
     */
    public void setOnClose(Runnable callback) {
        this.onCloseCallback = callback;
    }

    /**
     * Show the component window
     */
    public void show() {
        SwingUtilities.invokeLater(this::createAndShowWindow);
    }

    /**
     * Get the JFrame window (for positioning relative to other windows)
     */
    public JFrame getFrame() {
        return frame;
    }

    /**
     * Check if component is initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Get current agent state
     */
    public String getCurrentAgentState() {
        return currentAgentState;
    }

    /**
     * Programmatically accept call
     */
    public void acceptCall() {
        executeScript("scripts/acceptCall.js");
    }

    /**
     * Programmatically reject call
     */
    public void rejectCall() {
        executeScript("scripts/rejectCall.js");
    }

    /**
     * Programmatically hang up call
     */
    public void hangupCall() {
        executeScript("scripts/hangupCall.js");
    }

    /**
     * Set agent to available
     */
    public void setAgentAvailable() {
        executeScript("scripts/setAgentAvailable.js");
    }

    /**
     * Set agent to offline
     */
    public void setAgentOffline() {
        executeScript("scripts/setAgentOffline.js");
    }

    /**
     * Send DTMF digit
     */
    public void sendDtmf(String digit) {
        String script = loadScript("scripts/sendDtmf.js");
        if (script != null && browser_ != null) {
            script = script.replace("{{DIGIT}}", digit);
            browser_.executeJavaScript(script, browser_.getURL(), 0);
        }
    }

    /**
     * Close and dispose the component
     */
    public void dispose() {
        if (frame != null) {
            frame.dispose();
        }
        if (cefApp_ != null) {
            cefApp_.dispose();
        }
    }

    // ==================== PRIVATE METHODS ====================

    private void createAndShowWindow() {
        frame = new JFrame("Kloia - AWS Connect Agent Desktop");
        frame.setSize(950, 700);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(new java.awt.Color(44, 62, 80));
        frame.setLayout(new BorderLayout());

        // Show splash
        showSplashDialog();

        // Initialize JCEF in background
        new Thread(() -> {
            try {
                initializeCEF();
                SwingUtilities.invokeLater(this::createUI);
            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> updateSplashStatus("Error: " + e.getMessage()));
            }
        }).start();

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (onCloseCallback != null) {
                    onCloseCallback.run();
                }
                if (cefApp_ != null) {
                    cefApp_.dispose();
                }
                frame.dispose();
            }
        });

        frame.setVisible(true);
    }

    private void showSplashDialog() {
        splashDialog = new JDialog(frame, "Loading...", false);
        splashDialog.setUndecorated(true);
        splashDialog.setSize(500, 350);
        splashDialog.setLocationRelativeTo(frame);

        JFXPanel splashFxPanel = new JFXPanel();
        splashDialog.add(splashFxPanel);

        Platform.runLater(() -> {
            VBox splashRoot = new VBox(20);
            splashRoot.setAlignment(Pos.CENTER);
            splashRoot.setPadding(new Insets(50));
            splashRoot.setStyle("-fx-background-color: #2C3E50; -fx-border-color: #0073BB; -fx-border-width: 2;");

            Label titleLabel = new Label("KLOIA");
            titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 48));
            titleLabel.setTextFill(Color.web("#0073BB"));

            Label subtitleLabel = new Label("AWS Connect Agent Desktop");
            subtitleLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 18));
            subtitleLabel.setTextFill(Color.WHITE);

            ProgressIndicator progressIndicator = new ProgressIndicator();
            progressIndicator.setPrefSize(50, 50);

            splashStatusLabel = new Label("Initializing...");
            splashStatusLabel.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 14));
            splashStatusLabel.setTextFill(Color.web("#969696"));

            splashRoot.getChildren().addAll(titleLabel, subtitleLabel, progressIndicator, splashStatusLabel);

            Scene splashScene = new Scene(splashRoot, 500, 350);
            splashFxPanel.setScene(splashScene);
        });

        splashDialog.setVisible(true);
    }

    private void updateSplashStatus(String message) {
        Platform.runLater(() -> {
            if (splashStatusLabel != null) {
                splashStatusLabel.setText(message);
            }
        });
    }

    private void closeSplash() {
        SwingUtilities.invokeLater(() -> {
            if (splashDialog != null) {
                splashDialog.dispose();
                splashDialog = null;
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
                System.out.println("EVENT FROM AWS CONNECT: " + request);

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

                boolean isLoginPage = url.contains("signin") || url.contains("login") ||
                        url.contains("oauth") || url.contains("authenticate") ||
                        url.contains("awsapps.com/auth") || url.contains("/auth/") ||
                        url.contains("/auth?") || url.contains("sso");

                if (isLoginPage) {
                    updateSplashStatus("Signing in...");
                    String script = loadScript("scripts/autoLogin.js");
                    if (script != null) {
                        script = script.replace("{{USERNAME}}", awsUsername)
                                .replace("{{PASSWORD}}", awsPassword);
                        browser.executeJavaScript(script, url, 0);
                    }
                }

                if (url.contains("ccp-v2") && !url.contains("login") && !url.contains("auth")) {
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
        browser_ = client_.createBrowser(awsConnectUrl, false, false);
        browserUI_ = browser_.getUIComponent();
    }

    private void createUI() {
        // Top panel with JavaFX buttons
        JFXPanel topFxPanel = new JFXPanel();
        topFxPanel.setPreferredSize(new Dimension(frame.getWidth(), 70));

        Platform.runLater(() -> {
            HBox topPanel = createTopPanel();
            Scene topScene = new Scene(topPanel, frame.getWidth(), 70);
            topFxPanel.setScene(topScene);
        });

        frame.add(topFxPanel, BorderLayout.NORTH);

        // Center content panel
        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));
        centerPanel.setBackground(new java.awt.Color(44, 62, 80));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 20, 20));

        // Left side: Customer card (JavaFX)
        JFXPanel customerFxPanel = new JFXPanel();
        customerFxPanel.setPreferredSize(new Dimension(260, 220));

        Platform.runLater(() -> {
            customerCardPanel = createCustomerCard();
            Scene customerScene = new Scene(customerCardPanel, 260, 220);
            customerScene.setFill(Color.TRANSPARENT);
            customerFxPanel.setScene(customerScene);
        });

        // Center: Dialpad (JavaFX)
        JFXPanel dialpadFxPanel = new JFXPanel();
        dialpadFxPanel.setPreferredSize(new Dimension(280, 380));

        Platform.runLater(() -> {
            VBox dialpad = createDialpad();
            Scene dialpadScene = new Scene(dialpad, 280, 380);
            dialpadScene.setFill(Color.TRANSPARENT);
            dialpadFxPanel.setScene(dialpadScene);
        });

        // Right side: JCEF Browser (Swing - native)
        JPanel browserPanel = new JPanel(new BorderLayout());
        browserPanel.setBackground(java.awt.Color.DARK_GRAY);
        browserPanel.setPreferredSize(new Dimension(350, 400));
        browserPanel.setBorder(BorderFactory.createLineBorder(new java.awt.Color(0, 115, 187), 2));

        if (browserUI_ != null) {
            browserPanel.add(browserUI_, BorderLayout.CENTER);
        }

        browserPanel.setVisible(ccpVisible);

        // Layout center components
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        leftPanel.setBackground(new java.awt.Color(44, 62, 80));
        leftPanel.add(customerFxPanel);

        JPanel middlePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        middlePanel.setBackground(new java.awt.Color(44, 62, 80));
        middlePanel.add(dialpadFxPanel);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightPanel.setBackground(new java.awt.Color(44, 62, 80));
        rightPanel.add(browserPanel);

        centerPanel.add(leftPanel, BorderLayout.WEST);
        centerPanel.add(middlePanel, BorderLayout.CENTER);
        centerPanel.add(rightPanel, BorderLayout.EAST);

        frame.add(centerPanel, BorderLayout.CENTER);

        initialized = true;
    }

    private HBox createTopPanel() {
        HBox topPanel = new HBox(15);
        topPanel.setPadding(new Insets(15, 20, 15, 20));
        topPanel.setAlignment(Pos.CENTER);
        topPanel.setStyle("-fx-background-color: #34495E;");

        acceptButton = createModernButton("Accept", "#2ECC71", "#27AE60", "#1E9650");
        acceptButton.setDisable(true);
        acceptButton.setOnAction(e -> acceptCall());

        rejectButton = createModernButton("Reject", "#E74C3C", "#C83C32", "#AA3228");
        rejectButton.setDisable(true);
        rejectButton.setOnAction(e -> rejectCall());

        hangupButton = createModernButton("Hang Up", "#C0392B", "#A02D23", "#82251D");
        hangupButton.setDisable(true);
        hangupButton.setOnAction(e -> hangupCall());

        stateToggleButton = createModernButton("Available", "#27AE60", "#1E9650", "#198246");
        stateToggleButton.setDisable(true);
        stateToggleButton.setOnAction(e -> {
            if ("Available".equalsIgnoreCase(currentAgentState)) {
                setAgentOffline();
            } else {
                setAgentAvailable();
            }
        });

        testButton = createModernButton("Test", "#9B59B6", "#82479B", "#6E3D82");
        testButton.setOnAction(e -> {
            String testCallData = "{\"customerName\":\"Test Müşteri\",\"phoneNumber\":\"+905551234567\"}";
            onIncomingCall(testCallData);
        });

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

    private VBox createCustomerCard() {
        VBox card = new VBox(15);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPadding(new Insets(20));
        card.setPrefSize(250, 200);
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
        dialpad.setPadding(new Insets(20));
        dialpad.setStyle("-fx-background-color: #232F3E; -fx-background-radius: 10; -fx-border-color: #0073BB; -fx-border-radius: 10; -fx-border-width: 2;");

        DropShadow shadow = new DropShadow();
        shadow.setRadius(10);
        shadow.setOffsetY(3);
        shadow.setColor(Color.rgb(0, 0, 0, 0.3));
        dialpad.setEffect(shadow);

        Label titleLabel = new Label("Dial Pad");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        titleLabel.setTextFill(Color.WHITE);

        GridPane gridPane = new GridPane();
        gridPane.setHgap(8);
        gridPane.setVgap(8);
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
            btn.setOnAction(e -> sendDtmf(digit));

            gridPane.add(btn, col, row);
            col++;
            if (col > 2) { col = 0; row++; }
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

        String normalStyle = "-fx-background-color: #34495E; -fx-background-radius: 8; -fx-border-color: #465C6E; -fx-border-radius: 8; -fx-border-width: 1; -fx-cursor: hand;";
        String hoverStyle = "-fx-background-color: #0073BB; -fx-background-radius: 8; -fx-border-color: #0073BB; -fx-border-radius: 8; -fx-border-width: 1; -fx-cursor: hand;";
        String pressedStyle = "-fx-background-color: #005A8E; -fx-background-radius: 8; -fx-border-color: #005A8E; -fx-border-radius: 8; -fx-border-width: 1; -fx-cursor: hand;";

        btn.setStyle(normalStyle);
        btn.setOnMouseEntered(e -> btn.setStyle(hoverStyle));
        btn.setOnMouseExited(e -> btn.setStyle(normalStyle));
        btn.setOnMousePressed(e -> btn.setStyle(pressedStyle));

        return btn;
    }

    // ==================== EVENT HANDLERS ====================

    private void onIncomingCall(String callData) {
        System.out.println("*** INCOMING CALL ***");

        String customerName = parseJsonField(callData, "customerName");
        String phoneNumber = parseJsonField(callData, "phoneNumber");

        Platform.runLater(() -> {
            if (!isCallActive) {
                isCallActive = true;
                updateCustomerCard(customerName, phoneNumber, "#27AE60");
            } else {
                updateCustomerCard(customerName, phoneNumber, "#E74C3C");
                PauseTransition pause = new PauseTransition(Duration.seconds(2));
                pause.setOnFinished(e -> resetCustomerCard());
                pause.play();
            }

            if ("Available".equalsIgnoreCase(currentAgentState)) {
                if (acceptButton != null) acceptButton.setDisable(false);
                if (rejectButton != null) rejectButton.setDisable(false);
            }
            if (hangupButton != null) hangupButton.setDisable(true);
        });

        if (onIncomingCallCallback != null) onIncomingCallCallback.accept(callData);
    }

    private void onCallConnected(String callData) {
        System.out.println("*** CALL CONNECTED ***");

        Platform.runLater(() -> {
            if (acceptButton != null) acceptButton.setDisable(true);
            if (rejectButton != null) rejectButton.setDisable(true);
            if (hangupButton != null) hangupButton.setDisable(false);
        });

        if (onCallConnectedCallback != null) onCallConnectedCallback.accept(callData);
    }

    private void onCallEnded(String callData) {
        System.out.println("*** CALL ENDED ***");

        Platform.runLater(() -> {
            if (acceptButton != null) acceptButton.setDisable(true);
            if (rejectButton != null) rejectButton.setDisable(true);
            if (hangupButton != null) hangupButton.setDisable(true);
        });

        if (onCallEndedCallback != null) onCallEndedCallback.accept(callData);
    }

    private void onAgentStateChange(String state) {
        System.out.println("*** AGENT STATE CHANGED: " + state + " ***");

        if (isFirstLogin && "Offline".equalsIgnoreCase(state)) {
            isFirstLogin = false;
            PauseTransition pause = new PauseTransition(Duration.millis(500));
            pause.setOnFinished(e -> setAgentAvailable());
            Platform.runLater(() -> pause.play());
            return;
        }

        if (isFirstLogin) isFirstLogin = false;
        currentAgentState = state;

        Platform.runLater(() -> {
            if (stateToggleButton != null) {
                if ("Available".equalsIgnoreCase(state)) {
                    stateToggleButton.setText("Offline");
                    updateButtonStyle(stateToggleButton, "#95A5A6", "#829191", "#6E7B7B");
                } else {
                    stateToggleButton.setText("Available");
                    updateButtonStyle(stateToggleButton, "#27AE60", "#1E9650", "#198246");
                    if (acceptButton != null) acceptButton.setDisable(true);
                    if (rejectButton != null) rejectButton.setDisable(true);
                    if (hangupButton != null) hangupButton.setDisable(true);
                }
            }
        });

        if (onAgentStateChangeCallback != null) onAgentStateChangeCallback.accept(state);
    }

    private void onNumpadReady() {
        System.out.println("*** NUMPAD READY ***");
        closeSplash();
        initialized = true;
        Platform.runLater(() -> {
            if (stateToggleButton != null) stateToggleButton.setDisable(false);
        });
        if (onReadyCallback != null) onReadyCallback.run();
    }

    private void updateCustomerCard(String name, String phone, String bgColor) {
        Platform.runLater(() -> {
            if (customerCardPanel != null) {
                customerCardPanel.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 10; -fx-border-color: " + bgColor + "; -fx-border-radius: 10; -fx-border-width: 2;");
            }
            if (customerNameLabel != null) customerNameLabel.setText(name != null ? name : "-");
            if (customerPhoneLabel != null) customerPhoneLabel.setText(phone != null ? phone : "-");
        });
    }

    private void resetCustomerCard() {
        Platform.runLater(() -> {
            if (customerCardPanel != null) {
                customerCardPanel.setStyle("-fx-background-color: #34495E; -fx-background-radius: 10; -fx-border-color: #465C6E; -fx-border-radius: 10; -fx-border-width: 2;");
            }
            if (customerNameLabel != null) customerNameLabel.setText("-");
            if (customerPhoneLabel != null) customerPhoneLabel.setText("-");
            isCallActive = false;
        });
    }

    // ==================== UTILITY ====================

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

    private String loadScript(String resourcePath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) return null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            return null;
        }
    }

    private void executeScript(String resourcePath) {
        String script = loadScript(resourcePath);
        if (script != null && browser_ != null) {
            browser_.executeJavaScript(script, browser_.getURL(), 0);
        }
    }
}
