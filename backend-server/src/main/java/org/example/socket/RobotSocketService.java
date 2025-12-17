package org.example.socket;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.database.repo.SensorSnapshotRepo;
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

    // ===== stale / snapshot =====
    private static final long DUST_STALE_MS = 3_000;
    private static final long PIR_STALE_MS  = 3_000;
    private static final long VISION_STALE_MS = 3_000;

    private static final long SNAPSHOT_INTERVAL_MS = 500;

    // ===== demo defaults =====
    private static final double CO2_DEMO_DEFAULT = 450.0;

    private double demoPm25 = 18.0;
    private double demoPm10 = 28.0;
    private int demoTick = 0;

    // DB
    private final SensorSnapshotRepo sensorRepo = new SensorSnapshotRepo();


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

                    // 새 로봇이 붙으면 기존 소켓 정리
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

                    // 2) State 반영 (SENSOR / STT / VISION)
                    //    - PIR은 SENSOR에 같이 오거나, 별도 타입으로 올 수도 있음(프로젝트 상황에 따라)
                    if ("SENSOR".equals(type) || "STT".equals(type) || "VISION".equals(type) || "PIR".equals(type)) {
                        try { StateUpdater.applyJson(line, state); } catch (Exception ignored) {}
                    }

                    // 3) GUI로 raw forwarding
                    //    - SENSOR는 snapshot으로만 보내고 raw는 보내지 않는다(중복/형식불일치 방지)
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
                    if (!isConnected()) continue;

                    long now = System.currentTimeMillis();

                    // ===== dust stale 보정 (B방향: null/미수신이면 demo 생성해서 state에 넣고 GUI로 보냄) =====
                    if (state.getDustTs() == null || (now - state.getDustTs()) > DUST_STALE_MS) {
                        demoTick++;
                        if (demoTick % 5 == 0) {
                            demoPm25 = clamp(demoPm25 + ((demoTick % 2 == 0) ? 0.3 : -0.2), 12, 35);
                            demoPm10 = clamp(demoPm10 + ((demoTick % 2 == 0) ? 0.4 : -0.3), 18, 50);
                        }
                        state.setDust(demoPm25, demoPm10, "DEMO");
                    }

                    // ===== snapshot 생성 =====
                    JsonObject snap = new JsonObject();
                    snap.addProperty("type", "SENSOR");

                    // fire는 flame(0~1) 기반으로 계산
                    boolean fire = false;
                    Double flame = state.getFlame();
                    if (flame != null) fire = flame > 0.5;
                    snap.addProperty("fire", fire);

                    // co2 기본값
                    Double co2 = state.getCo2();
                    snap.addProperty("co2", (co2 != null) ? co2 : CO2_DEMO_DEFAULT);

                    // dust
                    JsonObject dust = new JsonObject();
                    Double pm25 = state.getPm25();
                    Double pm10 = state.getPm10();
                    dust.addProperty("pm25", (pm25 != null) ? pm25 : demoPm25);
                    dust.addProperty("pm10", (pm10 != null) ? pm10 : demoPm10);
                    snap.add("dust", dust);
                    if (state.getDustSource() != null) {
                        snap.addProperty("dustSource", state.getDustSource());
                    }

                    // ===== PIR / VISION 동시 포함 (서버 시각 기준 stale 처리) =====
                    // pir
                    Boolean pir = state.getPir();
                    Long pirTs = state.getPirTs();
                    boolean pirValid = (pirTs != null) && ((now - pirTs) <= PIR_STALE_MS);
                    snap.addProperty("pir", (pir != null && pirValid) ? pir : false);
                    snap.addProperty("pirStale", !pirValid);

                    // visionPerson
                    Boolean visionPerson = state.getVisionPerson();
                    Long visionTs = state.getVisionTs();
                    boolean visionValid = (visionTs != null) && ((now - visionTs) <= VISION_STALE_MS);
                    snap.addProperty("visionPerson", (visionPerson != null && visionValid) ? visionPerson : false);
                    snap.addProperty("visionStale", !visionValid);

                    // 선택: conf도 같이
                    Double conf = state.getVisionConf();
                    if (conf != null) snap.addProperty("visionConf", conf);

                    /* DB 전송 */
                    long ts = now;

                    boolean fireVal = fire;
                    double co2Val = (co2 != null) ? co2 : CO2_DEMO_DEFAULT;
                    double pm25Val = (pm25 != null) ? pm25 : demoPm25;
                    double pm10Val = (pm10 != null) ? pm10 : demoPm10;

                    // source 컬럼에 DEMO/REAL 넣기
                    String sourceVal = (state.getDustSource() != null && !state.getDustSource().isBlank())
                            ? state.getDustSource()
                            : "REAL";

                    // vision 기반 사람 감지(시연용)
                    boolean visionPersonVal = (visionPerson != null && visionValid) ? visionPerson : false;
                    Double visionConfVal = conf;
                    boolean personDetected = visionPersonVal && (visionConfVal == null || visionConfVal >= 0.5);

                    // ✅ DB insert (pir 컬럼에 personDetected 저장)
                    sensorRepo.insert(
                            ts,
                            fireVal,
                            co2Val,
                            pm25Val,
                            pm10Val,
                            personDetected, // pir
                            sourceVal
                    );
                    //


                    // 최종: GUI로 snapshot 송신
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