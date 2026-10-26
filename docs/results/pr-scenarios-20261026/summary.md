# 실제 GitHub PR 검증 결과 (2026-10-26 ~ 27)

저장소: `jaezero/AIPRGATE`. 운영 방식: **1인 운영 모드**(구성원 1명).
시각은 GitHub 기록의 UTC다(KST = UTC+9). 원자료는 같은 폴더의 `github-record.json`
(GitHub REST API 읽기 전용 조회를 정리한 것으로, `self_review`처럼 본문 표식에서 계산한 필드가 있다)이며,
이 문서는 그 요약이다. 수집 시점은 PR #11 병합 직후(2026-10-26 15:53 UTC 이후)라서
PR #4 항목은 이 문서를 추가하기 전 상태(`1d9ef0e`, open)로 남아 있다.

## 1. 적용한 병합 조건

| 항목 | 실제 설정 (API 조회) |
|---|---|
| 필수 검사 | `quality-gate` 1개, GitHub Actions 앱(ID 15368)이 올린 결과만 인정 |
| 최신 main 기준 | `strict: true` — main이 바뀌면 브랜치 갱신 후 재검사해야 병합 가능 |
| 관리자 포함 | `enforce_admins: true` |
| PR 필수 | main 직접 push 금지 |
| 필수 승인 수 | 0 (1인 운영. GitHub는 작성자 자기 승인을 받지 않음) |
| 새 커밋 시 승인 해제 | `dismiss_stale_reviews: true` — GitHub는 **승인(APPROVED)**만 해제한다. 1인 운영의 자기 검토(COMMENTED) 무효화는 앱이 강제한다 |

보호 설정은 `policy/main-protection-solo.json`을 적용했다. 첫 적용에서 GitHub가 HTTP 422로
두 번 거절했고(`contexts`와 `checks` 동시 지정, 조직 전용 `bypass_pull_request_allowances`),
그 수정을 PR #7로 같은 검사·검토를 거쳐 병합했다.

## 2. 시나리오별 결과

| ID | PR | 변경 | 실패 실행 | 수정 후 실행 | 결과 |
|---|---|---|---|---|---|
| S1 정상 | #2 | 회귀 테스트 1건 | — | 40325462148 (`053b43d`) 통과 | 자기 검토 후 앱에서 병합 `ff70ab4` |
| S2 테스트 실패 | #3 | 신규 테스트 + `trim()` 제거 주입 | 40325989563 (`ba02f85`) BLOCK: 76건 중 3건 실패 | 40334923251 (`4f4c9a7`) 통과 | 병합 `a20b9fc` |
| S3 PR 정보 누락 | #4 | 문서 1개, 본문 템플릿 그대로 | 40325994294 (`1d9ef0e`) BLOCK: `PR_REQUIRED` 4건 | 40334908860 (같은 `1d9ef0e`) 통과. 본문 수정 API 응답의 `updated_at` 13:55:36, 실행 시작 13:55:39 | 이후 같은 PR에 이 결과 문서를 추가해 병합(S3 증거 커밋과 최종 병합 커밋은 다름) |
| S4 Lint | #5 | 빈 `checkServerTrusted` 주입 | 40325998750 (`5e46e65`) BLOCK: `lint:TrustAllX509TrustManager` | 40334935136 (`925938e`) 통과 | main 대비 변경 0 → 병합 없이 종료 |
| S5 시크릿 | #6 | 무작위 무효 키 리터럴 주입 | 40326000915 (`adf2813`) BLOCK: `gitleaks:generic-api-key` | 40334939665 (`706d8a7`) 통과 | main 대비 변경 0 → 병합 없이 종료 |
| S6 검토 후 새 커밋 | #3 | 자기 검토(`4f4c9a7`) 뒤 커밋 `df44461` | 앱: "이전 커밋 대상", 병합 불가 | 재검토(`98bf4a7`) 후 병합 | 앱에서 오래된 검토 재사용 안 됨(GitHub 강제 아님, 1절 참고) |

S2·S4·S5의 결함은 검출 시험을 위해 **사람이 주입한 것**이며 AI가 자연스럽게 만든 위험 코드의 관찰 사례가 아니다.
각 차단 사유 원문: `gate-summary-<커밋>.md`.

## 3. 병합 차단의 실제 증거 (검사 로그와 별도)

| 시도 | GitHub 응답 | 증거 |
|---|---|---|
| quality-gate 실패 상태의 PR #3을 API로 병합 (관리자 권한 계정, 요청 본문 `sha=ba02f857…`, 2026-10-26 13:0x UTC경, 정확한 시각 미기록) | `405 Required status check "quality-gate" is failing.` | `merge-attempt-pr3.json` |
| main에 직접 push (`82ae7a3` 위의 빈 커밋, 같은 시각대, 정확한 시각 미기록) | `GH006 Protected branch update failed — Changes must be made through a pull request.` | `direct-push-attempt.txt` |

앱에서도 막힌 PR은 병합 버튼이 비활성화되고 사유가 표시된다(`screens/06-pr3-review-blocked.png`).

## 4. 실제 개발 PR과 AI 리뷰

| PR | 내용 | AI 리뷰가 지적한 실제 문제 | 처리 |
|---|---|---|---|
| #7 | 보호 설정 형식 수정 | — (AI 리뷰 도입 전) | 병합 |
| #8 | 병합 후 '병합할 수 없습니다' 표시 결함 수정 | — | 병합 |
| #9 | AI 리뷰봇 워크플로우 | AI 기록 파일 누락, 금지어 방식 테스트의 누락(`MultiEdit`) | 테스트를 허용 목록 비교로 강화, 기록 파일은 이 문서와 함께 추가 |
| #10 | 샘플 앱 로컬 로그인 | 손상된 저장 데이터로 앱 종료 가능, 계정 상태 오류가 '비밀번호 틀림'으로 표시 | 수정 커밋 `a7e810c` + 테스트 3건, AI 재리뷰에서 해결 확인 |
| #11 | 앱에 AI 의견 표시, behind 사유, 상태 용어 개선 | PR 본문에 용어 변경 설명 누락, AI 의견 없음/조회 실패 구분 불가, 초안 문구 불일치 | 본문 보완. 나머지는 한계로 수용 |

AI 리뷰의 오판 사례: PR #10에서 AI 기록의 날짜(KST 2026-10-27)를 UTC 기준으로 "미래 날짜, 오기"라고 판단했다.
사람이 확인해 반영하지 않았다. AI 의견은 승인으로 집계하지 않는다.

## 5. 앱 화면 (실제 저장소, 데모 데이터 아님)

| 파일 | 내용 |
|---|---|
| `screens/03-list-open.png` | 실제 PR 목록과 검사·승인 요약 |
| `screens/04-pr3-summary.png` | PR 본문(AI 사용·테스트 근거) |
| `screens/05-pr3-checks.png` | 테스트 실패로 막힌 검사 탭 |
| `screens/06-pr3-review-blocked.png` | 병합 불가 사유 |
| `screens/08-pr2-selfreview-dialog.png` | 자기 검토 확인창(대상 커밋 표시) |
| `screens/11-pr2-merge-dialog.png` | 병합 확인창(확인한 커밋 병합) |
| `screens/13-pr2-merged-top.png` | 병합 완료 메시지 |
| `screens/17-pr3-stale-review-bottom.png` | 새 커밋 후 자기 검토가 '이전 커밋 대상'으로 표시 |
| `screens/18-pr7-merged-fixed-app.png` | PR #8 수정 후 병합된 PR의 '병합 완료' 표시 |

캡처 일부는 PR #11의 용어 변경('열림' → '검토 중' 등) 이전 화면이다.

## 6. 한계

- **1인 운영:** 필수 승인 수가 0이라 GitHub는 사람 검토를 강제하지 않는다. 자기 검토는 앱 안에서만
  병합 조건이고, GitHub 웹에서는 게이트만 통과하면 병합할 수 있다. 작성자 외 승인은 검증하지 못했다.
  팀 운영 정책(`policy/main-protection.json`, 작성자 외 1명 승인·마지막 push 재승인)은 준비만 했다.
- **검사 범위:** 로직 결함은 테스트가 있는 부분만 잡는다. Lint 차단 규칙은 검증한 1개뿐이고 나머지는 경고다.
- **BUILD_DEBUG 표시:** 빌드와 테스트를 한 Gradle 실행으로 돌려 테스트 실패 시 `BUILD_DEBUG`도 함께 표시된다(S2).
- **앱의 검사 상세:** 차단 사유(실패한 테스트·규칙)는 Actions 결과 파일에만 있어 앱 검사 카드에는
  "상세 메시지가 없습니다"로 표시된다.
- **워크플로우 출처:** `pull_request` 이벤트는 PR 쪽 워크플로우 파일로 실행된다. 검사 도구·정책은 base에서
  가져오지만, 쓰기 권한자가 워크플로우 파일 자체를 고치면 우회할 수 있다(신뢰된 구성원 범위).
- **AI 리뷰봇:** Max 구독 OAuth 토큰을 사용하며 본인 사용 한도에서 차감된다. `gh pr comment` 허용 범위에는
  자기 댓글 수정·삭제 옵션도 포함된다. 본문만 수정하면 다시 리뷰하지 않는다.
- **S4·S5 재통과:** 결함 파일을 제거해 main과 같아진 상태의 통과라, 결함을 고친 정상 코드의 통과(정상 반례)로는 약하다.
  정상 반례는 Lint·시크릿 파일럿(`tools/pr-gate/LINT-PILOT.md`, `SECRET-PILOT.md`)의 로컬 시험에 있다.
- **AI 기록의 시점:** PR #2~#11은 병합 당시 `docs/ai-usage-scenarios-20261026.md`가 없었다(이 PR에서 추가).
- **strict 운영 비용:** PR 하나를 병합할 때마다 나머지 PR은 브랜치 갱신과 재검사(약 3~4분)가 필요했다.
