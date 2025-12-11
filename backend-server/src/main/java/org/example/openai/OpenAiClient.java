package org.example.openai;

import org.example.config.EnvLoader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class OpenAiClient {

    private static final String API_KEY = EnvLoader.get("OPENAI_API_KEY");
    private static final String OPENAI_URL = "https://api.openai.com/v1/responses";
    public static String callOpenAi(String prompt) {
    System.out.println("API_KEY = " + API_KEY);
        try {
            String jsonBody = """
            {
              "model": "gpt-4.1-mini",
              "input": %s
            }
            """.formatted(quoteJson(prompt));
            System.out.println("jsonBody = " + jsonBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OPENAI_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + API_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            return response.body();

        } catch (Exception e) {
            throw new RuntimeException("OpenAI API 호출 실패", e);
        }
    }
    private static String quoteJson(String text) {
        if (text == null) return "\"\"";
        String escaped = text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        return "\"" + escaped + "\"";
    }
}
