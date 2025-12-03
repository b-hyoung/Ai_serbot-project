import org.json.JSONObject; // JSON 도구

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class MainFrame extends JFrame {

    // --- 통신 관련 변수 ---
    private Socket socket;
    private PrintWriter out;
    private final String SERVER_IP = "127.0.0.1"; // 내 컴퓨터(서버) 주소
    private final int SERVER_PORT = 6000;

    // --- 화면 구성 요소 (라벨) ---
    private JLabel lblStatus, lblTemp, lblGas, lblFire;

    public MainFrame() {
        // 1. 기본 창 설정
        setTitle("J-SafeGuard 관제 시스템");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 2. 상단: 상태 표시줄
        lblStatus = new JLabel("상태: 서버 연결 대기중...");
        lblStatus.setHorizontalAlignment(SwingConstants.CENTER);
        lblStatus.setFont(new Font("맑은 고딕", Font.BOLD, 16));
        lblStatus.setOpaque(true);
        lblStatus.setBackground(Color.LIGHT_GRAY);
        add(lblStatus, BorderLayout.NORTH);

        // 3. 중앙: 센서 데이터 대시보드 (그리드 레이아웃)
        JPanel panelCenter = new JPanel(new GridLayout(2, 2, 10, 10)); // 2행 2열
        
        lblTemp = createSensorLabel("온도", "0.0 °C");
        lblGas = createSensorLabel("가스", "0.0 ppm");
        lblFire = createSensorLabel("화재 감지", "정상");
        
        panelCenter.add(lblTemp);
        panelCenter.add(lblGas);
        panelCenter.add(lblFire);
        // (빈 공간 하나 남음 - 나중에 지도 넣을 곳)
        panelCenter.add(new JLabel(" ")); 

        add(panelCenter, BorderLayout.CENTER);

        // 4. 키보드 리스너 (조종)
        // 창이 포커스를 받아야 키 입력을 먹음
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                sendDriveCommand(e.getKeyCode());
            }
        });

        // 5. 서버 연결 시작
        connectToServer();

        setVisible(true);
    }

    // 예쁜 라벨 만드는 함수
    private JLabel createSensorLabel(String title, String initValue) {
        JLabel label = new JLabel("<html><center>" + title + "<br><h1>" + initValue + "</h1></center></html>");
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        return label;
    }

    // --- [기능 1] 서버 연결 및 데이터 수신 (귀) ---
    private void connectToServer() {
        new Thread(() -> {
            try {
                socket = new Socket(SERVER_IP, SERVER_PORT);
                out = new PrintWriter(socket.getOutputStream(), true);
                
                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("상태: 서버 연결됨 (조종 가능)");
                    lblStatus.setBackground(Color.GREEN);
                });

                // 서버가 보내주는 데이터 계속 듣기
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    // JSON 데이터 파싱
                    // 예: {"type":"SENSOR", "temp":24.5, "gas":0.1, "fire":false}
                    try {
                        JSONObject json = new JSONObject(inputLine);
                        
                        if (json.getString("type").equals("SENSOR")) {
                            double temp = json.getDouble("temp");
                            double gas = json.getDouble("gas");
                            boolean fire = json.getBoolean("fire");

                            // 화면 갱신 (Swing 스레드 안전하게)
                            SwingUtilities.invokeLater(() -> {
                                updateDashboard(temp, gas, fire);
                            });
                        }
                    } catch (Exception e) {
                        System.out.println("데이터 형식 오류: " + inputLine);
                    }
                }

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("상태: 연결 실패 (서버 꺼짐)");
                    lblStatus.setBackground(Color.RED);
                });
            }
        }).start();
    }

    // --- [기능 2] 대시보드 갱신 ---
    private void updateDashboard(double temp, double gas, boolean fire) {
        lblTemp.setText("<html><center>온도<br><h1>" + temp + " °C</h1></center></html>");
        lblGas.setText("<html><center>가스<br><h1>" + gas + " ppm</h1></center></html>");

        if (fire) {
            lblFire.setText("<html><center>화재 감지<br><h1>🚨 비상!</h1></center></html>");
            lblFire.setOpaque(true);
            lblFire.setBackground(Color.RED);
            lblFire.setForeground(Color.WHITE);
        } else {
            lblFire.setText("<html><center>화재 감지<br><h1>정상</h1></center></html>");
            lblFire.setOpaque(false);
            lblFire.setBackground(null);
            lblFire.setForeground(Color.BLACK);
        }
    }

    // --- [기능 3] 키보드 명령 전송 (입) ---
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
            out.println(cmd); // 서버로 전송!
            System.out.println("보냄: " + cmd);
        }
    }

    public static void main(String[] args) {
        new MainFrame();
    }
}