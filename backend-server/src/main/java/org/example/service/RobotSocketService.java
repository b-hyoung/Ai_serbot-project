package org.example.service;

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

    // GUI 서비스 주입
    public void setGuiService(GUISocketService guiService) {
        this.guiService = guiService;
    }

    public void startServer() {
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
        }).start();
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

                /* 타입에 따라 값 받아서 처리하기 */
                String line;
                while ((line = in.readLine()) != null) {

                    try {
                        JsonObject json = JsonParser.parseString(line).getAsJsonObject();
                        String type = json.get("type").getAsString();

                        if (type.equals("CHAT")) {
                            String text = json.get("text").getAsString();
                            System.out.println("🗣 STT 명령: " + text);
                        } else if (type.equals("SENSOR")) {
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
                        }

                        // GUI에는 원본 JSON 그대로 전달
                        guiService.sendToGui(line);

                    } catch (Exception e) {
                        System.out.println("⚠ JSON 파싱 실패 → raw forwarding");
                        guiService.sendToGui(line);
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
        }).start();
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
