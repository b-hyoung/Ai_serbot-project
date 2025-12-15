package org.example.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.state.SensorState;

import java.time.LocalDateTime;

public class StateUpdater {

    public static void applyJson(String line, SensorState state) {

        JsonObject obj = JsonParser.parseString(line).getAsJsonObject();
        String type = obj.get("type").getAsString();

        switch (type) {
            case "SENSOR" -> {
                String name = obj.get("name").getAsString();

                switch (name) {
                    case "FLAME" -> state.setFlame(obj.get("value").getAsDouble());
                    case "CO2" -> state.setCo2(obj.get("value").getAsDouble());
                    case "DUST" -> {
                        state.setPm25(obj.get("pm25").getAsDouble());
                        state.setPm10(obj.get("pm10").getAsDouble());
                    }
                    case "PIR" -> state.setPir(obj.get("detected").getAsBoolean());
                    case "ULTRASONIC" -> state.setUltrasonic(obj.get("distance").getAsDouble());
                }
            }

            case "STT" -> {
                state.setLastStt(obj.get("text").getAsString());
            }
            case "VISION" -> {
                // yolo 객체 안에 person/conf가 있다고 가정
                if (obj.has("yolo") && obj.get("yolo").isJsonObject()) {
                    JsonObject yolo = obj.getAsJsonObject("yolo");

                    if (yolo.has("person")) state.setVisionPerson(yolo.get("person").getAsBoolean());
                    if (yolo.has("best") && yolo.get("best").isJsonObject()) {
                        JsonObject best = yolo.getAsJsonObject("best");
                        if (best.has("conf")) state.setVisionConf(best.get("conf").getAsDouble());
                    }
                    if (obj.has("ts")) state.setVisionTs(obj.get("ts").getAsLong());
                }
            }
        }
    }
}
