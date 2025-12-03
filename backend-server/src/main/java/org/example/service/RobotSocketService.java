package org.example.service;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

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
                e.printStackTrace();
            }
        }).start();
    }
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
