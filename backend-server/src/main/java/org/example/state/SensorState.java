package org.example.state;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class SensorState {

    // ===== sensors (nullable 권장: 미수신/null 구분) =====
    public Double ultrasonic;     // cm 등
    public String lastStt;
    public long lastSttTime;

    public Double flame;          // 0~1 or raw
    private Double co2;           // ppm
    private Double pm25;
    private Double pm10;
    private Double gas;           // 너가 따로 쓰면 유지, 아니면 co2로 매핑 가능

    private Boolean pir;          // PIR true/false/null
    private Boolean visionPerson; // YOLO person true/false/null

    private Double visionConf;
    private Long visionTs;

    // ===== llm =====
    private long lastLlmCallAtMs = 0;
    private String lastLlmRaw;    // 디버그/리플레이용 (원하면)

    // ===== llm getters/setters =====
    public long getLastLlmCallAtMs() { return lastLlmCallAtMs; }
    public void setLastLlmCallAtMs(long t) { this.lastLlmCallAtMs = t; }

    public String getLastLlmRaw() { return lastLlmRaw; }
    public void setLastLlmRaw(String raw) { this.lastLlmRaw = raw; }

    // ===== vision =====
    public void setVisionPerson(Boolean v) { this.visionPerson = v; }
    public Boolean getVisionPerson() { return visionPerson; }

    public void setVisionConf(Double v) { this.visionConf = v; }
    public Double getVisionConf() { return visionConf; }

    public void setVisionTs(Long v) { this.visionTs = v; }
    public Long getVisionTs() { return visionTs; }

    // ===== sensors getters/setters =====
    public Double getFlame() { return flame; }
    public void setFlame(Double flame) { this.flame = flame; }

    public Double getCo2() { return co2; }
    public void setCo2(Double co2) { this.co2 = co2; }

    public Double getPm25() { return pm25; }
    public void setPm25(Double pm25) { this.pm25 = pm25; }

    public Double getPm10() { return pm10; }
    public void setPm10(Double pm10) { this.pm10 = pm10; }

    public Double getGas() { return gas; }
    public void setGas(Double gas) { this.gas = gas; }

    public Boolean getPir() { return pir; }
    public void setPir(Boolean pir) { this.pir = pir; }

    public String getLastStt() { return lastStt; }

    public void setLastStt(String text) {
        this.lastStt = text;
        this.lastSttTime = System.currentTimeMillis();
    }

    public void setUltrasonic(Double distance) { this.ultrasonic = distance; }

    public boolean isVisionPerson() {
        return Boolean.TRUE.equals(this.visionPerson);
    }

    @Override
    public String toString() {
        String sttTimeFormatted;
        if (lastSttTime > 0) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM월dd일 HH시 mm분 ss초");
            sttTimeFormatted = Instant.ofEpochMilli(lastSttTime)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()
                    .format(formatter);
        } else {
            sttTimeFormatted = "null";
        }

        return "SensorState{" +
                "flame=" + flame +
                ", co2=" + co2 +
                ", pm25=" + pm25 +
                ", pm10=" + pm10 +
                ", gas=" + gas +
                ", pir=" + pir +
                ", ultrasonic=" + ultrasonic +
                ", visionPerson=" + visionPerson +
                ", visionConf=" + visionConf +
                ", visionTs=" + visionTs +
                ", lastStt='" + lastStt + '\'' +
                ", lastSttTime=" + sttTimeFormatted +
                '}';
    }
}