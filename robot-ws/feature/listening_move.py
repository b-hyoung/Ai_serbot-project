from pop import Pilot
# ==========================================
# [기능 1] 수신
# ==========================================

bot = Pilot.SerBot()
bot.setSpeed(30)
def listening_move(sock):
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
            elif command == "BACKWARD":
                bot.backward()
            elif command == "LEFT":
                bot.move(90,30)
            elif command == "RIGHT":
                bot.move(270,30)
            elif command == "STOP":
                bot.stop()
                print("정지!")

        except Exception as e:
            print(f"수신 에러: {e}")
            break
