package org.example;

import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.ByteArrayInputStream;
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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;

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

    // ===== DB 재생용 세션 정보 DTO =====
    public static class VideoSession {
        final long id;
        final long startedAtMs;

        VideoSession(long id, long startedAtMs) {
            this.id = id;
            this.startedAtMs = startedAtMs;
        }

        @Override
        public String toString() {
            // Format the timestamp for display
            Timestamp ts = new Timestamp(startedAtMs);
            String formattedTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(ts);
            return String.format("Session %d (%s)", id, formattedTime);
        }
    }

    public static List<VideoSession> fetchAllSessions(String url, String user, String pass) {
        List<VideoSession> sessions = new ArrayList<>();
        String sql = "SELECT id, started_at_ms FROM video_session ORDER BY started_at_ms DESC";

        try (Connection c = DriverManager.getConnection(url, user, pass);
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                long id = rs.getLong("id");
                long startedAtMs = rs.getLong("started_at_ms");
                sessions.add(new VideoSession(id, startedAtMs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            // In a real app, show an error dialog to the user
        }
        return sessions;
    }

    // ====== 원본 필드들(그대로) ======
    private XYChart.Series<Number, Number> tempSeries;
    private XYChart.Series<Number, Number> co2Series;
    private Label tempValueLabel;
    private Label co2ValueLabel;
    private Label flameValueLabel;
    private Label pirValueLabel;
    private Label pm25ValueLabel;
    private Label pm10ValueLabel;
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

    // ====== camera video view (추가: 기존 Camera 화면 위에 영상만 덮어씀) ======
    private ImageView cameraImageView;
    private VBox cameraPlaceholderBox;

    // ====== DB playback (추가) ======
    private boolean dbMode = false;

    // DB connection (환경변수/시스템프로퍼티로 덮어쓰기 가능)
    private static final String DB_URL = System.getProperty(
            "SERBOT_DB_URL",
            System.getenv().getOrDefault("SERBOT_DB_URL", "jdbc:mysql://localhost:3306/serbot?useSSL=false&serverTimezone=Asia/Seoul")
    );
    private static final String DB_USER = System.getProperty(
            "SERBOT_DB_USER",
            System.getenv().getOrDefault("SERBOT_DB_USER", "root")
    );
    private static final String DB_PASS = System.getProperty(
            "SERBOT_DB_PASS",
            System.getenv().getOrDefault("SERBOT_DB_PASS", "")
    );
    // 외부에서 주입 가능(없으면 환경변수/기본값 사용)
    private String dbUrlOverride = null;
    private String dbUserOverride = null;
    private String dbPassOverride = null;

    private volatile long currentSessionId = -1;
    private volatile long sessionStartMs = 0;
    private volatile int sessionFps = 5;
    private volatile int sessionDurationSec = 300; // slider max 기본

    private static final class DbFrame {
        final long tsMs;
        final int frameIndex;
        final byte[] jpeg;
        DbFrame(long tsMs, int frameIndex, byte[] jpeg) {
            this.tsMs = tsMs;
            this.frameIndex = frameIndex;
            this.jpeg = jpeg;
        }
    }

    private static final class DbSensor {
        final long tsMs;
        final boolean fire;
        final double co2;
        final double pm25;
        final double pm10;
        final Boolean pir;
        DbSensor(long tsMs, boolean fire, double co2, double pm25, double pm10, Boolean pir) {
            this.tsMs = tsMs;
            this.fire = fire;
            this.co2 = co2;
            this.pm25 = pm25;
            this.pm10 = pm10;
            this.pir = pir;
        }
    }

    private final List<DbFrame> frames = Collections.synchronizedList(new ArrayList<>());
    private final List<DbSensor> sensors = Collections.synchronizedList(new ArrayList<>());

    private volatile int framePtr = 0;
    private volatile int sensorPtr = 0;

    private final AtomicBoolean sliderIsDragging = new AtomicBoolean(false);
    private final AtomicBoolean internalSliderUpdate = new AtomicBoolean(false);

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
        // 오버레이 패널 클릭/드래그 영역 안정화
        widgetsPanel.setPickOnBounds(true);

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

        // Camera content
        VBox centerContent = new VBox(20);
        centerContent.setAlignment(Pos.CENTER);
        VBox.setVgrow(centerContent, Priority.ALWAYS);

        // ✅ 영상이 들어올 자리(ImageView). 기본 화면은 유지하고, 영상만 겹쳐서 띄운다.
        cameraImageView = new ImageView();
        cameraImageView.setPreserveRatio(true);
        cameraImageView.setSmooth(true);
        cameraImageView.setFitWidth(1100);
        cameraImageView.setFitHeight(600);

        // ✅ 기존 "Camera" 플레이스홀더 UI (원본 유지)
        Label cameraIcon = new Label("📷");
        cameraIcon.setFont(Font.font(100));

        Label cameraTitle = new Label("Camera");
        cameraTitle.setFont(Font.font("Arial", FontWeight.BOLD, 36));
        cameraTitle.setTextFill(Color.WHITE);

        Label subtitle = new Label("Robot Vision Feed");
        subtitle.setFont(Font.font("Arial", 18));
        subtitle.setTextFill(Color.web("#9CA3AF"));

        cameraPlaceholderBox = new VBox(20);
        cameraPlaceholderBox.setAlignment(Pos.CENTER);
        cameraPlaceholderBox.getChildren().addAll(cameraIcon, cameraTitle, subtitle);

        // ✅ 겹치기: 영상 + 플레이스홀더
        StackPane cameraStack = new StackPane();
        cameraStack.setAlignment(Pos.CENTER);
        cameraStack.getChildren().addAll(cameraImageView, cameraPlaceholderBox);

        // 이미지가 없을 때만 플레이스홀더 보이게
        cameraPlaceholderBox.setVisible(cameraImageView.getImage() == null);
        cameraImageView.imageProperty().addListener((obs, oldImg, newImg) -> {
            if (cameraPlaceholderBox != null) {
                cameraPlaceholderBox.setVisible(newImg == null);
            }
        });

        centerContent.getChildren().add(cameraStack);

        // Video controls
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

            // ✅ DB 모드: 슬라이더 값(초) = 해당 시점 프레임 보여주기
            if (dbMode && !internalSliderUpdate.get() && !sliderIsDragging.get()) {
                showFrameBySecond(videoTime);
            }
        });

        videoSlider.setOnMousePressed(e -> {
            sliderIsDragging.set(true);
            if (isPlaying) {
                togglePlayPause();
            }
        });

        videoSlider.setOnMouseReleased(e -> {
            sliderIsDragging.set(false);
            if (dbMode) {
                showFrameBySecond((int) videoSlider.getValue());
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

        tempValueLabel = new Label("온도: -- °C");
        tempValueLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));

        tempXAxis = new NumberAxis(0, MAX_DATA_POINTS, 1);
        tempXAxis.setAutoRanging(false);
        tempXAxis.setTickLabelsVisible(false);
        tempXAxis.setTickMarkVisible(false);

        NumberAxis yAxis = new NumberAxis(24, 28, 0.5);
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

        NumberAxis yAxis = new NumberAxis(0, 500, 100);
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

        if (titleText.contains("Flame")) {
            this.flameValueLabel = value;
        } else if (titleText.contains("PIR")) {
            this.pirValueLabel = value;
        }

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

        this.pm25ValueLabel = new Label("PM2.5: - μg/m³");
        this.pm25ValueLabel.setFont(Font.font("Arial", 12));

        this.pm10ValueLabel = new Label("PM10: - μg/m³");
        this.pm10ValueLabel.setFont(Font.font("Arial", 12));

        widget.getChildren().addAll(title, pm25ValueLabel, pm10ValueLabel);

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
            if (isPlaying && !dbMode) {
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
            if (!isPlaying) return;

            if (!dbMode) {
                videoTime++;
                if (videoTime > 300) videoTime = 0;
                if (videoSlider != null) {
                    internalSliderUpdate.set(true);
                    try { videoSlider.setValue(videoTime); } finally { internalSliderUpdate.set(false); }
                }
                return;
            }

            // DB mode: 프레임 단위 재생
            if (frames.isEmpty()) return;
            int nextIdx = Math.min(framePtr + 1, frames.size() - 1);
            showFrameAt(nextIdx);

        }), 0, 200, TimeUnit.MILLISECONDS); // 5fps 기본 틱
    }

    // ====== 외부 제어용(필요하면 MainFx에서 사용) ======

    // ====================== DB 재생 API ======================

    /**
     * DB의 특정 video_session을 로드해서 재생 준비한다.
     * - UI 구조는 유지, 카메라 영역에 DB 프레임을 넣어준다.
     */
    public void loadDbSession(long sessionId) {
        pause();

        this.dbMode = true;
        this.currentSessionId = sessionId;
        this.framePtr = 0;
        this.sensorPtr = 0;
        this.timeCounter = 0;

        frames.clear();
        sensors.clear();

        new Thread(() -> {
            try {
                loadSessionMeta(sessionId);
                loadFrames(sessionId);
                loadSensorsForSessionWindow();

                Platform.runLater(() -> {
                    if (videoSlider != null) {
                        internalSliderUpdate.set(true);
                        try {
                            videoSlider.setMin(0);
                            videoSlider.setMax(sessionDurationSec);
                            videoSlider.setValue(0);
                        } finally {
                            internalSliderUpdate.set(false);
                        }
                    }
                    showFrameAt(0);
                });

            } catch (Exception e) {
                System.out.println("⚠ loadDbSession failed: " + e.getMessage());
            }
        }, "DB-Loader").start();
    }

    /** DB 모드를 끄고(실시간/데모 모드로) 되돌린다. */
    public void disableDbMode() {
        pause();
        dbMode = false;
        currentSessionId = -1;
        clearCameraImage();

        timeCounter = 0;
        if (tempSeries != null) tempSeries.getData().clear();
        if (co2Series != null) co2Series.getData().clear();
    }

    private Connection openDb() throws SQLException {
        String url = (dbUrlOverride != null) ? dbUrlOverride : DB_URL;
        String user = (dbUserOverride != null) ? dbUserOverride : DB_USER;
        String pass = (dbPassOverride != null) ? dbPassOverride : DB_PASS;
        return DriverManager.getConnection(url, user, pass);
    }

    private void loadSessionMeta(long sessionId) throws SQLException {
        String sql = "SELECT started_at_ms, ended_at_ms, fps FROM video_session WHERE id=?";
        try (Connection c = openDb(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("video_session not found: id=" + sessionId);
                sessionStartMs = rs.getLong("started_at_ms");
                sessionFps = rs.getInt("fps");

                long ended = rs.getLong("ended_at_ms");
                boolean endedIsNull = rs.wasNull();

                sessionDurationSec = 300;
                if (!endedIsNull && ended > sessionStartMs) {
                    sessionDurationSec = (int) Math.max(1, (ended - sessionStartMs) / 1000L);
                }
            }
        }
    }

    private void loadFrames(long sessionId) throws SQLException {
        String sql = "SELECT frame_index, received_at_ms, jpeg_bytes FROM video_frame WHERE session_id=? ORDER BY frame_index ASC";
        try (Connection c = openDb(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int idx = rs.getInt("frame_index");
                    long ts = rs.getLong("received_at_ms");
                    byte[] jpeg = rs.getBytes("jpeg_bytes");
                    frames.add(new DbFrame(ts, idx, jpeg));
                }
            }
        }

        if (!frames.isEmpty()) {
            long endTs = frames.get(frames.size() - 1).tsMs;
            if (endTs > sessionStartMs) {
                sessionDurationSec = (int) Math.max(sessionDurationSec, (endTs - sessionStartMs) / 1000L);
            }
        }

        System.out.println("✅ loaded frames: " + frames.size() + " (session=" + sessionId + ")");
    }

    private void loadSensorsForSessionWindow() throws SQLException {
        long start = sessionStartMs;
        long end;
        if (!frames.isEmpty()) end = frames.get(frames.size() - 1).tsMs;
        else end = sessionStartMs + (long) sessionDurationSec * 1000L;

        String sql = "SELECT received_at_ms, fire, co2, pm25, pm10, pir FROM sensor_snapshot WHERE received_at_ms BETWEEN ? AND ? ORDER BY received_at_ms ASC";
        try (Connection c = openDb(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, start);
            ps.setLong(2, end);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long ts = rs.getLong("received_at_ms");
                    boolean fire = rs.getInt("fire") == 1;
                    double co2 = rs.getDouble("co2");
                    double pm25 = rs.getDouble("pm25");
                    double pm10 = rs.getDouble("pm10");

                    int pirInt = rs.getInt("pir");
                    Boolean pir = rs.wasNull() ? null : (pirInt == 1);

                    sensors.add(new DbSensor(ts, fire, co2, pm25, pm10, pir));
                }
            }
        }
        System.out.println("✅ loaded sensors: " + sensors.size());
    }

    private void showFrameBySecond(int sec) {
        if (!dbMode) return;
        if (frames.isEmpty()) return;

        long targetTs = sessionStartMs + (long) sec * 1000L;

        int bestIdx = 0;
        synchronized (frames) {
            for (int i = 0; i < frames.size(); i++) {
                if (frames.get(i).tsMs <= targetTs) bestIdx = i;
                else break;
            }
        }
        showFrameAt(bestIdx);
    }

    /** frames 리스트의 인덱스(0-based) 기준으로 화면 표시 */
    private void showFrameAt(int frameIndex0Based) {
        if (!dbMode) return;
        DbFrame f;
        synchronized (frames) {
            if (frames.isEmpty()) return;
            int idx = Math.max(0, Math.min(frameIndex0Based, frames.size() - 1));
            f = frames.get(idx);
            framePtr = idx;
        }

        if (f.jpeg != null && f.jpeg.length > 0) {
            showCameraJpeg(f.jpeg);
        }

        advanceSensorTo(f.tsMs);

        long elapsedMs = Math.max(0, f.tsMs - sessionStartMs);
        int sec = (int) (elapsedMs / 1000L);
        videoTime = sec;

        int mins = sec / 60;
        int secs = sec % 60;
        if (timeLabel != null) timeLabel.setText(String.format("%d:%02d", mins, secs));

        if (videoSlider != null) {
            internalSliderUpdate.set(true);
            try { videoSlider.setValue(sec); } finally { internalSliderUpdate.set(false); }
        }
    }

    private void advanceSensorTo(long targetTsMs) {
        if (!dbMode) return;

        DbSensor last = null;
        synchronized (sensors) {
            while (sensorPtr < sensors.size() && sensors.get(sensorPtr).tsMs <= targetTsMs) {
                last = sensors.get(sensorPtr);
                sensorPtr++;
            }
        }
        if (last == null) return;

        // Generate random temperature between 25 and 27
        currentTemp = 25.0 + (27.0 - 25.0) * random.nextDouble();
        currentCO2 = last.co2;

        if (tempValueLabel != null) {
            tempValueLabel.setText(String.format("온도: %.1f °C", currentTemp));
        }
        if (co2ValueLabel != null) {
            co2ValueLabel.setText(String.format("%.0f ppm", currentCO2));
        }

        // Update Flame widget
        if (flameValueLabel != null) {
            flameValueLabel.setText(last.fire ? "화재 감지!" : "정상");
            flameValueLabel.setTextFill(last.fire ? Color.RED : Color.GREEN);
        }

        // Update PIR widget
        if (pirValueLabel != null) {
            String pirText = (last.pir == null) ? "N/A" : (last.pir ? "감지됨" : "대기중");
            pirValueLabel.setText(pirText);
        }

        // Update Dust widget
        if (pm25ValueLabel != null) {
            pm25ValueLabel.setText(String.format("PM2.5: %.1f μg/m³", last.pm25));
        }
        if (pm10ValueLabel != null) {
            pm10ValueLabel.setText(String.format("PM10: %.1f μg/m³", last.pm10));
        }

        if (tempSeries != null) {
            tempSeries.getData().add(new XYChart.Data<>(timeCounter, currentTemp));
            if (tempSeries.getData().size() > MAX_DATA_POINTS) tempSeries.getData().remove(0);
        }
        if (co2Series != null) {
            co2Series.getData().add(new XYChart.Data<>(timeCounter, currentCO2));
            if (co2Series.getData().size() > MAX_DATA_POINTS) co2Series.getData().remove(0);
        }

        if (timeCounter >= MAX_DATA_POINTS) {
            tempXAxis.setLowerBound(timeCounter - MAX_DATA_POINTS + 1);
            tempXAxis.setUpperBound(timeCounter);
            co2XAxis.setLowerBound(timeCounter - MAX_DATA_POINTS + 1);
            co2XAxis.setUpperBound(timeCounter);
        }
        timeCounter++;
    }

    /**
     * 소켓/DB에서 받은 JPEG 바이트를 카메라 영역에 표시한다.
     * - 기존 Camera 플레이스홀더 UI는 유지하고, 프레임이 들어오면 자동으로 영상이 올라간다.
     */
    public void showCameraJpeg(byte[] jpegBytes) {
        if (jpegBytes == null || jpegBytes.length == 0) return;
        Platform.runLater(() -> {
            try {
                if (cameraImageView == null) return;
                Image img = new Image(new ByteArrayInputStream(jpegBytes));
                cameraImageView.setImage(img);
                if (cameraPlaceholderBox != null) cameraPlaceholderBox.setVisible(false);
            } catch (Exception e) {
                System.out.println("⚠ showCameraJpeg failed: " + e.getMessage());
            }
        });
    }

    /** 프레임이 없을 때 다시 기본 Camera 화면만 보이게 하고 싶으면 호출 */
    public void clearCameraImage() {
        Platform.runLater(() -> {
            if (cameraImageView != null) cameraImageView.setImage(null);
            if (cameraPlaceholderBox != null) cameraPlaceholderBox.setVisible(true);
        });
    }

    // ====================== MainFx 호환 API ======================

    /**
     * MainFx에서 호출하는 호환 API.
     * - url/user/pass가 null/blank면 기존 환경변수/기본값(DB_URL/DB_USER/DB_PASS)을 사용한다.
     */
    public void setDbConfig(String url, String user, String pass) {
        this.dbUrlOverride = (url == null || url.isBlank()) ? null : url;
        this.dbUserOverride = (user == null || user.isBlank()) ? null : user;
        this.dbPassOverride = (pass == null) ? null : pass;
        System.out.println("✅ [BlackBox] DB config set (override=" + (this.dbUrlOverride != null) + ")");
    }

    /**
     * MainFx에서 호출하는 호환 API.
     * 내부적으로 loadDbSession(sessionId)를 호출한다.
     */
    public void loadSessionFromDb(long sessionId) {
        try {
            loadDbSession(sessionId);
        } catch (Exception e) {
            System.out.println("⚠ [BlackBox] loadSessionFromDb failed: " + e.getMessage());
        }
    }

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