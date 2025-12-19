package org.example.socket;

import com.google.gson.JsonObject;
import org.example.database.Db;
import org.example.database.repo.VideoSessionRepo;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Base64;

public class VideoSocketService {

    private final int PORT = 6003;

    private ServerSocket serverSocket;
    private volatile Socket videoSocket;

    private GUISocketService guiService;

    // ✅ DB 세션
    private final VideoSessionRepo sessionRepo = new VideoSessionRepo();
    private volatile long currentSessionId = -1;

    // 시연/기본값
    private static final int DB_FPS = 5;
    private static final String CODEC = "JPEG";
    private static final String MIME = "image/jpeg";

    // ✅ 무한 대기 방지(전송 멈추고 연결만 살아있는 케이스)
    private static final int READ_TIMEOUT_MS = 5_000;

    // ✅ 프레임 저장 SQL (repo 따로 안 만들고 여기서 바로 처리)
    private static final String INSERT_FRAME_SQL = """
        INSERT INTO video_frame
        (session_id, received_at_ms, frame_index, mime, jpeg_bytes, bytes_len)
        VALUES (?, ?, ?, ?, ?, ?)
        """;

    private volatile boolean shutdownHookInstalled = false;

    public void setGuiService(GUISocketService guiService) {
        this.guiService = guiService;
    }

    public void startServer() {
        // ✅ 서버 강제종료/IDE stop 대비: 열려있는 세션 종료
        installShutdownHookOnce();

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                System.out.println("🎥 Video server started : " + PORT);

                while (true) {
                    Socket socket = serverSocket.accept();
                    socket.setTcpNoDelay(true);

                    // ✅ 무한 대기 방지: 읽기 타임아웃 설정
                    socket.setSoTimeout(READ_TIMEOUT_MS);

                    // ✅ 중복 연결 정리: 이전 소켓 닫기 + 세션 종료
                    Socket prev = videoSocket;
                    if (prev != null && !prev.isClosed()) {
                        try {
                            System.out.println("⚠ Previous video socket exists -> closing old connection");
                            prev.close();
                        } catch (Exception ignored) {}
                    }
                    endCurrentSession("replaced");

                    System.out.println("🎥 Video connected: " + socket.getInetAddress());

                    // ✅ 새 세션 시작
                    startNewSession("robot:6003");

                    handleVideo(socket);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "Video-Accept").start();
    }

    private void handleVideo(Socket socket) {
        new Thread(() -> {
            DataInputStream in = null;
            int frameIndex = 0;

            try {
                videoSocket = socket;
                in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

                while (true) {
                    int len;

                    try {
                        len = in.readInt(); // big-endian
                    } catch (SocketTimeoutException te) {
                        // ✅ 전송이 멈췄는데 연결만 살아있는 상태 -> 세션 종료 처리
                        System.out.println("⚠ video read timeout (" + READ_TIMEOUT_MS + "ms) -> end session");
                        break;
                    } catch (Exception e) {
                        // 연결 종료 등
                        break;
                    }

                    if (len <= 0 || len > 5_000_000) {
                        System.out.println("⚠ invalid frame length: " + len);
                        break;
                    }

                    byte[] jpg = new byte[len];
                    in.readFully(jpg);

                    long now = System.currentTimeMillis();
                    long sid = currentSessionId;

                    // ✅ DB(video_frame) 저장
                    if (sid > 0) {
                        insertFrame(sid, now, frameIndex, jpg);
                    }

                    // ✅ GUI로 전송 (기존 그대로)
                    if (guiService != null && guiService.isConnected()) {
                        String b64 = Base64.getEncoder().encodeToString(jpg);

                        JsonObject msg = new JsonObject();
                        msg.addProperty("type", "IMAGE");
                        msg.addProperty("data", b64);

                        guiService.sendToGui(msg.toString());
                    }

                    frameIndex++;
                }

            } catch (Exception e) {
                System.out.println("🎥 Video disconnected");
            } finally {
                try { if (in != null) in.close(); } catch (Exception ignored) {}
                try { socket.close(); } catch (Exception ignored) {}
                videoSocket = null;

                // ✅ 연결 종료/timeout/에러 -> 세션 종료
                endCurrentSession("disconnected_or_timeout");
            }
        }, "Video-Conn").start();
    }

    private void insertFrame(long sessionId, long receivedAtMs, int frameIndex, byte[] jpg) {
        try (Connection c = Db.getConnection();
             PreparedStatement ps = c.prepareStatement(INSERT_FRAME_SQL)) {

            ps.setLong(1, sessionId);
            ps.setLong(2, receivedAtMs);
            ps.setInt(3, frameIndex);
            ps.setString(4, MIME);
            ps.setBytes(5, jpg);
            ps.setInt(6, jpg.length);

            ps.executeUpdate();

        } catch (Exception e) {
            System.out.println("⚠ DB insert video_frame failed: " + e.getMessage());
        }
    }

    private void startNewSession(String note) {
        long now = System.currentTimeMillis();
        long sid = sessionRepo.startSession(
                now,
                DB_FPS,
                null,   // width
                null,   // height
                CODEC,
                note
        );
        currentSessionId = sid;
        System.out.println("✅ video_session started id=" + currentSessionId);
    }

    private void endCurrentSession(String reason) {
        long sid = currentSessionId;
        if (sid <= 0) return;

        long now = System.currentTimeMillis();
        try {
            sessionRepo.endSession(sid, now);
            System.out.println("✅ video_session ended id=" + sid + " (" + reason + ")");
        } catch (Exception e) {
            System.out.println("⚠ endSession failed id=" + sid + " : " + e.getMessage());
        } finally {
            currentSessionId = -1;
        }
    }

    private void installShutdownHookOnce() {
        if (shutdownHookInstalled) return;
        shutdownHookInstalled = true;

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                endCurrentSession("shutdown");
            } catch (Exception ignored) {}
        }, "Video-ShutdownHook"));
    }

    public boolean isConnected() {
        return videoSocket != null && !videoSocket.isClosed();
    }
}