package org.example.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class RobotSocketService {
    private ServerSocket serverSocket;
    private volatile Socket rbotSocket; // 로봇 소켓 보관용 (여러 스레드에서 공유)
    private volatile Socket guiSocket;  // GUI 소켓 보관용 (여러 스레드에서 공유)
    private final int PORT = 6000;

    // 클라이언트 종류 구분용
    private enum ClientRole {
        ROBOT,
        GUI
    }

    public void startServer() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                System.out.println("🤖 서버 시작! 로봇/GUI 연결 대기중 ... PORT : " + PORT);

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    clientSocket.setTcpNoDelay(true); // 딜레이 제거
                    System.out.println("새로운 손님이 접속했습니다: " + clientSocket.getInetAddress());

                    handleConnection(clientSocket);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // 각 클라이언트 연결 담당
    private void handleConnection(Socket socket) {
        new Thread(() -> {
            ClientRole role = null;

            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

                // 1) 먼저 "역할(ROLE)"부터 선언하도록 프로토콜 정의
                // 예: "ROLE:ROBOT" 또는 "ROLE:GUI"
                String firstLine = in.readLine();
                if (firstLine == null) {
                    System.out.println("❌ 첫 메시지 없이 연결 종료됨");
                    return;
                }

                firstLine = firstLine.trim();

                if (firstLine.startsWith("ROLE:ROBOT")) {
                    role = ClientRole.ROBOT;
                    synchronized (this) {
                        rbotSocket = socket;
                    }
                    System.out.println("🤖 로봇 클라이언트 등록 완료: " + socket.getInetAddress());
                } else if (firstLine.startsWith("ROLE:GUI")) {
                    role = ClientRole.GUI;
                    synchronized (this) {
                        guiSocket = socket;
                    }
                    System.out.println("💻 GUI 클라이언트 등록 완료: " + socket.getInetAddress());
                } else {
                    System.out.println("❌ 알 수 없는 역할: " + firstLine + " → 연결 종료");
                    return;
                }

                // 2) 역할에 따라 메시지 중계
                String line;
                while ((line = in.readLine()) != null) {
                    if (role == ClientRole.ROBOT) {
                        // 로봇 → GUI로 센서 데이터 등 전달
                        System.out.println("🤖 로봇 -> GUI 전송: " + line);

                        Socket gui = guiSocket;
                        if (gui != null && !gui.isClosed()) {
                            PrintWriter guiOut = new PrintWriter(gui.getOutputStream(), true);
                            guiOut.println(line);
                        }
                    } else if (role == ClientRole.GUI) {
                        // GUI → 로봇으로 명령 전달
                        System.out.println("💻 GUI -> 로봇 명령: " + line);

                        Socket robot = rbotSocket;
                        if (robot != null && !robot.isClosed()) {
                            PrintWriter robotOut = new PrintWriter(robot.getOutputStream(), true);
                            robotOut.println(line);
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("❌ 손님 연결 중 오류 또는 끊김: " + e.getMessage());
            } finally {
                // 연결 종료 시 정리
                try {
                    if (role == ClientRole.ROBOT) {
                        synchronized (this) {
                            if (socket == rbotSocket) {
                                System.out.println("🤖 로봇 연결 종료: " + socket.getInetAddress());
                                rbotSocket = null;
                            }
                        }
                    } else if (role == ClientRole.GUI) {
                        synchronized (this) {
                            if (socket == guiSocket) {
                                System.out.println("💻 GUI 연결 종료: " + socket.getInetAddress());
                                guiSocket = null;
                            }
                        }
                    }

                    socket.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    // 로봇 연결 여부 체크
    public boolean isConnected() {
        Socket robot = this.rbotSocket;
        return robot != null && !robot.isClosed();
    }
}
