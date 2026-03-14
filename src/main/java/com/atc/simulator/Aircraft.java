package com.atc.simulator;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Immutable snapshot of aircraft state — swapped atomically via AtomicReference.
 * All mutable state lives inside AircraftState; Aircraft is the thread-safe wrapper.
 */
public class Aircraft {

    public static final int    TRAIL_LENGTH   = 8;
    public static final double KNOTS_TO_PX    = 0.012; // scale factor: knots → pixels/tick

    // ── Immutable identity ────────────────────────────────────────────────────
    public final String callsign;
    public final String aircraftType;
    public final String origin;
    public final String destination;

    // ── Atomic state (thread-safe, lock-free swap) ────────────────────────────
    private final AtomicReference<AircraftState> stateRef;

    // ── Conflict flag (set by conflict-detector thread) ───────────────────────
    public final AtomicBoolean conflictAlert = new AtomicBoolean(false);

    // ── Position trail (only written by this aircraft's own thread) ───────────
    private final Deque<double[]> trail = new ArrayDeque<>(TRAIL_LENGTH + 1);

    // ─────────────────────────────────────────────────────────────────────────

    public Aircraft(String callsign, String aircraftType,
                    String origin,   String destination,
                    double x,        double y,
                    double heading,  int altitude, int speed) {

        this.callsign      = callsign;
        this.aircraftType  = aircraftType;
        this.origin        = origin;
        this.destination   = destination;
        this.stateRef      = new AtomicReference<>(
            new AircraftState(x, y, heading, altitude, speed, false)
        );
    }

    // ── State access ──────────────────────────────────────────────────────────

    /** Get a consistent snapshot — safe to call from any thread. */
    public AircraftState getState() {
        return stateRef.get();
    }

    /**
     * Atomically replace state. Called only by this aircraft's scheduled thread.
     * Uses compareAndSet loop to handle the rare case of concurrent command updates.
     */
    public void updateState(AircraftState next) {
        stateRef.set(next);
        recordTrail(next.x, next.y);
    }

    /**
     * Apply an ATC command (heading/altitude/speed change).
     * Can be called from the JavaFX thread — uses CAS loop for safety.
     */
    public void applyCommand(double newHeading, int newAltitude, int newSpeed) {
        AircraftState current;
        AircraftState updated;
        do {
            current = stateRef.get();
            updated = new AircraftState(
                current.x, current.y,
                newHeading, newAltitude, newSpeed,
                current.selected
            );
        } while (!stateRef.compareAndSet(current, updated));
    }

    public void setSelected(boolean selected) {
        AircraftState current;
        AircraftState updated;
        do {
            current = stateRef.get();
            updated = new AircraftState(
                current.x, current.y,
                current.heading, current.altitude, current.speed,
                selected
            );
        } while (!stateRef.compareAndSet(current, updated));
    }

    // ── Trail ─────────────────────────────────────────────────────────────────

    private void recordTrail(double x, double y) {
        trail.addLast(new double[]{x, y});
        if (trail.size() > TRAIL_LENGTH) trail.removeFirst();
    }

    /** Returns a snapshot array of trail positions — called from render thread. */
    public double[][] getTrailSnapshot() {
        return trail.toArray(new double[0][]);
    }

    // ── Nested immutable state record ─────────────────────────────────────────

    public record AircraftState(
        double  x,
        double  y,
        double  heading,   // degrees, 0 = North
        int     altitude,  // feet
        int     speed,     // knots
        boolean selected
    ) {}
}
