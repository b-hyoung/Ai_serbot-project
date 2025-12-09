package org.example.agent;

import org.example.agent.model.AgentInput;
import org.example.agent.model.HazardLevel;

/* 센서데이터 + STT를 기반으로 행동 JSON을 받기위한 프롬프트 */
public class PromptBuilder {

    public static String build(AgentInput in, HazardLevel hazardLevel) {
        return """
                너는 재난 구조 로봇의 행동을 결정하는 AI 에이전트이다.
                아래는 현재 로봇의 상태와 센서, 음성 정보이다:

                {
                  "phase": "%s",
                  "sensor": {
                    "flame": %.2f,
                    "co2": %.2f,
                    "pm25": %.2f,
                    "pm10": %.2f,
                    "gas": %.2f
                  },
                  "detection": {
                    "pir": %s,
                    "vision_person": %s
                  },
                  "audio": {
                    "recent_stt": "%s",
                    "last_stt": "%s",
                    "has_human_like_speech": %s
                  },
                  "precomputed_hazard_level": "%s"
                }

                위 정보를 바탕으로 로봇의 행동과,
                관제(구조대 오퍼레이터)가 취해야 할 대응까지 함께 결정하라.

                규칙:
                - voice_instruction: 생존자(피구조자)에게 들려줄 한국어 음성 안내 문장
                - gui_message: 관제 화면에 표시할 한국어 문장 (오퍼레이터를 위한 요약 + 권장 액션)
                  예시:
                  - "위험도 HIGH, 생존자 의식 있음. 구조대를 즉시 호출하고 해당 구역을 붉은색으로 표시하십시오."
                  - "위험도 LOW, 생존자 이동 가능. 출구 방향 경로를 확보하고 로봇의 안내를 따라 이동시키십시오."

                출력 형식 제약:
                - 출력은 **JSON 객체 한 개만** 포함해야 한다.
                - JSON 바깥의 설명, 문장, 코드블록, 공백 줄을 절대 넣지 마라.
                - true/false는 따옴표 없이 불리언으로 작성하라.
                - 문자열 값에는 반드시 큰따옴표(")를 사용하라.
                - phase, hazard_level, survivor_state 값은 아래 목록 중 하나만 사용하라.

                출력 JSON 필드 규칙:
                - "phase": "SEARCHING" | "AUDIO_CONTACT" | "CONFIRMED_CONTACT" | "RESCUE_GUIDE"
                - "hazard_level": "LOW" | "MEDIUM" | "HIGH" | "CRITICAL"
                - "survivor_state": "NONE" | "POSSIBLE" | "CONSCIOUS" | "UNCONSCIOUS" | "UNSURE"
                - "robot_action": 로봇이 무엇을 해야 하는지 요약한 영어 대문자 코드 (예: "GUIDE_SURVIVOR", "STAY_AND_WAIT")
                - "voice_instruction": 생존자에게 들려줄 한국어 안내 문장
                - "gui_message": 관제(오퍼레이터)에게 보여줄 한국어 문장 (상황 요약 + 추가 액션 제안)
                - "need_rescue_team": 구조대 추가 투입이 필요한 경우 true, 아니면 false
                
                전체 출력은 아래와 같은 **하나의 JSON 객체**만 포함해야 한다.
                
                {
                  "phase": "...",
                  "hazard_level": "...",
                  "survivor_state": "...",
                  "robot_action": "...",
                  "voice_instruction": "...",
                  "gui_message": "...",
                  "need_rescue_team": true
                }
                """.formatted(
                in.getPhase().name(),
                in.getFlame(), in.getCo2(), in.getPm25(), in.getPm10(), in.getGas(),
                in.isPir(), in.isVisionPerson(),
                in.getRecentStt().toString(),   // "[...]" 형태 문자열로 넣음
                in.getLastStt(),
                in.isHasHumanLikeSpeech(),
                hazardLevel.name()
        );
    }
}
