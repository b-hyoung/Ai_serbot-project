package org.example.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class LlmResponseParser {

    public static JsonObject parse(String raw) {
        // 혹시 앞/뒤에 공백/개행 섞여도 안전하게
        String s = raw == null ? "" : raw.trim();
        return JsonParser.parseString(s).getAsJsonObject();
    }

    public static String getString(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return "";
        return o.get(key).getAsString();
    }
}