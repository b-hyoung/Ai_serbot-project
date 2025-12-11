package org.example;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

// 카메라 영상 표시용 패널
public class CameraPanel extends JPanel {
    private BufferedImage image;

    public CameraPanel() {
        setBackground(Color.BLACK);
        setBorder(BorderFactory.createTitledBorder("Camera"));
    }

    public void setImage(BufferedImage img) {
        this.image = img;
        repaint();
    }

    @Override
    protected void paintComponent(java.awt.Graphics g) {
        super.paintComponent(g);
        if (image == null) {
            g.setColor(Color.GRAY);
            g.drawString("영상 데이터 대기중...", 10, 20);
            return;
        }

        int panelW = getWidth();
        int panelH = getHeight();
        int imgW = image.getWidth();
        int imgH = image.getHeight();

        double scale = Math.min(
                (double) panelW / imgW,
                (double) panelH / imgH
        );

        int drawW = (int) (imgW * scale);
        int drawH = (int) (imgH * scale);
        int x = (panelW - drawW) / 2;
        int y = (panelH - drawH) / 2;

        g.drawImage(image, x, y, drawW, drawH, null);
    }
}
