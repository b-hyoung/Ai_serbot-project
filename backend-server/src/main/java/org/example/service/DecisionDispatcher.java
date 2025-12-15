package org.example.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class DecisionDispatcher {

    private final GUISocketService gui;
    private final RobotSocketService robot;
    private final DecisionLogRepository logRepo; // 파일 로그든 DB든

    public DecisionDispatcher(GUISocketService gui, RobotSocketService robot, DecisionLogRepository logRepo) {
        this.gui = gui;
        this.robot = robot;
        this.logRepo = logRepo;
    }

    public void dispatch(String trigger, String rawDecisionJson) {
        // 1) 로그 저장 (GUI 없어도 남아야 함)
        logRepo.append(trigger, rawDecisionJson);

        // 2) 파싱
        JsonObject d = JsonParser.parseString(rawDecisionJson).getAsJsonObject();

        // 3) GUI로 gui_message
        if (d.has("gui_message")) {
            String guiMsg = d.get("gui_message").getAsString();
            if (guiMsg != null && !guiMsg.isBlank()) {
                JsonObject out = new JsonObject();
                out.addProperty("type", "GUI_MESSAGE");
                out.addProperty("ts", System.currentTimeMillis());
                out.addProperty("text", guiMsg);
                gui.sendToGui(out.toString());
            }
        }

        // 4) 로봇으로 survivor_speech (TTS)
        if (d.has("survivor_speech")) {
            String speech = d.get("survivor_speech").getAsString();
            if (speech != null && !speech.isBlank()) {
                JsonObject cmd = new JsonObject();
                cmd.addProperty("type", "TTS");
                cmd.addProperty("ts", System.currentTimeMillis());
                cmd.addProperty("text", speech);
                robot.sendToRobot(cmd.toString());
            }
        }
    }
}