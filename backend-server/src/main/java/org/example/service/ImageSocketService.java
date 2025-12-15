package org.example.service;

import com.google.gson.JsonObject;
import org.example.state.SensorState;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

public class ImageSocketService {

    private final int PORT = 6002;
    private final Path baseDir = Paths.get("./data/images");

    private final GUISocketService guiService;
    private final VisionClient visionClient;
    private final SensorState state;

    // 튜닝 포인트
    private final double conf = 0.35;           // YOLO confidence threshold
    private final int maxBytes = 5_000_000;     // 5MB safety guard

    // LLM 호출 제어
    private volatile long lastLlmCallAtMs = 0;
    private final long llmCooldownMs = 2000;    // 2초 쿨다운

    public ImageSocketService(
            GUISocketService guiService,
            VisionClient visionClient,
            SensorState state
    ) {
        this.guiService = guiService;
        this.visionClient = visionClient;
        this.state = state;
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
                        len = in.readInt(); // big-endian
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

                    Path saved = saveImage(jpg);
                    String absPath = saved.toAbsolutePath().toString();

                    // ======================
                    // 1) YOLO 추론
                    // ======================
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
                        guiService.sendToGui(fail.toString());
                        continue;
                    }

                    // ======================
                    // 2) VISION 이벤트 생성
                    // ======================
                    JsonObject visionEvt = new JsonObject();
                    visionEvt.addProperty("type", "VISION");
                    visionEvt.addProperty("path", absPath);
                    visionEvt.addProperty("ts", System.currentTimeMillis());
                    visionEvt.add("yolo", yolo);

                    // ======================
                    // 3) 상태 갱신
                    // ======================
                    StateUpdater.applyJson(visionEvt.toString(), state);

                    boolean person =
                            yolo.has("person") && yolo.get("person").getAsBoolean();

                    // ======================
                    // 4) person=true → LLM 호출
                    // ======================
                    if (person) {
                        long now = System.currentTimeMillis();

                        if (now - lastLlmCallAtMs >= llmCooldownMs) {
                            lastLlmCallAtMs = now;

                            try {
                                PromptBuilder.HazardLevel hazard =
                                        HazardEvaluator.compute(state);

                                boolean hasHumanLikeSpeech =
                                        state.lastStt != null && !state.lastStt.isBlank();

                                PromptBuilder.Phase phase;
                                if (!state.isVisionPerson() && !hasHumanLikeSpeech) {
                                    phase = PromptBuilder.Phase.SEARCHING;
                                } else if (state.isVisionPerson() && !hasHumanLikeSpeech) {
                                    phase = PromptBuilder.Phase.CONFIRMED_CONTACT;
                                } else {
                                    phase = PromptBuilder.Phase.RESCUE_GUIDE;
                                }

                                String prompt =
                                        PromptBuilder.buildSevenKeyFewShotPrompt(
                                                phase,
                                                state,
                                                state.getCo2(),              // gas 임시 대입
                                                state.isVisionPerson(),
                                                hasHumanLikeSpeech,
                                                false                   // unconscious 없음
                                        );

                                String llmRaw = AgentService.ask(prompt);

                                JsonObject llmEvt = new JsonObject();
                                llmEvt.addProperty("type", "LLM");
                                llmEvt.addProperty("ts", System.currentTimeMillis());
                                llmEvt.addProperty("trigger", "VISION_PERSON_TRUE");
                                llmEvt.addProperty("raw", llmRaw);

                                guiService.sendToGui(llmEvt.toString());

                            } catch (Exception e) {
                                JsonObject fail = new JsonObject();
                                fail.addProperty("type", "LLM");
                                fail.addProperty("ok", false);
                                fail.addProperty("error", "llm_call_failed");
                                fail.addProperty("msg", String.valueOf(e.getMessage()));
                                guiService.sendToGui(fail.toString());
                            }
                        }
                    }

                    // ======================
                    // 5) GUI로 VISION 이벤트 전송
                    // ======================
                    if (person) {
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

        String name =
                LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss_SSS")) + ".jpg";

        Path file = dir.resolve(name);
        Files.write(file, jpg, StandardOpenOption.CREATE_NEW);
        return file;
    }
}