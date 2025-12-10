import time
import json
import urllib.request

from pop.pilot import UltraSonic, Psd, Lidar, Flame, Gas, Dust, Temp, Motion, PixelDisplay

# 설정 및 센서 초기화
SERVER_URL = "http://192.168.0.100:8080/api/sensor"  # 서버 주소 변경 해야함

# 센서 객체를 담을 딕셔너리
sensors = {}

print("센서 초기화를 시작합니다")

try:
    #여기 센서 포트 번호 각 센서에 맞게 바꿔야함    
    sensors['ultrasonic'] = [UltraSonic(i) for i in range(6)]  
    sensors['psd']        = [Psd(i) for i in range(3)]         

    sensors['lidar']     = Lidar()      # 보통 USB 연결
    sensors['flame']     = Flame(7)     
    sensors['gas']       = Gas(3)       
    sensors['dust']      = Dust(1)      
    sensors['temp']      = Temp(4)      
    sensors['motion']    = Motion(8)    
    
    display = PixelDisplay()
    
    print("모든 센서 초기화 완료!")

except Exception as e:
    print(f"\n센서 초기화 실패: {e}")
    print("포트 번호가 중복되었거나, 연결되지 않은 센서가 있습니다.")
    exit() 

# 데이터 전송 함수
def send_data(data_dict):
    try:
        json_bytes = json.dumps(data_dict).encode('utf-8')
        req = urllib.request.Request(SERVER_URL, data=json_bytes)
        req.add_header('Content-Type', 'application/json')
        
        with urllib.request.urlopen(req, timeout=1) as response:
            if response.getcode() == 200:
                print(f"전송 성공 ! {len(json_bytes)} bytes 전송됨")
            else:
                print(f"전송 실패 ! 상태 코드: {response.getcode()}")
    except Exception as e:
        print(f"통신 에러 {e}")

# 메인 루프 (데이터 수집 -> 전송)
while True:
    data = {}
    
    try:
        # (1) 초음파 센서 값 읽기
        for i, us in enumerate(sensors['ultrasonic']):
            data[f'us_{i}'] = us.read()
            
        # (2) PSD 센서 값 읽기
        for i, psd in enumerate(sensors['psd']):
            data[f'psd_{i}'] = psd.read()

        # (3) 기타 센서 값 읽기 (초기화된 경우에만)
        if 'flame' in sensors:  data['flame'] = sensors['flame'].read()
        if 'gas' in sensors:    data['gas'] = sensors['gas'].read()
        if 'dust' in sensors:   data['dust'] = sensors['dust'].read()
        if 'temp' in sensors:   data['temp'] = sensors['temp'].read()
        if 'motion' in sensors: data['motion'] = sensors['motion'].read()
        if 'lidar' in sensors: data['lidar'] = sensors['lidar'].read()

        # (4) 서버 전송
        send_data(data)

    except KeyboardInterrupt:
        print("\n>> 프로그램 종료")
        break
    except Exception as e:
        print(f"런타임 에러 {e}")

    time.sleep(0.5) 
    