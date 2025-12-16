package org.example.state;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class SensorState {

    // ===== sensors (nullable 권장: 미수신/null 구분) =====
    private Double ultrasonic;     // cm
    private String lastStt;
    private long lastSttTime;

    private Double flame;          // 0~1 or raw
    private Double co2;            // ppm (gas 제거, co2로 단일화)
    private Double pm25;
    private Double pm10;

    private Boolean pir;           // PIR true/false/null
    private Boolean visionPerson;  // YOLO person true/false/null

    private Double visionConf;
    private Long visionTs;

    // ===== timestamps (stale 판단용) =====
    private long lastSensorUpdateAtMs = 0;
    private Long flameTs;
    private Long co2Ts;
    private Long dustTs;
    private Long pirTs;
    private Long ultrasonicTs;

    // ===== dust meta (시연/운영 공용) =====
    // "ROBOT" | "DEMO" | "CACHE" | null
    private String dustSource;

    // ---------- 공통 유틸 ----------
    private void markAnySensorUpdate() {
        this.lastSensorUpdateAtMs = System.currentTimeMillis();
    }

    public long getLastSensorUpdateAtMs() {
        return lastSensorUpdateAtMs;
    }

    // ===== vision =====
    public void setVisionPerson(Boolean v) {
        this.visionPerson = v;
        this.visionTs = System.currentTimeMillis();
        markAnySensorUpdate();
    }
    public Boolean getVisionPerson() { return visionPerson; }

    public void setVisionConf(Double v) {
        this.visionConf = v;
        this.visionTs = System.currentTimeMillis();
        markAnySensorUpdate();
    }
    public Double getVisionConf() { return visionConf; }

    public Long getVisionTs() { return visionTs; }

    public boolean isVisionPerson() {
        return Boolean.TRUE.equals(this.visionPerson);
    }

    // ===== flame =====
    public Double getFlame() { return flame; }
    public void setFlame(Double flame) {
        this.flame = flame;
        this.flameTs = System.currentTimeMillis();
        markAnySensorUpdate();
    }
    public Long getFlameTs() { return flameTs; }

    // ===== CO2 (gas 통합) =====
    public Double getCo2() { return co2; }
    public void setCo2(Double co2) {
        this.co2 = co2;
        this.co2Ts = System.currentTimeMillis();
        markAnySensorUpdate();
    }
    public Long getCo2Ts() { return co2Ts; }

    // ===== dust =====
    public Double getPm25() { return pm25; }
    public Double getPm10() { return pm10; }
    public Long getDustTs() { return dustTs; }

    /**
     * dust는 한 패킷 단위로 들어오기 때문에 묶어서 세팅
     * @param pm25 pm2.5
     * @param pm10 pm10
     * @param source "ROBOT" | "DEMO" | "CACHE"
     */
    public void setDust(Double pm25, Double pm10, String source) {
        this.pm25 = pm25;
        this.pm10 = pm10;
        this.dustSource = source;
        this.dustTs = System.currentTimeMillis();
        markAnySensorUpdate();
    }

    public String getDustSource() { return dustSource; }

    // ===== PIR =====
    public Boolean getPir() { return pir; }
    public void setPir(Boolean pir) {
        this.pir = pir;
        this.pirTs = System.currentTimeMillis();
        markAnySensorUpdate();
    }
    public Long getPirTs() { return pirTs; }

    // ===== ultrasonic =====
    public Double getUltrasonic() { return ultrasonic; }
    public void setUltrasonic(Double distance) {
        this.ultrasonic = distance;
        this.ultrasonicTs = System.currentTimeMillis();
        markAnySensorUpdate();
    }
    public Long getUltrasonicTs() { return ultrasonicTs; }

    // ===== STT =====
    public String getLastStt() { return lastStt; }
    public long getLastSttTime() { return lastSttTime; }

    public void setLastStt(String text) {
        this.lastStt = text;
        this.lastSttTime = System.currentTimeMillis();
        markAnySensorUpdate();
    }

    // ===== 호환성 유지용 (기존 코드 보호) =====
    public void setPm25(Double pm25) {
        setDust(pm25, this.pm10, this.dustSource == null ? "ROBOT" : this.dustSource);
    }
    public void setPm10(Double pm10) {
        setDust(this.pm25, pm10, this.dustSource == null ? "ROBOT" : this.dustSource);
    }

    // ===== stale 판단 =====
    public boolean isDustStale(long staleMs) {
        if (dustTs == null) return true;
        return (System.currentTimeMillis() - dustTs) > staleMs;
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
                ", pir=" + pir +
                ", ultrasonic=" + ultrasonic +
                ", visionPerson=" + visionPerson +
                ", visionConf=" + visionConf +
                ", visionTs=" + visionTs +
                ", dustSource=" + dustSource +
                ", dustTs=" + dustTs +
                ", lastStt='" + lastStt + '\'' +
                ", lastSttTime=" + sttTimeFormatted +
                ", lastSensorUpdateAtMs=" + lastSensorUpdateAtMs +
                '}';
    }
}