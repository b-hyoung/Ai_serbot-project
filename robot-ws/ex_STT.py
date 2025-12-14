import speech_recognition as sr

# 1. 아까 확인한 PyAudio를 이용해 마이크 잡기
r = sr.Recognizer()
#마이크는 디바이스의 4번이 가지고 있으며 구글이 듣기좋아하는 주파수 16000으로 맞춰줌.
mic = sr.Microphone(device_index=4,sample_rate=16000)

print("🎤 로봇의 귀가 열렸습니다. 말씀하세요! (3초간)")

try:
    with mic as source: #말 다 하면 알아서 마이크 close로 종료하기위해 with 사용함.
        # 주변 잡음 적응 (1초)
        r.energy_threshold = 300  # 숫자가 낮을수록 예민해짐 (기본값은 3000 정도임)
        r.dynamic_energy_threshold = False # "자동 조절 하지마! 무조건 300으로 유지해!"
        
        # 소리 듣기 (말이 끝날 때까지 자동으로 기다림)
        # timeout=5: 5초간 아무 말 없으면 에러
        # phrase_time_limit=5: 한 번에 최대 5초까지만 듣기
        audio = r.listen(source, timeout=5, phrase_time_limit=5)
        
    print("⏳ 변환 중...")
    
    # 2. 구글 서버로 보내서 텍스트로 바꿔오기
    text = r.recognize_google(audio, language='ko-KR')
    
    print("Result: " + text)

except sr.WaitTimeoutError:
    print("❌ 아무 말도 들리지 않았습니다.")
except sr.UnknownValueError:
    print("❌ 무슨 말인지 못 알아들었습니다.")
except sr.RequestError as e:
    print(f"❌ 인터넷/구글 서버 에러: {e}")
except Exception as e:
    print(f"❌ 기타 에러 발생: {e}")