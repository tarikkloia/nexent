package com.kloia.jview;

/**
 * Launcher class that doesn't extend JavaFX Application.
 * This allows running the application directly from IDE without module-path issues.
 */
public class Launcher {
    public static void main(String[] args) {
        MainApp.main(args);
    }
}
