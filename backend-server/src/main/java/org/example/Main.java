package org.example;

import org.example.mock.MockScenario;
import org.example.service.*;
import org.example.service.GUISocketService;
import org.example.state.SensorState;

public class Main {

    public static void main(String[] args) throws Exception {
        // 로봇 서버 + GUI 서버 생성
//        RobotSocketService robotServer = new RobotSocketService();
//        GUISocketService guiServer = new GUISocketService(robotServer);
//
//        // 서로 연결 (로봇 서버가 GUI 서버로 데이터 보내게)
//        robotServer.setGuiService(guiServer);
//
//        // 서버 시작
//        robotServer.startServer();  // PORT 6000 (로봇)
//        guiServer.startServer();    // PORT 6001 (GUI)
//
//        System.out.println("⏳ 로봇 접속을 기다리는 중...");
//        while (!robotServer.isConnected()) {
//            Thread.sleep(2000);
//        }
//        System.out.println("✨ 로봇 감지됨! 명령 전송 준비 완료");
//
        SensorState state = new SensorState();

        System.out.println("=== MOCK SCENARIO: 생존자 + 구조 요청 ===");
        for (String line : MockScenario.scenarioSurvivorNear()) {
            StateUpdater.applyJson(line, state);
        }

        System.out.println("현재 센서 상태:");
        System.out.println(state);

        String prompt = PromptBuilder.buildRiskPrompt(state);
        System.out.println("LLM 프롬프트:");
        System.out.println(prompt);

        String result = AgentService.ask(prompt);
        System.out.println("LLM 응답:");
        System.out.println(result);

    }
}