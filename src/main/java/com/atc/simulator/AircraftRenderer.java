package com.atc.simulator;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.Collection;

/**
 * Stateless renderer — reads aircraft state snapshots and paints them.
 * Called from the JavaFX AnimationTimer (render thread) each frame.
 * Reads ConcurrentHashMap values safely — no locks needed.
 */
public class AircraftRenderer {

    // ── Colours ───────────────────────────────────────────────────────────────
    private static final Color COLOR_BLIP          = Color.web("#39ff14");
    private static final Color COLOR_BLIP_SELECTED = Color.web("#ffffff");
    private static final Color COLOR_BLIP_CONFLICT = Color.web("#ff4444");
    private static final Color COLOR_TAG           = Color.web("#39ff14");
    private static final Color COLOR_TAG_DIM       = Color.web("#1a8c0d");
    private static final Color COLOR_TAG_SELECTED  = Color.web("#ffffff");
    private static final Color COLOR_TAG_CONFLICT  = Color.web("#ff6666");
    private static final Color COLOR_TRAIL         = Color.web("#39ff14");
    private static final Color COLOR_LEADER        = Color.web("#1a5c1a");
    private static final Color COLOR_HALO          = Color.web("#39ff1430");
    private static final Color COLOR_CONFLICT_RING = Color.web("#ff444460");

    // ── Sizes ─────────────────────────────────────────────────────────────────
    private static final double BLIP_RADIUS   = 4.0;
    private static final double TAG_OFFSET_X  = 12.0;
    private static final double TAG_OFFSET_Y  = -8.0;
    private static final double LEADER_LENGTH = 10.0;

    private static final Font FONT_TAG      = Font.font("Courier New", 11);
    private static final Font FONT_TAG_BOLD = Font.font("Courier New", 12);

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Render all aircraft. Called every animation frame.
     * @param gc      Graphics context of RadarCanvas
     * @param aircraft  Live collection from ConcurrentHashMap.values()
     * @param sweepAngle  Current sweep angle (radians) — used for "lit" effect
     */
    public void renderAll(GraphicsContext gc,
                          Collection<Aircraft> aircraft,
                          double sweepAngle) {

        for (Aircraft ac : aircraft) {                    // safe ConcurrentHashMap iteration
            Aircraft.AircraftState state = ac.getState(); // atomic snapshot
            boolean conflict  = ac.conflictAlert.get();
            boolean selected  = state.selected();

            drawTrail(gc, ac.getTrailSnapshot(), conflict);
            drawLeaderLine(gc, state, conflict, selected);
            drawBlip(gc, state, conflict, selected);
            drawTag(gc, ac, state, conflict, selected);

            if (conflict) drawConflictRing(gc, state);
        }
    }

    // ── Trail ─────────────────────────────────────────────────────────────────

    private void drawTrail(GraphicsContext gc, double[][] trail, boolean conflict) {
        if (trail.length < 2) return;

        for (int i = 0; i < trail.length; i++) {
            double alpha  = 0.08 + 0.25 * ((double) i / trail.length);
            double radius = 1.5 + 1.5 * ((double) i / trail.length);

            Color trailColor = conflict
                ? Color.color(1.0, 0.27, 0.27, alpha)
                : Color.color(0.22, 1.0, 0.08, alpha);

            gc.setFill(trailColor);
            gc.fillOval(trail[i][0] - radius, trail[i][1] - radius,
                        radius * 2, radius * 2);
        }
    }

    // ── Leader line (blip → tag) ──────────────────────────────────────────────

    private void drawLeaderLine(GraphicsContext gc,
                                Aircraft.AircraftState state,
                                boolean conflict, boolean selected) {
        Color c = conflict ? COLOR_TAG_CONFLICT
                : selected ? COLOR_TAG_SELECTED
                : COLOR_LEADER;

        gc.setStroke(c);
        gc.setLineWidth(0.6);
        gc.strokeLine(
            state.x(), state.y(),
            state.x() + TAG_OFFSET_X,
            state.y() + TAG_OFFSET_Y
        );
    }

    // ── Blip ──────────────────────────────────────────────────────────────────

    private void drawBlip(GraphicsContext gc,
                          Aircraft.AircraftState state,
                          boolean conflict, boolean selected) {
        double x = state.x();
        double y = state.y();

        // Halo glow
        Color halo = conflict ? COLOR_CONFLICT_RING : COLOR_HALO;
        gc.setFill(halo);
        gc.fillOval(x - BLIP_RADIUS * 2.5, y - BLIP_RADIUS * 2.5,
                    BLIP_RADIUS * 5, BLIP_RADIUS * 5);

        // Core blip
        Color blipColor = conflict  ? COLOR_BLIP_CONFLICT
                        : selected  ? COLOR_BLIP_SELECTED
                        : COLOR_BLIP;
        gc.setFill(blipColor);
        gc.fillOval(x - BLIP_RADIUS, y - BLIP_RADIUS,
                    BLIP_RADIUS * 2, BLIP_RADIUS * 2);

        // Heading tick
        double headRad = Math.toRadians(state.heading() - 90);
        double tickLen = 8.0;
        gc.setStroke(blipColor);
        gc.setLineWidth(1.2);
        gc.strokeLine(
            x, y,
            x + tickLen * Math.cos(headRad),
            y + tickLen * Math.sin(headRad)
        );
    }

    // ── Data tag ──────────────────────────────────────────────────────────────

    private void drawTag(GraphicsContext gc,
                         Aircraft ac,
                         Aircraft.AircraftState state,
                         boolean conflict, boolean selected) {

        double tx = state.x() + TAG_OFFSET_X;
        double ty = state.y() + TAG_OFFSET_Y;

        Color tagColor = conflict  ? COLOR_TAG_CONFLICT
                       : selected  ? COLOR_TAG_SELECTED
                       : COLOR_TAG;
        Color dimColor = conflict  ? Color.web("#cc3333")
                       : selected  ? Color.web("#cccccc")
                       : COLOR_TAG_DIM;

        gc.setTextAlign(TextAlignment.LEFT);

        // ── Line 1: callsign ──────────────────────────────────────────────
        gc.setFont(FONT_TAG_BOLD);
        gc.setFill(tagColor);
        gc.fillText(ac.callsign, tx, ty);

        // ── Line 2: altitude (hundreds of feet) ───────────────────────────
        gc.setFont(FONT_TAG);
        gc.setFill(dimColor);
        String altStr = String.format("%03d", state.altitude() / 100);
        gc.fillText(altStr, tx, ty + 12);

        // ── Line 3: speed (knots) ─────────────────────────────────────────
        String spdStr = String.format("%03d", state.speed());
        gc.fillText(spdStr, tx + 28, ty + 12);

        // ── Line 4: type (shown when selected) ────────────────────────────
        if (selected) {
            gc.setFill(tagColor);
            gc.fillText(ac.aircraftType, tx, ty + 24);
            gc.fillText(ac.origin + "→" + ac.destination, tx, ty + 36);
        }
    }

    // ── Conflict ring ─────────────────────────────────────────────────────────

    private void drawConflictRing(GraphicsContext gc,
                                  Aircraft.AircraftState state) {
        double r = 18.0;
        gc.setStroke(Color.web("#ff4444"));
        gc.setLineWidth(1.0);
        gc.setLineDashes(3, 3);
        gc.strokeOval(state.x() - r, state.y() - r, r * 2, r * 2);
        gc.setLineDashes(null);
    }
}
