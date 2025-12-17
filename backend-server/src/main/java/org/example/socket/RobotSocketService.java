package org.example.socket;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.state.SensorState;
import org.example.state.StateUpdater;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class RobotSocketService {

    private final int PORT = 6000;

    private ServerSocket serverSocket;
    private volatile Socket robotSocket;

    private final SensorState state;
    private GUISocketService guiService;

    private static final long DUST_STALE_MS = 3_000;
    private static final long SNAPSHOT_INTERVAL_MS = 500;
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

    public void startServer() {
        startSnapshotThread();

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                System.out.println("🤖 Robot server started : " + PORT);

                while (true) {
                    Socket socket = serverSocket.accept();
                    socket.setTcpNoDelay(true);

                    // ✅ 새 로봇이 붙으면 기존 소켓 정리 (중복 연결로 상태 꼬임 방지)
                    Socket prev = robotSocket;
                    if (prev != null && !prev.isClosed()) {
                        try {
                            System.out.println("⚠ Previous robot socket exists -> closing old connection");
                            prev.close();
                        } catch (Exception ignored) {}
                    }

                    System.out.println("🤖 Robot connected: " + socket.getInetAddress());
                    handleRobot(socket);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "Robot-Accept").start();
    }

    private void handleRobot(Socket socket) {
        new Thread(() -> {
            try {
                robotSocket = socket;
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                String line;
                while ((line = in.readLine()) != null) {

                    // 1) 타입 확인(빠르게)
                    String type = null;
                    try {
                        JsonObject obj = JsonParser.parseString(line).getAsJsonObject();
                        if (obj.has("type") && !obj.get("type").isJsonNull()) {
                            type = obj.get("type").getAsString();
                        }
                    } catch (Exception ignored) {}

                    // 2) SENSOR는 상태 갱신만 하고 raw는 GUI로 보내지 않음
                    if ("SENSOR".equals(type) || "STT".equals(type) || "VISION".equals(type)) {
                        // ✅ StateUpdater가 처리하는 타입만 상태에 반영
                        try { StateUpdater.applyJson(line, state); } catch (Exception ignored) {}
                    }

                    // 3) ✅ SENSOR만 제외하고, 나머지는 GUI로 그대로 전달
                    //    (LIDAR, IMAGE, LLM, 등은 raw forwarding 필요)
                    if (guiService != null && guiService.isConnected()) {
                        if (!"SENSOR".equals(type)) {
                            guiService.sendToGui(line);
                        }
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

    private void startSnapshotThread() {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(SNAPSHOT_INTERVAL_MS);

                    if (guiService == null || !guiService.isConnected()) continue;

                    // ✅ 로봇 연결 전에는 snapshot 송신 금지
                    if (!isConnected()) continue;

                    long now = System.currentTimeMillis();

                    // dust stale 보정
                    if (state.getDustTs() == null || (now - state.getDustTs()) > DUST_STALE_MS) {
                        demoTick++;
                        if (demoTick % 5 == 0) {
                            demoPm25 = clamp(demoPm25 + ((demoTick % 2 == 0) ? 0.3 : -0.2), 12, 35);
                            demoPm10 = clamp(demoPm10 + ((demoTick % 2 == 0) ? 0.4 : -0.3), 18, 50);
                        }
                        state.setDust(demoPm25, demoPm10, "DEMO");
                    }

                    // 완전체 snapshot 강제
                    JsonObject snap = new JsonObject();
                    snap.addProperty("type", "SENSOR");

                    boolean fire = false;
                    Double flame = state.getFlame();
                    if (flame != null) fire = flame > 0.5;
                    snap.addProperty("fire", fire);

                    Double co2 = state.getCo2();
                    snap.addProperty("co2", (co2 != null) ? co2 : CO2_DEMO_DEFAULT);

                    JsonObject dust = new JsonObject();
                    Double pm25 = state.getPm25();
                    Double pm10 = state.getPm10();
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

    public void sendToRobot(String msg) {
        try {
            if (robotSocket != null && !robotSocket.isClosed()) {
                PrintWriter out = new PrintWriter(robotSocket.getOutputStream(), true);
                out.println(msg);
            }
        } catch (Exception e) {
            System.out.println("⚠ sendToRobot failed: " + e.getMessage());
        }
    }

    public boolean isConnected() {
        return robotSocket != null && !robotSocket.isClosed();
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}