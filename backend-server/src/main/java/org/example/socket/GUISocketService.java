package org.example.socket;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class GUISocketService {

    private final int PORT = 6001;          // GUI 전용 포트
    private ServerSocket serverSocket;
    private volatile Socket guiSocket;      // GUI 소켓
    private final RobotSocketService robotService; // 로봇으로 명령 전달용
    private final AtomicBoolean manualLlmTriggered;

    public GUISocketService(RobotSocketService robotService, AtomicBoolean manualLlmTriggered) {
        this.robotService = robotService;
        this.manualLlmTriggered = manualLlmTriggered;
    }

    public boolean isConnected() {
        Socket s = this.guiSocket;
        return s != null && !s.isClosed();
    }

    public void startServer() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                System.out.println("💻 GUI 서버 시작! PORT : " + PORT);

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    clientSocket.setTcpNoDelay(true);
                    System.out.println("💻 새로운 GUI 접속: " + clientSocket.getInetAddress());
                    handleGuiConnection(clientSocket);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // GUI 연결 담당
    private void handleGuiConnection(Socket socket) {
        new Thread(() -> {
            try {
                synchronized (this) {
                    guiSocket = socket;
                }

                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream())
                );

                String line;
                while ((line = in.readLine()) != null) {
                    System.out.println("💻 GUI -> 서버 수신: " + line);

                    try {
                        JsonObject json = JsonParser.parseString(line).getAsJsonObject();
                        String type = json.get("type").getAsString();

                        if ("PAD".equals(type)) {
                            double lx = json.get("lx").getAsDouble();
                            double ly = json.get("ly").getAsDouble();
                            double rx = json.get("rx").getAsDouble();
                            System.out.printf("🎮 GUI PAD 입력: lx=%.2f, ly=%.2f, rx=%.2f%n", lx, ly, rx);

                            // 그대로 로봇에 전달
                            robotService.sendToRobot(line);

                        } else if ("KEY".equals(type)) {
                            String cmd = json.get("cmd").getAsString();
                            System.out.println("⌨ GUI KEY 명령: " + cmd);
                            // 이 역시 로봇으로 그대로 전달할 수도 있고,
                            // 서버에서 변환해서 보낼 수도 있음
                            robotService.sendToRobot(line);
                        } else if ("MANUAL_LLM_TRIGGER".equals(type)) {
                            System.out.println("🔥 MANUAL LLM TRIGGER RECEIVED");
                            if (this.manualLlmTriggered != null) {
                                this.manualLlmTriggered.set(true);
                            }
                        } else {
                            // 기타 타입
                            robotService.sendToRobot(line);
                        }

                    } catch (Exception ex) {
                        // JSON 아니면 그냥 raw로 로봇에 포워딩
                        robotService.sendToRobot(line);
                    }
                }
            } catch (Exception e) {
                System.out.println("💻 GUI 연결 중 오류 또는 끊김: " + e.getMessage());
            } finally {
                try {
                    synchronized (this) {
                        if (socket == guiSocket) {
                            System.out.println("💻 GUI 연결 종료: " + socket.getInetAddress());
                            guiSocket = null;
                        }
                    }
                    socket.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    // 로봇에서 GUI로 데이터 보낼 때 사용
    public void sendToGui(String msg) {
        try {
            Socket gui = guiSocket;
            if (gui != null && !gui.isClosed()) {
                PrintWriter out = new PrintWriter(gui.getOutputStream(), true);
                out.println(msg);
            } else {
                System.out.println("⚠ GUI 소켓이 없어서 메시지 전송 불가: " + msg);
            }
        } catch (Exception e) {
            System.out.println("⚠ GUI로 데이터 전송 중 오류: " + e.getMessage());
        }
    }
}