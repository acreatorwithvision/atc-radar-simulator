package com.atc.simulator;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

public class ATCApplication extends Application {

    public static final String APP_TITLE = "ATC Radar Simulator";
    public static final int WINDOW_WIDTH  = 1200;
    public static final int WINDOW_HEIGHT = 800;

    @Override
    public void start(Stage primaryStage) {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #0a0f0a;");

        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT, Color.web("#0a0f0a"));

        primaryStage.setTitle(APP_TITLE);
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();

        System.out.println("[ATC] Application started. Window: "
            + WINDOW_WIDTH + "x" + WINDOW_HEIGHT);
    }

    @Override
    public void stop() {
        System.out.println("[ATC] Application shutting down.");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
