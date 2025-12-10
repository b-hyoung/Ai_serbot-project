package org.example.mock;

import java.util.List;

public class MockScenario {

    public static List<String> scenarioSurvivorNear() {
        /* 임시 값 문자열앞에 이스케잎문자 필수 */
        return List.of(
                "{\"type\":\"SENSOR\",\"name\":\"FLAME\",\"value\":0.2}",
                "{\"type\":\"SENSOR\",\"name\":\"DUST\",\"pm25\":160,\"pm10\":200}",
                "{\"type\":\"SENSOR\",\"name\":\"CO2\",\"value\":2400}",
                "{\"type\":\"SENSOR\",\"name\":\"PIR\",\"detected\":true}",
                "{\"type\":\"STT\",\"text\":\"살려주세요\"}"
        );
    }

    public static List<String> scenarioFireOnly() {
        return List.of(
                "{\"type\":\"SENSOR\",\"name\":\"FLAME\",\"value\":0.85}",
                "{\"type\":\"SENSOR\",\"name\":\"DUST\",\"pm25\":210,\"pm10\":280}",
                "{\"type\":\"SENSOR\",\"name\":\"CO2\",\"value\":1800}"
        );
    }

    public static List<String> scenarioUnconsciousVictim() {
        return List.of(
                "{\"type\":\"SENSOR\",\"name\":\"FLAME\",\"value\":0.1}",
                "{\"type\":\"SENSOR\",\"name\":\"CO2\",\"value\":3500}",
                "{\"type\":\"SENSOR\",\"name\":\"DUST\",\"pm25\":200,\"pm10\":260}",
                "{\"type\":\"SENSOR\",\"name\":\"PIR\",\"detected\":true}"
        );
    }
}
