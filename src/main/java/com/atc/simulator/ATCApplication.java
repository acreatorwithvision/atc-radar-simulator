package com.atc.simulator;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import java.util.List;

public class ATCApplication extends Application {

    public static final String APP_TITLE    = "ATC Radar Simulator";
    public static final int    WINDOW_WIDTH  = 1200;
    public static final int    WINDOW_HEIGHT = 800;

    private RadarCanvas      radarCanvas;
    private SimulationEngine engine;

    @Override
    public void start(Stage primaryStage) {
        // ── Engine ─────────────────────────────────────────────────────────
        engine = new SimulationEngine();
        List<Aircraft> traffic = AircraftFactory.createInitialTraffic(8);
        engine.init(traffic);

        // ── Radar ──────────────────────────────────────────────────────────
        radarCanvas = new RadarCanvas();
        radarCanvas.setAircraftCollection(engine.getAllAircraft());

        // ── Sidebar ────────────────────────────────────────────────────────
        VBox sidebar = buildSidebar();

        // ── Layout ─────────────────────────────────────────────────────────
        HBox root = new HBox();
        root.setStyle("-fx-background-color: #000000;");
        HBox.setHgrow(sidebar, Priority.ALWAYS);
        root.getChildren().addAll(radarCanvas, sidebar);

        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT, Color.BLACK);

        primaryStage.setTitle(APP_TITLE);
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();

        // ── Start ──────────────────────────────────────────────────────────
        engine.start();
        System.out.println("[ATC] Engine online. Aircraft: " + engine.getAircraftCount());
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox(4);
        sidebar.setPrefWidth(WINDOW_WIDTH - RadarCanvas.RADAR_SIZE);
        sidebar.setStyle("-fx-background-color: #050d05; -fx-border-color: #1a5c1a;"
                       + "-fx-border-width: 0 0 0 1; -fx-padding: 10;");

        Label title = new Label("FLIGHT STRIPS");
        title.setStyle("-fx-text-fill: #39ff14; -fx-font-family: 'Courier New';"
                     + "-fx-font-size: 13; -fx-font-weight: bold;");

        Label info = new Label("Click aircraft to select.\nCommands in Commit 6.");
        info.setStyle("-fx-text-fill: #1a8c0d; -fx-font-family: 'Courier New';"
                    + "-fx-font-size: 10; -fx-padding: 6 0 0 0;");

        sidebar.getChildren().addAll(title, info);
        return sidebar;
    }

    @Override
    public void stop() {
        if (engine != null)      engine.shutdown();
        if (radarCanvas != null) radarCanvas.stopRendering();
    }

    public static void main(String[] args) { launch(args); }
}
