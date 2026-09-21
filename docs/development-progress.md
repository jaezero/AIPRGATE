# 단계별 개발 진행 기록

이 문서는 `C:\AIPRGATE` 프로젝트의 진행 기록이다. 과거 기록은 다른 폴더의 사본에 있으며 원본을 옮기지 않았다.

| 과거 기록 | 위치 |
|---|---|
| 2026-09-21 A단계 점검·환경·파인튜닝 계획 | `C:\Users\박재영\Desktop\기업 모둠학습\AiPrGate\docs\` (한글 경로 사본) |
| 같은 문서와 경로 이전 시험 결과 | `C:\AndroidProjects\AiPrGate\docs\` |
| 수정계획 | `C:\Users\박재영\Desktop\기업 모둠학습\AI-PR_Gate_피드백반영_문제점과_수정계획.md` |

---

## 2026-09-21 — PHASE 0~2: 환경 검증·분석·Guide.md

결과 요약은 [Guide.md](../Guide.md) 2장과 9장에 있다.

| 항목 | 결과 |
|---|---|
| APK 빌드 | 성공 |
| JVM 테스트 | 기본 옵션으로 통과 (템플릿 1건). COMPAT 옵션 미사용 |
| 계측 테스트 | Pixel_9_ASCII API 35에서 템플릿 1건 통과 |
| 앱 실행 | `Hello Android!` 확인 ([캡처](results/step1-20260921/phase0-template-hello.png)) |
| 한글 경로 테스트 오류 | 현재 프로젝트·Gradle 홈이 ASCII 경로라 재현되지 않음. 원인 해결이 아니라 조건 제거 |
| Git | 저장소 아님 → 사용자 진행 승인 후 초기화 |

---

## 2026-09-21 — Step 0: Git 기준점

사용자가 Guide.md를 확인하고 "진행"을 지시했다. Guide 5장 Step 0 권장안에 따라 로컬 Git 저장소를 만들었다. 원격 저장소는 없다.

| 커밋 | 내용 | 작성 주체 |
|---|---|---|
| `aefe759` (main) | Android Studio Empty Activity 템플릿 원본과 기획문서. **AI 생성 코드 아님** | 템플릿·사용자 |
| `e2ba127` (main) | Guide.md | AI 작성, 사용자 진행 승인 |

`.kotlin` 생성 폴더는 기준 커밋에서 제외했고, Step 1에서 `.gitignore`에 추가했다.

---

## 2026-09-21 — Step 1: 앱 구조와 기본 UI

브랜치 `feature/step1-app-structure`, 기준 SHA `e2ba127`. 작업 결과는 커밋하지 않은 상태로 두었다(사용자 검토 후 커밋).

### 1. 구현한 기능과 요구사항 ID

| 요구사항 | 구현 | 상태 |
|---|---|---|
| APP-01 목록·빈 상태 | ID 오름차순 목록, 로딩, 빈 상태 문구 | 로직 테스트·에뮬레이터 확인 |
| APP-02 공백 제거 후 추가 | trim 후 비어 있으면 저장하지 않고 "제목을 입력해 주세요" | 로직 테스트·에뮬레이터 확인 |
| APP-03 ID 기준 토글 | 행 전체 또는 체크박스, 완료 시 취소선 | 로직 테스트·에뮬레이터 확인 |
| APP-04 ID 기준 삭제 | 항목별 "삭제" 버튼 | 로직 테스트·에뮬레이터 확인 |
| APP-05 재실행 후 보존 | **미구현.** Step 1은 인메모리 저장소라 앱 종료 시 사라진다 | Step 2 |
| APP-06 실패·재시도 | 추가 실패 시 입력 보존, 변경·삭제 실패 시 화면은 저장된 상태 유지, Snackbar "다시 시도", 조회 실패 화면·재시도 | 로직 테스트만. **실패 UI 표시는 에뮬레이터 미검증** (인메모리 저장소는 실패하지 않음) |
| APP-07 중복 요청 방지 | 추가 중 입력·버튼 비활성화와 ViewModel 가드, 항목별 진행 중 가드 | 로직 테스트. 버튼 비활성화 화면 미검증 |
| APP-08 Repository 인터페이스 | `TaskRepository`, 생성자 주입, fake 주입 가능 | 테스트로 확인 |
| APP-09 | 권한·네트워크 추가 없음. 로그 출력 없음 | Step 2에서 Manifest·백업 검토 |
| TST-01 | ViewModel JVM 테스트 21건 | 통과 |

디자인은 Guide 3.2 제안을 그대로 적용했다. 기존 테마·dynamic color 유지, 텍스트 삭제 버튼, 좌우 16dp, 항목 최소 56dp, 터치 대상 48dp. 앱 표시 이름은 `AIPRGATE` 그대로 두었다.

### 2. 생성·수정 파일

| 파일 | 변경 |
|---|---|
| `gradle/libs.versions.toml` | `lifecycleCompose` 2.9.4, `coroutinesTest` 1.9.0 추가. 현재 해석 버전에 맞춤. 기존 선언 버전은 변경하지 않음 |
| `app/build.gradle.kts` | `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`, 테스트용 `kotlinx-coroutines-test` |
| `app/src/main/.../data/Task.kt` | 도메인 모델 |
| `app/src/main/.../data/TaskRepository.kt` | 저장소 인터페이스 |
| `app/src/main/.../data/InMemoryTaskRepository.kt` | **임시** 인메모리 구현. Step 2에서 Room으로 교체 |
| `app/src/main/.../ui/tasks/TaskListUiState.kt` | 화면 상태, 입력 오류, 실패 메시지 |
| `app/src/main/.../ui/tasks/TaskListViewModel.kt` | 검증·중복 방지·실패 처리·재시도 |
| `app/src/main/.../ui/tasks/TaskListScreen.kt` | Compose 화면과 미리보기 |
| `app/src/main/.../MainActivity.kt` | 템플릿 `Greeting` 제거, 화면 연결 |
| `app/src/main/res/values/strings.xml` | 한국어 화면 문구 |
| `app/src/test/.../testutil/FakeTaskRepository.kt` | 실패·지연 주입 fake |
| `app/src/test/.../testutil/MainDispatcherRule.kt` | Main 디스패처 교체 |
| `app/src/test/.../ui/tasks/TaskListViewModelTest.kt` | ViewModel 테스트 21건 |
| `app/src/test/.../data/InMemoryTaskRepositoryTest.kt` | 임시 저장소 테스트 2건 |
| `.gitignore` | `.kotlin` 추가 |
| `docs/development-progress.md`, `docs/ai-evidence/index.md`, `docs/results/step1-20260921/*` | 기록과 증거 |

템플릿 예제 테스트 두 개는 그대로 두었다. 기능 검증으로 집계하지 않는다.

### 3. 검사 명령과 실제 결과

| 명령 | 결과 |
|---|---|
| `gradlew :app:assembleDebug :app:testDebugUnitTest --rerun-tasks` | BUILD SUCCESSFUL, 9.9초(캐시 있는 로컬). Kotlin 경고 없음. [로그](results/step1-20260921/build-test.log) |
| JVM 테스트 | `TaskListViewModelTest` 21건, `InMemoryTaskRepositoryTest` 2건, 템플릿 1건. 실패·오류·skip 0 |
| 변형 확인 | 추가 중복 가드 제거, trim 제거, 항목 중복 가드 제거를 각각 적용하면 관련 테스트가 실패함을 확인하고 원복 |
| `gradlew :app:installDebug` + 에뮬레이터 | Pixel_9_ASCII API 35에서 빈 상태, 공백 입력 오류, 추가, 토글, 삭제, 키보드 표시, 글꼴 200% 확인 |
| `connectedDebugAndroidTest` | Step 1에서는 새 계측 테스트가 없어 실행하지 않음 |
| `lintDebug` | 미실행. Step 4 정책 파일럿 범위 |

APK SHA-256: `e029d67a90c36525ff71b7fab7110a6c411d179963601c074e139ed7b009bf45`

화면 캡처: [빈 상태](results/step1-20260921/s1-empty.png), [공백 입력 오류](results/step1-20260921/s2-blank-error.png), [항목 추가](results/step1-20260921/s3-two-items.png), [토글·삭제·키보드](results/step1-20260921/s4-toggle-delete.png), [글꼴 200%](results/step1-20260921/s5-font200.png). 에뮬레이터 글꼴 배율은 확인 후 1.0으로 되돌렸다.

UI 확인 중 체크박스가 화면 왼쪽 끝에 붙어 있어 시작 여백을 16dp로 수정하고 다시 확인했다. 에뮬레이터 입력은 `adb input text` 제약으로 영문 제목을 사용했다. 한글 제목 입력은 미확인이다.

### 4. Android Studio에서 확인할 순서

1. `feature/step1-app-structure` 브랜치 상태로 프로젝트를 열고 Gradle Sync를 실행한다.
2. `TaskListScreen.kt`의 미리보기 두 개를 확인한다.
3. 에뮬레이터에서 Run 후 빈 상태 → 빈 입력으로 추가 → 한글 제목 추가 → 토글 → 삭제를 확인한다.
4. 앱을 완전히 종료 후 다시 실행하면 목록이 비어 있다. Step 1의 알려진 한계이며 정상이다.
5. `app/src/test`의 `TaskListViewModelTest`를 실행한다.

### 5. 남은 문제

- 저장 실패·조회 실패·진행 중 비활성화 화면은 로직 테스트만 있고 화면 표시를 확인하지 못했다. Step 2에서 fake 저장소를 쓰는 Compose UI 계측 테스트로 확인할 계획이다.
- Android Studio 안에서의 Sync·Run·Debug(ENV-01)는 사용자 확인이 필요하다.
- Step 1 변경은 아직 커밋하지 않았다.
- Git Bash에서 `adb`와 프로세스 생성이 간헐적으로 권한 오류를 냈다. 재실행으로 해결했으며 결과에는 영향이 없었다.

### 6. 다음 Step과 진입 조건

Step 2 실제 저장과 앱 동작. 진입 조건은 다음과 같다.

- Step 1 변경을 사용자가 검토하고 커밋 여부를 결정.
- Room·KSP 다운로드를 위한 네트워크 사용 가능.
- AGP 9.4.1 내장 Kotlin과 KSP 호환 확인. 실패하면 원인과 대안을 보고하고 승인 후 진행.

---

## 2026-09-21 — Step 2: 실제 저장과 앱 동작

브랜치 `feature/step1-app-structure`. Step 1은 `c2fb4d4`로 커밋했다(사용자가 Step 2 진행을 지시해 Step 1 결과를 확정한 것으로 보고, 증거 분리를 위해 로컬 커밋). Step 2 변경은 미커밋 상태다.

### 1. 구현한 기능과 요구사항 ID

| 요구사항 | 구현·검증 | 상태 |
|---|---|---|
| APP-01~04 | Room `tasks` 테이블, DAO 조회(ID 오름차순)·삽입·완료 변경·삭제 | 계측 테스트 통과 |
| APP-05 재실행 후 보존 | Room 파일 DB `aiprgate.db`. 앱 강제 종료 후 콜드 스타트에서 항목·완료 상태 유지 | 에뮬레이터 확인 |
| APP-06 실패 표시 | 없는 항목의 완료 변경은 실패로 알림(성공 위장 방지). 실패·조회 실패 화면을 Compose UI 테스트로 확인 | Step 1 미검증 항목 해소 |
| APP-07 진행 중 비활성화 | 추가 버튼·항목 삭제 버튼 비활성화를 UI 테스트로 확인 | 해소 |
| APP-08 | `RoomTaskRepository`가 같은 인터페이스를 구현. Application에서 단일 인스턴스 | 확인 |
| APP-09 | 아래 3절 검토 | 검토 기록 |
| TST-02 | DAO 6건, Repository 3건, 파일 DB 재오픈 1건 계측 테스트 | 통과 |
| TST-03 | JVM 테스트 전체 실행, DB·DAO 구현 변경에 따른 계측 테스트 실행 | 수행 |

### 2. 생성·수정 파일

| 파일 | 변경 |
|---|---|
| `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts` | Room 2.8.5(`room-runtime`, `room-compiler`), KSP 플러그인 2.3.12, 스키마 경로 인자 |
| `app/src/main/.../data/local/TaskEntity.kt`, `TaskDao.kt`, `AppDatabase.kt`, `RoomTaskRepository.kt` | Room 구현 |
| `app/src/main/.../AiPrGateApplication.kt`, `AndroidManifest.xml` | DB·저장소 단일 인스턴스, `android:name` 등록 |
| `app/src/main/.../MainActivity.kt` | Room 저장소 연결 |
| `app/src/main/.../data/InMemoryTaskRepository.kt`, 해당 테스트 | **삭제.** Step 1 임시 구현. `c2fb4d4`에 보존 |
| `app/schemas/.../1.json` | Room 스키마 버전 1. DB 구조 변경을 PR에서 검토하기 위해 저장소에 포함 |
| `app/src/androidTest/.../data/local/TaskDaoTest.kt`, `RoomTaskRepositoryTest.kt`, `TaskDatabasePersistenceTest.kt` | 실제 Room 테스트 |
| `app/src/androidTest/.../ui/tasks/TaskListScreenTest.kt` | 화면 상태 UI 테스트 10건 |

버전 선정 근거:
- Room 2.8.5는 Google Maven 메타데이터 기준 최신 안정판이다(2026-09-21 조회).
- KSP는 2.3.1에서 AGP 9 내장 Kotlin을 지원했고, 2.3.6·2.3.10에서 관련 수정이 있었다. 2.3.12는 최소 AGP를 8.12.0으로 올렸다(GitHub 릴리스 노트).
- Guide 2.5의 `room-ktx`는 Room 2.8 runtime에 코루틴 API가 포함되어 추가하지 않았다. 빌드로 확인했다.

### 3. APP-09 수동 검토 (debug 병합 Manifest 기준)

| 항목 | 결과 | 판단 |
|---|---|---|
| 권한 | 위험 권한·`INTERNET` 없음. androidx가 추가한 앱 전용 서명 권한 `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`만 있음 | 네트워크·민감 권한 요청 없음 |
| exported=true | `MainActivity`(런처), debug 전용 `PreviewActivity`·`ComponentActivity`(ui-tooling·ui-test-manifest), `ProfileInstallReceiver`(`android.permission.DUMP` 보호) | debug 전용 두 개는 release 병합 Manifest에 없음을 확인. 일괄 취약 판정하지 않음 |
| `debuggable=true` | debug variant 기본값 | 기획 4.2에 따라 차단 대상 아님 |
| `allowBackup=true`, 기본 백업 규칙 | Room DB가 자동 백업 대상에 포함될 수 있음 | 저장 데이터는 테스트용 할 일 제목뿐이라 현재 유지. 실사용 민감 데이터를 다루면 재검토 |
| 로그 | `app/src/main`에 `Log`·`println`·`printStackTrace` 없음 | 사용자 데이터 로그 출력 없음 |

### 4. 검사 명령과 실제 결과

| 명령 | 결과 |
|---|---|
| `gradlew :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest --rerun-tasks` | BUILD SUCCESSFUL, 37.9초(캐시 있는 로컬). [로그](results/step2-20260921/build-test.log) |
| JVM 테스트 | `TaskListViewModelTest` 21건, 템플릿 1건. 실패 0 |
| 계측 테스트 (Pixel_9_ASCII API 35) | 21건 통과: `TaskListScreenTest` 10, `TaskDaoTest` 6, `RoomTaskRepositoryTest` 3, `TaskDatabasePersistenceTest` 1, 템플릿 1. [결과 XML](results/step2-20260921/connected/) |
| 변형 확인 | DAO 정렬을 DESC로 바꾸면 계측 테스트 3건 실패(`expected [1, 2, 3] but was [3, 2, 1]` 포함). 원복 후 재실행 21건 통과 |
| 재실행 보존 | 항목 3개 추가 → 1개 완료 → 1개 삭제 → `am force-stop`(pid 없음 확인) → 콜드 스타트. 남은 2개와 완료 상태 일치. [종료 전](results/step2-20260921/s1-before-restart.png), [재실행 후](results/step2-20260921/s2-after-restart.png) |
| `lintDebug` | 미실행. Step 4 범위 |

APK SHA-256: `cdf7c28a41c90df5069bd787dbb372cbf58b1671955ef3e9a516789c26c43b2c`

실패·무효 시도:
- 재실행 보존 1차 시도는 무효다. 조작 스크립트의 뒤로가기 키가 키보드가 닫힌 상태에서 앱을 종료시켰고, 이후 탭이 홈 화면에 입력되었다. [당시 화면](results/step2-20260921/invalid-attempt1-home-screen.png). 앱 결함이 아니며 단계별로 화면을 확인하는 방식으로 다시 수행했다.
- 거부된 명령 1건: 파일 삭제를 포함한 명령이 사용자에게 거부 표시되었으나 실제로는 실행이 끝난 상태였다. 사용자 재지시로 그 상태에서 계속했다.

### 5. Android Studio에서 확인할 순서

1. Gradle Sync 후 KSP가 Room 코드를 생성하는지 확인한다(`Build > Make Project`).
2. `app/src/androidTest`를 에뮬레이터에서 실행한다.
3. 앱에서 한글 제목을 추가하고, 완료·삭제 후 앱을 완전히 종료했다가 다시 실행한다.
4. App Inspection > Database Inspector에서 `aiprgate.db`의 `tasks` 테이블을 확인한다.

### 6. 남은 문제

- 계측 테스트 XML의 한글 테스트 이름이 깨져 기록된다. 이 PC의 기본 인코딩(MS949)과 관련된 것으로 보이며, 원인은 확정하지 않았다. 결과 판정에는 영향이 없다. JVM 테스트 XML은 정상이다. Step 3 이후 CI 결과 파싱에서 확인이 필요하다.
- Gradle 실행 시 JDK 25의 `sun.misc.Unsafe` 경고가 출력된다. Gradle 도구 쪽 protobuf에서 나오며 앱 코드와 무관하다.
- 한글 제목 입력은 에뮬레이터 자동 입력 제약으로 여전히 미확인이다.
- Android Studio 안에서의 Sync·Run·Debug(ENV-01)는 사용자 확인이 필요하다.
- 루트에 사용자 파일(성과공유회 포스터 PPTX와 `~$` 잠금 파일)이 생겼다. 건드리지 않았고 커밋하지 않는다. `.gitignore` 추가 여부는 사용자 결정이다.

### 7. 다음 Step과 진입 조건

Step 3 최소 PR 검사 연결. 진입 조건:
- Step 2 변경 커밋 여부 결정.
- GitHub 저장소 소유 계정, 공개/비공개 여부, 보호 규칙 사용 가능 플랜 확인(ENV-05).
- `gh` CLI 설치 여부 결정. 미설치 시 GitHub 웹에서 사용자가 저장소를 만들고 remote URL을 알려 주는 방식도 가능.

---

## 2026-09-21 — Step 3: 최소 PR 검사 연결 (진행 중)

브랜치 `feature/step3-pr-check` (Step 2 `94d04d5` 기반). 원격 `https://github.com/jaezero/AIPRGATE` (공개 저장소, 사용자 제공).

### 1. 구현한 기능과 요구사항 ID

| 요구사항 | 구현 | 상태 |
|---|---|---|
| PR-02 | `.github/PULL_REQUEST_TEMPLATE.md`: 변경 요약, 관련 요구사항, AI 사용 여부, 생성 기록 위치, 작성자 설명, 테스트 근거, 수동 리뷰 체크리스트(PR-05 항목) | 작성 |
| CI-01 | `pull_request` → main, opened·synchronize·reopened·edited·ready_for_review. path filter 없음 | 작성, 실제 실행 대기 |
| CI-02 | `.github/workflows/pr-check.yml` 하나에 `pr-info`, `app-check`, `quality-check`, `secret-check`, `quality-gate` | 작성 |
| CI-03 | `app-check`: debug 빌드 후 JVM 테스트. 빌드 실패 시 테스트는 skipped로 기록하고 필수 미완료 처리 | 로컬 모의 실행 확인 |
| CI-04 | `quality-gate`는 `if: always()`로 선행 실패에도 실행. 누락·skipped·cancelled·error 는 CHECK_ERROR | 집계 테스트 확인 |
| CI-05 | concurrency 로 같은 PR 이전 실행 취소 | 작성. 최신 PR 재조회(GAT-05)는 Step 5 |
| CI-06 | head·base SHA(이벤트), tested SHA(checkout 후 `git rev-parse HEAD`) 기록 | 작성 |
| CI-07 | check·run ID·attempt 를 포함한 artifact 이름, 같은 run 결과만 내려받기 | 작성 |
| CI-08 | 판정을 job summary 와 `verdict.json` artifact 로 제공 | 작성 |
| GAT-01~04 | PASS / POLICY_FAIL / CHECK_ERROR, 두 사유 동시 출력, WARN·REPORT 비차단 | 집계 테스트 확인 |
| NFR-01·03 | `contents: read`만 부여, 비밀값 없음, `pull_request_target` 미사용, fork PR 은 job 미실행 → gate 통과 불가 | 작성 |
| NFR-02 | PR 본문·제목을 run 스크립트에 넣지 않음. 리포트는 JSON 데이터로만 파싱 | 작성 |

**미연결 검사는 성공으로 표시하지 않는다.** `pr-info`(Step 5), `quality-check`·`secret-check`(Step 4)는 `execution_status=skipped`, `error.code=NOT_CONNECTED` 결과를 남기고 job 을 실패시킨다. 따라서 Step 3 단계의 `quality-gate`는 정상 코드에서도 CHECK_ERROR 다. Step 3 의 "정상 통과"는 `app-check` job 의 통과를 뜻한다.

### 2. 생성·수정 파일

| 파일 | 내용 |
|---|---|
| `gradlew` | 실행 권한(100755) 부여. 이전에는 100644라 Linux CI 에서 실행 불가 |
| `.gitattributes` | `gradlew`·`*.py`·`*.yml` LF, `gradlew.bat` CRLF |
| `.github/PULL_REQUEST_TEMPLATE.md` | PR 템플릿 |
| `.github/workflows/pr-check.yml` | 단일 workflow |
| `tools/gate/result.py` | 정규화 JSON 공통 모듈, 미연결 결과 기록, policy_revision 계산 |
| `tools/gate/app_check.py` | 빌드·테스트 결과 adapter. 컴파일 오류(BLOCK)와 빌드 실행 장애(error) 구분 |
| `tools/gate/gate.py` | 집계·판정·요약 |
| `tools/gate/tests/*` | 집계 24건, adapter 8건 |

선정 근거:
- 집계는 Python 표준 라이브러리만 쓴다. GitHub runner 와 로컬에 모두 있고 추가 패키지가 필요 없다.
- Actions 는 2026-09-21 조회한 최신 릴리스 커밋 SHA 로 고정했다: checkout v7.0.1, setup-java v6.0.1, setup-python v7.0.0, upload-artifact v7.0.1, download-artifact v8.0.1.
- runner 는 `ubuntu-24.04`, JDK 는 Temurin 25 (로컬 JBR 25 와 같은 주 버전, `gradle-daemon-jvm.properties` 요구와 일치).
- 캐시(CI-09, S)는 아직 쓰지 않는다. 최초 실행 시간 측정을 먼저 한다.

### 3. 검사 명령과 실제 결과

| 명령 | 결과 |
|---|---|
| `python -m unittest discover -s tools/gate/tests -t .` | 32건 통과. [로그](results/step3-20260921/gate-unittest.log). 첫 실행에서 1건 실패(윈도우 경로 정규화 오류) → 수정 후 통과 |
| 로컬 모의 파이프라인 | 실제 `assembleDebug`·`testDebugUnitTest` 결과(22건)로 `app-check` completed, 미연결 3건으로 gate CHECK_ERROR, 종료 코드 1. [판정](results/step3-20260921/local-sim-verdict.json) |
| workflow YAML 문법 | 로컬 미검사(PyYAML 없음, 설치하지 않음). GitHub 첫 실행에서 확인 |
| 실제 GitHub Actions 실행 | **대기.** PR 생성 후 확인 |
