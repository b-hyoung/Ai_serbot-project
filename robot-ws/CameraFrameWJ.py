import socket
import json
import time
import threading
import base64

import cv2
from pop import Pilot, Util

bot = Pilot.SerBot()
bot.setSpeed(30)

SERVER_IP = "192.168.0.19"
SERVER_PORT = 6000

FPS = 30
FRAME_INTERVAL = 1.0 / FPS

Util.enable_imshow()
cam = Util.gstrmer(width=640, height=480)
camera = cv2.VideoCapture(cam, cv2.CAP_GSTREAMER)

if not camera.isOpened():
    print("❌ 카메라를 열 수 없습니다.")
    exit()

# 기능 1 명령 수신 → 로봇 제어
def start_listening(sock):
    print("📡 명령 수신 스레드 시작")

    while True:
        try:
            data = sock.recv(1024)
            if not data:
                print("⚠️ 서버 연결 끊김 (수신 종료)")
                break

            command = data.decode("utf-8").strip()

            if command == "FORWARD":
                bot.forward()
            elif command == "BACKWARD":
                bot.backward()
            elif command == "LEFT":
                bot.move(90, 30)
            elif command == "RIGHT":
                bot.move(270, 30)
            elif command == "STOP":
                bot.stop()

        except Exception as e:
            print("❌ 수신 에러:", e)
            break


# 카메라 이미지 송신
def start_sending_image(sock):
    print(f"📤 이미지 전송 시작 (FPS={FPS})")

    encode_param = [int(cv2.IMWRITE_JPEG_QUALITY), 80]
    prev_time = time.time()

    frame_count = 0
    last_log_time = time.time()

    try:
        while True:
            ret, frame = camera.read()
            if not ret:
                print("❌ 프레임 읽기 실패")
                break

            ret, buffer = cv2.imencode('.jpg', frame, encode_param)
            if not ret:
                print("❌ JPEG 인코딩 실패")
                continue

            jpg_bytes = buffer.tobytes()
            b64_str = base64.b64encode(jpg_bytes).decode("ascii")

            h, w = frame.shape[:2]

            payload = {
                "type": "IMAGE",
                "width": w,
                "height": h,
                "format": "jpg",
                "timestamp": time.time(),
                "data": b64_str,
            }

            msg = json.dumps(payload) + "\n"
            sock.sendall(msg.encode("utf-8"))

            frame_count += 1
            now = time.time()
            if now - last_log_time >= 1.0:
                print(f"📤 전송 중... FPS={frame_count}")
                frame_count = 0
                last_log_time = now

            # 로컬 미리보기
            cv2.imshow("soda", frame)
            if cv2.waitKey(1) & 0xFF == 27:
                print("🛑 ESC 입력 → 종료")
                break

            sleep_time = FRAME_INTERVAL - (now - prev_time)
            if sleep_time > 0:
                time.sleep(sleep_time)
            prev_time = time.time()

    except Exception as e:
        print("❌ 송신 에러:", e)

# [메인]
def main():
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        print(f"🔌 서버({SERVER_IP}:{SERVER_PORT}) 접속 중...")
        sock.connect((SERVER_IP, SERVER_PORT))
        print("✅ 서버 연결 성공!")
    except Exception as e:
        print("❌ 서버 연결 실패:", e)
        return

    listener = threading.Thread(target=start_listening, args=(sock,))
    listener.daemon = True
    listener.start()

    try:
        start_sending_image(sock)
    finally:
        sock.close()
        camera.release()
        cv2.destroyAllWindows()
        print("🔒 소켓 / 카메라 종료 완료")

if __name__ == "__main__":
    main()
