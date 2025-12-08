package org.example;

import org.example.service.GUISocketService;
import org.example.service.RobotSocketService;
import org.example.service.GUISocketService;

public class Main {

    public static void main(String[] args) throws InterruptedException {
        // 로봇 서버 + GUI 서버 생성
        RobotSocketService robotServer = new RobotSocketService();
        GUISocketService guiServer = new GUISocketService(robotServer);

        // 서로 연결 (로봇 서버가 GUI 서버로 데이터 보내게)
        robotServer.setGuiService(guiServer);

        // 서버 시작
        robotServer.startServer();  // PORT 6000 (로봇)
        guiServer.startServer();    // PORT 6001 (GUI)

        System.out.println("⏳ 로봇 접속을 기다리는 중...");
        while (!robotServer.isConnected()) {
            Thread.sleep(2000);
        }
        System.out.println("✨ 로봇 감지됨! 명령 전송 준비 완료");
        // 연결된 후에 전송!
        System.out.println("✨ 로봇 감지됨! 명령 전송!");
        // robotSocketService.sendToMessage("FORWARD");
    }
}