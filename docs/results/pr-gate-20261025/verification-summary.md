# PR 프로세스 구현 검증 기록

검증일: 2026-10-25. 로컬 구현 검증이며 실제 GitHub PR 인수 결과가 아니다.

| 항목 | 관측 결과 | 원자료 |
|---|---|---|
| 최종 엔진·정책·워크플로우 테스트 | 103 통과, 0 실패, exit 0, 자식 테스트 실행 4,368ms | `engine-tests-final/run.json`, `tests.log` |
| 이전 엔진 실행 | 102 통과, 0 실패 | `engine-tests/` |
| 실제 앱 빌드·JUnit | PASS, 단위 테스트 12건, 실패·오류·생략 0 | `local-run-2/build-test.json`, `unit-counts.json`, `TEST-*.xml`, `build-test.log` |
| 실제 앱 Lint | PASS. 기존 경고는 기록하며 선정 BLOCK 규칙 위반 없음 | `local-run-2/quality.json`, `lint-results-debug.xml`, `quality.log` |
| 실제 Gitleaks·PR 형식 검사 | 각각 PASS | `local-run-2/secrets.json`, `pr-info.json` |
| 실제 로컬 결과 집계 | quality-gate PASS | `local-run-2/quality-gate.json`, `summary.md` |
| 오류 상태의 실제 집계 | 첫 실행의 Lint 실행기 오류가 ERROR로 집계됨 | `local-run-1/` |
| Lint 위험·정상 반례 | 두 코드 모두 컴파일. 위험 코드에서 선정 룰 1건, 정상 코드 0건 | `lint-pilot/`, `../../../tools/pr-gate/LINT-PILOT.md` |
| Gitleaks 위험·정상 반례 | 무효 시험 리터럴 검출, 환경 변수 참조는 통과. 실제 도구 테스트 포함 | `../../../tools/pr-gate/SECRET-PILOT.md`, 최종 `tests.log` |
| 게이트 입력 시나리오 | 기대 판정 10/10 일치, 실제 Lint 보고서 정책 분류 2/2 일치 | `gate-scenarios/scenario-results.json` |
| 보호 설정 오프라인 CLI | exit 0, `configured: true`, `missing: []` | `policy/main-protection.json`을 실제 CLI로 조회. 원격 적용 결과 아님 |

로컬 실측: PR 정보 0ms, 시크릿 877ms, 빌드·테스트 10,314ms,
Lint 11,063ms. 공유 `app/build`를 쓰는 두 Gradle 단계는 로컬에서 직렬 실행했다.
GitHub job은 독립 runner에서 병렬 실행하도록 구성했다. 이 수치는 CI 실측이 아니다.

## 결함 발견과 수정

첫 전체 로컬 실행은 PowerShell의 단일 요소 배열이 문자열로 평가되어
Lint 명령 인수가 잘못 연결됐다. `$tasks`를 명시적 배열로 바꿨다.
첫 ERROR 증거를 보존하고 두 번째 실행을 별도 경로에 저장했다.
검사 실패를 무시하거나 Lint를 생략하여 통과시키지 않았다.

최종 회귀 검사에는 CI에서 합성 event·최신 PR 파일·run ID·attempt 및
로컬 실행 모드를 거부하는 실제 CLI 자식 프로세스 검증을 추가했다.

## 증거의 범위

`local-run-*`는 실제 앱/도구 실행이지만 PR 번호·저장소·SHA는
`tools/pr-gate/fixtures/pr-valid.json`의 합성 입력이다.
게이트 시나리오는 실제 로컬 receipt에 누락·취소·시간 초과·새 커밋·본문 수정·
이전 run/attempt·정보 누락을 주입한 합성 시험이다.
실제 GitHub에서 push·차단·재승인·병합했다고 해석하면 안 된다.

Lint 위험·정상 반례의 공통 기준 경고는 9건이며 위험 코드는 추가 경고 2건이다.
그중 파일럿 선정 룰만 BLOCK으로 분류한다. 모든 upstream 탐지를 검증한 것은 아니다.
무효 시험 자격증명만 사용했고 최종 시크릿 결과에는 값·소스 줄을 남기지 않는다.

## 미확인 및 외부 의존 항목

- 현재 폴더는 Git 저장소가 아니다. Git 초기화·커밋·푸시는 수행하지 않았다.
- 저장소 주소 질문에 응답이 없어 실제 GitHub 저장소·권한·요금제는 확인하지 못했다.
- Actions 호스팅 실행, required check 등록, 브랜치 보호 적용, 직접 push 거부,
  승인 없는 병합 차단, 새 커밋 후 재승인, 최종 병합은 미실행이다.
- 적용 명령은 제공하지만 외부 쓰기 권한을 임의로 사용하지 않았다.
- JavaScript/Kotlin LSP가 구성되지 않아 LSP 진단은 실행하지 못했다.
  JavaScript는 실제 Bun 테스트, Android는 실제 Gradle 실행으로 검증했다.
- 별도 읽기 전용 통합 검토는 공급자 사용량 제한으로 결과를 반환하지 못했다.
  독립 검토가 통과했다고 기록하지 않는다.

GitHub 인수 절차는 [운영 문서](../../pr-process.md)의 5절에 있다.
