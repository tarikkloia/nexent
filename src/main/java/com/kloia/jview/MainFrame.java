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
import java.io.File;
import java.io.IOException;

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

    // Numpad panel reference
    private JPanel numpadPanel;

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

                    String script = "setTimeout(function() {\n" +
                        "  console.log('AUTO LOGIN: Starting...');\n" +
                        "  \n" +
                        "  // AWS Identity Center specific fields\n" +
                        "  var userField = document.getElementById('wdc_username');\n" +
                        "  var passField = document.getElementById('wdc_password');\n" +
                        "  \n" +
                        "  console.log('AUTO LOGIN: userField=' + (userField ? 'found' : 'null') + ', passField=' + (passField ? 'found' : 'null'));\n" +
                        "  \n" +
                        "  if (userField && passField) {\n" +
                        "    userField.focus();\n" +
                        "    userField.value = '" + AWS_USERNAME + "';\n" +
                        "    userField.dispatchEvent(new Event('input', {bubbles: true}));\n" +
                        "    userField.dispatchEvent(new Event('change', {bubbles: true}));\n" +
                        "    console.log('AUTO LOGIN: Username filled: ' + userField.value);\n" +
                        "    \n" +
                        "    passField.focus();\n" +
                        "    passField.value = '" + AWS_PASSWORD + "';\n" +
                        "    passField.dispatchEvent(new Event('input', {bubbles: true}));\n" +
                        "    passField.dispatchEvent(new Event('change', {bubbles: true}));\n" +
                        "    console.log('AUTO LOGIN: Password filled');\n" +
                        "    \n" +
                        "    setTimeout(function() {\n" +
                        "      var btns = document.querySelectorAll('button');\n" +
                        "      console.log('AUTO LOGIN: Found ' + btns.length + ' buttons');\n" +
                        "      for (var j = 0; j < btns.length; j++) {\n" +
                        "        console.log('AUTO LOGIN: Button ' + j + ' text=' + btns[j].innerText);\n" +
                        "        var txt = btns[j].innerText.toLowerCase();\n" +
                        "        if (txt.indexOf('sign in') >= 0 || txt.indexOf('login') >= 0 || txt.indexOf('submit') >= 0) {\n" +
                        "          console.log('AUTO LOGIN: Clicking button: ' + btns[j].innerText);\n" +
                        "          btns[j].click();\n" +
                        "          break;\n" +
                        "        }\n" +
                        "      }\n" +
                        "    }, 500);\n" +
                        "  } else {\n" +
                        "    console.log('AUTO LOGIN: Fields not found, retrying in 2 seconds...');\n" +
                        "    setTimeout(arguments.callee, 2000);\n" +
                        "  }\n" +
                        "}, 3000);";

                    browser.executeJavaScript(script, url, 0);
                }

                // Add AWS Connect Streams event listeners when CCP is loaded
                if (url.contains("ccp-v2") && !url.contains("login") && !url.contains("auth")) {
                    System.out.println("AWS Connect CCP loaded, adding event listeners...");
                    updateSplashStatus("Loading AWS Connect CCP...");

                    String connectEventsScript = "setTimeout(function() {\n" +
                        "  console.log('AWS CONNECT: Setting up event listeners...');\n" +
                        "  \n" +
                        "  // Check if connect global object exists\n" +
                        "  if (typeof connect !== 'undefined' && connect.contact) {\n" +
                        "    console.log('AWS CONNECT: connect object found, subscribing to events...');\n" +
                        "    \n" +
                        "    // Global contact reference for Java button control\n" +
                        "    window.currentContact = null;\n" +
                        "    \n" +
                        "    // Contact (call) events\n" +
                        "    connect.contact(function(contact) {\n" +
                        "      console.log('AWS CONNECT: New contact detected!');\n" +
                        "      window.currentContact = contact;\n" +
                        "      \n" +
                        "      var contactId = contact.getContactId();\n" +
                        "      var queue = contact.getQueue();\n" +
                        "      var queueName = queue ? queue.name : 'Unknown';\n" +
                        "      \n" +
                        "      // Incoming call\n" +
                        "      contact.onIncoming(function(contact) {\n" +
                        "        console.log('AWS CONNECT: INCOMING CALL!');\n" +
                        "        window.currentContact = contact;\n" +
                        "        var conn = contact.getInitialConnection();\n" +
                        "        var phoneNumber = conn ? conn.getEndpoint().phoneNumber : 'Unknown';\n" +
                        "        var data = JSON.stringify({contactId: contactId, phoneNumber: phoneNumber, queue: queueName, type: 'incoming'});\n" +
                        "        window.cefQuery({request: 'INCOMING_CALL:' + data});\n" +
                        "      });\n" +
                        "      \n" +
                        "      // Call connected\n" +
                        "      contact.onConnected(function(contact) {\n" +
                        "        console.log('AWS CONNECT: CALL CONNECTED!');\n" +
                        "        var conn = contact.getInitialConnection();\n" +
                        "        var phoneNumber = conn ? conn.getEndpoint().phoneNumber : 'Unknown';\n" +
                        "        var data = JSON.stringify({contactId: contactId, phoneNumber: phoneNumber, queue: queueName, type: 'connected'});\n" +
                        "        window.cefQuery({request: 'CALL_CONNECTED:' + data});\n" +
                        "      });\n" +
                        "      \n" +
                        "      // Call accepted (agent answered)\n" +
                        "      contact.onAccepted(function(contact) {\n" +
                        "        console.log('AWS CONNECT: CALL ACCEPTED!');\n" +
                        "        var data = JSON.stringify({contactId: contactId, type: 'accepted'});\n" +
                        "        window.cefQuery({request: 'CALL_ACCEPTED:' + data});\n" +
                        "      });\n" +
                        "      \n" +
                        "      // Call ended\n" +
                        "      contact.onEnded(function(contact) {\n" +
                        "        console.log('AWS CONNECT: CALL ENDED!');\n" +
                        "        window.currentContact = null;\n" +
                        "        var data = JSON.stringify({contactId: contactId, type: 'ended'});\n" +
                        "        window.cefQuery({request: 'CALL_ENDED:' + data});\n" +
                        "      });\n" +
                        "      \n" +
                        "      // Missed call\n" +
                        "      contact.onMissed(function(contact) {\n" +
                        "        console.log('AWS CONNECT: CALL MISSED!');\n" +
                        "        window.currentContact = null;\n" +
                        "        var data = JSON.stringify({contactId: contactId, type: 'missed'});\n" +
                        "        window.cefQuery({request: 'CALL_MISSED:' + data});\n" +
                        "      });\n" +
                        "    });\n" +
                        "    \n" +
                        "    // Agent state events\n" +
                        "    connect.agent(function(agent) {\n" +
                        "      console.log('AWS CONNECT: Agent connected');\n" +
                        "      window.currentAgent = agent;\n" +
                        "      \n" +
                        "      agent.onStateChange(function(agentStateChange) {\n" +
                        "        var newState = agentStateChange.newState;\n" +
                        "        var oldState = agentStateChange.oldState;\n" +
                        "        console.log('AWS CONNECT: Agent state changed from ' + oldState + ' to ' + newState);\n" +
                        "        window.cefQuery({request: 'AGENT_STATE:' + newState});\n" +
                        "      });\n" +
                        "      \n" +
                        "      // Initial state\n" +
                        "      var currentState = agent.getState().name;\n" +
                        "      console.log('AWS CONNECT: Initial agent state: ' + currentState);\n" +
                        "      window.cefQuery({request: 'AGENT_STATE:' + currentState});\n" +
                        "    });\n" +
                        "    \n" +
                        "    console.log('AWS CONNECT: Event listeners setup complete!');\n" +
                        "    \n" +
                        "    // Auto-click numpad button if not already on numpad screen\n" +
                        "    setTimeout(function() {\n" +
                        "      function clickNumpadButton() {\n" +
                        "        var buttons = document.querySelectorAll('button');\n" +
                        "        for (var i = 0; i < buttons.length; i++) {\n" +
                        "          var btn = buttons[i];\n" +
                        "          var text = btn.innerText.toLowerCase();\n" +
                        "          var ariaLabel = (btn.getAttribute('aria-label') || '').toLowerCase();\n" +
                        "          if (text.indexOf('number') >= 0 || text.indexOf('numpad') >= 0 || text.indexOf('dialpad') >= 0 || text.indexOf('dial pad') >= 0 ||\n" +
                        "              ariaLabel.indexOf('number') >= 0 || ariaLabel.indexOf('numpad') >= 0 || ariaLabel.indexOf('dialpad') >= 0) {\n" +
                        "            console.log('AWS CONNECT: Clicking numpad button: ' + btn.innerText);\n" +
                        "            btn.click();\n" +
                        "            return true;\n" +
                        "          }\n" +
                        "        }\n" +
                        "        // Also try to find by icon or class\n" +
                        "        var dialpadBtn = document.querySelector('[class*=\"dialpad\"], [class*=\"numpad\"], [class*=\"numberpad\"]');\n" +
                        "        if (dialpadBtn) {\n" +
                        "          console.log('AWS CONNECT: Clicking numpad by class');\n" +
                        "          dialpadBtn.click();\n" +
                        "          return true;\n" +
                        "        }\n" +
                        "        return false;\n" +
                        "      }\n" +
                        "      \n" +
                        "      // Check if numpad is already visible (look for digit buttons)\n" +
                        "      var hasNumpad = false;\n" +
                        "      var allBtns = document.querySelectorAll('button');\n" +
                        "      for (var j = 0; j < allBtns.length; j++) {\n" +
                        "        if (allBtns[j].innerText.trim() === '1' || allBtns[j].innerText.indexOf('1') === 0) {\n" +
                        "          hasNumpad = true;\n" +
                        "          break;\n" +
                        "        }\n" +
                        "      }\n" +
                        "      \n" +
                        "      if (!hasNumpad) {\n" +
                        "        console.log('AWS CONNECT: Numpad not visible, trying to open it...');\n" +
                        "        clickNumpadButton();\n" +
                        "        // Wait for numpad to appear then notify Java\n" +
                        "        setTimeout(function checkNumpad() {\n" +
                        "          var btns = document.querySelectorAll('button');\n" +
                        "          for (var k = 0; k < btns.length; k++) {\n" +
                        "            if (btns[k].innerText.trim() === '1' || btns[k].innerText.indexOf('1') === 0) {\n" +
                        "              console.log('AWS CONNECT: Numpad is now ready!');\n" +
                        "              window.cefQuery({request: 'NUMPAD_READY'});\n" +
                        "              return;\n" +
                        "            }\n" +
                        "          }\n" +
                        "          console.log('AWS CONNECT: Waiting for numpad...');\n" +
                        "          setTimeout(checkNumpad, 500);\n" +
                        "        }, 1000);\n" +
                        "      } else {\n" +
                        "        console.log('AWS CONNECT: Numpad already visible');\n" +
                        "        window.cefQuery({request: 'NUMPAD_READY'});\n" +
                        "      }\n" +
                        "    }, 2000);\n" +
                        "    \n" +
                        "  } else {\n" +
                        "    console.log('AWS CONNECT: connect object not found, retrying in 2 seconds...');\n" +
                        "    setTimeout(arguments.callee, 2000);\n" +
                        "  }\n" +
                        "}, 3000);";

                    browser.executeJavaScript(connectEventsScript, url, 0);
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

        // Accept call
        acceptBtn.addActionListener(e -> {
            System.out.println("Accept button clicked");
            browser_.executeJavaScript(
                "if (window.currentContact) { " +
                "  window.currentContact.accept(); " +
                "  console.log('Call accepted via Java button'); " +
                "}", browser_.getURL(), 0);
        });

        // Reject call
        rejectBtn.addActionListener(e -> {
            System.out.println("Reject button clicked");
            browser_.executeJavaScript(
                "if (window.currentContact) { " +
                "  window.currentContact.reject(); " +
                "  console.log('Call rejected via Java button'); " +
                "}", browser_.getURL(), 0);
        });

        // End call
        hangupBtn.addActionListener(e -> {
            System.out.println("Hangup button clicked");
            browser_.executeJavaScript(
                "if (window.currentContact) { " +
                "  var conn = window.currentContact.getAgentConnection(); " +
                "  if (conn) conn.destroy(); " +
                "  console.log('Call ended via Java button'); " +
                "}", browser_.getURL(), 0);
        });

        // Set agent state to Available
        availableBtn.addActionListener(e -> {
            System.out.println("Setting agent to Available");
            browser_.executeJavaScript(
                "if (typeof connect !== 'undefined') { " +
                "  connect.agent(function(agent) { " +
                "    var states = agent.getAgentStates(); " +
                "    var availableState = states.find(function(s) { return s.name === 'Available'; }); " +
                "    if (availableState) { " +
                "      agent.setState(availableState, { " +
                "        success: function() { console.log('Agent set to Available'); }, " +
                "        failure: function() { console.log('Failed to set Available'); } " +
                "      }); " +
                "    } " +
                "  }); " +
                "}", browser_.getURL(), 0);
        });

        // Set agent state to Offline
        offlineBtn.addActionListener(e -> {
            System.out.println("Setting agent to Offline");
            browser_.executeJavaScript(
                "if (typeof connect !== 'undefined') { " +
                "  connect.agent(function(agent) { " +
                "    var states = agent.getAgentStates(); " +
                "    var offlineState = states.find(function(s) { return s.name === 'Offline'; }); " +
                "    if (offlineState) { " +
                "      agent.setState(offlineState, { " +
                "        success: function() { console.log('Agent set to Offline'); }, " +
                "        failure: function() { console.log('Failed to set Offline'); } " +
                "      }); " +
                "    } " +
                "  }); " +
                "}", browser_.getURL(), 0);
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

        // Top panel with call controls
        JPanel callControlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        callControlPanel.add(acceptBtn);
        callControlPanel.add(rejectBtn);
        callControlPanel.add(hangupBtn);
        callControlPanel.add(new JSeparator(SwingConstants.VERTICAL));
        callControlPanel.add(availableBtn);
        callControlPanel.add(offlineBtn);

        JPanel topPanel = new JPanel(new BorderLayout(5, 0));
        topPanel.add(callControlPanel, BorderLayout.CENTER);
        topPanel.add(refreshBtn, BorderLayout.WEST);
        topPanel.add(logoutBtn, BorderLayout.EAST);

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

        // CCP browser hidden (but still active for CEF to work)
        JPanel browserPanel = new JPanel(new BorderLayout());
        browserPanel.setPreferredSize(new Dimension(331, 331));
        browserPanel.add(browserUI_, BorderLayout.CENTER);
        mainContentPanel.add(browserPanel, BorderLayout.EAST);

        // Add main content to frame
        getContentPane().add(mainContentPanel, BorderLayout.CENTER);
        getContentPane().setBackground(new Color(44, 62, 80));

        setSize(900, 650);
        setLocationRelativeTo(null); // Center on screen
        setVisible(true);

        // Create splash dialog on top of main frame
        splashDialog = createSplashDialog();
        splashDialog.setVisible(true);

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

        // Enable buttons
        SwingUtilities.invokeLater(() -> {
            if (acceptButton != null) acceptButton.setEnabled(true);
            if (rejectButton != null) rejectButton.setEnabled(true);
            if (hangupButton != null) hangupButton.setEnabled(false);
        });
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

            btn.addActionListener(e -> sendDtmfDigit(digit));

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

        return panel;
    }

    /**
     * Simulates clicking a numpad button in CCP
     */
    private void sendDtmfDigit(String digit) {
        System.out.println("Numpad button pressed: " + digit);

        String script =
            "(function() {\n" +
            "  var allButtons = document.querySelectorAll('button');\n" +
            "  for (var i = 0; i < allButtons.length; i++) {\n" +
            "    var btn = allButtons[i];\n" +
            "    var text = btn.innerText.trim();\n" +
            "    if (text === '" + digit + "' || text.indexOf('" + digit + "') === 0) {\n" +
            "      btn.click();\n" +
            "      console.log('DTMF: " + digit + "');\n" +
            "      return;\n" +
            "    }\n" +
            "  }\n" +
            "})();\n";

        browser_.executeJavaScript(script, browser_.getURL(), 0);
    }

    // ==================== UTILITY METHODS ====================

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
