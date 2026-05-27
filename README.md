<div align="center">

<img src="https://capsule-render.vercel.app/api?type=waving&color=gradient&customColorList=12,18,20&height=200&section=header&text=SUDA&fontSize=64&fontColor=ffffff&animation=fadeIn&fontAlignY=34&desc=On-Device%20Sign%20Language%20Communication%20Platform&descSize=18&descAlignY=56" width="100%"/>

<br/>

### 농인 부모와 청인 자녀의 대화를 잇는 온디바이스 수어 소통 서비스

<br/>

[![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin_2.3.20-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](#)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](#)
[![LiteRT](https://img.shields.io/badge/LiteRT-FF6F00?style=for-the-badge&logo=tensorflow&logoColor=white)](#)
[![MediaPipe](https://img.shields.io/badge/MediaPipe-0097A7?style=for-the-badge&logo=google&logoColor=white)](#)

[![Java](https://img.shields.io/badge/Java_21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](#)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot_4.0.5-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)](#)
[![Python](https://img.shields.io/badge/Python_3.12-3776AB?style=for-the-badge&logo=python&logoColor=white)](#)
[![FastAPI](https://img.shields.io/badge/FastAPI-009688?style=for-the-badge&logo=fastapi&logoColor=white)](#)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL_16-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)](#)

</div>

---

## 프로젝트 소개

**SUDA(수다)** 는 CODA, 즉 **농인 부모님의 청인 자녀**가 더 자연스럽게 대화할 수 있도록 돕는 Android 기반 수어·음성 소통 서비스입니다.

카메라로 입력된 수어 영상은 서버로 전송하지 않고, Android 기기 내부에서 **MediaPipe 랜드마크 추출**과 **TFLite/LiteRT 수어 인식 모델 추론**을 통해 실시간으로 처리합니다.  
이후 추출된 단어 또는 문장은 Spring Boot API Gateway를 통해 Gemini 기반 문맥 보정과 CLOVA TTS/STT를 거쳐 자막과 음성으로 변환됩니다.

> **SSAFY 14기 A404팀**  
> On-Device AI 기반 수어 인식 · 음성 변환 · 학습 리포트 플랫폼

---

## 핵심 기능

<table>
<tr>
<td width="50%">

### 온디바이스 수어 인식
- CameraX 기반 실시간 카메라 프레임 수집
- MediaPipe Holistic 랜드마크 추출
- TFLite / LiteRT 모델 기반 수어 단어 추론
- 영상 원본을 서버로 보내지 않는 프라이버시 중심 구조

</td>
<td width="50%">

### 수어 → 음성 변환
- 인식된 수어 단어를 자연스러운 문장으로 보정
- Gemini API 기반 문맥 교정
- CLOVA TTS 기반 음성 합성
- 자막과 음성을 동시에 제공하는 대화형 UI

</td>
</tr>
<tr>
<td width="50%">

### 음성 → 텍스트 변환
- Android 로컬 STT 및 CLOVA STT 연동
- 영유아 발음 보정 파이프라인
- 원본 발화와 보정 문장 분리 제공
- 대화 세션 기반 자막 렌더링

</td>
<td width="50%">

### 학습 및 리포트
- 자녀 프로필 기반 학습 관리
- 카테고리/난이도별 수어 단어 학습
- 퀴즈 세션, 정답 제출, 결과 확인
- 취약 단어, 학습 요약, 대화 분석 리포트 제공

</td>
</tr>
</table>

---

## 시스템 아키텍처


<img width="1920" height="870" alt="image" src="https://github.com/user-attachments/assets/b1e45dd0-6c55-4ee0-8d8e-0111b959207a" />



| 계층 | 기술 | 설명 |
|:--|:--|:--|
| **Mobile** | Android, Kotlin, Jetpack Compose, Hilt | 앱 화면, 카메라/마이크 제어, 상태 관리 |
| **On-Device AI** | MediaPipe, TensorFlow Lite, LiteRT, Qwen LiteRT-LM | 랜드마크 추출, 수어 추론, 온디바이스 문장 변환 |
| **Backend** | Java 21, Spring Boot 4.0.5, Spring Security, JPA | 인증/인가, 자녀 프로필, 학습/퀴즈/리포트 API |
| **AI Server** | Python 3.12, FastAPI, PyTorch | 서버 측 수어 인식 추론 API |
| **Database** | PostgreSQL 16, Redis 7, Flyway | 영속 데이터, 토큰/캐시, 마이그레이션 |
| **External API** | Gemini, NAVER CLOVA TTS/STT/Speech | 문맥 교정, 음성 합성, 음성 인식 |
| **Infra** | Docker, Docker Compose | 로컬/배포 환경 컨테이너 구성 |

---

## 프로젝트 구조

```text
.
├── backend/              # Spring Boot API 서버
├── mobile/               # Android 앱
├── ai/                   # 수어 인식 AI 서버 및 학습/추론 코드
├── docs/                 # API, ERD, 아키텍처, 요구사항 문서
├── exec/                 # 포팅 매뉴얼 및 제출 산출물
├── docker-compose.yml    # Backend, DB, Redis, AI 서버 통합 실행
└── docker-compose.ai.yml # AI 서버 단독 배포용 Compose
```

---

## 주요 도메인

| 도메인 | 설명 |
|:--|:--|
| **Auth** | 회원가입, 로그인, 토큰 재발급, 로그아웃, 네이버 OAuth, 비밀번호 재설정 |
| **Child Profile** | 자녀 프로필 생성, 조회, 수정, 삭제 |
| **Translation** | 수어 → 음성 변환, 음성 → 텍스트 변환, STT 설정 조회 |
| **Communication** | 대화 세션 생성/종료, 대화 메시지 및 분석 관리 |
| **Learn** | 학습 카테고리, 난이도, 단어 목록 조회 |
| **Quiz** | 퀴즈 세션 생성, 현재 문제 조회, 답안 제출, 결과 조회 |
| **Report** | 학습 요약, 카테고리별 진도, 취약 단어, 퀴즈 기록, 대화 요약 |
| **Sign** | 수어 추론 API, FastAPI AI 서버 연동 |

---

## AI 모델

SUDA는 모바일과 서버 양쪽에서 수어 인식을 처리할 수 있도록 모델을 분리해 운영합니다.

| 모델 | 위치 | 용도 |
|:--|:--|:--|
| `best_sign_model_v7_float16.tflite` | Android assets | 온디바이스 수어 인식 |
| `best_sign_model_v6_float16.tflite` | Android assets | 온디바이스 수어 인식 |
| `best_sign_model_v6.pt` | AI server artifact | FastAPI 서버 측 수어 추론 |
| `Qwen2.5 LiteRT-LM` | 별도 다운로드 또는 로컬 경로 | 온디바이스 문장 변환 |

### 모델 특징

- MediaPipe 기반 손/몸/얼굴 랜드마크 추출
- TCN / Transformer 계열 수어 인식 모델 실험
- 모바일 탑재를 위한 TFLite 변환 및 float16 경량화
- 서버 추론용 PyTorch TCN 모델과 FastAPI inference endpoint 제공

---

## API Overview

| Method | Endpoint | 설명 |
|:--|:--|:--|
| `POST` | `/api/v1/auth/signup` | 회원가입 |
| `POST` | `/api/v1/auth/login` | 로그인 |
| `POST` | `/api/v1/auth/refresh` | 토큰 재발급 |
| `POST` | `/api/v1/auth/oauth/naver` | 네이버 OAuth 로그인 |
| `GET` | `/api/v1/children` | 자녀 프로필 목록 조회 |
| `POST` | `/api/v1/children` | 자녀 프로필 생성 |
| `POST` | `/api/v1/translation/sign-to-speech` | 수어 문장 음성 변환 |
| `POST` | `/api/v1/translation/speech-to-text` | 음성 텍스트 변환 |
| `POST` | `/api/v1/comms/sessions` | 대화 세션 생성 |
| `PATCH` | `/api/v1/comms/sessions/{sessionId}/end` | 대화 세션 종료 |
| `GET` | `/api/v1/learn/categories` | 학습 카테고리 조회 |
| `GET` | `/api/v1/learn/words` | 학습 단어 조회 |
| `POST` | `/api/v1/learn/quizzes/sessions` | 퀴즈 세션 생성 |
| `POST` | `/api/v1/sign/inference` | 수어 추론 요청 |
| `GET` | `/api/v1/children/{childId}/reports/summary` | 학습 리포트 요약 |

---

## 실행 방법

### 1. 환경변수 설정

루트에 `.env` 파일을 생성합니다.

```bash
cp .env.example .env
```

주요 환경변수는 다음과 같습니다.

```text
POSTGRES_DB
DB_URL
DB_USERNAME
DB_PASSWORD
REDIS_PASSWORD
AUTH_JWT_SECRET
GMS_KEY
CLOVA_CLIENT_ID
CLOVA_CLIENT_SECRET
CLOVA_SPEECH_SECRET_KEY
NAVER_CLIENT_ID
NAVER_CLIENT_SECRET
SIGN_AI_BASE_URL
BACKEND_SIGN_AI_BASE_URL
SLLM_MODEL_DOWNLOAD_URL
```

---

### 2. Backend 실행

```bash
cd backend
./gradlew bootRun
```

빌드 및 검증:

```bash
cd backend
./gradlew spotlessCheck --no-daemon
./gradlew build -x test --no-daemon
```

---

### 3. Mobile 실행

```bash
cd mobile
./gradlew assembleDebug
```

디바이스 설치:

```bash
./gradlew installDebug
```

검증:

```bash
cd mobile
./gradlew ktlintCheck detekt lintDebug testDebugUnitTest assembleDebug --no-daemon
```

---

### 4. Docker Compose 실행

DB, Redis, Backend를 실행합니다.

```bash
docker compose up -d --build
```

로컬 AI 서버까지 함께 실행하려면 다음 profile을 사용합니다.

```bash
docker compose --profile local-ai up -d --build
```

GPU 서버에서 AI 서버만 단독 배포할 경우:

```bash
docker compose -f docker-compose.ai.yml up -d --build
```

---

## 필수 외부 파일

용량, 라이선스, 보안 문제로 아래 파일은 Git에 포함하지 않습니다.

### MediaPipe Holistic 모델

```text
mobile/app/src/main/assets/models/holistic_landmarker.task
```

다운로드:

```text
https://storage.googleapis.com/mediapipe-models/holistic_landmarker/holistic_landmarker/float16/1/holistic_landmarker.task
```

### AI 서버 모델 artifact

AI 서버 실행 시 artifact 디렉터리에 아래 파일이 필요합니다.

```text
best_sign_model_v6.pt
train_config_v6.json
label_map_v6.json
model.py
```

### Qwen LiteRT-LM 모델

온디바이스 문장 변환용 Qwen 모델은 Git에 포함하지 않습니다.  
`mobile/local.properties`에 로컬 모델 경로를 지정할 수 있습니다.

```properties
QWEN_MODEL_LOCAL_PATH=C:/path/to/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm
```

또는 기본 위치에 배치할 수 있습니다.

```text
mobile/local-models/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm
```

---

## 주요 문서

| 문서 | 경로 |
|:--|:--|
| API 명세 | `docs/api/api-spec.md` |
| ERD | `docs/architecture/erd.md` |
| 시스템 흐름 | `docs/architecture/system-flow.md` |
| 요구사항 명세 | `docs/specs/requirements.md` |
| 기능 명세 | `docs/specs/features.md` |
| 모바일 실행 가이드 | `mobile/README.md` |
| AI 서버 실행 가이드 | `ai/ai-server/README.md` |
| 포팅 매뉴얼 | `docs/porting-manual.md` |

---

## 팀원

<div align="center">

<table>
<tr>
<td align="center" width="160">
<a href="https://github.com/HWISU96">
<img src="https://github.com/HWISU96.png" width="100" style="border-radius:50%"/><br/>
<sub><b>김휘수</b></sub>
</a><br/>
<sub>Android</sub>
</td>

<td align="center" width="160">
<a href="https://github.com/seolsa1014">
<img src="https://github.com/seolsa1014.png" width="100" style="border-radius:50%"/><br/>
<sub><b>설현원</b></sub>
</a><br/>
<sub>AI & Infra</sub>
</td>

<td align="center" width="160">
<a href="https://github.com/minseond">
<img src="https://github.com/minseond.png" width="100" style="border-radius:50%"/><br/>
<sub><b>김민선</b></sub>
</a><br/>
<sub>Backend</sub>
</td>

<td align="center" width="160">
<a href="https://github.com/piequal3141592">
<img src="https://github.com/piequal3141592.png" width="100" style="border-radius:50%"/><br/>
<sub><b>김순우</b></sub>
</a><br/>
<sub>AI</sub>
</td>

<td align="center" width="160">
<a href="https://github.com/yezi720">
<img src="https://github.com/yezi720.png" width="100" style="border-radius:50%"/><br/>
<sub><b>나예지</b></sub>
</a><br/>
<sub>Backend</sub>
</td>

<td align="center" width="160">
<a href="https://github.com/skywalkbee300">
<img src="https://github.com/skywalkbee300.png" width="100" style="border-radius:50%"/><br/>
<sub><b>손홍헌</b></sub>
</a><br/>
<sub>Android</sub>
</td>
</tr>
</table>

</div>

## 보안 및 커밋 제외 항목

아래 파일은 Git에 포함하지 않습니다.

```text
.env
*.pem
*.key
*.pt
*.pth
ai/model-artifacts/
local.properties
keystore.properties
secrets.xml
```

secret, API key, 비밀번호, 개인 로컬 경로는 커밋하지 않습니다.

---

<div align="center">

<br/>

**SUDA**  
농인 부모와 청인 자녀의 일상 대화를 더 자연스럽게.

<br/>

<img src="https://capsule-render.vercel.app/api?type=waving&color=gradient&customColorList=12,18,20&height=100&section=footer" width="100%"/>

</div>
