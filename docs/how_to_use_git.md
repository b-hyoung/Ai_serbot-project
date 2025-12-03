#### 📂 1. 폴더 구조 (여기다가 저장하세요!)우리는 3가지 다른 언어를 쓰기 때문에, 반드시 자기 담당 폴더 안에서만 작업해야 합니다.
 남의 폴더 건드리면 안 돼요!

```BashJ-SafeGuard (전체 프로젝트)
├── 📂 backend-server      # ☕ [서버 담당] 본인 공간 (Spring Boot)
├── 📂 desktop-client      # 🖥️ [GUI 팀]   팀원 A, B 공간 (Java Swing)
├── 📂 robot-ws            # 🤖 [로봇 팀]   팀원 C, D 공간 (Python/ROS)
├── 📂 docs                # 📝 [모두]      기획서, 회로도, 회의록 넣는 곳
└── README.md              # 지금 읽고 있는 파일
```
#### 🚦 2. 협업 규칙 (Git Flow - 아주 중요!)
##### 우리는 main, dev, feat 3가지 브랜치(도로)만 사용합니다.
##### 🛣️ 브랜치 설명 (도로망)
##### 1. main (고속도로): 최종 발표용. 절대 직접 수정 금지! (항상 에러 없이 돌아가는 상태여야 함)
##### 2. develop (국도): 개발 통합용. 각자 만든 걸 여기에 합칩니다.feat/... (우리집 앞마당): 실제 작업 공간. 여기서 지지고 볶고 다 합니다.
```
A[feat/gui-login] -->|합치기 (PR)| B[develop]
C[feat/robot-motor] -->|합치기 (PR)| B
B -->|최종 완성| D[main]
```
##### ⚡ 3. 깃(Git) 따라하기 (복붙하세요!)팀원들은 아래 4단계 순서만 기억하면 됩니다.
### Step 1. 내 작업 공간 만들기 (브랜치 생성)
처음 시작할 때 딱 한 번, 혹은 새로운 기능을 만들 때마다 합니다.
규칙: git checkout -b feat/기능이름
```
# 예시: GUI 팀이 '지도' 기능을 만들 때
git checkout -b feat/gui-map

# 예시: 로봇 팀이 '모터' 기능을 만들 때
git checkout -b feat/robot-motor
```

### Step 2. 작업하고 저장하기 (커밋 & 푸시)
코드를 수정하고 저장할 때마다 합니다. (자주 할수록 좋아요)
```
git add .
git commit -m "지도 화면 레이아웃 잡기 완료"  # 메시지는 한글로 구체적으로!
git push origin feat/gui-map               # 내 브랜치 이름으로 올리기
```
⚠️ 주의: 절대 git push origin main 하지 마세요! 큰일 납니다.
### Step 3. 합쳐달라고 요청하기
(Pull Request)기능 하나가 완성되었나요?
이제 깃허브 웹사이트로 가세요.
1. GitHub 페이지에 가면 "Compare & pull request" 초록색 버튼이 떴을 겁니다. 클릭!
2. Base: develop <-  Compare: feat/gui-map (방향 확인!)
3. 제목: "[GUI] 지도 패널 구현 완료"
4. 내용: "지도 이미지 띄우는 것까지 했습니다." 라고 쓰고 Create Pull Request 클릭.
### Step 4. 서버 담당자(팀장)가 합치기(Merge)
팀장은 코드를 쓱 보고 문제없으면 Merge 버튼을 눌러서 develop에 합쳐줍니다.

---

### 🚨 4. 사고 발생 시 대처법 (FAQ)
#### Q. git push 했는데 에러가 나요! (Rejected)
A. 남이 수정한 코드가 내 컴퓨터에 없어서 그래요.
먼저 받아야 합니다.
```
git pull origin develop   # dev에 있는 최신 코드를 내 거랑 합치기
```
그 다음 다시 push 하세요.

---

#### Q. 코드가 꼬였어요 (Conflict)
A. 깃허브가 "너랑 쟤랑 같은 줄을 수정해서 못 합치겠어"라고 하는 겁니다.
VS Code나 인텔리제이에서 빨간색으로 표시된 부분을 찾아서,
남길 코드만 남기고 지운 뒤 다시 커밋하세요. (모르겠으면 팀장 호출!)

-----

#### Q. .class 파일이나 build 폴더가 올라갔어요!
A. .gitignore가 제대로 작동 안 한 겁니다.
팀장에게 말해서 파일을 삭제 요청하세요.