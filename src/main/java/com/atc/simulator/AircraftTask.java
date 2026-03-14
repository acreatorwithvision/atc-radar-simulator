package com.atc.simulator;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Runnable executed on a ScheduledThreadPoolExecutor thread.
 * One instance per aircraft — updates position each tick.
 *
 * Concurrency guarantees:
 *   - Waits on CountDownLatch before first move
 *   - Checks AtomicBoolean paused each tick (no blocking, just skip)
 *   - Writes new state via AtomicReference CAS inside Aircraft.updateState()
 */
public class AircraftTask implements Runnable {

    private static final Logger LOG = Logger.getLogger(AircraftTask.class.getName());

    private final Aircraft       aircraft;
    private final CountDownLatch startLatch;
    private final AtomicBoolean  paused;
    private final AtomicBoolean  running;
    private final AtomicInteger  tickCount;

    private boolean hasStarted = false;

    public AircraftTask(Aircraft aircraft,
                        CountDownLatch startLatch,
                        AtomicBoolean  paused,
                        AtomicBoolean  running,
                        AtomicInteger  tickCount) {
        this.aircraft   = aircraft;
        this.startLatch = startLatch;
        this.paused     = paused;
        this.running    = running;
        this.tickCount  = tickCount;
    }

    @Override
    public void run() {
        // ── Wait for simulation start signal (CountDownLatch) ──────────────
        if (!hasStarted) {
            try {
                startLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            hasStarted = true;
            LOG.fine("[Task] " + aircraft.callsign + " ready on thread "
                     + Thread.currentThread().getName());
        }

        // ── Skip tick if paused or shut down ───────────────────────────────
        if (!running.get() || paused.get()) return;

        // ── Increment global tick counter ─────────────────────────────────
        tickCount.incrementAndGet();

        // ── Compute next position ─────────────────────────────────────────
        Aircraft.AircraftState state = aircraft.getState();

        double headingRad = Math.toRadians(state.heading() - 90);
        double velocityPx = state.speed() * Aircraft.KNOTS_TO_PX;

        double newX = state.x() + velocityPx * Math.cos(headingRad);
        double newY = state.y() + velocityPx * Math.sin(headingRad);

        // ── Wrap around radar boundary (toroidal space) ───────────────────
        double r = RadarCanvas.MAX_RADIUS;
        double cx = RadarCanvas.CENTER_X;
        double cy = RadarCanvas.CENTER_Y;

        double dx = newX - cx;
        double dy = newY - cy;
        if (Math.sqrt(dx * dx + dy * dy) > r) {
            // Re-enter from the opposite side
            newX = cx + (cx - newX) * 0.1;
            newY = cy + (cy - newY) * 0.1;
        }

        // ── Swap in new state atomically ───────────────────────────────────
        aircraft.updateState(new Aircraft.AircraftState(
            newX, newY,
            state.heading(),
            state.altitude(),
            state.speed(),
            state.selected()
        ));
    }
}
