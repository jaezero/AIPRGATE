# Android Implementation Guide

| 항목 | 내용 |
|---|---|
| 프로젝트 | AI-PR Gate 기준 앱 `C:\AIPRGATE` |
| 기준 문서 | `기획문서/01_PRD_AI생성코드_PR자동검증게이트.md` v2.0, `기획문서/02_요구사항명세서_AI-PR-Gate.md` v2.0 |
| 수정 근거 | `C:\Users\박재영\Desktop\기업 모둠학습\AI-PR_Gate_피드백반영_문제점과_수정계획.md` (프로젝트 밖. 아래 2.6 참고) |
| 상태 | **승인 대기 중인 구현 계획.** 이 문서의 계획·제안은 구현 완료나 검증 결과를 뜻하지 않는다 |

표기 규칙: **[확인]** 이번 점검에서 명령·파일로 직접 확인한 사실. **[기획]** PRD·SRS에 적힌 결정. **[제안]** 이 문서가 새로 제안하는 값으로, 사용자 승인 전에는 확정이 아니다. **[미검증]** 아직 실행·확인하지 못한 항목.

---

## 변경 이력

| 일시 | 변경 | 작성 |
|---|---|---|
| 2026-09-21 | 최초 작성. PHASE 0 환경 점검, PHASE 1 기획·소스 분석 결과와 Step 1~6 계획 | AI(Claude Code) 작성, 사용자 검토 전 |
| 2026-09-21 | 사용자 "진행" 승인. 3.5 디자인 제안은 제안값 그대로 적용, 앱 표시 이름은 `AIPRGATE` 유지 | 기록 |
| 2026-09-21 | 2.5 추가 의존성 버전 확정: lifecycle-*-compose 2.9.4, coroutines-test 1.9.0 (Step 1), Room 2.8.5·KSP 2.3.12 (Step 2). `room-ktx`는 불필요해 미추가. 근거는 `docs/development-progress.md` | AI 작성 |

---

## 1. 목표와 범위

### 1.1 프로젝트 목표 [기획]

AI를 활용해 작성한 안드로이드 코드에 대해, 선정 근거가 있는 검사 정책을 PR 병합 조건으로 연결하고 실제 작동 여부를 검증한다. 새 분석 엔진이나 AI 생성 여부 판별기를 만들지 않는다. 팀의 직접 기여는 정책 선정, 결과 집계와 오류 처리, 병합 제한 연결, 증거 관리다.

### 1.2 이번 필수 범위 (SRS 등급 M)

| 구분 | 내용 | 요구사항 ID |
|---|---|---|
| 필수 앱 기능 | Compose·ViewModel·Room 로컬 할 일 앱. 목록, 추가, 완료 토글, 삭제, 재실행 후 보존, 오류·재시도, 중복 요청 방지 | APP-01~09 |
| 필수 테스트 | fake repository 기반 JVM 테스트, 실제 Room instrumented 테스트, 에뮬레이터 재실행 보존 확인, 커버리지 측정·보고, assertion 사람 검토 | TST-01~05 |
| 검사 룰 파일럿 | 위험 후보 전체 검토, 품질(Android Lint 후보)·시크릿(gitleaks 후보) 각 범주에서 검증된 BLOCK 룰 활성화 | POL-01~07 |
| PR 게이트와 승인 | 단일 workflow `pr-check.yml`, 5개 job, 정규화 결과 계약, `quality-gate` 집계, required check, 작성자 외 승인 1명, 직접 push 제한 | PR-01~05, CI-01~08, GAT-01~05, GOV-01~04 |
| 증거·운영 | AI 생성 기록, 환경표, 위험·정책 선정표, 검증 추적표, README, 교차 재실행 | EVD-01~05, ENV-01~05, NFR-01~09 |

### 1.3 후속·선택·제외 범위

| 구분 | 내용 |
|---|---|
| 선택(S) | Gradle 캐시(CI-09), CODEOWNERS(PR-06), 다른 저장소 이식(NFR-10) |
| 후속·제외 [기획] | 로그인, 서버, Retrofit·네트워크, 외부 API 키, 동기화, 앱 배포, AI 리뷰 봇, 웹 대시보드, 중복 스타일 도구, 광범위 커스텀 분석, 의존성 취약점 자동 차단, 모델 비교, 외부 fork PR 자동 실행 |
| 앱 비필수 [기획] | 편집, 검색, 정렬 옵션, 제목 길이 제한 |
| 선택 연구 트랙 | 리뷰 코멘트 생성 파인튜닝 검토. 8장 참고. 필수 게이트와 분리 |

이번 계획은 로그인·서버·네트워크·뉴스 피드 등 기획에 없는 기능을 추가하지 않는다. 폐기된 약속인 15개 PR, 커버리지 60% 차단, 10분 보장, 60줄·복잡도 15 차단은 복원하지 않는다.

### 1.4 구현 사실과 제안의 구분

현재 구현된 앱 기능은 없다. 1~6장의 구조·UI·도구·명령은 모두 [제안]이며, 각 Step 완료 보고와 `docs/development-progress.md`에 실제 결과를 기록한 뒤에만 사실로 옮긴다.

---

## 2. 실제 개발 환경

### 2.1 확인된 도구 버전 (2026-09-21 점검)

| 항목 | 선언 값 | 실제 실행 값 | 근거 |
|---|---|---|---|
| OS | - | Windows 11 Pro 10.0.26200 amd64 | [확인] Gradle 출력 |
| Android Studio | - | `C:\Program Files\Android\Android Studio1`, build `AI-261.26222.65.2614.16204760`, 실행 중 | [확인] `product-info.json`, 프로세스 경로. 이 프로젝트의 IDE Sync·Run·Debug는 [미검증] |
| JDK (Gradle 실행) | daemon: Java 25, vendor 무관 (`gradle/gradle-daemon-jvm.properties`) | JetBrains Runtime 25.0.3 (`Android Studio1\jbr`) | [확인] `java -version`, `gradlew --version` |
| JAVA_HOME | - | 사용자 환경 변수 `C:\Program Files\Android\Android Studio1\jbr`, `bin\java.exe` 존재 | [확인] |
| GRADLE_USER_HOME | - | `C:\GradleCache` (ASCII 경로) | [확인] |
| Gradle Wrapper | 9.6.0, SHA-256 지정 | 9.6.0 | [확인] |
| AGP | 9.4.1 | 9.4.1로 빌드 성공 | [확인] |
| Kotlin (앱) | `kotlin.compose` 플러그인 2.2.10, AGP 내장 Kotlin 사용 | `kotlin-stdlib` 2.2.10 해석 | [확인] 의존성 트리 |
| Kotlin (Gradle 내장) | - | 2.3.21. 앱 컴파일러 버전과 다름 | [확인] |
| Java 소스·타깃 | `JavaVersion.VERSION_11` | - | [확인] |
| compileSdk / targetSdk / minSdk | 37 / 37 / 24 | 빌드 성공 | [확인] |
| SDK 위치 | `local.properties`: `C:\Android\Sdk` | platforms 34·35·36·37.0, build-tools 34.0.0~37.0.0 | [확인] |
| 에뮬레이터 | - | 실행 중 `emulator-5554` = AVD `Pixel_9_ASCII`, Android 15, API 35 | [확인] `adb` |
| 설치된 AVD | - | Medium_Phone, Pixel_9_ASCII, Pixel_9_GA36, Pixel_9_Manual, Pixel_9_UI | [확인] |
| Git | - | git 2.52.0 설치. **프로젝트는 Git 저장소가 아님** | [확인] |
| GitHub CLI | - | `gh` 미설치. remote·보호 규칙 없음 | [확인] |
| CI runner | - | 없음 | [미검증] |

### 2.2 기본 빌드·테스트 결과 (PHASE 0)

| 명령 | 결과 |
|---|---|
| `gradlew :app:assembleDebug` | [확인] BUILD SUCCESSFUL, 36 tasks |
| `gradlew :app:testDebugUnitTest --rerun-tasks` | [확인] 기본 옵션으로 BUILD SUCCESSFUL. 템플릿 테스트 1건 통과 |
| `gradlew :app:connectedDebugAndroidTest` | [확인] Pixel_9_ASCII API 35에서 템플릿 테스트 1건 통과 |
| `gradlew :app:installDebug` 후 실행 | [확인] `MainActivity` COLD 시작, 화면에 `Hello Android!` 표시 |

통과한 테스트는 템플릿의 `2+2=4`와 package 이름 확인뿐이다. 앱 기능·Room·커버리지 검증이 아니다.

### 2.3 Kotlin·Compose 관련 설정 [확인]

- AGP 9 내장 Kotlin을 사용하며 `org.jetbrains.kotlin.android` 플러그인은 없다.
- `buildFeatures.compose = true`, Compose BOM 2026.02.01. 해석 결과 Compose UI 1.10.4, Material3 1.4.0.
- `org.gradle.configuration-cache=true`, `org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8`.
- release `optimization.enable = false`. 이번 기본 variant는 `debug`다.

### 2.4 현재 의존성과 용도 [확인]

| 선언 | 해석 버전 | 용도 |
|---|---|---|
| `androidx.core:core-ktx` 1.10.1 | 1.18.0 (전이 상향) | Android API Kotlin 확장 |
| `androidx.lifecycle:lifecycle-runtime-ktx` 2.6.1 | 2.9.4 (전이 상향) | 생명주기 코루틴 |
| `androidx.activity:activity-compose` 1.13.0 | 1.13.0 | `setContent`, `enableEdgeToEdge` |
| Compose BOM 2026.02.01 | - | Compose 버전 정렬 |
| `compose-ui`, `ui-graphics`, `ui-tooling-preview`, `material3` | 1.10.4 / 1.4.0 | UI·미리보기·Material 3 |
| `ui-tooling`, `ui-test-manifest` (debug) | 1.10.4 | 미리보기 도구, UI 테스트 매니페스트 |
| `junit` 4.13.2 (test) | - | JVM 테스트 |
| `androidx.test.ext:junit` 1.3.0, `espresso-core` 3.7.0, `ui-test-junit4` (androidTest) | - | 기기 테스트 |

선언 버전과 해석 버전이 다른 항목이 있다. 이유 없이 선언 버전을 올리지 않으며, 새 의존성은 해석된 lifecycle 2.9.x 계열과 맞춘다.

### 2.5 추가 예정 의존성과 선정 이유 [제안]

정확한 버전은 해당 Step에서 공식 릴리스 노트와 실제 빌드로 호환성을 확인한 뒤 `libs.versions.toml`에 고정한다. 아래 표는 버전을 확정하지 않는다.

| Step | 의존성 | 선정 이유 | 확인할 점 |
|---|---|---|---|
| 1 | `androidx.lifecycle:lifecycle-viewmodel-compose` | Compose에서 `viewModel()` 사용. APP-08 | 해석된 lifecycle 2.9.x와 같은 계열 |
| 1 | `androidx.lifecycle:lifecycle-runtime-compose` | `collectAsStateWithLifecycle`로 생명주기 인식 수집 | 위와 동일 |
| 1 | `org.jetbrains.kotlinx:kotlinx-coroutines-test` (test) | ViewModel 코루틴을 JVM에서 결정적으로 실행. TST-01 | 캐시에 1.9.0 존재. 해석 버전과 일치 여부 |
| 2 | `androidx.room:room-runtime`, `room-ktx` | 기획의 저장 방식. APP-05 | AGP 9.4.1·Kotlin 2.2.10과의 호환 |
| 2 | `androidx.room:room-compiler` + KSP 플러그인 | Room 코드 생성. kapt보다 KSP가 공식 권장 경로 | **AGP 9 내장 Kotlin과 KSP 플러그인 조합이 이번 계획의 가장 큰 호환성 위험.** 실패 시 원인과 대안을 보고하고 승인 후 진행 |
| 2 | `androidx.test:runner`·`core` (androidTest) | Room DAO instrumented 테스트. TST-02 | 기존 androidTest 의존성으로 충분한지 먼저 확인 |
| 4 | gitleaks CLI (CI에서 고정 버전·체크섬으로 실행) | 시크릿 범주 후보 [기획] | 라이선스·출력 형식·종료 코드. Gradle 의존성 아님 |
| 6 | 커버리지 도구 1개 (AGP 내장 JaCoCo 우선 후보) | TST-04. 추가 플러그인 없이 사용 가능할 가능성 | AGP 9.4.1에서 JVM 테스트 커버리지 task 생성 여부 |

Hilt·Navigation·DataStore·Retrofit은 추가하지 않는다. 화면 하나와 저장소 하나에는 수동 주입으로 충분하다.

### 2.6 환경 문제와 미검증 항목

| ID | 내용 | 상태 |
|---|---|---|
| ENV-ISSUE-01 | 과거 JAVA_HOME이 없는 JDK 경로(`Android Studio\jbr`)를 가리켰다 | **현재 해결됨 [확인].** 사용자 JAVA_HOME은 실재하는 `Android Studio1\jbr`다. 단 PATH에는 `java.exe`가 없는 `Android Studio\jbr\bin`이 남아 있어 터미널의 `java` 명령은 찾지 못한다. `gradlew`는 JAVA_HOME을 쓰므로 빌드에는 영향이 없다. 전역 PATH는 변경하지 않았다 |
| ENV-ISSUE-02 | 한글 경로에서 JVM 테스트 worker 시작 실패 | **현재 프로젝트에서는 재현되지 않음 [확인].** 프로젝트 `C:\AIPRGATE`와 Gradle 홈 `C:\GradleCache`가 모두 ASCII 경로이고, 기본 옵션으로 `--rerun-tasks` 테스트가 통과했다. 원인이 해결된 것이 아니라 한글 경로를 쓰지 않아 조건이 사라진 것이다. COMPAT 옵션은 사용하지 않았다 |
| ENV-ISSUE-03 | `android.overridePathCheck=true` | 이전 한글 경로 사본(`Desktop\기업 모둠학습\AiPrGate`)에만 있다. 현재 프로젝트 `gradle.properties`에는 없고 필요하지도 않다 [확인] |
| ENV-ISSUE-04 | Git 저장소 아님 | 기준 SHA가 없어 EVD-01·EVD-04, ENV-03의 새 checkout 검증, PR-01을 진행할 수 없다. 임의로 초기화하지 않았다. **사용자 결정 필요** (5장 Step 1 진입 조건) |
| ENV-ISSUE-05 | 기존 작업 기록 위치 | 요청에 적힌 `C:\AIPRGATE\docs\*.md`와 `C:\AIPRGATE\AI-PR_Gate_…수정계획.md`는 존재하지 않는다. 같은 파일이 `C:\Users\박재영\Desktop\기업 모둠학습\`와 `C:\AndroidProjects\AiPrGate\docs\`에 있다(두 사본 내용 동일). 이번에는 읽기만 했다. PRD·SRS의 `../AI-PR_Gate_…md` 상대 링크도 현재 위치에서는 깨져 있다 |
| ENV-ISSUE-06 | Room·KSP 아티팩트가 Gradle 캐시에 없음 | Step 1~2에서 Google Maven·Maven Central 다운로드가 필요하다. 네트워크 차단 시 진행 불가 |
| ENV-ISSUE-07 | CI 환경 | Linux runner의 JDK 25·SDK platform 37 설치 방식 [미검증]. `gradle-daemon-jvm.properties`의 toolchain 자동 다운로드 여부도 CI에서 확인 필요 |
| 미검증 | Android Studio에서 이 프로젝트의 Gradle Sync·Run·Debug (ENV-01) | 사용자가 IDE에서 확인해야 한다 |

---

## 3. 앱 구조와 디자인

### 3.1 현재 UI 자료와 기존 테마 분석 [확인]

- 프로젝트에 `UI/`·디자인 폴더·화면 시안이 없다. 바탕화면의 PNG 도식은 발표용 아키텍처·플로우차트이며 앱 화면 디자인이 아니다.
- 현재 앱은 Android Studio Empty Activity 템플릿이다. `Greeting("Android")`만 표시한다.
- 테마 `AIPRGATETheme`: Material 3, Android 12 이상에서 dynamic color 사용, 그 외 템플릿 보라색 팔레트(Purple40·PurpleGrey40·Pink40 / 80 계열).
- Typography: `bodyLarge` 16sp·24sp line height만 정의, 나머지는 Material 3 기본값.
- `enableEdgeToEdge()`, Manifest `windowSoftInputMode="adjustResize"` 적용.
- XML 테마 `Theme.AIPRGATE` 부모는 `android:Theme.Material.Light.NoActionBar`.
- `app_name` = `AIPRGATE`.

### 3.2 최소 UI 안 [제안 — 승인 필요]

기획문서에는 색상·타이포그래피·간격 값이 없다. 아래 값은 모두 제안이며, 기획 확정값이 아니다.

**화면**: 단일 화면 `TaskListScreen`. 화면 개수를 목표로 두지 않는다 [기획].

```
┌───────────────────────────────┐
│ 할 일                          │  TopAppBar
├───────────────────────────────┤
│ [ 할 일 제목 입력        ] [추가]│  입력 영역
│   제목을 입력해 주세요 (오류 시) │
├───────────────────────────────┤
│ ☐ 보고서 초안 작성        [삭제]│  목록 (ID 오름차순)
│ ☑ 회의 자료 확인          [삭제]│
│ ...                           │
├───────────────────────────────┤
│ (Snackbar) 저장하지 못했습니다  │
│                      [다시 시도]│
└───────────────────────────────┘
```

**공통 컴포넌트**

| 컴포넌트 | 내용 |
|---|---|
| `TaskInputRow` | `OutlinedTextField` + `Button("추가")`. 키보드 완료 동작으로도 추가 |
| `TaskRow` | `Checkbox` + 제목 + `TextButton("삭제")`. 행 전체 클릭으로 토글. 완료 항목은 취소선 |
| `EmptyState` | "아직 할 일이 없습니다. 위에서 추가해 보세요." |
| `ErrorState` | 목록 조회 실패 안내 + `Button("다시 시도")` |
| `Snackbar` | 추가·토글·삭제 실패 안내와 "다시 시도" 동작 |

삭제는 아이콘 대신 텍스트 버튼을 제안한다. Material3 1.4에서 아이콘 라이브러리가 전이 포함되지 않을 수 있어 의존성 추가를 피하기 위해서다.

**색상**: 기존 테마를 유지한다. dynamic color를 켜 두면 기기 배경화면에 따라 색이 달라진다. 오류 표시는 `MaterialTheme.colorScheme.error`만 사용한다. 새 브랜드 색은 만들지 않는다.

**타이포그래피**: Material 3 기본 스케일. 제목 `titleLarge`, 항목 `bodyLarge`, 보조·오류 `bodyMedium`/`bodySmall`. 모두 sp 단위.

**간격**: 좌우 여백 16dp, 입력 영역과 목록 사이 8dp, 항목 최소 높이 56dp, 터치 대상 최소 48dp.

### 3.3 화면 상태

| 상태 | 표시 | 관련 ID |
|---|---|---|
| 로딩 | 첫 조회 완료 전 중앙 `CircularProgressIndicator` | APP-01 |
| 빈 목록 | `EmptyState` 문구 | APP-01 |
| 입력 오류 | 공백만 입력 시 저장하지 않고 입력란 아래 "제목을 입력해 주세요" | APP-02 |
| 추가 진행 중 | 추가 버튼·입력 비활성화. 같은 요청 재제출 불가 | APP-07 |
| 저장 실패 | 입력값 유지, "할 일을 저장하지 못했습니다" + 다시 시도 | APP-06 |
| 토글·삭제 진행 중 | 해당 항목의 체크박스·삭제 버튼만 비활성화 | APP-07 |
| 토글·삭제 실패 | 오류 안내. 화면은 실제 저장 상태를 유지하며 낙관적 반영을 하지 않음 | APP-06 |
| 조회 실패 | `ErrorState` + 다시 시도 | APP-06 |

### 3.4 접근성·글자 크기·키보드·시스템 여백

- 체크박스와 삭제 버튼에 항목 제목을 포함한 설명을 준다. 예: "보고서 초안 작성 삭제".
- 고정 높이 대신 최소 높이를 써서 시스템 글꼴 200%에서도 잘리지 않게 한다. 긴 제목은 줄바꿈한다.
- 입력란은 한 줄, IME 동작 `Done`으로 추가한다. 키보드가 열리면 `Scaffold` 인셋과 `imePadding`으로 입력 영역을 가리지 않게 한다.
- edge-to-edge 상태에서 상태바·내비게이션바 영역은 `Scaffold`의 `innerPadding`으로 처리한다.
- 색만으로 상태를 전달하지 않는다. 완료는 체크 상태와 취소선을 함께 쓴다.

### 3.5 확정 사항과 승인받을 제안

| 구분 | 항목 |
|---|---|
| 확정 [기획] | 단일 목록 화면, 추가·토글·삭제·빈 상태·오류·재시도, 데이터 `Task(id, title, isCompleted)`, ID 오름차순, 제목 중복 허용, 새 항목 미완료, 한국어 화면 문구 |
| **승인 필요** | ① 기존 dynamic color 유지 여부 ② 삭제를 텍스트 버튼으로 할지 ③ 간격 값 ④ 화면 문구 ⑤ 앱 표시 이름을 `AIPRGATE`로 둘지 "할 일"로 바꿀지 ⑥ 삭제 확인 대화상자 없음 |

---

## 4. 폴더 및 책임 구조 [제안]

단일 `app` 모듈, 패키지 `com.example.aiprgate`를 유지한다. 모듈 분리는 이번 규모에 맞지 않는다.

```
app/src/main/java/com/example/aiprgate/
  MainActivity.kt                 # setContent, ViewModel 생성
  AiPrGateApplication.kt          # (Step 2) DB·Repository 단일 인스턴스 보관
  data/
    Task.kt                       # 도메인 모델
    TaskRepository.kt             # 인터페이스: observeTasks(), addTask(), setCompleted(), deleteTask()
    local/
      TaskEntity.kt               # (Step 2) Room Entity
      TaskDao.kt                  # (Step 2) Room DAO
      AppDatabase.kt              # (Step 2) RoomDatabase
      RoomTaskRepository.kt       # (Step 2) TaskRepository 구현
  ui/
    tasks/
      TaskListViewModel.kt        # 상태·입력 검증·중복 요청 방지·오류 처리
      TaskListUiState.kt
      TaskListScreen.kt           # Compose UI와 공통 컴포넌트
    theme/                        # 기존 테마 유지
app/src/test/.../                 # FakeTaskRepository + ViewModel JVM 테스트
app/src/androidTest/.../          # 실제 Room DAO·Repository 테스트, 필요 시 UI 테스트
.github/workflows/pr-check.yml    # (Step 3)
.github/PULL_REQUEST_TEMPLATE.md  # (Step 3)
tools/gate/                       # (Step 3~5) adapter·schema·집계와 그 테스트
config/                           # (Step 4) 채택 룰·정책·예외
verification/fixtures/            # (Step 4) 비활성 룰 검증 입력
docs/                             # 진행 기록·환경표·선정표·추적표·AI 증거
```

| 계층 | 책임 | 테스트 |
|---|---|---|
| Compose UI | 상태 표시와 사용자 이벤트 전달만 담당 | UI 확인은 에뮬레이터. 필요 시 Compose UI 테스트 |
| ViewModel | 제목 trim·검증, 진행 중 플래그, 실패 시 입력 보존, 재시도 | fake repository로 JVM 테스트 (TST-01) |
| TaskRepository | 저장소 추상화. 실패는 예외로 전달 | 인터페이스 |
| Room 구현 | Entity·DAO·DB. `Flow`로 목록 관찰, suspend 함수로 변경 | instrumented 테스트 (TST-02). **fake 테스트를 DB 검증으로 표시하지 않음** |
| 게이트 코드 | 정규화 JSON 검증·집계·판정 | 결과 fixture 기반 테스트 |

게이트 집계 언어는 Python 표준 라이브러리를 제안한다. GitHub Linux runner에 기본 설치되어 있고 추가 패키지가 필요 없다. 로컬에는 Python 3.11·3.12가 있다. 최종 선택은 Step 3에서 확정한다.

---

## 5. 단계별 구현 계획

각 Step은 구현·관련 테스트·결과 기록까지 마친 뒤 멈추고, 다음 진행 지시를 기다린다. 결과는 `docs/development-progress.md`에 누적한다(현재 `C:\AIPRGATE\docs`는 없으므로 Step 1에서 새로 만든다).

### Step 0 — 진입 전 결정 (사용자)

| 결정 | 이유 | 권장안 |
|---|---|---|
| Git 저장소 초기화와 기준 커밋 | EVD-01은 첫 AI 생성 전 기준 SHA를 요구한다. Git이 없으면 생성 기록을 SHA에 연결할 수 없다 | Step 1 시작 전에 `git init`과 현재 템플릿 상태를 기준 커밋으로 만들기. 사용자가 승인하면 수행 |
| 기존 docs 기록 가져오기 | 과거 기록이 다른 폴더에 있다 | 원본은 그대로 두고, `docs/development-progress.md`에 과거 기록 위치를 링크만 한다 |

### Step 1 — 앱 구조와 기본 UI

- **의존성**: `lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`, `kotlinx-coroutines-test`.
- **구현**: `Task`, `TaskRepository`, `TaskListUiState`, `TaskListViewModel`, `TaskListScreen`. 목록·입력·빈 상태·로딩·입력 오류·실패·재시도 UI.
- **임시 데이터 표시**: 이 Step은 Room이 없으므로 `InMemoryTaskRepository`를 앱에 연결한다. 재실행 시 데이터가 사라진다는 사실을 코드 주석과 진행 기록에 명시하고, APP-05 충족으로 표시하지 않는다.
- **테스트**: `FakeTaskRepository`(실패 주입 가능)로 ViewModel JVM 테스트. 목록·빈 상태·정상/공백 추가·대상 토글·대상 삭제·실패 시 입력 보존·재시도·진행 중 중복 요청 차단.
- **UI 확인**: 에뮬레이터 실행, 빈 상태·추가·토글·삭제·공백 입력 화면 캡처.
- **관련 ID**: APP-01~04, APP-06~08(로직), TST-01, EVD-01~04.
- **종료 조건**: `assembleDebug`, `testDebugUnitTest` 통과, 에뮬레이터 UI 확인, 생성 기록 작성.

### Step 2 — 실제 저장과 앱 동작

- **의존성**: Room runtime·ktx·compiler, KSP 플러그인. 호환 버전 확인 후 고정.
- **구현**: `TaskEntity`, `TaskDao`, `AppDatabase`, `RoomTaskRepository`, Application 단 단일 인스턴스. 인메모리 구현을 Room으로 교체.
- **데이터 규칙**: ID 자동 생성, 새 항목 미완료, ID 오름차순 조회, 제목 중복 허용.
- **테스트**: 인메모리 Room DB instrumented 테스트로 DAO 삽입·조회·변경·삭제, 대상 외 항목 보존 확인(TST-02). 에뮬레이터에서 앱 강제 종료 후 재실행해 디스크 보존을 별도로 확인하고 캡처·로그를 남긴다(APP-05).
- **검토**: Manifest 권한 없음, 백업 규칙, 로그에 사용자 데이터 미출력 확인(APP-09). `allowBackup`은 일괄 취약 판정하지 않고 검토 결과를 기록한다.
- **관련 ID**: APP-01~09, TST-01~03, AC-01.
- **종료 조건**: JVM·instrumented 테스트 통과, 재실행 보존 확인, 실패 경로 UI 확인.

### Step 3 — 최소 PR 검사 연결

- **선행 확인**: Git 기준 커밋, GitHub 저장소·플랜·보호 규칙 사용 가능 여부(ENV-05). 비공개 저장소의 보호 규칙은 계정 플랜에 따라 제한될 수 있으므로 실제 설정 화면으로 확인한다. `gh` 설치는 사용자 승인 사항이다.
- **구현**: `.github/PULL_REQUEST_TEMPLATE.md`(PR-02), `.github/workflows/pr-check.yml`(CI-01·02). `app-check`에서 debug 빌드 후 JVM 테스트(CI-03). `quality-gate`는 `always()`로 실행하고 선행 결과를 검사(CI-04).
- **미연결 표시**: 이 Step에서 `quality-check`·`secret-check`·`pr-info`가 아직 실제 검사를 하지 않으면 `skipped` 또는 `error`로 기록하고 게이트를 통과시키지 않는다. 자리만 만든 job을 성공으로 표시하지 않는다.
- **권한**: `permissions: contents: read`, PR 조회가 필요할 때만 `pull-requests: read`(NFR-01). `pull_request_target` 사용 금지(NFR-03).
- **관련 ID**: PR-01·02, CI-01~04, GAT-01 기본, AC-06 일부.
- **종료 조건**: 실제 PR에서 정상 통과 1회, 컴파일 오류와 실패 assertion 각각의 실패 run 기록.

### Step 4 — 검사 정책 파일럿

- **위험 목록**: PRD 7장 위험 후보 전체를 `docs/risk-selection.md`에 적용성·영향·근거·탐지 수단·정상 반례·비용·처리로 기록(POL-01).
- **후보 실행**: Android Lint(`lintDebug`)와 gitleaks를 같은 프로젝트에서 실행해 버전·라이선스·룰 ID·출력·종료 코드를 확인(POL-02).
- **fixture**: 룰마다 위험·유사 정상·경계 입력. 튜닝용과 최종 확인용을 분리(POL-03). 시크릿 fixture는 형식만 맞춘 무효 값만 사용하고 제품 스캔에서 분리한다.
- **정책표**: `docs/policy-decisions.md`에 원본 등급과 BLOCK·WARN·REPORT를 따로 기록. 미등록 룰은 REPORT(POL-05). 예외는 정책 ID·사유·검토자·만료 포함(POL-06).
- **연결**: 검증된 BLOCK 룰만 `quality-check`·`secret-check`에 연결. 품질·시크릿 각 범주에 검증된 BLOCK 정책이 하나도 없으면 완료가 아니라 범위 재검토로 보고한다(POL-04).
- **관련 ID**: POL-01~07, AC-05, AC-07 일부.

### Step 5 — 게이트와 승인 완성

- **결과 계약**: 각 adapter가 SRS 5.2 정규화 JSON과 원본 리포트를 run 고유 artifact로 업로드(CI-07).
- **집계 판정**: PASS, POLICY_FAIL, CHECK_ERROR, STALE. 누락·중복·스키마 오류·wrong SHA/run/revision·취소·timeout·필수 skipped는 비성공(GAT-03). WARN·REPORT만 있으면 통과하되 표시(GAT-04). 결과 fixture로 판정 테스트.
- **최신성**: head·base·tested SHA와 run ID 기록(CI-06). 집계 직전 PR 현재 head·base·본문 해시 재조회 후 불일치 시 통과 금지(GAT-05). concurrency로 이전 실행 취소(CI-05). 본문 수정 `edited` 재평가(PR-04). 본문 필수 항목 자동 검사(PR-03).
- **입력 안전**: PR 본문·파일명을 shell에 보간하지 않고 환경 변수·파일로 전달(NFR-02).
- **저장소 설정**: `quality-gate` required, 작성자 외 승인 1명, 새 커밋 시 승인 무효화, 최신 브랜치 요구, 직접 push·force push·삭제 제한, 관리자 우회 범위 문서화(GOV-01~04).
- **실제 PR 검증**: 승인 전 병합 거부, 직접 push 거부, 새 커밋 후 재승인 요구, base 갱신 후 재검사(AC-09~11). 검사용 실패 PR은 main에 병합하지 않는다.
- **관련 ID**: PR-03~05, CI-04~08, GAT-01~05, GOV-01~04, NFR-01~03, AC-04, AC-06~12.

### Step 6 — 최종 검증과 문서화

- 요구사항·AC별 검증 추적표 `docs/verification-matrix.md`와 결과표(SRS 8.3 형식).
- 실행 시간 측정: 대기열·runner 실행·승인 대기 분리, 최초·캐시 실행 구분(NFR-05).
- 커버리지: 도구 1개로 핵심 로직 라인 커버리지 측정. 패키지·제외·분모·버전 공개, 차단하지 않음(TST-04).
- 생성 기록과 검사 증거 연결 감사(AC-03), CI artifact 만료 전 보존(NFR-08).
- 실제 구현도 `docs/architecture.md`, README, 운영 가이드.
- 다른 팀원의 README 기반 재실행 기록(NFR-09). 이 항목은 AI가 대신할 수 없다.
- 실패·미검증·잔여 한계 보고.

---

## 6. 인수 조건 매핑

Step 번호와 사례 행 수는 PR 개수가 아니다. 하나의 PR에서 여러 SHA·상태를 확인할 수 있고, 로컬 fixture로 검증하는 사례도 있다.

| Step | 요구사항 | AC | 검증 방법 | 증거 |
|---|---|---|---|---|
| 1 | APP-01~04, APP-06~08, TST-01 | AC-01 일부 | fake repository JVM 테스트, 에뮬레이터 UI 확인 | 테스트 XML, 화면 캡처, 생성 기록 |
| 2 | APP-05, APP-09, TST-02·03, ENV-01~04 | AC-01 | Room instrumented 테스트, 종료·재실행 보존 확인, Manifest·로그 리뷰 | androidTest 결과, 재실행 캡처·로그, 리뷰 기록 |
| 3 | PR-01·02, CI-01~04, GAT-01, ENV-05 | AC-06 일부 | 실제 PR 정상·컴파일 오류·실패 assertion run | PR·SHA·run ID·job 로그 |
| 4 | POL-01~07 | AC-05, AC-07 일부 | 룰별 위험·정상·경계 fixture 로컬 실행 | 위험 선정표, 정책 결정표, 룰 출력 |
| 5 | PR-03~05, CI-04~08, GAT-01~05, GOV-01~04, NFR-01~03 | AC-04, AC-06~12 | 집계 fixture 테스트, 실제 PR 상태 전환, 보호 규칙 동작 | 집계 테스트 결과, PR 기록, 설정 캡처, 거부 로그 |
| 6 | TST-04·05, EVD-01~05, NFR-04~09 | AC-02, AC-03, AC-13 | 커버리지 리포트, 증거 감사, 반복 실행, 교차 재실행 | 리포트, 추적표, 시간 원자료, 팀원 실행 기록 |

---

## 7. AI 활용 및 변경 이력

### 7.1 기록 원칙

- 기존 코드는 Android Studio Empty Activity 템플릿이다(파일 생성 시각 2026-09-21 17:36). **AI 생성 코드로 표시하지 않는다.**
- 이 Guide.md와 이후 Step에서 Claude Code가 작성하는 코드·문서는 AI 생성물이다.
- 입력 원본, AI 원본 출력, 사람 수정 diff, 최종 통합 코드, 검사 결과를 분리해 보존한다(EVD-02·04).
- 정상·실패·컴파일 실패·거절·재시도를 포함한 전체 시도를 목록에 남긴다(EVD-03). 성공한 것만 남기지 않는다.
- 이번 작업은 일반 개발 요청 관찰에 해당한다. 의도적 위험 생성이나 수작업 주입이 아니다. 위험 fixture를 만들 때는 유형을 따로 표시한다.

### 7.2 기록 항목 [제안]

`docs/ai-evidence/index.md`에 시도마다 한 행을 둔다.

| 필드 | 이번 세션 값 |
|---|---|
| 도구 | Claude Code CLI (Windows) |
| 표시 모델 | PHASE 0 일부는 `claude-fable-5-1`, 이후 사용자 `/model` 전환으로 `claude-opus-5`. 세부 생성 설정은 확인 불가 |
| 기준 코드 | Git 없음. 기준 커밋 확보 후 SHA 기록. 그 전에는 파일 해시로 식별 |
| 입력 | 사용자 요청 원문, 제공 문맥(기획문서·기존 소스) |
| 출력 | 생성 파일, 세션 기록 위치 `C:\Users\박재영\.claude\projects\C--AIPRGATE\` |
| 후속 요청 | 사용자 수정 지시와 재생성 이력 |
| 결과 | 빌드·테스트 명령과 결과, 실패 포함 |
| 검토 | 다른 팀원의 검토자 이름·의견 (EVD-05) |

세션 원본 기록에는 로컬 경로 등이 들어 있으므로 공유 전 사람 검토가 필요하다.

---

## 8. 리뷰 코멘트 파인튜닝 검토

기존 `finetuning-review-plan.md`(2026-09-21, `Desktop\기업 모둠학습\AiPrGate\docs`)의 요약이다. 계획만 있고 모델 다운로드·추론·학습·데이터 생성은 수행되지 않았다.

| 항목 | 요약 |
|---|---|
| 목표 | 리뷰 코멘트 생성: 이미 확인된 finding에 대해 문제 근거와 검토 의견을 한국어로 작성. 새 취약점 탐지·자동 수정은 제외 |
| 현재 판단 | **학습 필요성 미입증.** 기준 앱·검사 룰·리뷰 데이터·기준선이 없어 개선 여부를 판단할 수 없다 |
| 개선 기준선 | B0 룰별 정적 템플릿, B1 고정 1.5B 모델+기본 프롬프트, B2 같은 모델+검증 집합으로 개선한 프롬프트, F1 같은 모델+SFT adapter. 주 비교는 F1 대 B2 |
| 모델 후보 | `Qwen/Qwen2.5-Coder-1.5B-Instruct` 우선, 7B는 확장 후보. 공식 카드 Apache-2.0 표시. revision 해시 고정 필요 |
| 하드웨어 | RTX 3050 8GB, 드라이버 560.94. CUDA·학습 라이브러리·WSL2 호환성 미확인. LoRA도 이 GPU에서 가능하다는 보장 없음 |
| 데이터 출처·권한 | 팀 자체 코드·리뷰 우선. 오픈소스 리뷰 코멘트는 라이선스·사용 조건 확인 전 수집 금지. 외부 모델 출력을 정답으로 삼지 않음. 실키·개인정보·권한 불명확 자료 제외 |
| 정답 검토 | 2인 독립 검토와 이견 조정. 사실 오류·위치·근거·활용성·불확실성·정상 사례 기준 |
| 중복 제거·분리 | 원본·수정 전후·변형·같은 대화·fork를 하나의 원형 그룹으로 묶고 한 split에만 배정. 해시·유사도 기반 중복 감사 |
| 누출 방지 | 행 무작위 분할 금지. 프롬프트 예시·튜닝 입력에 테스트 정답·변형 금지. 테스트를 보고 수정했다면 새 테스트 확보 |
| 평가 | 근거 정확성, 실질적 사용 가능 비율, 정상 사례 부당 지적률, 누락·과도한 보류, 위치·근거 참조 정확성, 안전성·형식, 비용·시간. 블라인드·순서 섞기, 원형 그룹 단위 불확실성. BLEU·LLM 단독 채점은 주 기준 아님 |
| 평가 조건 고정 | Go/No-Go 기준은 최종 테스트를 보기 전에 고정. "정확도 90%" 같은 임의 수치 약속 없음 |
| 비용·일정 | 확정 비용 없음. 별도 약 12~24 작업일 이상 추정. 2026-10-30 안에 앱·게이트와 함께 완료를 약속하기 어려움 |
| 중단 조건 | 권한 있는 데이터 부족, 메모리·환경 불가, 평가자 불일치, 누출 없는 테스트 부족, B2 대비 개선 없음 |
| 변경 제안 | PRD G-06 후보, 5.3에 조건부 오프라인 실험 추가, FT-01~08 요구사항 후보. **원본 PRD·SRS에는 미반영.** 모델 결과는 `quality-gate`·승인 조건에 영향 없음 |

**이 Guide.md 승인은 학습·데이터 제작·모델 다운로드·유료 자원 사용 승인이 아니다.** 연구 트랙은 필수 게이트(Step 5)가 안정된 뒤 별도 요청으로만 시작한다.

---

## 9. 문서 간 모순과 구현 차단 요소

| ID | 관련 요구사항 | 내용 | 처리 제안 |
|---|---|---|---|
| DOC-01 | PRD 헤더, SRS 헤더 | `../AI-PR_Gate_…수정계획.md` 상대 경로가 현재 프로젝트 위치에서 깨져 있다 | 원본 문서는 수정하지 않고 이 Guide에 실제 위치를 기록. 링크 수정 여부는 사용자 결정 |
| DOC-02 | EVD-01, EVD-04, ENV-03, PR-01 | 기준 SHA를 요구하지만 Git 저장소가 없다 | **구현 차단 요소.** Step 1 전 Git 초기화 승인 필요 |
| DOC-03 | PRD 11 단계 A, ENV-05 | 로컬 앱 개발 전에 GitHub 보호 기능 확인까지 끝내도록 읽힌다 | 로컬 앱 Step 1~2는 진행하고, ENV-05는 Step 3 진입 조건으로 둔다 |
| DOC-04 | SRS 9 저장소 구조 | 이름 `ai-pr-gate-android/`와 실제 `AIPRGATE`가 다르다 | 구조 예시로 해석하고 현재 이름 유지 |
| DOC-05 | CI-05, GAT-05, SRS 5.3 | concurrency와 본문 해시 대조만으로 "확인 직후 본문 변경"까지 막는다고 단정할 수 없다 | AC-04·AC-09 실제 경합으로 확인하고 사람의 병합 직전 확인 책임 유지 |
| DOC-06 | POL-04 | 품질·시크릿 각 범주에 BLOCK 룰이 필수지만 실제 룰은 미정 | Step 4 파일럿 결과로 결정. 도구 기본 ERROR를 그대로 차단값으로 쓰지 않음 |
| DOC-07 | PRD 5.3, NFR-07 | 파인튜닝 목표는 현재 제외 범위(AI 리뷰 봇·모델 비교)와 별도다 | 선택 연구 트랙. PRD·SRS 개정은 사용자 결정 |
| DOC-08 | ENV-04, CI-02 | CI Linux runner에서 JDK 25와 SDK platform 37 확보 방식이 정해지지 않았다 | Step 3에서 실제 runner로 확인 |

---

## 10. 사용자 확인이 필요한 결정

1. **Git 저장소 초기화와 기준 커밋 생성**을 허용할지.
2. 3.5의 **디자인 제안**(dynamic color 유지, 텍스트 삭제 버튼, 간격, 문구, 앱 표시 이름).
3. Step 1에서 Room 없이 **인메모리 저장소로 UI를 먼저 연결**하는 순서에 동의하는지.
4. Step 3 전에 **GitHub 저장소 소유 계정과 공개 여부**를 정하는 것.
