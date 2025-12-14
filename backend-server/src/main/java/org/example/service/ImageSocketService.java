package org.example.service;

import com.google.gson.JsonObject;

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

    // 튜닝 포인트
    private final double conf = 0.35;           // YOLO confidence threshold
    private final int maxBytes = 5_000_000;     // 5MB safety guard

    public ImageSocketService(GUISocketService guiService, VisionClient visionClient) {
        this.guiService = guiService;
        this.visionClient = visionClient;
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
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(sock.getInputStream()))) {

                while (true) {
                    int len;
                    try {
                        len = in.readInt(); // big-endian 4 bytes
                    } catch (EOFException eof) {
                        break;
                    }

                    if (len <= 0 || len > maxBytes) {
                        System.out.println("📷 invalid image len=" + len);
                        break;
                    }

                    byte[] jpg = in.readNBytes(len);
                    if (jpg.length != len) {
                        System.out.println("📷 truncated image bytes=" + jpg.length + "/" + len);
                        break;
                    }

                    Path saved = saveImage(jpg);
                    String absPath = saved.toAbsolutePath().toString();
                    System.out.println("📷 saved: " + absPath);

                    // 1) YOLO 추론 요청
                    JsonObject yolo;
                    try {
                        yolo = visionClient.infer(absPath, conf);
                    } catch (Exception e) {
                        System.out.println("🧠 YOLO infer failed: " + e.getMessage());
                        // GUI로도 “비전 불가” 상태를 알려주는 게 좋음
                        JsonObject fail = new JsonObject();
                        fail.addProperty("type", "VISION");
                        fail.addProperty("ok", false);
                        fail.addProperty("error", "yolo_infer_failed");
                        fail.addProperty("path", absPath);
                        guiService.sendToGui(fail.toString());
                        continue;
                    }

                    // 2) GUI로 보낼 메시지 구성 (타입 붙여서 구분)
                    JsonObject out = new JsonObject();
                    out.addProperty("type", "VISION");
                    out.addProperty("path", absPath);
                    out.addProperty("ts", System.currentTimeMillis());
                    out.add("yolo", yolo);

                    // 3) GUI로 전송
                    guiService.sendToGui(out.toString());
                }

            } catch (Exception e) {
                System.out.println("📷 이미지 연결 끊김/오류: " + e.getMessage());
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