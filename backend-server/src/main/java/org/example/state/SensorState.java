package org.example.state;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class SensorState {

    public double ultrasonic;
    public String lastStt;
    public long lastSttTime;
    private double flame;
    private double co2;
    private double pm25;
    private double pm10;
    private double gas;

    private boolean pir;
    private boolean visionPerson; // YOLO 등으로 사람 감지 여부

    // --- getter & setter ---

    public double getFlame() {
        return flame;
    }

    public void setFlame(double flame) {
        this.flame = flame;
    }

    public double getCo2() {
        return co2;
    }

    public void setCo2(double co2) {
        this.co2 = co2;
    }

    public double getPm25() {
        return pm25;
    }

    public void setPm25(double pm25) {
        this.pm25 = pm25;
    }

    public double getPm10() {
        return pm10;
    }

    public void setPm10(double pm10) {
        this.pm10 = pm10;
    }

    public double getGas() {
        return gas;
    }

    public void setGas(double gas) {
        this.gas = gas;
    }

    public boolean isPir() {
        return pir;
    }

    public void setPir(boolean pir) {
        this.pir = pir;
    }

    public boolean isVisionPerson() {
        return visionPerson;
    }

    public void setVisionPerson(boolean visionPerson) {
        this.visionPerson = visionPerson;
    }

    public void setLastStt(String text) {
        this.lastStt = text;
        this.lastSttTime = System.currentTimeMillis(); // 여기서 바로 업데이트 가능
    }
    public void setUltrasonic(double distance) {
        this.ultrasonic = distance;
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
                ", lastStt='" + lastStt + '\'' +
                ", lastSttTime=" + sttTimeFormatted +
                '}';

    }

}
