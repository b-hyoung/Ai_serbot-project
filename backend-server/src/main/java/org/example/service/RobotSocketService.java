package org.example.service;

<<<<<<< HEAD
import java.io.BufferedInputStream;
=======
>>>>>>> e900f30abf7b6d1d0c17966a62afc61e3f2aa454
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

<<<<<<< HEAD
public class RobotSocketService {
    private ServerSocket serverSocket;
    private Socket socket;
    private final int PORT = 6000; //포트 번호

    public void startServer(){
        new Thread(()->{
            try {
                serverSocket = new ServerSocket(PORT);
                System.out.println("로봇 연결 대기중 ... PORT : " + PORT);

                while (true){
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("로봇(클라이언트)가 접속했습니다." + clientSocket.getInetAddress());
                    listenToRobot(clientSocket);
                }
            }catch (Exception e){
=======

public class RobotSocketService {
    private ServerSocket serverSocket;
    private Socket rbotSocket; // 로봇 소켓 보관용
    private Socket guiSocket;  // GUI 소켓 보관용
    private final int PORT = 6000;

    public void startServer() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                System.out.println("🤖 서버 시작! 로봇/GUI 연결 대기중 ... PORT : " + PORT);

                while (true) {
                    // 1. 접속 요청이 오면 수락
                    Socket clientSocket = serverSocket.accept();
                    clientSocket.setTcpNoDelay(true); //딜레이 없애기
                    System.out.println("새로운 손님이 접속했습니다: " + clientSocket.getInetAddress());

                    // 2. [중요] 각 손님마다 별도의 '전담 마크맨(스레드)'을 붙여줍니다.
                    // 그래야 한 명이 말하는 동안 다른 명도 접속할 수 있습니다.
                    handleConnection(clientSocket);
                }
            } catch (Exception e) {
>>>>>>> e900f30abf7b6d1d0c17966a62afc61e3f2aa454
                e.printStackTrace();
            }
        }).start();
    }
<<<<<<< HEAD
    private void listenToRobot(Socket socket){
        this.socket = socket;
        try(BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))){
            String inputLine;
            while ((inputLine = in.readLine()) != null){
                System.out.println("수신된 메시지" + inputLine);
            }
        }catch (Exception e){
            e.printStackTrace();
            this.socket = null; //만약 소켓연결이 끊긴다면 null로 보내는 소켓도 제거
        }
    }
    public void sendToMessage(String command){
        try{
            if(socket != null && !socket.isClosed()){
                PrintWriter out = new PrintWriter(socket.getOutputStream(),true);
                out.println(command);
                System.out.println(command + "로봇에게 전달");
            }else{
                System.out.println("로봇 전송 실패");
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    public boolean isConnected() {
        // 소켓이 비어있지 않고(null), 닫히지 않았으면 true
        return this.socket != null && !this.socket.isClosed();
    }
}
=======

    // 쓰레드를 통해 프로세스를 송/수신으로 나눠서 관리
    private void handleConnection(Socket socket) {
        new Thread(() -> {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

                String line;
                while ((line = in.readLine()) != null) {

                    // [1] 로봇인지 GUI인지 판단하는 로직
                    if (line.trim().startsWith("{")) {
                        // JSON 데이터({로 시작)가 오면 "너는 로봇이구나!"
                        this.rbotSocket = socket;
                        System.out.println("🤖 로봇 -> GUI 전송: " + line);

                        // GUI가 연결되어 있다면 그대로 토스 (중계)
                        if (this.guiSocket != null && !this.guiSocket.isClosed()) {
                            PrintWriter guiOut = new PrintWriter(this.guiSocket.getOutputStream(), true);
                            guiOut.println(line);
                        }
                    }
                    else {
                        // 일반 문자열(FORWARD 등)이 오면 "너는 GUI구나!"
                        this.guiSocket = socket;
                        System.out.println("💻 GUI -> 로봇 명령: " + line);

                        // 로봇이 연결되어 있다면 그대로 토스 (중계)
                        if (this.rbotSocket != null && !this.rbotSocket.isClosed()) {
                            PrintWriter robotOut = new PrintWriter(this.rbotSocket.getOutputStream(), true);
                            robotOut.println(line);
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("❌ 손님 연결 끊김");
            } finally {
                // 연결이 끊어지면 소켓 정리
                try {
                    socket.close();
                }catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    // 서버 자체에서 강제로 로봇에게 보낼 때 쓰는 함수 (테스트용)
    public void sendToRobot(String command) {
        try {
            if (isConnected()) {
                PrintWriter out = new PrintWriter(rbotSocket.getOutputStream(), true);
                out.println(command);
                System.out.println("[서버 직접 전송] " + command);
            } else {
                System.out.println("로봇 미연결: 전송 실패");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    //로봇 연결 여부 체크 --> 체크여부에 따라 센서값 받아오기 또는 값 전달 여부
    public boolean isConnected() {
        return this.rbotSocket != null && !this.rbotSocket.isClosed();
    }
}
>>>>>>> e900f30abf7b6d1d0c17966a62afc61e3f2aa454
