package com.kloia.jview;

import me.friwi.jcefmaven.*;
import org.cef.CefApp;
import org.cef.CefApp.CefAppState;
import org.cef.CefBrowserSettings;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

public class MainFrame extends JFrame {
    private static final long serialVersionUID = -5570653778104813836L;
    private final JTextField address_;
    private final CefApp cefApp_;
    private final CefClient client_;
    private final CefBrowser browser_;
    private final Component browserUI_;
    private boolean browserFocus_ = true;
    private boolean loginAttempted_ = false;

    // Call control buttons
    private JButton acceptButton;
    private JButton rejectButton;
    private JButton hangupButton;

    // AWS Connect credentials
    private static final String AWS_USERNAME = "o_ozcan";
    private static final String AWS_PASSWORD = "326748.k_AWS";
    private static final String AWS_CONNECT_URL = "https://kloia-nexent.my.connect.aws/ccp-v2";

    private MainFrame(String startURLAWS,String startURL, boolean useOSR, boolean isTransparent, String[] args)
            throws UnsupportedPlatformException, CefInitializationException, IOException, InterruptedException {
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
            System.err.println("Dock icon değiştirilemiyor: " + e.getMessage());
        }
        // CEF initialization
        CefAppBuilder builder = new CefAppBuilder();
        builder.addJcefArgs("--use-fake-ui-for-media-stream"); // tüm medya izinlerini otomatik kabul eder
        builder.addJcefArgs("--disable-popup-blocking");
        builder.addJcefArgs("--enable-features=NetworkService,NetworkServiceInProcess");
        builder.addJcefArgs("--disable-features=SameSiteByDefaultCookies,CookiesWithoutSameSiteMustBeSecure");



        builder.getCefSettings().windowless_rendering_enabled = useOSR;

        builder.getCefSettings().persist_session_cookies = true;
//        builder.getCefSettings().cache_path = "jcef_cache"; // cookie ve cache kalıcı olur
        // Kalıcı cache ve cookie
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
        client_.addLifeSpanHandler(new org.cef.handler.CefLifeSpanHandlerAdapter() {
            @Override
            public boolean onBeforePopup(org.cef.browser.CefBrowser browser,
                                         org.cef.browser.CefFrame frame,
                                         String target_url,
                                         String target_frame_name) {
                System.out.println("Popup engellendi, yönlendiriliyor: " + target_url);
                SwingUtilities.invokeLater(() -> browser.loadURL(target_url));
                return true;
            }
        });


        // Message router (CEF-JS iletişimi için)
        CefMessageRouter msgRouter = CefMessageRouter.create();
        msgRouter.addHandler(new CefMessageRouterHandlerAdapter() {
            @Override
            public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request,
                                  boolean persistent, CefQueryCallback callback) {
                System.out.println("===========================================");
                System.out.println("EVENT FROM AWS CONNECT: " + request);
                System.out.println("===========================================");

                // Event tipine göre işlem yap
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
                }

                callback.success("Event received");
                return true;
            }
        }, true);
        client_.addMessageRouter(msgRouter);


//        CefBrowser popup = client_.createBrowser(startURLAWS, false, false);

        // Browser oluşturma
        browser_ = client_.createBrowser(startURLAWS, useOSR, isTransparent);
        browserUI_ = browser_.getUIComponent();

        browser_.executeJavaScript(
                "console.log = function(msg) { java.log(msg); };",
                browser_.getURL(), 0
        );

        // Adres çubuğu
        address_ = new JTextField(startURLAWS, 100);
        address_.addActionListener(e -> browser_.loadURL(address_.getText()));

        // Browser URL değiştiğinde adres çubuğunu güncelle
//        client_.addDisplayHandler(new CefDisplayHandlerAdapter() {
//            @Override
//            public void onAddressChange(CefBrowser browser, CefFrame frame, String url) {
//                address_.setText(url);
//            }
//        });
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

        // Auto-login handler - sayfa yüklendiğinde login formunu doldur
        client_.addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadEnd(CefBrowser browser, CefFrame frame, int httpStatusCode) {
                String url = browser.getURL();
                System.out.println("Page loaded: " + url);

                // Login sayfası kontrolü
                boolean isLoginPage = url.contains("signin") || url.contains("login") ||
                                     url.contains("oauth") || url.contains("authenticate") ||
                                     url.contains("awsapps.com/auth") || url.contains("/auth/") ||
                                     url.contains("/auth?") || url.contains("sso");

                if (isLoginPage) {
                    System.out.println("Login page detected, executing auto-login script...");

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

                // CCP yüklendiyse AWS Connect Streams event listener'larını ekle
                if (url.contains("ccp-v2") && !url.contains("login") && !url.contains("auth")) {
                    System.out.println("AWS Connect CCP yüklendi, event listener'lar ekleniyor...");

                    String connectEventsScript = "setTimeout(function() {\n" +
                        "  console.log('AWS CONNECT: Setting up event listeners...');\n" +
                        "  \n" +
                        "  // connect global objesi var mı kontrol et\n" +
                        "  if (typeof connect !== 'undefined' && connect.contact) {\n" +
                        "    console.log('AWS CONNECT: connect object found, subscribing to events...');\n" +
                        "    \n" +
                        "    // Global contact reference for Java button control\n" +
                        "    window.currentContact = null;\n" +
                        "    \n" +
                        "    // Contact (arama) event'leri\n" +
                        "    connect.contact(function(contact) {\n" +
                        "      console.log('AWS CONNECT: New contact detected!');\n" +
                        "      window.currentContact = contact;\n" +
                        "      \n" +
                        "      var contactId = contact.getContactId();\n" +
                        "      var queue = contact.getQueue();\n" +
                        "      var queueName = queue ? queue.name : 'Unknown';\n" +
                        "      \n" +
                        "      // Gelen arama (incoming)\n" +
                        "      contact.onIncoming(function(contact) {\n" +
                        "        console.log('AWS CONNECT: INCOMING CALL!');\n" +
                        "        window.currentContact = contact;\n" +
                        "        var conn = contact.getInitialConnection();\n" +
                        "        var phoneNumber = conn ? conn.getEndpoint().phoneNumber : 'Unknown';\n" +
                        "        var data = JSON.stringify({contactId: contactId, phoneNumber: phoneNumber, queue: queueName, type: 'incoming'});\n" +
                        "        window.cefQuery({request: 'INCOMING_CALL:' + data});\n" +
                        "      });\n" +
                        "      \n" +
                        "      // Arama bağlandı\n" +
                        "      contact.onConnected(function(contact) {\n" +
                        "        console.log('AWS CONNECT: CALL CONNECTED!');\n" +
                        "        var conn = contact.getInitialConnection();\n" +
                        "        var phoneNumber = conn ? conn.getEndpoint().phoneNumber : 'Unknown';\n" +
                        "        var data = JSON.stringify({contactId: contactId, phoneNumber: phoneNumber, queue: queueName, type: 'connected'});\n" +
                        "        window.cefQuery({request: 'CALL_CONNECTED:' + data});\n" +
                        "      });\n" +
                        "      \n" +
                        "      // Arama kabul edildi (agent cevapladı)\n" +
                        "      contact.onAccepted(function(contact) {\n" +
                        "        console.log('AWS CONNECT: CALL ACCEPTED!');\n" +
                        "        var data = JSON.stringify({contactId: contactId, type: 'accepted'});\n" +
                        "        window.cefQuery({request: 'CALL_ACCEPTED:' + data});\n" +
                        "      });\n" +
                        "      \n" +
                        "      // Arama sonlandı\n" +
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
                        "    // Agent durumu event'leri\n" +
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
                        "      // İlk durum\n" +
                        "      var currentState = agent.getState().name;\n" +
                        "      console.log('AWS CONNECT: Initial agent state: ' + currentState);\n" +
                        "      window.cefQuery({request: 'AGENT_STATE:' + currentState});\n" +
                        "    });\n" +
                        "    \n" +
                        "    console.log('AWS CONNECT: Event listeners setup complete!');\n" +
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
        JButton acceptBtn = new JButton("✓ Cevapla");
        acceptBtn.setBackground(new Color(46, 204, 113));
        acceptBtn.setForeground(Color.WHITE);
        acceptBtn.setFont(new Font("Arial", Font.BOLD, 14));
        acceptBtn.setEnabled(false);

        JButton rejectBtn = new JButton("✗ Reddet");
        rejectBtn.setBackground(new Color(231, 76, 60));
        rejectBtn.setForeground(Color.WHITE);
        rejectBtn.setFont(new Font("Arial", Font.BOLD, 14));
        rejectBtn.setEnabled(false);

        JButton hangupBtn = new JButton("Kapat");
        hangupBtn.setBackground(new Color(192, 57, 43));
        hangupBtn.setForeground(Color.WHITE);
        hangupBtn.setFont(new Font("Arial", Font.BOLD, 14));
        hangupBtn.setEnabled(false);

        JButton availableBtn = new JButton("Available");
        availableBtn.setBackground(new Color(39, 174, 96));
        availableBtn.setForeground(Color.WHITE);

        JButton offlineBtn = new JButton("Offline");
        offlineBtn.setBackground(new Color(149, 165, 166));
        offlineBtn.setForeground(Color.WHITE);

        // Aramayı kabul et
        acceptBtn.addActionListener(e -> {
            System.out.println("Accept button clicked");
            browser_.executeJavaScript(
                "if (window.currentContact) { " +
                "  window.currentContact.accept(); " +
                "  console.log('Call accepted via Java button'); " +
                "}", browser_.getURL(), 0);
        });

        // Aramayı reddet
        rejectBtn.addActionListener(e -> {
            System.out.println("Reject button clicked");
            browser_.executeJavaScript(
                "if (window.currentContact) { " +
                "  window.currentContact.reject(); " +
                "  console.log('Call rejected via Java button'); " +
                "}", browser_.getURL(), 0);
        });

        // Aramayı sonlandır
        hangupBtn.addActionListener(e -> {
            System.out.println("Hangup button clicked");
            browser_.executeJavaScript(
                "if (window.currentContact) { " +
                "  var conn = window.currentContact.getAgentConnection(); " +
                "  if (conn) conn.destroy(); " +
                "  console.log('Call ended via Java button'); " +
                "}", browser_.getURL(), 0);
        });

        // Agent durumunu Available yap
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

        // Agent durumunu Offline yap
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

        JButton refreshBtn = new JButton("Refresh");
        JButton logoutBtn = new JButton("Logout");
        refreshBtn.addActionListener(e -> browser_.reload());
        logoutBtn.addActionListener(e -> {
            File cacheDir2 = new File("jcef_cache");
            if (cacheDir2.exists()) {
                deleteDir(cacheDir2);
            }
            CefCookieManager cookieManager = CefCookieManager.getGlobalManager();
            if (cookieManager != null) {
                boolean success = cookieManager.deleteCookies("", "");
                System.out.println("Cookie temizleme durumu: " + success);
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
// Sağ tıklama menüsünü kapat
        client_.addContextMenuHandler(new CefContextMenuHandlerAdapter() {
            @Override
            public void onBeforeContextMenu(CefBrowser browser, CefFrame frame,
                                            CefContextMenuParams params, CefMenuModel model) {
                model.clear(); // tüm context menüyü temizle
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
//        // Frame icon
//        ImageIcon icon2 = new ImageIcon(getClass().getResource("/klogo.png"));
//        setIconImage(icon2.getImage());
//
//        // ClassLoader ile yükleme
//        java.net.URL iconURL = getClass().getClassLoader().getResource("klogo.png");
//        if(iconURL != null) {
//            ImageIcon icon = new ImageIcon(iconURL);
//            setIconImage(icon.getImage());
//        } else {
//            System.err.println("Icon bulunamadı!");
//        }

// Fullscreen açmak için
//        setExtendedState(JFrame.MAXIMIZED_BOTH);
//        setUndecorated(true); // başlık çubuğunu kaldırır
//        GraphicsEnvironment.getLocalGraphicsEnvironment()
//                .getDefaultScreenDevice()
//                .setFullScreenWindow(this);

        getContentPane().add(topPanel, BorderLayout.NORTH);

        // CCP'yi gizlemek için browserUI_ boyutunu küçültebilir veya tamamen gizleyebilirsiniz
        // browserUI_.setPreferredSize(new Dimension(1, 1)); // Minimum boyut - görünmez ama aktif
        // browserUI_.setVisible(false); // Tamamen gizle (DİKKAT: Ses çalışmayabilir)

        // ==================== NUMPAD PANEL ====================
        JPanel numpadPanel = createNumpadPanel();
        getContentPane().add(numpadPanel, BorderLayout.EAST);

        getContentPane().add(browserUI_, BorderLayout.CENTER);
        pack();
//        setSize(800, 600);
//        setExtendedState(JFrame.MAXIMIZED_BOTH);
//        setBounds(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds());
//
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice device = env.getDefaultScreenDevice();
        Rectangle bounds = device.getDefaultConfiguration().getBounds();
        setBounds(bounds);
        setVisible(true);

//        addWindowListener(new WindowAdapter() {
//            @Override
//            public void windowClosing(WindowEvent e) {
//                CefApp.getInstance().dispose();
//                dispose();
//            }
//        });

//        addWindowListener(new WindowAdapter() {
//            @Override
//            public void windowClosing(WindowEvent e) {
//                new Thread(() -> {
//                    try {
//                        if (!CefApp.getState().equals(CefApp.CefAppState.TERMINATED)) {
//                            CefApp.getInstance().dispose();
//                        }
//                    } catch (Exception ex) {
//                        ex.printStackTrace();
//                    } finally {
//                        SwingUtilities.invokeLater(() -> dispose());
//                    }
//                }).start();
//            }
//        });
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
     * Gelen arama event'i - Arama geldiğinde çağrılır
     */
    private void onIncomingCall(String callData) {
        System.out.println("*** GELEN ARAMA ***");
        System.out.println("Call Data: " + callData);

        // Butonları aktif et
        SwingUtilities.invokeLater(() -> {
            if (acceptButton != null) acceptButton.setEnabled(true);
            if (rejectButton != null) rejectButton.setEnabled(true);
            if (hangupButton != null) hangupButton.setEnabled(false);

            // Burada müşteri kartı gösterebilirsiniz
            // showCustomerCard(callData);
        });
    }

    /**
     * Arama bağlandığında çağrılır
     */
    private void onCallConnected(String callData) {
        System.out.println("*** ARAMA BAĞLANDI ***");
        System.out.println("Call Data: " + callData);

        SwingUtilities.invokeLater(() -> {
            if (acceptButton != null) acceptButton.setEnabled(false);
            if (rejectButton != null) rejectButton.setEnabled(false);
            if (hangupButton != null) hangupButton.setEnabled(true);
        });
    }

    /**
     * Arama sonlandığında çağrılır
     */
    private void onCallEnded(String callData) {
        System.out.println("*** ARAMA SONLANDI ***");
        System.out.println("Call Data: " + callData);

        SwingUtilities.invokeLater(() -> {
            if (acceptButton != null) acceptButton.setEnabled(false);
            if (rejectButton != null) rejectButton.setEnabled(false);
            if (hangupButton != null) hangupButton.setEnabled(false);

            // Müşteri kartını gizle
            // hideCustomerCard();
        });
    }

    /**
     * Agent durumu değiştiğinde çağrılır (Available, Busy, Offline vb.)
     */
    private void onAgentStateChange(String state) {
        System.out.println("*** AGENT DURUMU DEĞİŞTİ: " + state + " ***");
    }

    // ==================== NUMPAD PANEL ====================

    /**
     * Numpad paneli oluşturur - telefon tuş takımı gibi
     */
    private JPanel createNumpadPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Tuş Takımı"));
        panel.setPreferredSize(new Dimension(200, 350));

        // Numpad grid (4x3)
        JPanel gridPanel = new JPanel(new GridLayout(4, 3, 5, 5));
        gridPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        String[] buttons = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"};

        for (String label : buttons) {
            JButton btn = new JButton(label);
            btn.setFont(new Font("Arial", Font.BOLD, 24));
            btn.setPreferredSize(new Dimension(55, 55));
            btn.setBackground(new Color(52, 73, 94));
            btn.setForeground(Color.WHITE);
            btn.setFocusPainted(false);
            btn.setBorder(BorderFactory.createRaisedBevelBorder());

            btn.addActionListener(e -> sendDtmfDigit(label));

            // Hover efekti
            btn.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    btn.setBackground(new Color(41, 128, 185));
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    btn.setBackground(new Color(52, 73, 94));
                }
            });

            gridPanel.add(btn);
        }

        panel.add(gridPanel, BorderLayout.CENTER);

        // Bilgi label'ı
        JLabel infoLabel = new JLabel("<html><center>Görüşme sırasında<br>tuşlara basın</center></html>");
        infoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        infoLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        infoLabel.setForeground(Color.GRAY);
        panel.add(infoLabel, BorderLayout.SOUTH);

        return panel;
    }

    /**
     * CCP'deki numpad butonuna tıklama simüle eder
     */
    private void sendDtmfDigit(String digit) {
        System.out.println("Numpad tuşu basıldı: " + digit);

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

    public static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            for (File f : dir.listFiles()) {
                deleteDir(f);
            }
        }
        return dir.delete();
    }
    public static void main(String[] args) throws UnsupportedPlatformException, CefInitializationException, IOException, InterruptedException {

//        Path cachePath = Paths.get("jcef_cache");
//        if (Files.exists(cachePath)) {
//            try {
//                Files.walk(cachePath)
//                        .sorted(Comparator.reverseOrder())
//                        .map(Path::toFile)
//                        .forEach(File::delete);
//                System.out.println("JCEF cache ve cookie temizlendi.");
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }
        String urlAWS = (args.length > 0) ? args[0] : System.getenv("AWSCONNECT_URL");
        if (urlAWS == null || urlAWS.isEmpty()) {
            urlAWS = AWS_CONNECT_URL; // Varsayılan AWS Connect URL
        }

        String url = (args.length > 0) ? args[1] : System.getenv("JVIEW_URL");
        if (url == null || url.isEmpty()) {
//            url = "http://localhost:3000";
        }

//        url = "https://www.google.com";
        boolean useOsr = false;
        // EDT üzerinde başlat

        String finalUrlAWS = urlAWS;
        String finalUrl = url;
//        SwingUtilities.invokeLater(() -> {
//            try {
//                new MainFrame(finalUrlAWS,finalUrl, useOsr, false, args);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        });
                new MainFrame(finalUrlAWS,finalUrl, useOsr, false, args);



//        new MainFrame(url, useOsr, false, args);
    }

//    public class CookieUtils {
//
//        public static void clearAllCookies() {
//            // Varsayılan cookie manager
//            CefCookieManager cookieManager = CefCookieManager.getGlobalManager();
//            if (cookieManager != null) {
//                // Tüm cookie’leri sil
//                cookieManager.deleteCookies("", "", result -> {
//                    if (result) {
//                        System.out.println("Tüm cookie’ler silindi.");
//                    } else {
//                        System.out.println("Cookie silme başarısız.");
//                    }
//                });
//            }
//        }
//    }
}
