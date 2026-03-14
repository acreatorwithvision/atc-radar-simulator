package com.atc.simulator;

import javafx.scene.layout.*;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.geometry.Insets;

/**
 * One flight strip row — represents a single aircraft in the sidebar.
 * Created and updated exclusively on the JavaFX thread.
 * Reads Aircraft state via AtomicReference snapshot — no locks needed.
 */
public class FlightStrip extends VBox {

    // ── Styles ────────────────────────────────────────────────────────────────
    private static final String STYLE_NORMAL =
        "-fx-background-color: #070f07; " +
        "-fx-border-color: #1a5c1a; " +
        "-fx-border-width: 0 0 1 0;";

    private static final String STYLE_SELECTED =
        "-fx-background-color: #0d260d; " +
        "-fx-border-color: #39ff14; " +
        "-fx-border-width: 1;";

    private static final String STYLE_CONFLICT =
        "-fx-background-color: #1a0505; " +
        "-fx-border-color: #ff4444; " +
        "-fx-border-width: 1;";

    private static final String STYLE_CALLSIGN =
        "-fx-font-family: 'Courier New'; -fx-font-size: 12; " +
        "-fx-font-weight: bold; -fx-text-fill: #39ff14;";

    private static final String STYLE_CALLSIGN_CONFLICT =
        "-fx-font-family: 'Courier New'; -fx-font-size: 12; " +
        "-fx-font-weight: bold; -fx-text-fill: #ff4444;";

    private static final String STYLE_CALLSIGN_SELECTED =
        "-fx-font-family: 'Courier New'; -fx-font-size: 12; " +
        "-fx-font-weight: bold; -fx-text-fill: #ffffff;";

    private static final String STYLE_DATA =
        "-fx-font-family: 'Courier New'; -fx-font-size: 10; " +
        "-fx-text-fill: #1a8c0d;";

    private static final String STYLE_DATA_CONFLICT =
        "-fx-font-family: 'Courier New'; -fx-font-size: 10; " +
        "-fx-text-fill: #cc3333;";

    private static final String STYLE_DATA_SELECTED =
        "-fx-font-family: 'Courier New'; -fx-font-size: 10; " +
        "-fx-text-fill: #aaaaaa;";

    private static final String STYLE_ROUTE =
        "-fx-font-family: 'Courier New'; -fx-font-size: 9; " +
        "-fx-text-fill: #1a5c1a;";

    // ── Nodes ─────────────────────────────────────────────────────────────────
    private final Label callsignLabel = new Label();
    private final Label dataLabel     = new Label();
    private final Label routeLabel    = new Label();

    // ── Bound aircraft ────────────────────────────────────────────────────────
    private final Aircraft aircraft;
    private       boolean  lastConflict  = false;
    private       boolean  lastSelected  = false;

    // ─────────────────────────────────────────────────────────────────────────

    public FlightStrip(Aircraft ac) {
        this.aircraft = ac;

        setSpacing(1);
        setPadding(new Insets(5, 6, 5, 6));
        setPrefWidth(Double.MAX_VALUE);
        setStyle(STYLE_NORMAL);

        routeLabel.setStyle(STYLE_ROUTE);
        routeLabel.setText(ac.origin + " → " + ac.destination
                         + "   " + ac.aircraftType);

        getChildren().addAll(
            makeTopRow(),
            dataLabel,
            routeLabel
        );

        refresh(); // initial paint
    }

    private HBox makeTopRow() {
        HBox row = new HBox(6, callsignLabel);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return row;
    }

    // ── Update (called from JavaFX thread by FlightStripPanel) ────────────────

    /** Refresh labels from latest AtomicReference snapshot. */
    public void refresh() {
        Aircraft.AircraftState s = aircraft.getState(); // lock-free read
        boolean conflict = aircraft.conflictAlert.get();
        boolean selected = s.selected();

        // ── Callsign line ─────────────────────────────────────────────────
        String prefix = conflict ? "▶ " : selected ? "● " : "  ";
        callsignLabel.setText(prefix + aircraft.callsign);
        callsignLabel.setStyle(
            conflict ? STYLE_CALLSIGN_CONFLICT :
            selected ? STYLE_CALLSIGN_SELECTED :
                       STYLE_CALLSIGN
        );

        // ── Data line: alt / hdg / spd ────────────────────────────────────
        String altStr = String.format("A%03d", s.altitude() / 100);
        String hdgStr = String.format("H%03.0f", s.heading());
        String spdStr = String.format("S%03d", s.speed());
        dataLabel.setText("  " + altStr + "  " + hdgStr + "  " + spdStr);
        dataLabel.setStyle(
            conflict ? STYLE_DATA_CONFLICT :
            selected ? STYLE_DATA_SELECTED :
                       STYLE_DATA
        );

        // ── Strip background ──────────────────────────────────────────────
        boolean styleChanged = (conflict != lastConflict) || (selected != lastSelected);
        if (styleChanged) {
            setStyle(conflict ? STYLE_CONFLICT :
                     selected ? STYLE_SELECTED :
                                STYLE_NORMAL);
            lastConflict = conflict;
            lastSelected = selected;
        }
    }

    public String getCallsign() { return aircraft.callsign; }
}
