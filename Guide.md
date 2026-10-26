# Android Implementation Guide

| 항목 | 내용 |
|---|---|
| 작성일 | 2026-10-26 (최초 작성. 기존 Guide.md 없음) |
| 기준 문서 | `01_PRD_AI생성코드_PR자동검증게이트.md` v2.0, `02_요구사항명세서_AI-PR-Gate.md` v2.0, `../AI-PR_Gate_피드백반영_문제점과_수정계획.md`, `AGENTS.md`, `docs/management-app-requirements.md` |
| 범위 결정 | 2026-10-26 사용자 결정: **관리 앱을 유지**한다. PRD 5.3의 "로그인·외부 API 후속" 조항보다 이 결정과 `AGENTS.md`를 우선한다. |
| 상태 | 계획 문서. 사용자의 '진행해줘' 확인 전에는 앱·CI·학습 코드를 수정하지 않는다. |

표기: **[구현]** 코드와 실행 증거가 있음 · **[부분]** 일부만 구현·검증 · **[미구현]** · **[미검증]** 코드는 있으나 해당 조건에서 실행하지 않음 · **[제안]** 승인 전 제안.

---

## 1. 목표와 범위

### 1.1 프로젝트 목표

AI를 활용해 작성한 코드를 선정 근거가 있는 검사로 확인하고, 검사 결과와 사람 승인을 PR 병합 조건으로 연결한다.
주 산출물은 그 과정을 실제 GitHub 데이터와 사용자 권한으로 조회·처리하는 **Android PR 관리 앱**이다.
할 일 앱은 검사 대상이 되는 **샘플 앱**이다.

### 1.2 이번 필수 범위

| 구분 | 내용 | 근거 |
|---|---|---|
| 관리 앱 | GitHub 연결·저장소 선택·PR 목록·상세·diff·검사 결과·리뷰/승인·병합 요청 | AGENTS.md 1·4절, management-app-requirements 2절 |
| 샘플 앱 | 목록·추가(trim/빈 제목 거부)·완료 토글·삭제·재실행 보존·오류/재시도·중복 방지 | APP-01~09 |
| 필수 테스트 | fake 기반 JVM 테스트, 실제 Room 계측 테스트, 커버리지 측정·보고(차단 없음) | TST-01~05 |
| 검사 룰 파일럿 | Android Lint·Gitleaks 후보의 위험/정상/경계 사례와 BLOCK·WARN·REPORT 근거 | POL-01~07 |
| PR 게이트와 승인 | 단일 workflow, 네 검사 + `quality-gate`, required check, 작성자 외 승인 1명, 새 커밋 후 재승인, 직접 push 제한 | CI-01~08, GAT-01~05, GOV-01~04 |
| 증거·문서 | AI 생성 기록, 정책표, 검증 추적표, README·환경표, 다른 팀원 재실행 | EVD-01~05, NFR-08~09 |

### 1.3 후속·제외 범위

- 백그라운드 알림·실시간 동기화, 모바일 코드 편집, 자체 빌드 서버·백엔드
- AI 리뷰 봇, 모델 의견에 따른 자동 승인·병합, 웹 대시보드
- 할 일 앱의 담당자·마감일·통계·검색·편집, 서버 동기화
- 외부 fork PR 자동 실행, 의존성 취약점 자동 차단, 다중 스타일 도구
- 리뷰 코멘트 파인튜닝은 **선택 연구 트랙**이다(8절). 필수 게이트에 포함하지 않는다.
- 15개 PR·커버리지 60%·10분 같은 수치는 v2.0 기준이 아니다. 근거 없이 되살리지 않는다.

### 1.4 구현 사실과 제안의 구분

- 2~6절의 [구현] 표시는 `docs/development-progress.md`와 `docs/results/`의 실행 기록에 근거한다.
- 빌드·테스트 통과는 기능의 일부 증거다. 보안 보장, 전체 요구사항 충족, 실제 GitHub 인수 완료를 뜻하지 않는다.
- 실제 계정 인증·다른 사람의 승인·병합·보호 규칙 적용은 **아직 실행하지 않았다**.

---

## 2. 실제 개발 환경

### 2.1 확인된 버전 (2026-10-26 재대조)

| 항목 | 선언 값 | 실제 실행 확인 | 근거 |
|---|---|---|---|
| Android Studio | — | 설치본 `AI-261.26222.65.2614.16204760` (2026.1.4 데이터 경로). 열린 IDE의 버전과 GUI Sync·디버거는 **미확인** | `docs/environment.md` |
| JDK | daemon 조건 Java 25 | `C:\Program Files\Android\Android Studio1\jbr` (JetBrains 25.0.3). 이번 대조에서 `JAVA_HOME`이 이 실제 경로를 가리키는 것을 확인함(과거에는 없는 경로였음) | `gradle-daemon-jvm.properties`, 셸 확인 |
| Gradle Wrapper | 9.6.0, 배포 SHA-256 고정 | 9.6.0 실행 | `gradle-wrapper.properties`, 실행 로그 |
| AGP | 9.4.1 | 빌드 성공으로 사용 확인 | `libs.versions.toml` |
| Kotlin | AGP 내장 Kotlin, Compose 플러그인 2.2.10 | 내장 컴파일러의 정확한 해석 버전은 **미확인** | 위 문서 |
| KSP | 2.3.12 | Room 코드 생성 성공 | 실행 로그 |
| compile / target / min SDK | 37 / 37 / 24 | API 35 에뮬레이터(Pixel_9_ASCII)만 계측. API 24·37 기기 **미검증** | `app/build.gradle.kts` |
| Java 소스/타깃 | 11 | — | `app/build.gradle.kts` |
| SDK 위치 | `C:\Android\Sdk` (`local.properties`) | 확인 | — |
| 기기 | — | `emulator-5554` 연결 확인. AVD: Medium_Phone, Pixel_9_ASCII, Pixel_9_GA36(이미지 없음, 부팅 실패 이력), Pixel_9_Manual, Pixel_9_UI | `adb devices`, `emulator -list-avds` |
| Git | git 2.52.0 설치 | **프로젝트 폴더는 Git 저장소가 아님**. 원격 후보 `jaezero/AIPRGATE`는 읽기 전용으로만 확인 | — |
| CI | `.github/workflows/pr-gate.yml` | GitHub 호스팅 runner에서 **미실행** | — |

### 2.2 현재 의존성과 용도

| 의존성 | 버전 | 용도 |
|---|---|---|
| Compose BOM | 2026.02.01 | Compose UI·Material 3 버전 정렬 |
| activity-compose | 1.13.0 | `setContent`, Activity 진입점 |
| material3, ui, ui-graphics, ui-tooling(-preview) | BOM | 화면 구성·미리보기 |
| core-ktx | 1.10.1 | Android KTX |
| lifecycle-runtime-ktx / -runtime-compose / -viewmodel-compose | 2.9.4 | ViewModel, lifecycle-aware 상태 수집 |
| room-runtime / room-compiler(KSP) | 2.8.5 | 샘플 앱 로컬 저장 |
| junit | 4.13.2 | JVM 테스트 |
| kotlinx-coroutines-test | 1.10.2 | ViewModel·Repository 비동기 테스트 |
| org.json (test) | 20250517 | JVM 테스트에서 GitHub JSON 파싱 검증(Android `org.json` 스텁 대체) |
| androidx.test.ext junit 1.3.0, espresso 3.7.0, compose ui-test-junit4 | — | 계측·Compose UI 테스트 |

GitHub API 호출은 별도 HTTP 라이브러리 없이 플랫폼 `HttpURLConnection`과 `org.json`으로 구현되어 있다. 토큰 보관은 Android Keystore(AES/GCM)를 쓴다.
검사 엔진(`tools/pr-gate/*.mjs`)은 Bun 1.4.3(Linux x64/WSL)에서 실행한다. 외부 도구는 Android Lint(AGP 내장)와 Gitleaks(`bootstrap-gitleaks.mjs`로 고정 버전 설치)다.

### 2.3 추가 예정 의존성 [제안]

| 후보 | 이유 | 결정 조건 |
|---|---|---|
| 커버리지 도구 1개 (AGP 내장 JaCoCo 리포트 우선 검토) | TST-04 측정·보고 | AGP 9.4.1·한글 경로·COMPAT 조건에서 실제 리포트 생성 확인 후. 차단 하한 없음 |

그 외 신규 의존성은 추가하지 않는다. 버전 업그레이드는 근거가 있을 때만 별도로 제안한다.

### 2.4 환경 문제와 미검증 항목

1. **한글 경로 JVM 테스트 문제가 남아 있다.** 기본 옵션에서는 테스트 클래스 로딩이 실패한다(`docs/results/app-20261025/baseline.log`). `-Dfile.encoding=COMPAT --no-configuration-cache` 조건에서만 72건이 통과했다. COMPAT 성공은 우회일 뿐 기본 환경 문제의 해결이 아니다.
   최소 변경안 [제안]: 프로젝트를 ASCII 경로 사본에서 기본 옵션으로 재현하고, Linux CI 결과로 기본 경로의 정상 동작을 확인한다. 전역 인코딩·JAVA_HOME은 바꾸지 않는다.
2. `android.overridePathCheck=true`는 AGP 경로 검사만 건너뛴다. 도구별 경로 문제를 해결하지 않는다.
3. Android Studio GUI의 Sync·Run·디버거는 에이전트가 직접 확인하지 않았다.
4. 최근 검증(2026-10-26 11:38 빌드, 11:43 휴대폰 계측, 11:45 넓은 화면 계측) 이후 `app/src`, `tools`, `policy`, `.github`, Gradle 설정이 바뀌지 않은 것을 파일 시각으로 확인했다. 따라서 그 결과는 현재 소스에 유효하며 이번 문서 작업에서 다시 실행하지 않았다.
5. Kotlin LSP가 설치되어 있지 않다. 진단은 Gradle 컴파일·Lint·테스트로 대신한다.

---

## 3. 앱 구조와 디자인

### 3.1 디자인 자료

`UI/` 등 별도 디자인 자료 폴더는 **없다**. 기존 Compose 테마(`ui/theme/Color.kt·Theme.kt·Type.kt`, Material 3, 동적 색상, 시스템 밝기)를 유지한다.
기준은 `docs/DESIGN.md`에 정리되어 있다. 아래 값은 기획문서의 확정 값이 아니라 **구현에 적용된 제안 값**이며, 사용자의 확인이 필요하다(3.6).

### 3.2 화면 구성 [구현]

```text
MainActivity ─ 관리 앱 (단일 Scaffold, 상태별 화면 전환)
 ├─ PR 목록         헤더(PR 관리 · 저장소 · [연결 설정|@로그인]) / 연결 상태 · [새로고침]
 │                  필터 칩: 열림 · 닫힘·병합 · 전체 / PR 카드(번호·상태·제목·작성자·갱신일·검사·승인 요약)
 │                  더 보기(페이지) / 샘플 앱 열기
 ├─ GitHub 연결     저장소 입력 · 기기 코드 로그인(Client ID) · 개인 토큰 로그인 · 취소 · 로그아웃
 └─ PR 상세 (#번호)  탭: 요약 | 변경 | 검사 | 리뷰
      요약: 목적·본문·AI 사용 기록·테스트 근거·브랜치·head SHA·GitHub 원문 링크
      변경: 파일 목록 + patch(diff만 가로 스크롤), 파일 더 보기
      검사: 네 검사 + quality-gate 상태·기준 SHA·실패 요약·근거 링크
      리뷰: 리뷰 이력 · 의견 입력 · [의견][수정 요청][승인] → 확인창
            병합 영역: 가능/불가/확인 중 + 차단 사유 · 방법 선택 → SHA 확인창 → 병합 요청
SampleActivity ─ 팀 학습 할 일 (원래 코드·learning-tasks.db 보존)
      제목·설명 · 입력란+추가 · 목록(체크박스·제목·삭제) · 빈 목록/오류 안내
```

### 3.3 공통 컴포넌트·색상·타이포그래피·간격

| 요소 | 적용 값 |
|---|---|
| 색상 | `MaterialTheme.colorScheme`. 오류는 `errorContainer/onErrorContainer`. 상태는 항상 텍스트로 함께 표시하고 색상만으로 전달하지 않음 |
| 타이포 | 제목 `headlineSmall` Bold. 관리 앱 본문 17sp, 보조 15sp, 한국어 locale·Paragraph 줄바꿈 |
| 간격 | 4/8/12/16/20/24/32dp. 바깥 20dp, 그룹 16dp, 관련 항목 8dp |
| 최대 너비 | 관리 화면 840dp, 연결 폼·샘플 640dp, 넓은 화면에서 가운데 정렬 |
| 컴포넌트 | 관리 헤더, StatusLabel(상태 배지), PR 카드, NoticeCard(안내/오류), 상세 탭, 검사 카드, ReviewForm, MergeForm, AlertDialog 확인창 |
| 스크롤 | 헤더·필터·탭 고정, 본문은 화면별 단일 LazyColumn |

### 3.4 상태 처리

| 상태 | 관리 앱 | 샘플 앱 |
|---|---|---|
| 로딩 | 진행 막대 + "GitHub의 최신 상태를 가져오는 중입니다." | 짧은 로딩 안내 |
| 빈 목록 | 필터별 빈 목록 안내 | 추가 방법 안내 |
| 입력 오류 | 저장소 형식·토큰·Client ID 오류 | 빈 제목·공백 제목 오류(필드 아래) |
| 조회/저장 실패 | "요청을 완료하지 못했습니다" + 원인 + 새로고침. 권한 부족·오프라인을 통과로 표시하지 않음 | 읽기/쓰기 실패 구분, 입력 유지, 재시도 |
| 쓰기 중 | "GitHub에 요청 중" + 중복 제출·화면 이동 차단 | 저장 중 입력·버튼 비활성화 |
| 대상 변경 | head/base SHA·브랜치가 바뀌면 확인창을 닫고 재검토 요구 | — |

### 3.5 접근성·글자 크기·키보드·시스템 여백

- Material 기본 48dp 터치 영역, 시스템 Back 처리, Scaffold 시스템 바 여백과 `imePadding`
- 라벨이 항상 보이는 입력란. 토큰은 비밀번호 키보드, 저장소는 URI 키보드
- 샘플의 체크박스·삭제 버튼 접근성 이름에 항목 제목 포함. 키보드 완료 키는 추가만 실행
- 본문·SHA·오류·diff 복사 가능
- **미검증**: 200% 글꼴 크기, 하드웨어 키보드 탐색, 실물 기기, 제조사별 키보드 조합

### 3.6 확정 사항과 승인받을 제안

| 구분 | 내용 |
|---|---|
| 확정(문서 근거) | 한국어 화면 문구, 관리 앱 주 진입, 샘플 보존, 상태별 안내, 승인·병합은 사용자의 명시적 제출 |
| **승인 필요 제안** | ① 기존 Material 3 테마·동적 색상 유지(브랜드 색 없음) ② 간격·최대 너비·17/15sp 본문 값 ③ 상세 4탭 구성과 병합 영역을 리뷰 탭에 두는 배치 ④ 샘플 앱 진입을 목록 하단에 두는 위치 ⑤ 관리 앱 문구를 `strings.xml`로 옮길지 여부(현재 샘플만 리소스화, 관리 앱은 코드 내 한국어 문자열) |

---

## 4. 폴더 및 책임 구조

현재 규모(단일 `app` 모듈, 소스 약 2,500줄)에 맞춰 **모듈을 나누지 않고 패키지로 책임을 나눈 현재 구조를 유지**한다.

```text
app/src/main/java/com/example/aiprgate/
  MainActivity.kt          관리 앱 진입·ViewModel 연결
  SampleActivity.kt        샘플 앱 진입
  TaskApplication.kt       Room DB·Repository 수명 관리
  management/              관리 앱 Compose UI·상태·ViewModel(ManagementScreen/State/ViewModel)
  github/                  GitHub REST 호출·모델(GitHubApi, GitHubModels)
  auth/                    device flow·토큰 검증·Keystore 세션(DeviceAuth, GitHubDeviceAuth, KeystoreSessionStore, SessionStore)
  ui/                      샘플 Compose UI·TaskViewModel, theme/
  data/                    TaskRepository 인터페이스, RoomTaskRepository
  data/local/              Room Entity·DAO·Database
  model/                   LearningTask
app/src/test/              JVM 테스트(fake Repository·fake GitHubService·전송 계층)
app/src/androidTest/       계측 테스트(실제 Room, Keystore, Compose 화면)
app/schemas/               Room 스키마 v1
.github/                   PULL_REQUEST_TEMPLATE.md, workflows/pr-gate.yml, actions/run-check
policy/                    checks.json(정책·BLOCK/WARN), gitleaks.toml/ignore, main-protection.json
tools/pr-gate/             검사 adapter·집계(core/reports/secrets/process/cli/verify), 테스트, fixture, 파일럿 문서
scripts/                   로컬 검증·증거 수집 PowerShell
docs/                      진행 기록·환경·설계·운영·AI 활용·결과(results/)
```

책임 원칙: Compose는 상태 표시와 동작 전달만 담당한다. ViewModel은 상태·검증·중복 방지를 맡는다. Repository/Service 인터페이스가 Room·GitHub 구현을 숨기고, 테스트는 fake를 주입한다.

---

## 5. 단계별 구현 계획

각 Step은 **현재 상태를 확인하고, 이미 있는 구현은 재사용하며, 남은 공백만 메운다.** 한 번에 하나의 Step만 진행하고, 끝나면 멈춰서 다음 지시를 기다린다.

### Step 1 — 앱 구조와 기본 UI

| 항목 | 상태 |
|---|---|
| 의존성(Compose·lifecycle·Room·KSP) | [구현] |
| 샘플: 목록·입력·빈 상태 UI, `TaskRepository` + fake 기반 구조 | [구현] |
| 관리 앱: 목록·연결·상세 4탭·상태 카드 구조, `GitHubService` fake 주입 | [구현] |
| 임시 데이터 표시 | 일반 앱에는 가짜 데이터 모드가 없다. 화면 캡처용 harness는 androidTest 소스에만 있다 [구현] |

남은 작업: 3.6 디자인 제안의 승인, 관리 앱 문구의 리소스화 여부 결정. UI 변경 시에는 기기 캡처로 확인한다.

### Step 2 — 실제 저장과 앱 동작

| 항목 | 상태 |
|---|---|
| Room 저장·추가·완료 변경·삭제·재실행 보존 | [구현] 계측 5건 + `force-stop` 후 재실행 수동 확인 |
| 입력 검증·오류 안내·재시도·중복 요청 방지 | [구현] JVM 테스트 |
| 관리 앱 실제 조회(공개 저장소 PR #1 목록·본문·diff·검사·리뷰) | [구현] 실기기 캡처 |
| 인증(device flow·개인 토큰)·Keystore 세션·로그아웃 | [부분] Keystore는 기기 테스트 완료. **실제 계정 로그인은 미실행** |

남은 작업: 사용자가 직접 본인 계정으로 로그인 확인(에이전트는 토큰을 받지 않음). 사용자가 원하면 커버리지 측정 준비는 Step 6에서 함께 한다.

### Step 3 — 최소 PR 검사 연결

| 항목 | 상태 |
|---|---|
| Git·GitHub 준비 | [미구현] 로컬 폴더가 Git 저장소가 아님. 원격 `jaezero/AIPRGATE`에는 이전 버전 코드와 이전 검사 이름의 PR이 있음 |
| PR 템플릿 | [구현] `.github/PULL_REQUEST_TEMPLATE.md` |
| 빌드·JVM 테스트 job, 단일 workflow, 초기 집계 | [구현·로컬] `pr-gate.yml`의 `pr-info / build-test / quality / secrets / quality-gate` |
| 원격 실행 | [미검증] Actions 호스팅 실행 없음. 미연결 검사를 성공으로 표시하지 않는다 |

진입 조건(**사용자 결정 필요**): 로컬 폴더와 원격 저장소를 어떻게 연결할지(원격 clone에 현재 파일 반영 vs 로컬 `git init` 후 원격 연결), 커밋·푸시 권한. `docs/pr-process.md`의 초기 게시 순서(제어 파일을 먼저 main에 게시)를 따른다.

### Step 4 — 검사 정책 파일럿

| 항목 | 상태 |
|---|---|
| 위험 목록·적용성 | [부분] PRD 7절 위험 후보를 기준으로 했으나 `docs/risk-selection.md` 형식의 위험 선정표는 없음 |
| 품질 룰 | [구현·로컬] `lint:TrustAllX509TrustManager` BLOCK. 나머지 Lint 결과는 WARN. 위험·정상 fixture 각 1개: 위험 fixture에서 선정 룰 1건 검출(경고 총 11), 정상 fixture에서 선정 룰 0건(경고 총 9) (`LINT-PILOT.md`) |
| 시크릿 룰 | [구현·로컬] `gitleaks:generic-api-key` BLOCK. 무효 시험 문자열과 환경 변수 참조 반례 (`SECRET-PILOT.md`) |
| PR 정보·빌드·테스트 | BLOCK (`PR_REQUIRED`, `BUILD_DEBUG`, `UNIT_TESTS`) |
| 예외 | `exceptions: []` |

남은 작업: 위험 선정표·정책 결정표를 PRD 7절 열 구성으로 정리한다(POL-01). 관리 앱이 추가한 네트워크·토큰 처리 위험을 재검토한다(PRD 7절 "TLS·인증: 기능 추가 시 재검토"에 해당). 검증된 정책만 게이트에 연결한다.

### Step 5 — 게이트와 승인 완성

| 항목 | 상태 |
|---|---|
| 결과 누락·오류·취소·timeout·오래된 SHA/run/attempt/본문 차단 | [구현·로컬] 엔진 테스트 103건, 게이트 시나리오 10건 |
| PR·SHA·run ID 연결, concurrency | [구현·로컬] |
| 본문 수정·새 커밋·기준 브랜치 변경 | [부분] 로컬 시나리오만. 실제 PR **미검증** |
| required check·승인 1명·재승인·직접 push 제한 | [미검증] `policy/main-protection.json` 적용용 설정만 있음. **미적용** |
| 관리 앱 리뷰·승인·SHA 지정 병합 | [부분] fake 기반 테스트만 있음. 실제 제출 **미실행** |

진입 조건: Step 3 완료, 저장소 관리자 권한, 작성자·리뷰어 두 계정. 보호 설정 적용·리뷰 제출·병합 같은 외부 쓰기는 대상과 동작마다 사용자의 명시적 허가를 받은 뒤 실행한다.

### Step 6 — 최종 검증과 문서화

- 요구사항·AC별 검증 추적표(`docs/verification-matrix.md` [제안])
- 실행 시간(대기열·runner·승인 대기 구분, 최초/캐시 구분), 커버리지 측정
- AI 생성 기록 ↔ PR·SHA·run 연결, 실제 구현도, README·운영 가이드 갱신
- 다른 팀원의 재실행 기록
- 실패·미검증·잔여 한계 보고

### 문서·구현 불일치 [제안: Step 착수 전 결정]

| ID | 연결 요구사항 | 불일치 | 제안 |
|---|---|---|---|
| G-01 | PRD 5.3, APP-09 | 관리 앱 때문에 Manifest에 `INTERNET` 권한과 로그인이 있다. 원문은 "네트워크·민감 권한 요청 금지" | APP-09를 "샘플 앱 기능은 네트워크를 쓰지 않음, 관리 앱은 GitHub API만 사용"으로 개정하는 변경안을 작성한다 |
| G-02 | CI-02 | SRS는 `pr-check.yml`과 `app-check / quality-check / secret-check`를 지정하지만, 구현은 `pr-gate.yml`과 `build-test / quality / secrets`다 | 구현 이름을 기준으로 SRS를 개정한다(필수 체크 `quality-gate` 이름은 같음). 또는 파일·job 이름을 바꾼다. 원격 PR #1의 이전 체크 이름과도 함께 정리한다 |
| G-03 | SRS 9절 | `tools/gate`, `config/`, `verification/fixtures` 대신 `tools/pr-gate`, `policy/`, `tools/pr-gate/fixtures`를 쓴다 | 구조 문서만 현행화한다 |
| G-04 | 프롬프트 경로 | `기업 모둠학습\기획문서\` 폴더가 없다. 기획문서는 프로젝트 루트에 있다 | 루트 사본을 기준으로 삼는다 |
| G-05 | ENV-04 | 기본 JVM 테스트가 한글 경로에서 실패한다 | 2.4의 최소 변경안을 따른다 |

---

## 6. 인수 조건 매핑

Step 번호와 표의 행 수는 PR 개수가 아니다. 한 PR에서 여러 SHA·상태를 확인할 수 있지만, 상태마다 증거를 분리해서 남긴다.

| Step | 요구사항 | AC | 검증 방법 | 현재 증거 | 상태 |
|---|---|---|---|---|---|
| 1 | APP-01, APP-08, ENV-01 | AC-01 | Compose 화면 테스트, 기기 캡처 | `results/app-20261025/`, `results/management-app/device-*-final-8` | 부분(IDE GUI 미확인) |
| 2 | APP-02~07, TST-01~03 | AC-01 | JVM 72건, 계측 17건, 재실행 수동 확인 | `implementation-8/run.json`, `device-phone-final-8/` | 구현(COMPAT 조건) |
| 2 | 관리 앱 조회·인증 | (관리 앱 7절 1~2) | 실제 공개 PR 조회, Keystore 기기 테스트 | `management-app/verification.md` | 부분(실계정 미실행) |
| 2·6 | TST-04~05 | AC-02 | 커버리지 리포트, assertion 검토 | 없음 | 미구현 |
| 3 | ENV-03·05, PR-01~02, CI-01~03 | AC-04, AC-06 | 실제 PR 실행 | 없음(로컬 합성만) | 미검증 |
| 4 | POL-01~07 | AC-05, AC-07 | Lint·Gitleaks fixture, 정책표 | `pr-gate-20261025/lint-pilot`, `SECRET-PILOT.md` | 부분(선정표 미작성, 실제 PR 미연결) |
| 5 | CI-04~08, GAT-01~05 | AC-08, AC-09 | 엔진 테스트, 실제 PR 갱신·취소 | `engine-tests-final`, `gate-scenarios` | 로컬 구현 / 실제 미검증 |
| 5 | GOV-01~04 | AC-10, AC-11 | 보호 적용 후 거부·허용 기록 | 없음 | 미검증 |
| 5 | 관리 앱 리뷰·승인·병합 | (관리 앱 7절 4~5) | 두 계정 실제 시나리오 | fake 테스트만 | 미검증 |
| 6 | EVD-01~05 | AC-03 | AI 기록 ↔ SHA·run 감사 | `docs/ai-usage-*.md` | 부분(SHA 연결 불가: Git 없음) |
| 6 | NFR-01~09 | AC-12, AC-13 | 권한·입력 리뷰, 시간 측정, 팀원 재실행 | 일부 설정 리뷰 | 미검증 |

---

## 7. AI 활용 및 변경 이력

### 7.1 기록 원칙

- 입력(프롬프트·제공 문맥)과 AI 원본 출력, 사람 수정 코드, 최종 통합 코드를 구분한다.
- 도구·화면에 표시된 모델명·기준 코드(파일 해시 또는 SHA)·후속 요청을 기록한다. 확인할 수 없는 모델 세부 버전은 "확인 불가"로 적는다.
- 정상·실패·컴파일 오류·거절을 포함한 전체 시도를 보존하고, 성공한 결과만 골라 남기지 않는다.
- Android Studio 템플릿 코드(최초 `MainActivity`의 `Hello Android!`, 예제 테스트)를 AI 생성 코드로 표시하지 않는다.
- 검출 시험을 위해 사람이 주입한 결함(Lint/Gitleaks fixture)과 AI가 실제로 만든 위험 사례를 구분한다.

### 7.2 기존 기록

| 기록 | 범위 |
|---|---|
| `docs/ai-usage-20261025.md` | 샘플 앱 B단계 |
| `docs/ai-usage-pr-process-20261025.md` | 검사 기반·게이트 |
| `docs/ai-usage-management-app.md` | 관리 앱 |
| `docs/development-progress.md` | 단계별 누적 기록(이 Guide의 Step 결과도 여기에 누적한다) |

### 7.3 변경 이력

| 날짜 | 변경 | 주체 |
|---|---|---|
| 2026-10-26 | Guide.md 최초 작성. 관리 앱 유지 결정 반영, 기존 구현을 Step 1~6에 매핑 | AI 작성(Claude Code), 사용자 검토 대기 |

---

## 8. 리뷰 코멘트 파인튜닝 검토 (선택 연구 트랙)

`docs/finetuning-review-plan.md`(2026-09-21) 요약. **계획만 있고 모델 다운로드·추론·학습·데이터 제작은 수행하지 않았다.**

| 항목 | 요약 |
|---|---|
| 현재 판단 | 학습 필요성은 **입증되지 않았다**. 기준선·데이터·독립 테스트가 없다. 게이트를 먼저 완성하고, 학습 없는 방식으로 남는 문제가 관찰될 때만 검토한다 |
| 목표 | 이미 확인된 finding에 대해 문제 근거와 검토 의견을 작성한다. 새 취약점 탐지·자동 수정은 범위 밖이다 |
| 기준선 | B0 룰별 정적 템플릿 → B1 기본 프롬프트 → B2 개선 프롬프트(강한 기준선) → F1 SFT adapter. 주 비교는 **F1 대 B2** |
| 모델·하드웨어 | 1순위 `Qwen2.5-Coder-1.5B-Instruct`, 확장 후보 7B. LoRA(PEFT). RTX 3050 8GB에서 CUDA·라이브러리 호환성 **미확인** |
| 데이터·권한 | 팀 자체 자료 우선. 오픈소스 리뷰는 라이선스·사용 조건을 확인한다. 외부 모델 출력은 정답으로 쓰지 않는다. 실키·개인정보·비공개 제3자 코드는 제외 |
| 정답 검토 | 사람 2인 독립 검토 후 이견 조정. tool finding을 그대로 정답으로 복사하지 않는다 |
| 중복 제거·분리 | 원형 코드 그룹 단위로 분할(hash·유사도 검사). 같은 원형의 변형은 한 split에만 둔다. 최종 테스트는 선택(튜닝)에 쓰지 않는다 |
| 평가 | 근거 정확성, 실질적 사용 가능 비율, 정상 사례 부당 지적률, 누락·과도한 보류, 위치·근거 참조 정확성, 안전성. BLEU/LLM 단독 채점은 주 기준이 아님. Go/No-Go 기준은 **결과를 보기 전에 고정**한다 |
| 비용·일정 | 직렬 약 12~24 작업일 이상 추정. 2026-10-30 종료일 안에 게이트와 함께 끝내기 어렵다 |
| 중단 조건 | 권한 있는 데이터 부족, 실행 환경 불가, 낮은 평가자 일치, 누출 없는 테스트 부족, B2 대비 개선 없음 |
| PRD·SRS 변경 제안 | G-06 목표 후보, FT-01~08 요구사항 후보. 모델 결과는 `quality-gate`와 사람 승인 조건을 바꾸지 않는다 |

이 Guide를 승인해도 학습·데이터 제작·모델 다운로드·유료 자원 사용은 **승인된 것이 아니다**. 각각 별도의 명시적 요청이 필요하다.
