package org.example.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.IOException;

public class AgentService {

    private static final OkHttpClient client = new OkHttpClient();
    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");
    private static final Gson gson = new Gson();

    public static String ask(String prompt) {

        // 1) JSON 바디를 객체로 만들고
        JsonObject req = new JsonObject();
        req.addProperty("model", "llama3.1");
        req.addProperty("prompt", prompt);
        req.addProperty("stream", false);  // 한 번에 응답 받기

        String bodyStr = gson.toJson(req);  // 2) Gson이 알아서 \n, ", \ 이런 거 전부 이스케이프해줌

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
            String answer = json.get("response").getAsString();
            return answer;  // 일단은 raw로 반환, 나중에 "response" 필드 파싱해도 됨
        } catch (IOException e) {
            throw new RuntimeException("🔥 Ollama 요청 실패: " + e.getMessage(), e);
        }
    }
}
