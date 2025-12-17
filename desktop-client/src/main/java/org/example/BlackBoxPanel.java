package org.example;

import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BlackBoxPanel {

    private VBox panel;
    private boolean isVisible = false;

    private XYChart.Series<Number, Number> tempSeries;
    private XYChart.Series<Number, Number> co2Series;
    private Label tempValueLabel;
    private Label co2ValueLabel;

    private boolean isPlaying = false;
    private int timeCounter = 0;

    private static final int MAX_DATA_POINTS = 15;
    private NumberAxis tempXAxis;
    private NumberAxis co2XAxis;

    private double currentTemp = 24.0;
    private double currentCO2 = 420.0;
    private final Random random = new Random();

    public BlackBoxPanel() {
        panel = createWidgetsPanel();
        panel.setTranslateY(-700);

        startDataUpdates();
    }

    public VBox getView() {
        return panel;
    }

    public void toggle() {
        TranslateTransition tt = new TranslateTransition(Duration.millis(400), panel);
        if (isVisible) {
            tt.setToY(-700);
        } else {
            tt.setToY(60);
        }
        tt.play();
        isVisible = !isVisible;
    }

    /* ================= UI ================= */

    private VBox createWidgetsPanel() {
        VBox panel = new VBox(15);
        panel.setAlignment(Pos.TOP_CENTER);
        panel.setStyle(
                "-fx-background-color: rgba(255, 255, 255, 0.95);" +
                        "-fx-padding: 30;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 5);");
        panel.setMaxWidth(1300);
        panel.setMaxHeight(650);

        // Close
        HBox topBar = new HBox();
        topBar.setAlignment(Pos.CENTER_RIGHT);

        Button closeBtn = new Button("✕ Close");
        closeBtn.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        closeBtn.setStyle("-fx-background-color: #EF4444; -fx-text-fill: white;");
        closeBtn.setOnAction(e -> toggle());

        topBar.getChildren().add(closeBtn);

        Label title = new Label("SENSOR WIDGETS");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        title.setTextFill(Color.web("#1F2937"));

        HBox titleBox = new HBox(title);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        titleBox.setStyle("-fx-background-color: #E5E7EB; -fx-padding: 10;");

        HBox row1 = new HBox(15, createTempWidget(), createCO2Widget());
        row1.setAlignment(Pos.CENTER);

        panel.getChildren().addAll(topBar, titleBox, row1);
        return panel;
    }

    private VBox createTempWidget() {
        VBox box = baseWidget("🌡️ Temperature");
        tempValueLabel = new Label("24.0 °C");
        tempValueLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));

        tempXAxis = new NumberAxis(0, MAX_DATA_POINTS, 1);
        tempXAxis.setAutoRanging(false);
        tempXAxis.setTickLabelsVisible(false);
        tempXAxis.setTickMarkVisible(false);

        NumberAxis yAxis = new NumberAxis(15, 35, 5);
        yAxis.setAutoRanging(false);
        yAxis.setTickLabelsVisible(false);

        LineChart<Number, Number> chart = new LineChart<>(tempXAxis, yAxis);
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        chart.setLegendVisible(false);
        chart.setPrefHeight(100);

        tempSeries = new XYChart.Series<>();
        chart.getData().add(tempSeries);

        box.getChildren().addAll(tempValueLabel, chart);
        return box;
    }

    private VBox createCO2Widget() {
        VBox box = baseWidget("💨 CO2 Sensor");
        co2ValueLabel = new Label("420 ppm");
        co2ValueLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));

        co2XAxis = new NumberAxis(0, MAX_DATA_POINTS, 1);
        co2XAxis.setAutoRanging(false);
        co2XAxis.setTickLabelsVisible(false);
        co2XAxis.setTickMarkVisible(false);

        NumberAxis yAxis = new NumberAxis(300, 800, 100);
        yAxis.setAutoRanging(false);
        yAxis.setTickLabelsVisible(false);

        LineChart<Number, Number> chart = new LineChart<>(co2XAxis, yAxis);
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        chart.setLegendVisible(false);
        chart.setPrefHeight(100);

        co2Series = new XYChart.Series<>();
        chart.getData().add(co2Series);

        box.getChildren().addAll(co2ValueLabel, chart);
        return box;
    }

    private VBox baseWidget(String titleText) {
        VBox box = new VBox(10);
        box.setAlignment(Pos.TOP_CENTER);
        box.setPrefWidth(400);
        box.setStyle(
                "-fx-background-color: #F3F4F6;" +
                        "-fx-border-color: #D1D5DB;" +
                        "-fx-border-width: 2;" +
                        "-fx-padding: 15;");

        Label title = new Label(titleText);
        title.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        box.getChildren().add(title);

        return box;
    }

    /* ================= DATA ================= */

    private void startDataUpdates() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate(() -> Platform.runLater(() -> {
            if (!isPlaying)
                return;

            currentTemp += (random.nextDouble() - 0.5) * 2;
            currentCO2 += (random.nextDouble() - 0.5) * 30;

            tempValueLabel.setText(String.format("%.1f °C", currentTemp));
            co2ValueLabel.setText(String.format("%.0f ppm", currentCO2));

            tempSeries.getData().add(new XYChart.Data<>(timeCounter, currentTemp));
            co2Series.getData().add(new XYChart.Data<>(timeCounter, currentCO2));

            if (tempSeries.getData().size() > MAX_DATA_POINTS) {
                tempSeries.getData().remove(0);
                co2Series.getData().remove(0);
            }

            timeCounter++;
        }), 0, 1, TimeUnit.SECONDS);
    }

    /* ================= CONTROL ================= */

    public void play() {
        isPlaying = true;
    }

    public void stop() {
        isPlaying = false;
    }
}