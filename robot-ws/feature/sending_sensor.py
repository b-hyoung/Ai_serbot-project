
import time
import json

def sending_sensor(sock):
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

            time.sleep(30) # 0.5초 대기

    except Exception as e:
        print(f"송신 에러: {e}")