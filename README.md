# AiPrGate

**PR 목록·검사 결과·사람 리뷰·승인·병합을 통합하는 관리 앱**을 만드는 프로젝트다.
앱이 GitHub의 실제 데이터와 사용자 권한으로 동작하는 것이 목표다.
Android 할 일 앱은 빌드·테스트·정적 분석을 실행하는 샘플이다.

최신 방향과 화면·기능·완료 기준은 [관리 앱 요구사항](docs/management-app-requirements.md)에 있다.
연동 대상은 사용자가 제공한 `jaezero/AIPRGATE`다.

## 현재 상태

- 관리 앱 화면·실제 PR API·기기 코드/개인 토큰 인증·리뷰/승인/병합 요청 코드를 구현했다.
- 기본 대상은 `jaezero/AIPRGATE`이며 공개 조회는 인증 없이 시작한다.
- 기존 할 일 앱은 목록 아래 샘플 진입점으로 보존했다.
- 사용자 인증과 실제 두 사람의 승인·병합 인수는 계정 설정·사용자 동작이 필요하다.
- 아래 자동화는 관리 앱이 조회하는 검사 기반이다.

- PR 템플릿, 네 독립 검사 job, 최신 PR 확인을 포함한 `quality-gate`를 구현했다.
- 검사 오류·누락·취소·시간 초과·오래된 결과는 통과시키지 않는다.
- 검증된 Lint/Gitleaks 규칙만 차단하고 나머지 탐지는 경고로 남긴다.
- 사람 승인·재승인·main 필수 체크의 적용용 설정과 조회 도구를 준비했다.
- 이전 검사 엔진 103건, 관리 앱을 포함한 JVM 테스트 72건과 기기 테스트 17건이 통과했다.
- **새 워크플로우 게시·보호 적용·실제 계정 승인·병합 인수는 미실행이다.**

## 시작하기

[관리 앱 사용법](docs/management-app-usage.md)에 로그인·PR 화면·권한·승인·병합
사용 방법과 실제 연동 인수의 제한을 정리했다.
Android Studio에서 `app`을 실행하면 관리 앱이 열린다.
APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성된다.

[운영·실행 문서](docs/pr-process.md)에 로컬 명령, GitHub 초기 게시 순서,
보호 설정 조회와 적용 절차가 있다. 제어 파일을 먼저 main에 게시해야
이후 PR에서 base 기준 검사 코드를 사용할 수 있다.

Linux x64/WSL, Bun 1.4.3에서 검사 엔진을 실행한다.

```sh
bun tools/pr-gate/verify.mjs docs/results/pr-gate-20261025/engine-tests
bun tools/pr-gate/github-protection.mjs --file policy/main-protection.json
```

로컬 Android 검사는 이 PC의 Windows JDK/SDK를 사용한다.
설정·예외는 `policy/`, 실행 코드는 `tools/pr-gate/`,
GitHub 자동화는 `.github/`에 있다.

- [실측 결과와 미확인 범위](docs/results/pr-gate-20261025/verification-summary.md)
- [관리 앱 실측과 실제 공개 PR 확인](docs/results/management-app/verification.md)
- [개발 진행 기록](docs/development-progress.md)
- [관리 앱 AI 활용 기록](docs/ai-usage-management-app.md)
- [검사 기반 AI 활용 기록](docs/ai-usage-pr-process-20261025.md)
- [프로젝트 목적과 후속 작업 지침](AGENTS.md)

자동 검사 PASS는 사람의 승인이나 병합 완료를 의미하지 않는다.
