package org.example.service;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class DecisionLogRepository {

    private final Path baseDir = Paths.get("./data/logs/decision");

    public void append(String trigger, String rawDecisionJson) {
        try {
            Files.createDirectories(baseDir);

            String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            Path file = baseDir.resolve(day + ".jsonl");

            JsonObject line = new JsonObject();
            line.addProperty("type", "LLM");
            line.addProperty("ts", System.currentTimeMillis());
            line.addProperty("trigger", trigger);
            line.addProperty("raw", rawDecisionJson);

            Files.writeString(
                    file,
                    line.toString() + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            System.out.println("⚠ decision log append failed: " + e.getMessage());
        }
    }
}