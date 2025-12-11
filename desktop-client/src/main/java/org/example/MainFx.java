package org.example;

import com.studiohartman.jamepad.ControllerManager;
import com.studiohartman.jamepad.ControllerState;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
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

/**
 * JavaFX 기반 관제 UI
 * - 인트로 화면: 배경 이미지 + 하단 연결 상태 문구 + 파랑/빨강 상태 아이콘 + Skip 버튼
 * - 연결 성공 시 2초 뒤 메인으로 자동 전환 (사용자는 Skip으로 바로 진입 가능)
 * - 메인 화면:
 *    중앙: 카메라 영상
 *    왼쪽: 로봇 연결 상태 / 온도 그래프 / 가스 그래프 / 화재 상태
 *    오른쪽 위: LiDAR 맵
 *    오른쪽 아래: STT 텍스트
 * - 게임패드:
 *    왼/오른쪽 스틱 아날로그 값 PAD JSON 전송, LB/RB로 LiDAR 줌 제어
 */
public class MainFx extends Application {

    // --- 서버 연결 정보 ---
    private static final String SERVER_IP = "192.168.0.27"; // 필요 시 수정
    private static final int SERVER_PORT = 6001;

    // --- 네트워크 관련 ---
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    // --- 공용 배경 이미지 ---
    private Image appBackgroundImage;

    // --- 루트 / 화면 전환 관련 ---
    private StackPane root;
    private StackPane introView;
    private BorderPane mainView;

    // 인트로 상태 표시
    private Label introStatusLabel;
    private Circle introStatusCircle;

    // 메인 화면 요소
    private Label lblConnStatus;
    private Circle connStatusCircle;

    private LineChartWithApi tempChart;
    private LineChartWithApi gasChart;
    private Label lblFireStatus;

    private ImageView cameraView;
    private LidarView lidarView;
    private TextArea sttTextArea;

    // --- 게임패드 관련 (Main.java에서 이식) ---
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
        mainView = buildMainView();

        // 처음에는 인트로 화면만 보여줌
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

    /**
     * 인트로/메인에서 공통으로 사용하는 배경 설정
     * startup_background.png 를 화면 크기에 맞게 cover 로 채운다.
     */
    private Background createAppBackground() {
        if (appBackgroundImage == null) {
            // desktop-client 루트(또는 프로젝트 루트)에 있는 이미지 사용
            appBackgroundImage = new Image("file:startup_background.png", true);
        }

        BackgroundSize bgSize = new BackgroundSize(
                100, 100,   // width / height = 100%
                true, true, // percent 단위
                false,      // contain
                true        // cover (잘리더라도 전체 채우기)
        );

        BackgroundImage bgImage = new BackgroundImage(
                appBackgroundImage,
                BackgroundRepeat.NO_REPEAT,
                BackgroundRepeat.NO_REPEAT,
                BackgroundPosition.CENTER,
                bgSize
        );

        return new Background(bgImage);
    }

    // ==========================
    // 1. 인트로 화면 구성
    // ==========================
    private StackPane buildIntroView() {
        StackPane introRoot = new StackPane();

        // 1) 배경 이미지를 StackPane 배경으로 설정 (cover, 레터박스 제거)
        introRoot.setBackground(createAppBackground());

        // 2) 하단 상태 문구 (배경 박스 없이)
        introStatusLabel = new Label("로봇 연결 상태를 확인하는 중입니다...");
        introStatusLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: white;");

        introStatusCircle = new Circle(8, Color.DODGERBLUE); // 파랑: 연결 시도 중

        HBox statusBox = new HBox(8, introStatusCircle, introStatusLabel);
        statusBox.setAlignment(Pos.CENTER);
        statusBox.setPadding(new Insets(15));

        introRoot.getChildren().add(statusBox);
        StackPane.setAlignment(statusBox, Pos.BOTTOM_CENTER);
        StackPane.setMargin(statusBox, new Insets(0, 0, 25, 0)); // 아래 여백 약간

        // 3) Skip 버튼 (우측 하단)
        Button skipButton = new Button("Skip");
        skipButton.setStyle(
                "-fx-background-color: rgba(0,0,0,0.6);" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 14px;" +
                "-fx-background-radius: 20;"
        );
        skipButton.setOnAction(e -> showMainView());

        introRoot.getChildren().add(skipButton);
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
        introStatusCircle.setFill(Color.DODGERBLUE); // 필요하면 Color.LIME 등으로 변경
    }

    private void setIntroStateFailed() {
        introStatusLabel.setText("로봇 연결 실패! 연결을 확인해주세요.");
        introStatusCircle.setFill(Color.RED);
    }

    // ==========================
    // 2. 메인 화면 구성
    // ==========================
    private BorderPane buildMainView() {
        BorderPane border = new BorderPane();

        // 메인 화면 배경도 인트로와 동일한 이미지 사용
        border.setBackground(createAppBackground());

        // ---- 중앙: 카메라 영상 ----
        cameraView = new ImageView();
        cameraView.setPreserveRatio(true);
        cameraView.setSmooth(true);
        cameraView.setFitHeight(450); // 중앙 크게

        StackPane cameraWrapper = new StackPane(cameraView);
        cameraWrapper.setPadding(new Insets(10));
        TitledPane cameraPane = new TitledPane("Camera", cameraWrapper);
        cameraPane.setCollapsible(false);
        border.setCenter(cameraPane);

        // ---- 왼쪽: 연결 상태 / 온도 그래프 / 가스 그래프 / 화재 상태 ----
        VBox leftBox = new VBox(10);
        leftBox.setPadding(new Insets(10));
        leftBox.setPrefWidth(280);

        // (1) 로봇 연결 상태
        lblConnStatus = new Label("로봇 연결 상태: 대기중");
        lblConnStatus.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        connStatusCircle = new Circle(7, Color.GRAY);

        HBox connBox = new HBox(8, lblConnStatus, connStatusCircle);
        connBox.setAlignment(Pos.CENTER_LEFT);

        // (2) 온도 그래프
        tempChart = new LineChartWithApi("온도 (°C)");
        TitledPane tempPane = new TitledPane("온도 그래프", tempChart.getChart());
        tempPane.setCollapsible(false);

        // (3) 가스 그래프
        gasChart = new LineChartWithApi("가스 (ppm)");
        TitledPane gasPane = new TitledPane("가스 그래프", gasChart.getChart());
        gasPane.setCollapsible(false);

        // (4) 화재 상태
        lblFireStatus = new Label("화재 상태: 정상");
        lblFireStatus.setStyle("-fx-font-size: 16px;");
        TitledPane firePane = new TitledPane("화재 상태", lblFireStatus);
        firePane.setCollapsible(false);

        leftBox.getChildren().addAll(connBox, tempPane, gasPane, firePane);
        VBox.setVgrow(tempPane, Priority.ALWAYS);
        VBox.setVgrow(gasPane, Priority.ALWAYS);

        border.setLeft(leftBox);

        // ---- 오른쪽: LiDAR + STT 텍스트 ----
        VBox rightBox = new VBox(10);
        rightBox.setPadding(new Insets(10));
        rightBox.setPrefWidth(320);

        lidarView = new LidarView();
        TitledPane lidarPane = new TitledPane("LiDAR SLAM Map", lidarView);
        lidarPane.setCollapsible(false);

        sttTextArea = new TextArea();
        sttTextArea.setEditable(false);
        sttTextArea.setWrapText(true);
        sttTextArea.setPromptText("로봇의 음성 인식 텍스트가 여기 출력됩니다.");
        TitledPane sttPane = new TitledPane("로봇 음성 인식 결과", sttTextArea);
        sttPane.setCollapsible(false);

        rightBox.getChildren().addAll(lidarPane, sttPane);
        VBox.setVgrow(lidarPane, Priority.ALWAYS);
        VBox.setVgrow(sttPane, Priority.ALWAYS);

        border.setRight(rightBox);

        return border;
    }

    private void updateConnectionStatusLabel(boolean connected) {
        if (lblConnStatus == null || connStatusCircle == null) return;
        if (connected) {
            lblConnStatus.setText("로봇 연결 상태: 연결됨");
            connStatusCircle.setFill(Color.DODGERBLUE);
        } else {
            lblConnStatus.setText("로봇 연결 상태: 연결 실패");
            connStatusCircle.setFill(Color.RED);
        }
    }

    // ==========================
    // 3. 로봇 연결 시도 & 화면 전환
    // ==========================
    private void startRobotConnection() {
        setIntroStateConnecting();

        Thread t = new Thread(() -> {
            boolean success = false;
            try {
                socket = new Socket(SERVER_IP, SERVER_PORT);
                socket.setTcpNoDelay(true);

                out = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
                        true
                );
                in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
                );

                // 역할 전송 (기존과 동일)
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

                    // 2초 뒤 메인 화면으로 전환
                    PauseTransition delay = new PauseTransition(Duration.seconds(2));
                    delay.setOnFinished(ev -> showMainView());
                    delay.play();
                } else {
                    setIntroStateFailed();
                    updateConnectionStatusLabel(false);
                }
            });

            if (!success) {
                return;
            }

            // 소켓 읽기 루프 (JSON 처리)
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
    // 4. JSON 데이터 처리
    // ==========================
    private void handleJsonLine(String line) {
        try {
            JSONObject json = new JSONObject(line);
            String type = json.optString("type", "");

            if ("SENSOR".equalsIgnoreCase(type)) {

                double temp = json.getDouble("temp");
                double gas = json.getDouble("gas");
                boolean fire = json.getBoolean("fire");

                Platform.runLater(() -> {
                    addTemperatureSample(temp);
                    addGasSample(gas);
                    updateFireStatus(fire);
                });

            } else if ("LIDAR".equalsIgnoreCase(type)) {

                double robotX = json.optDouble("robotX", 0.0);
                double robotY = json.optDouble("robotY", 0.0);
                double robotTheta = json.optDouble("robotTheta", 0.0);

                JSONArray arr = json.optJSONArray("points");
                if (arr == null) return;

                List<LidarPoint> localPoints = new ArrayList<>(arr.length());
                for (int i = 0; i < arr.length(); i++) {
                    Object elem = arr.get(i);
                    double x, y;
                    try {
                        if (elem instanceof JSONArray) {
                            JSONArray p = (JSONArray) elem;
                            if (p.length() < 2) continue;
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

                Platform.runLater(() ->
                        addLidarScan(localPoints, robotX, robotY, robotTheta)
                );

            } else if ("IMAGE".equalsIgnoreCase(type)) {

                String base64 = json.optString("data", null);
                if (base64 == null || base64.isEmpty()) return;

                byte[] bytes = Base64.getDecoder().decode(base64);
                Platform.runLater(() -> updateCameraImage(bytes));

            } else if ("STT".equalsIgnoreCase(type)) {
                // 예: {"type":"STT","text":"앞으로 이동합니다"}
                String text = json.optString("text", "");
                if (!text.isEmpty()) {
                    Platform.runLater(() -> appendSttText(text));
                }
            }

        } catch (Exception e) {
            System.out.println("데이터 형식 오류: " + line);
        }
    }

    // ==========================
    // 5. 센서 / 카메라 / LiDAR / STT 업데이트 메서드
    // ==========================

    private void addTemperatureSample(double value) {
        tempChart.addValue(value);
    }

    private void addGasSample(double value) {
        gasChart.addValue(value);
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

    private void updateCameraImage(byte[] imageBytes) {
        Image img = new Image(new ByteArrayInputStream(imageBytes));
        if (!img.isError()) {
            cameraView.setImage(img);
        } else {
            System.out.println("카메라 이미지 디코딩 실패");
        }
    }

    private void addLidarScan(List<LidarPoint> localPoints,
                              double robotX,
                              double robotY,
                              double robotTheta) {
        lidarView.addScan(localPoints, robotX, robotY, robotTheta);
    }

    private void appendSttText(String text) {
        sttTextArea.appendText(text + System.lineSeparator());
    }

    // ==========================
    // 6. 키보드 → 로봇 운전 명령 전송
    // ==========================
    private void sendDriveCommand(KeyCode code) {
        if (out == null) return;

        String cmd = "";
        switch (code) {
            case W: cmd = "FORWARD";  break;
            case S: cmd = "BACKWARD"; break;
            case A: cmd = "LEFT";     break;
            case D: cmd = "RIGHT";    break;
            case SPACE: cmd = "STOP"; break;
            // MOCK 시나리오: Main.java에서 쓰던 F5/F6는
            // 아래처럼 참고용으로만 남기고, 실제 전송은 주석 처리
            /*
            case F5:
                System.out.println("[MOCK] F5: ForwardStop 시나리오 시작");
                playMockPadScenario(MockPadData.scenarioForwardStop(), 80);
                return;
            case F6:
                System.out.println("[MOCK] F6: FromLogLike 시나리오 시작");
                playMockPadScenario(MockPadData.scenarioFromLogLike(), 80);
                return;
            */
            default:
                return;
        }

        String json = String.format("{\"type\":\"KEY\",\"cmd\":\"%s\"}", cmd);
        out.println(json);
        System.out.println("보냄: " + json);
    }

    // ==========================
    // 7. 게임패드 기능 (Main.java에서 이식)
    // ==========================

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
     * Main.java의 pollGamepad 로직 이식
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
                    // 패드 없다고 해서 로봇 연결 상태 아이콘 색은 그대로 두거나,
                    // 필요하면 별도 색으로 변경 가능
                }
            });
            return;
        }

        // 서버 소켓이 아직 없으면, 연결될 때까지 전송은 보류
        if (out == null) {
            return;
        }

        // 왼쪽 스틱 X/Y, 오른쪽 스틱 X 사용
        float lx = state.leftStickX;
        float ly = state.leftStickY;
        float rx = state.rightStickX;

        // 줌 버튼 상태 읽기 (LB/RB)
        boolean zoomOutPressed = state.lb;   // LB → zoom out
        boolean zoomInPressed  = state.rb;   // RB → zoom in

        // 데드존 적용 (작은 흔들림 제거)
        lx = deadZone(lx, 0.05f);
        ly = deadZone(ly, 0.05f);
        rx = deadZone(rx, 0.05f);

        // 값 변화가 거의 없으면 전송하지 않음 (트래픽 절약)
        float epsilon = 0.01f;
        if (Math.abs(lx - lastLX) >= epsilon ||
            Math.abs(ly - lastLY) >= epsilon ||
            Math.abs(rx - lastRX) >= epsilon) {

            lastLX = lx;
            lastLY = ly;
            lastRX = rx;

            sendAnalogState(lx, ly, rx);
        }

        // LiDAR 맵 줌 인/아웃 (엣지 감지: 눌린 순간만 반응)
        if (lidarView != null) {
            if (zoomInPressed && !lastZoomInPressed) {
                // 20% 확대
                Platform.runLater(() -> lidarView.adjustZoom(1.2));
            }
            if (zoomOutPressed && !lastZoomOutPressed) {
                // 20% 축소
                Platform.runLater(() -> lidarView.adjustZoom(0.8));
            }
        }

        // 다음 호출을 위해 현재 버튼 상태 저장
        lastZoomInPressed = zoomInPressed;
        lastZoomOutPressed = zoomOutPressed;
    }

    // 헬퍼: 데드존 처리
    private float deadZone(float value, float threshold) {
        return Math.abs(value) < threshold ? 0.0f : value;
    }

    /**
     * 아날로그 스틱 값을 JSON으로 서버에 전송 (Main.java에서 이식)
     * {"type":"PAD","lx":..,"ly":..,"rx":..}
     */
    private void sendAnalogState(float lx, float ly, float rx) {
        if (out == null) return;

        String json = String.format(Locale.US,
                "{\"type\":\"PAD\",\"lx\":%.3f,\"ly\":%.3f,\"rx\":%.3f}",
                lx, ly, rx);

        out.println(json);
        System.out.println("패드 아날로그 전송: " + json);
    }

    /**
     * MOCK 패드 시나리오 재생 (Main.java에서 이식)
     * - 현재는 실제 전송 부분은 전부 주석 처리됨.
     * - 필요 시 MockPadData 와 함께 주석을 풀어 사용할 수 있음.
     */
    private void playMockPadScenario(List<String> lines, int delayMs) {
        // if (out == null) {
        //     System.out.println("[MOCK] 서버에 아직 연결 안 됨. 시나리오 전송 불가");
        //     return;
        // }
        //
        // new Thread(() -> {
        //     try {
        //         for (String json : lines) {
        //             out.println(json);   // 서버로 전송
        //             System.out.println("[MOCK PAD 전송] " + json);
        //             Thread.sleep(delayMs);
        //         }
        //         System.out.println("[MOCK] 시나리오 재생 완료");
        //     } catch (InterruptedException e) {
        //         System.out.println("[MOCK] 시나리오 중단");
        //     }
        // }).start();
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

/**
 * JavaFX Canvas 기반 LiDAR 맵 뷰
 */
class LidarView extends Canvas {

    private final Object lock = new Object();

    private final List<LidarPoint> globalPoints = new ArrayList<>();
    private List<LidarPoint> lastScanGlobal = new ArrayList<>();

    private double robotX = 0.0;
    private double robotY = 0.0;
    private double robotTheta = 0.0;

    private double zoomFactor = 1.0;
    private static final int MAX_POINTS = 20000;

    public LidarView() {
        setWidth(320);
        setHeight(260);

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
                if (dx < minDX) minDX = dx;
                if (dx > maxDX) maxDX = dx;
                if (dy < minDY) minDY = dy;
                if (dy > maxDY) maxDY = dy;
            }
        }

        double margin = 0.1;
        double worldW = (maxDX - minDX);
        double worldH = (maxDY - minDY);
        if (worldW == 0) worldW = 1;
        if (worldH == 0) worldH = 1;
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
// 온도/가스 그래프용 유틸
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