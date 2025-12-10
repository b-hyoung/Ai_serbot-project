package org.example;

import com.studiohartman.jamepad.ControllerManager;
import com.studiohartman.jamepad.ControllerState;

import java.io.PrintWriter;
import java.net.Socket;
import java.util.Locale;

public class XboxAnalogTest {

    // ★ 여기 IP/포트는 환경에 맞게 바꿔 주세요
    private static final String SERVER_IP = "192.168.0.31";
    private static final int SERVER_PORT = 6000;

    public static void main(String[] args) {
        ControllerManager controllers = new ControllerManager();
        Socket socket = null;
        PrintWriter out = null;

        try {
            // 1) 서버 연결
            System.out.println("서버 연결 시도: " + SERVER_IP + ":" + SERVER_PORT);
            socket = new Socket(SERVER_IP, SERVER_PORT);
            socket.setTcpNoDelay(true);
            out = new PrintWriter(socket.getOutputStream(), true); // autoFlush = true
            System.out.println("서버 연결 성공!");

            // 2) 패드 초기화
            controllers.initSDLGamepad();
            System.out.println("Jamepad 초기화 완료.");
            System.out.println("Xbox 패드를 연결/켜고 아날로그 스틱과 트리거를 움직여 보세요.\n");

            while (true) {
                ControllerState state = controllers.getState(0);

                if (!state.isConnected) {
                    System.out.println("0번 컨트롤러가 연결되어 있지 않습니다.");
                } else {
                    float leftX  = state.leftStickX;
                    float leftY  = state.leftStickY;
                    float rightX = state.rightStickX;
                    float rightY = state.rightStickY;
                    float lt = state.leftTrigger;
                    float rt = state.rightTrigger;

                    leftX  = deadZone(leftX, 0.15f);
                    leftY  = deadZone(leftY, 0.15f);
                    rightX = deadZone(rightX, 0.15f);
                    rightY = deadZone(rightY, 0.15f);

                    // 콘솔 출력 (디버그용)
                    System.out.printf(
                        "L(%.2f, %.2f)  R(%.2f, %.2f)  LT: %.2f  RT: %.2f%n",
                        leftX, leftY, rightX, rightY, lt, rt
                    );

                    // ★ 여기서 lx, ly, rx만 JSON으로 서버에 전송
                    if (out != null) {
                        String json = String.format(
                            Locale.US,
                            "{\"type\":\"PAD\",\"lx\":%.3f,\"ly\":%.3f,\"rx\":%.3f}",
                            leftX, leftY, rightX
                        );
                        out.println(json);
                        // 디버그 로그
                        System.out.println("전송: " + json);
                    }
                }

                Thread.sleep(50);
            }

        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            try {
                controllers.quitSDLGamepad();
            } catch (Throwable ignored) {}

            try {
                if (out != null) out.close();
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (Throwable ignored) {}
        }
    }

    private static float deadZone(float value, float threshold) {
        return Math.abs(value) < threshold ? 0.0f : value;
    }
}
