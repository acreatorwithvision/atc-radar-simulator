package com.atc.simulator;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class ATCApplication extends Application {

    public static final String APP_TITLE    = "ATC Radar Simulator";
    public static final int    WINDOW_WIDTH  = 1200;
    public static final int    WINDOW_HEIGHT = 800;

    private RadarCanvas       radarCanvas;
    private SimulationEngine  engine;
    private ConflictDetector  conflictDetector;
    private AlertPanel        alertPanel;
    private CommandPanel      commandPanel;

    @Override
    public void start(Stage primaryStage) {
        // ── Engine ─────────────────────────────────────────────────────────
        engine = new SimulationEngine();
        engine.init(AircraftFactory.createInitialTraffic(10));

        // ── Conflict detector ──────────────────────────────────────────────
        conflictDetector = new ConflictDetector(engine);
        alertPanel       = new AlertPanel(conflictDetector);
        conflictDetector.setListener(conflicts -> alertPanel.update(conflicts));

        // ── Command panel ──────────────────────────────────────────────────
        commandPanel = new CommandPanel();

        // ── Radar canvas ───────────────────────────────────────────────────
        radarCanvas = new RadarCanvas();
        radarCanvas.setAircraftCollection(engine.getAllAircraft());
        radarCanvas.setClickListener(new RadarCanvas.AircraftClickListener() {
            @Override
            public void onAircraftClicked(Aircraft ac) {
                // Deselect previous
                Aircraft prev = commandPanel.getSelectedAircraft();
                if (prev != null && !prev.callsign.equals(ac.callsign)) {
                    prev.setSelected(false);
                }
                commandPanel.selectAircraft(ac);
            }

            @Override
            public void onEmptyClicked() {
                commandPanel.deselect();
            }
        });

        commandPanel.setOnDeselect(() -> {});

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
        conflictDetector.start();

        System.out.println("[ATC] Online. Aircraft: " + engine.getAircraftCount());
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox();
        sidebar.setPrefWidth(WINDOW_WIDTH - RadarCanvas.RADAR_SIZE);
        sidebar.setStyle("-fx-background-color: #050d05; "
                       + "-fx-border-color: #1a5c1a; "
                       + "-fx-border-width: 0 0 0 1;");

        // Title bar
        Label title = new Label("  ATC RADAR  //  KXYZ");
        title.setStyle("-fx-text-fill: #39ff14; -fx-font-family: 'Courier New'; "
                     + "-fx-font-size: 12; -fx-font-weight: bold; "
                     + "-fx-padding: 8 0 8 0; "
                     + "-fx-border-color: #1a5c1a; -fx-border-width: 0 0 1 0;");

        Label hint = new Label("  Click a blip to select");
        hint.setStyle("-fx-text-fill: #1a5c1a; -fx-font-family: 'Courier New'; "
                    + "-fx-font-size: 10; -fx-padding: 6 0 0 0;");

        // Spacer pushes command + alert to bottom
        VBox spacer = new VBox();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        sidebar.getChildren().addAll(
            title,
            hint,
            spacer,
            commandPanel,
            alertPanel
        );
        return sidebar;
    }

    @Override
    public void stop() {
        if (conflictDetector != null) conflictDetector.shutdown();
        if (engine != null)           engine.shutdown();
        if (radarCanvas != null)      radarCanvas.stopRendering();
    }

    public static void main(String[] args) { launch(args); }
}
