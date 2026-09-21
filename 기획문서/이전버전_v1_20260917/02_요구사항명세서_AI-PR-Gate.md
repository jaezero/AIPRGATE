# 요구사항 명세서 (SRS) — AI-PR Gate

| 항목 | 내용 |
|---|---|
| 시스템명 | AI-PR Gate: AI 생성 코드 PR 자동 검증 및 승인 게이트 |
| 관련 문서 | 01_PRD_AI생성코드_PR자동검증게이트.md |
| 버전 | v1.0 (2026-09-17) |
| 우선순위 표기 | **M** = Must(필수), **S** = Should(권장), **C** = Could(여유 시) |

---

## 1. 시스템 개요

### 1.1 목적
안드로이드 샘플 앱 저장소에서 PR이 생성·갱신될 때 자동으로 빌드·테스트·정적분석·보안검사를 수행하고, 정의된 품질 기준을 통과한 PR만 리뷰어 승인 후 main 브랜치에 병합되도록 강제하는 시스템.

### 1.2 시스템 경계
```
┌──────────────────────── AI-PR Gate 시스템 범위 ────────────────────────┐
│  [샘플 앱 저장소]  [협업 규칙]  [CI 워크플로우]  [검증 도구 설정]       │
│  [AI 리뷰 봇]      [품질 게이트 스크립트]  [브랜치 보호 규칙]  [문서]  │
└─────────────────────────────────────────────────────────────────────────┘
        ▲ 입력: PR 이벤트(open/synchronize)          ▼ 출력: 상태 체크, PR 코멘트, 병합 허용/차단
   외부: GitHub(플랫폼), AI 코딩 도구(코드 생성원), LLM API(리뷰 봇)
```

### 1.3 용어
| 용어 | 정의 |
|---|---|
| PR | Pull Request. feature 브랜치의 변경을 main에 병합 요청하는 단위 |
| 게이트(Gate) | 병합 전 반드시 통과해야 하는 자동 검증 관문 |
| 필수 체크(Required Check) | 브랜치 보호 규칙에서 병합 조건으로 지정된 GitHub 상태 체크 |
| 차단(Block) | 게이트 실패로 병합 버튼이 비활성화되는 상태 |
| 경고(Warn) | 결과는 코멘트로 보고하되 병합은 막지 않는 상태 |
| 취약 시나리오 | 게이트 검증을 위해 의도적으로 문제를 넣은 테스트용 PR |

---

## 2. 기능 요구사항 (Functional Requirements)

### FR-1. 검증 대상 샘플 앱
| ID | 요구사항 | 우선순위 |
|---|---|---|
| FR-1.1 | Kotlin + Jetpack Compose 기반 안드로이드 앱이어야 하며, 최소 2개 이상의 화면(목록/상세 또는 목록/작성)을 가진다. | M |
| FR-1.2 | 로컬 DB(Room)를 사용하는 CRUD 기능 1종을 포함한다. (예: 메모/할일 저장) | M |
| FR-1.3 | 외부 네트워크 호출(Retrofit) 1종을 포함하여 보안 검사(시크릿·네트워크 설정)가 의미 있게 동작하도록 한다. | M |
| FR-1.4 | ViewModel/Repository 계층에 단위 테스트가 가능한 구조(의존성 주입 가능)여야 한다. | M |
| FR-1.5 | 앱 코드의 대부분은 AI 코딩 도구(Copilot/Cursor/Claude 등)로 생성하고, 생성 과정과 발견된 위험 패턴을 기록한다. | S |

### FR-2. 협업 규칙 및 PR 워크플로우
| ID | 요구사항 | 우선순위 |
|---|---|---|
| FR-2.1 | main 브랜치 직접 push를 금지하고, 모든 변경은 feature 브랜치 → PR을 통해서만 반영한다. | M |
| FR-2.2 | 브랜치 명명 규칙(`feat/`, `fix/`, `chore/` 접두사)과 Conventional Commits 커밋 규칙을 문서화한다. | M |
| FR-2.3 | PR 템플릿을 제공하며 다음 항목을 필수로 포함한다: 변경 요약, **AI 생성 코드 여부 및 사용 도구**, **작성자가 자기 말로 설명한 코드 동작**, 테스트 방법, 셀프 체크리스트. | M |
| FR-2.4 | PR 템플릿의 필수 항목(AI 생성 여부, 자기 설명)이 비어 있으면 게이트가 실패한다. | S |
| FR-2.5 | CODEOWNERS로 디렉터리별 기본 리뷰어를 지정한다. | S |
| FR-2.6 | AI 생성 코드가 포함된 PR에는 `ai-generated` 라벨이 자동 부착된다. | C |

### FR-3. CI 파이프라인 (GitHub Actions)
| ID | 요구사항 | 우선순위 |
|---|---|---|
| FR-3.1 | PR `opened`, `synchronize`, `reopened` 이벤트에서 워크플로우가 자동 실행된다. | M |
| FR-3.2 | 빌드 job: `./gradlew assembleDebug`를 실행하고 실패 시 이후 job을 건너뛰고 차단한다. | M |
| FR-3.3 | 테스트 job: `./gradlew testDebugUnitTest`를 실행하고 실패한 테스트가 1건이라도 있으면 차단한다. | M |
| FR-3.4 | 커버리지 측정(Kover 또는 JaCoCo)을 수행하고, 핵심 패키지(viewmodel, repository) 라인 커버리지 임계값(초기 60%) 미달 시 차단한다. | M |
| FR-3.5 | 빌드·테스트·정적분석·보안검사 job은 독립적으로 병렬 실행 가능해야 한다. | S |
| FR-3.6 | Gradle 및 의존성 캐시를 적용하여 반복 실행 시간을 단축한다. | S |
| FR-3.7 | 각 job의 결과 리포트(HTML/XML/SARIF)를 아티팩트로 업로드한다. | S |

### FR-4. 정적 분석
| ID | 요구사항 | 우선순위 |
|---|---|---|
| FR-4.1 | ktlint로 코드 스타일을 검사하며, 위반 시 차단한다. | M |
| FR-4.2 | Detekt로 코드 품질을 검사하며, 다음 룰 위반 시 차단한다: `LongMethod`(60줄), `ComplexMethod`(15), `EmptyCatchBlock`, `TooGenericExceptionCaught`, `MagicNumber`(경고). | M |
| FR-4.3 | Android Lint를 실행하고 `Error` 등급 이슈 발생 시 차단한다. (`Warning`은 코멘트) | M |
| FR-4.4 | 임계값과 룰셋은 설정 파일(`detekt.yml`, `.editorconfig`, `lint.xml`)로 관리하고 문서화한다. | M |
| FR-4.5 | AI 생성 코드 특화 커스텀 Detekt 룰 1종 이상을 추가한다. (예: 코루틴 밖 네트워크 호출 탐지) | C |

### FR-5. 보안 검사
| ID | 요구사항 | 우선순위 |
|---|---|---|
| FR-5.1 | 시크릿 스캔(gitleaks)을 수행하여 API 키·토큰·비밀번호 하드코딩 발견 시 차단한다. | M |
| FR-5.2 | SAST(Semgrep, 안드로이드/Kotlin 룰셋)를 수행하여 `HIGH` 이상 이슈 발견 시 차단한다. | M |
| FR-5.3 | 의존성 취약점 검사(OWASP Dependency-Check 또는 Trivy)를 수행하여 `CRITICAL` 취약 라이브러리 발견 시 차단, `HIGH`는 경고한다. | S |
| FR-5.4 | AndroidManifest 검사: `usesCleartextTraffic="true"`, `debuggable="true"`, `allowBackup="true"`, 불필요한 위험 권한을 탐지하여 차단한다. | M |
| FR-5.5 | 인증서 검증 우회 코드(모든 인증서를 신뢰하는 `X509TrustManager`, `HostnameVerifier` 무력화)를 탐지하여 차단한다. | M |
| FR-5.6 | 보안 결과를 SARIF로 업로드하여 GitHub Security 탭에서 확인 가능하게 한다. | C |

### FR-6. AI 리뷰 봇
| ID | 요구사항 | 우선순위 |
|---|---|---|
| FR-6.1 | PR diff를 LLM API(Claude)에 전달하여 리뷰 코멘트를 PR에 자동 게시한다. | S |
| FR-6.2 | 리뷰 프롬프트는 AI 생성 코드 위험 패턴(보안, 예외 처리, 성능, 가독성, 테스트 필요성)을 중심으로 구성한다. | S |
| FR-6.3 | AI 리뷰는 **차단 조건이 아닌 경고**로 동작하며, API 실패 시 워크플로우 전체가 실패하지 않는다. | S |
| FR-6.4 | API 키는 GitHub Secrets로 관리하고, 호출당 diff 크기 상한(예: 4,000줄)을 둔다. | S |
| FR-6.5 | 리뷰 결과에 "작성자가 반드시 이해하고 답변해야 할 질문 3개"를 포함한다. | C |

### FR-7. 품질 게이트 및 승인 프로세스
| ID | 요구사항 | 우선순위 |
|---|---|---|
| FR-7.1 | 빌드·테스트·정적분석·보안검사 결과를 집계하여 단일 `quality-gate` 상태 체크로 통과/실패를 결정한다. | M |
| FR-7.2 | 게이트 결과 요약 리포트(항목별 ✅/❌, 이슈 건수, 링크)를 PR 코멘트로 게시하며, 재실행 시 기존 코멘트를 갱신한다. | M |
| FR-7.3 | 브랜치 보호 규칙: `quality-gate` 필수 체크 통과 + 리뷰어 1명 이상 승인 + 최신 커밋 기준 승인 + 대화 해결 완료를 병합 조건으로 설정한다. | M |
| FR-7.4 | 병합 방식은 Squash merge로 통일한다. | S |
| FR-7.5 | 게이트 정책(각 검사의 차단/경고 구분, 임계값)을 `docs/quality-gate-policy.md`로 문서화한다. | M |
| FR-7.6 | 로직 파일 변경 시 테스트 파일 변경이 없으면 경고 코멘트를 남긴다. | C |

### FR-8. 검증 시나리오 및 문서
| ID | 요구사항 | 우선순위 |
|---|---|---|
| FR-8.1 | 취약 시나리오 PR 최소 10건(하드코딩 키, 평문 통신, 빈 catch, 메인스레드 I/O, 과도 권한, 테스트 실패, 스타일 위반, 복잡도 초과, 취약 의존성, 인증서 우회)을 작성하고 전부 차단됨을 확인한다. | M |
| FR-8.2 | 정상 PR 최소 5건이 오탐 없이 통과함을 확인한다. | M |
| FR-8.3 | 시나리오별 결과(검출 도구, 실행 시간, 차단 여부)를 표로 기록한다. | M |
| FR-8.4 | README에 파이프라인 구조도, 로컬 실행 방법, 게이트 정책 링크를 포함한다. | M |
| FR-8.5 | 신규 팀원이 PR 1건을 처음부터 끝까지 수행할 수 있는 온보딩 가이드를 작성한다. | S |

---

## 3. 비기능 요구사항 (Non-Functional Requirements)

| ID | 분류 | 요구사항 | 우선순위 |
|---|---|---|---|
| NFR-1 | 성능 | PR 1건당 전체 파이프라인 실행 시간 10분 이내 (캐시 적용 시) | M |
| NFR-2 | 신뢰성 | 동일 커밋에 대해 파이프라인 결과가 재현 가능해야 한다(비결정적 실패 0건 목표) | M |
| NFR-3 | 보안 | 모든 자격증명(LLM API 키 등)은 GitHub Secrets에만 저장하고 로그에 노출되지 않아야 한다 | M |
| NFR-4 | 유지보수성 | 검사 도구 추가/제거가 workflow 파일과 설정 파일 수정만으로 가능해야 한다 | S |
| NFR-5 | 가독성 | 게이트 실패 시 개발자가 코멘트만 보고 무엇을 고쳐야 하는지 알 수 있어야 한다 | M |
| NFR-6 | 비용 | GitHub Actions 무료 한도(공개 저장소 무제한 또는 월 2,000분) 및 LLM API 월 1만 원 이내 | S |
| NFR-7 | 이식성 | 다른 안드로이드 프로젝트에 workflow와 설정 파일을 복사하여 적용 가능해야 한다 | S |
| NFR-8 | 문서화 | 모든 임계값과 정책 변경은 문서와 함께 PR로 이력 관리한다 | S |

---

## 4. 인터페이스 요구사항

| 인터페이스 | 설명 |
|---|---|
| GitHub Events | `pull_request` 이벤트(opened/synchronize/reopened)를 트리거로 사용 |
| GitHub Checks API | job별 상태 체크 + `quality-gate` 종합 체크 게시 |
| GitHub Comments API | 게이트 요약 리포트 및 AI 리뷰 코멘트 게시/갱신 |
| GitHub Branch Protection | 필수 체크·승인 수·대화 해결 설정 (UI 또는 rulesets) |
| LLM API | Claude Messages API, 입력: PR diff + 프롬프트, 출력: 마크다운 리뷰 |
| 도구 출력 형식 | ktlint(텍스트/SARIF), Detekt(XML/SARIF), Semgrep(SARIF/JSON), gitleaks(JSON), 커버리지(XML) |

---

## 5. 제약사항
- 운영 기간: 2026. 10. 30까지 완료해야 하며, 실질 구현 가능 기간은 약 6주.
- 팀 구성: 학생 5명, 매주 금요일 18:00~20:00 정기 모임 + 개별 작업.
- 플랫폼: GitHub(공개 저장소 권장, Actions 무료 한도 활용).
- 도구: 오픈소스 또는 무료 티어 우선. 유료 도구는 사용하지 않는다.
- 자문: 산업체 전문가 자문은 회차당 진행되므로, 정책 결정은 자문회의 회차에 맞춰 확정한다.

---

## 6. 인수 기준 (Acceptance Criteria)

| 번호 | 기준 | 검증 방법 |
|---|---|---|
| AC-1 | main에 직접 push가 거부된다 | 실제 push 시도 → 거부 확인 |
| AC-2 | 빌드 실패 PR은 병합 버튼이 비활성화된다 | 컴파일 오류 PR 시나리오 |
| AC-3 | 하드코딩 API 키가 포함된 PR은 gitleaks에 의해 차단되고 코멘트에 위치가 표시된다 | 시나리오 PR |
| AC-4 | 커버리지 60% 미만 PR은 차단된다 | 테스트 삭제 PR 시나리오 |
| AC-5 | 정상 PR은 리뷰어 승인 후에만 병합 가능하다 | 승인 전/후 병합 버튼 상태 확인 |
| AC-6 | 게이트 요약 코멘트가 PR당 1개만 유지되며 재실행 시 갱신된다 | 커밋 2회 push 후 코멘트 수 확인 |
| AC-7 | 취약 시나리오 10건 전부 차단, 정상 5건 전부 통과 | 결과표 |
| AC-8 | 전체 실행 시간 10분 이내 | Actions 실행 로그 |
| AC-9 | AI 리뷰 봇 API 실패 시에도 나머지 체크는 정상 완료된다 | 잘못된 키로 실행 |
| AC-10 | README만 보고 팀원이 로컬에서 ktlint/Detekt/테스트를 실행할 수 있다 | 팀원 교차 검증 |

---

## 7. 요구사항 ↔ 회차 추적표

| 회차 | 일자 | 구현 대상 요구사항 |
|---|---|---|
| 7 | 9/18 | 자문회의: 전체 요구사항 검토, 우선순위·임계값 확정 |
| 8 | 9/25 | FR-1, FR-2.1~2.3, FR-3.1~3.3 |
| 9 | 10/2 | FR-3.4~3.7, FR-4.1~4.4 |
| 10 | 10/9 | FR-5.1~5.5, FR-6.1~6.4 |
| 11 | 10/16 | FR-7.1~7.5, FR-2.4~2.5 |
| 12 | 10/23 | FR-8.1~8.4, NFR-1·2 검증, 전문가 피드백 반영 |
| 13 | 10/30 | 최종 발표, AC 전체 재확인, 회고 |

---

## 8. 저장소 구조 (예정)
```
ai-pr-gate-android/
├── .github/
│   ├── workflows/
│   │   ├── pr-check.yml          # build / test / lint / security 병렬 job
│   │   ├── ai-review.yml         # LLM 리뷰 봇 (non-blocking)
│   │   └── quality-gate.yml      # 결과 집계 + 요약 코멘트 + 상태 체크
│   ├── PULL_REQUEST_TEMPLATE.md
│   ├── CODEOWNERS
│   └── scripts/
│       ├── gate.py               # 결과 집계·판정
│       └── ai_review.py          # diff → LLM → 코멘트
├── app/                          # 안드로이드 샘플 앱 (Compose, Room, Retrofit)
├── config/
│   ├── detekt/detekt.yml
│   ├── semgrep/android-rules.yml
│   └── lint.xml
├── docs/
│   ├── quality-gate-policy.md
│   ├── onboarding.md
│   └── scenarios/                # 취약/정상 시나리오 결과표
├── .editorconfig                 # ktlint
├── .gitleaks.toml
├── CONTRIBUTING.md
└── README.md
```
