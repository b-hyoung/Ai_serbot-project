package org.example.agent.model;

import com.google.gson.annotations.SerializedName;

public class AgentOutput {

    // LLM JSON의 "phase"
    private MissionPhase phase;

    // LLM JSON의 "hazard_level"
    @SerializedName("hazard_level")
    private HazardLevel hazardLevel;

    // LLM JSON의 "survivor_state"
    @SerializedName("survivor_state")
    private SurvivorState survivorState;

    // LLM JSON의 "robot_action"
    @SerializedName("robot_action")
    private String robotAction;

    // LLM JSON의 "voice_instruction"
    @SerializedName("voice_instruction")
    private String voiceInstruction;

    // LLM JSON의 "need_rescue_team"
    @SerializedName("need_rescue_team")
    private boolean needRescueTeam;

    @SerializedName("gui_message")
    private String guiMessage;   // ← 관제용 메시지
    // ======= getter / setter =======

    public MissionPhase getPhase() {
        return phase;
    }

    public void setPhase(MissionPhase phase) {
        this.phase = phase;
    }

    public HazardLevel getHazardLevel() {
        return hazardLevel;
    }

    public void setHazardLevel(HazardLevel hazardLevel) {
        this.hazardLevel = hazardLevel;
    }

    public SurvivorState getSurvivorState() {
        return survivorState;
    }

    public void setSurvivorState(SurvivorState survivorState) {
        this.survivorState = survivorState;
    }

    public String getRobotAction() {
        return robotAction;
    }

    public void setRobotAction(String robotAction) {
        this.robotAction = robotAction;
    }

    public String getVoiceInstruction() {
        return voiceInstruction;
    }

    public void setVoiceInstruction(String voiceInstruction) {
        this.voiceInstruction = voiceInstruction;
    }

    public boolean isNeedRescueTeam() {
        return needRescueTeam;
    }

    public void setNeedRescueTeam(boolean needRescueTeam) {
        this.needRescueTeam = needRescueTeam;
    }
    public String getGuiMessage() { return guiMessage; }
    public void setGuiMessage(String guiMessage) { this.guiMessage = guiMessage; }

    @Override
    public String toString() {
        return "AgentOutput{" +
                "phase=" + phase +
                ", hazardLevel=" + hazardLevel +
                ", survivorState=" + survivorState +
                ", robotAction='" + robotAction + '\'' +
                ", voiceInstruction='" + voiceInstruction + '\'' +
                ", needRescueTeam=" + needRescueTeam +
                ", guiMessage='" + guiMessage + '\'' +
                '}';
    }
}
