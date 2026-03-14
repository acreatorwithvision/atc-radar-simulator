package com.atc.simulator;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.List;

/**
 * JavaFX panel that displays live conflict alerts.
 * Updated exclusively on the JavaFX thread via ConflictListener callback.
 */
public class AlertPanel extends VBox {

    private static final String STYLE_HEADER =
        "-fx-text-fill: #ff4444; -fx-font-family: 'Courier New'; " +
        "-fx-font-size: 12; -fx-font-weight: bold;";

    private static final String STYLE_ALERT =
        "-fx-text-fill: #ff6666; -fx-font-family: 'Courier New'; -fx-font-size: 11;";

    private static final String STYLE_CLEAR =
        "-fx-text-fill: #1a8c0d; -fx-font-family: 'Courier New'; -fx-font-size: 11;";

    private static final String STYLE_STAT =
        "-fx-text-fill: #1a5c1a; -fx-font-family: 'Courier New'; -fx-font-size: 10;";

    // ─────────────────────────────────────────────────────────────────────────

    private final Label         headerLabel;
    private final VBox          alertList;
    private final Label         statLabel;
    private final Timeline      blinkTimeline;
    private boolean             blinkState = false;

    private final ConflictDetector detector;

    // ─────────────────────────────────────────────────────────────────────────

    public AlertPanel(ConflictDetector detector) {
        this.detector = detector;

        setSpacing(3);
        setPadding(new Insets(8, 6, 8, 6));
        setStyle("-fx-background-color: #050d05; "
               + "-fx-border-color: #3b0d0d; "
               + "-fx-border-width: 1 0 0 0;");

        headerLabel = new Label("⚠ CONFLICT ALERTS");
        headerLabel.setStyle(STYLE_HEADER);

        alertList = new VBox(2);

        statLabel = new Label("Scans: 0  |  Total: 0");
        statLabel.setStyle(STYLE_STAT);

        getChildren().addAll(headerLabel, alertList, statLabel);

        // Blink header when conflicts are active
        blinkTimeline = new Timeline(
            new KeyFrame(Duration.millis(600), e -> {
                blinkState = !blinkState;
                headerLabel.setStyle(blinkState
                    ? STYLE_HEADER
                    : "-fx-text-fill: #3b0d0d; -fx-font-family: 'Courier New'; "
                    + "-fx-font-size: 12; -fx-font-weight: bold;");
            })
        );
        blinkTimeline.setCycleCount(Timeline.INDEFINITE);
    }

    /** Called on JavaFX thread by ConflictDetector listener. */
    public void update(List<ConflictDetector.ConflictPair> conflicts) {
        alertList.getChildren().clear();

        if (conflicts.isEmpty()) {
            blinkTimeline.stop();
            headerLabel.setStyle(STYLE_HEADER);

            Label ok = new Label("● NO CONFLICTS");
            ok.setStyle(STYLE_CLEAR);
            alertList.getChildren().add(ok);
        } else {
            blinkTimeline.play();

            for (ConflictDetector.ConflictPair pair : conflicts) {
                Label row = new Label("▶ " + pair.label()
                    + "  H:" + String.format("%.0f", pair.separationPx()) + "px"
                    + "  V:" + pair.verticalSepFt() + "ft");
                row.setStyle(STYLE_ALERT);

                // Fade in each alert row
                FadeTransition ft = new FadeTransition(Duration.millis(200), row);
                ft.setFromValue(0.3);
                ft.setToValue(1.0);
                ft.play();

                alertList.getChildren().add(row);
            }
        }

        // Update scan statistics
        statLabel.setText("Scans: " + detector.getScanCount()
                        + "  |  Found: " + detector.getTotalConflictsFound());
    }
}
