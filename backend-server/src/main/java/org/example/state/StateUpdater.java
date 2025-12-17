package org.example.state;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class StateUpdater {

    public static void applyJson(String line, SensorState state) {
        JsonObject obj;
        try {
            obj = JsonParser.parseString(line).getAsJsonObject();
        } catch (Exception e) {
            return; // JSON 아니면 무시
        }

        if (!obj.has("type") || obj.get("type").isJsonNull()) return;
        String type = obj.get("type").getAsString();

        switch (type) {
            case "SENSOR" -> applySensor(obj, state);
            case "STT" -> {
                if (obj.has("text") && !obj.get("text").isJsonNull()) {
                    state.setLastStt(obj.get("text").getAsString());
                }
            }
            case "VISION" -> applyVision(obj, state);
            default -> {
                // ignore
            }
        }
    }

    private static void applySensor(JsonObject obj, SensorState state) {

        // ===== 1) 기존 단일 센서 포맷 =====
        if (obj.has("name") && !obj.get("name").isJsonNull()) {
            String name = obj.get("name").getAsString();

            switch (name) {
                case "FLAME" -> {
                    if (obj.has("value") && !obj.get("value").isJsonNull()) {
                        state.setFlame(obj.get("value").getAsDouble());
                    }
                }
                case "CO2" -> {
                    if (obj.has("value") && !obj.get("value").isJsonNull()) {
                        state.setCo2(obj.get("value").getAsDouble());
                    }
                }
                case "DUST" -> {
                    Double pm25 = null, pm10 = null;
                    if (obj.has("pm25") && !obj.get("pm25").isJsonNull())
                        pm25 = obj.get("pm25").getAsDouble();
                    if (obj.has("pm10") && !obj.get("pm10").isJsonNull())
                        pm10 = obj.get("pm10").getAsDouble();

                    // ✅ 둘 다 null이면 덮어쓰지 않음
                    if (pm25 != null || pm10 != null) {
                        state.setDust(pm25, pm10, "ROBOT");
                    }
                }
                case "PIR" -> {
                    if (obj.has("detected") && !obj.get("detected").isJsonNull()) {
                        state.setPir(obj.get("detected").getAsBoolean());
                    }
                }
                case "ULTRASONIC" -> {
                    if (obj.has("distance") && !obj.get("distance").isJsonNull()) {
                        state.setUltrasonic(obj.get("distance").getAsDouble());
                    }
                }
            }
            return;
        }

        // ===== 2) 통합 SENSOR 포맷 =====
        // {type:SENSOR, fire, co2, dust:{pm25,pm10}}

        // fire → flame(double) 임시 매핑
        if (obj.has("fire") && !obj.get("fire").isJsonNull()) {
            boolean fire = obj.get("fire").getAsBoolean();
            state.setFlame(fire ? 1.0 : 0.0);
        }

        // co2 단일화 (우선순위)
        if (obj.has("co2") && !obj.get("co2").isJsonNull()) {
            state.setCo2(obj.get("co2").getAsDouble());
        } else if (obj.has("co2_ppm") && !obj.get("co2_ppm").isJsonNull()) {
            state.setCo2(obj.get("co2_ppm").getAsDouble());
        } else if (obj.has("gas") && !obj.get("gas").isJsonNull()) {
            state.setCo2(obj.get("gas").getAsDouble());
        }

        // dust 묶음
        if (obj.has("dust") && obj.get("dust").isJsonObject()) {
            JsonObject dust = obj.getAsJsonObject("dust");
            Double pm25 = null, pm10 = null;

            if (dust.has("pm25") && !dust.get("pm25").isJsonNull())
                pm25 = dust.get("pm25").getAsDouble();
            if (dust.has("pm10") && !dust.get("pm10").isJsonNull())
                pm10 = dust.get("pm10").getAsDouble();

            // ✅ 둘 다 null이면 업데이트 안 함
            if (pm25 != null || pm10 != null) {
                state.setDust(pm25, pm10, "ROBOT");
            }
        }
    }

    private static void applyVision(JsonObject obj, SensorState state) {

        if (!obj.has("yolo") || !obj.get("yolo").isJsonObject()) return;
        JsonObject yolo = obj.getAsJsonObject("yolo");

        if (yolo.has("person") && !yolo.get("person").isJsonNull()) {
            state.setVisionPerson(yolo.get("person").getAsBoolean());
        }

        if (yolo.has("best") && yolo.get("best").isJsonObject()) {
            JsonObject best = yolo.getAsJsonObject("best");
            if (best.has("conf") && !best.get("conf").isJsonNull()) {
                state.setVisionConf(best.get("conf").getAsDouble());
            }
        }
    }
}