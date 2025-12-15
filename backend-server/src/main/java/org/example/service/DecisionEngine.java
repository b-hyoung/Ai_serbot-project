package org.example.service;

import org.example.state.SensorState;

public class DecisionEngine {

    private final SensorState state;
    private final DecisionDispatcher dispatcher;

    // 너 기존 PromptBuilder/AgentService 그대로 사용
    private final long cooldownMs = 60_000; // ✅ 1분

    public DecisionEngine(SensorState state, DecisionDispatcher dispatcher) {
        this.state = state;
        this.dispatcher = dispatcher;
    }

    public void start() {
        new Thread(() -> {
            while (true) {
                try {
                    tick();
                    Thread.sleep(500); // 0.5초마다 조건 체크
                } catch (Exception e) {
                    System.out.println("⚠ DecisionEngine error: " + e.getMessage());
                }
            }
        }, "DecisionEngine").start();
    }

    private void tick() throws Exception {
        long now = System.currentTimeMillis();

        Boolean visionPerson = state.getVisionPerson();
        if (visionPerson == null || !visionPerson) return; // ✅ person true 아니면 실행 X

        if (now - state.getLastLlmCallAtMs() < cooldownMs) return; // ✅ 쿨다운

        // ✅ 여기부터 “네 기존 구조 안 무너뜨리는” 방식:
        // PromptBuilder.buildSevenKeyFewShotPrompt(...) 를 그대로 써서 prompt 만들면 됨.
        boolean hasHumanLikeSpeech = state.lastStt != null && !state.lastStt.isBlank();
        Double gas = state.getCo2(); // 네 프로젝트에서 gas를 co2로 쓴다면 이렇게 임시 매핑
        boolean survivorUnconscious = false; // 아직 없으면 false

        PromptBuilder.Phase phase = PromptBuilder.Phase.CONFIRMED_CONTACT; // 임시 고정(원하면 규칙으로 변경)

        String prompt = PromptBuilder.buildSevenKeyFewShotPrompt(
                phase,
                state,
                gas,
                true,                 // visionPerson true
                hasHumanLikeSpeech,
                survivorUnconscious
        );

        String raw = AgentService.ask(prompt); // 네 기존 AgentService 사용

        state.setLastLlmCallAtMs(now);
        state.setLastLlmRaw(raw);

        dispatcher.dispatch("VISION_PERSON_TRUE", raw);
    }
}