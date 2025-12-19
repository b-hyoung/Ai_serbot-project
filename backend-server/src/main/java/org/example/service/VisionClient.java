package org.example.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;

import java.time.Duration;

public class VisionClient {
    private final OkHttpClient client = new OkHttpClient.Builder()
            .callTimeout(Duration.ofSeconds(5))
            .build();

    private final String baseUrl;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public VisionClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }
    

    public JsonObject infer(String absoluteImagePath, double conf) throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("path", absoluteImagePath);
        req.addProperty("conf", conf);

        String bodyStr = req.toString();
        Request request = new Request.Builder()
                .url(baseUrl + "/infer")
                .post(RequestBody.create(bodyStr, JSON))
                .build();

        try (Response resp = client.newCall(request).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "";
            System.out.println("🧠 YOLO RESP " + resp.code() + " = " + respBody);

            if (resp.code() != 200) {
                throw new RuntimeException("YOLO HTTP " + resp.code() + " body=" + respBody);
            }
            return JsonParser.parseString(respBody).getAsJsonObject();
        }
    }

    

}