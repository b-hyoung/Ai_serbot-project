import socket
import json
import time
from pop import Pilot
import threading

bot = Pilot.SerBot()
bot.setSpeed(30)

SERVER_IP = '192.168.0.18'
SERVER_PORT = 6000

# ==========================================
# [기능 1] 수신
# ==========================================
def start_listening(sock):
    while True:
        try:
            data = sock.recv(1024)
            if not data:
                print("서버 연결 끊김 (수신 중단)")
                break

            command = data.decode('utf-8').strip()
            print(f"[명령] {command}")

            # 로봇 동작 수행
            if command == "FORWARD":
                print("전진!")
                bot.forward()
                time.sleep(2)
                bot.backward(30)
                time.sleep(2)
            elif command == "STOP":
                print("정지!")

        except Exception as e:
            print(f"수신 에러: {e}")
            break

# ==========================================
# [기능 2] 센서 전송
# ==========================================
def start_sending_sensor(sock):
    print("📤 센서 데이터 전송을 시작합니다.")
    try:
        while True:
            # 1. 실제 센서값 읽기 (함수화하면 더 좋음)
            # real_temp = sensor.get_temp() 
            real_temp = 24.5  # 테스트용
            real_fire = False

            # 2. 데이터 포장
            data = {
                "type": "SENSOR",
                "temp": real_temp,
                "fire": real_fire
            }

            # 3. 전송 (엔터 \n 필수!)
            msg = json.dumps(data) + "\n"
            sock.sendall(msg.encode())
            
            # 로그가 너무 빠르면 정신없으니까 1초에 한 번만 출력
            # print(f"📤 전송: {data}") 

            time.sleep(0.5) # 0.5초 대기

    except Exception as e:
        print(f"송신 에러: {e}")

# ==========================================
# [메인] 전체 흐름 관리
# ==========================================
def main():
    # 1. 서버 연결
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        print(f"서버({SERVER_IP}:{SERVER_PORT}) 접속 시도...")
        sock.connect((SERVER_IP, SERVER_PORT))
        print("연결 성공!")
    except Exception as e:
        print(f"연결 실패: {e}")
        return

    # 2. 듣는 귀(수신)는 별도 스레드(일꾼)에게 맡김
    listener = threading.Thread(target=start_listening, args=(sock,)) # 프로세스에서 쓰레드를 통해 듣기 생성
    listener.daemon = True # 메인 종료시 까지 종료x ->별도의 수신을 받자마자 종료하면 x
    listener.start() # 시작

    # 3. 송신은 메인 스레드가 직접 담당
    start_sending_sensor(sock)

    # 프로그램 종료 시 정리
    sock.close()

if __name__ == "__main__":
    main()