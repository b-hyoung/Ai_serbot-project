package org.example;

import org.json.JSONObject;

import javax.swing.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Random;

public class Main extends JFrame {

    // --- 통신 관련 변수 ---
    private Socket socket;
    private PrintWriter out;
    private final String SERVER_IP = "172.31.38.120";
    private final int SERVER_PORT = 6000;

    // --- MYGUI 인스턴스 ---
    private MYGUI gui;

    // 테스트용 모드: true면 mock 데이터 생성 (GUI에서 바로 보임)
    private final boolean USE_MOCK = true;

    public Main() {
        gui = new MYGUI();

        gui.setFocusable(true);
        gui.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                sendDriveCommand(e.getKeyCode());
            }
        });

        // mock 모드면 mock 시작, 아니면 서버 연결 시도
        if (USE_MOCK) {
            startMockDataGenerator();
        } else {
            connectToServer();
        }
    }

    // --- 공통 JSON 처리 메서드 (재사용) ---
    private void handleIncomingJson(String inputLine) {
        try {
            JSONObject json = new JSONObject(inputLine);
            if ("SENSOR".equals(json.getString("type"))) {
                double temp = json.getDouble("temp");
                double gas = json.getDouble("gas");
                boolean fire = json.getBoolean("fire");
                boolean pir = json.getBoolean("pir");
                double humidity = json.getDouble("humidity");
                double pm25 = json.getDouble("pm25");
                double pm10 = json.getDouble("pm10");

                SwingUtilities.invokeLater(() ->
                        gui.updateSensorData(temp, gas, fire, pir, humidity, pm25, pm10));
            }
        } catch (Exception e) {
            System.out.println("데이터 형식 오류: " + inputLine);
        }
    }

    // --- [기능 1] 서버 연결 및 데이터 수신 (귀) ---
    private void connectToServer() {
        new Thread(() -> {
            try {
                socket = new Socket(SERVER_IP, SERVER_PORT);
                socket.setTcpNoDelay(true);
                out = new PrintWriter(socket.getOutputStream(), true);

                System.out.println("✅ 서버 연결 성공!");
                SwingUtilities.invokeLater(() -> gui.updateConnectionStatus(true));

                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    handleIncomingJson(inputLine); // 재사용
                }

            } catch (Exception e) {
                System.out.println("❌ 서버 연결 실패!");
                SwingUtilities.invokeLater(() -> gui.updateConnectionStatus(false));
                e.printStackTrace();
            }
        }).start();
    }

    // --- [기능 2] 키보드 명령 전송 (입) ---
    private void sendDriveCommand(int keyCode) {
        if (out == null) return;

        String cmd = "";
        switch (keyCode) {
            case KeyEvent.VK_W: cmd = "FORWARD"; break;
            case KeyEvent.VK_S: cmd = "BACKWARD"; break;
            case KeyEvent.VK_A: cmd = "LEFT"; break;
            case KeyEvent.VK_D: cmd = "RIGHT"; break;
            case KeyEvent.VK_SPACE: cmd = "STOP"; break;
        }

        if (!cmd.isEmpty()) {
            out.println(cmd);
            System.out.println("보냄: " + cmd);
        }
    }

    // --- 모의 데이터 생성기 (테스트용) ---
    private void startMockDataGenerator() {
        new Thread(() -> {
            Random rnd = new Random();
            SwingUtilities.invokeLater(() -> gui.updateConnectionStatus(true)); // 모드상 연결 성공으로 표시

            while (true) {
                try {
                    // 랜덤 값 생성 (실제 센서 범위에 맞춰 조절 가능)
                    double temp = 20.0 + rnd.nextDouble() * 10.0;        // 20 ~ 30
                    double gas = rnd.nextDouble() * 1.0;                 // 0 ~ 1
                    boolean fire = rnd.nextInt(100) < 2;                 // 2% 확률
                    boolean pir = rnd.nextInt(100) < 20;                 // 20% 확률
                    double humidity = 30.0 + rnd.nextDouble() * 60.0;    // 30 ~ 90
                    double pm25 = rnd.nextDouble() * 150.0;              // 0 ~ 150
                    double pm10 = rnd.nextDouble() * 200.0;              // 0 ~ 200

                    // JSON 문자열처럼 만들고 (옵션) 처리함수 호출 — 실제 소켓 문자열과 동일 흐름
                    JSONObject json = new JSONObject();
                    json.put("type", "SENSOR");
                    json.put("temp", temp);
                    json.put("gas", gas);
                    json.put("fire", fire);
                    json.put("pir", pir);
                    json.put("humidity", humidity);
                    json.put("pm25", pm25);
                    json.put("pm10", pm10);

                    // 실제 수신과 동일한 처리 경로 사용
                    handleIncomingJson(json.toString());

                    Thread.sleep(1000); // 1초마다 업데이트 (원하면 간격 변경)
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Main());
    }
}