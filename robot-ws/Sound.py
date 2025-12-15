import speech_recognition as sr
import pyaudio
import numpy as np
import time 

r = sr.Recognizer()
mic = sr.Microphone(device_index=4, sample_rate=16000)

print("🎤 로봇의 귀가 열렸습니다. 말씀하세요! (최대 5초간)")

text = "" 

try:
    with mic as source:
        # 주변 잡음 적응 및 감도 설정
        r.energy_threshold = 300
        r.dynamic_energy_threshold = False
        
        # 소리 듣기
        audio = r.listen(source, timeout=5, phrase_time_limit=5)
        
    print("⏳ 변환 중...")
    
    # 구글 서버로 보내서 텍스트로 변환 (한국어)
    text = r.recognize_google(audio, language='ko-KR')
    
    print("✅ 인식 결과: " + text)

except sr.WaitTimeoutError:
    print("❌ 5초간 아무 말도 들리지 않았습니다.")
except sr.UnknownValueError:
    print("❌ 무슨 말인지 못 알아들었습니다.")
except sr.RequestError as e:
    print(f"❌ 인터넷/구글 서버 에러: {e}")
except Exception as e:
    print(f"❌ 기타 에러 발생: {e}")

if text == "살려주세요":
    print("구조요청이 있습니다 !")
    
    volume = 0.5   # 볼륨
    fs = 48000     # 샘플링 주파수
    duration = 0.2 # 소리가 나는 길이 (0.2초로 줄여서 1초 간격이 더 명확하게 들리도록 했습니다.)
    f = 1240.0     # 주파수 (Hz)
    
    data = (np.sin(2 * np.pi * np.arange(fs * duration) * f/fs)).astype(np.float32)
    
    p = pyaudio.PyAudio()
    stream = p.open(format=pyaudio.paFloat32, channels=1, rate=fs, output=True)
    
    for i in range(5):
        print(f"소리 출력 중... ({i + 1}회차)")
        
        stream.write(volume * data)
        
        if i < 4: 
            time.sleep(0.8) 
    
    stream.stop_stream()
    stream.close()
    p.terminate()
    print(" 소리 출력이 완료되었습니다.")
else:
    if text: # 인식된 텍스트가 있지만 "안녕하세요"가 아닌 경우
        print(f"⚠️ 인식된 단어는 '{text}' 이므로 소리를 출력하지 않습니다.")