# 🏛️ 시스템 아키텍처 및 데이터 흐름도 (System Flow)

본 문서는 SUDA 프로젝트의 온디바이스(On-Device) AI 처리 과정과 Spring Boot API Gateway를 통한 외부 API(Gemini, CLOVA) 연동 시퀀스를 정의합니다.

## 1. 전체 시스템 시퀀스 다이어그램 (Sequence Diagram)

```mermaid
sequenceDiagram
    autonumber
    actor Parent as 부모 (농인)
    actor Child as 자녀 (청인)
    participant App as Android App (On-Device)
    participant Server as Spring Boot (API Gateway)
    participant Gemini as Gemini API (LLM)
    participant Clova as CLOVA API (TTS)

    %% Flow 1: 수어 -> 음성
    rect rgb(240, 248, 255)
    Note over Parent, Clova: [Flow 1] 부모(수어) -> 자녀(음성)
    Parent->>App: 수어 동작 (CameraX 15~30fps)
    Note over App: [On-Device] MediaPipe 랜드마크 추출
    Note over App: [On-Device] TFLite (LSTM) 원시 단어 추론
    App->>Server: 원시 단어 배열 전송 (POST /api/v1/translate/sign)
    
    Server->>Gemini: 문맥 교정 프롬프트 전송
    Gemini-->>Server: 자연스러운 문장 반환
    
    Server->>Clova: 교정된 텍스트 음성 합성 요청
    Clova-->>Server: 오디오 데이터 반환
    
    Server-->>App: 최종 텍스트 + 오디오 데이터 응답
    
    Note over App: [에코 캔슬링] 로컬 STT 일시 정지 (Mute)
    App-->>Child: 스피커 재생 & 화면 자막 렌더링
    Note over App: 재생 완료 후 로컬 STT 재개
    end

    %% Flow 2: 음성 -> 텍스트 (UI 토글 반영)
    rect rgb(255, 250, 240)
    Note over Child, Gemini: [Flow 2] 자녀(음성) -> 부모(텍스트)
    Child->>App: 아이 음성 발화 (Mic)
    Note over App: [On-Device] Android 내장 STT (1차 변환)
    App->>Server: 영유아 발음 텍스트 전송 (POST /api/v1/translate/voice)
    
    Server->>Gemini: 발음 보정 및 교정 프롬프트 전송
    Gemini-->>Server: 보정된 단어 반환
    
    Note right of Server: 원본("옴마")과 보정본("엄마")<br/>데이터를 JSON으로 병합
    Server-->>App: 원본 텍스트 + 보정된 텍스트 동시 응답
    
    Note over Parent, App: [UI 제어] 부모의 토글 선택 (원본 / 보정본 / 모두 보기)
    App-->>Parent: 선택된 모드에 맞추어 화면 자막 렌더링
    end
```

## 2. 단계별 데이터 흐름 상세 (Text Description)
> AI 에이전트의 컨텍스트 파악을 위한 텍스트 설명입니다.

### [Flow 1] 부모(수어) -> 자녀(음성) 흐름
1. **On-Device 처리:** 카메라 프레임에서 MediaPipe 좌표를 추출하고, TFLite 모델이 200ms 이내에 원시 단어(Gloss)를 도출합니다.
2. **Server 교정:** 안드로이드 앱이 원시 단어들을 보내면, Spring Boot가 Gemini API를 통해 자연스러운 문장으로 교정합니다.
3. **TTS 합성 및 반환:** 교정된 문장을 CLOVA TTS로 음성 파일화하여 앱으로 반환합니다.
4. **출력 및 제어:** 앱은 자막을 띄우고 스피커로 출력하며, **출력 중에는 에코 캔슬링을 위해 로컬 STT를 잠시 음소거(Mute)합니다.**

### [Flow 2] 자녀(음성) -> 부모(텍스트) 흐름
1. **On-Device STT:** 자녀의 발화를 외부 전송 없이 안드로이드 기기 내장의 `SpeechRecognizer`로 1차 텍스트화합니다. (예: "옴마")
2. **Server 발음 보정:** 1차 텍스트를 서버로 보내면, Gemini API가 영유아 발음 특성을 고려해 문맥에 맞는 보정 텍스트(예: "엄마")를 생성합니다.
3. **듀얼 응답(Dual Response):** 서버는 **원본 텍스트와 보정된 텍스트를 모두 포함한 JSON**을 앱으로 응답합니다.
4. **UI 맞춤 렌더링:** 안드로이드 앱은 사용자가 누른 **토글 버튼 상태(원본만 / 보정본만 / 모두 보기)**에 따라 수신된 데이터를 파싱하여 자막으로 표시합니다.
