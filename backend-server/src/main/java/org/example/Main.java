package org.example;

import org.example.mock.MockScenario;
import org.example.service.*;
import org.example.service.GUISocketService;
import org.example.state.SensorState;

public class Main {

    public static void main(String[] args) throws Exception {
        // 로봇 서버 + GUI 서버 생성
       RobotSocketService robotServer = new RobotSocketService();
       GUISocketService guiServer = new GUISocketService(robotServer);

       // 서로 연결 (로봇 서버가 GUI 서버로 데이터 보내게)
        robotServer.setGuiService(guiServer);
        VisionClient visionClient = new VisionClient("http://127.0.0.1:8008");
        ImageSocketService imageServer = new ImageSocketService(guiServer, visionClient);

       // 서버 시작
        robotServer.startServer();  // PORT 6000 (로봇)
        guiServer.startServer();    // PORT 6001 (GUI)
        imageServer.startServer();

       System.out.println("⏳ 로봇 접속을 기다리는 중...");
       while (!robotServer.isConnected()) {
           Thread.sleep(2000);
       }
       System.out.println("✨ 로봇 감지됨! 명령 전송 준비 완료");



//        SensorState state = new SensorState();
//        for (String line : MockScenario.scenarioSurvivorNearHigh()) {
//            StateUpdater.applyJson(line, state);
//        }
//
//        boolean hasHumanLikeSpeech = state.lastStt != null && !state.lastStt.isBlank();
//
//        // ✅ gas/vision은 현재 없으니 임시값
//        Double gas = null;
//        boolean visionPerson = false;
//        boolean survivorUnconscious = false;
//
//        // ✅ phase는 지금 상태값이 없으면 일단 SEARCHING/CONFIRMED_CONTACT 중 하나로 지정
//        PromptBuilder.Phase phase = PromptBuilder.Phase.CONFIRMED_CONTACT;
//
//        String prompt = PromptBuilder.buildSevenKeyFewShotPrompt(
//                phase,
//                state,
//                gas,
//                visionPerson,
//                hasHumanLikeSpeech,
//                survivorUnconscious
//        );
//        System.out.println(prompt);
//        String result = AgentService.ask(prompt);
//        System.out.println("LLM 응답:");
//        System.out.println(result);
    }
}