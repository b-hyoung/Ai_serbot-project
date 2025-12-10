package org.example;

import org.json.JSONObject;

import javax.swing.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Main extends JFrame {

    // --- 통신 관련 변수 ---
    private Socket socket;
    private PrintWriter out;
    private final String SERVER_IP = "172.31.38.120"; // 서버 IP (테스트: 잘못된 IP로 바꿔보세요)
    private final int SERVER_PORT = 6000;

    // --- MYGUI 인스턴스 ---
    private MYGUI gui;

    public Main() {
        // 1. MYGUI 창 생성
        gui = new MYGUI();

        // 2. MYGUI에 키보드 리스너 추가
        gui.setFocusable(true);
        gui.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                sendDriveCommand(e.getKeyCode());
            }
        });

        // 창 보이도록 (MYGUI 내부에서 setVisible 해주므로 여기서는 필요없음)
        // 3. 서버 연결 시작
        connectToServer();
    }

    // --- [기능 1] 서버 연결 및 데이터 수신 (귀) ---
    private void connectToServer() {
        new Thread(() -> {
            try {
                socket = new Socket(SERVER_IP, SERVER_PORT);
                socket.setTcpNoDelay(true); // 딜레이 제거
                out = new PrintWriter(socket.getOutputStream(), true);

                System.out.println("✅ 서버 연결 성공!");

                // GUI 상태 업데이트 (연결 성공)
                if (gui != null) {
                    SwingUtilities.invokeLater(() -> gui.updateConnectionStatus(true));
                }

                // 서버가 보내주는 데이터 계속 듣기// server ကို clientကို ချိတ်ပေးတဲ့ ဟာ
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    // JSON 데이터 파싱
                    // 예: {"type":"SENSOR", "temp":24.5, "gas":0.1, "fire":false}
                    try {
                        JSONObject json = new JSONObject(inputLine);

                        if (json.getString("type").equals("SENSOR")) {// server json string ကို json object

                            double temp = json.getDouble("temp");
                            double gas = json.getDouble("gas");
                            boolean fire = json.getBoolean("fire");
                            boolean pir = json.getBoolean("pir");
                            double humidity = json.getDouble("humidity");
                            double pm25 = json.getDouble("pm25");
                            double pm10 = json.getDouble("pm10");

                            // MYGUI 화면 갱신 (Swing 스레드 안전하게)
                            SwingUtilities.invokeLater(
                                    () -> gui.updateSensorData(temp, gas, fire, pir, humidity, pm25, pm10));
                        }

                        // 필요하면 다른 타입 처리 (예: DUST, PIR 등) 추가 가능
                        // if ("DUST".equals(json.getString("type"))) { ... gui.updateDust(pm25, pm10);
                        // }

                    } catch (Exception e) {
                        System.out.println("데이터 형식 오류: " + inputLine);
                    }
                }

            } catch (Exception e) {
                System.out.println("❌ 서버 연결 실패!");

                // GUI 상태 업데이트 (연결 실패)//gui update ကို main မှာ လုပ်ဖို့ထားထားတာ
                if (gui != null) {
                    SwingUtilities.invokeLater(() -> gui.updateConnectionStatus(false));
                }

                e.printStackTrace();
            }
        }).start();
    }

    // --- [기능 2] 키보드 명령 전송 (입) ---
    private void sendDriveCommand(int keyCode) {
        if (out == null)
            return;

        String cmd = "";
        switch (keyCode) {
            case KeyEvent.VK_W:
                cmd = "FORWARD";
                break;
            case KeyEvent.VK_S:
                cmd = "BACKWARD";
                break;
            case KeyEvent.VK_A:
                cmd = "LEFT";
                break;
            case KeyEvent.VK_D:
                cmd = "RIGHT";
                break;
            case KeyEvent.VK_SPACE:
                cmd = "STOP";
                break;
        }

        if (!cmd.isEmpty()) {
            out.println(cmd); // 서버로 전송!
            System.out.println("보냄: " + cmd);
        }
    }

    public static void main(String[] args) {
        // Swing UIကိူEvent Dispatch Threadမှာ ထားတာက ေကာင်းတယ်
        SwingUtilities.invokeLater(() -> new Main());
    }
}