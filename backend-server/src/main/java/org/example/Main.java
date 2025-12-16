package org.example;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.service.*;
import org.example.state.SensorState;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {

    private static String jstr(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return "";
        try { return o.get(key).getAsString(); } catch (Exception e) { return ""; }
    }

    public static void main(String[] args) throws Exception {

        // ====== 센서 상태 ======
        SensorState state = new SensorState();

        // ====== 로봇 및 GUI 서버 ======
        RobotSocketService robotServer = new RobotSocketService();
        GUISocketService guiServer = new GUISocketService(robotServer);

        robotServer.setGuiService(guiServer);

        // ======= 이미지 모델 서버 =======
        VisionClient visionClient = new VisionClient("http://127.0.0.1:8008");
        ImageSocketService imageServer = new ImageSocketService(guiServer, visionClient, state, robotServer);

        // ====== Start Servers ======
        robotServer.startServer(); // 6000
        guiServer.startServer();   // 6001 (안 켜도 되지만 서버는 떠도 됨)
        imageServer.startServer(); // 6002

        System.out.println("⏳ 로봇 접속을 기다리는 중...");
        while (!robotServer.isConnected()) {
            Thread.sleep(500);
        }
        System.out.println("✨ 로봇 감지됨! 명령 전송 준비 완료");

        // ====== LLM Trigger Loop (poll state) ======
        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();

        exec.scheduleAtFixedRate(() -> {
            try {
                // 최신 state에서 읽기
                boolean hasHumanLikeSpeech =
                        state.getLastStt() != null && !state.getLastStt().isBlank();

                boolean visionPerson = Boolean.TRUE.equals(state.getVisionPerson());

                // 사람 감지 안되면 스킵
                if (!visionPerson) return;

                // 쿨다운 60초
                long now = System.currentTimeMillis();
                if (now - state.getLastLlmCallAtMs() < 60_000) return;

                // phase (임시 규칙)
                PromptBuilder.Phase phase =
                        hasHumanLikeSpeech ? PromptBuilder.Phase.RESCUE_GUIDE
                                : PromptBuilder.Phase.CONFIRMED_CONTACT;

                // 임시값들
                Double gas = state.getCo2();            // 임시로 co2 재사용
                boolean survivorUnconscious = false;    // 임시

                // 프롬프트 생성 (기존 구조 유지)
                String prompt = PromptBuilder.buildSevenKeyFewShotPrompt(
                        phase,
                        state,
                        gas,
                        true,                 // visionPerson
                        hasHumanLikeSpeech,
                        survivorUnconscious
                );

                // 중복 호출 방지 (먼저 찍음)
                state.setLastLlmCallAtMs(now);

                // LLM 호출
                String raw = AgentService.ask(prompt);
                System.out.println("🧠 LLM RAW:\n" + raw);

                // ====== LLM JSON 파싱 ======
                JsonObject obj;
                try {
                    obj = JsonParser.parseString(raw.trim()).getAsJsonObject();
                } catch (Exception pe) {
                    System.out.println("🧠 LLM JSON parse failed: " + pe.getMessage());
                    return;
                }

                String survivorSpeech = jstr(obj, "survivor_speech");
                String guiMessage     = jstr(obj, "gui_message");

                // ====== 로봇으로 전송 (6000) ======
                // 로봇 수신 코드가 JSON(type=TTS)을 처리하도록 해야 실제로 말함.
                if (!survivorSpeech.isBlank()) {
                    JsonObject toRobot = new JsonObject();
                    toRobot.addProperty("type", "TTS");
                    toRobot.addProperty("text", survivorSpeech);
                    robotServer.sendToRobot(toRobot.toString());
                }

                // ====== GUI로 전송 (6001) ======
                // GUI 안 켰으면 sendToGui가 실패 로그를 찍는 게 정상.
                if (!guiMessage.isBlank()) {
                    JsonObject toGui = new JsonObject();
                    toGui.addProperty("type", "GUI_MESSAGE");
                    toGui.addProperty("text", guiMessage);
                    guiServer.sendToGui(toGui.toString());
                }

                // (원하면 원본도 이벤트로 보낼 수 있음)
                // JsonObject llmRawEvt = new JsonObject();
                // llmRawEvt.addProperty("type", "LLM");
                // llmRawEvt.addProperty("ts", now);
                // llmRawEvt.add("raw", obj);
                // guiServer.sendToGui(llmRawEvt.toString());

            } catch (Exception e) {
                System.out.println("🧠 LLM loop error: " + e.getMessage());
            }
        }, 0, 200, TimeUnit.MILLISECONDS);

        // 메인 스레드 종료 방지
        while (true) {
            Thread.sleep(10_000);
        }
    }
}