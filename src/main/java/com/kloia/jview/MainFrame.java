package com.kloia.jview;

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
import org.cef.handler.CefFocusHandlerAdapter;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.cef.network.CefCookieManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class MainFrame extends JFrame {
    private static final long serialVersionUID = -5570653778104813836L;
    private final JTextField address_;
    private final CefApp cefApp_;
    private final CefClient client_;
    private final CefBrowser browser_;
    private final Component browserUI_;
    private boolean browserFocus_ = true;

    // Call control buttons
    private JButton acceptButton;
    private JButton rejectButton;
    private JButton hangupButton;
    private JButton availableButton;
    private JButton offlineButton;
    private JButton refreshButton;
    private JButton logoutButton;
    private JButton testButton;

    // Numpad panel reference
    private JPanel numpadPanel;

    // Customer info card components
    private JPanel customerCardPanel;
    private JLabel customerNameLabel;
    private JLabel customerPhoneLabel;
    private boolean isCallActive = false;
    private Timer cardResetTimer;

    // Phone input components
    private JTextField phoneNumberInput;
    private JButton dialButton;

    // Splash screen components
    private JDialog splashDialog;
    private JPanel mainContentPanel;
    private JLabel statusLabel;

    // AWS Connect credentials
    private static final String AWS_USERNAME = "o_ozcan";
    private static final String AWS_PASSWORD = "326748.k_AWS";
    private static final String AWS_CONNECT_URL = "https://kloia-nexent.my.connect.aws/ccp-v2";

    private MainFrame(String startURLAWS, String startURL, boolean useOSR, boolean isTransparent, String[] args)
            throws UnsupportedPlatformException, CefInitializationException, IOException, InterruptedException {

        // Set taskbar icon
        try {
            if (Taskbar.isTaskbarSupported()) {
                Taskbar taskbar = Taskbar.getTaskbar();
                java.net.URL iconURL = MainFrame.class.getClassLoader().getResource("klogo.png");
                if (iconURL != null) {
                    Image image = new ImageIcon(iconURL).getImage();
                    taskbar.setIconImage(image);
                }
            }
        } catch (UnsupportedOperationException e) {
            System.err.println("Cannot change dock icon: " + e.getMessage());
        }

        // CEF initialization
        CefAppBuilder builder = new CefAppBuilder();
        builder.addJcefArgs("--use-fake-ui-for-media-stream"); // Auto-accept all media permissions
        builder.addJcefArgs("--disable-popup-blocking");
        builder.addJcefArgs("--enable-features=NetworkService,NetworkServiceInProcess");
        builder.addJcefArgs("--disable-features=SameSiteByDefaultCookies,CookiesWithoutSameSiteMustBeSecure");

        builder.getCefSettings().windowless_rendering_enabled = useOSR;
        builder.getCefSettings().persist_session_cookies = true;

        // Persistent cache and cookies
        File cacheDir = new File("jcef_cache");
        if (!cacheDir.exists()) cacheDir.mkdirs();
        builder.getCefSettings().cache_path = cacheDir.getAbsolutePath();
        builder.getCefSettings().persist_session_cookies = true;

        builder.setAppHandler(new MavenCefAppHandlerAdapter() {
            @Override
            public void stateHasChanged(CefAppState state) {
                if (state == CefAppState.TERMINATED) System.exit(0);
            }
        });

        if (args.length > 0) builder.addJcefArgs(args);

        cefApp_ = builder.build();
        client_ = cefApp_.createClient();

        // Popup handler - redirect popups to main browser
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

                // Handle events based on type
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

        // Create browser
        browser_ = client_.createBrowser(startURLAWS, useOSR, isTransparent);
        browserUI_ = browser_.getUIComponent();

        browser_.executeJavaScript(
                "console.log = function(msg) { java.log(msg); };",
                browser_.getURL(), 0
        );

        // Address bar
        address_ = new JTextField(startURLAWS, 100);
        address_.addActionListener(e -> browser_.loadURL(address_.getText()));

        // Display handler for URL changes and console messages
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

        // Auto-login handler - fill login form when page loads
        client_.addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
                String url = browser.getURL();
                System.out.println("Page loaded: " + url);

                // Check if login page
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

                // Add AWS Connect Streams event listeners when CCP is loaded
                if (url.contains("ccp-v2") && !url.contains("login") && !url.contains("auth")) {
                    System.out.println("AWS Connect CCP loaded, adding event listeners...");
                    updateSplashStatus("Loading AWS Connect CCP...");

                    String connectEventsScript = loadScript("scripts/connectEventsScript.js");
                    if (connectEventsScript != null) {
                        browser.executeJavaScript(connectEventsScript, url, 0);
                    } else {
                        System.err.println("Failed to load connectEventsScript.js");
                    }
                }
            }
        });

        // ==================== CALL CONTROL BUTTONS ====================
        JButton acceptBtn = new JButton("Accept");
        acceptBtn.setBackground(new Color(46, 204, 113));
        acceptBtn.setForeground(Color.WHITE);
        acceptBtn.setFont(new Font("Arial", Font.BOLD, 14));
        acceptBtn.setEnabled(false);

        JButton rejectBtn = new JButton("Reject");
        rejectBtn.setBackground(new Color(231, 76, 60));
        rejectBtn.setForeground(Color.WHITE);
        rejectBtn.setFont(new Font("Arial", Font.BOLD, 14));
        rejectBtn.setEnabled(false);

        JButton hangupBtn = new JButton("Hang Up");
        hangupBtn.setBackground(new Color(192, 57, 43));
        hangupBtn.setForeground(Color.WHITE);
        hangupBtn.setFont(new Font("Arial", Font.BOLD, 14));
        hangupBtn.setEnabled(false);

        JButton availableBtn = new JButton("Available");
        availableBtn.setBackground(new Color(39, 174, 96));
        availableBtn.setForeground(Color.WHITE);
        availableBtn.setEnabled(false);

        JButton offlineBtn = new JButton("Offline");
        offlineBtn.setBackground(new Color(149, 165, 166));
        offlineBtn.setForeground(Color.WHITE);
        offlineBtn.setEnabled(false);

        JButton testBtn = new JButton("Test");
        testBtn.setBackground(new Color(155, 89, 182));
        testBtn.setForeground(Color.WHITE);
        testBtn.setFont(new Font("Arial", Font.BOLD, 14));
        testBtn.addActionListener(e -> {
            // Test data simulating incoming call
            String testCallData = "{\"customerName\":\"Test Müşteri\",\"phoneNumber\":\"+905551234567\"}";
            onIncomingCall(testCallData);
        });
        this.testButton = testBtn;

        // Accept call
        acceptBtn.addActionListener(e -> {
            System.out.println("Accept button clicked");
            executeScript("scripts/acceptCall.js");
        });

        // Reject call
        rejectBtn.addActionListener(e -> {
            System.out.println("Reject button clicked");
            executeScript("scripts/rejectCall.js");
        });

        // End call
        hangupBtn.addActionListener(e -> {
            System.out.println("Hangup button clicked");
            executeScript("scripts/hangupCall.js");
        });

        // Set agent state to Available
        availableBtn.addActionListener(e -> {
            System.out.println("Setting agent to Available");
            executeScript("scripts/setAgentAvailable.js");
        });

        // Set agent state to Offline
        offlineBtn.addActionListener(e -> {
            System.out.println("Setting agent to Offline");
            executeScript("scripts/setAgentOffline.js");
        });

        // Store buttons as instance variables for enabling/disabling
        this.acceptButton = acceptBtn;
        this.rejectButton = rejectBtn;
        this.hangupButton = hangupBtn;
        this.availableButton = availableBtn;
        this.offlineButton = offlineBtn;

        JButton refreshBtn = new JButton("Refresh");
        JButton logoutBtn = new JButton("Logout");
        refreshBtn.setEnabled(false);
        logoutBtn.setEnabled(false);
        logoutBtn.setVisible(false);
        this.refreshButton = refreshBtn;
        this.logoutButton = logoutBtn;

        refreshBtn.addActionListener(e -> browser_.reload());
        logoutBtn.addActionListener(e -> {
            File cacheDir2 = new File("jcef_cache");
            if (cacheDir2.exists()) {
                deleteDir(cacheDir2);
            }
            CefCookieManager cookieManager = CefCookieManager.getGlobalManager();
            if (cookieManager != null) {
                boolean success = cookieManager.deleteCookies("", "");
                System.out.println("Cookie cleanup status: " + success);
            }
            browser_.loadURL(startURLAWS);
        });

        // ==================== PHONE INPUT PANEL ====================
        JPanel phoneInputPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        phoneInputPanel.setBackground(new Color(44, 62, 80));

        JLabel phoneLabel = new JLabel("Phone:");
        phoneLabel.setForeground(Color.WHITE);
        phoneLabel.setFont(new Font("Arial", Font.BOLD, 12));

        phoneNumberInput = new JTextField(15);
        phoneNumberInput.setFont(new Font("Arial", Font.PLAIN, 14));
        phoneNumberInput.setToolTipText("E.164 format: +905321234567");
        phoneNumberInput.setText("+90");

        dialButton = new JButton("Call");
        dialButton.setBackground(new Color(46, 204, 113));
        dialButton.setForeground(Color.WHITE);
        dialButton.setFont(new Font("Arial", Font.BOLD, 14));
        dialButton.setEnabled(false);

        // Dial button action
        dialButton.addActionListener(e -> {
            String phoneNumber = phoneNumberInput.getText().trim();
            if (phoneNumber.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter a phone number", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (!phoneNumber.startsWith("+")) {
                JOptionPane.showMessageDialog(this, "Phone number must start with + (E.164 format)\nExample: +905321234567", "Invalid Format", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!phoneNumber.matches("\\+[0-9]{10,15}")) {
                JOptionPane.showMessageDialog(this, "Invalid E.164 format!\nMust be: +[country code][number]\nExample: +905321234567", "Invalid Format", JOptionPane.WARNING_MESSAGE);
                return;
            }
            initiateOutboundCall(phoneNumber);
        });

        // Enter key to dial
        phoneNumberInput.addActionListener(e -> dialButton.doClick());

        phoneInputPanel.add(phoneLabel);
        phoneInputPanel.add(phoneNumberInput);
        phoneInputPanel.add(dialButton);

        // Top panel with call controls
        JPanel callControlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        callControlPanel.add(acceptBtn);
        callControlPanel.add(rejectBtn);
        callControlPanel.add(hangupBtn);
        callControlPanel.add(new JSeparator(SwingConstants.VERTICAL));
        callControlPanel.add(availableBtn);
        callControlPanel.add(testBtn);
        callControlPanel.add(offlineBtn);

        JPanel topPanel = new JPanel(new BorderLayout(5, 0));
        topPanel.add(phoneInputPanel, BorderLayout.WEST);
        topPanel.add(callControlPanel, BorderLayout.CENTER);
        topPanel.add(refreshBtn, BorderLayout.EAST);

        address_.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (!browserFocus_) return;
                browserFocus_ = false;
                KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
                address_.requestFocus();
            }
        });

        // Disable right-click context menu
        client_.addContextMenuHandler(new CefContextMenuHandlerAdapter() {
            @Override
            public void onBeforeContextMenu(CefBrowser browser, CefFrame frame,
                                            CefContextMenuParams params, CefMenuModel model) {
                model.clear();
            }
        });

        client_.addFocusHandler(new CefFocusHandlerAdapter() {
            @Override
            public void onGotFocus(CefBrowser browser) {
                if (browserFocus_) return;
                browserFocus_ = true;
                KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
                browser.setFocus(true);
            }

            @Override
            public void onTakeFocus(CefBrowser browser, boolean next) {
                browserFocus_ = false;
            }
        });

        setTitle("Kloia");

        getContentPane().add(topPanel, BorderLayout.NORTH);

        // ==================== MAIN CONTENT PANEL ====================
        mainContentPanel = new JPanel(new BorderLayout(10, 10));
        mainContentPanel.setBackground(new Color(44, 62, 80));
        mainContentPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Numpad in center
        numpadPanel = createNumpadPanel();
        setNumpadEnabled(false); // Disabled until CCP login
        JPanel centerWrapper = new JPanel(new GridBagLayout());
        centerWrapper.setBackground(new Color(44, 62, 80));
        centerWrapper.add(numpadPanel);
        mainContentPanel.add(centerWrapper, BorderLayout.CENTER);

        // Customer info card on the left
        customerCardPanel = createCustomerCardPanel();
        mainContentPanel.add(customerCardPanel, BorderLayout.WEST);

//        // CCP browser in a separate off-screen window (must be visible for CEF to work)
//        JWindow browserWindow = new JWindow();
//        browserWindow.setSize(400, 400);
//        browserWindow.setLocation(-500, -500); // Off-screen
//        browserWindow.add(browserUI_);
//        browserWindow.setVisible(true);

        // CCP browser hidden (but still active for CEF to work)
        JPanel browserPanel = new JPanel(new BorderLayout());
        browserPanel.setPreferredSize(new Dimension(400, 400));
        browserPanel.setLocation(500,500);
//        browserPanel.setPreferredSize(new Dimension(331, 331));
        browserPanel.add(browserUI_, BorderLayout.CENTER);
        mainContentPanel.add(browserPanel, BorderLayout.EAST);


        // Add main content to frame
        getContentPane().add(mainContentPanel, BorderLayout.CENTER);
        getContentPane().setBackground(new Color(44, 62, 80));

        setSize(900, 650);
        setLocationRelativeTo(null); // Center on screen

        // Create and show splash dialog BEFORE main frame is visible
        splashDialog = createSplashDialog();
        splashDialog.setVisible(true);

        // Now show main frame (splash is already on top)
        setVisible(true);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dispose();
                System.exit(0);
            }
        });
    }

    // ==================== EVENT HANDLERS ====================

    /**
     * Called when an incoming call is detected
     */
    private void onIncomingCall(String callData) {
        System.out.println("*** INCOMING CALL ***");
        System.out.println("Call Data: " + callData);

        // Cancel any existing reset timer
        if (cardResetTimer != null) {
            cardResetTimer.stop();
            cardResetTimer = null;
        }

        // Parse JSON to get customer info
        String customerName = parseJsonField(callData, "customerName");
        String phoneNumber = parseJsonField(callData, "phoneNumber");

        SwingUtilities.invokeLater(() -> {
            if (!isCallActive) {
                // First click - Green state (incoming call)
                isCallActive = true;
                updateCustomerCard(customerName, phoneNumber, new Color(39, 174, 96)); // Green
            } else {
                // Second click - Red state (call ended/rejected simulation)
                updateCustomerCard(customerName, phoneNumber, new Color(231, 76, 60)); // Red

                // Start 10 second timer to reset card
                cardResetTimer = new Timer(2000, e -> {
                    resetCustomerCard();
                    cardResetTimer = null;
                });
                cardResetTimer.setRepeats(false);
                cardResetTimer.start();
            }

            // Enable buttons
            if (acceptButton != null) acceptButton.setEnabled(true);
            if (rejectButton != null) rejectButton.setEnabled(true);
            if (hangupButton != null) hangupButton.setEnabled(false);
        });
    }

    /**
     * Simple JSON field parser (for fields like "fieldName":"value")
     */
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

    /**
     * Called when a call is connected
     */
    private void onCallConnected(String callData) {
        System.out.println("*** CALL CONNECTED ***");
        System.out.println("Call Data: " + callData);

        SwingUtilities.invokeLater(() -> {
            if (acceptButton != null) acceptButton.setEnabled(false);
            if (rejectButton != null) rejectButton.setEnabled(false);
            if (hangupButton != null) hangupButton.setEnabled(true);
        });
    }

    /**
     * Called when a call ends
     */
    private void onCallEnded(String callData) {
        System.out.println("*** CALL ENDED ***");
        System.out.println("Call Data: " + callData);

        SwingUtilities.invokeLater(() -> {
            if (acceptButton != null) acceptButton.setEnabled(false);
            if (rejectButton != null) rejectButton.setEnabled(false);
            if (hangupButton != null) hangupButton.setEnabled(false);
        });
    }

    /**
     * Called when agent state changes (Available, Busy, Offline, etc.)
     */
    private void onAgentStateChange(String state) {
        System.out.println("*** AGENT STATE CHANGED: " + state + " ***");
    }

    /**
     * Called when CCP numpad is ready and visible
     */
    private void onNumpadReady() {
        System.out.println("*** NUMPAD READY ***");
        onCCPReady();
    }

    // ==================== SPLASH SCREEN ====================

    /**
     * Creates the splash dialog
     */
    private JDialog createSplashDialog() {
        JDialog dialog = new JDialog(this, "Loading...", false); // Non-modal
        dialog.setUndecorated(true);
        dialog.setSize(getWidth(), getHeight());
        dialog.setLocation(getLocation());

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(44, 62, 80));
        panel.setBorder(BorderFactory.createLineBorder(new Color(0, 115, 187), 2));

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(new Color(44, 62, 80));

        // Logo/Title
        JLabel titleLabel = new JLabel("KLOIA");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 48));
        titleLabel.setForeground(new Color(0, 115, 187));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Subtitle
        JLabel subtitleLabel = new JLabel("AWS Connect Agent Desktop");
        subtitleLabel.setFont(new Font("Arial", Font.PLAIN, 18));
        subtitleLabel.setForeground(Color.WHITE);
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Status label
        statusLabel = new JLabel("Connecting to AWS Connect...");
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        statusLabel.setForeground(new Color(150, 150, 150));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Progress indicator
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new Dimension(300, 10));
        progressBar.setMaximumSize(new Dimension(300, 10));
        progressBar.setBackground(new Color(52, 73, 94));
        progressBar.setForeground(new Color(0, 115, 187));
        progressBar.setBorderPainted(false);
        progressBar.setAlignmentX(Component.CENTER_ALIGNMENT);

        contentPanel.add(titleLabel);
        contentPanel.add(Box.createVerticalStrut(10));
        contentPanel.add(subtitleLabel);
        contentPanel.add(Box.createVerticalStrut(40));
        contentPanel.add(progressBar);
        contentPanel.add(Box.createVerticalStrut(15));
        contentPanel.add(statusLabel);

        panel.add(contentPanel);
        dialog.setContentPane(panel);
        return dialog;
    }

    /**
     * Updates the splash screen status message
     */
    private void updateSplashStatus(String message) {
        SwingUtilities.invokeLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText(message);
            }
        });
    }

    /**
     * Called when CCP is fully loaded and agent is connected
     */
    private void onCCPReady() {
        SwingUtilities.invokeLater(() -> {
            // Close splash dialog
            if (splashDialog != null) {
                splashDialog.dispose();
                splashDialog = null;
            }

            // Enable controls
            setNumpadEnabled(true);
            if (availableButton != null) availableButton.setEnabled(true);
            if (offlineButton != null) offlineButton.setEnabled(true);
            if (refreshButton != null) refreshButton.setEnabled(true);
            if (logoutButton != null) logoutButton.setEnabled(true);
            if (dialButton != null) dialButton.setEnabled(true);

            // Refresh UI
            revalidate();
            repaint();

            System.out.println("*** CCP READY - UI ENABLED ***");
        });
    }

    /**
     * Enables or disables all numpad buttons
     */
    private void setNumpadEnabled(boolean enabled) {
        if (numpadPanel == null) return;

        for (Component comp : numpadPanel.getComponents()) {
            if (comp instanceof JPanel) {
                for (Component innerComp : ((JPanel) comp).getComponents()) {
                    if (innerComp instanceof JButton) {
                        innerComp.setEnabled(enabled);
                    }
                }
            }
        }
    }

    // ==================== CUSTOMER CARD PANEL ====================

    /**
     * Creates the customer info card panel
     */
    private JPanel createCustomerCardPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(52, 73, 94));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(70, 90, 110), 2),
            BorderFactory.createEmptyBorder(20, 20, 20, 20)
        ));
        panel.setPreferredSize(new Dimension(250, 200));

        // Title
        JLabel titleLabel = new JLabel("Customer Info");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Customer name label
        customerNameLabel = new JLabel("-");
        customerNameLabel.setFont(new Font("Arial", Font.BOLD, 18));
        customerNameLabel.setForeground(Color.WHITE);
        customerNameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Phone number label
        customerPhoneLabel = new JLabel("-");
        customerPhoneLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        customerPhoneLabel.setForeground(new Color(189, 195, 199));
        customerPhoneLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(30));
        panel.add(customerNameLabel);
        panel.add(Box.createVerticalStrut(10));
        panel.add(customerPhoneLabel);
        panel.add(Box.createVerticalGlue());

        return panel;
    }

    /**
     * Updates the customer card with call information
     */
    private void updateCustomerCard(String customerName, String phoneNumber, Color backgroundColor) {
        SwingUtilities.invokeLater(() -> {
            if (customerCardPanel != null) {
                customerCardPanel.setBackground(backgroundColor);
            }
            if (customerNameLabel != null) {
                customerNameLabel.setText(customerName != null ? customerName : "-");
            }
            if (customerPhoneLabel != null) {
                customerPhoneLabel.setText(phoneNumber != null ? phoneNumber : "-");
            }
        });
    }

    /**
     * Resets the customer card to initial state
     */
    private void resetCustomerCard() {
        SwingUtilities.invokeLater(() -> {
            if (customerCardPanel != null) {
                customerCardPanel.setBackground(new Color(52, 73, 94));
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

    // ==================== NUMPAD PANEL ====================

    /**
     * Creates the numpad panel - AWS Connect style
     */
    private JPanel createNumpadPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(new Color(35, 47, 62));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0, 115, 187), 2),
            BorderFactory.createEmptyBorder(20, 25, 20, 25)
        ));

        // Title
        JLabel titleLabel = new JLabel("Dial Pad");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(titleLabel, BorderLayout.NORTH);

        // Numpad grid (4x3)
        JPanel gridPanel = new JPanel(new GridLayout(4, 3, 8, 8));
        gridPanel.setBackground(new Color(35, 47, 62));
        gridPanel.setBorder(BorderFactory.createEmptyBorder(15, 10, 15, 10));

        // Phone keypad format (with letters)
        String[][] buttons = {
            {"1", ""},
            {"2", "ABC"},
            {"3", "DEF"},
            {"4", "GHI"},
            {"5", "JKL"},
            {"6", "MNO"},
            {"7", "PQRS"},
            {"8", "TUV"},
            {"9", "WXYZ"},
            {"*", ""},
            {"0", "+"},
            {"#", ""}
        };

        for (String[] btnData : buttons) {
            String digit = btnData[0];
            String letters = btnData[1];

            JButton btn = new JButton();
            btn.setLayout(new BorderLayout());
            btn.setPreferredSize(new Dimension(70, 70));
            btn.setBackground(new Color(52, 73, 94));
            btn.setFocusPainted(false);
            btn.setBorder(BorderFactory.createLineBorder(new Color(70, 90, 110), 1));
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

            // Digit label
            JLabel digitLabel = new JLabel(digit);
            digitLabel.setFont(new Font("Arial", Font.BOLD, 28));
            digitLabel.setForeground(Color.WHITE);
            digitLabel.setHorizontalAlignment(SwingConstants.CENTER);

            // Letters label
            JLabel lettersLabel = new JLabel(letters);
            lettersLabel.setFont(new Font("Arial", Font.PLAIN, 9));
            lettersLabel.setForeground(new Color(150, 150, 150));
            lettersLabel.setHorizontalAlignment(SwingConstants.CENTER);

            btn.add(digitLabel, BorderLayout.CENTER);
            btn.add(lettersLabel, BorderLayout.SOUTH);

            btn.addActionListener(e -> {
                // Append digit to Java phone input
                if (phoneNumberInput != null) {
                    phoneNumberInput.setText(phoneNumberInput.getText() + digit);
                }
                // Send to CCP numpad
                sendDtmfDigit(digit);
            });

            // Hover effect
            btn.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    btn.setBackground(new Color(0, 115, 187));
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    btn.setBackground(new Color(52, 73, 94));
                }
                @Override
                public void mousePressed(MouseEvent e) {
                    btn.setBackground(new Color(0, 90, 150));
                }
                @Override
                public void mouseReleased(MouseEvent e) {
                    btn.setBackground(new Color(0, 115, 187));
                }
            });

            gridPanel.add(btn);
        }

        panel.add(gridPanel, BorderLayout.CENTER);

        // Bottom panel with Clear and Backspace
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        bottomPanel.setBackground(new Color(35, 47, 62));

        JButton clearBtn = new JButton("Clear");
        clearBtn.setBackground(new Color(231, 76, 60));
        clearBtn.setForeground(Color.WHITE);
        clearBtn.setFont(new Font("Arial", Font.BOLD, 12));
        clearBtn.setFocusPainted(false);
        clearBtn.addActionListener(e -> {
            if (phoneNumberInput != null) {
                phoneNumberInput.setText("+90");
            }
        });

        JButton backspaceBtn = new JButton("⌫");
        backspaceBtn.setBackground(new Color(52, 73, 94));
        backspaceBtn.setForeground(Color.WHITE);
        backspaceBtn.setFont(new Font("Arial", Font.BOLD, 16));
        backspaceBtn.setFocusPainted(false);
        backspaceBtn.addActionListener(e -> {
            if (phoneNumberInput != null) {
                String text = phoneNumberInput.getText();
                if (text.length() > 0) {
                    phoneNumberInput.setText(text.substring(0, text.length() - 1));
                }
            }
        });

        bottomPanel.add(clearBtn);
        bottomPanel.add(backspaceBtn);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * Initiates an outbound call to the specified phone number
     */
    private void initiateOutboundCall(String phoneNumber) {
        System.out.println("Initiating outbound call to: " + phoneNumber);
        executeScriptWithParams("scripts/outboundCall.js", "{{PHONE_NUMBER}}", phoneNumber);
    }

    /**
     * Simulates clicking a numpad button in CCP
     */
    private void sendDtmfDigit(String digit) {
        System.out.println("Numpad button pressed: " + digit);
        executeScriptWithParams("scripts/sendDtmf.js", "{{DIGIT}}", digit);
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Loads a JavaScript file from resources
     */
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

    /**
     * Executes a JavaScript file from resources
     */
    private void executeScript(String resourcePath) {
        String script = loadScript(resourcePath);
        if (script != null) {
            browser_.executeJavaScript(script, browser_.getURL(), 0);
        }
    }

    /**
     * Executes a JavaScript file with parameter replacement
     */
    private void executeScriptWithParams(String resourcePath, String placeholder, String value) {
        String script = loadScript(resourcePath);
        if (script != null) {
            script = script.replace(placeholder, value);
            browser_.executeJavaScript(script, browser_.getURL(), 0);
        }
    }

    /**
     * Recursively deletes a directory
     */
    public static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            for (File f : dir.listFiles()) {
                deleteDir(f);
            }
        }
        return dir.delete();
    }

    public static void main(String[] args) throws UnsupportedPlatformException, CefInitializationException, IOException, InterruptedException {
        String urlAWS = (args.length > 0) ? args[0] : System.getenv("AWSCONNECT_URL");
        if (urlAWS == null || urlAWS.isEmpty()) {
            urlAWS = AWS_CONNECT_URL; // Default AWS Connect URL
        }

        String url = (args.length > 0) ? args[1] : System.getenv("JVIEW_URL");
        if (url == null || url.isEmpty()) {
            // url = "http://localhost:3000";
        }

        boolean useOsr = false;
        String finalUrlAWS = urlAWS;
        String finalUrl = url;

        new MainFrame(finalUrlAWS, finalUrl, useOsr, false, args);
    }
}
