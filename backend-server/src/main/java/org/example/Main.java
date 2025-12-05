package org.example;

import org.example.service.RobotSocketService;

//TIP 코드를 <b>실행</b>하려면 <shortcut actionId="Run"/>을(를) 누르거나
// 에디터 여백에 있는 <icon src="AllIcons.Actions.Execute"/> 아이콘을 클릭하세요.
public class Main {
    public static void main(String[] args) throws InterruptedException {
        RobotSocketService robotSocketService = new RobotSocketService();
        robotSocketService.startServer();

        System.out.println("⏳ 로봇 접속을 기다리는 중...");
        while (!robotSocketService.isConnected()) {
            Thread.sleep(2000); // 1초 쉬기
        }

        // 연결된 후에 전송!
        System.out.println("✨ 로봇 감지됨! 명령 전송!");
    }
}