package org.example.service;

import org.example.state.SensorState;
/* 센서데이터를 통해 위험도(risk)를 출력하기위한 프롬프트 */
public class PromptBuilder {

    public static String buildRiskPrompt(SensorState s) {
        return """
                너는 화재 구조 로봇의 위험 판단 AI 에이전트이다.
                아래는 현재 센서 상태이다:
                {
                  "flame": %s,
                  "co2": %s,
                  "pm25": %s,
                  "pm10": %s,
                  "pir": %s,
                  "ultrasonic": %s,
                  "last_stt": "%s"
                }
                
                반드시 **아래 JSON 형식 그대로** 출력하라.
                설명, 텍스트, 코드블록, 자연어 문장은 절대 포함하지 마라.
                정확히 하나의 JSON 객체만 출력해야 한다.
                한국어로 번역해서 값을 받아줘
                {
                  "survivor_probability": number,        // 0~1 사이의 실수값
                  "consciousness": "CONSCIOUS" | "UNCONSCIOUS" | "UNKNOWN",
                  "environment_risk": "LOW" | "MEDIUM" | "HIGH" | "CRITICAL",
                  "priority": number,                    // 1~5
                  "reason": "string"                     // 판단 근거
                }
                """;
    }
}

