package org.example;

import javafx.application.Application;
import javafx.stage.Stage;
import javafx.scene.Scene;

public class App extends Application {
    @Override
    public void start(Stage stage) {
        stage.setScene(new Scene(new MainView()));
        stage.show();
    }
}