package org.example.state;

public class SensorState {
    /* 불꽃 강도(0~1) -> 근처에 불이 존재하는지 */
    public Double flame = null;
    /* 질식 위험도(1000~1500 = 정상 , 2000 이상 = 위험 , 3000이상 = 치명적 */
    public Double co2 = null;
    /*
        연기 / 먼지 농도
        PM2.5 높음 → 생존자 호흡 곤란 위험
        PM10 높음 → 로봇 시야 저하 / 연기 발생
    */
    public Double pm25 = null;
    public Double pm10 = null;
    // 열 신호를 통해 사람이 있는지 확인(true/false) */
    public Boolean pir = null;
    /* 라이다 역할을 통한 장애물 값 */
    public Double ultrasonic = null;

    /* STT를 통해 생존자의 의식상태 확인 및 대화 전달 */
    public String lastStt = null;
    public long lastSttTime = 0;

    // 디버깅 시 상태 확인하기 쉽게
    @Override
    public String toString() {
        return "SensorState{" +
                "flame=" + flame +
                ", co2=" + co2 +
                ", pm25=" + pm25 +
                ", pm10=" + pm10 +
                ", pir=" + pir +
                ", ultrasonic=" + ultrasonic +
                ", lastStt='" + lastStt + '\'' +
                '}';
    }
}
