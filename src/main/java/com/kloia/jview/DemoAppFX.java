package com.kloia.jview;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Demo JavaFX Application with fully embedded CCP
 *
 * This version embeds everything in a single JavaFX window using SwingNode.
 * The JCEF browser will appear as a black rectangle but functions correctly.
 *
 * Architecture:
 * - DemoAppFX: Pure JavaFX Application (single window)
 * - KloiaConnectServiceFX: JCEF embedded via SwingNode
 * - KloiaConnectUIFX: Pure JavaFX UI with embedded browser area
 *
 * Note: Browser area appears black due to JCEF/SwingNode rendering limitations,
 * but all functionality works (DTMF, call control, events).
 */
public class DemoAppFX extends Application {

    private KloiaConnectServiceFX service;
    private KloiaConnectUIFX ui;

    // CCP panel visibility (--ccp=1 visible, --ccp=0 hidden, default: 1)
    private static boolean ccpVisible = true;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Kloia Connect - Embedded FX");

        // Create service with SwingNode
        service = new KloiaConnectServiceFX();

        // Create UI (includes embedded browser area if ccpVisible)
        ui = new KloiaConnectUIFX(service, ccpVisible);

        // Create scene with the UI root
        Scene scene = new Scene(ui.getRoot(), 950, 650);
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);

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
                System.out.println("CCP panel visibility: " + (ccpVisible ? "VISIBLE" : "HIDDEN"));
            }
        }
        launch(args);
    }
}
