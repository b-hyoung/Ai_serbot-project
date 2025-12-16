package org.example.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class FollowController {

    // ✅ 런타임에 실제 이미지 크기로 업데이트 가능하게 final 제거
    private int imgW;
    private int imgH;

    // 튜닝값
    private final double centerDeadband = 0.12; // 화면 중심 ±12%는 직진
    private final double stopAreaRatio  = 0.20; // bbox 면적이 화면의 20% 넘으면 STOP
    private final long   cmdCooldownMs  = 250;  // 명령 너무 자주 보내지 않기

    private long lastCmdAt = 0;
    private String lastCmd = "STOP";

    // 디버그 제어
    private final boolean debug = true;
    private long lastDbgAt = 0;
    private final long dbgEveryMs = 500;

    public FollowController(int imgW, int imgH) {
        this.imgW = imgW;
        this.imgH = imgH;
    }

    /** ✅ 이미지 크기 갱신(저장된 이미지 실제 해상도랑 맞춰야 RIGHT 고정이 풀림) */
    public void updateFrameSize(int w, int h) {
        if (w > 0 && h > 0 && (this.imgW != w || this.imgH != h)) {
            this.imgW = w;
            this.imgH = h;
            if (debug) {
                System.out.println("📐 FollowController frame size updated => " + imgW + "x" + imgH);
            }
        }
    }

    /** yolo 응답(JsonObject) 받아서 로봇 명령 문자열 리턴 */
    public String decide(JsonObject yolo) {
        if (yolo == null) return "STOP";

        if (yolo.has("w") && yolo.has("h")) {
            updateFrameSize(yolo.get("w").getAsInt(), yolo.get("h").getAsInt());
        }

        // person false면 STOP
        if (!yolo.has("person") || !yolo.get("person").getAsBoolean()) return "STOP";
        if (!yolo.has("best") || !yolo.get("best").isJsonObject()) return "STOP";

        JsonObject best = yolo.getAsJsonObject("best");
        if (!best.has("xyxy") || !best.get("xyxy").isJsonArray()) return "STOP";

        JsonArray xyxy = best.getAsJsonArray("xyxy");
        if (xyxy.size() < 4) return "STOP";

        double x1 = xyxy.get(0).getAsDouble();
        double y1 = xyxy.get(1).getAsDouble();
        double x2 = xyxy.get(2).getAsDouble();
        double y2 = xyxy.get(3).getAsDouble();

        // ✅ bbox sanity check (yolo가 가끔 이상값 줄 때 방어)
        if (x2 <= x1 || y2 <= y1) return "STOP";
        if (imgW <= 0 || imgH <= 0) return "STOP";

        double bw = x2 - x1;
        double bh = y2 - y1;
        double area = bw * bh;

        double frameArea = (double) imgW * imgH;
        double areaRatio = area / frameArea;

        // ✅ 너무 가까우면 정지
        if (areaRatio >= stopAreaRatio) {
            dbg(x1,y1,x2,y2,areaRatio,0.0,"STOP(close)");
            return "STOP";
        }

        // ✅ 좌/우/전진 결정 (bbox 중심)
        double cx = (x1 + x2) / 2.0;
        double centerNorm = (cx - (imgW / 2.0)) / (imgW / 2.0); // -1 ~ +1

        String cmd;
        if (centerNorm < -centerDeadband) cmd = "LEFT";
        else if (centerNorm > centerDeadband) cmd = "RIGHT";
        else cmd = "FORWARD";

        dbg(x1,y1,x2,y2,areaRatio,centerNorm,cmd);
        return cmd;
    }

    /** 쿨다운/중복명령 억제 적용 */
    public String decideThrottled(JsonObject yolo) {
        long now = System.currentTimeMillis();
        String cmd = decide(yolo);

        // 같은 명령을 너무 자주 보내지 않기
        if (cmd.equals(lastCmd) && (now - lastCmdAt) < cmdCooldownMs) {
            return null;
        }

        lastCmd = cmd;
        lastCmdAt = now;
        return cmd;
    }

    private void dbg(double x1, double y1, double x2, double y2, double areaRatio, double centerNorm, String cmd) {
        if (!debug) return;
        long now = System.currentTimeMillis();
        if (now - lastDbgAt < dbgEveryMs) return;
        lastDbgAt = now;

        System.out.printf(
                "FOLLOW DBG frame=%dx%d bbox=[%.1f,%.1f,%.1f,%.1f] areaRatio=%.3f centerNorm=%.3f -> %s%n",
                imgW, imgH, x1, y1, x2, y2, areaRatio, centerNorm, cmd
        );
    }
}