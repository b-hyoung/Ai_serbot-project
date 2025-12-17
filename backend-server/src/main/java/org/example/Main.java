package org.example;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.service.AgentService;
import org.example.service.PromptBuilder;
import org.example.service.VisionClient;
import org.example.socket.GUISocketService;
import org.example.socket.ImageSocketService;
import org.example.socket.RobotSocketService;
import org.example.socket.VideoSocketService;
import org.example.state.SensorState;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class Main {

    private static String jstr(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return "";
        try { return o.get(key).getAsString(); } catch (Exception e) { return ""; }
    }

    public static void main(String[] args) throws Exception {

        // ====== 센서 상태 ======
        SensorState state = new SensorState();

        // ✅ LLM 루프 제어 상태는 SensorState에 두지 말고 Main에서 관리 (시연용 안정)
        AtomicLong lastLlmCallAtMs = new AtomicLong(0);

        // ====== 로봇 및 GUI 서버 ======
        RobotSocketService robotServer = new RobotSocketService(state);
        GUISocketService guiServer = new GUISocketService(robotServer);

        robotServer.setGuiService(guiServer);

        // ======= 이미지 모델 서버 =======
        VisionClient visionClient = new VisionClient("http://127.0.0.1:8008");
        ImageSocketService imageServer = new ImageSocketService(guiServer, visionClient, state, robotServer);

        VideoSocketService video = new VideoSocketService();
        video.setGuiService(guiServer);


        // ====== Start Servers ======
        robotServer.startServer(); // 6000
        guiServer.startServer();   // 6001
        imageServer.startServer(); // 6002
        video.startServer(); // 6003


        System.out.println("⏳ 로봇 접속을 기다리는 중...");
        while (!robotServer.isConnected()) {
            Thread.sleep(500);
        }
        System.out.println("✨ 로봇 감지됨! 명령 전송 준비 완료");

        // ====== LLM Trigger Loop (poll state) ======
        ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();

        exec.scheduleAtFixedRate(() -> {
            try {
                boolean hasHumanLikeSpeech =
                        state.getLastStt() != null && !state.getLastStt().isBlank();

                boolean visionPerson = Boolean.TRUE.equals(state.getVisionPerson());

                // 사람 감지 안되면 스킵
                if (!visionPerson) return;

                // 쿨다운 60초
                long now = System.currentTimeMillis();
                if (now - lastLlmCallAtMs.get() < 60_000) return;

                // phase (임시 규칙)
                PromptBuilder.Phase phase =
                        hasHumanLikeSpeech ? PromptBuilder.Phase.RESCUE_GUIDE
                                : PromptBuilder.Phase.CONFIRMED_CONTACT;

                // ✅ gas 제거했으니 co2로 통일 (PromptBuilder가 인자를 gas라고 받아도 값은 co2)
                Double co2 = state.getCo2();
                boolean survivorUnconscious = false; // 임시

                String prompt = PromptBuilder.buildSevenKeyFewShotPrompt(
                        phase,
                        state,
                        co2,                  // (기존 gas 인자 자리에 co2 전달)
                        true,                 // visionPerson
                        hasHumanLikeSpeech,
                        survivorUnconscious
                );

                // 중복 호출 방지
                lastLlmCallAtMs.set(now);

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
                if (!survivorSpeech.isBlank()) {
                    JsonObject toRobot = new JsonObject();
                    toRobot.addProperty("type", "TTS");
                    toRobot.addProperty("text", survivorSpeech);
                    robotServer.sendToRobot(toRobot.toString());
                }

                // ====== GUI로 전송 (6001) ======
                if (!guiMessage.isBlank()) {
                    JsonObject toGui = new JsonObject();
                    toGui.addProperty("type", "GUI_MESSAGE");
                    toGui.addProperty("text", guiMessage);
                    guiServer.sendToGui(toGui.toString());
                }

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