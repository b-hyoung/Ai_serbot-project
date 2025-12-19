package org.example.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class EnvLoader {
    private static final Map<String, String> ENV = new HashMap<>();

    static {
        try {
            for (String line : Files.readAllLines(Path.of(".env"))) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    ENV.put(parts[0].trim(), parts[1].trim());
                }
            }
        } catch (IOException e) {
            System.err.println(".env 파일을 읽을 수 없습니다: " + e.getMessage());
        }
    }

    public static String get(String key) {
        return ENV.get(key);
    }
}
