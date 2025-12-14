package org.example.mock;

import java.util.List;

public class MockScenario {

    /** 1) 의식 생존자 확정 + 고위험(HIGH): CO2/PM 높고 PIR+STT로 확정 */
    public static List<String> scenarioSurvivorNearHigh() {
        return List.of(
                "{\"type\":\"SENSOR\",\"name\":\"FLAME\",\"value\":0.2}",
                "{\"type\":\"SENSOR\",\"name\":\"DUST\",\"pm25\":160,\"pm10\":200}",
                "{\"type\":\"SENSOR\",\"name\":\"CO2\",\"value\":2400}",
                "{\"type\":\"SENSOR\",\"name\":\"PIR\",\"detected\":true}",
                "{\"type\":\"STT\",\"text\":\"살려주세요\"}"
        );
    }

    /** 2) 화재 위험 치명(CRITICAL) + 생존자 징후 없음: flame만으로 CRITICAL 만들기 */
    public static List<String> scenarioFireCriticalNoSurvivor() {
        return List.of(
                "{\"type\":\"SENSOR\",\"name\":\"FLAME\",\"value\":0.9}",
                "{\"type\":\"SENSOR\",\"name\":\"DUST\",\"pm25\":120,\"pm10\":180}",
                "{\"type\":\"SENSOR\",\"name\":\"CO2\",\"value\":1600}",
                "{\"type\":\"SENSOR\",\"name\":\"PIR\",\"detected\":false}"
        );
    }

    /** 3) 질식 위험 치명(CRITICAL) + 사람 가능(POSSIBLE): PIR만 true, STT 없음 */
    public static List<String> scenarioCO2CriticalPossible() {
        return List.of(
                "{\"type\":\"SENSOR\",\"name\":\"FLAME\",\"value\":0.1}",
                "{\"type\":\"SENSOR\",\"name\":\"CO2\",\"value\":3400}",
                "{\"type\":\"SENSOR\",\"name\":\"DUST\",\"pm25\":140,\"pm10\":190}",
                "{\"type\":\"SENSOR\",\"name\":\"PIR\",\"detected\":true}"
        );
    }

    /** 4) 안전(LOW) + 생존자 없음: 정상 수색 */
    public static List<String> scenarioSafeSearching() {
        return List.of(
                "{\"type\":\"SENSOR\",\"name\":\"FLAME\",\"value\":0.0}",
                "{\"type\":\"SENSOR\",\"name\":\"CO2\",\"value\":800}",
                "{\"type\":\"SENSOR\",\"name\":\"DUST\",\"pm25\":30,\"pm10\":50}",
                "{\"type\":\"SENSOR\",\"name\":\"PIR\",\"detected\":false}",
                "{\"type\":\"STT\",\"text\":\"\"}"
        );
    }

    /** 5) '무의식' 테스트용: STT 없음 + PIR true + (선택) 초음파로 근접감지 */
    public static List<String> scenarioUnconsciousLike() {
        return List.of(
                "{\"type\":\"SENSOR\",\"name\":\"FLAME\",\"value\":0.1}",
                "{\"type\":\"SENSOR\",\"name\":\"CO2\",\"value\":2200}",
                "{\"type\":\"SENSOR\",\"name\":\"DUST\",\"pm25\":180,\"pm10\":240}",
                "{\"type\":\"SENSOR\",\"name\":\"PIR\",\"detected\":true}"
                // 무의식 확정은 현재 SensorState에 'is_unconscious'가 없어서
                // 지금 로직에서는 "POSSIBLE"로 떨어지는 게 정상이다.
        );
    }
}
