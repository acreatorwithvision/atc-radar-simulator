package com.atc.simulator;

import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Live metrics panel — redraws every animation frame.
 *
 * Reads engine stats (all AtomicInteger / thread-safe) directly:
 *   - getTickCount()        → AtomicInteger
 *   - getActiveThreadCount()→ ScheduledThreadPoolExecutor
 *   - getAircraftCount()    → ConcurrentHashMap.size()
 *   - isPaused()            → AtomicBoolean
 *
 * The mini tick-rate sparkline is drawn on a Canvas updated each frame.
 */
public class MetricsPanel extends VBox {

    // ── Styles ────────────────────────────────────────────────────────────────
    private static final String S_HEADER =
        "-fx-text-fill: #39ff14; -fx-font-family: 'Courier New'; " +
        "-fx-font-size: 11; -fx-font-weight: bold;";
    private static final String S_ROW =
        "-fx-text-fill: #1a8c0d; -fx-font-family: 'Courier New'; -fx-font-size: 10;";
    private static final String S_ROW_WARN =
        "-fx-text-fill: #ff9900; -fx-font-family: 'Courier New'; -fx-font-size: 10;";
    private static final String S_ROW_OK =
        "-fx-text-fill: #39ff14; -fx-font-family: 'Courier New'; -fx-font-size: 10;";

    // ── Nodes ─────────────────────────────────────────────────────────────────
    private final Label statusLabel   = new Label();
    private final Label tickLabel     = new Label();
    private final Label threadLabel   = new Label();
    private final Label acLabel       = new Label();
    private final Label conflictLabel = new Label();
    private final Label fpsLabel      = new Label();
    private final Canvas sparkline;

    // ── Sparkline state ───────────────────────────────────────────────────────
    private static final int SPARK_W      = 188;
    private static final int SPARK_H      = 36;
    private static final int SPARK_POINTS = 60;

    private final Deque<Double> tickRates  = new ArrayDeque<>(SPARK_POINTS);
    private int                 lastTick   = 0;
    private long                lastNano   = System.nanoTime();

    // ── Frame counter ─────────────────────────────────────────────────────────
    private int    frameCount  = 0;
    private long   fpsLastNano = System.nanoTime();
    private double currentFps  = 0;

    // ── Engine reference ──────────────────────────────────────────────────────
    private final SimulationEngine  engine;
    private final ConflictDetector  detector;

    private AnimationTimer timer;

    // ─────────────────────────────────────────────────────────────────────────

    public MetricsPanel(SimulationEngine engine, ConflictDetector detector) {
        this.engine   = engine;
        this.detector = detector;

        setSpacing(3);
        setPadding(new Insets(8, 6, 8, 6));
        setStyle("-fx-background-color: #050d05; " +
                 "-fx-border-color: #1a5c1a; " +
                 "-fx-border-width: 1 0 0 0;");

        Label header = new Label("── SYSTEM METRICS ────");
        header.setStyle(S_HEADER);

        for (Label l : new Label[]{statusLabel, tickLabel, threadLabel,
                                   acLabel, conflictLabel, fpsLabel}) {
            l.setStyle(S_ROW);
        }

        sparkline = new Canvas(SPARK_W, SPARK_H);

        getChildren().addAll(
            header,
            statusLabel,
            tickLabel,
            threadLabel,
            acLabel,
            conflictLabel,
            fpsLabel,
            sparkline
        );

        startUpdating();
    }

    // ── Animation loop ────────────────────────────────────────────────────────

    private void startUpdating() {
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                updateMetrics(now);
                drawSparkline();
            }
        };
        timer.start();
    }

    public void stop() {
        if (timer != null) timer.stop();
    }

    // ── Metrics update (JavaFX thread) ────────────────────────────────────────

    private void updateMetrics(long now) {
        frameCount++;

        // FPS every second
        double elapsedSec = (now - fpsLastNano) / 1_000_000_000.0;
        if (elapsedSec >= 1.0) {
            currentFps  = frameCount / elapsedSec;
            frameCount  = 0;
            fpsLastNano = now;
        }

        // Tick rate
        int    curTick      = engine.getTickCount();
        double elapsedTickS = (now - lastNano) / 1_000_000_000.0;
        double ticksPerSec  = (curTick - lastTick) / Math.max(elapsedTickS, 0.001);

        if (elapsedTickS >= 0.5) {
            if (tickRates.size() >= SPARK_POINTS) tickRates.removeFirst();
            tickRates.addLast(ticksPerSec);
            lastTick = curTick;
            lastNano = now;
        }

        // Pull thread-safe values
        boolean paused   = engine.isPaused();
        int     threads  = engine.getActiveThreadCount();
        int     aircraft = engine.getAircraftCount();
        int     scans    = detector.getScanCount();
        int     found    = detector.getTotalConflictsFound();

        // Status
        statusLabel.setText("STATUS   : " + (paused ? "PAUSED" : "ONLINE"));
        statusLabel.setStyle(paused ? S_ROW_WARN : S_ROW_OK);

        // Ticks
        tickLabel.setText(String.format("TICKS    : %-6d  %.0f/s",
            curTick % 1_000_000, ticksPerSec));
        tickLabel.setStyle(ticksPerSec < 20 ? S_ROW_WARN : S_ROW);

        // Threads
        threadLabel.setText("THREADS  : " + threads
            + " active / " + aircraft + " sched");
        threadLabel.setStyle(threads > 8 ? S_ROW_WARN : S_ROW);

        // Aircraft
        acLabel.setText("AIRCRAFT : " + aircraft);
        acLabel.setStyle(S_ROW);

        // Conflict
        conflictLabel.setText("CONFLICTS: scans=" + scans + " found=" + found);
        conflictLabel.setStyle(found > 0 ? S_ROW_WARN : S_ROW);

        // FPS
        fpsLabel.setText(String.format("RENDER   : %.0f fps", currentFps));
        fpsLabel.setStyle(currentFps < 30 ? S_ROW_WARN : S_ROW);
    }

    // ── Sparkline ─────────────────────────────────────────────────────────────

    private void drawSparkline() {
        GraphicsContext gc = sparkline.getGraphicsContext2D();
        gc.setFill(Color.web("#020702"));
        gc.fillRect(0, 0, SPARK_W, SPARK_H);

        // Border
        gc.setStroke(Color.web("#1a5c1a"));
        gc.setLineWidth(0.5);
        gc.strokeRect(0, 0, SPARK_W, SPARK_H);

        // Label
        gc.setFill(Color.web("#1a5c1a"));
        gc.setFont(javafx.scene.text.Font.font("Courier New", 8));
        gc.fillText("TICK RATE", 3, 9);

        if (tickRates.isEmpty()) return;

        Double[] rates = tickRates.toArray(new Double[0]);
        double   max   = 0;
        for (double r : rates) max = Math.max(max, r);
        if (max < 1) max = 1;

        double step = (double) SPARK_W / SPARK_POINTS;
        double padT = 12, padB = 4;
        double graphH = SPARK_H - padT - padB;

        gc.setStroke(Color.web("#39ff14"));
        gc.setLineWidth(1.0);
        gc.beginPath();

        for (int i = 0; i < rates.length; i++) {
            double x = i * step;
            double y = padT + graphH - (rates[i] / max) * graphH;
            if (i == 0) gc.moveTo(x, y);
            else        gc.lineTo(x, y);
        }
        gc.stroke();

        // Latest value label
        double latest = rates[rates.length - 1];
        gc.setFill(Color.web("#39ff14"));
        gc.fillText(String.format("%.0f/s", latest), SPARK_W - 32, 9);
    }
}
