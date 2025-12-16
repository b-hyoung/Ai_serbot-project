package org.example.hazard;

import org.example.state.SensorState;
import org.example.service.PromptBuilder.HazardLevel;

public class HazardEvaluator {

    public static HazardLevel compute(SensorState s) {
        double flame = safe(s.getFlame());
        double co2   = safe(s.getCo2());
        double pm25  = safe(s.getPm25());
        double pm10  = safe(s.getPm10());

        // 너가 쓰는 기준에 맞게 조정하면 됨 (일단 “현실적인” 기본값)
        if (flame >= 0.8 || co2 >= 2600 || pm25 >= 250 || pm10 >= 350) return HazardLevel.CRITICAL;
        if (flame >= 0.5 || co2 >= 2000 || pm25 >= 150 || pm10 >= 250) return HazardLevel.HIGH;
        if (co2 >= 1000 || pm25 >= 80  || pm10 >= 120)               return HazardLevel.MEDIUM;
        return HazardLevel.LOW;
    }

    private static double safe(Double v) {
        return v == null ? 0.0 : v;
    }
}