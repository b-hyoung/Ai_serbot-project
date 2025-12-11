package org.example.agent.audio.evaluator;

import org.example.agent.model.HazardLevel;

/* LLM에게 전부 맡기기보단 현재 수치에 따른 현장 상태는 개발자가 체크 */
public class HazardEvaluator {

    public HazardLevel evaluate(double flame, double co2, double pm25) {

        if (co2 > 3000 || flame > 0.9) {
            return HazardLevel.CRITICAL;
        }

        if (co2 > 2000 || pm25 > 150 || flame > 0.7) {
            return HazardLevel.HIGH;
        }

        if (co2 > 1000 || pm25 > 80) {
            return HazardLevel.MEDIUM;
        }

        return HazardLevel.LOW;
    }
}


