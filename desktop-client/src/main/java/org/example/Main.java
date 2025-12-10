package org.example;

import org.json.JSONObject;

import javax.swing.*;
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
    private final String SERVER_IP = "172.31.38.120"; // 서버 IP (테스트: 잘못된 IP로 바꿔보세요)
    private final int SERVER_PORT = 6000;

    // --- 화면 구성 요소 (라벨/패널) ---
    private LidarPanel lidarPanel; // LiDAR SLAM 맵 패널

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


    // --- MYGUI 인스턴스 ---
    private MYGUI gui;

    public Main() {
        // 1. MYGUI 창 생성
        gui = new MYGUI();

        // 2. MYGUI에 키보드 리스너 추가
        gui.setFocusable(true);
        gui.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                sendDriveCommand(e.getKeyCode());
            }
        });

        // 창 보이도록 (MYGUI 내부에서 setVisible 해주므로 여기서는 필요없음)
        // 3. 서버 연결 시작
        connectToServer();

        // 6. 게임패드 초기화 & 폴링 시작
        initGamepad();
    }

    // --- [기능 1] 서버 연결 및 데이터 수신 ---
    private void connectToServer() {
        new Thread(() -> {
            try {
                socket = new Socket(SERVER_IP, SERVER_PORT);
                socket.setTcpNoDelay(true); // 딜레이 제거
                out = new PrintWriter(socket.getOutputStream(), true);
                out.println("ROLE:GUI");

                System.out.println("✅ 서버 연결 성공!");

                // GUI 상태 업데이트 (연결 성공)
                if (gui != null) {
                    SwingUtilities.invokeLater(() -> gui.updateConnectionStatus(true));
                }

                // 서버가 보내주는 데이터 계속 듣기// server ကို clientကို ချိတ်ပေးတဲ့ ဟာ
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    try {
                        JSONObject json = new JSONObject(inputLine);


                        /* 타입이 센서일때 처리하는 함수 클래스 따로 생성하세요 */


                        String type = json.optString("type", "");

                        if (json.getString("type").equals("SENSOR")) {// server json string ကို json object
                            double temp = json.getDouble("temp");
                            double gas = json.getDouble("gas");
                            boolean fire = json.getBoolean("fire");
                            boolean pir = json.getBoolean("pir");
                            double humidity = json.getDouble("humidity");
                            double pm25 = json.getDouble("pm25");
                            double pm10 = json.getDouble("pm10");

                            // MYGUI 화면 갱신 (Swing 스레드 안전하게)
                            SwingUtilities.invokeLater(
                                    () -> gui.updateSensorData(temp, gas, fire, pir, humidity, pm25, pm10));
                        }

                        // 필요하면 다른 타입 처리 (예: DUST, PIR 등) 추가 가능
                        // if ("DUST".equals(json.getString("type"))) { ... gui.updateDust(pm25, pm10);
                        // }

                    } catch (Exception e) {
                        System.out.println("데이터 형식 오류: " + inputLine);
                    }
                }

            } catch (Exception e) {
                System.out.println("❌ 서버 연결 실패!");

                // GUI 상태 업데이트 (연결 실패)//gui update ကို main မှာ လုပ်ဖို့ထားထားတာ
                if (gui != null) {
                    SwingUtilities.invokeLater(() -> gui.updateConnectionStatus(false));
                }

                e.printStackTrace();
            }
        }).start();
    }

    // --- LIDAR JSON 처리 ---
    // 기대 JSON 형식:
    // {
    //   "type":"LIDAR",
    //   "robotX": 0.0,
    //   "robotY": 0.0,
    //   "robotTheta": 1.57,
    //   "points":[ [x_local,y_local], ... ]  또는  [{ "x":..,"y":.. }, ...]
    // }
    private void handleLidarJson(JSONObject json) {
        if (lidarPanel == null) return;

        // 로봇 위치/자세 (월드 좌표계)
        double robotX = json.optDouble("robotX", 0.0);
        double robotY = json.optDouble("robotY", 0.0);
        double robotTheta = json.optDouble("robotTheta", 0.0); // 라디안

        JSONArray arr = json.optJSONArray("points");
        if (arr == null) return;

        List<Point2D.Double> localPoints = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            Object elem = arr.get(i);
            double x, y;

            try {
                if (elem instanceof JSONArray) {
                    // "points":[[x,y],[x,y],...]
                    JSONArray p = (JSONArray) elem;
                    if (p.length() < 2) continue;
                    x = p.getDouble(0);
                    y = p.getDouble(1);
                } else if (elem instanceof JSONObject) {
                    // "points":[{"x":..,"y":..}, ...]
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

        // 새 스캔을 SLAM 맵에 누적
        SwingUtilities.invokeLater(() ->
                lidarPanel.addScan(localPoints, robotX, robotY, robotTheta)
        );
    }

    private void sendDriveCommand(int keyCode) {
        if (out == null)
            return;

        String cmd = "";
        switch (keyCode) {
            case KeyEvent.VK_W: cmd = "FORWARD";  break;
            case KeyEvent.VK_S: cmd = "BACKWARD"; break;
            case KeyEvent.VK_A: cmd = "LEFT";     break;
            case KeyEvent.VK_D: cmd = "RIGHT";    break;
            case KeyEvent.VK_SPACE: cmd = "STOP"; break;
        }

        if (!cmd.isEmpty()) {
           // JSON 형식으로 전송: {"type":"KEY","cmd":"FORWARD"}
          String json = String.format("{\"type\":\"KEY\",\"cmd\":\"%s\"}", cmd);

          out.println(json);                // 서버로 전송
          System.out.println("보냄: " + json);
        }
    }

    // --- [기능 4-1] 게임패드 초기화 ---
    private void initGamepad() {
        try {
            controllers = new ControllerManager();
            controllers.initSDLGamepad();
            System.out.println("Jamepad 초기화 완료.");

            // 프로그램 종료 시 네이티브 정리
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    controllers.quitSDLGamepad();
                } catch (Throwable ignored) {}
            }));

            // 50ms마다 게임패드 상태 폴링 (20Hz)
            gamepadTimer = new Timer(50, e -> pollGamepad());
            gamepadTimer.start();

        } catch (Throwable t) {
            t.printStackTrace();
            SwingUtilities.invokeLater(() -> {
//                lblStatus.setText("상태: 서버 연결됨 (패드 초기화 실패, 키보드만 사용 가능)");
//                lblStatus.setBackground(Color.ORANGE);
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
//            SwingUtilities.invokeLater(() ->
//                    lblStatus.setText("상태: 서버 연결됨 (패드 없음)")
//            );
            return;
        }

        // 왼쪽 스틱 X/Y, 오른쪽 스틱 X 사용
        float lx = state.leftStickX;
        float ly = state.leftStickY;
        float rx = state.rightStickX;

        // 줌 버튼 상태 읽기 (LB/RB)
        boolean zoomOutPressed = state.lb;   // LB → zoom out
        boolean zoomInPressed  = state.rb;  // RB → zoom in

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
        if (lidarPanel != null) {
            // RB 눌린 순간 → 줌 인
            if (zoomInPressed && !lastZoomInPressed) {
                lidarPanel.adjustZoom(1.2);   // 20% 확대
            }
            // LB 눌린 순간 → 줌 아웃
            if (zoomOutPressed && !lastZoomOutPressed) {
                lidarPanel.adjustZoom(0.8);   // 20% 축소
            }
        }

        // 다음 호출을 위해 현재 버튼 상태 저장
        lastZoomInPressed = zoomInPressed;
        lastZoomOutPressed = zoomOutPressed;
    }

    // --- [기능 4-3] 아날로그 스틱 값을 JSON으로 서버에 전송 ---
    private void sendAnalogState(float lx, float ly, float rx) {
        if (out == null) return;

        // 소수점 구분자 통일 (Locale.US → 항상 '.' 사용)
        String json = String.format(Locale.US,
                "{\"type\":\"PAD\",\"lx\":%.3f,\"ly\":%.3f,\"rx\":%.3f}",
                lx, ly, rx);

        out.println(json);
        System.out.println("패드 아날로그 전송: " + json);

//        SwingUtilities.invokeLater(() -> {
//            lblStatus.setText(String.format(Locale.US,
//                    "상태: 서버 연결됨 (패드 LX=%.2f, LY=%.2f, RX=%.2f)",
//                    lx, ly, rx));
//        });
    }

    // --- 헬퍼: 데드존 처리 ---
    private float deadZone(float value, float threshold) {
        return Math.abs(value) < threshold ? 0.0f : value;
        }

    // --- LiDAR SLAM 맵 패널 (로봇 항상 중앙 뷰 + 줌) ---
    private static class LidarPanel extends JPanel {
        private final Object lock = new Object();

        // SLAM 누적용 전역 포인트들 (월드 좌표계)
        private final List<Point2D.Double> globalPoints = new ArrayList<>();

        // 가장 최근 스캔 포인트 (월드 좌표, 하이라이트용)
        private List<Point2D.Double> lastScanGlobal = new ArrayList<>();

        // 현재 로봇 포즈 (월드 좌표)
        private double robotX = 0.0;
        private double robotY = 0.0;
        private double robotTheta = 0.0; // 라디안

        // 줌 배율 (1.0 = 기본)
        private double zoomFactor = 1.0;

        // 너무 많아지는 것을 방지하기 위한 최대 포인트 수
        private static final int MAX_POINTS = 20000;

        public LidarPanel() {
            setBackground(Color.BLACK);
            setBorder(BorderFactory.createTitledBorder("LiDAR SLAM (Robot-Centered)"));
        }

        /**
         * 새 스캔(local 좌표)을 받아서
         * 로봇 포즈를 기준으로 월드 좌표로 변환한 뒤 globalPoints에 누적
         */
        public void addScan(List<Point2D.Double> localPoints,
                            double robotX,
                            double robotY,
                            double robotTheta) {

            synchronized (lock) {
                this.robotX = robotX;
                this.robotY = robotY;
                this.robotTheta = robotTheta;

                double cos = Math.cos(robotTheta);
                double sin = Math.sin(robotTheta);

                List<Point2D.Double> newGlobal = new ArrayList<>(localPoints.size());

                for (Point2D.Double lp : localPoints) {
                    // 로봇 기준(local) -> 월드 좌표
                    double gx = robotX + (lp.x * cos - lp.y * sin);
                    double gy = robotY + (lp.x * sin + lp.y * cos);

                    Point2D.Double gp = new Point2D.Double(gx, gy);
                    globalPoints.add(gp);
                    newGlobal.add(gp);
                }

                // 오래된 포인트 잘라내기 (메모리/속도 보호용)
                if (globalPoints.size() > MAX_POINTS) {
                    int removeCount = globalPoints.size() - MAX_POINTS;
                    globalPoints.subList(0, removeCount).clear();
                }

                // 최근 스캔 저장 (하이라이트용)
                lastScanGlobal = newGlobal;
            }

            repaint();
        }

        // 줌 조절 (multiplier > 1 : 확대, < 1 : 축소)
        public void adjustZoom(double multiplier) {
            synchronized (lock) {
                zoomFactor *= multiplier;
                // 줌 범위 제한
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

            List<Point2D.Double> globalSnapshot;
            List<Point2D.Double> lastScanSnapshot;
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
                g2.setColor(Color.GRAY);
                g2.drawString("LiDAR 데이터 대기중...", 10, 20);
                g2.dispose();
                return;
            }

            // 화면 중앙을 로봇 위치로 사용
            double centerX = w / 2.0;
            double centerY = h / 2.0;

            // 로봇을 기준으로 한 상대 좌표 범위 계산
            double minDX = 0, maxDX = 0, minDY = 0, maxDY = 0;
            boolean first = true;

            for (Point2D.Double p : globalSnapshot) {
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

            // 보기 좋게 조금 여유를 둠
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

            // 줌 배율 적용
            scale *= zf;

            int pointSize = 2;

            // 1) 누적 맵(globalPoints) – 연한 초록
            g2.setColor(new Color(0, 160, 0));
            for (Point2D.Double p : globalSnapshot) {
                double dx = p.x - rX;
                double dy = p.y - rY;

                double sx = centerX + dx * scale;
                double sy = centerY - dy * scale; // y 반전

                g2.fillOval(
                        (int) Math.round(sx - pointSize / 2.0),
                        (int) Math.round(sy - pointSize / 2.0),
                        pointSize,
                        pointSize
                );
            }

            // 2) 최근 스캔(lastScanGlobal) – 더 밝은 초록
            g2.setColor(new Color(0, 255, 0));
            for (Point2D.Double p : lastScanSnapshot) {
                double dx = p.x - rX;
                double dy = p.y - rY;

                double sx = centerX + dx * scale;
                double sy = centerY - dy * scale;

                g2.fillOval(
                        (int) Math.round(sx - pointSize),
                        (int) Math.round(sy - pointSize),
                        pointSize * 2,
                        pointSize * 2
                );
            }

            // 3) 로봇 위치/방향 (항상 화면 중앙)
            double robotSX = centerX;
            double robotSY = centerY;

            int rPix = 8;
            g2.setColor(Color.RED);
            g2.fillOval((int) (robotSX - rPix), (int) (robotSY - rPix),
                    rPix * 2, rPix * 2);

            double arrowLen = 25;
            double hx = robotSX + Math.cos(rTheta) * arrowLen;
            double hy = robotSY - Math.sin(rTheta) * arrowLen;

            g2.setColor(Color.YELLOW);
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine((int) robotSX, (int) robotSY, (int) hx, (int) hy);

            // 4) 외곽 박스
            g2.setColor(Color.DARK_GRAY);
            g2.drawRect(padding, padding, w - 2 * padding, h - 2 * padding);

            // 5) 현재 줌 배율 표시
            g2.setColor(Color.WHITE);
            g2.drawString(String.format(Locale.US, "Zoom: x%.2f", zf), padding + 5, h - padding - 5);

            g2.dispose();
        }
    }

    public static void main(String[] args) {
        // Swing UIကိူEvent Dispatch Threadမှာ ထားတာက ေကာင်းတယ်
        SwingUtilities.invokeLater(() -> new Main());
    }
}
