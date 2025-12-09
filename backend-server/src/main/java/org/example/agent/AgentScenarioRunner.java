package org.example.agent;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.agent.AgentOrchestrator;
import org.example.agent.model.AgentOutput;
import org.example.agent.model.MissionPhase;
import org.example.mock.MockScenario;
import org.example.service.StateUpdater;
import org.example.state.SensorState;

import java.util.ArrayList;
import java.util.List;

public class AgentScenarioRunner {

    public void runMockSurvivorScenario() {

        // 1. 센서 상태 객체
        SensorState state = new SensorState();

        // 2. 최근 STT 목록 저장용
        List<String> recentStt = new ArrayList<>();

        System.out.println("=== MOCK SCENARIO: 생존자 + 구조 요청 ===");

        // 3. 모의 시나리오 적용
        for (String line : MockScenario.scenarioSurvivorNear()) {
            // 센서/상태 업데이트
            StateUpdater.applyJson(line, state);

            // STT 타입이면 텍스트만 따로 저장
            JsonObject obj = JsonParser.parseString(line).getAsJsonObject();
            String type = obj.get("type").getAsString();
            if ("STT".equalsIgnoreCase(type) && obj.has("text")) {
                recentStt.add(obj.get("text").getAsString());
            }
        }

        // 4. 현재 미션 단계 설정
        MissionPhase phase = MissionPhase.CONFIRMED_CONTACT;

        // 5. 에이전트 오케스트레이터 생성
        AgentOrchestrator orchestrator = new AgentOrchestrator();

        // 6. 에이전트 호출
        AgentOutput output = orchestrator.process(state, recentStt, phase);

        System.out.println("현재 센서 상태:");
        System.out.println(state);

        // 7. 결과 출력
        printOutput(output);
    }

    private void printOutput(AgentOutput output) {
        System.out.println("=== 에이전트 결정 결과 ===");
        System.out.println("phase           : " + output.getPhase());
        System.out.println("hazard_level    : " + output.getHazardLevel());
        System.out.println("survivor_state  : " + output.getSurvivorState());
        System.out.println("robot_action    : " + output.getRobotAction());
        System.out.println("voice_instruction:" + output.getVoiceInstruction());
        System.out.println("gui_message     : " + output.getGuiMessage());
        System.out.println("need_rescue_team: " + output.isNeedRescueTeam());

    }
}
