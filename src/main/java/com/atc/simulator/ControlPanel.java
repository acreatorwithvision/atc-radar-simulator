package com.atc.simulator;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Simulation control panel.
 *
 * Controls:
 *   ● Pause / Resume  → engine.pause() / engine.resume()  (AtomicBoolean flip)
 *   ● Spawn rate      → ScheduledExecutorService spawns aircraft at chosen rate
 *   ● Clear all       → engine.removeAircraft() for each callsign
 *   ● Spawn one now   → AircraftFactory.spawnAtEdge() + engine.registerAircraft()
 */
public class ControlPanel extends VBox {

    // ── Styles ────────────────────────────────────────────────────────────────
    private static final String S_HEADER =
        "-fx-text-fill: #39ff14; -fx-font-family: 'Courier New'; " +
        "-fx-font-size: 11; -fx-font-weight: bold;";
    private static final String S_LABEL =
        "-fx-text-fill: #1a8c0d; -fx-font-family: 'Courier New'; -fx-font-size: 10;";
    private static final String S_BTN =
        "-fx-background-color: #0d3b0d; -fx-text-fill: #39ff14; " +
        "-fx-font-family: 'Courier New'; -fx-font-size: 10; " +
        "-fx-border-color: #1a5c1a; -fx-border-width: 1; " +
        "-fx-cursor: hand; -fx-padding: 3 8;";
    private static final String S_BTN_PAUSE =
        "-fx-background-color: #3b2600; -fx-text-fill: #ff9900; " +
        "-fx-font-family: 'Courier New'; -fx-font-size: 10; " +
        "-fx-border-color: #ff9900; -fx-border-width: 1; " +
        "-fx-cursor: hand; -fx-padding: 3 8;";
    private static final String S_BTN_WARN =
        "-fx-background-color: #3b0d0d; -fx-text-fill: #ff4444; " +
        "-fx-font-family: 'Courier New'; -fx-font-size: 10; " +
        "-fx-border-color: #ff4444; -fx-border-width: 1; " +
        "-fx-cursor: hand; -fx-padding: 3 8;";
    private static final String S_SLIDER_LABEL =
        "-fx-text-fill: #1a8c0d; -fx-font-family: 'Courier New'; -fx-font-size: 9;";

    // ── Nodes ─────────────────────────────────────────────────────────────────
    private final Button    pauseBtn   = new Button("⏸ PAUSE");
    private final Button    spawnBtn   = new Button("✈ SPAWN");
    private final Button    clearBtn   = new Button("✗ CLEAR ALL");
    private final Slider    spawnSlider;
    private final Label     spawnLabel = new Label("SPAWN: OFF");

    // ── State ─────────────────────────────────────────────────────────────────
    private boolean paused = false;

    private final SimulationEngine engine;
    private final ScheduledExecutorService spawner;
    private       ScheduledFuture<?>       spawnFuture;

    // ─────────────────────────────────────────────────────────────────────────

    public ControlPanel(SimulationEngine engine) {
        this.engine = engine;

        setSpacing(6);
        setPadding(new Insets(8, 6, 8, 6));
        setStyle("-fx-background-color: #050d05; " +
                 "-fx-border-color: #1a5c1a; " +
                 "-fx-border-width: 1 0 0 0;");

        spawner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("atc-spawner");
            return t;
        });

        Label header = new Label("── SIM CONTROLS ──────");
        header.setStyle(S_HEADER);

        spawnSlider = buildSpawnSlider();

        getChildren().addAll(
            header,
            makeButtonRow(),
            makeSeparator(),
            spawnLabel,
            spawnSlider,
            makeSeparator()
        );

        wireButtons();
    }

    // ── UI builders ───────────────────────────────────────────────────────────

    private HBox makeButtonRow() {
        pauseBtn.setStyle(S_BTN_PAUSE);
        spawnBtn.setStyle(S_BTN);
        clearBtn.setStyle(S_BTN_WARN);
        HBox row = new HBox(6, pauseBtn, spawnBtn, clearBtn);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Slider buildSpawnSlider() {
        Slider s = new Slider(0, 5, 0);
        s.setMajorTickUnit(1);
        s.setMinorTickCount(0);
        s.setSnapToTicks(true);
        s.setShowTickMarks(true);
        s.setShowTickLabels(true);
        s.setPrefWidth(180);
        s.setStyle(
            "-fx-control-inner-background: #050d05; " +
            "-fx-tick-label-fill: #1a5c1a;"
        );
        return s;
    }

    private Label makeSeparator() {
        Label l = new Label("──────────────────────");
        l.setStyle(S_SLIDER_LABEL);
        return l;
    }

    // ── Button wiring ─────────────────────────────────────────────────────────

    private void wireButtons() {
        // Pause / Resume
        pauseBtn.setOnAction(e -> {
            paused = !paused;
            if (paused) {
                engine.pause();
                pauseBtn.setText("▶ RESUME");
                pauseBtn.setStyle(S_BTN);
            } else {
                engine.resume();
                pauseBtn.setText("⏸ PAUSE");
                pauseBtn.setStyle(S_BTN_PAUSE);
            }
        });

        // Spawn one now
        spawnBtn.setOnAction(e -> spawnOne());

        // Clear all
        clearBtn.setOnAction(e -> {
            new java.util.ArrayList<>(engine.getMap().keySet())
                .forEach(engine::removeAircraft);
        });

        // Spawn rate slider
        spawnSlider.valueProperty().addListener((obs, oldV, newV) -> {
            int rate = newV.intValue();
            updateSpawnSchedule(rate);
            spawnLabel.setText(rate == 0
                ? "AUTO-SPAWN: OFF"
                : "AUTO-SPAWN: 1 / " + rate + "s");
            spawnLabel.setStyle(rate > 0 ? S_BTN : S_LABEL);
        });
    }

    // ── Spawn logic ───────────────────────────────────────────────────────────

    private void spawnOne() {
        if (engine.getAircraftCount() >= 20) return; // cap
        Aircraft ac = AircraftFactory.spawnAtEdge();
        engine.registerAircraft(ac);
    }

    private void updateSpawnSchedule(int intervalSecs) {
        if (spawnFuture != null) {
            spawnFuture.cancel(false);
            spawnFuture = null;
        }
        if (intervalSecs > 0) {
            spawnFuture = spawner.scheduleAtFixedRate(
                this::spawnOne,
                intervalSecs,
                intervalSecs,
                TimeUnit.SECONDS
            );
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void shutdown() {
        spawner.shutdownNow();
    }
}
