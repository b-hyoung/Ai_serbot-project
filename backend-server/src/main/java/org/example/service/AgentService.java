package org.example.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.IOException;
import java.time.Duration;

public class AgentService {

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofMinutes(1))
            .writeTimeout(Duration.ofMinutes(1))
            .readTimeout(Duration.ofMinutes(1))
            .build();

    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");
    private static final Gson gson = new Gson();

    public static String ask(String prompt) {

        JsonObject req = new JsonObject();
        req.addProperty("model", "robot-agent");
        req.addProperty("prompt", prompt);
        req.addProperty("stream", false);

        String bodyStr = gson.toJson(req);
        RequestBody body = RequestBody.create(bodyStr, JSON);

        Request request = new Request.Builder()
                .url(OLLAMA_URL)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("HTTP 실패: " + response.code());
            }

            String resBody = response.body().string();
            JsonObject json = gson.fromJson(resBody, JsonObject.class);

            // ✅ 1) raw 응답 문자열
            String raw = json.get("response").getAsString();

            // ✅ 2) raw에서 첫 번째 JSON 객체만 추출해서 반환
            return extractFirstJsonObject(raw);

        } catch (IOException e) {
            throw new RuntimeException("🔥 Ollama 요청 실패: " + e.getMessage(), e);
        }
    }

    /**
     * LLM이 설명/코드블록/여러 JSON을 섞어도, 첫 번째 완전한 JSON 객체({ ... })만 뽑는다.
     * - 문자열 내부의 중괄호는 무시(따옴표 처리)
     */
    private static String extractFirstJsonObject(String text) {
        if (text == null) throw new IllegalArgumentException("LLM response is null");

        int start = -1;
        int depth = 0;
        boolean inString = false;
        boolean escape = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (start == -1) {
                if (c == '{') {
                    start = i;
                    depth = 1;
                }
                continue;
            }

            if (escape) {
                escape = false;
                continue;
            }

            if (c == '\\') {
                escape = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (!inString) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return text.substring(start, i + 1).trim();
                    }
                }
            }
        }

        throw new IllegalStateException("❌ LLM 응답에서 JSON 객체를 찾지 못함. raw=" + text);
    }
}
