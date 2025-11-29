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

                // Login başarılı olduysa
                if (url.contains("ccp-v2") && url.contains("softphone")) {
                    System.out.println("AWS Connect CCP'ye başarıyla giriş yapıldı!");
                }
            }
        });
        JButton refreshBtn = new JButton("Refresh");
        JButton logoutBtn = new JButton("Logout");
        refreshBtn.addActionListener(e -> browser_.reload());
        logoutBtn.addActionListener(e -> {
// Cache temizlemek için
            File cacheDir2 = new File("jcef_cache");
            if (cacheDir2.exists()) {
                deleteDir(cacheDir2);
            }
            CefCookieManager cookieManager = CefCookieManager.getGlobalManager();
            if (cookieManager != null) {
                // Tüm cookie’leri silmek için boş stringler ver
                boolean success = cookieManager.deleteCookies("", "");
                System.out.println("Cookie temizleme durumu: " + success);
            }

//            CookieUtils.clearAllCookies();
            browser_.loadURL(startURLAWS);
        });


        JPanel topPanel = new JPanel(new BorderLayout(5, 0));
//        topPanel.add(address_, BorderLayout.CENTER);
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
