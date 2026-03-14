package com.atc.simulator;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Core simulation engine.
 *
 * Concurrency architecture:
 *   - ConcurrentHashMap<String, Aircraft>   → registry, safe multi-thread read/write
 *   - ScheduledThreadPoolExecutor           → one recurring task per aircraft
 *   - CountDownLatch(1)                     → all aircraft tasks wait for "sim start" signal
 *   - AtomicBoolean paused                  → pause/resume without killing threads
 *   - AtomicInteger tickCount               → global simulation tick counter
 */
public class SimulationEngine {

    private static final Logger LOG = Logger.getLogger(SimulationEngine.class.getName());

    // ── Aircraft registry ─────────────────────────────────────────────────────
    private final ConcurrentHashMap<String, Aircraft> aircraftMap = new ConcurrentHashMap<>();

    // ── Thread pool: core = 4, max = 16, named daemon threads ─────────────────
    private final ScheduledThreadPoolExecutor scheduler;

    // ── Simulation-start latch: released when user hits START ─────────────────
    private final CountDownLatch startLatch = new CountDownLatch(1);

    // ── Control flags ─────────────────────────────────────────────────────────
    private final AtomicBoolean  paused    = new AtomicBoolean(false);
    private final AtomicBoolean  running   = new AtomicBoolean(false);
    private final AtomicInteger  tickCount = new AtomicInteger(0);

    // ── Tick interval ─────────────────────────────────────────────────────────
    public static final long TICK_MS = 33L; // ~30 ticks/sec

    // ── Future handles for each aircraft task (keyed by callsign) ────────────
    private final ConcurrentHashMap<String, ScheduledFuture<?>> taskMap = new ConcurrentHashMap<>();

    // ─────────────────────────────────────────────────────────────────────────

    public SimulationEngine() {
        scheduler = new ScheduledThreadPoolExecutor(4, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("atc-sim-" + t.threadId());
            return t;
        });
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Spawn initial aircraft and schedule their tasks.
     * Tasks block on startLatch until start() is called.
     */
    public void init(List<Aircraft> initialAircraft) {
        for (Aircraft ac : initialAircraft) {
            registerAircraft(ac);
        }
        LOG.info("[Engine] Initialized with " + initialAircraft.size() + " aircraft.");
    }

    /** Release the start latch — all waiting aircraft tasks begin executing. */
    public void start() {
        running.set(true);
        paused.set(false);
        startLatch.countDown();
        LOG.info("[Engine] Simulation started. Tick interval: " + TICK_MS + "ms");
    }

    public void pause()  { paused.set(true);  LOG.info("[Engine] Paused.");  }
    public void resume() { paused.set(false); LOG.info("[Engine] Resumed."); }

    public void shutdown() {
        running.set(false);
        startLatch.countDown(); // unblock any waiting tasks so they can exit cleanly
        scheduler.shutdownNow();
        LOG.info("[Engine] Shutdown. Total ticks: " + tickCount.get());
    }

    // ── Aircraft management ───────────────────────────────────────────────────

    /** Add an aircraft to the registry and schedule its movement task. */
    public void registerAircraft(Aircraft ac) {
        aircraftMap.put(ac.callsign, ac);
        scheduleAircraftTask(ac);
        LOG.info("[Engine] Registered: " + ac.callsign);
    }

    /** Remove aircraft from registry and cancel its scheduled task. */
    public void removeAircraft(String callsign) {
        aircraftMap.remove(callsign);
        ScheduledFuture<?> f = taskMap.remove(callsign);
        if (f != null) f.cancel(false);
        LOG.info("[Engine] Removed: " + callsign);
    }

    // ── Internal: schedule one recurring task per aircraft ────────────────────

    private void scheduleAircraftTask(Aircraft ac) {
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
            new AircraftTask(ac, startLatch, paused, running, tickCount),
            0L, TICK_MS, TimeUnit.MILLISECONDS
        );
        taskMap.put(ac.callsign, future);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public Collection<Aircraft> getAllAircraft()        { return aircraftMap.values();    }
    public Aircraft getAircraft(String callsign)        { return aircraftMap.get(callsign); }
    public boolean  isPaused()                          { return paused.get();             }
    public boolean  isRunning()                         { return running.get();            }
    public int      getTickCount()                      { return tickCount.get();          }
    public int      getActiveThreadCount()              { return scheduler.getActiveCount(); }
    public int      getAircraftCount()                  { return aircraftMap.size();        }
    public ConcurrentHashMap<String, Aircraft> getMap() { return aircraftMap;               }
}
