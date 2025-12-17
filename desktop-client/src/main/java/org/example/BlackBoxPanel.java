package org.example;

import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 기존 "Main extends Application"로 만들었던 블랙박스(위젯 오버레이) 화면을
 * 다른 화면(MainFx 등)에서 "패널/뷰"처럼 불러다 쓸 수 있게 분리한 클래스.
 *
 * - getView(): 이 패널의 루트(StackPane)를 반환
 * - openInNewWindow(...): 동일 UI를 새 창(Stage)으로 띄움
 *
 * ⚠️ 구조/레이아웃/동작(슬라이드 오버레이, 그래프 갱신, 비디오 타이머)은
 *   네가 올린 원본 코드 흐름을 최대한 그대로 유지.
 */
public class BlackBoxPanel {

    // ====== 원본 필드들(그대로) ======
    private XYChart.Series<Number, Number> tempSeries;
    private XYChart.Series<Number, Number> co2Series;
    private Label tempValueLabel;
    private Label co2ValueLabel;
    private Label timeLabel;
    private Slider videoSlider;
    private VBox widgetsPanel;
    private boolean isPanelVisible = false;
    private Button playBtn;

    private int timeCounter = 0;
    private int videoTime = 120;
    private boolean isPlaying = false;
    private final Random random = new Random();
    private double currentTemp = 24.0;
    private double currentCO2 = 420.0;
    private static final int MAX_DATA_POINTS = 15;
    private NumberAxis tempXAxis;
    private NumberAxis co2XAxis;

    // ====== 뷰 루트(추가) ======
    private final StackPane root;

    // 스케줄러(추가: 외부에서 stop 가능)
    private final ScheduledExecutorService dataScheduler;
    private final ScheduledExecutorService videoScheduler;

    public BlackBoxPanel() {
        // 기존 start()에서 하던 UI 구성 그대로
        BorderPane mainContent = new BorderPane();

        HBox header = createHeader();
        mainContent.setTop(header);

        VBox cameraArea = createCameraArea();
        mainContent.setCenter(cameraArea);

        mainContent.setStyle("-fx-background-color: #111827;");

        widgetsPanel = createWidgetsPanel();
        widgetsPanel.setTranslateY(-700); // Initially hidden above the screen

        root = new StackPane();
        root.getChildren().addAll(mainContent, widgetsPanel);
        StackPane.setAlignment(widgetsPanel, Pos.TOP_CENTER);

        // 원본의 타이머 시작
        dataScheduler = Executors.newScheduledThreadPool(1);
        videoScheduler = Executors.newScheduledThreadPool(1);
        startDataUpdates();
        startVideoTimer();
    }

    /** 이 패널의 루트 뷰(원본 화면 전체)를 반환 */
    public Parent getView() {
        return root;
    }

    /**
     * 이 블랙박스 UI를 "새 창"으로 띄우는 헬퍼.
     * - owner를 넘기면 모달/소유자 설정 가능
     */
    public Stage openInNewWindow(Stage owner) {
        Stage stage = new Stage();
        stage.setTitle("J-SafeGuard DB (BlackBox)");

        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.NONE);
        }

        Scene scene = new Scene(root, 1400, 900);
        stage.setScene(scene);
        stage.show();
        stage.toFront();

        return stage;
    }

    /** 외부에서 강제로 종료하고 싶으면 호출 */
    public void dispose() {
        try { dataScheduler.shutdownNow(); } catch (Exception ignored) {}
        try { videoScheduler.shutdownNow(); } catch (Exception ignored) {}
    }

    // ====================== 원본 메서드들(그대로) ======================

    private HBox createHeader() {
        HBox header = new HBox();
        header.setStyle("-fx-background-color: #1F2937; -fx-padding: 10 20;");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setSpacing(20);

        Label title = new Label("Frame 1 - AI Servot Robot Dashboard");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 18));
        title.setTextFill(Color.WHITE);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button menuBtn = new Button("☰ Menu");
        menuBtn.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        menuBtn.setStyle("-fx-background-color: #3B82F6; -fx-text-fill: white; -fx-cursor: hand;");
        menuBtn.setOnAction(e -> toggleWidgetsPanel());

        header.getChildren().addAll(title, spacer, menuBtn);

        return header;
    }

    private VBox createCameraArea() {
        VBox cameraArea = new VBox();
        cameraArea.setStyle("-fx-background-color: #484444;");
        cameraArea.setAlignment(Pos.CENTER);
        VBox.setVgrow(cameraArea, Priority.ALWAYS);

        VBox centerContent = new VBox(20);
        centerContent.setAlignment(Pos.CENTER);
        VBox.setVgrow(centerContent, Priority.ALWAYS);

        Label cameraIcon = new Label("📷");
        cameraIcon.setFont(Font.font(100));

        Label cameraTitle = new Label("Camera");
        cameraTitle.setFont(Font.font("Arial", FontWeight.BOLD, 36));
        cameraTitle.setTextFill(Color.WHITE);

        Label subtitle = new Label("Robot Vision Feed");
        subtitle.setFont(Font.font("Arial", 18));
        subtitle.setTextFill(Color.web("#9CA3AF"));

        centerContent.getChildren().addAll(cameraIcon, cameraTitle, subtitle);

        VBox controlsBox = createVideoControls();

        cameraArea.getChildren().addAll(centerContent, controlsBox);

        return cameraArea;
    }

    private VBox createVideoControls() {
        VBox controlsBox = new VBox(10);
        controlsBox.setStyle("-fx-background-color: rgba(31, 41, 55, 0.9); -fx-padding: 15;");
        controlsBox.setAlignment(Pos.CENTER);

        HBox sliderBox = new HBox(15);
        sliderBox.setAlignment(Pos.CENTER);

        timeLabel = new Label("2:00");
        timeLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14));
        timeLabel.setTextFill(Color.WHITE);

        videoSlider = new Slider(0, 300, 120);
        videoSlider.setPrefWidth(600);
        videoSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            videoTime = newVal.intValue();
            int mins = videoTime / 60;
            int secs = videoTime % 60;
            timeLabel.setText(String.format("%d:%02d", mins, secs));
        });

        videoSlider.setOnMousePressed(e -> {
            if (isPlaying) {
                togglePlayPause();
            }
        });

        Label endTime = new Label("5:00");
        endTime.setFont(Font.font("Arial", 12));
        endTime.setTextFill(Color.web("#9CA3AF"));

        sliderBox.getChildren().addAll(timeLabel, videoSlider, endTime);

        HBox buttonBox = new HBox(20);
        buttonBox.setAlignment(Pos.CENTER);

        Button backBtn = new Button("⏪ -5s");
        backBtn.setStyle("-fx-background-color: #374151; -fx-text-fill: white; -fx-cursor: hand;");
        backBtn.setOnAction(e -> videoSlider.setValue(Math.max(0, videoSlider.getValue() - 5)));

        playBtn = new Button("▶");
        playBtn.setStyle("-fx-background-color: #3B82F6; -fx-text-fill: white; -fx-font-size: 16; -fx-cursor: hand;");
        playBtn.setPrefSize(50, 35);
        playBtn.setOnAction(e -> togglePlayPause());

        Button forwardBtn = new Button("+5s ⏩");
        forwardBtn.setStyle("-fx-background-color: #374151; -fx-text-fill: white; -fx-cursor: hand;");
        forwardBtn.setOnAction(e -> videoSlider.setValue(Math.min(300, videoSlider.getValue() + 5)));

        buttonBox.getChildren().addAll(backBtn, playBtn, forwardBtn);

        controlsBox.getChildren().addAll(sliderBox, buttonBox);

        return controlsBox;
    }

    private VBox createWidgetsPanel() {
        VBox panel = new VBox(15);
        panel.setStyle("-fx-background-color: rgba(255, 255, 255, 0.95); -fx-padding: 30; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 5);");
        panel.setMaxWidth(1300);
        panel.setMaxHeight(650);
        panel.setAlignment(Pos.TOP_CENTER);

        HBox topBar = new HBox();
        topBar.setAlignment(Pos.CENTER_RIGHT);

        Button closeBtn = new Button("✕ Close");
        closeBtn.setFont(Font.font("Arial", FontWeight.BOLD, 12));
        closeBtn.setStyle("-fx-background-color: #EF4444; -fx-text-fill: white; -fx-cursor: hand;");
        closeBtn.setOnAction(e -> toggleWidgetsPanel());

        topBar.getChildren().add(closeBtn);

        Label widgetsTitle = new Label("SENSOR WIDGETS");
        widgetsTitle.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        widgetsTitle.setTextFill(Color.web("#1F2937"));

        HBox titleBox = new HBox(widgetsTitle);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        titleBox.setStyle("-fx-background-color: #E5E7EB; -fx-padding: 10;");

        HBox row1 = new HBox(15);
        row1.setAlignment(Pos.CENTER);
        row1.setPrefHeight(200);

        VBox tempWidget = createTempWidget();
        VBox co2Widget = createCO2Widget();
        VBox flameWidget = createSimpleWidget("🔥 Flame", "정상", "#22C55E");

        row1.getChildren().addAll(tempWidget, co2Widget, flameWidget);

        HBox row2 = new HBox(15);
        row2.setAlignment(Pos.CENTER);
        row2.setPrefHeight(160);

        VBox pirWidget = createSimpleWidget("🏃 PIR Sensor", "대기중", "#6B7280");
        VBox dustWidget = createDustWidget();
        VBox motionWidget = createSimpleWidget("📡 Microwave", "감지안됨", "#6B7280");

        row2.getChildren().addAll(pirWidget, dustWidget, motionWidget);

        HBox row3 = new HBox(15);
        row3.setAlignment(Pos.CENTER);
        row3.setPrefHeight(160);

        VBox ecoWidget = createSimpleWidget("🍃 Eco Sensor", "120 ppb", "#22C55E");
        VBox pixelWidget = createSimpleWidget("📺 Pixel Display", "Active", "#A855F7");
        VBox lidarWidget = createSimpleWidget("🗺️ LiDAR", "Scanning", "#3B82F6");

        row3.getChildren().addAll(ecoWidget, pixelWidget, lidarWidget);

        panel.getChildren().addAll(topBar, titleBox, row1, row2, row3);

        return panel;
    }

    private VBox createTempWidget() {
        VBox widget = new VBox(10);
        widget.setStyle("-fx-background-color: #F3F4F6; -fx-border-color: #D1D5DB; -fx-border-width: 2; -fx-padding: 15;");
        widget.setPrefWidth(400);
        widget.setAlignment(Pos.TOP_CENTER);

        Label title = new Label("🌡️ Temperature");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 13));

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
        chart.setStyle("-fx-background-color: transparent;");

        tempSeries = new XYChart.Series<>();
        chart.getData().add(tempSeries);

        widget.getChildren().addAll(title, tempValueLabel, chart);

        return widget;
    }

    private VBox createCO2Widget() {
        VBox widget = new VBox(10);
        widget.setStyle("-fx-background-color: #F3F4F6; -fx-border-color: #D1D5DB; -fx-border-width: 2; -fx-padding: 15;");
        widget.setPrefWidth(400);
        widget.setAlignment(Pos.TOP_CENTER);

        Label title = new Label("💨 CO2 Sensor");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 13));

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
        chart.setStyle("-fx-background-color: transparent;");

        co2Series = new XYChart.Series<>();
        chart.getData().add(co2Series);

        widget.getChildren().addAll(title, co2ValueLabel, chart);

        return widget;
    }

    private VBox createSimpleWidget(String titleText, String valueText, String statusColor) {
        VBox widget = new VBox(10);
        widget.setStyle("-fx-background-color: #F3F4F6; -fx-border-color: #D1D5DB; -fx-border-width: 2; -fx-padding: 15;");
        widget.setPrefWidth(400);
        widget.setAlignment(Pos.CENTER);

        Label title = new Label(titleText);
        title.setFont(Font.font("Arial", FontWeight.BOLD, 13));

        Label value = new Label(valueText);
        value.setFont(Font.font("Arial", FontWeight.BOLD, 20));

        widget.getChildren().addAll(title, value);

        return widget;
    }

    private VBox createDustWidget() {
        VBox widget = new VBox(8);
        widget.setStyle("-fx-background-color: #F3F4F6; -fx-border-color: #D1D5DB; -fx-border-width: 2; -fx-padding: 15;");
        widget.setPrefWidth(400);
        widget.setAlignment(Pos.CENTER);

        Label title = new Label("😷 Dust Sensor");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 13));

        Label pm25 = new Label("PM2.5: 8.5 μg/m³");
        pm25.setFont(Font.font("Arial", 12));

        Label pm10 = new Label("PM10: 15.2 μg/m³");
        pm10.setFont(Font.font("Arial", 12));

        widget.getChildren().addAll(title, pm25, pm10);

        return widget;
    }

    private void toggleWidgetsPanel() {
        TranslateTransition transition = new TranslateTransition(Duration.millis(400), widgetsPanel);

        if (isPanelVisible) {
            transition.setToY(-700);
        } else {
            transition.setToY(60);
        }

        transition.play();
        isPanelVisible = !isPanelVisible;
    }

    private void startDataUpdates() {
        dataScheduler.scheduleAtFixedRate(() -> Platform.runLater(() -> {
            if (isPlaying) {
                currentTemp += (random.nextDouble() - 0.5) * 2;
                currentTemp = Math.max(18, Math.min(32, currentTemp));

                currentCO2 += (random.nextDouble() - 0.5) * 30;
                currentCO2 = Math.max(350, Math.min(700, currentCO2));

                if (tempValueLabel != null) {
                    tempValueLabel.setText(String.format("%.1f °C", currentTemp));
                }

                if (co2ValueLabel != null) {
                    co2ValueLabel.setText(String.format("%.0f ppm", currentCO2));
                }

                if (tempSeries != null) {
                    tempSeries.getData().add(new XYChart.Data<>(timeCounter, currentTemp));
                    if (tempSeries.getData().size() > MAX_DATA_POINTS) {
                        tempSeries.getData().remove(0);
                    }
                }

                if (co2Series != null) {
                    co2Series.getData().add(new XYChart.Data<>(timeCounter, currentCO2));
                    if (co2Series.getData().size() > MAX_DATA_POINTS) {
                        co2Series.getData().remove(0);
                    }
                }

                if (timeCounter >= MAX_DATA_POINTS) {
                    tempXAxis.setLowerBound(timeCounter - MAX_DATA_POINTS + 1);
                    tempXAxis.setUpperBound(timeCounter);
                    co2XAxis.setLowerBound(timeCounter - MAX_DATA_POINTS + 1);
                    co2XAxis.setUpperBound(timeCounter);
                }

                timeCounter++;
            }
        }), 0, 1, TimeUnit.SECONDS);
    }

    private void togglePlayPause() {
        isPlaying = !isPlaying;
        if (playBtn != null) {
            if (isPlaying) {
                playBtn.setText("❚❚");
            } else {
                playBtn.setText("▶");
            }
        }
    }

    private void startVideoTimer() {
        videoScheduler.scheduleAtFixedRate(() -> Platform.runLater(() -> {
            if (isPlaying) {
                videoTime++;
                if (videoTime > 300) videoTime = 0;
                if (videoSlider != null) {
                    videoSlider.setValue(videoTime);
                }
            }
        }), 0, 1, TimeUnit.SECONDS);
    }

    // ====== 외부 제어용(필요하면 MainFx에서 사용) ======

    public void play() {
        if (!isPlaying) togglePlayPause();
    }

    public void pause() {
        if (isPlaying) togglePlayPause();
    }

    public boolean isPlaying() {
        return isPlaying;
    }
}