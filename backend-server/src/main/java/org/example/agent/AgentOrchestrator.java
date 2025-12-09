package org.example.agent;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.example.agent.audio.evaluator.AudioHeuristic;
import org.example.agent.audio.evaluator.HazardEvaluator;
import org.example.agent.model.AgentInput;
import org.example.agent.model.AgentOutput;
import org.example.agent.model.HazardLevel;
import org.example.agent.model.MissionPhase;
import org.example.openai.OpenAiClient;
import org.example.openai.OpenAiParser;
import org.example.state.SensorState;

import java.io.IOException;
import java.util.List;

public class AgentOrchestrator {

    /* STT */
    private final HazardEvaluator hazardEvaluator = new HazardEvaluator(); // 값에 따른 위험도 분류
    private final AudioHeuristic audioHeuristic = new AudioHeuristic();

    private final OkHttpClient client = new OkHttpClient();
    // TODO: OpenAI API 클라이언트 주입 예정


    public AgentOutput process(SensorState sensorState, List<String> recentStt, MissionPhase currentPhase) {
        // 1. 센서 기반 Hazard 계산 (서버 자체 로직)
        HazardLevel hazardLevel = hazardEvaluator.evaluate(
                sensorState.getFlame(),
                sensorState.getCo2(),
                sensorState.getPm25()
        );

        // 2. 오디오 값 읽어와서 체크하기
        String lastStt = recentStt.isEmpty() ? "" : recentStt.get(recentStt.size() - 1);
        boolean hasHumanLikeSpeech = audioHeuristic.isHumanLikeSpeech(lastStt);

        // 3. AgentInput 만들기
        AgentInput input = new AgentInput();
        input.setPhase(currentPhase);
        input.setFlame(sensorState.getFlame());
        input.setCo2(sensorState.getCo2());
        input.setPm25(sensorState.getPm25());
        input.setPm10(sensorState.getPm10());
        input.setGas(sensorState.getGas());
        input.setPir(sensorState.isPir());
        input.setVisionPerson(sensorState.isVisionPerson());
        input.setRecentStt(recentStt);
        input.setLastStt(lastStt);
        input.setHasHumanLikeSpeech(hasHumanLikeSpeech);

        // 4. LLM 프롬프트 문자열 생성
        String prompt = PromptBuilder.build(input, hazardLevel);

        // 5.1 OpenAI API 호출 (pseudo)
         String llmJson = callOpenAi(prompt); // TODO: 실제 구현
        // 5.2 ollama를 통한 localAI 호출
//        String llmJson = callOllama(prompt);

        // 6. JSON → AgentOutput 파싱
        AgentOutput output = parseAgentOutput(llmJson);

        return output;
    }

    /* open ai 연동 */
    private String callOpenAi(String prompt) {
        String raw = OpenAiClient.callOpenAi(prompt); // 실제 호출
        String json = OpenAiParser.extractDecisionJson(raw);
        System.out.println("JSON : " + json);
        return json;
    }


    /* llama 값 처리 */
    private String callOllama(String prompt) {
        String url = "http://localhost:11434/api/chat";

        MediaType JSON = MediaType.parse("application/json; charset=utf-8");

        // Ollama chat API 요청 바디
        String requestBodyJson = """
        {
          "model": "llama3.1:latest",
          "messages": [
            {
              "role": "user",
              "content": %s
            }
          ],
          "stream": false
        }
        """.formatted(quoteJson(prompt)); // content를 안전하게 JSON 문자열로 인코딩

        RequestBody body = RequestBody.create(JSON, requestBodyJson);

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected response from Ollama: " + response);
            }

            String responseStr = response.body().string();
            // Ollama 응답에서 message.content만 뽑기
            JsonObject root = JsonParser.parseString(responseStr).getAsJsonObject();
            JsonObject message = root.getAsJsonObject("message");
            String content = message.get("content").getAsString();

            // content 안에는 우리가 프롬프트에서 요구한 "JSON만" 있어야 한다.
            return content;

        } catch (Exception e) {
            e.printStackTrace();
            // 실패 시 안전하게 fallback
            return """
            {
              "phase": "SEARCHING",
              "hazard_level": "LOW",
              "survivor_state": "NONE",
              "robot_action": "NO_ACTION",
              "voice_instruction": "에이전트 호출에 실패했습니다.",
              "need_rescue_team": false
            }
            """;
        }
    }

    /**
     * JSON 문자열 안에 prompt를 넣을 때, 따옴표/줄바꿈 등을 안전하게 escape 하기 위한 헬퍼.
     */
    private String quoteJson(String text) {
        // 간단 escape: 큰따옴표와 역슬래시, 줄바꿈 처리
        if (text == null) return "\"\"";
        String escaped = text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        return "\"" + escaped + "\"";
    }

    private AgentOutput parseAgentOutput(String json) {
        // Gson or Jackson으로 파싱
        // TODO: 실제 구현
        Gson gson = new Gson();
        return gson.fromJson(json, AgentOutput.class);
    }
}

