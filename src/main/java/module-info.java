module com.atc.simulator {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires java.logging;

    opens com.atc.simulator to javafx.fxml;
    exports com.atc.simulator;
}
