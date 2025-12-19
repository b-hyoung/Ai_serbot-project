import socket
import json
import time
import threading

from pop import Pilot, Flame, Dust, Pir, CO2

# ==============================
# 로봇 / 서버 설정
# ==============================
bot = Pilot.SerBot()
bot.setSpeed(30)

SERVER_IP = "192.168.0.19"   # ★ PC(서버) IP로 바꿔줘
SERVER_PORT = 6000

SEND_INTERVAL = 0.5  # 0.5초마다 센서 데이터 전송

# ==============================
# 센서 포트/채널 설정
# ==============================
# Flame : 예제에서 Flame(2)를 쓰고 있으므로 기본 2 사용
FLAME_GPIO = 6

# Pir   : Pir(n) 형식. 일단 3으로 두고, 테스트해서 맞는 GPIO로 바꿔.
PIR_GPIO = 5   # ← Jupyter 테스트로 실제 GPIO 번호 확인해서 수정

# Dust  : I2C 주소 기반. 예제는 Dust() 그대로 사용.
# CO2   : SPI ADC 채널 2 (CO2(2)) 예제 사용.
CO2_CHANNEL = 2

# ==============================
# 센서 객체 생성 (에러 나도 프로그램 안 죽게 try/except)
# ==============================
# Flame
flame_sensor = None
try:
    flame_sensor = Flame(FLAME_GPIO)
    print(f"[Flame] GPIO {FLAME_GPIO} 에서 센서 객체 생성 성공")
except Exception as e:
    print(f"[Flame] GPIO {FLAME_GPIO} 에서 센서 생성 실패:", e)

# PIR
pir_sensor = None
try:
    pir_sensor = Pir(PIR_GPIO)
    print(f"[PIR] GPIO {PIR_GPIO} 에서 센서 객체 생성 성공")
except Exception as e:
    print(f"[PIR] GPIO {PIR_GPIO} 에서 센서 생성 실패:", e)

# Dust (I2C)
dust_sensor = None
try:
    dust_sensor = Dust()  # addr 기본값 0x28
    print("[Dust] 기본 주소(0x28)로 센서 객체 생성 성공")
except Exception as e:
    print("[Dust] Dust() 센서 생성 실패:", e)

# CO2 (SPI ADC)
co2_sensor = None
try:
    co2_sensor = CO2(CO2_CHANNEL)
    print(f"[CO2] 채널 {CO2_CHANNEL} 에서 센서 객체 생성 성공")
except Exception as e:
    print(f"[CO2] 채널 {CO2_CHANNEL} 에서 센서 생성 실패:", e)

# ==============================
# 센서 읽기 함수
# ==============================
def read_flame():
    """불꽃 감지: True/False 또는 None"""
    if flame_sensor is None:
        return None
    try:
        return bool(flame_sensor.read())
    except Exception as e:
        print("[센서 오류] flame:", e)
        return None

def read_pir():
    """PIR 인체감지: True/False 또는 None"""
    if pir_sensor is None:
        return None
    try:
        return bool(pir_sensor.read())
    except Exception as e:
        print("[센서 오류] pir:", e)
        return None

def read_dust():
    """
    먼지 센서: Dust.read() 호출 후 내부 필드들에 값이 채워짐.
    pm값들을 dict로 묶어서 반환.
    """
    if dust_sensor is None:
        return None
    try:
        dust_sensor.read()
        return {
            "status": dust_sensor.sensor_status,
            "mode": dust_sensor.measuring_mode,
            "pm_1p0_grimm": dust_sensor.pm_1p0_grimm,
            "pm_2p5_grimm": dust_sensor.pm_2p5_grimm,
            "pm_10_grimm": dust_sensor.pm_10_grimm,
            "pm_1p0_tsi": dust_sensor.pm_1p0_tsi,
            "pm_2p5_tsi": dust_sensor.pm_2p5_tsi,
            "pm_10_tsi": dust_sensor.pm_10_tsi,
            "num_0p3": dust_sensor.num_0p3,
            "num_0p5": dust_sensor.num_0p5,
            "num_1": dust_sensor.num_1,
            "num_2p5": dust_sensor.num_2p5,
            "num_5": dust_sensor.num_5,
            "num_10": dust_sensor.num_10,
        }
    except Exception as e:
        print("[센서 오류] dust:", e)
        return None

def read_co2():
    """
    CO2 센서: raw, volt, ppm 모두 묶어서 반환.
    calcPPM() = readVolt()/0.0004
    """
    if co2_sensor is None:
        return None
    try:
        raw = co2_sensor.read()
        volt = co2_sensor.readVolt()
        ppm = co2_sensor.calcPPM()
        return {
            "raw": raw,
            "volt": volt,
            "ppm": ppm,
        }
    except Exception as e:
        print("[센서 오류] co2:", e)
        return None

# (초음파는 UltraSonic 클래스가 없어서, 일단 여기선 제외)
def read_ultrasonic_all():
    """초음파는 나중에 CAN API 확인 후 구현 예정. 지금은 None 리턴."""
    return None

# ==========================================
# [기능 1] PC 서버에서 오는 명령 수신 → 로봇 제어
# ==========================================
def start_listening(sock):
    while True:
        try:
            data = sock.recv(1024)
            if not data:
                print("서버 연결 끊김 (수신 중단)")
                break

            command = data.decode("utf-8").strip()
            print(f"[명령] {command}")

            if command == "FORWARD":
                print("전진!")
                bot.forward()
            elif command == "BACKWARD":
                print("후진!")
                bot.backward()
            elif command == "LEFT":
                print("좌회전!")
                bot.move(90, 30)
            elif command == "RIGHT":
                print("우회전!")
                bot.move(270, 30)
            elif command == "STOP":
                print("정지!")
                bot.stop()

        except Exception as e:
            print("수신 에러:", e)
            break

# ==========================================
# [기능 2] 센서 데이터 0.5초마다 PC로 전송
# ==========================================
def start_sending_sensor(sock):
    print("📤 센서 데이터 전송 시작 (주기: 0.5초)")
    try:
        while True:
            flame_val = read_flame()
            pir_val   = read_pir()
            dust_val  = read_dust()
            co2_val   = read_co2()
            ultra_val = read_ultrasonic_all()

            payload = {
                "type": "SENSOR",
                "flame": flame_val,
                "pir": pir_val,
                "dust": dust_val,
                "co2": co2_val,
                "ultrasonic": ultra_val,  # 지금은 None
            }

            msg = json.dumps(payload) + "\n"
            sock.sendall(msg.encode("utf-8"))

            print("📤 전송:", payload)  # 필요하면 켜기
            time.sleep(SEND_INTERVAL)

    except Exception as e:
        print("송신 에러:", e)

# ==========================================
# [메인] PC 서버에 연결 후 수신+송신
# ==========================================
def main():
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        print(f"서버({SERVER_IP}:{SERVER_PORT}) 접속 시도...")
        sock.connect((SERVER_IP, SERVER_PORT))
        print("연결 성공!")
    except Exception as e:
        print("연결 실패:", e)
        return

    # 명령 수신 스레드
    listener = threading.Thread(target=start_listening, args=(sock,))
    listener.daemon = True
    listener.start()

    # 센서 송신 루프
    start_sending_sensor(sock)

    sock.close()

if __name__ == "__main__":
    main()
