package org.example.service;

import org.example.state.SensorState;
import static org.example.service.PromptBuilder.*;

public class DecisionEngine {

    public static Decision decide(SensorState s) {
        HazardLevel hazard = computeHazard(s);
        SurvivorEvidence evidence = computeEvidence(s);

        Phase phase;
        RobotAction robotAction;

        if (hazard == HazardLevel.CRITICAL) {
            phase = Phase.RETREAT;
            robotAction = RobotAction.RETREAT;
        } else {
            if (evidence == SurvivorEvidence.CONFIRMED) {
                phase = Phase.CONFIRMED_CONTACT;
                robotAction = RobotAction.CALL_OPERATOR;
            } else if (evidence == SurvivorEvidence.POSSIBLE) {
                phase = Phase.AUDIO_CONTACT;
                robotAction = RobotAction.HOLD;
            } else {
                phase = Phase.SEARCHING;
                robotAction = RobotAction.SEARCH;
            }
        }

        return new Decision(phase, hazard, evidence, robotAction);
    }

    private static HazardLevel computeHazard(SensorState s) {
        double flame = s.flame;
        double co2   = s.getCo2();
        double pm25  = s.getPm25();

        if (co2 >= 3000 || flame >= 0.8) return HazardLevel.CRITICAL;
        if (co2 >= 2000 || flame >= 0.5 || pm25 >= 150) return HazardLevel.HIGH;
        if (co2 >= 1000 || pm25 >= 80) return HazardLevel.MEDIUM;
        return HazardLevel.LOW;
    }

    private static SurvivorEvidence computeEvidence(SensorState s) {
        int score = 0;

        if (Boolean.TRUE.equals(s.isPir())) score += 1;

        boolean hasStt = s.lastStt != null && !s.lastStt.trim().isEmpty();
        boolean recentStt = hasStt && s.lastSttTime > 0
                && (System.currentTimeMillis() - s.lastSttTime) <= 10_000;
        if (recentStt) score += 1;

        if (score >= 2) return SurvivorEvidence.CONFIRMED;
        if (score == 1) return SurvivorEvidence.POSSIBLE;
        return SurvivorEvidence.NONE;
    }

    public record Decision(
            Phase phase,
            HazardLevel hazardLevel,
            SurvivorEvidence evidence,
            RobotAction robotAction
    ) {}

    /** 관제/로그용 한국어 결정 요약 (LLM 없이 생성) */
    public static String buildKoreanDecisionSummary(SensorState s, Decision d) {
        StringBuilder sb = new StringBuilder();

        sb.append("판단: ")
                .append(kPhase(d.phase())).append(", ")
                .append("위험도 ").append(d.hazardLevel()).append(", ")
                .append("행동 ").append(kAction(d.robotAction())).append(". ");

        sb.append("근거: ");
        sb.append("FLAME=").append(val(s.flame)).append(", ");
        sb.append("CO2=").append(val(s.getCo2())).append("ppm, ");
        sb.append("PM2.5=").append(val(s.getPm25())).append(", ");
        sb.append("PM10=").append(val(s.getPm10())).append(", ");
        sb.append("PIR=").append(Boolean.TRUE.equals(s.isPir()) ? "감지" : "미감지");

        if (s.lastStt != null && !s.lastStt.isBlank()) {
            sb.append(", STT=\"").append(s.lastStt).append("\"");
        }
        return sb.toString();
    }

    private static String kPhase(Phase p) {
        return switch (p) {
            case SEARCHING -> "수색";
            case AUDIO_CONTACT -> "음성 접촉";
            case CONFIRMED_CONTACT -> "접촉 확정";
            case RESCUE_GUIDE -> "구조 유도";
            case RETREAT -> "철수";
            case HOLD -> "정지";
        };
    }

    private static String kAction(RobotAction a) {
        return switch (a) {
            case SEARCH -> "수색 지속";
            case APPROACH -> "접근";
            case HOLD -> "정지 후 확인";
            case RETREAT -> "안전 지점으로 철수";
            case CALL_OPERATOR -> "관제 호출";
            case GUIDE_SURVIVOR -> "생존자 유도";
        };
    }

    private static String val(Double v) {
        return v == null ? "null" : (v % 1 == 0 ? String.valueOf(v.longValue()) : String.valueOf(v));
    }
}
