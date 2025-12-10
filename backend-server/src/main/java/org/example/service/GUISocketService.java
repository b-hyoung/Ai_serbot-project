package org.example.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class GUISocketService {

    private final int PORT = 6001;          // GUI 전용 포트
    private ServerSocket serverSocket;
    private volatile Socket guiSocket;      // GUI 소켓
    private final RobotSocketService robotService; // 로봇으로 명령 전달용

    public GUISocketService(RobotSocketService robotService) {
        this.robotService = robotService;
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

                    // GUI에서 온 명령을 로봇으로 전달
                    if (robotService != null) {
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