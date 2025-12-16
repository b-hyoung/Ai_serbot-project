package org.example.socket;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.example.service.*;
import org.example.state.SensorState;
import org.example.state.StateUpdater;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

public class ImageSocketService {

    private final int PORT = 6002;
    private final Path baseDir = Paths.get("./data/images");

    private final RobotSocketService robotServer;
    private final FollowController followController;

    private volatile long personBecameTrueAtMs = 0;
    private final long followWarmupMs = 800;

    private final GUISocketService guiService;
    private final VisionClient visionClient;
    private final SensorState state;

    private final double conf = 0.35;
    private final int maxBytes = 5_000_000;

    // LLM 호출 제어
    private volatile long lastLlmCallAtMs = 0;
    private final long llmCooldownMs = 2000;

    private volatile boolean lastPerson = false;

    public ImageSocketService(
            GUISocketService guiService,
            VisionClient visionClient,
            SensorState state,
            RobotSocketService robotServer
    ) {
        this.guiService = guiService;
        this.visionClient = visionClient;
        this.state = state;
        this.robotServer = robotServer;
        this.followController = new FollowController(640, 480);
    }

    public void startServer() {
        new Thread(() -> {
            try (ServerSocket server = new ServerSocket(PORT)) {
                Files.createDirectories(baseDir);
                System.out.println("📷 이미지 서버 시작! PORT : " + PORT);

                while (true) {
                    Socket sock = server.accept();
                    sock.setTcpNoDelay(true);
                    handleClient(sock);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "ImageServer-6002").start();
    }

    private void handleClient(Socket sock) {
        new Thread(() -> {
            try (DataInputStream in =
                         new DataInputStream(new BufferedInputStream(sock.getInputStream()))) {

                while (true) {
                    int len;
                    try {
                        len = in.readInt();
                    } catch (EOFException eof) {
                        break;
                    }

                    if (len <= 0 || len > maxBytes) {
                        System.out.println("📷 invalid image len=" + len);
                        break;
                    }

                    byte[] jpg = in.readNBytes(len);
                    if (jpg.length != len) {
                        System.out.println("📷 truncated image bytes");
                        break;
                    }

                    // 0) 저장
                    Path saved = saveImage(jpg);
                    String absPath = saved.toAbsolutePath().toString();

                    // 실제 이미지 크기 반영
                    int frameW = 640, frameH = 480;
                    try {
                        BufferedImage img = ImageIO.read(saved.toFile());
                        if (img != null) {
                            frameW = img.getWidth();
                            frameH = img.getHeight();
                            followController.updateFrameSize(frameW, frameH);
                        }
                    } catch (Exception e) {
                        System.out.println("⚠️ ImageIO read failed: " + e.getMessage());
                    }

                    // 1) YOLO 추론
                    JsonObject yolo;
                    try {
                        yolo = visionClient.infer(absPath, conf);
                    } catch (Exception e) {
                        System.out.println("🧠 YOLO infer failed: " + e.getMessage());

                        JsonObject fail = new JsonObject();
                        fail.addProperty("type", "VISION");
                        fail.addProperty("ok", false);
                        fail.addProperty("error", "yolo_infer_failed");
                        fail.addProperty("path", absPath);

                        if (guiService != null) guiService.sendToGui(fail.toString());
                        continue;
                    }

                    // best 재선정(가능할 때만)
                    yolo = rewriteBestToCenterMost(yolo, frameW);

                    // 2) VISION 이벤트
                    JsonObject visionEvt = new JsonObject();
                    visionEvt.addProperty("type", "VISION");
                    visionEvt.addProperty("path", absPath);
                    visionEvt.addProperty("ts", System.currentTimeMillis());
                    visionEvt.add("yolo", yolo);

                    // 3) 상태 갱신
                    StateUpdater.applyJson(visionEvt.toString(), state);

                    boolean person = yolo.has("person") && yolo.get("person").getAsBoolean();

                    // person false -> true 순간 워밍업 타이머
                    if (person && !lastPerson) {
                        personBecameTrueAtMs = System.currentTimeMillis();
                    }

                    // 3.5) FOLLOW 명령: CMD로 통일
                    if (robotServer != null) {
                        if (person) {
                            long now = System.currentTimeMillis();

                            if (now - personBecameTrueAtMs < followWarmupMs) {
                                sendRobotCmd("STOP");
                                System.out.println("🤖 FOLLOW WARMUP -> STOP (" + (now - personBecameTrueAtMs) + "ms)");
                            } else {
                                String cmd = followController.decideThrottled(yolo);
                                if (cmd != null) {
                                    sendRobotCmd(cmd);
                                    System.out.println("🤖 FOLLOW CMD -> " + cmd);
                                }
                            }
                        } else {
                            if (lastPerson) {
                                sendRobotCmd("STOP");
                                System.out.println("🤖 FOLLOW CMD -> STOP(person_lost)");
                            }
                        }
                    }

                    lastPerson = person;

                    // 4) person=true → LLM 호출
                    if (person) {
                        long now = System.currentTimeMillis();
                        if (now - lastLlmCallAtMs >= llmCooldownMs) {
                            lastLlmCallAtMs = now;

                            try {
                                boolean hasHumanLikeSpeech =
                                        state.getLastStt() != null && !state.getLastStt().isBlank();

                                boolean visionPerson =
                                        Boolean.TRUE.equals(state.getVisionPerson());

                                PromptBuilder.Phase phase;
                                if (!visionPerson && !hasHumanLikeSpeech) {
                                    phase = PromptBuilder.Phase.SEARCHING;
                                } else if (visionPerson && !hasHumanLikeSpeech) {
                                    phase = PromptBuilder.Phase.CONFIRMED_CONTACT;
                                } else {
                                    phase = PromptBuilder.Phase.RESCUE_GUIDE;
                                }

                                String prompt = PromptBuilder.buildSevenKeyFewShotPrompt(
                                        phase,
                                        state,
                                        state.getCo2(),      // ✅ co2(ppm)
                                        visionPerson,
                                        hasHumanLikeSpeech,
                                        false
                                );

                                String llmRaw = AgentService.ask(prompt);
                                state.setLastLlmRaw(llmRaw);

                                JsonObject llmEvt = new JsonObject();
                                llmEvt.addProperty("type", "LLM");
                                llmEvt.addProperty("ts", System.currentTimeMillis());
                                llmEvt.addProperty("trigger", "VISION_PERSON_TRUE");
                                llmEvt.addProperty("raw", llmRaw);

                                if (guiService != null) guiService.sendToGui(llmEvt.toString());

                            } catch (Exception e) {
                                JsonObject fail = new JsonObject();
                                fail.addProperty("type", "LLM");
                                fail.addProperty("ok", false);
                                fail.addProperty("error", "llm_call_failed");
                                fail.addProperty("msg", String.valueOf(e.getMessage()));

                                if (guiService != null) guiService.sendToGui(fail.toString());
                            }
                        }
                    }

                    // ✅ 5) GUI로 VISION 이벤트는 "항상" 전송 (person false도 포함)
                    if (guiService != null) {
                        guiService.sendToGui(visionEvt.toString());
                    }
                }

            } catch (Exception e) {
                System.out.println("📷 이미지 연결 오류: " + e.getMessage());
            } finally {
                try { sock.close(); } catch (Exception ignored) {}
            }
        }, "ImageClientHandler").start();
    }

    /** ✅ 로봇에 이동 명령은 CMD로 통일 */
    private void sendRobotCmd(String cmd) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "CMD");
        o.addProperty("cmd", cmd);
        // ✅ 여기서 \n 붙이지 말 것: RobotSocketService가 println이면 중복 개행 됨
        robotServer.sendToRobot(o.toString());
    }

    private JsonObject rewriteBestToCenterMost(JsonObject yolo, int frameW) {
        if (yolo == null) return yolo;
        if (!yolo.has("person") || !yolo.get("person").getAsBoolean()) return yolo;

        JsonArray candidates = null;
        if (yolo.has("all") && yolo.get("all").isJsonArray()) candidates = yolo.getAsJsonArray("all");
        else if (yolo.has("boxes") && yolo.get("boxes").isJsonArray()) candidates = yolo.getAsJsonArray("boxes");
        else if (yolo.has("dets") && yolo.get("dets").isJsonArray()) candidates = yolo.getAsJsonArray("dets");

        if (candidates == null || candidates.size() == 0) return yolo;

        double bestDist = Double.MAX_VALUE;
        JsonObject picked = null;

        for (int i = 0; i < candidates.size(); i++) {
            if (!candidates.get(i).isJsonObject()) continue;
            JsonObject det = candidates.get(i).getAsJsonObject();

            if (!det.has("xyxy") || !det.get("xyxy").isJsonArray()) continue;
            JsonArray xy = det.getAsJsonArray("xyxy");
            if (xy.size() < 4) continue;

            double x1 = xy.get(0).getAsDouble();
            double x2 = xy.get(2).getAsDouble();
            if (x2 <= x1) continue;

            double cx = (x1 + x2) / 2.0;
            double dist = Math.abs(cx - (frameW / 2.0));

            if (dist < bestDist) {
                bestDist = dist;
                picked = det;
            }
        }

        if (picked != null) yolo.add("best", picked);
        return yolo;
    }

    private Path saveImage(byte[] jpg) throws IOException {
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        Path dir = baseDir.resolve(day);
        Files.createDirectories(dir);

        String name = LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss_SSS")) + ".jpg";
        Path file = dir.resolve(name);

        Files.write(file, jpg, StandardOpenOption.CREATE_NEW);
        return file;
    }
}