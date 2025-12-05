import socket
import threading

from feature.listening_move import listening_move
from feature.sending_sensor import sending_sensor

SERVER_IP = '192.168.0.18'
SERVER_PORT = 6000

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

    # 메인 스레드는 '송신(Send)'에만 집중하여 입력이 멈추지 않게 하고,
    # 별도의 서브 스레드가 '수신(Recv)'을 전담하여 데이터가 들어오는 즉시 처리하도록 함
    # 2. 수신
    listener = threading.Thread(target=listening_move, args=(sock,)) # 프로세스에서 쓰레드를 통해 듣기 생성
    listener.daemon = True # 메인 종료시 까지 종료x ->별도의 수신을 받자마자 종료하면 x
    listener.start() # 시작

    #소켓은 문자열x 바이트단위로 보내야하기때문에 b를 앞에 붙임
    sock.sendall(b"ROLL:ROBOT\n")
    # 3. 송신은 메인 스레드가 직접 담당
    sending_sensor(sock)
    
    # 프로그램 종료 시 정리
    sock.close()
    
if __name__ == "__main__":
    main()