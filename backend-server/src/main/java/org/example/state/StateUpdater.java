package org.example.state;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class StateUpdater {

    public static void applyJson(String line, SensorState state) {
        JsonObject obj;
        try {
            obj = JsonParser.parseString(line).getAsJsonObject();
        } catch (Exception e) {
            // JSON 아니면 무시
            return;
        }

        if (!obj.has("type")) return;
        String type = obj.get("type").getAsString();

        switch (type) {
            case "SENSOR" -> applySensor(obj, state);
            case "STT" -> {
                if (obj.has("text")) state.setLastStt(obj.get("text").getAsString());
            }
            case "VISION" -> applyVision(obj, state);
        }
    }

    private static void applySensor(JsonObject obj, SensorState state) {
        // ✅ 1) 기존 포맷: {type:SENSOR, name:CO2, value:...}
        if (obj.has("name")) {
            String name = obj.get("name").getAsString();
            switch (name) {
                case "FLAME" -> { if (obj.has("value")) state.setFlame(obj.get("value").getAsDouble()); }
                case "CO2"   -> { if (obj.has("value")) state.setCo2(obj.get("value").getAsDouble()); }
                case "DUST"  -> {
                    if (obj.has("pm25")) state.setPm25(obj.get("pm25").getAsDouble());
                    if (obj.has("pm10")) state.setPm10(obj.get("pm10").getAsDouble());
                }
                case "PIR" -> { if (obj.has("detected")) state.setPir(obj.get("detected").getAsBoolean()); }
                case "ULTRASONIC" -> { if (obj.has("distance")) state.setUltrasonic(obj.get("distance").getAsDouble()); }
            }
            return;
        }

        // ✅ 2) 새 포맷(로봇 통합): {type:SENSOR, fire:bool, gas:number, dust:{pm25,pm10}, ts:...}
        if (obj.has("fire") && !obj.get("fire").isJsonNull()) {
            // 너 SensorState가 flame을 double로 받는 구조라면 불리언을 0/1로 매핑(임시)
            // 가능하면 SensorState에 fire(boolean) 필드 추가가 맞다.
            boolean fire = obj.get("fire").getAsBoolean();
            state.setFlame(fire ? 1.0 : 0.0);
        }

        // gas = co2_ppm으로 바꾸는 게 맞지만, 지금은 네 로봇이 gas로 보냄
        if (obj.has("co2_ppm") && !obj.get("co2_ppm").isJsonNull()) {
            state.setCo2(obj.get("co2_ppm").getAsDouble());
        } else if (obj.has("gas") && !obj.get("gas").isJsonNull()) {
            state.setCo2(obj.get("gas").getAsDouble());
        }

        if (obj.has("dust") && obj.get("dust").isJsonObject()) {
            JsonObject dust = obj.getAsJsonObject("dust");
            if (dust.has("pm25") && !dust.get("pm25").isJsonNull()) state.setPm25(dust.get("pm25").getAsDouble());
            if (dust.has("pm10") && !dust.get("pm10").isJsonNull()) state.setPm10(dust.get("pm10").getAsDouble());
        }
    }

    private static void applyVision(JsonObject obj, SensorState state) {
        if (obj.has("yolo") && obj.get("yolo").isJsonObject()) {
            JsonObject yolo = obj.getAsJsonObject("yolo");

            if (yolo.has("person")) state.setVisionPerson(yolo.get("person").getAsBoolean());

            if (yolo.has("best") && yolo.get("best").isJsonObject()) {
                JsonObject best = yolo.getAsJsonObject("best");
                if (best.has("conf")) state.setVisionConf(best.get("conf").getAsDouble());
            }
        }
        if (obj.has("ts")) state.setVisionTs(obj.get("ts").getAsLong());
    }
}