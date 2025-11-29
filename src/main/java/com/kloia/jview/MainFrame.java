package me.friwi.kjview;

import me.friwi.jcefmaven.*;
import org.cef.CefApp;
import org.cef.CefApp.CefAppState;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefFocusHandlerAdapter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;

public class MainFrame extends JFrame {
    private static final long serialVersionUID = -5570653778104813836L;
    private final JTextField address_;
    private final CefApp cefApp_;
    private final CefClient client_;
    private final CefBrowser browser_;
    private final Component browserUI_;
    private boolean browserFocus_ = true;

    private MainFrame(String startURL, boolean useOSR, boolean isTransparent, String[] args)
            throws UnsupportedPlatformException, CefInitializationException, IOException, InterruptedException {

        // CEF initialization
        CefAppBuilder builder = new CefAppBuilder();
        builder.addJcefArgs("--use-fake-ui-for-media-stream"); // tüm medya izinlerini otomatik kabul eder
        builder.getCefSettings().windowless_rendering_enabled = useOSR;

        builder.setAppHandler(new MavenCefAppHandlerAdapter() {
            @Override
            public void stateHasChanged(CefAppState state) {
                if (state == CefAppState.TERMINATED) System.exit(0);
            }
        });

        if (args.length > 0) builder.addJcefArgs(args);

        cefApp_ = builder.build();
        client_ = cefApp_.createClient();

        // Message router (CEF-JS iletişimi için)
        CefMessageRouter msgRouter = CefMessageRouter.create();
        client_.addMessageRouter(msgRouter);

        // Browser oluşturma
        browser_ = client_.createBrowser(startURL, useOSR, isTransparent);
        browserUI_ = browser_.getUIComponent();

        // Adres çubuğu
        address_ = new JTextField(startURL, 100);
        address_.addActionListener(e -> browser_.loadURL(address_.getText()));

        // Browser URL değiştiğinde adres çubuğunu güncelle
        client_.addDisplayHandler(new CefDisplayHandlerAdapter() {
            @Override
            public void onAddressChange(CefBrowser browser, CefFrame frame, String url) {
                address_.setText(url);
            }
        });

        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> browser_.reload());


        JPanel topPanel = new JPanel(new BorderLayout(5, 0));
        topPanel.add(address_, BorderLayout.CENTER);
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


        getContentPane().add(topPanel, BorderLayout.NORTH);
        getContentPane().add(browserUI_, BorderLayout.CENTER);
        pack();
        setSize(800, 600);
        setVisible(true);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                CefApp.getInstance().dispose();
                dispose();
            }
        });
    }

    public static void main(String[] args) throws UnsupportedPlatformException, CefInitializationException, IOException, InterruptedException {
        boolean useOsr = false;
        new MainFrame("https://webcamtests.com", useOsr, false, args);
    }
}
