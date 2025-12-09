package org.example.agent.model;

import java.util.List;

public class AgentInput {

    private MissionPhase phase;

    private double flame;
    private double co2;
    private double pm25;
    private double pm10;
    private double gas;

    private boolean pir;
    private boolean visionPerson;

    private List<String> recentStt;
    private String lastStt;
    private boolean hasHumanLikeSpeech;

    // --- getter & setter ---

    public MissionPhase getPhase() {
        return phase;
    }

    public void setPhase(MissionPhase phase) {
        this.phase = phase;
    }

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

    public List<String> getRecentStt() {
        return recentStt;
    }

    public void setRecentStt(List<String> recentStt) {
        this.recentStt = recentStt;
    }

    public String getLastStt() {
        return lastStt;
    }

    public void setLastStt(String lastStt) {
        this.lastStt = lastStt;
    }

    public boolean isHasHumanLikeSpeech() {
        return hasHumanLikeSpeech;
    }

    public void setHasHumanLikeSpeech(boolean hasHumanLikeSpeech) {
        this.hasHumanLikeSpeech = hasHumanLikeSpeech;
    }
}

