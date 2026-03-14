package com.atc.simulator;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class ATCApplication extends Application {

    public static final String APP_TITLE    = "ATC Radar Simulator";
    public static final int    WINDOW_WIDTH  = 1200;
    public static final int    WINDOW_HEIGHT = 800;

    private RadarCanvas radarCanvas;

    @Override
    public void start(Stage primaryStage) {
        // ── Radar canvas (left/center) ─────────────────────────────────────
        radarCanvas = new RadarCanvas();

        // ── Right sidebar placeholder ──────────────────────────────────────
        javafx.scene.layout.VBox sidebar = new javafx.scene.layout.VBox();
        sidebar.setPrefWidth(WINDOW_WIDTH - RadarCanvas.RADAR_SIZE);
        sidebar.setStyle("-fx-background-color: #050d05; -fx-border-color: #1a5c1a; "
                       + "-fx-border-width: 0 0 0 1;");

        javafx.scene.control.Label sidebarLabel = new javafx.scene.control.Label("FLIGHT STRIPS");
        sidebarLabel.setStyle("-fx-text-fill: #39ff14; -fx-font-family: 'Courier New'; "
                            + "-fx-font-size: 12; -fx-padding: 10 0 0 10;");
        sidebar.getChildren().add(sidebarLabel);

        // ── Root layout ────────────────────────────────────────────────────
        HBox root = new HBox();
        root.setStyle("-fx-background-color: #000000;");
        HBox.setHgrow(sidebar, Priority.ALWAYS);
        root.getChildren().addAll(radarCanvas, sidebar);

        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT, Color.BLACK);

        primaryStage.setTitle(APP_TITLE);
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();

        System.out.println("[ATC] Radar scope online.");
    }

    @Override
    public void stop() {
        if (radarCanvas != null) radarCanvas.stopRendering();
        System.out.println("[ATC] Shutdown complete.");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
