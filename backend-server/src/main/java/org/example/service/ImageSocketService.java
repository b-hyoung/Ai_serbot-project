package org.example.service;

import com.google.gson.JsonObject;
import org.example.state.SensorState;

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

    private final GUISocketService guiService;
    private final VisionClient visionClient;
    private final SensorState state;

    // 튜닝 포인트
    private final double conf = 0.35;
    private final int maxBytes = 5_000_000;

    // LLM 호출 제어
    private volatile long lastLlmCallAtMs = 0;
    private final long llmCooldownMs = 2000;

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

        // 초기값은 대충, 실제 크기는 이미지 읽어서 updateFrameSize로 갱신
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
                    System.out.println("📷 이미지 접속: " + sock.getInetAddress());
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

                    // ✅ (중요) 실제 이미지 크기 반영 (RIGHT 고정 문제 원인 가능성 큼)
                    try {
                        BufferedImage img = ImageIO.read(saved.toFile());
                        if (img != null) {
                            followController.updateFrameSize(img.getWidth(), img.getHeight());
                            // 원하면 로그
                            // System.out.println("📐 saved image size = " + img.getWidth() + "x" + img.getHeight());
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

                    // 2) VISION 이벤트
                    JsonObject visionEvt = new JsonObject();
                    visionEvt.addProperty("type", "VISION");
                    visionEvt.addProperty("path", absPath);
                    visionEvt.addProperty("ts", System.currentTimeMillis());
                    visionEvt.add("yolo", yolo);

                    // 3) 상태 갱신
                    StateUpdater.applyJson(visionEvt.toString(), state);

                    boolean person = yolo.has("person") && yolo.get("person").getAsBoolean();

                    // 3.5) FOLLOW 명령
                    if (robotServer != null) {
                        if (person) {
                            String cmd = followController.decideThrottled(yolo);
                            if (cmd != null) {
                                robotServer.sendToRobot(cmd + "\n"); // ✅ 서버 구현이 라인 기반이면 개행 필요
                                System.out.println("🤖 FOLLOW CMD -> " + cmd);
                            }
                        } else {
                            robotServer.sendToRobot("STOP\n");
                        }
                    }

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
                                        state.getCo2(),      // gas 임시 대입 (원하면 state.getGas로 바꿔)
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

                    // 5) GUI로 VISION 이벤트 전송
                    if (person && guiService != null) {
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