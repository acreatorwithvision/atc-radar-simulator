package com.atc.simulator;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * Scrollable flight strip panel.
 *
 * A ScheduledExecutorService polls the ConcurrentHashMap every second
 * and calls Platform.runLater() to push updates to the JavaFX thread.
 *
 * Strips are kept in a LinkedHashMap keyed by callsign so insertion
 * order is preserved and lookup is O(1).
 */
public class FlightStripPanel extends VBox {

    private static final Logger LOG = Logger.getLogger(FlightStripPanel.class.getName());

    // ── Styles ────────────────────────────────────────────────────────────────
    private static final String STYLE_HEADER =
        "-fx-text-fill: #39ff14; -fx-font-family: 'Courier New'; " +
        "-fx-font-size: 11; -fx-font-weight: bold;";

    private static final String STYLE_SUBHEADER =
        "-fx-text-fill: #1a5c1a; -fx-font-family: 'Courier New'; -fx-font-size: 9;";

    private static final String STYLE_STAT =
        "-fx-text-fill: #1a5c1a; -fx-font-family: 'Courier New'; -fx-font-size: 9;";

    // ── State ─────────────────────────────────────────────────────────────────
    private final SimulationEngine engine;
    private final LinkedHashMap<String, FlightStrip> stripMap = new LinkedHashMap<>();

    // ── UI ────────────────────────────────────────────────────────────────────
    private final VBox         stripContainer = new VBox(2);
    private final Label        statLabel      = new Label();
    private final ScrollPane   scrollPane;

    // ── Background updater ────────────────────────────────────────────────────
    private final ScheduledExecutorService updater;

    // ─────────────────────────────────────────────────────────────────────────

    public FlightStripPanel(SimulationEngine engine) {
        this.engine = engine;

        setSpacing(0);
        setStyle("-fx-background-color: #050d05;");

        // Header
        Label header = new Label("  FLIGHT STRIPS");
        header.setStyle(STYLE_HEADER);
        header.setPadding(new Insets(8, 0, 2, 0));

        Label subHeader = new Label("  CALLSIGN  ALT   HDG   SPD");
        subHeader.setStyle(STYLE_SUBHEADER);

        statLabel.setStyle(STYLE_STAT);
        statLabel.setPadding(new Insets(0, 0, 4, 0));
        statLabel.setText("  Loading...");

        // Scroll pane for strips
        stripContainer.setStyle("-fx-background-color: #050d05;");
        stripContainer.setPadding(new Insets(2, 0, 2, 0));

        scrollPane = new ScrollPane(stripContainer);
        scrollPane.setStyle(
            "-fx-background: #050d05; " +
            "-fx-background-color: #050d05; " +
            "-fx-border-color: transparent;"
        );
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        getChildren().addAll(header, subHeader, statLabel, scrollPane);

        // ── Background update thread ───────────────────────────────────────
        updater = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("atc-strip-updater");
            return t;
        });

        updater.scheduleAtFixedRate(this::pollAndUpdate, 100, 800, TimeUnit.MILLISECONDS);
    }

    // ── Background poll ───────────────────────────────────────────────────────

    /**
     * Runs on the strip-updater thread.
     * Builds a snapshot of callsigns, then marshals UI work to JavaFX thread.
     */
    private void pollAndUpdate() {
        try {
            // Snapshot from ConcurrentHashMap — safe, no lock
            Collection<Aircraft> all = engine.getAllAircraft();
            Set<String> liveCallsigns = new LinkedHashSet<>();
            for (Aircraft ac : all) liveCallsigns.add(ac.callsign);

            int   total    = all.size();
            int   conflicts = (int) all.stream()
                                       .filter(a -> a.conflictAlert.get())
                                       .count();
            int   ticks    = engine.getTickCount();
            int   threads  = engine.getActiveThreadCount();

            Platform.runLater(() ->
                syncStrips(all, liveCallsigns, total, conflicts, ticks, threads)
            );

        } catch (Exception e) {
            LOG.warning("[Strips] Poll error: " + e.getMessage());
        }
    }

    // ── JavaFX thread: sync strip list ────────────────────────────────────────

    private void syncStrips(Collection<Aircraft> all,
                            Set<String> liveCallsigns,
                            int total, int conflicts,
                            int ticks, int threads) {

        boolean layoutChanged = false;

        // Add new strips
        for (Aircraft ac : all) {
            if (!stripMap.containsKey(ac.callsign)) {
                FlightStrip strip = new FlightStrip(ac);
                stripMap.put(ac.callsign, strip);
                stripContainer.getChildren().add(strip);
                layoutChanged = true;
                LOG.fine("[Strips] Added strip: " + ac.callsign);
            }
        }

        // Remove departed strips
        Iterator<Map.Entry<String, FlightStrip>> it = stripMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, FlightStrip> entry = it.next();
            if (!liveCallsigns.contains(entry.getKey())) {
                stripContainer.getChildren().remove(entry.getValue());
                it.remove();
                layoutChanged = true;
                LOG.fine("[Strips] Removed strip: " + entry.getKey());
            }
        }

        // Refresh all existing strips
        for (FlightStrip strip : stripMap.values()) {
            strip.refresh();
        }

        // Sort: conflicts first, then selected, then alphabetical
        if (layoutChanged || conflicts > 0) {
            sortStrips();
        }

        // Update stat line
        String conflictText = conflicts > 0 ? "  ⚠ " + conflicts + " CONFLICT" : "";
        statLabel.setText("  AC: " + total
                        + "   T: " + threads
                        + "   TICKS: " + (ticks % 100000)
                        + conflictText);
        statLabel.setStyle(conflicts > 0
            ? "-fx-text-fill: #ff4444; -fx-font-family: 'Courier New'; -fx-font-size: 9;"
            : STYLE_STAT);
    }

    // ── Sort strips ───────────────────────────────────────────────────────────

    private void sortStrips() {
        List<FlightStrip> sorted = new ArrayList<>(stripMap.values());
        sorted.sort((a, b) -> {
            boolean ac = a.getCallsign() != null &&
                         engine.getAircraft(a.getCallsign()) != null &&
                         engine.getAircraft(a.getCallsign()).conflictAlert.get();
            boolean bc = b.getCallsign() != null &&
                         engine.getAircraft(b.getCallsign()) != null &&
                         engine.getAircraft(b.getCallsign()).conflictAlert.get();

            if (ac && !bc) return -1;
            if (!ac && bc) return  1;

            boolean as = engine.getAircraft(a.getCallsign()) != null &&
                         engine.getAircraft(a.getCallsign()).getState().selected();
            boolean bs = engine.getAircraft(b.getCallsign()) != null &&
                         engine.getAircraft(b.getCallsign()).getState().selected();

            if (as && !bs) return -1;
            if (!as && bs) return  1;

            return a.getCallsign().compareTo(b.getCallsign());
        });

        stripContainer.getChildren().setAll(sorted);

        // Rebuild map in new order
        stripMap.clear();
        for (FlightStrip s : sorted) stripMap.put(s.getCallsign(), s);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void shutdown() {
        updater.shutdownNow();
        LOG.info("[Strips] Updater stopped.");
    }
}
