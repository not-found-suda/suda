# Git Flow 브랜치 전략 (Branching Strategy)

우리 프로젝트는 **Git Flow**를 기반으로 하되, Jira 이슈 트래킹 효율성을 위해 단순화된 프로세스를 사용합니다. AI 에이전트와 팀원 모두가 이 흐름을 준수합니다.

## 1. 주요 브랜치 (Main Branches)

| 브랜치       | 설명 | 보호 여부 |
|:----------| :--- | :--- |
| `main`    | 제품으로 출시되는 최종 브랜치 (배포용) | ✅ Protected |
| `develop` | 다음 출시 버전을 위해 개발 중인 기능을 합치는 브랜치 | ✅ Protected |

## 2. 보조 브랜치 (Supporting Branches)

| 브랜치 | 목적 | 생성 위치     | 합쳐질 곳             |
| :--- | :--- |:----------|:------------------|
| `feature/` | 새로운 기능 개발 및 문서 작업 | `develop` | `develop`         |
| `hotfix/` | 출시 버전의 긴급 버그 수정 | `main`    | `main`, `develop` |

## 3. 작업 프로세스 (Work Step-by-Step)

### ① 이슈 확인 및 브랜치 생성
1. Jira에서 할당된 티켓(예: `S14P31A404-124`)을 확인합니다.
2. 로컬의 `develop` 브랜치를 최신 상태로 유지합니다 (`git pull origin develop`).
3. 규칙에 따라 작업 브랜치를 생성합니다.
  - **형식:** `feature/{Jira-Issue-Key}-{Description}`
  - **예시:** `feature/S14P31A404-124-docs-rules`

### ② 코드 작업 및 커밋
1. 로컬 환경에서 작업을 수행합니다.
2. `docs/rules/commit-convention.md`에 정의된 **커밋 컨벤션**에 따라 커밋을 남깁니다.

### ③ 푸시 및 Merge Request (MR) 생성
1. 원격 저장소에 푸시합니다: `git push origin {Branch-Name}`
2. GitLab에서 `develop` 브랜치를 대상으로 MR을 생성합니다.
3. **MR 내용에 Jira 이슈 번호를 기입**하여 티켓과 연동합니다.
  - **예시:** `[S14P31A404-124] 팀 협업 규칙 문서 초기 세팅`

### ④ 코드 리뷰 및 병합
1. 팀원 및 AI 에이전트의 리뷰를 거칩니다.
2. 최소 1명 이상의 Approve를 획득한 후 Merge를 진행합니다.
3. 병합이 완료된 로컬 브랜치는 삭제하여 정리합니다.

## 4. AI 에이전트 활용 가이드
- AI 에이전트에게 코딩이나 문서 수정을 요청할 때, 이 파일을 컨텍스트로 제공하여 브랜치 생성 및 작업 방향을 스스로 잡을 수 있도록 유도합니다.
