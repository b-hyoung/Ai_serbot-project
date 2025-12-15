package org.example.service;

import org.example.state.SensorState;

/**
 * PromptBuilder
 * - 2키 메시지(JSON) 프롬프트 (voice_to_survivor, gui_message)
 * - 2키 + gui 고정(voice만 생성) 프롬프트
 * - 7키 Few-shot(Output 7 keys) 프롬프트
 *
 * ⚠️ 유틸(n/b/q)은 이 클래스 내부에서만 사용한다.
 */
public class PromptBuilder {

    /* =======================
       LLM 호출 조건(최소)
       ======================= */
    public static boolean shouldCallLlm(
            SurvivorEvidence evidence,
            HazardLevel hazardLevel,
            long nowMs,
            long lastLlmCallAtMs,
            long cooldownMs
    ) {
        if (hazardLevel == HazardLevel.CRITICAL) return false;
        if (nowMs - lastLlmCallAtMs < cooldownMs) return false;
        return evidence == SurvivorEvidence.CONFIRMED;
    }

    /* =========================================================
       1) (권장) 2키 메시지 생성 프롬프트: voice + gui 둘 다 생성
       ========================================================= */
    public static String buildMessagePrompt(
            Phase phase,
            HazardLevel hazardLevel,
            RobotAction robotAction,
            SensorState s
    ) {
        String inputJson = buildInputJsonMinimal(phase, hazardLevel, robotAction, s);

        return ""
                + "역할: 재난 구조 로봇 메시지 생성기\n"
                + "중요: 입력 JSON을 절대 재출력하지 마라. 분석/설명/코드블록/마크다운 금지.\n"
                + "출력은 반드시 JSON 객체 1개만.\n"
                + "키는 정확히 2개만: voice_to_survivor, gui_message (추가 키 금지)\n"
                + "언어 규칙: 영어 금지. 반드시 한국어로만 작성.\n"
                + "내용 규칙:\n"
                + "- gui_message는 관제(구조요원)용이다. 사실/수치/권고 행동을 포함해 1~2문장으로 작성.\n"
                + "- gui_message는 절대 빈 문자열 금지.\n"
                + "- hazard_level이 CRITICAL이면 voice_to_survivor는 빈 문자열로 한다.\n"
                + "- hazard_level이 CRITICAL이 아니면 voice_to_survivor는 생존자에게 말할 1문장(안전지시/응답요청)로 작성하며 빈 문자열 금지.\n"
                + "\n"
                + "입력(JSON):\n"
                + inputJson + "\n"
                + "\n"
                + "출력(JSON):\n"
                + "{\"voice_to_survivor\":\"(한국어 1문장 또는 CRITICAL일 때 빈 문자열)\",\"gui_message\":\"(관제용 한국어 1~2문장)\"}";
    }

    /* ======================================================================
       2) (안정) voice만 생성 + gui는 코드에서 고정한 문장을 그대로 복사하게 강제
       ====================================================================== */
    public static String buildVoiceOnlyPrompt(
            Phase phase,
            HazardLevel hazardLevel,
            RobotAction robotAction,
            SensorState s,
            String guiMessageFixed
    ) {
        String inputJson = "{\n"
                + "  \"decision\": {\n"
                + "    \"phase\": \"" + phase + "\",\n"
                + "    \"hazard_level\": \"" + hazardLevel + "\",\n"
                + "    \"robot_action\": \"" + robotAction + "\"\n"
                + "  },\n"
                + "  \"sensors\": {\n"
                + "    \"flame\": " + n(s.getFlame()) + ",\n"
                + "    \"co2\": " + n(s.getCo2()) + ",\n"
                + "    \"pm25\": " + n(s.getPm25()) + ",\n"
                + "    \"pm10\": " + n(s.getPm10()) + ",\n"
                + "    \"pir\": " + b(s.getPir()) + "\n"
                + "  },\n"
                + "  \"audio\": {\n"
                + "    \"last_stt\": \"" + q(s.lastStt) + "\"\n"
                + "  },\n"
                + "  \"gui_message_fixed\": \"" + q(guiMessageFixed) + "\"\n"
                + "}";

        return ""
                + "역할: 재난 구조 로봇의 생존자 안내 문장 생성기\n"
                + "중요: 입력 JSON을 재출력하지 마라. 설명/코드블록/마크다운 금지.\n"
                + "출력 규칙:\n"
                + "1) 출력은 JSON 객체 1개만.\n"
                + "2) 키는 정확히 2개: voice_to_survivor, gui_message\n"
                + "3) gui_message는 입력의 gui_message_fixed 값을 그대로 복사해서 출력(수정 금지).\n"
                + "4) voice_to_survivor만 한국어로 1문장 생성.\n"
                + "5) 금지 단어: 해안, 바다, 선박 등 상황과 무관한 장소 언급 금지.\n"
                + "6) hazard_level이 CRITICAL이면 voice_to_survivor는 빈 문자열.\n"
                + "\n"
                + "입력:\n" + inputJson + "\n"
                + "출력:\n"
                + "{\"voice_to_survivor\":\"(한국어 1문장 또는 CRITICAL일 때 빈 문자열)\",\"gui_message\":\"" + q(guiMessageFixed) + "\"}";
    }

    /* =========================================================
       3) 7키 Few-shot 프롬프트 (파이썬 구조 재현용)
       - LLM이 행동까지 결정하도록 유도 (실험/비교용)
       ========================================================= */
    public static String buildSevenKeyFewShotPrompt(
            Phase phase,
            SensorState s,
            Double gas,                 // 없으면 null
            boolean visionPerson,       // 없으면 false
            boolean hasHumanLikeSpeech, // 임시 추정 가능
            boolean survivorUnconscious // 없으면 false
    ) {
        String inputJson =
                "{\n" +
                        "  \"phase\": \"" + phase + "\",\n" +
                        "  \"sensors\": {\n" +
                        "    \"flame\": " + n(s.flame) + ",\n" +
                        "    \"co2\": " + n(s.getCo2()) + ",\n" +
                        "    \"pm25\": " + n(s.getPm25()) + ",\n" +
                        "    \"pm10\": " + n(s.getPm10()) + ",\n" +
                        "    \"gas\": " + (gas == null ? "null" : gas) + ",\n" +
                        "    \"pir\": " + b(s.getPir()) + ",\n" +
                        "    \"vision_person\": " + (visionPerson ? "true" : "false") + "\n" +
                        "  },\n" +
                        "  \"audio\": {\n" +
                        "    \"recent_stt\": \"" + q(s.lastStt) + "\",\n" +
                        "    \"has_human_like_speech\": " + (hasHumanLikeSpeech ? "true" : "false") + "\n" +
                        "  },\n" +
                        "  \"survivor\": {\n" +
                        "    \"is_unconscious\": " + (survivorUnconscious ? "true" : "false") + "\n" +
                        "  }\n" +
                        "}";

        return ""
                + "너는 재난 현장에 투입된 구조 로봇을 제어하는, 침착하고 전문적인 AI 에이전트이다.\n"
                + "너의 임무는 센서 데이터를 분석하고, 로봇의 다음 행동을 결정하며, 인간 구조대 오퍼레이터 및 생존자와 명확하게 소통하는 것이다.\n"
                + "모든 응답은 간결하고 사실에 기반해야 한다.\n"
                + "\n"
                + "주어진 입력 정보(Input)에 대해, 반드시 지정된 7개의 키를 포함하는 JSON 객체(Output)를 생성해야 한다.\n"
                + "출력 규칙을 반드시 준수하라.\n"
                + "\n"
                + "규칙:\n"
                + "- 출력은 JSON 객체 1개만 포함해야 한다.\n"
                + "- JSON 바깥의 설명/문장/코드블록/마크다운을 절대 넣지 마라.\n"
                + "- 문자열은 반드시 큰따옴표(\") 사용.\n"
                + "- 반드시 한국어로 작성.\n"
                + "- 반드시 아래 7개 키를 모두 포함: phase, hazard_level, survivor_state, robot_action, gui_message, voice_instruction, survivor_speech\n"
                + "\n"
                + "### 예시 ###\n"
                + "Input:\n"
                + "{\n"
                + "  \"input\": {\n"
                + "    \"phase\": \"CONFIRMED_CONTACT\",\n"
                + "    \"sensors\": {\n"
                + "      \"flame\": 0.9,\n"
                + "      \"co2\": 2800,\n"
                + "      \"pm25\": 200,\n"
                + "      \"pm10\": 250,\n"
                + "      \"gas\": 0.8,\n"
                + "      \"pir\": true,\n"
                + "      \"vision_person\": true\n"
                + "    },\n"
                + "    \"audio\": {\n"
                + "      \"recent_stt\": \"살려주세요\",\n"
                + "      \"has_human_like_speech\": true\n"
                + "    },\n"
                + "    \"survivor\": {\n"
                + "      \"is_unconscious\": false\n"
                + "    }\n"
                + "  },\n"
                + "  \"output\": {\n"
                + "    \"phase\": \"CONFIRMED_CONTACT\",\n"
                + "    \"hazard_level\": \"HIGH\",\n"
                + "    \"survivor_state\": \"CONSCIOUS\",\n"
                + "    \"robot_action\": \"GUIDE_SURVIVOR\",\n"
                + "    \"gui_message\": \"[긴급] 화재 구역 / 의식 있는 생존자\",\n"
                + "    \"voice_instruction\": \"오퍼레이터님, 고위험 화재 구역에서 의식 있는 생존자를 발견했습니다. 대피 유도를 시작하겠습니다.\",\n"
                + "    \"survivor_speech\": \"저는 구조 로봇입니다. 이 구역은 위험하니 즉시 저를 따라 대피해야 합니다. 스스로 이동 가능하십니까?\"\n"
                + "  }\n"
                + "}\n"
                + "Output:\n"
                + "{\n"
                + "  \"phase\": \"CONFIRMED_CONTACT\",\n"
                + "  \"hazard_level\": \"HIGH\",\n"
                + "  \"survivor_state\": \"CONSCIOUS\",\n"
                + "  \"robot_action\": \"GUIDE_SURVIVOR\",\n"
                + "  \"gui_message\": \"[긴급] 화재 구역 / 의식 있는 생존자\",\n"
                + "  \"voice_instruction\": \"오퍼레이터님, 고위험 화재 구역에서 의식 있는 생존자를 발견했습니다. 대피 유도를 시작하겠습니다.\",\n"
                + "  \"survivor_speech\": \"저는 구조 로봇입니다. 이 구역은 위험하니 즉시 저를 따라 대피해야 합니다. 스스로 이동 가능하십니까?\"\n"
                + "}\n"
                + "\n"
                + "### 예시 ###\n"
                + "Input:\n"
                + "{\n"
                + "  \"input\": {\n"
                + "    \"phase\": \"SEARCHING\",\n"
                + "    \"sensors\": {\n"
                + "      \"flame\": 0.0,\n"
                + "      \"co2\": 900,\n"
                + "      \"pm25\": 40,\n"
                + "      \"pm10\": 60,\n"
                + "      \"gas\": 0.2,\n"
                + "      \"pir\": false,\n"
                + "      \"vision_person\": false\n"
                + "    },\n"
                + "    \"audio\": {\n"
                + "      \"recent_stt\": \"\",\n"
                + "      \"has_human_like_speech\": false\n"
                + "    },\n"
                + "    \"survivor\": {\n"
                + "      \"is_unconscious\": false\n"
                + "    }\n"
                + "  },\n"
                + "  \"output\": {\n"
                + "    \"phase\": \"SEARCHING\",\n"
                + "    \"hazard_level\": \"LOW\",\n"
                + "    \"survivor_state\": \"NONE\",\n"
                + "    \"robot_action\": \"SEARCH\",\n"
                + "    \"gui_message\": \"[정상] 안전 구역 / 수색 지속\",\n"
                + "    \"voice_instruction\": \"오퍼레이터님, 현재 구역은 안전합니다. 수색을 계속 진행하겠습니다.\",\n"
                + "    \"survivor_speech\": \"\"\n"
                + "  }\n"
                + "}\n"
                + "Output:\n"
                + "{\n"
                + "  \"phase\": \"SEARCHING\",\n"
                + "  \"hazard_level\": \"LOW\",\n"
                + "  \"survivor_state\": \"NONE\",\n"
                + "  \"robot_action\": \"SEARCH\",\n"
                + "  \"gui_message\": \"[정상] 안전 구역 / 수색 지속\",\n"
                + "  \"voice_instruction\": \"오퍼레이터님, 현재 구역은 안전합니다. 수색을 계속 진행하겠습니다.\",\n"
                + "  \"survivor_speech\": \"\"\n"
                + "}\n"
                + "\n"
                + "### 실제 임무 ###\n"
                + "Input:\n"
                + inputJson + "\n"
                + "Output:\n";
    }

    /* =======================
       입력 JSON(최소) 생성
       ======================= */
    private static String buildInputJsonMinimal(
            Phase phase,
            HazardLevel hazardLevel,
            RobotAction robotAction,
            SensorState s
    ) {
        return "{\n"
                + "  \"decision\": {\n"
                + "    \"phase\": \"" + phase + "\",\n"
                + "    \"hazard_level\": \"" + hazardLevel + "\",\n"
                + "    \"robot_action\": \"" + robotAction + "\"\n"
                + "  },\n"
                + "  \"sensors\": {\n"
                + "    \"flame\": " + n(s.flame) + ",\n"
                + "    \"co2\": " + n(s.getCo2()) + ",\n"
                + "    \"pm25\": " + n(s.getPm25()) + ",\n"
                + "    \"pm10\": " + n(s.getPm10()) + ",\n"
                + "    \"pir\": " + b(s.getPir()) + ",\n"
                + "    \"ultrasonic\": " + n(s.ultrasonic) + "\n"
                + "  },\n"
                + "  \"audio\": {\n"
                + "    \"last_stt\": \"" + q(s.lastStt) + "\",\n"
                + "    \"last_stt_time\": " + s.lastSttTime + "\n"
                + "  }\n"
                + "}";
    }

    /* =======================
       Enum 정의
       ======================= */
    public enum Phase {
        SEARCHING, AUDIO_CONTACT, CONFIRMED_CONTACT, RESCUE_GUIDE, RETREAT, HOLD
    }

    public enum HazardLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum RobotAction {
        SEARCH, APPROACH, HOLD, RETREAT, CALL_OPERATOR, GUIDE_SURVIVOR
    }

    public enum SurvivorEvidence {
        NONE, POSSIBLE, CONFIRMED
    }

    /* =======================
       유틸 (이 클래스 내부 사용)
       ======================= */
    private static String n(Object v) {
        return v == null ? "null" : v.toString();
    }

    private static String b(Boolean v) {
        return v != null && v ? "true" : "false";
    }

    private static String q(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ");
    }
}
