import torch
import cv2
import numpy as np
import pyaudio
import time 


try:
    model = torch.hub.load('ultralytics/yolov5', 'yolov5s', pretrained=True)
    model.conf = 0.5 

except Exception as e:
    print(f"❌ YOLOv5 모델 로드 에러: {e}")
    print("YOLOv5 저장소를 클론하고 requirements.txt를 설치했는지 확인하세요.")
    exit()


def play_beep():
    """특정 주파수와 길이의 비프음을 재생하는 함수"""
    volume = 0.5   # 볼륨
    fs = 48000     # 샘플링 주파수
    duration = 0.2 # 소리가 나는 길이 (0.2초)
    f = 1240.0     # 주파수 (Hz)
    
    # sine wave (사인파) 데이터 생성
    data = (np.sin(2 * np.pi * np.arange(fs * duration) * f/fs)).astype(np.float32)
    
    # PyAudio 초기화 및 스트림 열기
    p = pyaudio.PyAudio()
    stream = p.open(format=pyaudio.paFloat32, channels=1, rate=fs, output=True)
    
    # 소리 재생
    stream.write(volume * data)
    
    # 스트림 닫기
    stream.stop_stream()
    stream.close()
    p.terminate()


# --- 🎥 실시간 객체 인식 및 소리 출력 ---
# 0번 인덱스는 보통 기본 웹캠을 나타냅니다. (SerBot의 카메라 인덱스에 따라 변경 필요)
cap = cv2.VideoCapture(0)
mouse_detected = False

print("🎥 AI 객체 인식 시작: 'mouse'를 인식하면 삐 소리가 납니다. (종료: 'q')")

while cap.isOpened():
    ret, frame = cap.read()
    if not ret:
        break
        
    # 1. YOLOv5 모델을 이용한 객체 추론 (Inference)
    # 'frame'은 OpenCV의 numpy array 형식이며, YOLOv5가 자동으로 처리합니다.
    results = model(frame)
    
    # 결과를 Pandas DataFrame으로 변환하여 분석
    # COCO 데이터셋의 클래스 이름은 results.names에서 확인 가능
    # 'mouse'의 클래스 ID는 보통 13번입니다.
    detections = results.pandas().xyxy[0] 
    
    # 2. 'mouse' 객체 감지 조건 확인
    is_mouse_present = ('mouse' in detections['name'].values)
    
    if is_mouse_present and not mouse_detected:
        # 마우스가 인식되었고, 이전 상태는 '인식 안 됨'이었을 때 (한 번만 트리거)
        print("🚨 마우스 인식! 1초 간격으로 5회 삐 소리 출력 시작.")
        
        # 5번 반복 실행
        for i in range(5):
            play_beep()
            print(f"🎵 소리 출력 중... ({i + 1}회차)")
            if i < 4:
                time.sleep(0.8) # 소리 길이 0.2초 + 대기 0.8초 = 1초 간격
        
        mouse_detected = True # 소리 출력이 완료되었음을 표시
        print("✅ 소리 출력이 완료되었습니다.")
        
    elif not is_mouse_present:
        # 마우스가 사라지면 상태 초기화 (다음 감지를 위해)
        mouse_detected = False

    # 3. 인식 결과 화면에 표시 (선택 사항)
    # results.render()는 탐지된 객체에 경계 상자를 그려줍니다.
    annotated_frame = results.render()[0]
    cv2.imshow('YOLOv5 SerBot Detection', annotated_frame)
    
    # 'q' 키를 누르면 루프를 종료합니다.
    if cv2.waitKey(1) & 0xFF == ord('q'):
        break

# --- 🧹 리소스 해제 ---
cap.release()
cv2.destroyAllWindows()
print("프로그램이 종료되었습니다.")