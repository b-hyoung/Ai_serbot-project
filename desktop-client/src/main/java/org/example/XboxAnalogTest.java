package org.example;

import com.studiohartman.jamepad.ControllerManager;
import com.studiohartman.jamepad.ControllerState;

public class XboxAnalogTest {

    public static void main(String[] args) {
        ControllerManager controllers = new ControllerManager();

        try {
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

                    System.out.printf(
                        "L(%.2f, %.2f)  R(%.2f, %.2f)  LT: %.2f  RT: %.2f%n",
                        leftX, leftY, rightX, rightY, lt, rt
                    );
                }

                Thread.sleep(50);
            }

        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            try {
                controllers.quitSDLGamepad();
            } catch (Throwable ignored) {}
        }
    }

    private static float deadZone(float value, float threshold) {
        return Math.abs(value) < threshold ? 0.0f : value;
    }
}
