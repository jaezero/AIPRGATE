# AI 생성 코드 PR 자동 검증 및 승인 프로세스

2026-10-25 데굴데굴 포스터를 기준으로 구현했다.
**주 산출물은 이 PR 프로세스이며 안드로이드 앱은 실제 검사 대상이다.**
현재 로컬 코드·검사·파일럿은 구현했지만 GitHub 저장소 연결·보호 규칙 적용·실제 사람 승인과
병합까지 완료한 상태는 아니다. 포스터의 과거 진행 상태와 최신 실측을 구분한다.

## 1. 포스터와 구현의 대응

| 포스터 단계 | 구현 | 확인 범위 |
|---|---|---|
| Android Studio 개발, AI 기록 | 기존 앱, `docs/ai-usage-20261025.md`, 이번 생성 기록 | 앱의 로컬 빌드·테스트 검증 |
| PR 생성과 필수 정보 | `.github/PULL_REQUEST_TEMPLATE.md`, `checkPrInfo` | 필수 정보·AI 여부·근거 누락 및 형식 검사 |
| 4개 검사 동시 실행 | `.github/workflows/pr-gate.yml`와 composite action | 네 독립 job과 결과 업로드를 코드·테스트로 확인, 호스팅 실행 미확인 |
| Quality gate | `tools/pr-gate/core.mjs`, `cli.mjs` | 실제 로컬 결과 집계, 오류/누락/최신성 입력 시나리오 검증 |
| 수정 후 다시 push | `pull_request.synchronize`, `edited`, concurrency | 이벤트·취소 설정 검증, 실제 PR 재실행 미확인 |
| 작성자 외 1인 승인, 새 커밋 재승인 | `policy/main-protection.json` | 설정·약화 탐지 테스트, 실제 계정 적용 미확인 |
| 1인 운영: 승인 0명 + 앱의 작성자 자기 검토 | `policy/main-protection-solo.json`, `--solo` | 오프라인 감사·약화 탐지 테스트. 타인 승인 요구사항은 미충족 |
| main 병합과 직접 push 금지 | 필수 `quality-gate`, strict, 관리자 포함 보호, bypass 없음 | 적용용 설정과 조회 도구 준비, 실제 차단·병합 미확인 |

```text
AI 입력·원본 응답 기록 -> 코드 이해·수정·테스트 -> PR 템플릿 작성
  -> [PR 정보 | 빌드·JUnit | Android Lint 정책 | Gitleaks] 병렬
  -> quality-gate 집계와 최신 PR 재조회
       BLOCK: 선정한 정책 위반 -> 실패, 수정 후 push
       ERROR: 누락·실행 오류·시간 초과·오래된 결과 -> 실패, 원인 해결 후 재실행
       PASS: 자동 검사 통과 -> 작성자 외 사람 리뷰·승인
  -> 필수 체크·최신 변경·사람 승인 조건 충족 -> main 병합
```

PASS를 자동 승인이나 이미 병합된 상태로 표시하지 않는다.

## 2. 검사 정책과 선정 근거

기준 파일: `policy/checks.json`. Gitleaks 설정: `policy/gitleaks.toml`.
실제 사용한 도구 버전은 Gradle 설정과 고정된 Gitleaks bootstrap에 있다.

| 검사 | 차단 근거와 적용 범위 | 자동 검사로 확인하지 못하는 것 |
|---|---|---|
| PR 정보 | 목적·작성자 설명·테스트 근거·yes/no AI 사용 여부, yes일 때 AI 기록 필요 | 작성자가 정말 이해했는지, 기록이 사실인지 |
| 빌드·단위 테스트 | debug 빌드, 실행된 JUnit 보고서 필요. 실패·오류·생략은 통과 불가 | 작성되지 않은 요구사항·테스트 |
| Lint | 실제 위험·정상 파일럿으로 검증한 `TrustAllX509TrustManager`만 BLOCK | 비어 있지 않은 잘못된 인증서 검증, 모든 보안 문제 |
| Gitleaks | 실제 파일럿으로 검증한 `generic-api-key`만 BLOCK | 모든 자격증명 형식·외부 저장 위치·이력의 모든 노출 |

다른 Lint/Gitleaks 탐지는 기본 WARN이다. upstream 규칙이 존재한다는 이유만으로
이 프로젝트에서 파일럿을 완료했다고 하지 않는다. 실행 오류는 WARN으로 낮추지 않는다.
각 검사 실행 시간 제한은 정책 파일에 있고 GitHub job 전체에도 상한을 둔다.
이는 성능 달성 목표가 아니라 멈춘 검사를 실패 처리하기 위한 제한이다.

정상 반례로는 기본 `TrustManagerFactory` 사용과 환경 변수에서 키를 읽는 형태를 시험했다.
위험 예시는 사람이 의도적으로 주입한 결함이며 AI가 자연스럽게 생성한 위험 사례로 포장하지 않는다.

### 경고·보고·예외

- `block`: quality-gate 실패.
- `warn`: 결과에 표시하고 리뷰어가 확인. 그 자체만으로 자동 차단하지 않음.
- `report`: 결과 파일에 보존. 경고로 승격하거나 숨은 차단으로 사용하지 않음.
- `exceptions`: 현재 비어 있다. 예외가 필요하면 룰 ID·정확한 상대 파일 경로·사유·검토자 GitHub login·만료일을 기록한다.
- 경로 wildcard·상위 경로·빈 사유·빈 검토자·잘못된 만료일은 정책 오류다.
- 예외는 품질/시크릿의 특정 finding만 WARN으로 조정한다. 빌드·테스트·PR 정보 또는
  도구 실행 실패·결과 누락을 우회할 수 없다.
- CI는 PR의 정책이 아니라 **base 커밋의 정책과 검사 코드**를 읽는다.
  예외 변경도 독립적인 사람 리뷰를 거쳐 기준 브랜치에 반영되어야 한다.

### 검사 범위와 시크릿 보존

Gitleaks 8.30.1 공식 Linux x64 아카이브의 SHA-256을 확인한 후 실행한다.
현재 체크아웃 파일을 `dir`로 검사하며 Git 전체 이력 검사는 아니다.
기본 규칙을 확장하고 `.git`, `.gradle`, `.idea`, `.kotlin`, `build`라는
디렉터리 구간만 생성물로 제외한다. 이 이름의 디렉터리에 작성 소스를 두지 않는다.
임의 PR-local 설정, ignore 파일, inline `gitleaks:allow`로 검사 기준을 바꾸지 못하게 했다.
최종 결과에는 룰·상대 경로·줄 번호만 남기고 시크릿 값·소스 줄은 보존하지 않는다.
도구 원본 보고서는 100% redaction과 임시 폴더 정리 대상으로 취급한다.

## 3. 게이트가 확인하는 값

네 검사는 각각 JSON receipt를 생성한다.

- 저장소와 PR 번호
- 정확한 head와 base SHA
- PR 제목·본문·작성자·브랜치 정보의 digest
- 신뢰한 검사 정책·시크릿 설정의 digest
- workflow run ID와 재실행 attempt
- PASS/BLOCK/ERROR, finding, 실행 시간

집계기는 결과가 정확히 4개인지, 중복·누락이 없는지 확인한다.
job이 skipped/cancelled이거나 결과가 없으면 실패한다.
receipt의 자체 판정만 믿지 않고 기준 정책으로 finding을 다시 판정한다.
마지막에 GitHub API에서 현재 열린 PR을 다시 조회하여 커밋·base·본문 등과 비교한다.
조회 실패도 ERROR다. CI에서는 합성 event·임의 run ID·로컬 latest 파일을 사용할 수 없다.

PR 본문 수정은 커밋이 같아도 다시 검사한다. 단, 집계 직후의 본문 변경과 병합을
하나의 원자적 거래로 묶는 기능은 아니다. 리뷰어는 병합 직전 최신 PR과 체크를 확인한다.
strict 체크는 기준 브랜치 변경 시 최신 상태로 갱신하도록 하는 별도 조건이다.

### 신뢰 경계

일반 `pull_request` 이벤트와 읽기 권한만 사용한다. PR 코드를 높은 권한으로 실행하는
`pull_request_target`, 자동 승인, 자동 병합은 사용하지 않는다.
체크아웃 자격증명도 작업 폴더에 보존하지 않는다.

base checkout은 정책·검사 코드의 실수나 일반적인 PR-local 설정 변경을 분리하기 위한 것이다.
그러나 PR이 workflow 자체를 바꾸거나 빌드 스크립트가 runner에서 임의 코드를 실행하는
상황까지 변조 불가능하게 만드는 sandbox는 아니다. 제어 파일 변경에는 신뢰할 수 있는
리뷰를 적용하고, 강한 강제가 필요한 운영 환경에서는 조직의 별도 required workflow 등
추가 통제를 검토해야 한다. 현재 구현을 공격자에게 절대 우회 불가능한 시스템으로 주장하지 않는다.

## 4. 로컬 실행

새 JavaScript 패키지 의존성 없이 **Linux x64/WSL의 Bun 1.4.3**을 사용한다.
Android 빌드는 이 PC의 기존 Windows JDK·SDK를 사용한다.
검사 엔진 검증:

```sh
bun tools/pr-gate/verify.mjs docs/results/pr-gate-20261025/engine-tests
```

실제 앱에 네 검사를 적용하는 로컬 실행:

```sh
cd "/mnt/c/Users/박재영/Desktop/기업 모둠학습/AiPrGate"
bun tools/pr-gate/cli.mjs local \
  --root . --control . \
  --event tools/pr-gate/fixtures/pr-valid.json \
  --latest-file tools/pr-gate/fixtures/pr-valid.json \
  --run-id 3 --attempt 1 \
  --windows-root 'C:\Users\박재영\Desktop\기업 모둠학습\AiPrGate' \
  --out docs/results/pr-gate-20261025/local-run-3
```

합성 PR의 SHA와 example 저장소는 시험 입력이며 실제 PR의 증거가 아니다.
앱의 빌드·단위 테스트·Lint·시크릿 검사는 실제로 실행한다.
로컬에서는 app/build를 공유하는 Gradle 두 단계를 직렬 실행하여 충돌을 막는다.
GitHub에서는 독립 runner의 네 job을 병렬 실행한다.

현재 도구처럼 OmO 내장 Bun만 제공하는 환경의 동일한 실행 명령 접두부:

```sh
BUN_BE_BUN=1 /home/jaeyeong/.omo/binary-runtime/5.1.40/omo
```

도구 출력이 종료 코드 0이라고만 되어 있어도 receipt에 ERROR가 있으면 게이트는 실패한다.
이전 보고서를 재사용하지 않도록 이번 실행 대상의 생성 JUnit/Lint 보고서를 먼저 정리한다.
원본 앱 코드나 보존된 증거를 삭제하지 않는다.
Windows 한글 경로 테스트의 COMPAT 조건은 로컬 실행기에만 적용한다.
GitHub Linux job에는 이 우회를 복사하지 않는다.

## 5. GitHub 적용 순서 — 아직 실행하지 않음

1. 사용할 `owner/repository`, 접근 권한, 요금제에서 보호 기능 사용 가능 여부를 확인한다.
2. 로컬 파일과 변경 내용을 검토하고 Git 기준 상태를 정한다.
   커밋·푸시·저장소 생성은 사용자의 명시적 허가를 받고 수행한다.
3. **초기 bootstrap:** 검사 코드·정책·composite action을 먼저 main에 게시해야 한다.
   첫 기능 PR의 base에 제어 파일이 없으면 검사 실행에 실패하며 이를 우회해 통과시키지 않는다.
4. 실제 PR을 열어 Actions가 실행되는지 확인한다. 정상 PR에서 4개 job과 `quality-gate`를 확인한다.
5. 저장소 관리자가 `policy/main-protection.json`을 기준으로 보호를 적용한다.
6. 작성자와 다른 팀원이 리뷰·승인한다. 새 커밋 후 예전 승인이 해제되는지 확인한다.
7. 승인 없는 병합, 필수 체크 실패, main 직접 push가 실제로 제한되는지 확인한다.
8. 문제 수정·재검사·재승인 후 정상 병합을 확인하고 로그·화면·PR 번호와 SHA를 보존한다.

설정은 `quality-gate`를 GitHub Actions 앱(ID 15368)이 발행한 필수 체크로 묶고,
최신 base 요구·승인 1명 이상·오래된 승인 해제·마지막 push에 대한 별도 승인·
관리자 포함 보호·리뷰 bypass 없음·force push/삭제 금지를 포함한다.
GitHub 자체가 PR 작성자의 자기 승인을 필요한 승인으로 인정하지 않는 동작을 사용한다.
체크리스트나 앱의 가짜 승인 버튼으로 이를 대체하지 않는다.

### 설정 조회와 적용 도구

설정 파일 자체에 필요한 조건이 있는지 로컬 검사:

```sh
bun tools/pr-gate/github-protection.mjs --file policy/main-protection.json
```

운영자가 `GITHUB_TOKEN`을 안전하게 제공한 후 **읽기 전용**으로 실제 설정 확인:

```sh
bun tools/pr-gate/github-protection.mjs --repo OWNER/REPOSITORY
```

이 문서의 OWNER/REPOSITORY는 사용자가 정할 대상이며 실행 실적이 아니다.
토큰을 코드·PR 본문·로그에 붙여넣지 않는다. 권한/조회 오류를 보호 없음으로 해석하지 않는다.
`--apply`를 명시하면 외부 설정을 변경하므로 대상과 변경에 대한 사용자 허가 후에만 사용한다.
도구는 기존 보호가 있으면 임의로 덮어쓰지 않고 수동 조정을 요구한다.
새 보호를 적용할 때는 다시 조회하여 실제 조건을 확인한다.
저장소 관리자가 나중에 설정을 해제하는 것까지 방지하는 기능은 아니다.

## 6. 파일럿과 평가

- Lint: `tools/pr-gate/LINT-PILOT.md`. 실제 격리 앱에서 위험·정상 코드를 각각 컴파일하고 Lint 실행.
- 시크릿: `tools/pr-gate/SECRET-PILOT.md`. 실제 고정 Gitleaks에서 무효 리터럴/환경 변수 참조 비교.
- 게이트: `tools/pr-gate/pilot-gate.mjs`. 누락·오류·취소·시간 초과·이전 실행·최신 커밋/본문 변경을 합성 입력으로 시험.
- 실행 증거: `docs/results/pr-gate-20261025/`.

룰 파일럿, 합성 게이트 입력 시험, 실제 GitHub PR 인수 시험은 별개다.
로컬 평가 결과를 실제 PR에서 차단·승인·병합한 결과로 표시하지 않는다.
PR 수를 15개로 고정하지 않고 검사 목적별 위험·정상 사례를 먼저 확보한다.
실행 시간은 각 실행에서 관측한 값이며 CI 속도 보장이나 전반적인 검출률이 아니다.
