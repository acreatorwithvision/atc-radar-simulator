package com.atc.simulator;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

public class RadarCanvas extends Canvas {

    // Radar visual constants
    public static final double RADAR_SIZE     = 760.0;
    public static final double CENTER_X       = RADAR_SIZE / 2.0;
    public static final double CENTER_Y       = RADAR_SIZE / 2.0;
    public static final double MAX_RADIUS     = RADAR_SIZE / 2.0 - 10;
    public static final int    RANGE_RINGS    = 4;
    public static final double SWEEP_SPEED    = 0.012; // radians per frame (~3 RPM)

    private double sweepAngle = 0.0;
    private AnimationTimer animationTimer;

    // Phosphor green palette
    private static final Color COLOR_BACKGROUND   = Color.web("#050d05");
    private static final Color COLOR_RING         = Color.web("#0d3b0d");
    private static final Color COLOR_RING_BRIGHT  = Color.web("#1a5c1a");
    private static final Color COLOR_SWEEP        = Color.web("#00ff41");
    private static final Color COLOR_SWEEP_TRAIL  = Color.web("#00ff4108");
    private static final Color COLOR_TEXT         = Color.web("#39ff14");
    private static final Color COLOR_TEXT_DIM     = Color.web("#1a8c0d");
    private static final Color COLOR_CROSSHAIR    = Color.web("#0d3b0d");

    public RadarCanvas() {
        super(RADAR_SIZE, RADAR_SIZE);
        startRendering();
    }

    private void startRendering() {
        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                sweepAngle = (sweepAngle + SWEEP_SPEED) % (2 * Math.PI);
                render();
            }
        };
        animationTimer.start();
    }

    public void stopRendering() {
        if (animationTimer != null) animationTimer.stop();
    }

    public double getSweepAngle() { return sweepAngle; }

    // ── Main render pipeline ──────────────────────────────────────────────────

    protected void render() {
        GraphicsContext gc = getGraphicsContext2D();
        clearBackground(gc);
        drawSweepTrail(gc);
        drawRangeRings(gc);
        drawCrosshairs(gc);
        drawCompassRose(gc);
        drawSweepLine(gc);
        drawBorder(gc);
        drawOverlayText(gc);
    }

    // ── Background ────────────────────────────────────────────────────────────

    private void clearBackground(GraphicsContext gc) {
        gc.setFill(COLOR_BACKGROUND);
        gc.fillRect(0, 0, RADAR_SIZE, RADAR_SIZE);

        // Circular clip mask — fill outside circle with solid black
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, RADAR_SIZE, CENTER_Y - MAX_RADIUS);
        gc.fillRect(0, CENTER_Y + MAX_RADIUS, RADAR_SIZE, CENTER_Y - MAX_RADIUS);
        gc.fillRect(0, 0, CENTER_X - MAX_RADIUS, RADAR_SIZE);
        gc.fillRect(CENTER_X + MAX_RADIUS, 0, CENTER_X - MAX_RADIUS, RADAR_SIZE);

        // Overdraw corners with black arcs to create circular scope
        gc.setFill(Color.BLACK);
        // We'll draw the circular background cleanly
        gc.setFill(COLOR_BACKGROUND);
        gc.fillOval(CENTER_X - MAX_RADIUS, CENTER_Y - MAX_RADIUS,
                    MAX_RADIUS * 2, MAX_RADIUS * 2);
    }

    // ── Sweep trail (fading green wedge behind sweep line) ────────────────────

    private void drawSweepTrail(GraphicsContext gc) {
        int trailSteps = 60;
        for (int i = 0; i < trailSteps; i++) {
            double fraction = (double) i / trailSteps;
            double trailAngle = sweepAngle - (fraction * Math.PI * 0.55);
            double alpha = fraction * 0.18;

            gc.setFill(Color.color(0.0, 1.0, 0.25, alpha));
            gc.fillArc(
                CENTER_X - MAX_RADIUS, CENTER_Y - MAX_RADIUS,
                MAX_RADIUS * 2, MAX_RADIUS * 2,
                -Math.toDegrees(trailAngle),
                -(360.0 / trailSteps),
                javafx.scene.shape.ArcType.ROUND
            );
        }
    }

    // ── Range rings ───────────────────────────────────────────────────────────

    private void drawRangeRings(GraphicsContext gc) {
        gc.setTextAlign(TextAlignment.LEFT);

        for (int i = 1; i <= RANGE_RINGS; i++) {
            double r = MAX_RADIUS * i / RANGE_RINGS;
            boolean outerRing = (i == RANGE_RINGS);

            gc.setStroke(outerRing ? COLOR_RING_BRIGHT : COLOR_RING);
            gc.setLineWidth(outerRing ? 1.2 : 0.7);
            gc.strokeOval(CENTER_X - r, CENTER_Y - r, r * 2, r * 2);

            // Range label (NM = nautical miles)
            int nmLabel = i * 25; // 25 / 50 / 75 / 100 NM
            gc.setFill(COLOR_TEXT_DIM);
            gc.setFont(Font.font("Courier New", 10));
            gc.fillText(nmLabel + "NM",
                CENTER_X + 4,
                CENTER_Y - r + 12);
        }
    }

    // ── Crosshairs ────────────────────────────────────────────────────────────

    private void drawCrosshairs(GraphicsContext gc) {
        gc.setStroke(COLOR_CROSSHAIR);
        gc.setLineWidth(0.5);
        gc.setLineDashes(4, 4);

        // Horizontal
        gc.strokeLine(CENTER_X - MAX_RADIUS, CENTER_Y,
                      CENTER_X + MAX_RADIUS, CENTER_Y);
        // Vertical
        gc.strokeLine(CENTER_X, CENTER_Y - MAX_RADIUS,
                      CENTER_X, CENTER_Y + MAX_RADIUS);

        gc.setLineDashes(null);

        // Center dot
        gc.setFill(COLOR_SWEEP);
        gc.fillOval(CENTER_X - 3, CENTER_Y - 3, 6, 6);
    }

    // ── Compass rose ──────────────────────────────────────────────────────────

    private void drawCompassRose(GraphicsContext gc) {
        String[] cardinals = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        double   labelRadius = MAX_RADIUS - 14;

        gc.setFont(Font.font("Courier New", 11));
        gc.setTextAlign(TextAlignment.CENTER);

        for (int i = 0; i < 360; i += 10) {
            double rad = Math.toRadians(i - 90);
            boolean isCardinal  = (i % 90 == 0);
            boolean isOrdinal   = (i % 45 == 0);
            boolean isMajorTick = (i % 30 == 0);

            double tickOuter = MAX_RADIUS;
            double tickInner = isCardinal  ? MAX_RADIUS - 12 :
                               isOrdinal   ? MAX_RADIUS - 9  :
                               isMajorTick ? MAX_RADIUS - 7  :
                                             MAX_RADIUS - 4;

            gc.setStroke(isCardinal ? COLOR_TEXT : isOrdinal ? COLOR_TEXT_DIM : COLOR_RING_BRIGHT);
            gc.setLineWidth(isCardinal ? 1.5 : 0.8);

            gc.strokeLine(
                CENTER_X + tickInner * Math.cos(rad),
                CENTER_Y + tickInner * Math.sin(rad),
                CENTER_X + tickOuter * Math.cos(rad),
                CENTER_Y + tickOuter * Math.sin(rad)
            );
        }

        // Cardinal & ordinal labels
        for (int i = 0; i < 8; i++) {
            double rad = Math.toRadians(i * 45 - 90);
            double lx  = CENTER_X + labelRadius * Math.cos(rad);
            double ly  = CENTER_Y + labelRadius * Math.sin(rad) + 4;

            boolean isCardinal = (i % 2 == 0);
            gc.setFill(isCardinal ? COLOR_TEXT : COLOR_TEXT_DIM);
            gc.setFont(Font.font("Courier New", isCardinal ? 12 : 10));
            gc.fillText(cardinals[i], lx, ly);
        }
    }

    // ── Sweep line ────────────────────────────────────────────────────────────

    private void drawSweepLine(GraphicsContext gc) {
        double endX = CENTER_X + MAX_RADIUS * Math.cos(sweepAngle);
        double endY = CENTER_Y + MAX_RADIUS * Math.sin(sweepAngle);

        // Outer glow
        gc.setStroke(Color.color(0.0, 1.0, 0.25, 0.15));
        gc.setLineWidth(6);
        gc.strokeLine(CENTER_X, CENTER_Y, endX, endY);

        // Mid glow
        gc.setStroke(Color.color(0.0, 1.0, 0.25, 0.35));
        gc.setLineWidth(3);
        gc.strokeLine(CENTER_X, CENTER_Y, endX, endY);

        // Core bright line
        gc.setStroke(COLOR_SWEEP);
        gc.setLineWidth(1.2);
        gc.strokeLine(CENTER_X, CENTER_Y, endX, endY);
    }

    // ── Outer border ring ─────────────────────────────────────────────────────

    private void drawBorder(GraphicsContext gc) {
        gc.setStroke(COLOR_RING_BRIGHT);
        gc.setLineWidth(1.5);
        gc.strokeOval(CENTER_X - MAX_RADIUS, CENTER_Y - MAX_RADIUS,
                      MAX_RADIUS * 2, MAX_RADIUS * 2);
    }

    // ── Overlay text (facility ID, range, mode) ───────────────────────────────

    private void drawOverlayText(GraphicsContext gc) {
        gc.setFont(Font.font("Courier New", 11));
        gc.setFill(COLOR_TEXT_DIM);
        gc.setTextAlign(TextAlignment.LEFT);

        // Top-left info block
        gc.fillText("FACILITY : KXYZ",      CENTER_X - MAX_RADIUS + 8, CENTER_Y - MAX_RADIUS + 18);
        gc.fillText("RANGE    : 100NM",     CENTER_X - MAX_RADIUS + 8, CENTER_Y - MAX_RADIUS + 32);
        gc.fillText("MODE     : APPROACH",  CENTER_X - MAX_RADIUS + 8, CENTER_Y - MAX_RADIUS + 46);

        // Top-right: sweep RPM indicator
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.fillText("SWEEP 3RPM",  CENTER_X + MAX_RADIUS - 8, CENTER_Y - MAX_RADIUS + 18);
        gc.fillText("RANGE A/C",   CENTER_X + MAX_RADIUS - 8, CENTER_Y - MAX_RADIUS + 32);

        // Bottom-left: status
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(Color.color(0.0, 1.0, 0.25, 0.9));
        gc.fillText("● SYSTEM ONLINE", CENTER_X - MAX_RADIUS + 8, CENTER_Y + MAX_RADIUS - 10);
    }
}
