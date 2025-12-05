package com.kloia.jview;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Demo JavaFX Application using KloiaConnectService + KloiaConnectUI
 *
 * This demonstrates how to embed the AWS Connect CCP UI
 * in a pure JavaFX application. The JCEF browser runs
 * in a hidden window while the UI is pure JavaFX.
 *
 * Architecture:
 * - DemoApp: Pure JavaFX Application
 * - KloiaConnectService: JCEF in hidden window (handles CCP communication)
 * - KloiaConnectUI: Pure JavaFX Node (embedded in this app)
 *
 * Parameters:
 * --ccp=1  Show CCP window (for debugging)
 * --ccp=0  Hide CCP window (default)
 */
public class DemoApp extends Application {

    private KloiaConnectService service;
    private KloiaConnectUI ui;

    // CCP window visibility (--ccp=1 visible, --ccp=0 hidden, default: 0)
    private static boolean ccpVisible = false;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Kloia Connect - JavaFX Demo");

        // Create service (headless JCEF)
        service = new KloiaConnectService();
        service.setCcpWindowVisible(ccpVisible);

        // Create UI (pure JavaFX)
        ui = new KloiaConnectUI(service);

        // Create scene with the UI root
        Scene scene = new Scene(ui.getRoot(), 900, 600);
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(800);
        primaryStage.setMinHeight(500);

        // Handle close - stop the service
        primaryStage.setOnCloseRequest(e -> {
            if (service != null) {
                service.stop();
            }
            Platform.exit();
            System.exit(0);
        });

        primaryStage.show();

        // Start the service after UI is visible
        service.start(success -> {
            if (!success) {
                Platform.runLater(() -> ui.updateStatus("Failed to start service!"));
            }
        });
    }

    public static void main(String[] args) {
        // Parse --ccp parameter
        for (String arg : args) {
            if (arg.startsWith("--ccp=")) {
                String value = arg.substring(6);
                ccpVisible = "1".equals(value) || "true".equalsIgnoreCase(value);
                System.out.println("CCP window visibility: " + (ccpVisible ? "VISIBLE" : "HIDDEN"));
            }
        }
        launch(args);
    }
}
