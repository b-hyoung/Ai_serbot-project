package org.example;

import javax.swing.*;
import java.awt.*;

public class MYGUI extends JFrame {

    // --- UI 컴포넌트 참조 ---
    private JLabel tempValue1, tempValue2, gasValue, fireValue;
    private JLabel statusLabel;

    private JLabel lblTemp;
    private JLabel lblGas;
    private JLabel lblFire;
    private JLabel lblPIR;
    private JLabel lblHumidity;
    private JLabel lblPM25;
    private JLabel lblPM10;
    public MYGUI() {
        setTitle("Frame 1 - AI Servot Robot Dashboard");
        setSize(1400, 900);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        lblTemp = new JLabel("Temp: ---");
        lblGas = new JLabel("Gas: ---");
        lblFire = new JLabel("Fire: ---");
        lblPIR = new JLabel("PIR: ---");
        lblHumidity = new JLabel("Humidity: ---");
        lblPM25 = new JLabel("PM2.5: ---");
        lblPM10 = new JLabel("PM10: ---");




        // Main panel
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout(10, 10));
        mainPanel.setBackground(new Color(17, 24, 39));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        mainPanel.add(lblTemp);
        mainPanel.add(lblGas);
       mainPanel.add(lblFire);
       mainPanel.add(lblPIR);
       mainPanel.add(lblHumidity);
       mainPanel.add(lblPM25);
      mainPanel.add(lblPM10);
        // Header
        JPanel headerPanel = createHeader();
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Content panel
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BorderLayout(10, 10));
        contentPanel.setBackground(Color.WHITE);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));

        // Top row
        JPanel topRow = createTopRow();
        contentPanel.add(topRow, BorderLayout.NORTH);

        // Middle row
        JPanel middleRow = createMiddleRow();
        contentPanel.add(middleRow, BorderLayout.CENTER);

        // Bottom row
        JPanel bottomRow = createBottomRow();
        contentPanel.add(bottomRow, BorderLayout.SOUTH);

        mainPanel.add(contentPanel, BorderLayout.CENTER);

        // Footer
        JPanel footerPanel = createFooter();
        mainPanel.add(footerPanel, BorderLayout.SOUTH);

        add(mainPanel);
        setVisible(true);
    }

    // === 센서 데이터 업데이트 메서드 ===
    public void updateSensorData(double temp, double gas, boolean fire, boolean pir, double humidity) {
        tempValue1.setText(String.format("%.1f °C", temp));
        tempValue2.setText(String.format("%.1f °C", temp));
        gasValue.setText(String.format("%.1f ppm", gas));

        if (fire) {
            fireValue.setText("🚨 비상!");
            fireValue.setForeground(new Color(220, 38, 38));
        } else {
            fireValue.setText("정상");
            fireValue.setForeground(Color.BLACK);
        }
    }

    /**
     * 서버 연결 상태를 업데이트하는 메서드.
     * connected == true -> 녹색 상태표시
     * connected == false -> 빨간 상태표시
     */
    public void updateConnectionStatus(boolean connected) {
        // HTML을 이용해 점(●) 색상을 바꿔 보여주도록 함.
        if (connected) {
            statusLabel.setText("<html><font color='lime'>●</font> System Active • All Sensors Online</html>");
            statusLabel.setForeground(new Color(0, 255, 0)); // 필요시 텍스트 색도 변경
        } else {
            statusLabel.setText("<html><font color='red'>●</font> Disconnected • Check Server</html>");
            statusLabel.setForeground(new Color(220, 50, 50));
        }
    }

    private JPanel createHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BorderLayout());
        header.setBackground(new Color(17, 24, 39));
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));

        JLabel titleLabel = new JLabel("Frame 1 - AI Servot Robot Dashboard");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setForeground(Color.WHITE);
        header.add(titleLabel, BorderLayout.WEST);

        return header;
    }

    private JPanel createTopRow() {
        JPanel topRow = new JPanel();
        topRow.setLayout(new GridLayout(1, 3, 20, 0));
        topRow.setBackground(Color.WHITE);
        topRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));

        // Panel 1 - Digital Thermopile
        JPanel panel1 = new JPanel();
        panel1.setLayout(new BoxLayout(panel1, BoxLayout.Y_AXIS));
        panel1.setBackground(new Color(243, 244, 246));
        panel1.setBorder(BorderFactory.createLineBorder(new Color(209, 213, 219), 2));

        JLabel title1 = new JLabel("🌡️Digital Thermopile(온도)");
        title1.setFont(new Font("Arial", Font.BOLD, 14));
        title1.setAlignmentX(Component.CENTER_ALIGNMENT);

        tempValue1 = new JLabel("0.0 °C");
        tempValue1.setFont(new Font("Arial", Font.BOLD, 28));
        tempValue1.setAlignmentX(Component.CENTER_ALIGNMENT);

        panel1.add(Box.createRigidArea(new Dimension(0, 40)));
        panel1.add(title1);
        panel1.add(Box.createRigidArea(new Dimension(0, 20)));
        panel1.add(tempValue1);
        panel1.add(Box.createRigidArea(new Dimension(0, 40)));

        topRow.add(panel1);

        // Panel 2 - CO2 Gas Sensor
        JPanel panel2 = new JPanel();
        panel2.setLayout(new BoxLayout(panel2, BoxLayout.Y_AXIS));
        panel2.setBackground(new Color(243, 244, 246));
        panel2.setBorder(BorderFactory.createLineBorder(new Color(209, 213, 219), 2));

        JLabel title2 = new JLabel("💨 CO2 Gas Sensor (이산화탄소)");
        title2.setFont(new Font("Arial", Font.BOLD, 14));
        title2.setAlignmentX(Component.CENTER_ALIGNMENT);

        gasValue = new JLabel("0.0 ppm");
        gasValue.setFont(new Font("Arial", Font.BOLD, 28));
        gasValue.setAlignmentX(Component.CENTER_ALIGNMENT);

        panel2.add(Box.createRigidArea(new Dimension(0, 40)));
        panel2.add(title2);
        panel2.add(Box.createRigidArea(new Dimension(0, 20)));
        panel2.add(gasValue);
        panel2.add(Box.createRigidArea(new Dimension(0, 40)));

        topRow.add(panel2);

        // Panel 3 - Flame Module
        JPanel panel3 = new JPanel();
        panel3.setLayout(new BoxLayout(panel3, BoxLayout.Y_AXIS));
        panel3.setBackground(new Color(243, 244, 246));
        panel3.setBorder(BorderFactory.createLineBorder(new Color(209, 213, 219), 2));

        JLabel title3 = new JLabel("🔥 Flame Module");
        title3.setFont(new Font("Arial", Font.BOLD, 14));
        title3.setAlignmentX(Component.CENTER_ALIGNMENT);

        fireValue = new JLabel("정상");
        fireValue.setFont(new Font("Arial", Font.BOLD, 28));
        fireValue.setAlignmentX(Component.CENTER_ALIGNMENT);

        panel3.add(Box.createRigidArea(new Dimension(0, 40)));
        panel3.add(title3);
        panel3.add(Box.createRigidArea(new Dimension(0, 20)));
        panel3.add(fireValue);
        panel3.add(Box.createRigidArea(new Dimension(0, 40)));

        topRow.add(panel3);

        return topRow;
    }

    private JPanel createMiddleRow() {
        JPanel middleRow = new JPanel();
        middleRow.setLayout(new BorderLayout(20, 0));
        middleRow.setBackground(Color.WHITE);
        middleRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));

        // Left - Pixel Display
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setBackground(new Color(243, 244, 246));
        leftPanel.setBorder(BorderFactory.createLineBorder(new Color(209, 213, 219), 2));
        leftPanel.setPreferredSize(new Dimension(200, 400));

        JLabel leftTitle = new JLabel("Pixel Display");
        leftTitle.setFont(new Font("Arial", Font.BOLD, 14));
        leftTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel screenPanel = new JPanel();
        screenPanel.setBackground(new Color(31, 41, 55));
        screenPanel.setPreferredSize(new Dimension(140, 100));
        screenPanel.setMaximumSize(new Dimension(140, 100));

        JLabel emoji = new JLabel("😊");
        emoji.setFont(new Font("Arial", Font.PLAIN, 40));
        screenPanel.add(emoji);

        leftPanel.add(Box.createRigidArea(new Dimension(0, 80)));
        leftPanel.add(leftTitle);
        leftPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        leftPanel.add(screenPanel);
        leftPanel.add(Box.createRigidArea(new Dimension(0, 80)));

        middleRow.add(leftPanel, BorderLayout.WEST);

        // Center - Camera
        JPanel cameraPanel = new JPanel();
        cameraPanel.setLayout(new BorderLayout());
        cameraPanel.setBackground(new Color(31, 41, 55));
        cameraPanel.setBorder(BorderFactory.createLineBorder(new Color(156, 163, 175), 2));

        JPanel liveLabel = new JPanel();
        liveLabel.setBackground(new Color(220, 38, 38));
        JLabel live = new JLabel("● LIVE");
        live.setFont(new Font("Arial", Font.BOLD, 12));
        live.setForeground(Color.WHITE);
        liveLabel.add(live);

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        topPanel.setBackground(new Color(31, 41, 55));
        topPanel.add(liveLabel);
        cameraPanel.add(topPanel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setBackground(new Color(31, 41, 55));

        JLabel cameraIcon = new JLabel("📷");
        cameraIcon.setFont(new Font("Arial", Font.PLAIN, 80));
        cameraIcon.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel cameraTitle = new JLabel("Camera");
        cameraTitle.setFont(new Font("Arial", Font.BOLD, 32));
        cameraTitle.setForeground(Color.WHITE);
        cameraTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel cameraSubtitle = new JLabel("Robot Vision Feed");
        cameraSubtitle.setFont(new Font("Arial", Font.PLAIN, 16));
        cameraSubtitle.setForeground(new Color(156, 163, 175));
        cameraSubtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel resolution = new JLabel("Resolution: 1920x1080 • FPS: 30");
        resolution.setFont(new Font("Arial", Font.PLAIN, 12));
        resolution.setForeground(new Color(107, 114, 128));
        resolution.setAlignmentX(Component.CENTER_ALIGNMENT);

        centerPanel.add(Box.createVerticalGlue());
        centerPanel.add(cameraIcon);
        centerPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        centerPanel.add(cameraTitle);
        centerPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        centerPanel.add(cameraSubtitle);
        centerPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        centerPanel.add(resolution);
        centerPanel.add(Box.createVerticalGlue());

        cameraPanel.add(centerPanel, BorderLayout.CENTER);

        middleRow.add(cameraPanel, BorderLayout.CENTER);

        // Right - Digital Thermopile
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setBackground(new Color(243, 244, 246));
        rightPanel.setBorder(BorderFactory.createLineBorder(new Color(209, 213, 219), 2));
        rightPanel.setPreferredSize(new Dimension(200, 400));

        JLabel rightTitle = new JLabel("Digital Thermopile");
        rightTitle.setFont(new Font("Arial", Font.BOLD, 14));
        rightTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        tempValue2 = new JLabel("0.0 °C");
        tempValue2.setFont(new Font("Arial", Font.BOLD, 32));
        tempValue2.setAlignmentX(Component.CENTER_ALIGNMENT);

        rightPanel.add(Box.createVerticalGlue());
        rightPanel.add(rightTitle);
        rightPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        rightPanel.add(tempValue2);
        rightPanel.add(Box.createVerticalGlue());

        middleRow.add(rightPanel, BorderLayout.EAST);

        return middleRow;
    }

    private JPanel createBottomRow() {
        JPanel bottomRow = new JPanel();
        bottomRow.setLayout(new GridLayout(1, 3, 20, 0));
        bottomRow.setBackground(Color.WHITE);

        // Panel 1 - PIR Sensor
        JPanel panel1 = new JPanel();
        panel1.setLayout(new BoxLayout(panel1, BoxLayout.Y_AXIS));
        panel1.setBackground(new Color(243, 244, 246));
        panel1.setBorder(BorderFactory.createLineBorder(new Color(209, 213, 219), 2));

        JLabel title1 = new JLabel("🏃 PIR Sensor");
        title1.setFont(new Font("Arial", Font.BOLD, 14));
        title1.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel value1 = new JLabel("대기중");
        value1.setFont(new Font("Arial", Font.BOLD, 28));
        value1.setAlignmentX(Component.CENTER_ALIGNMENT);

        panel1.add(Box.createRigidArea(new Dimension(0, 40)));
        panel1.add(title1);
        panel1.add(Box.createRigidArea(new Dimension(0, 20)));
        panel1.add(value1);
        panel1.add(Box.createRigidArea(new Dimension(0, 40)));

        bottomRow.add(panel1);

        // Panel 2 - Dust Sensor
        JPanel panel2 = new JPanel();
        panel2.setLayout(new BoxLayout(panel2, BoxLayout.Y_AXIS));
        panel2.setBackground(new Color(243, 244, 246));
        panel2.setBorder(BorderFactory.createLineBorder(new Color(209, 213, 219), 2));

        JLabel title2 = new JLabel("😷 Dust Sensor:");
        title2.setFont(new Font("Arial", Font.BOLD, 14));
        title2.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel pm25 = new JLabel("PM2.5: 0.0 μg/m³");
        pm25.setFont(new Font("Arial", Font.PLAIN, 13));
        pm25.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel pm10 = new JLabel("PM10: 0.0 μg/m³");
        pm10.setFont(new Font("Arial", Font.PLAIN, 13));
        pm10.setAlignmentX(Component.CENTER_ALIGNMENT);

        panel2.add(Box.createRigidArea(new Dimension(0, 40)));
        panel2.add(title2);
        panel2.add(Box.createRigidArea(new Dimension(0, 15)));
        panel2.add(pm25);
        panel2.add(Box.createRigidArea(new Dimension(0, 5)));
        panel2.add(pm10);
        panel2.add(Box.createRigidArea(new Dimension(0, 40)));

        bottomRow.add(panel2);

        // Panel 3 - Microwave Motion
        JPanel panel3 = new JPanel();
        panel3.setLayout(new BoxLayout(panel3, BoxLayout.Y_AXIS));
        panel3.setBackground(new Color(243, 244, 246));
        panel3.setBorder(BorderFactory.createLineBorder(new Color(209, 213, 219), 2));

        JLabel title3 = new JLabel("📡 Microwave Motion");
        title3.setFont(new Font("Arial", Font.BOLD, 14));
        title3.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel value3 = new JLabel("감지안됨");
        value3.setFont(new Font("Arial", Font.BOLD, 28));
        value3.setAlignmentX(Component.CENTER_ALIGNMENT);

        panel3.add(Box.createRigidArea(new Dimension(0, 40)));
        panel3.add(title3);
        panel3.add(Box.createRigidArea(new Dimension(0, 20)));
        panel3.add(value3);
        panel3.add(Box.createRigidArea(new Dimension(0, 40)));

        bottomRow.add(panel3);

        return bottomRow;
    }

    private JPanel createFooter() {
        JPanel footer = new JPanel();
        footer.setLayout(new BorderLayout());
        footer.setBackground(new Color(31, 41, 55));
        footer.setBorder(BorderFactory.createEmptyBorder(15, 20, 0, 20));

        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new FlowLayout(FlowLayout.LEFT));
        leftPanel.setBackground(new Color(31, 41, 55));

        // 상태 라벨 (초기값: disconnected)
        statusLabel = new JLabel();
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 20));
        // label 배경/전경 색을 보이게 하려면 opaque 설정 필요 (여기서는 전경색 + HTML 점으로 처리)
        statusLabel.setOpaque(false);
        statusLabel.setText("<html><font color='red'>●</font> Disconnected • Check Server</html>");
        statusLabel.setForeground(new Color(220, 50, 50));
        leftPanel.add(statusLabel);

        footer.add(leftPanel, BorderLayout.WEST);

        return footer;
    }

    public void updateSensorData(double temp, double gas, boolean fire,
                                 boolean pir, double humidity, double pm25, double pm10) {

        lblTemp.setText("Temp: " + temp);
        lblGas.setText("Gas: " + gas);
        lblFire.setText("Fire: " + fire);
        lblPIR.setText("PIR: " + pir);
        lblHumidity.setText("Humidity: " + humidity);
        lblPM25.setText("PM2.5: " + pm25);
        lblPM10.setText("PM10: " + pm10);
    }
}