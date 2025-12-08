package org.example.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.state.SensorState;

public class StateUpdater {

    public static void applyJson(String line, SensorState state) {

        JsonObject obj = JsonParser.parseString(line).getAsJsonObject();
        String type = obj.get("type").getAsString();

        switch (type) {
            case "SENSOR" -> {
                String name = obj.get("name").getAsString();

                switch (name) {
                    case "FLAME" -> state.flame = obj.get("value").getAsDouble();
                    case "CO2" -> state.co2 = obj.get("value").getAsDouble();
                    case "DUST" -> {
                        state.pm25 = obj.get("pm25").getAsDouble();
                        state.pm10 = obj.get("pm10").getAsDouble();
                    }
                    case "PIR" -> state.pir = obj.get("detected").getAsBoolean();
                    case "ULTRASONIC" -> state.ultrasonic = obj.get("distance").getAsDouble();
                }
            }

            case "STT" -> {
                state.lastStt = obj.get("text").getAsString();
                state.lastSttTime = System.currentTimeMillis();
            }
        }
    }
}
