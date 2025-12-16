package org.example.socket;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class RobotSocketService {

    private final int PORT = 6000;          // 로봇 전용 포트
    private ServerSocket serverSocket;
    private volatile Socket robotSocket;    // 로봇 소켓
    private GUISocketService guiService;    // GUI 서비스로 데이터 보내기용

    // ===== dust 캐시 / 시연용 =====
    private volatile Double lastPm25 = null;
    private volatile Double lastPm10 = null;
    private volatile long lastDustUpdateTime = 0;

    // dust가 일정 시간 안 들어오면 데모값 송신
    private static final long DUST_STALE_MS = 3_000; // 3초
    private static final long DUST_PUSH_INTERVAL_MS = 1_000; // 1초

    // 데모용 기본 값 (너무 높게 잡으면 위험 경고 뜰 수 있음)
    private volatile double demoPm25 = 18.0;
    private volatile double demoPm10 = 28.0;
    private volatile int demoTick = 0;

    // GUI 서비스 주입
    public void setGuiService(GUISocketService guiService) {
        this.guiService = guiService;
    }

    public void startServer() {
        // ✅ dust 안정 송신 쓰레드 (서버 시작 시 1회)
        startDustReplayThread();

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                System.out.println("🤖 로봇 서버 시작! PORT : " + PORT);

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    clientSocket.setTcpNoDelay(true);
                    System.out.println("🤖 새로운 로봇 접속: " + clientSocket.getInetAddress());
                    handleRobotConnection(clientSocket);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "Robot-Accept-Thread").start();
    }

    // 로봇 연결 담당
    private void handleRobotConnection(Socket socket) {
        new Thread(() -> {
            try {
                synchronized (this) {
                    robotSocket = socket;
                }

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream())
                );

                String line;
                while ((line = in.readLine()) != null) {

                    try {
                        JsonObject json = JsonParser.parseString(line).getAsJsonObject();
                        String type = json.get("type").getAsString();

                        if ("CHAT".equals(type)) {
                            String text = json.get("text").getAsString();
                            System.out.println("🗣 STT 명령: " + text);

                        } else if ("SENSOR".equals(type)) {
                            boolean fire = json.has("fire") && !json.get("fire").isJsonNull() && json.get("fire").getAsBoolean();
                            Double gas = json.has("gas") && !json.get("gas").isJsonNull() ? json.get("gas").getAsDouble() : null;

                            // dust는 null 가능
                            Double pm25 = null, pm10 = null;
                            if (json.has("dust") && json.get("dust").isJsonObject()) {
                                JsonObject dust = json.getAsJsonObject("dust");
                                if (dust.has("pm25") && !dust.get("pm25").isJsonNull()) pm25 = dust.get("pm25").getAsDouble();
                                if (dust.has("pm10") && !dust.get("pm10").isJsonNull()) pm10 = dust.get("pm10").getAsDouble();
                            }

                            System.out.println("🔥 fire=" + fire + ", gas=" + gas + ", pm25=" + pm25 + ", pm10=" + pm10);

                            // ✅ 실제 dust가 들어오면 캐시 업데이트 (들어오는 값이 없으면 데모 쓰레드가 대신 보냄)
                            if (pm25 != null || pm10 != null) {
                                lastPm25 = pm25;
                                lastPm10 = pm10;
                                lastDustUpdateTime = System.currentTimeMillis();
                            }
                        }

                        // GUI에는 원본 JSON 그대로 전달
                        if (guiService != null) {
                            guiService.sendToGui(line);
                        }

                    } catch (Exception e) {
                        System.out.println("⚠ JSON 파싱 실패 → raw forwarding");
                        if (guiService != null) {
                            guiService.sendToGui(line);
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("🤖 로봇 연결 중 오류 또는 끊김: " + e.getMessage());
            } finally {
                try {
                    synchronized (this) {
                        if (socket == robotSocket) {
                            System.out.println("🤖 로봇 연결 종료: " + socket.getInetAddress());
                            robotSocket = null;
                        }
                    }
                    socket.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, "Robot-Conn-Thread").start();
    }

    // ✅ dust를 주기적으로 GUI로 보내는 쓰레드
    // - 최근 dust가 들어오면 그 값 재전송
    // - dust가 안 들어오면 데모값 생성해서 송신
    private void startDustReplayThread() {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(DUST_PUSH_INTERVAL_MS);

                    if (guiService == null) continue;

                    long now = System.currentTimeMillis();
                    boolean stale = (now - lastDustUpdateTime) > DUST_STALE_MS;

                    Double pm25ToSend;
                    Double pm10ToSend;

                    if (!stale && (lastPm25 != null || lastPm10 != null)) {
                        // 최근 실제 값이 있으면 그걸 사용
                        pm25ToSend = lastPm25;
                        pm10ToSend = lastPm10;
                    } else {
                        // ❗ 시연용 값 생성 (너무 티나지 않게 아주 천천히만 움직임)
                        demoTick++;
                        if (demoTick % 5 == 0) { // 5초에 한 번 정도만 변하게
                            demoPm25 = clamp(demoPm25 + ((demoTick % 2 == 0) ? 0.3 : -0.2), 12.0, 35.0);
                            demoPm10 = clamp(demoPm10 + ((demoTick % 2 == 0) ? 0.4 : -0.3), 18.0, 50.0);
                        }
                        pm25ToSend = demoPm25;
                        pm10ToSend = demoPm10;
                    }

                    JsonObject out = new JsonObject();
                    out.addProperty("type", "SENSOR");

                    JsonObject dust = new JsonObject();
                    if (pm25ToSend != null) dust.addProperty("pm25", pm25ToSend);
                    if (pm10ToSend != null) dust.addProperty("pm10", pm10ToSend);

                    out.add("dust", dust);

                    // GUI로 송신
                    guiService.sendToGui(out.toString());

                } catch (InterruptedException ie) {
                    break;
                } catch (Exception e) {
                    System.out.println("⚠ dust replay 오류: " + e.getMessage());
                }
            }
        }, "Dust-Replay-Thread").start();
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    // GUI 쪽에서 로봇으로 명령 보낼 때 호출
    public void sendToRobot(String msg) {
        try {
            Socket robot = robotSocket;
            if (robot != null && !robot.isClosed()) {
                PrintWriter out = new PrintWriter(robot.getOutputStream(), true);
                out.println(msg);
            } else {
                System.out.println("⚠ 로봇 소켓이 없어서 메시지 전송 불가: " + msg);
            }
        } catch (Exception e) {
            System.out.println("⚠ 로봇으로 데이터 전송 중 오류: " + e.getMessage());
        }
    }

    // 로봇 연결 여부 체크
    public boolean isConnected() {
        Socket robot = this.robotSocket;
        return robot != null && !robot.isClosed();
    }
}