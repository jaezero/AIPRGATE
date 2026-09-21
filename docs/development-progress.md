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
