package com.atc.simulator;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

/**
 * ATC command panel — shown in the sidebar when an aircraft is selected.
 *
 * The JavaFX thread calls Aircraft.applyCommand() which uses a CAS loop
 * (compareAndSet) on the AtomicReference<AircraftState> — completely
 * lock-free. The aircraft's ScheduledThreadPoolExecutor thread reads the
 * new state on its very next tick.
 */
public class CommandPanel extends VBox {

    // ── Styles ────────────────────────────────────────────────────────────────
    private static final String STYLE_HEADER =
        "-fx-text-fill: #39ff14; -fx-font-family: 'Courier New'; " +
        "-fx-font-size: 12; -fx-font-weight: bold;";

    private static final String STYLE_LABEL =
        "-fx-text-fill: #1a8c0d; -fx-font-family: 'Courier New'; -fx-font-size: 11;";

    private static final String STYLE_FIELD =
        "-fx-background-color: #0a1a0a; -fx-text-fill: #39ff14; " +
        "-fx-font-family: 'Courier New'; -fx-font-size: 12; " +
        "-fx-border-color: #1a5c1a; -fx-border-width: 1; -fx-padding: 3 6;";

    private static final String STYLE_BTN =
        "-fx-background-color: #0d3b0d; -fx-text-fill: #39ff14; " +
        "-fx-font-family: 'Courier New'; -fx-font-size: 11; " +
        "-fx-border-color: #39ff14; -fx-border-width: 1; " +
        "-fx-cursor: hand; -fx-padding: 4 8;";

    private static final String STYLE_BTN_HOVER =
        "-fx-background-color: #1a5c1a; -fx-text-fill: #39ff14; " +
        "-fx-font-family: 'Courier New'; -fx-font-size: 11; " +
        "-fx-border-color: #39ff14; -fx-border-width: 1; " +
        "-fx-cursor: hand; -fx-padding: 4 8;";

    private static final String STYLE_CALLSIGN =
        "-fx-text-fill: #ffffff; -fx-font-family: 'Courier New'; " +
        "-fx-font-size: 14; -fx-font-weight: bold;";

    private static final String STYLE_INFO =
        "-fx-text-fill: #1a8c0d; -fx-font-family: 'Courier New'; -fx-font-size: 10;";

    private static final String STYLE_FEEDBACK_OK  =
        "-fx-text-fill: #39ff14; -fx-font-family: 'Courier New'; -fx-font-size: 10;";

    private static final String STYLE_FEEDBACK_ERR =
        "-fx-text-fill: #ff4444; -fx-font-family: 'Courier New'; -fx-font-size: 10;";

    private static final String STYLE_NONE =
        "-fx-text-fill: #1a5c1a; -fx-font-family: 'Courier New'; " +
        "-fx-font-size: 11; -fx-font-style: italic;";

    // ── UI nodes ──────────────────────────────────────────────────────────────
    private final Label      headerLabel    = new Label("── COMMAND ──────────");
    private final Label      callsignLabel  = new Label("NO SELECTION");
    private final Label      infoLabel      = new Label("");
    private final Label      feedbackLabel  = new Label("");

    private final TextField  hdgField       = new TextField();
    private final TextField  altField       = new TextField();
    private final TextField  spdField       = new TextField();

    private final Button     sendBtn        = new Button("TRANSMIT");
    private final Button     clearBtn       = new Button("DESELECT");

    // ── State ─────────────────────────────────────────────────────────────────
    private Aircraft selectedAircraft = null;
    private Runnable onDeselect       = null;

    // ─────────────────────────────────────────────────────────────────────────

    public CommandPanel() {
        setSpacing(6);
        setPadding(new Insets(8, 6, 8, 6));
        setStyle("-fx-background-color: #050d05; "
               + "-fx-border-color: #1a5c1a; "
               + "-fx-border-width: 1 0 0 0;");

        headerLabel.setStyle(STYLE_HEADER);
        callsignLabel.setStyle(STYLE_NONE);
        infoLabel.setStyle(STYLE_INFO);
        feedbackLabel.setStyle(STYLE_FEEDBACK_OK);
        feedbackLabel.setWrapText(true);

        buildFields();
        buildButtons();

        getChildren().addAll(
            headerLabel,
            callsignLabel,
            infoLabel,
            makeSeparator(),
            makeRow("HDG °", hdgField, "000–359"),
            makeRow("ALT ft", altField, "1000–45000"),
            makeRow("SPD kt", spdField, "150–600"),
            makeSeparator(),
            makeButtonRow(),
            feedbackLabel
        );

        setDisabled(true);
    }

    // ── Build helpers ─────────────────────────────────────────────────────────

    private void buildFields() {
        for (TextField f : new TextField[]{hdgField, altField, spdField}) {
            f.setStyle(STYLE_FIELD);
            f.setPrefWidth(80);
            f.setMaxWidth(80);
        }
        hdgField.setPromptText("e.g. 090");
        altField.setPromptText("e.g. 10000");
        spdField.setPromptText("e.g. 280");
    }

    private void buildButtons() {
        sendBtn.setStyle(STYLE_BTN);
        clearBtn.setStyle(STYLE_BTN);

        sendBtn.setOnMouseEntered(e  -> sendBtn.setStyle(STYLE_BTN_HOVER));
        sendBtn.setOnMouseExited(e   -> sendBtn.setStyle(STYLE_BTN));
        clearBtn.setOnMouseEntered(e -> clearBtn.setStyle(STYLE_BTN_HOVER));
        clearBtn.setOnMouseExited(e  -> clearBtn.setStyle(STYLE_BTN));

        sendBtn.setOnAction(e  -> transmitCommand());
        clearBtn.setOnAction(e -> deselect());
    }

    private HBox makeRow(String labelText, TextField field, String hint) {
        Label lbl  = new Label(labelText);
        lbl.setStyle(STYLE_LABEL);
        lbl.setPrefWidth(52);

        Label hintLbl = new Label(hint);
        hintLbl.setStyle(STYLE_INFO);

        HBox row = new HBox(6, lbl, field, hintLbl);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return row;
    }

    private HBox makeButtonRow() {
        HBox row = new HBox(8, sendBtn, clearBtn);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return row;
    }

    private Label makeSeparator() {
        Label sep = new Label("─────────────────────");
        sep.setStyle(STYLE_INFO);
        return sep;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void setOnDeselect(Runnable r) { this.onDeselect = r; }

    /** Select an aircraft and populate fields with its current state. */
    public void selectAircraft(Aircraft ac) {
        selectedAircraft = ac;
        setDisabled(false);

        Aircraft.AircraftState s = ac.getState();

        callsignLabel.setStyle(STYLE_CALLSIGN);
        callsignLabel.setText(ac.callsign + "  " + ac.aircraftType);

        infoLabel.setText(ac.origin + " → " + ac.destination);

        hdgField.setText(String.format("%.0f", s.heading()));
        altField.setText(String.valueOf(s.altitude()));
        spdField.setText(String.valueOf(s.speed()));

        feedbackLabel.setStyle(STYLE_FEEDBACK_OK);
        feedbackLabel.setText("● " + ac.callsign + " selected");

        ac.setSelected(true);
    }

    /** Deselect current aircraft. */
    public void deselect() {
        if (selectedAircraft != null) {
            selectedAircraft.setSelected(false);
            selectedAircraft = null;
        }
        callsignLabel.setStyle(STYLE_NONE);
        callsignLabel.setText("NO SELECTION");
        infoLabel.setText("");
        feedbackLabel.setText("");
        hdgField.clear();
        altField.clear();
        spdField.clear();
        setDisabled(true);

        if (onDeselect != null) onDeselect.run();
    }

    public Aircraft getSelectedAircraft() { return selectedAircraft; }

    // ── Command transmission ──────────────────────────────────────────────────

    private void transmitCommand() {
        if (selectedAircraft == null) return;

        try {
            double newHdg = parseHeading(hdgField.getText().trim());
            int    newAlt = parseAltitude(altField.getText().trim());
            int    newSpd = parseSpeed(spdField.getText().trim());

            // ── CAS write on JavaFX thread — read by aircraft thread next tick ──
            selectedAircraft.applyCommand(newHdg, newAlt, newSpd);

            feedbackLabel.setStyle(STYLE_FEEDBACK_OK);
            feedbackLabel.setText("✓ CMD: HDG " + (int) newHdg
                + "°  ALT " + newAlt
                + "ft  SPD " + newSpd + "kt");

            // Refresh display
            infoLabel.setText(selectedAircraft.origin
                + " → " + selectedAircraft.destination
                + "   [CMD ACTIVE]");

        } catch (IllegalArgumentException ex) {
            feedbackLabel.setStyle(STYLE_FEEDBACK_ERR);
            feedbackLabel.setText("✗ " + ex.getMessage());
        }
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private double parseHeading(String s) {
        try {
            double v = Double.parseDouble(s);
            if (v < 0 || v > 359)
                throw new IllegalArgumentException("HDG must be 000–359");
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid heading: " + s);
        }
    }

    private int parseAltitude(String s) {
        try {
            int v = Integer.parseInt(s);
            if (v < 1_000 || v > 45_000)
                throw new IllegalArgumentException("ALT must be 1000–45000 ft");
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid altitude: " + s);
        }
    }

    private int parseSpeed(String s) {
        try {
            int v = Integer.parseInt(s);
            if (v < 150 || v > 600)
                throw new IllegalArgumentException("SPD must be 150–600 kt");
            return v;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid speed: " + s);
        }
    }
}
