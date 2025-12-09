package org.example.openai;

import com.google.gson.*;

public class OpenAiParser {

    private static final Gson gson = new Gson();

    // OpenAI 전체 응답 JSON에서 모델이 출력한 text(JSON 문자열)를 꺼내는 함수
    public static String extractDecisionJson(String openAiJson) {
        JsonObject root = JsonParser.parseString(openAiJson).getAsJsonObject();

        // 1) 에러 응답 방어
        if (root.has("error") && !root.get("error").isJsonNull()) {
            JsonObject err = root.getAsJsonObject("error");
            String msg = err.get("message").getAsString();
            String code = err.get("code").isJsonNull() ? null : err.get("code").getAsString();
            throw new IllegalStateException("OpenAI 에러 응답: code=" + code + ", message=" + msg);
        }

        // 2) output[0].content[0].text 꺼내기
        JsonArray output = root.getAsJsonArray("output");
        if (output == null || output.size() == 0) {
            throw new IllegalStateException("OpenAI 응답에 output이 없습니다: " + openAiJson);
        }

        JsonObject msg0 = output.get(0).getAsJsonObject();
        JsonArray content = msg0.getAsJsonArray("content");
        if (content == null || content.size() == 0) {
            throw new IllegalStateException("OpenAI 응답에 content가 없습니다: " + openAiJson);
        }

        JsonObject c0 = content.get(0).getAsJsonObject();
        String text = c0.getAsJsonPrimitive("text").getAsString();

        return text; // 이게 네가 원하는 JSON 문자열
    }
}
