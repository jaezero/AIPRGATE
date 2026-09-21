# Step 3 실제 CI 실행 기록 — PR #1

저장소: https://github.com/jaezero/AIPRGATE, PR: https://github.com/jaezero/AIPRGATE/pull/1 (base `main`, head `feature/step3-pr-check`).
runner `ubuntu-24.04`, Temurin JDK 25, Python 3.12. 캐시 미사용. 결과는 GitHub 공개 API의 run·job·check-run annotation 으로 확인했다.
job summary 와 artifact 본문은 로그인 없이 API 로 읽을 수 없어 주석으로 대조했다.

| 사례 | 커밋 | run ID | 주입 유형 | app-check | quality-gate 주석 (판정 사유) | 기대와 일치 |
|---|---|---|---|---|---|---|
| S3-01 정상 (최초) | `47aa463` | [35588697741](https://github.com/jaezero/AIPRGATE/actions/runs/35588697741) | 없음 | success, 3분 4초 | 당시 주석 기능 없음. job 결론: gate failure, 미연결 3 job failure | 예 |
| S3-02 정상 | `ab5e7cd` | [35589105270](https://github.com/jaezero/AIPRGATE/actions/runs/35589105270) | 없음 | success, 2분 22초 | CHECK_ERROR: NOT_CONNECTED ×3. **결함 발견**: 게이트 자체 테스트의 가짜 판정이 주석으로 섞임 | 판정은 일치, 주석 결함 |
| S3-03 정상 (주석 수정 후) | `0061ee4` | [35590085666](https://github.com/jaezero/AIPRGATE/actions/runs/35590085666) | 없음 | success, 3분 9초 | CHECK_ERROR: 위반 0, 검사 오류 3 (pr-info·quality-check·secret-check NOT_CONNECTED) | 예 |
| S3-04 컴파일 오류 | `a630d1b` | [35590468138](https://github.com/jaezero/AIPRGATE/actions/runs/35590468138) | 수작업 주입 (오타 `taskRepositry`) | failure, 2분 37초 | 위반 1: APP-BUILD `MainActivity.kt:18` Unresolved reference 'taskRepositry'. 검사 오류 4: SUBCHECK_SKIPPED(test, build_failed) + NOT_CONNECTED ×3 | 예 |
| S3-05 테스트 실패 | `621a9ad` | [35591390980](https://github.com/jaezero/AIPRGATE/actions/runs/35591390980) | 수작업 주입 (제목 `trim()` 제거) | failure, 3분 19초 | 위반 2: APP-TEST `제목의_양끝_공백을_제거해_저장하고_입력을_비운다` (expected [장보기] but was [  장보기  ]), `공백만_입력하면_저장하지_않고_안내한다`. 검사 오류 3: NOT_CONNECTED | 예 |
| S3-06 정상 복귀 | `ea12808` | [35591800043](https://github.com/jaezero/AIPRGATE/actions/runs/35591800043) | 주입 되돌림 | success, 3분 3초 | CHECK_ERROR: 위반 0, 검사 오류 3 (NOT_CONNECTED) | 예 |

집계:
- 상태 사례 6건, PR 1개, CI 실행 6회, 수작업 주입 2건. AI 생성 관찰 사례가 아니다.
- 정책 일치: 기대한 게이트 판정과 일치 6/6. S3-02 는 판정은 맞았으나 주석 출력 결함이 있었고 S3-03 에서 수정했다.
- 목표 원인 검출: 컴파일 오류 → APP-BUILD 1/1, assertion 실패 → APP-TEST 1/1. 두 사례는 서로 다른 원인으로 구분되었다.
- 실행 장애: 0건. 단, 확인 도구 쪽에서 GitHub 비인증 API 한도(시간당 60회) 초과가 1회 있었다. CI 실행과는 무관하다.
- 시간은 runner 기준 job 시작→종료이며 대기열 시간은 포함하지 않는다. 캐시 미사용, 표본 5회라 목표값 근거로 쓰지 않는다.
