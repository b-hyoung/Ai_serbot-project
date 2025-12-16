package org.example.socket;

import com.google.gson.JsonObject;
import org.example.state.SensorState;
import org.example.state.StateUpdater;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * 역할 정리
 * - 로봇 → 서버 : raw SENSOR / STT / CMD 수신 → SensorState 갱신
 * - 서버 → GUI   : 주기적 SENSOR_SNAPSHOT "완전체" 전송 (부분 전송 금지)
 * - dust demo    : SensorState 보정용 (이벤트 생성 ❌)
 */
public class RobotSocketService {

    private final int PORT = 6000;

    private ServerSocket serverSocket;
    private volatile Socket robotSocket;

    private final SensorState state;
    private GUISocketService guiService;

    // ===== demo / stale 보정 =====
    private static final long DUST_STALE_MS = 3_000;
    private static final long SNAPSHOT_INTERVAL_MS = 500;

    // co2가 안 들어오는 환경이면 demo로 채우는 게 더 안정적
    // (400~800은 "안전/정상"으로 보이기 쉬움)
    private static final double CO2_DEMO_DEFAULT = 450.0;

    private double demoPm25 = 18.0;
    private double demoPm10 = 28.0;
    private int demoTick = 0;

    public RobotSocketService(SensorState state) {
        this.state = state;
    }

    public void setGuiService(GUISocketService guiService) {
        this.guiService = guiService;
    }

    // =========================
    // 서버 시작
    // =========================
    public void startServer() {
        startSnapshotThread();

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                System.out.println("🤖 Robot server started : " + PORT);

                while (true) {
                    Socket socket = serverSocket.accept();
                    socket.setTcpNoDelay(true);
                    System.out.println("🤖 Robot connected: " + socket.getInetAddress());
                    handleRobot(socket);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "Robot-Accept").start();
    }

    // =========================
    // 로봇 수신
    // =========================
    private void handleRobot(Socket socket) {
        new Thread(() -> {
            try {
                robotSocket = socket;
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                String line;
                while ((line = in.readLine()) != null) {
                    try {
                        // raw JSON은 GUI로 직접 전달 금지
                        StateUpdater.applyJson(line, state);
                    } catch (Exception e) {
                        System.out.println("⚠ Robot JSON parse ignored");
                    }
                }
            } catch (Exception e) {
                System.out.println("🤖 Robot disconnected");
            } finally {
                try { socket.close(); } catch (Exception ignored) {}
                robotSocket = null;
            }
        }, "Robot-Conn").start();
    }

    // =========================
    // SENSOR_SNAPSHOT 생성 (완전체 강제)
    // =========================
    private void startSnapshotThread() {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(SNAPSHOT_INTERVAL_MS);

                    if (guiService == null || !guiService.isConnected()) continue;

                    // ✅ 로봇 연결 전에는 snapshot 자체를 보내지 않음
                    if (!isConnected()) continue;

                    long now = System.currentTimeMillis();

                    // ---- dust stale 보정: 없으면 DEMO로 채움 ----
                    if (state.getDustTs() == null || (now - state.getDustTs()) > DUST_STALE_MS) {
                        demoTick++;
                        if (demoTick % 5 == 0) {
                            demoPm25 = clamp(demoPm25 + ((demoTick % 2 == 0) ? 0.3 : -0.2), 12, 35);
                            demoPm10 = clamp(demoPm10 + ((demoTick % 2 == 0) ? 0.4 : -0.3), 18, 50);
                        }
                        state.setDust(demoPm25, demoPm10, "DEMO");
                    }

                    // ---- snapshot 생성: "항상 동일 스키마" ----
                    JsonObject snap = new JsonObject();
                    snap.addProperty("type", "SENSOR");

                    // fire: 항상 boolean
                    boolean fire = false;
                    Double flame = state.getFlame();
                    if (flame != null) fire = flame > 0.5;
                    snap.addProperty("fire", fire);

                    // co2: 항상 number
                    Double co2 = state.getCo2();
                    snap.addProperty("co2", (co2 != null) ? co2 : CO2_DEMO_DEFAULT);

                    // dust: 항상 object + pm25/pm10 둘 다 number
                    JsonObject dust = new JsonObject();
                    Double pm25 = state.getPm25();
                    Double pm10 = state.getPm10();

                    // dust 보정 로직을 탔으면 pm25/pm10은 거의 항상 존재해야 하지만,
                    // 혹시라도 null이면 demo 값으로 강제 채움
                    dust.addProperty("pm25", (pm25 != null) ? pm25 : demoPm25);
                    dust.addProperty("pm10", (pm10 != null) ? pm10 : demoPm10);

                    snap.add("dust", dust);

                    guiService.sendToGui(snap.toString());

                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    System.out.println("⚠ snapshot error: " + e.getMessage());
                }
            }
        }, "Sensor-Snapshot").start();
    }

    // =========================
    // 로봇으로 명령 송신
    // =========================
    public void sendToRobot(String msg) {
        try {
            if (robotSocket != null && !robotSocket.isClosed()) {
                PrintWriter out = new PrintWriter(robotSocket.getOutputStream(), true);
                out.println(msg);
            }
        } catch (Exception e) {
            System.out.println("⚠ sendToRobot failed");
        }
    }

    public boolean isConnected() {
        return robotSocket != null && !robotSocket.isClosed();
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}