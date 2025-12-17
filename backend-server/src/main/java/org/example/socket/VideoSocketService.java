package org.example.socket;

import com.google.gson.JsonObject;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Base64;

/**
 * 6003: 로봇 -> 서버 (영상)
 * - 프로토콜: [4바이트 big-endian length] + [JPEG bytes]
 * - 서버는 GUI로 {"type":"IMAGE","data":"base64..."} 를 한 줄(JSON + \n)로 전송
 */
public class VideoSocketService {

    private final int PORT = 6003;

    private ServerSocket serverSocket;
    private volatile Socket videoSocket;

    private GUISocketService guiService;

    public void setGuiService(GUISocketService guiService) {
        this.guiService = guiService;
    }

    public void startServer() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                System.out.println("🎥 Video server started : " + PORT);

                while (true) {
                    Socket socket = serverSocket.accept();
                    socket.setTcpNoDelay(true);

                    // 중복 연결 정리
                    Socket prev = videoSocket;
                    if (prev != null && !prev.isClosed()) {
                        try {
                            System.out.println("⚠ Previous video socket exists -> closing old connection");
                            prev.close();
                        } catch (Exception ignored) {}
                    }

                    System.out.println("🎥 Video connected: " + socket.getInetAddress());
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
            try {
                videoSocket = socket;
                in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

                while (true) {
                    // 4바이트 길이(빅엔디안)
                    int len;
                    try {
                        len = in.readInt(); // DataInputStream은 big-endian
                    } catch (Exception e) {
                        break; // 연결 종료
                    }

                    if (len <= 0 || len > 5_000_000) {
                        // 5MB 이상은 비정상(폭주/깨짐)
                        System.out.println("⚠ invalid frame length: " + len);
                        break;
                    }

                    byte[] jpg = new byte[len];
                    in.readFully(jpg);

                    if (guiService != null && guiService.isConnected()) {
                        String b64 = Base64.getEncoder().encodeToString(jpg);

                        JsonObject msg = new JsonObject();
                        msg.addProperty("type", "IMAGE");
                        msg.addProperty("data", b64);

                        guiService.sendToGui(msg.toString());
                    }
                }

            } catch (Exception e) {
                System.out.println("🎥 Video disconnected");
            } finally {
                try { if (in != null) in.close(); } catch (Exception ignored) {}
                try { socket.close(); } catch (Exception ignored) {}
                videoSocket = null;
            }
        }, "Video-Conn").start();
    }

    public boolean isConnected() {
        return videoSocket != null && !videoSocket.isClosed();
    }
}