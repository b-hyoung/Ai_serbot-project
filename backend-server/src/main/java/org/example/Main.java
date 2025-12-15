package org.example;

import org.example.service.PromptBuilder;
import org.example.service.*;
import org.example.state.SensorState;

public class Main {

    public static void main(String[] args) throws Exception {
        // 로봇 서버 + GUI 서버 생성

        SensorState state = new SensorState();
        RobotSocketService robotServer = new RobotSocketService();
        GUISocketService guiServer = new GUISocketService(robotServer);

        // 서로 연결 (로봇 서버가 GUI 서버로 데이터 보내게)
        robotServer.setGuiService(guiServer);
        VisionClient visionClient = new VisionClient("http://127.0.0.1:8008");
        ImageSocketService imageServer = new ImageSocketService(guiServer, visionClient, state);

        // 서버 시작
        robotServer.startServer(); // PORT 6000 (로봇)
        guiServer.startServer(); // PORT 6001 (GUI)
        imageServer.startServer();

        System.out.println("⏳ 로봇 접속을 기다리는 중...");
        while (!robotServer.isConnected()) {
            Thread.sleep(2000);
        }
        System.out.println("✨ 로봇 감지됨! 명령 전송 준비 완료");

        // ✅ 1) state에서 최대한 뽑고 없으면 임시값
        boolean hasHumanLikeSpeech = state.lastStt != null && !state.lastStt.isBlank();

        Double gas = state.getCo2(); // 임시: co2를 gas로 재사용 (없으면 null)
        boolean visionPerson = false; // 아직 state에 비전 결과 안 넣었으면 false
        boolean survivorUnconscious = false; // 임시

        // ✅ 2) phase 결정 (임시 규칙)
        PromptBuilder.Phase phase;
        if (!visionPerson && !hasHumanLikeSpeech) {
            phase = PromptBuilder.Phase.SEARCHING;
        } else if (visionPerson && !hasHumanLikeSpeech) {
            phase = PromptBuilder.Phase.CONFIRMED_CONTACT;
        } else {
            phase = PromptBuilder.Phase.RESCUE_GUIDE;
        }

        // ✅ 3) 7키 프롬프트 생성 (기존 구조 유지)
        String prompt = PromptBuilder.buildSevenKeyFewShotPrompt(
                phase,
                state,
                gas,
                visionPerson,
                hasHumanLikeSpeech,
                survivorUnconscious);

        System.out.println(prompt);

        // ✅ 4) person true일 때만 LLM 호출 (지금은 visionPerson이 false라 호출 안 됨)
        if (visionPerson) {
            String result = AgentService.ask(prompt);
            System.out.println("LLM 응답:");
            System.out.println(result);
        } else {
            System.out.println("LLM 스킵: visionPerson=false");
        }
    }
}