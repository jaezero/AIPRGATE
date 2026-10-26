# 단계별 개발 진행 기록

## 프로젝트 목적 — 작업 전에 확인

**주 프로젝트는 'AI 생성 코드 PR 검증·리뷰·승인 관리 앱 구축'이다.**
2026-10-25 사용자가 PR 목록·검사 결과·승인을 통합한 앱이 필요하다고 정정했다.
자동화만 주 산출물로 해석한 과거 기록은 현재 요구사항이 아니다.
안드로이드 할 일 앱은 검사와 PR 시나리오를 재현하기 위한 샘플 앱이다.
앱 완성을 주 프로젝트 전체 완료로 표현하거나 앱 기능 확장으로 PR 프로세스 구축을 대체하지 않는다.

후속 개발 전에 프로젝트 루트의 [AGENTS.md](../AGENTS.md)를 읽는다.
주 목적, 목표 처리 흐름, 개발 우선순위, 자동 검사와 사람 승인의 역할,
실제 완료 기준을 그 문서에 정리했다.
최신 화면·기능·동작·완료 기준은 [관리 앱 요구사항](management-app-requirements.md)에 있다.
할 일 앱의 담당자·마감일 확장은 별도 범위다.

## 2026-10-27 — 실제 GitHub PR 검증 완료 (1인 운영)

검사 기반을 `jaezero/AIPRGATE` main에 게시하고 1인 운영 보호 설정을 적용했다.
실제 PR #2~#11로 정상 통과, 테스트·PR 정보·Lint·시크릿 차단과 수정 후 재검사,
검토 후 새 커밋의 검토 무효화, 막힌 PR의 API 병합 거부(405)와 main 직접 push 거부(GH006)를 확인했다.
AI 1차 리뷰봇(참고용, 승인 아님)을 추가했고, 로그인 기능 PR에서 AI가 실제 결함 2건을 지적해 수정했다.
사용 중 발견한 보호 설정 형식 오류와 앱 문구 결함도 같은 흐름으로 고쳤다.
작성자 외 승인은 구성원이 1명이라 검증하지 못했다.

- [검증 결과와 한계](results/pr-scenarios-20261026/summary.md)
- [AI 활용 기록](ai-usage-scenarios-20261026.md)
- [시나리오 정의](pr-scenarios-20261026.md)

## 2026-10-26 — 1인 운영 모드

저장소 구성원이 한 명이라 사용자가 본인 검토·병합 방식을 선택했다.
GitHub는 작성자의 자기 승인을 거부하므로, 작성자가 현재 head SHA에 표식 붙은
의견 리뷰(자기 검토)를 남기면 앱의 병합 조건을 충족하도록 했다.
팀용 보호 정책은 유지하고 `policy/main-protection-solo.json`과 감사 도구의 `--solo`를 추가했다.
JVM 75건과 검사 도구 104건이 통과했다.
요구사항의 작성자 외 승인·팀원 재실행은 1인 운영으로 충족하지 않는다.

## 2026-10-25 — 관리 앱 구현

사용자의 "진행해" 요청으로 Android 관리 앱을 구현했다.
메인 화면은 실제 GitHub PR 목록이며 기존 할 일 앱은 SampleActivity로 분리해
원래 코드와 `learning-tasks.db`를 보존했다.

- 공개 저장소 읽기, 사용자 인증 후 권한별 작업, 저장소 변경과 PR 필터·페이지 조회.
- PR 본문·AI 근거·변경 파일·diff·최신 check runs/커밋 상태·리뷰 이력.
- 개인 토큰과 GitHub App device flow, 사용자 확인 후 Keystore AES/GCM 세션 저장·로그아웃.
- 명시적 리뷰·수정 요청·승인 확인창, 제출 전 head/base 확인.
- 같은 실행의 필수 검사·최신 사람 승인·권한·GitHub 상태 확인 후 SHA를 지정한 병합 요청.
- 요청 오류·취소·누락·오래된 승인·중복 제출·조회 중 push의 상태 처리.

수정 후 통합 빌드·JUnit **72건 통과**와 Lint 실행을 확인했다.
기존 샘플·새 Keystore·관리 화면의 첫 기기 계측은 **15건 통과**했다.
인증 재시작·로그아웃 경합과 같은 SHA 대상 브랜치 변경을 추가 검증한 뒤
기기 계측은 **17건 통과**했다. 최종 코드 검토는 독립 모델의 APPROVE를 받았다.
실제 계정 인증·리뷰·병합의 인수 승인을 뜻하지 않는다.
전환 중 화면이 섞인 첫 캡처는 최종 화면 검증으로 사용하지 않고 수집 방법을 수정했다.
실제 익명 GitHub 응답의 관리 필드 생략으로 발생한 조회 결함은
수정 전 실패 회귀 테스트와 수정 후 통과 결과를 별도로 보존했다.

실행 기록은 `results/management-app/`, 사용법은 [관리 앱 사용법](management-app-usage.md),
AI 생성·수정 근거는 [AI 활용 기록](ai-usage-management-app.md)에 있다.
정확한 실행 시각은 로그의 UTC/PC 현지 시각을 따른다.
GitHub 사용자 인증·실제 다른 사람의 승인·병합·보호 적용은 별도 인수이며
도구로 외부 쓰기를 실행하지 않았다.

## 2026-10-25 — 관리 앱 요구사항 정정

관리 앱에서 실제 PR 목록·본문·AI 근거·diff·검사·리뷰·승인·병합을 확인하고
처리하는 방향으로 목적을 바로잡았다. 기존 검사 코드·정책은 재사용하고
할 일 앱은 샘플로 보존하는 것을 제안했다. 코드 이동·화면 구현은 하지 않았다.
관리 앱의 인증·API 연동·실제 승인·병합은 아직 미구현이다.

사용자가 `jaezero/AIPRGATE` 저장소를 제공했다. 공개 페이지에서 기존 main과 파일을
읽기 전용으로 확인했다. 로컬 Git 연결·커밋·푸시·보호 변경은 수행하지 않았다.
실제 연동 시 사용자 인증 설정과 작성자 외 리뷰어가 필요하다.

## 2026-10-25 — PR 자동 검증·승인 프로세스 구현

포스터의 PR 정보·빌드/JUnit·Lint 정책·Gitleaks 네 검사와 최신 결과를 집계하는
`quality-gate`를 구현했다. 실패 후 push/본문 수정 재검사, 이전 실행 취소,
누락·오류·시간 초과·오래된 커밋/본문/run/attempt 차단을 포함한다.
사람 승인 1명·오래된 승인 해제·마지막 push 재승인·필수 체크·main 보호의
적용용 설정과 읽기 전용 조회 도구를 준비했다.

최종 엔진 테스트 103건 통과, 실제 로컬 앱 빌드·단위 테스트 12건·Lint·시크릿
검사와 집계가 통과했다. Lint/Gitleaks 위험·정상 반례를 실제 도구로 시험했고,
게이트 입력 시나리오 10건과 실제 Lint 보고서 정책 분류 2건의 판정이 일치했다.
첫 실행의 Lint 실행기 오류와 수정 후 결과는 별도로 보존했다.

**실제 GitHub 단계는 미완료다.** 폴더는 Git 저장소가 아니며 저장소 주소를 받지 못했다.
Actions 호스팅 실행·보호 적용·사람 승인·직접 push/병합 차단·최종 병합을
확인했다고 주장하지 않는다. 임의 Git 초기화·커밋·푸시·외부 설정 변경도 하지 않았다.
로컬 합성 PR 식별자를 실제 PR 번호·SHA로 제시하지 않는다.

- [운영·재현·GitHub 적용 절차](pr-process.md)
- [검증 증거와 미확인 항목](results/pr-gate-20261025/verification-summary.md)
- [이번 AI 활용 기록](ai-usage-pr-process-20261025.md)

## 2026-10-25 — B단계: 팀 학습 할 일 앱·핵심 테스트·Room

이번 요청은 기존 프로젝트의 실제 앱 구현이다. 아래 2026-09-21 기록의
“앱 기능 구현 제외”와 “B 미착수”는 당시 범위·상태이며 이번 작업의 제한이 아니다.
기존 패키지 `com.example.aiprgate`, 테마, AGP·Gradle·Kotlin 구성을 유지했다.

### 구현

- 한국어 제목·설명·입력란·추가 버튼·빈 목록 안내를 제공한다.
- Repository에서 제목의 앞뒤 공백을 제거하고 빈 제목·공백만 있는 제목을 거부한다.
- ViewModel이 입력·로딩·저장 중·오류 상태와 추가·완료/취소·삭제를 처리한다.
- Compose 화면은 상태 표시와 사용자 동작 전달만 담당한다.
- Room Entity/DAO/Database와 Repository를 분리했다. 목록은 ID 오름차순이며
  완료 상태 변경 시 임의로 재정렬하지 않는다.
- 앱 전용 `learning-tasks.db`에 저장한다. 메인 스레드 DB 접근 허용이나
  destructive migration 우회는 사용하지 않았다. 버전 1 스키마를 내보냈다.
- Scaffold 시스템 바 여백 소비, IME 여백, 단일 LazyColumn 스크롤,
  최대 640dp 콘텐츠 너비를 적용했다.
- 로그인·서버·외부 API·AI 모델·PR 자동 검증은 추가하지 않았다.

### 주요 파일

| 파일 | 역할 |
|---|---|
| `app/src/main/java/com/example/aiprgate/MainActivity.kt` | 앱 ViewModel과 화면 연결, lifecycle-aware 상태 수집 |
| `.../TaskApplication.kt` | 앱 수명의 Room DB·Repository 구성 |
| `.../model/LearningTask.kt` | 화면·로직이 사용하는 할 일 모델 |
| `.../data/local/TaskEntity.kt`, `TaskDao.kt`, `TaskDatabase.kt` | Room 테이블·비동기 CRUD·관찰 쿼리·DB |
| `.../data/TaskRepository.kt`, `RoomTaskRepository.kt` | 저장소 계약, trim·빈 제목 검증, 엔티티 매핑 |
| `.../ui/TaskViewModel.kt`, `TaskScreen.kt` | 상태 처리와 한국어 Compose UI |
| `app/src/main/res/values/strings.xml` | 한국어 문구·항목별 접근성 이름 |
| `app/schemas/com.example.aiprgate.data.local.TaskDatabase/1.json` | Room 컴파일러가 생성한 실제 스키마 |
| `app/src/test/.../RoomTaskRepositoryTest.kt`, `TaskViewModelTest.kt` | 새 단위 테스트 11건 |
| `app/src/androidTest/.../TaskDatabaseTest.kt`, `TaskScreenTest.kt` | 새 Room·화면 계측 테스트 4건 |
| `scripts/verify-local.ps1` | 이 PC의 JDK 선택, 검사 실행, UTF-8 로그와 실제 종료 코드 보존 |
| `scripts/capture-evidence.ps1` | 이미 생성된 보고서·APK·입력 소스 해시 보존. 검사를 실행하는 스크립트는 아님 |
| `docs/DESIGN.md` | 기존 Material 3 테마를 유지하는 화면 구성·상태 기준 |

### 의존성과 호환성

AGP 9.4.1, Gradle 9.6.0, Compose 플러그인 2.2.10,
Compose BOM 2026.02.01, Java 소스/타깃 11을 유지했다.
Room 2.8.5, KSP 2.3.12, lifecycle 2.9.4,
테스트용 coroutines-test 1.10.2를 고정 버전으로 추가·정렬했다.
AGP 내장 Kotlin을 계속 사용하며 별도 Kotlin Android/kapt 플러그인은 추가하지 않았다.
호환성은 실제 KSP 코드 생성·앱/계측 APK 빌드·단위 및 기기 테스트 결과로 확인했다.

### 실제 검증 결과

원자료: [B단계 결과 폴더](results/app-20261025/).

| 실행 | 실제 결과 | 증거 |
|---|---|---|
| 기본 옵션 단위 테스트 | exit 1. 컴파일은 됐으나 세 테스트 클래스 로딩 실패 | `baseline.log`, `baseline.json` |
| 로컬 COMPAT 조건: debug APK·단위 테스트·Lint·계측 APK | exit 0, BUILD SUCCESSFUL in 17s | `compat.log`, `compat.json` |
| 단위 테스트 | 12건 통과, 실패/오류/생략 0. 새 앱 테스트 11건 + 기존 예제 1건 | 보존한 `TEST-*.xml` 3개 |
| Android Lint | 오류 0, 경고 15. 검사·경고를 비활성화하지 않음 | `lint-results-debug.txt` |
| Pixel_9_ASCII, Android 15/API 35 기기 테스트 | exit 0, BUILD SUCCESSFUL in 34s. 5건 통과, 실패/오류/생략 0 | `device.log`, `device.json`, `device-tests.xml` |
| 수동 앱 동작 | 추가·앞뒤 공백 제거·빈 제목 오류·완료·완료 취소·선택 삭제 확인 | 실제 화면 캡처, AI 활용 기록의 조작 내역 |
| 프로세스 종료 후 복원 | `am force-stop` 후 `am start -W`에서 남긴 항목의 제목·완료 상태 및 삭제 상태 유지 확인 | `reopened-light.png`, `reopened-dark.png` |
| 화면 | 세로/가로, 밝은/어두운 모드, 긴 제목 줄바꿈, 가로 스크롤 끝의 삭제 버튼 접근 확인 | `long-dark.png`, `error-*.png`, `landscape-*.png` |
| 고정 키보드 추가 검증 | 세로/가로에서 입력·스크롤·추가 버튼·실제 완료 키 제출 확인. 새 독립 AI 검토 PASS | `ime-docked-*.png`, `ime-landscape-submissions.png`, `visual-qa.md` |

단위 Repository 테스트의 fake DAO는 SQL·디스크를 증명하지 않는다.
실제 Room 테스트는 별도 UUID 이름의 DB만 사용·정리하고 앱 DB를 삭제하지 않는다.
디스크 DB를 닫고 다시 열어 완료·삭제 상태를 확인하는 테스트를 포함한다.
Compose 화면 테스트는 콜백 전달을 검증하며, 실제 앱의 저장·재시작 증거는
Room 테스트와 위 수동 조작으로 따로 확보했다.

첫 전체 검사 실행은 PowerShell 5가 JVM stderr를 NativeCommandError로 처리하여
로그 수집까지 중단됐다. stderr를 cmd 내부에서 stdout으로 합치도록 실행 스크립트를
수정한 뒤, 기본 옵션 실패와 COMPAT 성공을 각각 별도 파일에 보존했다.
기기 검사 감시 도구는 BUILD SUCCESSFUL 뒤 시간 초과 알림을 냈지만,
실제 스크립트는 17:50:11 +09:00에 exit 0을 기록했고 기기 XML도 5건 성공이다.
감시 도구의 완료 알림과 Gradle의 실제 결과를 구분했다.

### APK와 실행

APK:
`C:\Users\박재영\Desktop\기업 모둠학습\AiPrGate\app\build\outputs\apk\debug\app-debug.apk`

- 크기: 12,129,191 bytes
- SHA-256: `010EB943C285E82F28C04C145449D62E211C2EB1BBCF533E0AFAA852820AE2DA`
- 실제 파일과 입력 소스 SHA-256: `results/app-20261025/artifact-snapshot.json`

Android Studio에서 기존 프로젝트를 열고 Gradle JDK를
`C:\Program Files\Android\Android Studio1\jbr`로 선택한 후 Sync한다.
`app` 실행 구성과 기기를 선택하고 Run한다.
기본 `Hello Android!`가 아니라 `팀 학습 할 일` 화면이 표시된다.
Room 데이터를 확인할 때 앱 제거·데이터 지우기를 하지 말고 종료 후 재실행한다.

프로젝트 루트의 PowerShell에서 이 PC의 검사를 재현하는 명령:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-local.ps1 -Mode compat
# 에뮬레이터 부팅 후:
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-local.ps1 -Mode device
```

### 한계와 후속 항목

- 한글 프로젝트 경로에서 기본 UTF-8 Gradle 테스트 실행의 클래스 로딩 오류는 남아 있다.
  이 PC에서는 COMPAT 및 configuration cache 미사용 조건으로 모든 단위 테스트를 실행했다.
  전역 인코딩 변경, 테스트 생략, 검사 끄기, JDK/AGP 다운그레이드는 하지 않았다.
- Lint 경고 15개는 기존 Manifest의 중복 label, 기존 미사용 색 리소스,
  유지·추가한 고정 의존성의 새 버전 안내다. 무관한 테마 정리·일괄 업그레이드는 하지 않았다.
- 최초 AVD `Pixel_9_GA36`은 이미지 경로가 없어 부팅 실패했다.
  실제 설치된 API 35 이미지의 `Pixel_9_ASCII`로 검증했다.
- Gboard가 물리 키보드 도구막대/플로팅 모드여서 초기 좌표 조작 일부가 IME에 전달됐다.
  앱 재실행으로 IME를 숨긴 뒤 완료·삭제를 재검증했다.
  이후 Gboard의 Physical keyboard에서 Show on-screen keyboard를 켜
  실제 하단 고정 IME를 확보했다. 두 테마의 세로/가로에서
  입력란·추가 버튼 접근, 실제 완료 키 제출을 확인했다. 가로에서는 입력과 추가를
  스크롤하여 접근한다. `ime-docked-*.png`와 `ime-landscape-submissions.png`가 추가 증거다.
  모든 제조사 키보드 조합까지 검증한 것은 아니다.
- 실물 기기, API 24·37, Android Studio GUI의 Sync/디버거, 커버리지 수치는 미검증이다.
  IDE에서 위 실행 순서와 단위/기기 테스트를 직접 실행해 추가 확인할 수 있다.
- 과거 문서에서 연결한 `../기획문서/01_PRD...md`, `02_요구사항...md`는 현재 폴더에서
  찾지 못했다. 이번 사용자 메시지를 기능 인수 기준으로 사용했으며 원본 문서를 수정하지 않았다.
- 백업 속성과 추출 규칙은 기존 값을 유지했다. 백업·복구 정책 검증이나
  GitHub/PR 게이트 완료를 이번 앱 구현 실적으로 표시하지 않는다.

AI 입력·생성/수정·실행 추적: [AI 활용 기록](ai-usage-20261025.md).
시험용 추가 항목은 화면에서 각각 삭제했고, 앱 재실행 후 빈 목록까지 확인했다.
기기 회전·스타일러스 및 Gboard 옵션은 관측했던 원래 값으로 복원했다.
아래 A단계 기록은 과거 이력으로 보존한다.

## 2026-09-21 — A단계 중 문서·로컬 환경·기본 빌드 점검

요청 범위: 기존 프로젝트와 PRD/SRS v2.0 분석, Gradle·JDK·SDK 확인, 기본 빌드 검증, 리뷰 코멘트 생성 파인튜닝 필요성 및 변경안 검토. 앱 기능 구현·학습·학습 데이터 생성은 이번 범위에서 제외했다.

검토 문서: [PRD v2.0](../../기획문서/01_PRD_AI생성코드_PR자동검증게이트.md), [요구사항 v2.0](../../기획문서/02_요구사항명세서_AI-PR-Gate.md). 문서 전체를 읽고 기존 파일과 대조했다. 프로젝트 재생성·설정 일괄 교체·의존성 추가를 하지 않았다.

### 1. 범위 분류

| 구분 | 내용 |
|---|---|
| 주목표 | 근거가 있는 검사 정책을 PR 병합과 사람 승인 조건에 연결하고 증거로 검증 |
| 필수 | 로컬 할 일 앱, Room, 핵심 테스트, 품질·시크릿 정책 파일럿, 단일 게이트, 보호 규칙, 생성·검증 기록 |
| 선택/후속 | 캐시·CODEOWNERS·이식, 네트워크·서버·로그인·AI 리뷰 봇·다중 도구·모델 비교 |
| 미확정 | Room/커버리지 도구 호환 버전, 채택 룰·예외, CI 환경, GitHub 계정·보호 권한, 시간 목표·담당·가용 시간 |
| 추가 검토 | 사용자에게 목표를 질문한 결과 ‘리뷰 코멘트 생성’ 선택. 오프라인 비교 실험 후보이며 현재 학습 착수 아님 |

### 2. 현재 구현 상태

- 기존 Compose `MainActivity`는 `Hello Android!`를 표시하는 템플릿이다.
- Room·할 일 모델·ViewModel·Repository·DAO와 앱 동작 테스트는 없다.
- `ExampleUnitTest`는 2+2=4 확인, instrumented 예제는 앱 package 확인이다. 앱 기능 검증으로 세지 않는다.
- Git 저장소가 아니며 CI workflow·remote·PR 보호 설정도 확보되지 않았다.
- Manifest에 명시적 uses-permission은 없지만 최종 앱 권한 검증 완료로 보지 않는다. launcher activity의 exported=true는 역할에 따라 검토하며 일괄 취약 판정하지 않는다.
- allowBackup와 백업 규칙은 템플릿 상태다. 실제 데이터 정책은 APP-09 및 POL-01 작업에서 검토한다.

### 3. 요구사항별 현재 판단

| 요구사항 | 상태 | 이유·증거 |
|---|---|---|
| ENV-01 | 부분 확인 | 프로젝트·Compose 컴파일·APK 생성 확인. IDE 동기화·실행·디버깅 직접 검증 안 함 |
| ENV-02 | 부분 확인 | 로컬 버전·SDK·GPU·설치 AVD 기록. Room·CI·실제 사용 AVD 등 미확정 |
| ENV-03 | 부분 확인 | Wrapper·checksum·정적 버전·ignore 규칙 있음. Git 미초기화로 추적 파일/새 checkout 검증 불가 |
| ENV-04 | 부분 확인 | Windows 기본 빌드 확인, 기본 테스트는 인코딩 진단 옵션에서 통과. Linux CI 미실행 |
| ENV-05 | 미확인 | GitHub 저장소·플랜·설정 권한 미정 |
| TST-01~05 | 미구현/미검증 | 예제 테스트는 핵심 로직·Room·커버리지 요구 충족이 아님 |
| APP-01~09 | 기능 구현 대기 | 이번 요청에 따라 기능을 추가하지 않음 |
| EVD-01~05 | 기록 기반만 시작 | 입력 파일 해시와 변경 주체·로그 보존. 실제 앱 AI 생성 실험·독립 검토는 아직 없음 |
| POL·CI·GAT·GOV | 미구현 | 이후 단계 대상 |
| AC-01~13 | 최종 인수 미완료 | 로컬 환경 점검을 전체 인수 완료로 표시하지 않음 |

### 4. 실제 실행과 결과

| 실행 ID | 명령·내용 | 결과 | 근거 |
|---|---|---|---|
| ENV-A01 | Gradle `--version` | Gradle 9.6.0·Launcher Java 25.0.3 확인 | `results/environment-20260921/gradle-version.log` |
| ENV-A02 | `:app:assembleDebug :app:testDebugUnitTest --rerun-tasks --console=plain` | APK 생성 성공, JVM worker 시작 실패. 전체 exit 1·10초 | `build-test.log`, `run.json` |
| ENV-A03 | 기본 JVM 테스트 `--info` | 같은 GradleWorkerMain ClassNotFoundException 재현 | `test-diagnostic.log` |
| ENV-A04 | `-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=COMPAT`와 `--no-configuration-cache`를 지정한 JVM 테스트 | exit 0·12초, 예제 1건 통과 | `test-compat-diagnostic.log`, `example-unit-test.xml` |
| ENV-A05 | 입력 파일 전후 SHA-256 비교 | 37개 입력 파일 내용 동일 | `inputs-before.json`, `inputs-after.json` |

원자료는 [환경 점검 결과 폴더](results/environment-20260921/)에 있다. 전체 실행 시간은 캐시·기존 의존성이 있는 환경의 관측값이며 CI 목표나 성능 보장이 아니다. A04는 조건부 우회 성공이고 기본 옵션의 문제가 해결된 것은 아니다. 테스트를 skip하거나 기대값을 변경하지 않았다.

### 5. 문서와 구현의 충돌·개발 전 결정 사항

| ID | 연결 요구사항·문서 | 발견 사항 | 제안 |
|---|---|---|---|
| ISSUE-01 | ENV-01~04 | 시스템 JAVA_HOME이 없는 JDK 경로를 가리킴 | 로컬 실행 시 실제 JDK 지정. IDE와 향후 CI의 JDK 선택을 기록 |
| ISSUE-02 | ENV-04, NFR-04 | 한글 경로 검사 우회만으로 기본 단위 테스트가 통과하지 않음 | 인코딩 진단 결과 보존. 장기적으로 프로젝트·Gradle 캐시 ASCII 경로 정리 검토. 전역 인코딩 변경·버전 다운그레이드를 즉시 적용하지 않음 |
| ISSUE-03 | ENV-03, EVD-01/04, PR-01 | Git이 없어 기준 SHA·PR 이력 확보 불가 | 다음 기능 작업 전에 Git 기준 커밋 확보. 이번에는 조회만 수행 |
| ISSUE-04 | PRD 단계 A, ENV-05 | 로컬 앱 개발 전에 GitHub 보호 기능까지 완료하도록 읽히지만 계정·remote가 없음 | 로컬 개발 진입과 실제 PR 단계 진입을 분리. ENV-05는 PR 연결 전에 반드시 확인하고 A단계 전체 완료라고 쓰지 않음 |
| ISSUE-05 | CI-05, GAT-05, SRS 5.3 | concurrency·본문 hash 확인만으로 ‘확인 직후 본문 변경’과 같은 SHA의 상태 재사용까지 완전히 방지한다고 단정할 수 없음 | 실제 edited/취소/오래된 실행 경합을 AC-04/09로 검증. 코드 검사와 본문 변경 최신성 보장의 범위를 명시하고 사람의 병합 직전 확인 책임 유지 |
| ISSUE-06 | PRD 5.3, NFR-07, GAT/GOV | 새 파인튜닝 과제는 현재 AI 리뷰 봇 제외 범위·비용·일정과 별도임 | 필수 게이트에 바로 넣지 않고 선택 연구 트랙으로 변경안 제시 |
| ISSUE-07 | POL-04 | 품질·시크릿을 각각 BLOCK으로 둔 목표는 있으나 실제 룰은 아직 미정 | 정상 반례까지 검증한 룰만 선정. 근거 없이 도구 기본 ERROR를 모두 차단하지 않음 |

APP-09의 백업/권한 검토와 템플릿 속성은 아직 정책을 정하지 않은 상태이며 자동으로 문서 위반·보안 취약점이라고 단정하지 않았다. 두 문서의 앱 범위와 단일 workflow 방향은 대체로 일치한다.

### 6. 이번 변경 파일과 작성 주체

| 파일 | 변경 내용·작성 주체 |
|---|---|
| `docs/development-progress.md` | 이번 점검·요구사항·문제·다음 단계, AI 작성 |
| `docs/environment.md` | 관측 환경과 재현 명령·한계, AI 작성 |
| `docs/finetuning-review-plan.md` | 사용자 선택 목표에 따른 검토·평가·변경 제안, AI 작성 |
| `docs/results/environment-20260921/*` | 명령 출력·실행 메타데이터·기존 테스트 결과·파일 해시 보존 |

빌드 산출물·Gradle 캐시는 검사 실행으로 갱신되었다. 기존 앱 소스·Gradle 설정은 이번 턴에 수정하지 않았다. 이전 턴의 `android.overridePathCheck=true` 추가는 이미 입력 기준 상태에 포함되어 있다. 원본 PRD/SRS는 이번 검토로 덮어쓰지 않았다.

코드 생성 증거의 출처를 혼동하지 않도록 한다: 기존 템플릿은 사용자가 Android Studio에서 생성했다고 설명한 코드다. 이번 검토에서 신규 앱 코드를 생성한 사례가 아니다. 현재 대화가 사용자 요청과 목표 응답의 원본이며, 이 문서는 그 요약이다. 모델 표시명·세부 생성 설정은 로컬 증거로 확인하지 못했으므로 추정해 기록하지 않는다. Git SHA 대신 파일 hash로 이번 입력을 식별했으며 EVD 전체 완료로 처리하지 않는다.

### 7. Android Studio에서 사용자가 확인할 순서

1. 기존 프로젝트를 열고 Gradle JDK가 실제 설치된 경로인지 확인한다.
2. Sync를 수행한다. 한글 경로 우회 경고는 현재 알려진 경고다.
3. 에뮬레이터에서 Run하고 `Hello Android!`를 확인한다.
4. 예제 JVM 테스트는 [환경 문서의 명령](environment.md)으로 재현한다. 기본 실행과 진단 우회 결과를 구분한다.
5. 결과를 바탕으로 경로·JDK 실행 방법을 정리하고 Git 기준 커밋·생성 기록 준비 후 다음 앱 단계로 진행한다.

### 8. 다음 단계 진입 판단

**이번 요청의 문서 분석·로컬 빌드 점검·파인튜닝 계획 작성은 완료했다. A단계 전체와 앱 기능 인수는 완료가 아니다.** APK 빌드가 가능하며 예제 테스트의 임시 실행 경로를 확보했다. 안정적인 테스트 경로·IDE 실행·Git 기준점 정리가 남아 있으므로 다음 할 일 기능 구현을 자동 시작하지 않았다.

파인튜닝 검토 결론: 현재 필요성 미입증. [리뷰 파인튜닝 검토·변경안](finetuning-review-plan.md)에 1.5B 모델 우선 후보, 권한·그룹 분리·B0/B1/B2/F1 비교·비용·일정·FT 요구사항을 정리했다. 현재 수행한 것은 계획 작성뿐이다.

| 다음 단계 | 현재 상태 |
|---|---|
| A 잔여 환경·Git·GitHub 확인 | 대기 |
| B 할 일 앱·핵심 테스트·Room | 미착수 |
| C 최소 PR 연결 | 미착수 |
| D 정책 파일럿 | 미착수 |
| E 게이트·승인 상태 강화 | 미착수 |
| F 인수·교차 검증 | 미착수 |
| 선택 FT 연구 트랙 | 목표 확인·검토 계획 완료, 학습/데이터 작업 미착수 |
