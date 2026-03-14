package com.atc.simulator;

import javafx.application.Platform;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Dedicated conflict-detection thread.
 *
 * Runs every 500 ms on its own daemon thread — completely independent of the
 * aircraft movement threads. For every unique pair of aircraft it:
 *   1. Reads both AtomicReference<AircraftState> snapshots  (lock-free)
 *   2. Computes horizontal separation in pixels
 *   3. Checks vertical separation in feet
 *   4. Sets / clears AtomicBoolean conflictAlert on each Aircraft
 *   5. Notifies the registered ConflictListener on the JavaFX thread
 *
 * Concurrency notes:
 *   - ConcurrentHashMap.values() snapshot is safe to iterate mid-update
 *   - AtomicBoolean.set() is the only write — no locks required
 *   - Platform.runLater() marshals UI callbacks back to JavaFX thread
 */
public class ConflictDetector {

    private static final Logger LOG = Logger.getLogger(ConflictDetector.class.getName());

    // ── Separation minima ─────────────────────────────────────────────────────
    /** Pixel radius below which horizontal conflict is declared (~5 NM at 100NM range). */
    public static final double MIN_HORIZONTAL_PX  = 38.0;

    /** Feet below which vertical conflict is declared (1 000 ft standard separation). */
    public static final int    MIN_VERTICAL_FT    = 1_000;

    // ── Scheduler ─────────────────────────────────────────────────────────────
    private final ScheduledExecutorService detectorThread;

    // ── Engine reference ──────────────────────────────────────────────────────
    private final SimulationEngine engine;

    // ── Statistics ────────────────────────────────────────────────────────────
    private final AtomicInteger totalConflictsFound = new AtomicInteger(0);
    private final AtomicInteger scanCount           = new AtomicInteger(0);

    // ── Callback ──────────────────────────────────────────────────────────────
    private ConflictListener listener;

    // ─────────────────────────────────────────────────────────────────────────

    public interface ConflictListener {
        /** Called on the JavaFX thread whenever the conflict set changes. */
        void onConflictUpdate(List<ConflictPair> activeConflicts);
    }

    /** Immutable snapshot of one conflict pair — safe to hand to UI thread. */
    public record ConflictPair(
        String callsignA,
        String callsignB,
        double separationPx,
        int    verticalSepFt
    ) {
        public String label() {
            return callsignA + " / " + callsignB;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    public ConflictDetector(SimulationEngine engine) {
        this.engine = engine;
        this.detectorThread = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("atc-conflict-detector");
            return t;
        });
    }

    public void setListener(ConflictListener listener) {
        this.listener = listener;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void start() {
        detectorThread.scheduleAtFixedRate(
            this::scan, 200, 500, TimeUnit.MILLISECONDS
        );
        LOG.info("[Conflict] Detector started. "
               + "H-sep: " + MIN_HORIZONTAL_PX + "px  "
               + "V-sep: " + MIN_VERTICAL_FT   + "ft");
    }

    public void shutdown() {
        detectorThread.shutdownNow();
        LOG.info("[Conflict] Detector stopped. "
               + "Total scans: "     + scanCount.get()
               + "  Conflicts found: " + totalConflictsFound.get());
    }

    // ── Core scan ─────────────────────────────────────────────────────────────

    private void scan() {
        try {
            scanCount.incrementAndGet();

            Collection<Aircraft> all = engine.getAllAircraft();
            List<Aircraft>       list = new ArrayList<>(all); // stable snapshot
            List<ConflictPair>   active = new ArrayList<>();

            // First pass: clear all flags
            for (Aircraft ac : list) {
                ac.conflictAlert.set(false);
            }

            // Second pass: O(n²) pair check
            for (int i = 0; i < list.size(); i++) {
                for (int j = i + 1; j < list.size(); j++) {

                    Aircraft a = list.get(i);
                    Aircraft b = list.get(j);

                    Aircraft.AircraftState sa = a.getState(); // AtomicReference read
                    Aircraft.AircraftState sb = b.getState();

                    double dx   = sa.x() - sb.x();
                    double dy   = sa.y() - sb.y();
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    int    vSep = Math.abs(sa.altitude() - sb.altitude());

                    boolean horizConflict = dist   < MIN_HORIZONTAL_PX;
                    boolean vertConflict  = vSep   < MIN_VERTICAL_FT;

                    if (horizConflict && vertConflict) {
                        // Set AtomicBoolean on both aircraft (visible to renderer)
                        a.conflictAlert.set(true);
                        b.conflictAlert.set(true);
                        totalConflictsFound.incrementAndGet();

                        active.add(new ConflictPair(
                            a.callsign, b.callsign,
                            dist, vSep
                        ));

                        LOG.warning("[Conflict] " + a.callsign
                            + " / " + b.callsign
                            + "  H=" + String.format("%.1f", dist) + "px"
                            + "  V=" + vSep + "ft");
                    }
                }
            }

            // Notify UI on JavaFX thread
            if (listener != null) {
                final List<ConflictPair> snapshot = List.copyOf(active);
                Platform.runLater(() -> listener.onConflictUpdate(snapshot));
            }

        } catch (Exception e) {
            LOG.severe("[Conflict] Scan error: " + e.getMessage());
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public int getTotalConflictsFound() { return totalConflictsFound.get(); }
    public int getScanCount()           { return scanCount.get();           }
}
