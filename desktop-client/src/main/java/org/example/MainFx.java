package org.example;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;

import com.studiohartman.jamepad.ControllerManager;
import com.studiohartman.jamepad.ControllerState;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TitledPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * JavaFX 기반 관제 UI
 *
 * 수신 JSON(한 줄에 JSON 1개 + \n 필수):
 * - SENSOR: {"type":"SENSOR","temp":..,"gas":..,"fire":..,"dust":..,"pir":..}
 * - IMAGE: {"type":"IMAGE","data":"base64..."}
 * - LIDAR:
 *   {"type":"LIDAR","robotX":..,"robotY":..,"robotTheta":..,"points":[[x,y],...]}
 *   or [{"x":..,"y":..},...]
 * - STT: {"type":"STT","text":"..."}
 *
 * 송신 JSON:
 * - KEY: {"type":"KEY","cmd":"FORWARD|BACKWARD|LEFT|RIGHT|STOP"}
 * - PAD: {"type":"PAD","lx":..,"ly":..,"rx":..}
 */
public class MainFx extends Application {

    // ===== DB 설정 유틸 =====
    // 우선순위: JVM 시스템 프로퍼티(-DKEY=...) -> 환경변수(KEY) -> 기본값
    private static String pick(String key, String fallback) {
        String v = System.getProperty(key);
        if (v != null && !v.isBlank()) return v;
        v = System.getenv(key);
        if (v != null && !v.isBlank()) return v;
        return fallback;
    }

    // --- 서버 연결 정보 ---
    private static final String SERVER_IP = "192.168.0.33";
    private static final int SERVER_PORT = 6001;

    // JVM 옵션으로 덮어쓰기 가능: -DSERBOT_DB_URL=... -DSERBOT_DB_USER=... -DSERBOT_DB_PASS=...
    // 환경변수로도 가능: SERBOT_DB_URL / SERBOT_DB_USER / SERBOT_DB_PASS
    private static String DB_URL  = pick("SERBOT_DB_URL",  "jdbc:mysql://localhost:3306/serbot?useSSL=false&serverTimezone=Asia/Seoul");
    private static String DB_USER = pick("SERBOT_DB_USER", "root");
    private static String DB_PASS = pick("SERBOT_DB_PASS", "4113");

    // --- 배경 이미지 경로 ---
    // 1) 리소스 우선: src/main/resources/desktop-client/startup_background.png
    private static final String BG_RESOURCE_PATH = "desktop-client\\startup_background.png";
    // 2) 폴백: 작업 디렉토리에 startup_background.png가 있을 때
    private static final String BG_FILE_FALLBACK = "file:desktop-client/startup_background.png";

    // --- 네트워크 ---
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    // --- 루트 / 화면 전환 ---
    private StackPane root;
    private StackPane introView;
    private StackPane mainView; // 배경+콘텐츠를 한 덩어리로 묶기 위해 StackPane

    // 인트로 상태 표시
    private Label introStatusLabel;
    private Circle introStatusCircle;

    // 메인 화면 요소
    private Label lblConnStatus;
    private Circle connStatusCircle;

    private LineChartWithApi tempChart;
    private LineChartWithApi gasChart;
    private Label lblFireStatus;

    // 카메라
    private ImageView cameraView;

    // PIR 별도 패널 (카메라 위)
    private Label lblPirPanel;

    // LiDAR + STT + Dust
    private LidarView lidarView;
    private TextArea sttTextArea;
    private LineChartWithApi dustChart;

    // --- BlackBox (새 창으로 띄우기) ---
    private Stage blackBoxStage;
    private BlackBoxPanel blackBoxPanel;

    // --- 게임패드 관련 ---
    private ControllerManager controllers;
    private ScheduledExecutorService gamepadExecutor;
    // 마지막으로 전송한 아날로그 값 (변화 있을 때만 다시 전송)
    private float lastLX = 0f; // left stick X
    private float lastLY = 0f; // left stick Y
    private float lastRX = 0f; // right stick X
    // 줌 버튼 이전 상태 (엣지 감지용)
    private boolean lastZoomInPressed = false;   // RB
    private boolean lastZoomOutPressed = false;  // LB

    @Override
    public void start(Stage stage) {
        root = new StackPane();

        introView = buildIntroView();
        mainView  = buildMainView();

        root.getChildren().add(introView);

        Scene scene = new Scene(root, 1200, 720);
        stage.setTitle("J-SafeGuard 관제 시스템 (JavaFX)");
        stage.setScene(scene);
        stage.show();

        // 키 입력 → 로봇 운전 명령 전송
        scene.setOnKeyPressed(e -> sendDriveCommand(e.getCode()));

        // 로봇 연결 시도 시작
        startRobotConnection();

        // 게임패드 초기화 & 폴링 시작
        initGamepad();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        // 게임패드 정리
        try {
            if (controllers != null) {
                controllers.quitSDLGamepad();
            }
        } catch (Throwable ignored) {
        }
        if (gamepadExecutor != null) {
            gamepadExecutor.shutdownNow();
        }
    }

    // ==========================
    // 1) 공통: 배경 이미지 cover 뷰 생성
    // ==========================
    private ImageView createCoverBackgroundView() {
        Image img = loadBackgroundImage();
        ImageView bgView = new ImageView(img);
        bgView.setSmooth(true);
        bgView.setPreserveRatio(false); // cover 느낌으로 "무조건 꽉 채움"
        bgView.fitWidthProperty().bind(root.widthProperty());
        bgView.fitHeightProperty().bind(root.heightProperty());
        return bgView;
    }

    private Image loadBackgroundImage() {
        // 1) 리소스 로딩 시도
        try (InputStream is = MainFx.class.getResourceAsStream(BG_RESOURCE_PATH)) {
            if (is != null) {
                return new Image(is);
            }
        } catch (Exception ignored) {
        }

        // 2) 폴백 (file:)
        return new Image(BG_FILE_FALLBACK, true);
    }

    // ==========================
    // 2) 인트로 화면
    // ==========================
    private StackPane buildIntroView() {
        StackPane introRoot = new StackPane();

        ImageView bgView = createCoverBackgroundView();

        introStatusLabel = new Label("로봇 연결 상태를 확인하는 중입니다...");
        introStatusLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: white;");

        introStatusCircle = new Circle(10, Color.DODGERBLUE);

        HBox statusBox = new HBox(10, introStatusLabel, introStatusCircle);
        statusBox.setAlignment(Pos.CENTER);

        Button skipButton = new Button("Skip");
        skipButton.setStyle(
                "-fx-background-color: rgba(0,0,0,0.6);" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 14px;" +
                        "-fx-background-radius: 20;");
        skipButton.setOnAction(e -> showMainView());

        introRoot.getChildren().addAll(bgView, statusBox, skipButton);

        StackPane.setAlignment(statusBox, Pos.BOTTOM_CENTER);
        StackPane.setMargin(statusBox, new Insets(0, 0, 24, 0));

        StackPane.setAlignment(skipButton, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(skipButton, new Insets(10));

        return introRoot;
    }

    private void setIntroStateConnecting() {
        introStatusLabel.setText("로봇 연결 상태를 확인하는 중입니다...");
        introStatusCircle.setFill(Color.DODGERBLUE);
    }

    private void setIntroStateConnected() {
        introStatusLabel.setText("로봇 연결 성공! 메인 화면으로 이동합니다.");
        introStatusCircle.setFill(Color.DODGERBLUE);
    }

    private void setIntroStateFailed() {
        introStatusLabel.setText("로봇 연결 실패! 연결을 확인해주세요.");
        introStatusCircle.setFill(Color.RED);
    }

    // ==========================
    // 3) 메인 화면 (배경 + 콘텐츠 BorderPane)
    // ==========================
    private StackPane buildMainView() {
        StackPane mainRoot = new StackPane();

        // 배경(확실히 깔리도록 ImageView로 cover)
        ImageView bgView = createCoverBackgroundView();

        BorderPane content = new BorderPane();
        content.setPadding(new Insets(10));

        // ---- 중앙: (PIR 패널) + (Camera 패널) + (DB 버튼) ----
        // PIR 패널(카메라 위 별도)
        lblPirPanel = new Label("인체 감지: -");
        lblPirPanel.setStyle(
                "-fx-font-size: 16px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-text-fill: white;" +
                        "-fx-padding: 8 12 8 12;" +
                        "-fx-background-color: rgba(0,0,0,0.55);" +
                        "-fx-background-radius: 10;");

        TitledPane pirPane = new TitledPane("PIR (인체 감지)", wrapCard(lblPirPanel));
        pirPane.setCollapsible(false);

        cameraView = new ImageView();
        cameraView.setPreserveRatio(true);
        cameraView.setSmooth(true);
        cameraView.setFitHeight(430);

        StackPane cameraWrapper = new StackPane(cameraView);
        cameraWrapper.setPadding(new Insets(10));
        cameraWrapper.setStyle("-fx-background-color: rgba(0,0,0,0.35); -fx-background-radius: 12;");

        TitledPane cameraPane = new TitledPane("Camera", cameraWrapper);
        cameraPane.setCollapsible(false);

        // === 여기서 DB 버튼 추가 ===
        Button dbButton = new Button("DB");
        dbButton.setPrefWidth(60);
        dbButton.setOnAction(e -> openDbWindow());

        HBox dbButtonBox = new HBox(dbButton);
        dbButtonBox.setAlignment(Pos.CENTER_RIGHT);
        dbButtonBox.setPadding(new Insets(4, 0, 0, 0));

        VBox centerBox = new VBox(10, pirPane, cameraPane, dbButtonBox);
        VBox.setVgrow(cameraPane, Priority.ALWAYS);
        content.setCenter(centerBox);

        // ---- 왼쪽: 연결 상태 / 온도 / 가스 / 화재 ----
        VBox leftBox = new VBox(10);
        leftBox.setPadding(new Insets(10));
        leftBox.setPrefWidth(280);
        leftBox.setStyle("-fx-background-color: rgba(255,255,255,0.78); -fx-background-radius: 12;");

        lblConnStatus = new Label("로봇 연결 상태: 대기중");
        lblConnStatus.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        connStatusCircle = new Circle(7, Color.GRAY);

        HBox connBox = new HBox(8, lblConnStatus, connStatusCircle);
        connBox.setAlignment(Pos.CENTER_LEFT);

        tempChart = new LineChartWithApi("온도 (°C)");
        TitledPane tempPane = new TitledPane("온도 그래프", tempChart.getChart());
        tempPane.setCollapsible(false);

        gasChart = new LineChartWithApi("가스 (ppm)");
        TitledPane gasPane = new TitledPane("가스 그래프", gasChart.getChart());
        gasPane.setCollapsible(false);

        lblFireStatus = new Label("화재 상태: 정상");
        lblFireStatus.setStyle("-fx-font-size: 16px;");
        TitledPane firePane = new TitledPane("화재 상태", wrapCard(lblFireStatus));
        firePane.setCollapsible(false);

        leftBox.getChildren().addAll(connBox, tempPane, gasPane, firePane);
        VBox.setVgrow(tempPane, Priority.ALWAYS);
        VBox.setVgrow(gasPane, Priority.ALWAYS);

        content.setLeft(leftBox);

        // ---- 오른쪽: LiDAR / STT / Dust(별도 패널) ----
        VBox rightBox = new VBox(10);
        rightBox.setPadding(new Insets(10));
        rightBox.setPrefWidth(380);
        rightBox.setStyle("-fx-background-color: rgba(255,255,255,0.78); -fx-background-radius: 12;");

        lidarView = new LidarView();
        TitledPane lidarPane = new TitledPane("LiDAR SLAM Map", lidarView);
        lidarPane.setCollapsible(false);

        sttTextArea = new TextArea();
        sttTextArea.setEditable(false);
        sttTextArea.setWrapText(true);
        sttTextArea.setPromptText("로봇의 음성 인식 텍스트가 여기 출력됩니다.");

        TitledPane sttPane = new TitledPane("로봇 음성 인식 결과", sttTextArea);
        sttPane.setCollapsible(false);

        dustChart = new LineChartWithApi("Dust (µg/m³)");
        dustChart.getChart().setMinHeight(180);
        dustChart.getChart().setPrefHeight(180);
        TitledPane dustPane = new TitledPane("Dust 센서 그래프", dustChart.getChart());
        dustPane.setCollapsible(false);

        rightBox.getChildren().addAll(lidarPane, sttPane, dustPane);
        VBox.setVgrow(lidarPane, Priority.ALWAYS);
        VBox.setVgrow(sttPane, Priority.ALWAYS);

        content.setRight(rightBox);

        // 메인 뷰 구성(배경 + 콘텐츠)
        mainRoot.getChildren().addAll(bgView, content);
        return mainRoot;
    }

    private Region wrapCard(Node node) {
        VBox box = new VBox(node);
        box.setPadding(new Insets(8));
        box.setStyle("-fx-background-color: rgba(0,0,0,0.10); -fx-background-radius: 10;");
        return box;
    }

    private void updateConnectionStatusLabel(boolean connected) {
        if (lblConnStatus == null || connStatusCircle == null)
            return;
        if (connected) {
            lblConnStatus.setText("로봇 연결 상태: 연결됨");
            connStatusCircle.setFill(Color.DODGERBLUE);
        } else {
            lblConnStatus.setText("로봇 연결 상태: 연결 실패");
            connStatusCircle.setFill(Color.RED);
        }
    }

    // ==========================
    // 4) 로봇 연결 시도 & 화면 전환
    // ==========================
    private void startRobotConnection() {
        setIntroStateConnecting();

        Thread t = new Thread(() -> {
            boolean success;
            try {
                socket = new Socket(SERVER_IP, SERVER_PORT);
                socket.setTcpNoDelay(true);

                out = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
                        true);
                in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

                out.println("ROLE:GUI");
                success = true;
            } catch (Exception e) {
                e.printStackTrace();
                success = false;
            }

            boolean finalSuccess = success;
            Platform.runLater(() -> {
                if (finalSuccess) {
                    setIntroStateConnected();
                    updateConnectionStatusLabel(true);

                    PauseTransition delay = new PauseTransition(Duration.seconds(2));
                    delay.setOnFinished(ev -> showMainView());
                    delay.play();
                } else {
                    setIntroStateFailed();
                    updateConnectionStatusLabel(false);
                }
            });

            if (!success)
                return;

            try {
                String line;
                while ((line = in.readLine()) != null) {
                    handleJsonLine(line);
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> updateConnectionStatusLabel(false));
            }
        });

        t.setDaemon(true);
        t.start();
    }

    private void showMainView() {
        root.getChildren().setAll(mainView);
    }

    // ==========================
    // 5) JSON 처리
    // ==========================
    private void handleJsonLine(String line) {
        try {
            JSONObject json = new JSONObject(line);
            String type = json.optString("type", "");

            if ("SENSOR".equalsIgnoreCase(type)) {

                // 새 표준: fire(boolean), co2(number), dust(object{pm25,pm10}), pir(optional)
                boolean fire = json.optBoolean("fire", false);

                double co2 = json.optDouble("co2", Double.NaN);

                JSONObject dustObj = json.optJSONObject("dust");
                double pm25;
                double pm10 = Double.NaN;
                if (dustObj != null) {
                    pm25 = dustObj.optDouble("pm25", Double.NaN);
                    pm10 = dustObj.optDouble("pm10", Double.NaN);
                } else {
                    pm25 = Double.NaN;
                }

                boolean hasPir = json.has("pir");
                boolean pir = json.optBoolean("pir", false);

                Platform.runLater(() -> {
                    // gasChart를 co2 그래프로 쓰기
                    if (!Double.isNaN(co2)) gasChart.addValue(co2);

                    updateFireStatus(fire);

                    // dustChart는 pm25만 그리기
                    if (!Double.isNaN(pm25) && dustChart != null) {
                        dustChart.addValue(pm25);
                    }

                    if (hasPir) updatePirPanel(pir);
                });

                return;
            } else if ("LIDAR".equalsIgnoreCase(type)) {

                double robotX = json.optDouble("robotX", 0.0);
                double robotY = json.optDouble("robotY", 0.0);
                double robotTheta = json.optDouble("robotTheta", 0.0);

                JSONArray arr = json.optJSONArray("points");
                if (arr == null)
                    return;

                List<LidarPoint> localPoints = new ArrayList<>(arr.length());
                for (int i = 0; i < arr.length(); i++) {
                    Object elem = arr.get(i);
                    double x, y;
                    try {
                        if (elem instanceof JSONArray) {
                            JSONArray p = (JSONArray) elem;
                            if (p.length() < 2)
                                continue;
                            x = p.getDouble(0);
                            y = p.getDouble(1);
                        } else if (elem instanceof JSONObject) {
                            JSONObject p = (JSONObject) elem;
                            x = p.getDouble("x");
                            y = p.getDouble("y");
                        } else {
                            continue;
                        }
                    } catch (Exception ex) {
                        continue;
                    }
                    localPoints.add(new LidarPoint(x, y));
                }

                Platform.runLater(() -> lidarView.addScan(localPoints, robotX, robotY, robotTheta));

            } else if ("IMAGE".equalsIgnoreCase(type)) {

                String base64 = json.optString("data", null);
                if (base64 == null || base64.isEmpty())
                    return;

                byte[] bytes = Base64.getDecoder().decode(base64);
                Platform.runLater(() -> updateCameraImage(bytes));

            } else if ("STT".equalsIgnoreCase(type)) {

                String text = json.optString("text", "");
                if (!text.isEmpty()) {
                    Platform.runLater(() -> sttTextArea.appendText(text + System.lineSeparator()));
                }
            } else if ("VISION".equalsIgnoreCase(type)) {

                JSONObject yolo = json.optJSONObject("yolo");
                boolean person = false;
                if (yolo != null) person = yolo.optBoolean("person", false);

                boolean finalPerson = person;
                Platform.runLater(() -> updatePirPanel(finalPerson));
            }
        } catch (Exception e) {
            System.out.println("데이터 형식 오류: " + line);
        }
    }

    private void updateFireStatus(boolean fire) {
        if (fire) {
            lblFireStatus.setText("화재 상태: 🚨 비상!");
            lblFireStatus.setTextFill(Color.RED);
        } else {
            lblFireStatus.setText("화재 상태: 정상");
            lblFireStatus.setTextFill(Color.BLACK);
        }
    }

    private void updatePirPanel(boolean pir) {
        if (lblPirPanel == null)
            return;

        lblPirPanel.setText("인체 감지: " + (pir ? "true" : "false"));
        if (pir) {
            lblPirPanel.setStyle(
                    "-fx-font-size: 16px;" +
                            "-fx-font-weight: bold;" +
                            "-fx-text-fill: white;" +
                            "-fx-padding: 8 12 8 12;" +
                            "-fx-background-color: rgba(220,20,60,0.75);" +
                            "-fx-background-radius: 10;");
        } else {
            lblPirPanel.setStyle(
                    "-fx-font-size: 16px;" +
                            "-fx-font-weight: bold;" +
                            "-fx-text-fill: white;" +
                            "-fx-padding: 8 12 8 12;" +
                            "-fx-background-color: rgba(0,0,0,0.55);" +
                            "-fx-background-radius: 10;");
        }
    }

    private void updateCameraImage(byte[] imageBytes) {
        Image img = new Image(new ByteArrayInputStream(imageBytes));
        if (!img.isError()) {
            cameraView.setImage(img);
        } else {
            System.out.println("카메라 이미지 디코딩 실패");
        }
    }

    // ==========================
    // 6) 키보드 → 로봇 운전 명령 전송
    // ==========================
    private void sendDriveCommand(KeyCode code) {
        if (out == null)
            return;

        String cmd;
        switch (code) {
            case W:
                cmd = "FORWARD";
                break;
            case S:
                cmd = "BACKWARD";
                break;
            case A:
                cmd = "LEFT";
                break;
            case D:
                cmd = "RIGHT";
                break;
            case SPACE:
                cmd = "STOP";
                break;
            default:
                return;
        }

        String json = String.format("{\"type\":\"KEY\",\"cmd\":\"%s\"}", cmd);
        out.println(json);
        System.out.println("보냄: " + json);
    }

    // ==========================
    // 7) BlackBox 새 창 열기 (DB 재생)
    // ==========================
    private void openDbWindow() {
        // 1) 세션 ID 입력
        TextInputDialog dialog = new TextInputDialog("5");
        dialog.setTitle("BlackBox DB 재생");
        dialog.setHeaderText("재생할 video_session id를 입력하세요");
        dialog.setContentText("session_id:");

        var result = dialog.showAndWait();
        if (result.isEmpty()) return;

        long sessionId;
        try {
            sessionId = Long.parseLong(result.get().trim());
            if (sessionId <= 0) throw new NumberFormatException();
        } catch (Exception e) {
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setTitle("입력 오류");
            a.setHeaderText(null);
            a.setContentText("session_id는 1 이상의 숫자여야 합니다.");
            a.showAndWait();
            return;
        }

        // 2) 이미 열려 있으면: 앞으로 + 세션만 다시 로드 시도
        if (blackBoxStage != null && blackBoxPanel != null) {
            if (!blackBoxStage.isShowing()) blackBoxStage.show();
            blackBoxStage.toFront();
            applyDbAndLoadSession(blackBoxPanel, sessionId);
            return;
        }

        // 3) 새 창 생성
        blackBoxPanel = new BlackBoxPanel();
        applyDbAndLoadSession(blackBoxPanel, sessionId);

        blackBoxStage = new Stage();
        blackBoxStage.setTitle("BlackBox (DB Replay)");
        blackBoxStage.setScene(new Scene(blackBoxPanel.getView(), 1000, 700));

        // 창 닫히면 참조 정리(다시 열 수 있게)
        blackBoxStage.setOnHidden(e -> {
            // BlackBoxPanel에 dispose()가 있으면 호출(스케줄러 정리)
            try {
                var m = blackBoxPanel.getClass().getMethod("dispose");
                m.invoke(blackBoxPanel);
            } catch (Exception ignored) {}

            blackBoxPanel = null;
            blackBoxStage = null;
        });

        blackBoxStage.show();
        blackBoxStage.toFront();
    }

    /**
     * BlackBoxPanel에 DB 설정/세션 로드를 주입한다.
     * - 메서드가 없으면(아직 구현 전) 조용히 스킵
     */
    private void applyDbAndLoadSession(BlackBoxPanel panel, long sessionId) {
        // setDbConfig(String url, String user, String pass)
        try {
            var m = panel.getClass().getMethod("setDbConfig", String.class, String.class, String.class);
            m.invoke(panel, DB_URL, DB_USER, DB_PASS);
        } catch (Exception ignored) {
            System.out.println("[BlackBox] setDbConfig() 없음 또는 호출 실패(스킵)");
        }

        // loadSessionFromDb(long sessionId)
        try {
            var m = panel.getClass().getMethod("loadSessionFromDb", long.class);
            m.invoke(panel, sessionId);
        } catch (Exception ignored) {
            System.out.println("[BlackBox] loadSessionFromDb() 없음 또는 호출 실패(스킵)");
        }
    }

    // ==========================
    // 8) 게임패드 기능
    // ==========================

    // 게임패드 초기화
    private void initGamepad() {
        try {
            controllers = new ControllerManager();
            controllers.initSDLGamepad();
            System.out.println("Jamepad 초기화 완료.");

            // 50ms마다 게임패드 상태 폴링 (20Hz)
            gamepadExecutor = Executors.newSingleThreadScheduledExecutor();
            gamepadExecutor.scheduleAtFixedRate(this::pollGamepad, 0, 50, TimeUnit.MILLISECONDS);

        } catch (Throwable t) {
            t.printStackTrace();
            Platform.runLater(() -> {
                if (lblConnStatus != null) {
                    lblConnStatus.setText("로봇 연결 상태: 서버 연결됨 (패드 초기화 실패)");
                }
            });
        }
    }

    /**
     * - 왼쪽 스틱 X/Y, 오른쪽 스틱 X → PAD JSON 전송
     * - LB / RB → LiDAR 줌 in/out (엣지 감지)
     */
    private void pollGamepad() {
        if (controllers == null) {
            return;
        }

        ControllerState state = controllers.getState(0);

        if (!state.isConnected) {
            Platform.runLater(() -> {
                if (lblConnStatus != null && connStatusCircle != null) {
                    lblConnStatus.setText("로봇 연결 상태: 연결됨 (패드 없음)");
                }
            });
            return;
        }

        // 서버 소켓이 아직 없으면, 연결될 때까지 전송은 보류
        if (out == null) {
            return;
        }

        // 왼쪽 스틱 X/Y, 오른쪽 스틱 X
        float lx = state.leftStickX;
        float ly = state.leftStickY;
        float rx = state.rightStickX;

        // 줌 버튼 (LB/RB)
        boolean zoomOutPressed = state.lb;  // LB → zoom out
        boolean zoomInPressed  = state.rb;  // RB → zoom in

        // 데드존 적용
        lx = deadZone(lx, 0.05f);
        ly = deadZone(ly, 0.05f);
        rx = deadZone(rx, 0.05f);

        // 값 변화가 거의 없으면 전송하지 않음
        float epsilon = 0.01f;
        if (Math.abs(lx - lastLX) >= epsilon ||
            Math.abs(ly - lastLY) >= epsilon ||
            Math.abs(rx - lastRX) >= epsilon) {

            lastLX = lx;
            lastLY = ly;
            lastRX = rx;

            sendAnalogState(lx, ly, rx);
        }

        // LiDAR 맵 줌 인/아웃
        if (lidarView != null) {
            if (zoomInPressed && !lastZoomInPressed) {
                Platform.runLater(() -> lidarView.adjustZoom(1.2));   // 줌 인
            }
            if (zoomOutPressed && !lastZoomOutPressed) {
                Platform.runLater(() -> lidarView.adjustZoom(0.8));   // 줌 아웃
            }
        }

        lastZoomInPressed = zoomInPressed;
        lastZoomOutPressed = zoomOutPressed;
    }

    // 헬퍼: 데드존 처리
    private float deadZone(float value, float threshold) {
        return Math.abs(value) < threshold ? 0.0f : value;
    }

    /** 아날로그 스틱 값을 JSON으로 서버에 전송 */
    private void sendAnalogState(float lx, float ly, float rx) {
        if (out == null) return;

        String json = String.format(Locale.US,
                "{\"type\":\"PAD\",\"lx\":%.3f,\"ly\":%.3f,\"rx\":%.3f}",
                lx, ly, rx);

        out.println(json);
        System.out.println("패드 아날로그 전송: " + json);
    }

    public static void main(String[] args) {
        launch(args);
    }
}

// ============================================
// LiDAR 관련 보조 클래스들
// ============================================

class LidarPoint {
    final double x;
    final double y;

    LidarPoint(double x, double y) {
        this.x = x;
        this.y = y;
    }
}

class LidarView extends Canvas {

    private final Object lock = new Object();

    private final List<LidarPoint> globalPoints = new ArrayList<>();
    private List<LidarPoint> lastScanGlobal = new ArrayList<>();

    private double robotX = 0.0;
    private double robotY = 0.0;
    private double robotTheta = 0.0;

    double zoomFactor = 1.0;
    private static final int MAX_POINTS = 20000;

    public LidarView() {
        setWidth(360);
        setHeight(280);

        widthProperty().addListener((obs, ov, nv) -> draw());
        heightProperty().addListener((obs, ov, nv) -> draw());
    }

    public void addScan(List<LidarPoint> localPoints,
                        double robotX,
                        double robotY,
                        double robotTheta) {
        synchronized (lock) {
            this.robotX = robotX;
            this.robotY = robotY;
            this.robotTheta = robotTheta;

            double cos = Math.cos(robotTheta);
            double sin = Math.sin(robotTheta);

            List<LidarPoint> newGlobal = new ArrayList<>(localPoints.size());
            for (LidarPoint lp : localPoints) {
                double gx = robotX + (lp.x * cos - lp.y * sin);
                double gy = robotY + (lp.x * sin + lp.y * cos);
                LidarPoint gp = new LidarPoint(gx, gy);
                globalPoints.add(gp);
                newGlobal.add(gp);
            }

            if (globalPoints.size() > MAX_POINTS) {
                int removeCount = globalPoints.size() - MAX_POINTS;
                globalPoints.subList(0, removeCount).clear();
            }

            lastScanGlobal = newGlobal;
        }
        draw();
    }

    // === 패드 LB/RB 에서 호출하는 줌 기능 ===
    public void adjustZoom(double multiplier) {
        synchronized (lock) {
            zoomFactor *= multiplier;
            if (zoomFactor < 0.2) zoomFactor = 0.2;
            if (zoomFactor > 10.0) zoomFactor = 10.0;
        }
        draw();
    }

    private void draw() {
        GraphicsContext g2 = getGraphicsContext2D();
        double w = getWidth();
        double h = getHeight();

        g2.setFill(Color.BLACK);
        g2.fillRect(0, 0, w, h);

        List<LidarPoint> globalSnapshot;
        List<LidarPoint> lastScanSnapshot;
        double rX, rY, rTheta, zf;

        synchronized (lock) {
            globalSnapshot = new ArrayList<>(globalPoints);
            lastScanSnapshot = new ArrayList<>(lastScanGlobal);
            rX = robotX;
            rY = robotY;
            rTheta = robotTheta;
            zf = zoomFactor;
        }

        if (globalSnapshot.isEmpty()) {
            g2.setFill(Color.GRAY);
            g2.fillText("LiDAR 데이터 대기중...", 10, 20);
            return;
        }

        double padding = 20;
        double centerX = w / 2.0;
        double centerY = h / 2.0;

        double minDX = 0, maxDX = 0, minDY = 0, maxDY = 0;
        boolean first = true;
        for (LidarPoint p : globalSnapshot) {
            double dx = p.x - rX;
            double dy = p.y - rY;
            if (first) {
                minDX = maxDX = dx;
                minDY = maxDY = dy;
                first = false;
            } else {
                if (dx < minDX)
                    minDX = dx;
                if (dx > maxDX)
                    maxDX = dx;
                if (dy < minDY)
                    minDY = dy;
                if (dy > maxDY)
                    maxDY = dy;
            }
        }

        double margin = 0.1;
        double worldW = (maxDX - minDX);
        double worldH = (maxDY - minDY);
        if (worldW == 0)
            worldW = 1;
        if (worldH == 0)
            worldH = 1;
        worldW *= (1.0 + margin);
        worldH *= (1.0 + margin);

        double scaleX = (w - 2.0 * padding) / worldW;
        double scaleY = (h - 2.0 * padding) / worldH;
        double scale = Math.min(scaleX, scaleY);
        scale *= zf;

        int pointSize = 2;

        g2.setFill(Color.rgb(0, 160, 0));
        for (LidarPoint p : globalSnapshot) {
            double dx = p.x - rX;
            double dy = p.y - rY;
            double sx = centerX + dx * scale;
            double sy = centerY - dy * scale;
            g2.fillOval(sx - pointSize / 2.0, sy - pointSize / 2.0, pointSize, pointSize);
        }

        g2.setFill(Color.LIME);
        for (LidarPoint p : lastScanSnapshot) {
            double dx = p.x - rX;
            double dy = p.y - rY;
            double sx = centerX + dx * scale;
            double sy = centerY - dy * scale;
            g2.fillOval(sx - pointSize, sy - pointSize, pointSize * 2, pointSize * 2);
        }

        double robotSX = centerX;
        double robotSY = centerY;

        int rPix = 8;
        g2.setFill(Color.RED);
        g2.fillOval(robotSX - rPix, robotSY - rPix, rPix * 2, rPix * 2);

        double arrowLen = 25;
        double hx = robotSX + Math.cos(rTheta) * arrowLen;
        double hy = robotSY - Math.sin(rTheta) * arrowLen;

        g2.setStroke(Color.YELLOW);
        g2.setLineWidth(2);
        g2.strokeLine(robotSX, robotSY, hx, hy);

        g2.setStroke(Color.DARKGRAY);
        g2.strokeRect(padding, padding, w - 2 * padding, h - 2 * padding);

        g2.setFill(Color.WHITE);
        g2.fillText(String.format(Locale.US, "Zoom: x%.2f", zf), padding + 5, h - padding - 5);
    }
}

// ============================================
// 그래프 유틸
// ============================================

class LineChartWithApi {

    private final LineChart<Number, Number> chart;
    private final XYChart.Series<Number, Number> series;
    private int xIndex = 0;

    LineChartWithApi(String yLabel) {
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("시간");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel(yLabel);

        chart = new LineChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setCreateSymbols(false);

        series = new XYChart.Series<>();
        chart.getData().add(series);
    }

    LineChart<Number, Number> getChart() {
        return chart;
    }

    void addValue(double value) {
        series.getData().add(new XYChart.Data<>(xIndex++, value));
        if (series.getData().size() > 300) {
            series.getData().remove(0);
        }
    }
}
