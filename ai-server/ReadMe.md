# AI Vision Inference Server (YOLOv8)

본 서버는 **로봇/메인 서버와 분리된 독립 AI 추론 서버**로,  
이미지 파일 경로를 입력받아 **YOLOv8 기반 사람(person) 감지 결과를 JSON으로 반환**한다.

- FastAPI 기반 REST 서버
- Python `venv` 환경 사용
- 메인 Java 서버와 **포트·프로세스 완전 분리**
- DB 저장 로직 없음 (추론 전용)

---

## 1. 역할 요약

| 항목 | 설명 |
|----|----|
| 목적 | 이미지에서 사람(person) 감지 |
| 입력 | 이미지 파일 **경로(path)** |
| 출력 | 감지 여부, confidence, bounding box |
| 통신 | HTTP REST API |
| 실행 | Python venv + uvicorn |
| 포트 | **8001** |

---

## 2. 프로젝트 위치 및 구조

```text
ai-server/
 ├─ infer_server.py      # FastAPI + YOLO 추론 서버
 ├─ venv/                # Python 가상환경 (git 제외)
 ├─ requirements.txt     # (선택) 패키지 고정용
 └─ README.md
```

---

## 3. 요구 환경

- Python 3.9 ~ 3.11 (권장: 3.10)  
- pip 사용 가능  
- OS: macOS / Linux / Windows  

Python 버전 확인:
```bash
python3 --version
```

---

## 4. 가상환경(venv) 생성 및 활성화

4-1. venv 생성
```bash
cd ai-server
python3 -m venv venv
```

4-2. 가상환경 활성화

macOS / Linux
```bash
source venv/bin/activate
```

Windows (PowerShell)
```powershell
venv\Scripts\activate
```

4-3. 패키지 설치
```bash
pip install --upgrade pip
pip install fastapi uvicorn ultralytics pydantic
```

---

## 5. 모델 설정 (선택)

YOLO 모델 경로를 환경 변수로 설정하면, 모델 파일 위치를 쉽게 변경하거나 관리할 수 있습니다.  
기본 모델 대신 커스텀 모델을 사용하거나 경로를 외부에서 지정할 때 유용합니다.

예시:

- macOS / Linux
```bash
export YOLO_MODEL_PATH="/path/to/custom/yolov8n.pt"
```

- Windows (PowerShell)
```powershell
setx YOLO_MODEL_PATH "C:\path\to\custom\yolov8n.pt"
```

서버 코드에서 `os.environ.get("YOLO_MODEL_PATH")`를 통해 해당 경로를 읽어 사용할 수 있습니다.

---

## 6. 서버 실행 방법

아래 명령어로 FastAPI 서버를 실행합니다. 포트는 8008로 지정되어 있습니다.

```bash
uvicorn infer_server:app --host 0.0.0.0 --port 8008
```

- `infer_server:app` : `infer_server.py` 파일 내 `app` 객체를 실행  
- `--host 0.0.0.0` : 모든 네트워크 인터페이스에서 접속 허용  
- `--port 8008` : 서버 포트 번호 지정  

필요 시 `--reload` 옵션을 추가하여 코드 변경 시 자동 재시작도 가능합니다.

---

## 7. API 사용 예시

### 요청

```http
POST /detect HTTP/1.1
Host: localhost:8008
Content-Type: application/json

{
  "image_path": "/path/to/image.jpg"
}
```

### 응답 (예시)

```json
{
  "person_detected": true,
  "confidence": 0.87,
  "bbox": [100, 150, 200, 300]
}
```

- `person_detected`: 사람 감지 여부 (true/false)  
- `confidence`: 감지 신뢰도 (0~1)  
- `bbox`: 바운딩 박스 좌표 [x_min, y_min, x_max, y_max]  

---

추가 문의 사항이나 개선 요청은 언제든지 공유 바랍니다.