from fastapi import FastAPI
from pydantic import BaseModel
from ultralytics import YOLO
from pathlib import Path
import os
import time

app = FastAPI()

# COCO 기준 person class id = 0
PERSON_CLASS_ID = 0

# 사전학습 모델로 시작 (나중에 best.pt로 교체)
MODEL_PATH = os.getenv("YOLO_MODEL", "yolov8n.pt")
model = YOLO(MODEL_PATH)

class InferReq(BaseModel):
    path: str
    conf: float = 0.35  # 기본 confidence threshold

@app.get("/health")
def health():
    return {"ok": True, "model": MODEL_PATH}

@app.post("/infer")
async def infer(req: Request, payload: InferReq):
    raw = await req.body()
    print("🧪 RAW BODY LEN =", len(raw))
    print("🧪 RAW BODY =", raw[:200])
    
    p = Path(req.path)
    if not p.exists() or not p.is_file():
        return {"ok": False, "error": "file_not_found", "path": req.path}

    t0 = time.time()
    r = model(str(p), verbose=False, conf=req.conf)[0]

    persons = []
    for b in r.boxes:
        cls = int(b.cls[0])
        if cls != PERSON_CLASS_ID:
            continue
        conf = float(b.conf[0])
        xyxy = [float(x) for x in b.xyxy[0]]
        persons.append({"conf": conf, "xyxy": xyxy})

    persons.sort(key=lambda x: x["conf"], reverse=True)
    best = persons[0] if persons else None

    return {
        "ok": True,
        "person": best is not None,
        "count": len(persons),
        "best": best,
        "ms": int((time.time() - t0) * 1000)
    }

print("🔥 THIS infer_server.py IS RUNNING 🔥")