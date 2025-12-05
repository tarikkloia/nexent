package com.kloia.jview;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
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
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Headless AWS Connect CCP Service
 *
 * Runs JCEF browser in a hidden window. Provides API for call control
 * and callbacks for events. Use with KloiaConnectUI for visual components.
 *
 * Usage:
 * <pre>
 * KloiaConnectService service = new KloiaConnectService();
 * service.setOnAgentStateChange(state -> System.out.println("State: " + state));
 * service.start(success -> {
 *     if (success) {
 *         // Service is ready
 *     }
 * });
 * </pre>
 */
public class KloiaConnectService {

    // JCEF components
    private CefApp cefApp_;
    private CefClient client_;
    private CefBrowser browser_;
    private Component browserUI_;

    // Hidden window for JCEF
    private JFrame hiddenFrame;

    // State tracking
    private boolean isFirstLogin = true;
    private String currentAgentState = "";
    private boolean initialized = false;
    private boolean isCallActive = false;
    private boolean ccpWindowVisible = false;

    // Callbacks
    private Consumer<String> onIncomingCallCallback;
    private Consumer<String> onCallConnectedCallback;
    private Consumer<String> onCallEndedCallback;
    private Consumer<String> onAgentStateChangeCallback;
    private Runnable onReadyCallback;
    private Consumer<String> onStatusChangeCallback;

    // AWS Connect credentials
    private String awsUsername = "o_ozcan";
    private String awsPassword = "326748.k_AWS";
    private String awsConnectUrl = "https://kloia-nexent.my.connect.aws/ccp-v2";

    public KloiaConnectService() {
        // Initialize JavaFX toolkit
        new JFXPanel();
    }

    // ==================== PUBLIC API ====================

    /**
     * Set AWS credentials before starting
     */
    public void setCredentials(String username, String password, String connectUrl) {
        this.awsUsername = username;
        this.awsPassword = password;
        this.awsConnectUrl = connectUrl;
    }

    /**
     * Set CCP window visibility (default: hidden)
     * @param visible true to show CCP window, false to hide
     */
    public void setCcpWindowVisible(boolean visible) {
        this.ccpWindowVisible = visible;
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
     * Set callback for when service is ready
     */
    public void setOnReady(Runnable callback) {
        this.onReadyCallback = callback;
    }

    /**
     * Set callback for status updates (for loading progress)
     */
    public void setOnStatusChange(Consumer<String> callback) {
        this.onStatusChangeCallback = callback;
    }

    /**
     * Start the service (initializes JCEF in hidden window)
     * @param onComplete Callback with success status
     */
    public void start(Consumer<Boolean> onComplete) {
        updateStatus("Starting service...");

        new Thread(() -> {
            try {
                initializeCEF();
                SwingUtilities.invokeLater(() -> createHiddenWindow());

                if (onComplete != null) {
                    onComplete.accept(true);
                }
            } catch (Exception e) {
                e.printStackTrace();
                updateStatus("Error: " + e.getMessage());
                if (onComplete != null) {
                    onComplete.accept(false);
                }
            }
        }).start();
    }

    /**
     * Check if service is initialized and ready
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
     * Check if a call is currently active
     */
    public boolean isCallActive() {
        return isCallActive;
    }

    /**
     * Accept incoming call
     */
    public void acceptCall() {
        executeScript("scripts/acceptCall.js");
    }

    /**
     * Reject incoming call
     */
    public void rejectCall() {
        executeScript("scripts/rejectCall.js");
    }

    /**
     * Hang up current call
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
     * Stop and dispose the service
     */
    public void stop() {
        if (hiddenFrame != null) {
            hiddenFrame.dispose();
        }
        if (cefApp_ != null) {
            cefApp_.dispose();
        }
    }

    // ==================== PRIVATE METHODS ====================

    private void updateStatus(String status) {
        System.out.println("[KloiaConnectService] " + status);
        if (onStatusChangeCallback != null) {
            Platform.runLater(() -> onStatusChangeCallback.accept(status));
        }
    }

    private void initializeCEF() throws Exception {
        updateStatus("Initializing browser engine...");

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
                System.out.println("[KloiaConnectService] EVENT: " + request);

                if (request.startsWith("INCOMING_CALL:")) {
                    String callData = request.substring("INCOMING_CALL:".length());
                    handleIncomingCall(callData);
                } else if (request.startsWith("CALL_CONNECTED:")) {
                    String callData = request.substring("CALL_CONNECTED:".length());
                    handleCallConnected(callData);
                } else if (request.startsWith("CALL_ENDED:")) {
                    String callData = request.substring("CALL_ENDED:".length());
                    handleCallEnded(callData);
                } else if (request.startsWith("AGENT_STATE:")) {
                    String state = request.substring("AGENT_STATE:".length());
                    handleAgentStateChange(state);
                } else if (request.equals("NUMPAD_READY")) {
                    handleNumpadReady();
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
                System.out.println("[KloiaConnectService] Navigated to: " + url);
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
                    updateStatus("Signing in...");
                    String script = loadScript("scripts/autoLogin.js");
                    if (script != null) {
                        script = script.replace("{{USERNAME}}", awsUsername)
                                .replace("{{PASSWORD}}", awsPassword);
                        browser.executeJavaScript(script, url, 0);
                    }
                }

                if (url.contains("ccp-v2") && !url.contains("login") && !url.contains("auth")) {
                    updateStatus("Loading AWS Connect CCP...");
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

        updateStatus("Connecting to AWS Connect...");
        browser_ = client_.createBrowser(awsConnectUrl, false, false);
        browserUI_ = browser_.getUIComponent();
    }

    private void createHiddenWindow() {
        if (ccpWindowVisible) {
            // Visible mode: show as normal window
            hiddenFrame = new JFrame("AWS Connect CCP");
            hiddenFrame.setSize(420, 600);
            hiddenFrame.setResizable(false);
            hiddenFrame.setLocationRelativeTo(null);
            hiddenFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        } else {
            // Hidden mode: off-screen, no taskbar, no Alt+Tab
            hiddenFrame = new JFrame();
            hiddenFrame.setType(Window.Type.UTILITY);
            hiddenFrame.setUndecorated(true);
            hiddenFrame.setSize(400, 400);
            hiddenFrame.setLocation(-2000, -2000);
        }

        JPanel browserPanel = new JPanel(new BorderLayout());
        if (browserUI_ != null) {
            browserPanel.add(browserUI_, BorderLayout.CENTER);
        }

        hiddenFrame.add(browserPanel);
        hiddenFrame.setVisible(true); // Must be visible for JCEF to work
    }

    // ==================== EVENT HANDLERS ====================

    private void handleIncomingCall(String callData) {
        isCallActive = true;
        updateStatus("Incoming call!");
        if (onIncomingCallCallback != null) {
            Platform.runLater(() -> onIncomingCallCallback.accept(callData));
        }
    }

    private void handleCallConnected(String callData) {
        updateStatus("Call connected");
        if (onCallConnectedCallback != null) {
            Platform.runLater(() -> onCallConnectedCallback.accept(callData));
        }
    }

    private void handleCallEnded(String callData) {
        isCallActive = false;
        updateStatus("Call ended");
        if (onCallEndedCallback != null) {
            Platform.runLater(() -> onCallEndedCallback.accept(callData));
        }
    }

    private void handleAgentStateChange(String state) {
        // Auto-available on first login if offline
        if (isFirstLogin && "Offline".equalsIgnoreCase(state)) {
            isFirstLogin = false;
            PauseTransition pause = new PauseTransition(Duration.millis(500));
            pause.setOnFinished(e -> setAgentAvailable());
            Platform.runLater(() -> pause.play());
            return;
        }

        if (isFirstLogin) isFirstLogin = false;
        currentAgentState = state;
        updateStatus("Agent: " + state);

        if (onAgentStateChangeCallback != null) {
            Platform.runLater(() -> onAgentStateChangeCallback.accept(state));
        }
    }

    private void handleNumpadReady() {
        initialized = true;
        updateStatus("Ready");
        if (onReadyCallback != null) {
            Platform.runLater(() -> onReadyCallback.run());
        }
    }

    // ==================== UTILITY ====================

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
