# API 명세서

본 문서는 무상태(Stateless) API Gateway 아키텍처를 기반으로 한 SUDA(수다) 서비스의 RESTful API 명세입니다.
기존의 WebSocket 스트리밍 및 세션 관리 API는 온디바이스(On-Device) 아키텍처 도입 및 MVP 스코프 조정에 따라 제거되었습니다.

## 1. 공통 규격 (Common)

- **Base URL:** `https://api.suda.com/api/v1` (환경에 따라 도메인 변경)
- **Content-Type:** `application/json`
- **Error Standard:** `RFC 9457` (Problem Details for HTTP APIs) 준수

---

## 2. 수어 -> 음성 번역 API

안드로이드 기기 내부(On-Device)에서 1차 추론된 원시 단어 배열을 서버로 전송하여, 자연스러운 문장 교정(Gemini)과 음성 파일(Clova TTS)을 함께 반환받습니다.

### 2.1. Request
- **URL:** `/translate/sign-to-voice`
- **Method:** `POST`

```json
{
  "gloss_list": ["나", "밥", "먹다"],
  "speaker": "PARENT",
  "target": "CHILD"
}
```

### 2.2. Response (200 OK)

```json
{
  "status": 200,
  "message": "성공적으로 변환되었습니다.",
  "data": {
    "corrected_text": "나 밥 먹었어.",
    "audio_url": "https://api.suda.com/temp-audio/a1b2c3d4.mp3", 
    "audio_duration_ms": 1200 
  }
}
```

---

## 3. 음성 -> 텍스트 변환 API

안드로이드 로컬 STT에서 추출된 아이의 불완전한 발음을 전송하여, **원본**과 **보정본**을 동시에 반환받습니다. (앱 내 UI 토글 버튼 분기용)

### 3.1. Request
- **URL:** `/translate/voice-to-text`
- **Method:** `POST`

```json
{
  "raw_text": "옴마 바방 모고써",
  "speaker": "CHILD",
  "target": "PARENT"
}
```

### 3.2. Response (200 OK)

```json
{
  "status": 200,
  "message": "성공적으로 보정되었습니다.",
  "data": {
    "raw_text": "옴마 바방 모고써",            
    "corrected_text": "엄마 밥 먹었어",        
    "confidence_score": 0.85               
  }
}
```

---

## 4. 에러 응답 규격 (Error Response - RFC 9457)

외부 AI API 지연이나 잘못된 요청 발생 시, RFC 9457 표준에 맞춘 에러 객체를 반환합니다.

### 4.1. 외부 API(Gemini/Clova) 타임아웃 예시 (504 Gateway Timeout)

```json
{
  "type": "https://api.suda.com/errors/ai-timeout",
  "title": "AI Service Timeout",
  "status": 504,
  "detail": "Gemini 서버로부터 응답을 받는 데 시간이 초과되었습니다. 다시 시도해 주세요.",
  "instance": "/translate/voice-to-text"
}
```

### 4.2. 잘못된 파라미터 예시 (400 Bad Request)

```json
{
  "type": "https://api.suda.com/errors/invalid-request",
  "title": "Invalid Request Parameters",
  "status": 400,
  "detail": "gloss_list 배열이 비어있습니다. 최소 1개 이상의 단어가 필요합니다.",
  "instance": "/translate/sign-to-voice"
}
```

---

## 5. 향후 확장 계획 (Future Plan)
- **`/comms/sessions`**: 정식 버전에서 대화 이력 및 통계 제공 시 추가
- **`/comms/feedbacks`**: 오번역 데이터 수집을 위한 DB 연동 시 추가
