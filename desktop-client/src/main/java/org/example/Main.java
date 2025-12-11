package org.example;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.json.JSONArray;
import org.json.JSONObject;

import com.studiohartman.jamepad.ControllerManager;
import com.studiohartman.jamepad.ControllerState;

public class Main extends JFrame {

    // --- 통신 관련 변수 ---
    private Socket socket;
    private PrintWriter out;
    private final String SERVER_IP = "192.168.0.22"; // 서버 주소
    private final int SERVER_PORT = 6001;

    // --- 화면 구성 요소 (라벨/패널) ---
    private JLabel lblStatus, lblTemp, lblGas, lblFire;
    private LidarPanel lidarPanel; // LiDAR 맵 패널 (로봇 중심 + 최신 스캔만)

    // --- 게임패드 관련 ---
    private ControllerManager controllers;
    private Timer gamepadTimer;
    // 마지막으로 전송한 아날로그 값 (변화 있을 때만 다시 전송)
    private float lastLX = 0f; // left stick X
    private float lastLY = 0f; // left stick Y
    private float lastRX = 0f; // right stick X

    // 줌 버튼 이전 상태 (엣지 감지용)
    private boolean lastZoomInPressed = false;   // RB
    private boolean lastZoomOutPressed = false;  // LB

    public Main() {
        // 1. 기본 창 설정
        setTitle("J-SafeGuard 관제 시스템");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 2. 상단: 상태 표시줄
        lblStatus = new JLabel("상태: 서버 연결 대기중...");
        lblStatus.setHorizontalAlignment(SwingConstants.CENTER);
        lblStatus.setFont(new Font("맑은 고딕", Font.BOLD, 16));
        lblStatus.setOpaque(true);
        lblStatus.setBackground(Color.LIGHT_GRAY);
        add(lblStatus, BorderLayout.NORTH);

        // 3. 중앙: 센서 데이터 + LiDAR 맵 (그리드 레이아웃)
        JPanel panelCenter = new JPanel(new GridLayout(2, 2, 10, 10)); // 2행 2열

        lblTemp = createSensorLabel("온도", "0.0 °C");
        lblGas = createSensorLabel("가스", "0.0 ppm");
        lblFire = createSensorLabel("화재 감지", "정상");

        panelCenter.add(lblTemp);
        panelCenter.add(lblGas);
        panelCenter.add(lblFire);

        // LiDAR 맵 패널 (로봇 중심 + 최신 스캔만)
        lidarPanel = new LidarPanel();
        panelCenter.add(lidarPanel);

        add(panelCenter, BorderLayout.CENTER);

        // 4. 키보드 리스너 (조종)
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                sendDriveCommand(e.getKeyCode());
            }
        });

        // 5. 서버 연결 시작
        connectToServer();

        // 6. 게임패드 초기화 & 폴링 시작
        initGamepad();

        setVisible(true);
    }

    // 예쁜 라벨 만드는 함수
    private JLabel createSensorLabel(String title, String initValue) {
        JLabel label = new JLabel("<html><center>" + title + "<br><h1>" + initValue + "</h1></center></html>");
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        return label;
    }

    // --- [기능 1] 서버 연결 및 데이터 수신 ---
    private void connectToServer() {
        new Thread(() -> {
            try {
                socket = new Socket(SERVER_IP, SERVER_PORT);
                socket.setTcpNoDelay(true);
                out = new PrintWriter(socket.getOutputStream(), true);
                out.println("ROLE:GUI");

                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("상태: 서버 연결됨 (조종 가능)");
                    lblStatus.setBackground(Color.GREEN);
                });

                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    try {
                        JSONObject json = new JSONObject(inputLine);
                        String type = json.optString("type", "");

                        if ("SENSOR".equalsIgnoreCase(type)) {
                            double temp = json.getDouble("temp");
                            double gas = json.getDouble("gas");
                            boolean fire = json.getBoolean("fire");

                            SwingUtilities.invokeLater(() -> {
                                updateDashboard(temp, gas, fire);
                            });

                        } else if ("LIDAR".equalsIgnoreCase(type)) {
                            // LiDAR 데이터 처리 (최신 스캔만, 로봇 중심 뷰)
                            handleLidarJson(json);
                        }

                    } catch (Exception e) {
                        System.out.println("데이터 형식 오류: " + inputLine);
                    }
                }

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("상태: 연결 실패 (서버 꺼짐)");
                    lblStatus.setBackground(Color.RED);
                });
            }
        }).start();
    }

    // --- MOCK: PAD 목데이터 시나리오 재생 (기존 코드 유지) ---
    private void playMockPadScenario(List<String> lines, int delayMs) {
        if (out == null) {
            System.out.println("[MOCK] 서버에 아직 연결 안 됨. 시나리오 전송 불가");
            return;
        }

        new Thread(() -> {
            try {
                for (String json : lines) {
                    out.println(json);
                    System.out.println("[MOCK PAD 전송] " + json);
                    Thread.sleep(delayMs);
                }
                System.out.println("[MOCK] 시나리오 재생 완료");
            } catch (InterruptedException e) {
                System.out.println("[MOCK] 시나리오 중단");
            }
        }).start();
    }

    // --- LIDAR JSON 처리 ---
    // 단순 버전 기대 형식:
    // {
    //   "type":"LIDAR",
    //   "points":[ [x_local,y_local], ... ]  또는  [{ "x":..,"y":.. }, ...]
    // }
    // robotX/Y/theta가 와도 무시 (서버에서 보내면 자바가 그냥 무시)
    private void handleLidarJson(JSONObject json) {
        if (lidarPanel == null) return;

        JSONArray arr = json.optJSONArray("points");
        if (arr == null) return;

        List<Point2D.Double> localPoints = new ArrayList<>(arr.length());
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

            // 여기의 x,y 는 "로봇 기준(local 좌표)"라고 가정
            localPoints.add(new Point2D.Double(x, y));
        }

        // 최신 스캔만 표시 (기존 맵 누적 없음)
        SwingUtilities.invokeLater(() ->
                lidarPanel.setLatestScan(localPoints)
        );
    }

    // --- [기능 2] 대시보드 갱신 ---
    private void updateDashboard(double temp, double gas, boolean fire) {
        lblTemp.setText("<html><center>온도<br><h1>" + temp + " °C</h1></center></html>");
        lblGas.setText("<html><center>가스<br><h1>" + gas + " ppm</h1></center></html>");

        if (fire) {
            lblFire.setText("<html><center>화재 감지<br><h1>🚨 비상!</h1></center></html>");
            lblFire.setOpaque(true);
            lblFire.setBackground(Color.RED);
            lblFire.setForeground(Color.WHITE);
        } else {
            lblFire.setText("<html><center>화재 감지<br><h1>정상</h1></center></html>");
            lblFire.setOpaque(false);
            lblFire.setBackground(null);
            lblFire.setForeground(Color.BLACK);
        }
    }

    // --- [기능 3] 키보드 명령 전송 ---
    private void sendDriveCommand(int keyCode) {
        if (out == null) return;

        String cmd = "";
        switch (keyCode) {
            case KeyEvent.VK_W: cmd = "FORWARD";  break;
            case KeyEvent.VK_S: cmd = "BACKWARD"; break;
            case KeyEvent.VK_A: cmd = "LEFT";     break;
            case KeyEvent.VK_D: cmd = "RIGHT";    break;
            case KeyEvent.VK_SPACE: cmd = "STOP"; break;
        }
        // F5/F6 모크는 그대로 유지 (원하면 삭제 가능)
        if (keyCode == KeyEvent.VK_F5) {
            System.out.println("[MOCK] F5: ForwardStop 시나리오 시작");
            playMockPadScenario(MockPadData.scenarioForwardStop(), 80);
        }
        if (keyCode == KeyEvent.VK_F6) {
            System.out.println("[MOCK] F6: FromLogLike 시나리오 시작");
            playMockPadScenario(MockPadData.scenarioFromLogLike(), 80);
        }

        if (!cmd.isEmpty()) {
            String json = String.format("{\"type\":\"KEY\",\"cmd\":\"%s\"}", cmd);
            out.println(json);
            System.out.println("보냄: " + json);
        }
    }

    // --- [기능 4-1] 게임패드 초기화 ---
    private void initGamepad() {
        try {
            controllers = new ControllerManager();
            controllers.initSDLGamepad();
            System.out.println("Jamepad 초기화 완료.");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    controllers.quitSDLGamepad();
                } catch (Throwable ignored) {}
            }));

            gamepadTimer = new Timer(50, e -> pollGamepad());
            gamepadTimer.start();

        } catch (Throwable t) {
            t.printStackTrace();
            SwingUtilities.invokeLater(() -> {
                lblStatus.setText("상태: 서버 연결됨 (패드 초기화 실패, 키보드만 사용 가능)");
                lblStatus.setBackground(Color.ORANGE);
            });
        }
    }

    // --- [기능 4-2] 게임패드 상태 읽고 아날로그 값 전송 + 줌 제어 ---
    private void pollGamepad() {
        if (controllers == null || out == null) {
            return;
        }

        ControllerState state = controllers.getState(0);

        if (!state.isConnected) {
            SwingUtilities.invokeLater(() ->
                    lblStatus.setText("상태: 서버 연결됨 (패드 없음)")
            );
            return;
        }

        float lx = state.leftStickX;
        float ly = state.leftStickY;
        float rx = state.rightStickX;

        boolean zoomOutPressed = state.lb;   // LB → zoom out
        boolean zoomInPressed  = state.rb;   // RB → zoom in

        lx = deadZone(lx, 0.05f);
        ly = deadZone(ly, 0.05f);
        rx = deadZone(rx, 0.05f);

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
        if (lidarPanel != null) {
            if (zoomInPressed && !lastZoomInPressed) {
                lidarPanel.adjustZoom(1.2);   // 줌 인
            }
            if (zoomOutPressed && !lastZoomOutPressed) {
                lidarPanel.adjustZoom(0.8);   // 줌 아웃
            }
        }

        lastZoomInPressed = zoomInPressed;
        lastZoomOutPressed = zoomOutPressed;
    }

    // --- [기능 4-3] 아날로그 스틱 값을 JSON으로 서버에 전송 ---
    private void sendAnalogState(float lx, float ly, float rx) {
        if (out == null) return;

        String json = String.format(Locale.US,
                "{\"type\":\"PAD\",\"lx\":%.3f,\"ly\":%.3f,\"rx\":%.3f}",
                lx, ly, rx);

        out.println(json);
        System.out.println("패드 아날로그 전송: " + json);

        SwingUtilities.invokeLater(() -> {
            lblStatus.setText(String.format(Locale.US,
                    "상태: 서버 연결됨 (패드 LX=%.2f, LY=%.2f, RX=%.2f)",
                    lx, ly, rx));
        });
    }

    // --- 헬퍼: 데드존 처리 ---
    private float deadZone(float value, float threshold) {
        return Math.abs(value) < threshold ? 0.0f : value;
    }

    // --- LiDAR 패널 (로봇은 중앙 점, 최신 스캔만 표시) ---
    private static class LidarPanel extends JPanel {
        private final Object lock = new Object();

        // 최신 스캔 (로봇 기준 local 좌표)
        private List<Point2D.Double> latestScanLocal = new ArrayList<>();

        // 줌 배율
        private double zoomFactor = 1.0;

        public LidarPanel() {
            setBackground(Color.BLACK);
            setBorder(BorderFactory.createTitledBorder("LiDAR (Robot-Centered, Latest Scan)"));
        }

        public void setLatestScan(List<Point2D.Double> localPoints) {
            synchronized (lock) {
                latestScanLocal = new ArrayList<>(localPoints);
            }
            repaint();
        }

        public void adjustZoom(double multiplier) {
            synchronized (lock) {
                zoomFactor *= multiplier;
                if (zoomFactor < 0.2) zoomFactor = 0.2;
                if (zoomFactor > 10.0) zoomFactor = 10.0;
            }
            repaint();
        }

        @Override
        protected void paintComponent(java.awt.Graphics g) {
            super.paintComponent(g);
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int padding = 20;

            List<Point2D.Double> scanSnapshot;
            double zf;

            synchronized (lock) {
                scanSnapshot = new ArrayList<>(latestScanLocal);
                zf = zoomFactor;
            }

            if (scanSnapshot.isEmpty()) {
                g2.setColor(Color.GRAY);
                g2.drawString("LiDAR 데이터 대기중...", 10, 20);
                g2.dispose();
                return;
            }

            double centerX = w / 2.0;
            double centerY = h / 2.0;

            // 스캔 점들의 범위를 이용해 자동 스케일 계산
            double minX = 0, maxX = 0, minY = 0, maxY = 0;
            boolean first = true;
            for (Point2D.Double p : scanSnapshot) {
                if (first) {
                    minX = maxX = p.x;
                    minY = maxY = p.y;
                    first = false;
                } else {
                    if (p.x < minX) minX = p.x;
                    if (p.x > maxX) maxX = p.x;
                    if (p.y < minY) minY = p.y;
                    if (p.y > maxY) maxY = p.y;
                }
            }

            double margin = 0.1;
            double worldW = (maxX - minX);
            double worldH = (maxY - minY);
            if (worldW == 0) worldW = 1;
            if (worldH == 0) worldH = 1;
            worldW *= (1.0 + margin);
            worldH *= (1.0 + margin);

            double scaleX = (w - 2.0 * padding) / worldW;
            double scaleY = (h - 2.0 * padding) / worldH;
            double scale = Math.min(scaleX, scaleY);

            scale *= zf;

            int pointSize = 2;

            // 1) 최신 스캔 점 (로봇 기준 local 좌표) – 초록색
            g2.setColor(new Color(0, 255, 0));
            for (Point2D.Double p : scanSnapshot) {
                double sx = centerX + p.x * scale;
                double sy = centerY - p.y * scale; // y 반전

                g2.fillOval(
                        (int) Math.round(sx - pointSize),
                        (int) Math.round(sy - pointSize),
                        pointSize * 2,
                        pointSize * 2
                );
            }

            // 2) 로봇 위치 (항상 중앙 점)
            double robotSX = centerX;
            double robotSY = centerY;
            int rPix = 5;
            g2.setColor(Color.RED);
            g2.fillOval((int) (robotSX - rPix), (int) (robotSY - rPix),
                    rPix * 2, rPix * 2);

            // 3) 외곽 박스
            g2.setColor(Color.DARK_GRAY);
            g2.drawRect(padding, padding, w - 2 * padding, h - 2 * padding);

            // 4) 현재 줌 배율 표시
            g2.setColor(Color.WHITE);
            g2.drawString(String.format(Locale.US, "Zoom: x%.2f", zf),
                    padding + 5, h - padding - 5);

            g2.dispose();
        }
    }

    public static void main(String[] args) {
        new Main();
    }
}
