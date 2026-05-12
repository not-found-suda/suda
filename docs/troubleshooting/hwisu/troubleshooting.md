# Troubleshooting - hwisu

이 문서는 APP-A 작업 중 반복해서 마주칠 수 있는 문제와 해결 과정을 정리한 개인 트러블슈팅 노트다.

## 기록 규칙

- 문제를 발견한 날짜, 작업 브랜치, 재현 절차를 함께 남긴다.
- 원인과 해결 방법이 확정되지 않았으면 "가설"로 구분해서 적는다.
- 로그나 에러 메시지는 필요한 부분만 붙이고, Secret/Env/개인정보는 제외한다.
- 모바일에서 직접 해결할 수 있는 일과 백엔드에 요청해야 하는 일을 구분해서 남긴다.

## 2026-05-12 - 로컬 백엔드 접속 IP 설정 오류

### 상황

- 작업 위치나 Wi-Fi가 바뀐 뒤 모바일 앱에서 로그인/API 호출이 실패했다.
- 앱에서는 "네트워크 연결을 확인해 주세요." 계열 메시지가 표시되었다.

### 재현 절차

- 휴대폰과 백엔드 서버 실행 PC를 같은 Wi-Fi에 연결한다.
- `.env`의 `MOBILE_API_BASE_URL`을 이전 네트워크 IP 또는 WSL 가상 어댑터 IP로 둔다.
- 앱에서 로그인 또는 보호 API 호출을 시도한다.

### 원인

- 실기기는 PC의 WSL 가상 어댑터 IP(`172.26.x.x`)에 접근할 수 없다.
- 실기기 테스트에서는 Wi-Fi 어댑터의 IPv4 주소를 사용해야 한다.
- Android Emulator라면 `10.0.2.2`를 사용할 수 있지만, 실제 휴대폰에서는 PC의 같은 네트워크 IP가 필요하다.

### 해결

- Windows에서 `ipconfig`로 현재 Wi-Fi IPv4를 확인한다.
- `.env`의 `MOBILE_API_BASE_URL`을 아래 형태로 수정한다.

```properties
MOBILE_API_BASE_URL=http://{PC_WIFI_IPV4}:8080/api/
```

### 재발 방지 / 체크 포인트

- 작업 장소나 Wi-Fi가 바뀌면 `.env`의 API Base URL을 먼저 확인한다.
- 백엔드 서버가 `0.0.0.0` 또는 외부 접속 가능한 인터페이스로 떠 있는지도 확인한다.
- 방화벽이 8080 포트를 막고 있지 않은지 확인한다.

### 관련 작업

- 네이버 로그인 실기기 QA
- 학습/리포트 API 실기기 QA

## 2026-05-12 - Flyway 미적용으로 학습 카테고리 API 500 발생

### 상황

- 로그인 후 학습 탭 진입 시 "카테고리를 불러오지 못했습니다. 다시 시도해주세요."가 계속 표시되었다.
- Logcat에서는 `GET /api/v1/learn/categories`가 500으로 실패했다.

### 재현 절차

- 로컬 백엔드 DB가 최신 migration이 적용되지 않은 상태에서 앱 실행
- 로그인 후 학습 탭 진입

### 원인

- 백엔드 로컬 환경에서 Flyway migration이 비활성화되어 DB schema/seed가 최신 코드와 맞지 않았다.

### 해결

- 백엔드 `.env`에 아래 설정을 추가하고 백엔드를 재실행했다.

```properties
SPRING_FLYWAY_ENABLED=true
```

### 재발 방지 / 체크 포인트

- API가 500이면 모바일 DTO 문제로 단정하지 말고 백엔드 로그와 DB migration 상태를 함께 확인한다.
- 로컬 DB를 새로 만들거나 백엔드 브랜치를 전환한 뒤에는 Flyway 적용 여부를 확인한다.

### 관련 작업

- S14P31A404-237 학습 카테고리 선택 화면
- S14P31A404-238 카테고리별 단어 목록 화면

## 2026-05-12 - API 응답 wrapper 불일치로 Gson 역직렬화 실패

### 상황

- API는 200 OK를 반환하지만 화면에는 오류 상태가 표시되었다.
- Logcat에 아래와 유사한 Gson 에러가 발생했다.

```text
Expected BEGIN_OBJECT but was BEGIN_ARRAY at line 1 column 2 path $
```

또는 반대로:

```text
Expected BEGIN_ARRAY but was BEGIN_OBJECT
```

### 재현 절차

- 백엔드 응답이 `{ "categories": [...] }`, `{ "words": [...] }`, `{ "children": [...] }` 같은 wrapper 형태인데 모바일 DTO가 `List<T>`를 직접 기대한다.
- 또는 백엔드는 배열을 직접 내려주는데 모바일 DTO가 wrapper를 기대한다.

### 원인

- 모바일 Retrofit 반환 타입과 백엔드 실제 JSON shape이 맞지 않았다.
- 문서/브랜치/머지 순서에 따라 wrapper 도입 시점이 달라지며 발생했다.

### 해결

- 백엔드 실제 응답 기준으로 DTO를 맞췄다.
- 예시:
  - 아이 목록: `ChildProfileListResponseDto(children: List<ChildProfileResponseDto>)`
  - 단어 목록: `LearningWordsResponseDto(words: List<LearningWordDto>)`
  - 리포트 카테고리: `ReportCategoryProgressListResponseDto(categories: List<ReportCategoryProgressDto>)`

### 재발 방지 / 체크 포인트

- API 연동 전에는 Swagger/Notion 문서뿐 아니라 최신 develop 또는 MR의 Controller/Response DTO도 확인한다.
- Logcat에서 200 OK 후 Gson 에러가 발생하면 wrapper 불일치를 가장 먼저 의심한다.
- MR 리뷰 시 "백엔드 MR만 먼저 머지되면 모바일 DTO와 계약이 맞는가"를 체크한다.

### 관련 작업

- S14P31A404-238 카테고리별 단어 목록 화면
- S14P31A404-296 아이 프로필 API 연동
- S14P31A404-243 리포트 홈 요약 API 연동
- S14P31A404-244 리포트 취약 단어 목록 화면
- S14P31A404-264 리포트 카테고리별 진행도 화면

## 2026-05-12 - Retrofit endpoint에 `api/` 중복 포함

### 상황

- 특정 API 호출이 예상 경로가 아니라 `/api/api/v1/...` 형태로 조합될 위험이 있었다.

### 원인

- `.env`의 `MOBILE_API_BASE_URL`이 `http://host:8080/api/` 형태다.
- Retrofit service endpoint에 `api/v1/...`를 쓰면 최종 URL이 `/api/api/v1/...`로 중복된다.

### 해결

- Retrofit endpoint는 `v1/...` 형태로 작성한다.

```kotlin
@POST("v1/auth/refresh")
```

### 재발 방지 / 체크 포인트

- 모바일 Base URL에 이미 `/api/`가 포함되어 있음을 기억한다.
- 새 API service를 만들 때 기존 service의 endpoint 작성 패턴을 먼저 확인한다.

### 관련 작업

- S14P31A404-294 모바일 인증 API 최신 명세 반영
- 학습/리포트 API 전반

## 2026-05-12 - 네이버 로그인 PKCE 방식 실패 및 SDK 방식 전환

### 상황

- 네이버 로그인 버튼 클릭 시 네이버 인증 화면에서 "SUDA 서비스 설정에 오류가 있어서 로그인할 수 없습니다." 계열 화면이 표시되었다.
- 기존 구현은 Authorization Code + PKCE + Custom Tabs + custom scheme redirect 구조였다.

### 원인

- 네이버 Android 환경에서는 개발자 콘솔에서 웹 callback URL 기반 PKCE 흐름을 그대로 쓰기 어렵고, Android SDK 기반 로그인이 더 적합하다는 결론이 났다.
- 백엔드도 최종적으로 provider access token 검증 방식으로 전환했다.

### 해결

- 모바일은 네이버 Android SDK를 통해 provider access token을 발급받는다.
- 백엔드에는 아래 body로 전달한다.

```json
{
  "providerAccessToken": "..."
}
```

- 백엔드는 provider access token으로 네이버 프로필 API를 호출하고, 우리 서비스 accessToken/refreshToken을 발급한다.

### 재발 방지 / 체크 포인트

- AndroidManifest의 PKCE redirect intent-filter와 MainActivity redirect 처리 로직은 SDK 방식에서는 필요 없다.
- 모바일에는 `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET`, `NAVER_CLIENT_NAME`이 필요하다.
- 네이버 개발자 콘솔에 Android 패키지명 `com.ssafy.mobile`과 테스트 계정 등록 여부를 확인한다.
- 백엔드 실패 시 status만 보지 말고 Problem Details의 `code`, `traceId`, `instance`를 로그/예외에 보존한다.

### 관련 작업

- S14P31A404-233 네이버 SDK 기반 로그인 연동

## 2026-05-12 - 비로그인 소통 기능이 앱 전체 로그인 가드에 막힘

### 상황

- 서비스 기획상 양방향 소통 기능은 비로그인 사용자도 사용할 수 있어야 했다.
- 하지만 앱 시작 시 로그인하지 않으면 모든 기능이 막히는 구조였다.

### 원인

- 앱 진입점과 하단 탭 접근 제어가 전체 인증 상태 중심으로 구성되어 있었다.
- Conversation 화면과 translation API가 비로그인 허용 정책임에도 모바일에서 로그인 범위를 넓게 잡고 있었다.

### 해결

- AppEntry에서 비로그인 사용자를 Conversation으로 진입시켰다.
- 하단 탭에서 Conversation은 비로그인 허용, Home/Learning/Report/MyPage는 로그인 필요 화면으로 분기했다.
- Conversation API는 `@Named("NoAuth") Retrofit`을 사용하도록 정리했다.
- 백엔드 미구현 상태인 translation feedback 신고 버튼은 feature flag로 숨겼다.

### 재발 방지 / 체크 포인트

- 기능별 인증 정책을 먼저 확인한다.
- 보호자/아이/학습/리포트처럼 개인 데이터가 필요한 기능과 공개 기능을 분리한다.
- 비로그인 허용 API는 AuthInterceptor가 붙은 Retrofit을 쓰지 않도록 주의한다.

### 관련 작업

- S14P31A404-224 로그인 범위 수정 작업

## 2026-05-12 - S3/CloudFront 이미지 및 오디오 리소스 접근 실패

### 상황

- 학습 이미지 또는 단어 음성 URL이 내려와도 로딩/재생이 실패할 수 있었다.
- S3 URL 직접 접근 시 `AccessDenied`가 발생했다.

### 원인

- S3 object 접근 권한 또는 CloudFront URL 변환 정책이 정리되기 전이었다.
- 일부 데이터는 object key가 아니라 이미 http(s) URL일 가능성도 있었다.

### 해결

- 모바일 이미지 로딩은 `AppNetworkImage`로 통합하고 null/blank/error fallback UI를 제공했다.
- 단어장 음성 재생은 `audioUrl` 접근 실패 시 사용자에게 "재생 실패" 상태를 보여주고 학습 진행은 막지 않도록 했다.
- 백엔드는 리소스 key를 CloudFront URL로 변환하고, 이미 URL인 값은 그대로 반환하도록 방어 로직을 추가했다.

### 재발 방지 / 체크 포인트

- 리소스 API 응답의 URL은 모바일에서 그대로 접근 가능한 public URL이어야 한다.
- URL이 없거나 접근 실패해도 UI 레이아웃이 깨지지 않아야 한다.
- `imageUrl`, `thumbnailUrl`, `audioUrl`은 null/blank 방어가 필요하다.

### 관련 작업

- S14P31A404-239 S3 학습 이미지 로딩 및 실패 대체 UI 구현
- S14P31A404-240 단어장 음성 재생 및 카드형 학습 화면 구현
- 백엔드 S14P31A404-319 학습 이미지 및 음성 리소스 key 기반 URL 변환

## 2026-05-12 - 퀴즈 current question에 targetText가 없어 words 선조회에 의존

### 상황

- 퀴즈 화면에서 문제 단어 텍스트를 표시하고 로컬 상태를 구성하기 위해 퀴즈 시작 전에 `GET /learn/words`를 먼저 호출했다.
- 단어 목록 조회가 실패하면 퀴즈 세션 생성 자체가 막힐 수 있었다.

### 원인

- 기존 current question 응답에는 `wordId`와 `imageUrl`만 있고 정답 단어 텍스트가 없었다.
- 모바일은 `wordId -> word` 매핑을 만들기 위해 별도 words 조회에 의존했다.
- 서버가 랜덤 퀴즈 세션을 생성하므로 words 목록과 실제 세션 문제가 항상 일치한다고 보장하기 어렵다.

### 해결

- 백엔드 current question 응답에 `targetText`를 추가했다.
- 모바일은 current question의 `targetText`를 기준으로 문제 단어를 표시하도록 수정했다.
- 퀴즈 시작 전 words 선조회와 `LearningWordRepository` 의존성을 제거했다.

### 재발 방지 / 체크 포인트

- 퀴즈 진행에 필요한 데이터는 current question 응답 하나로 충분해야 한다.
- 랜덤 세션/랜덤 문제를 다루는 API에서 별도 목록 조회 결과를 매핑 기준으로 삼지 않는다.
- 응답 필드 누락 시 백엔드 API 계약을 먼저 보강한다.

### 관련 작업

- S14P31A404-302 퀴즈 현재 문제 targetText 반영 및 words 선조회 제거
- 백엔드 S14P31A404-361 퀴즈 현재 문제 응답에 targetText 추가

## 2026-05-12 - 퀴즈 답변 제출 JSON 방식과 백엔드 Multipart 방식 불일치

### 상황

- 퀴즈 답변 제출 시 "서버에서 퀴즈 요청 처리에 실패했습니다."가 표시되며 진행이 막혔다.

### 원인

- 모바일 초기 구현은 로컬 STT 결과인 `recognizedText`를 JSON으로 제출하는 임시 흐름이었다.
- 백엔드 최종 구현은 `multipart/form-data`로 음성 파일을 받고, 서버에서 Clova STT 및 채점을 수행하는 구조였다.

### 해결

- 모바일 답변 제출을 multipart 방식으로 전환했다.
- 요청:

```text
POST /api/v1/learn/quizzes/sessions/{sessionId}/answers
Content-Type: multipart/form-data

questionId: form field
audioFile: file part, part name "audioFile"
```

- AndroidAudioRecorder로 WAV 파일을 만들고 `audio/wav` MIME과 `.wav` 파일명을 맞췄다.
- 서버 응답의 `recognizedText`, `star`, `isCorrect`, `feedback`, `hasNext`, `nextQuestionNumber`를 UI 기준으로 사용했다.

### 재발 방지 / 체크 포인트

- API가 파일 업로드인지 JSON 제출인지 먼저 확인한다.
- 파일 part name, MIME, 확장자가 실제 생성 파일과 맞는지 확인한다.
- 답변 저장 실패 상태에서도 새 녹음을 허용해 같은 실패 파일만 반복 제출하지 않도록 한다.
- 기존 JSON 오프라인 큐는 multipart 파일 재전송과 계약이 달라 별도 설계가 필요하다.

### 관련 작업

- S14P31A404-276 퀴즈 답변 제출 서버 채점 흐름 정렬
- S14P31A404-303 퀴즈 답변 제출 Multipart 서버 채점 연동

## 2026-05-12 - 로컬 퀴즈 채점과 서버 채점이 혼재

### 상황

- 모바일에서 `QuizStarScorer`로 로컬 채점을 수행하고, 서버도 답변을 채점하는 구조가 섞였다.
- 서버 응답과 모바일 로컬 판정이 달라질 수 있었다.

### 원인

- 백엔드 채점 API가 완성되기 전 모바일에서 임시 로컬 채점 로직을 사용했다.
- 이후 백엔드가 STT/채점 결과를 내려주도록 구현되며 기준이 서버로 이동했다.

### 해결

- 실제 서비스 플로우에서 `QuizStarScorer.score()` 호출을 제거했다.
- `QuizAnswer.star`, `isCorrect`를 nullable로 두고 서버 응답 전/후 상태를 구분했다.
- 제출 중에는 "답변을 확인하고 있어요" 상태를 보여줬다.
- 다음 문제 이동은 서버 채점 결과가 반영된 뒤에만 허용했다.
- `QuizStarScorer`는 즉시 삭제하지 않고 `@Deprecated` 처리해 히스토리와 테스트 맥락을 남겼다.

### 재발 방지 / 체크 포인트

- 채점 결과의 Single Source of Truth는 서버 응답이다.
- 서버 응답 전에는 확정 별점/정답 여부를 UI에 표시하지 않는다.
- 중복 제출 방지를 위해 코루틴 시작 전 `Submitting` 상태를 먼저 세팅한다.

### 관련 작업

- S14P31A404-276 퀴즈 답변 제출 서버 채점 흐름 정렬

## 2026-05-12 - 리포트 API 계약 변경 대응

### 상황

- 리포트 API 명세가 구현 중 여러 차례 변경되었다.
- summary, weak words, categories, sessions API마다 page 기준, 집계 기준, 필드명 차이가 있었다.

### 주요 변경/확정 사항

- summary
  - `totalSessionCount` 제거
  - `completedSessionCount`만 사용
  - 완료된 퀴즈 세션(`COMPLETED`)만 집계
  - 기록 없음은 `200 OK` + count 0 / rate 0.0 / latest null / empty list
- averageStar
  - 퀴즈 별점 정책과 동일하게 1~3점 기준
- 시간 필드
  - offset 없는 `LocalDateTime`
  - 서버 기준 KST로 표시
- pagination
  - `page=1`이 첫 페이지
  - 응답 page도 1-base
- categories
  - 초기 구현은 완료된 퀴즈 기록이 있는 카테고리만 반환
  - `sessionCount` 제거, `completedSessionCount`만 사용

### 해결

- 모바일 DTO와 domain model을 최신 필드 기준으로 정렬했다.
- summary/weak words/category progress 화면에서 완료된 기록 기준 데이터를 표시했다.
- 기간/카테고리/난이도 필터는 S14P31A404-267로 분리했다.

### 재발 방지 / 체크 포인트

- 리포트 API는 필드명과 집계 기준이 화면 의미에 직접 연결되므로 MR마다 DTO diff를 확인한다.
- count 필드 타입이 백엔드에서 Long이면 모바일 DTO/domain도 Long으로 맞춘다.
- page 기준이 0-base인지 1-base인지 반드시 확인한다.

### 관련 작업

- S14P31A404-243 리포트 홈 요약 API 연동
- S14P31A404-244 리포트 취약 단어 목록 화면 구현
- S14P31A404-264 리포트 카테고리별 진행도 화면 구현

## 2026-05-12 - CI 실패 원인이 현재 MR과 무관한 flaky test였던 사례

### 상황

- 퀴즈 multipart MR에서 GitLab CI가 실패했다.
- 실패 로그의 실제 실패 지점은 퀴즈 코드가 아니라 sign 테스트였다.

### 실패 로그 요약

```text
RealSignRecognitionEngineTest > emitsPredictionWhenSequenceIsReady FAILED
kotlinx.coroutines.TimeoutCancellationException
```

### 원인

- 해당 MR 변경 범위와 직접 관련 없는 수어 인식 테스트 timeout이었다.
- 로컬 단독 재실행 시 통과하여 flaky 가능성이 높았다.

### 해결

- failed job 로그에서 실제 실패 test class/method를 확인했다.
- 변경 파일과 실패 테스트의 관련성을 분리해서 리뷰어에게 설명했다.

### 재발 방지 / 체크 포인트

- CI 실패 시 전체 로그에서 `FAILED` test method를 먼저 찾는다.
- ktlint/detekt/compile 실패인지, unit test timeout인지 구분한다.
- 변경 범위와 무관한 flaky 가능성이 있더라도 로컬 재현 여부를 확인하고 기록한다.

### 관련 작업

- S14P31A404-303 퀴즈 답변 제출 Multipart 서버 채점 연동
