# PR 관리 앱 검증

초기 실행은 UTC 2026-10-25이며 최종 빌드·기기 검증은 PC 현지 2026-10-26이다.
현재 작업은 로컬 앱 구현과 검증이며 원격 코드·보호 설정·리뷰·병합을 변경하지 않았다.

## 실행 결과

| 검사 | 관측 결과 | 증거 |
|---|---|---|
| 최종 소스 통합 빌드 | exit 0, assembleDebug·단위 테스트·Lint·계측 APK 생성 | `implementation-8/run.json`, `gradle.log` |
| 단위 테스트 | 72건, 실패·오류·생략 0. 원래 앱 12건 포함 | JUnit XML, 최종 snapshot |
| Lint | 오류 0, 경고 16. 기존 리소스·버전 안내와 고정 JVM JSON 테스트 의존성의 버전 안내 | 최종 `lint-results-debug.txt` |
| 휴대폰 계측 | 17건, 실패·오류·생략 0. 실제 Keystore 6건·관리 UI 6건·기존 앱 5건 | `device-phone-final-8/`, 기기 JUnit XML |
| 넓은 화면 계측 | 1920×1200/density 240, 같은 17건 통과·실패/오류/생략 0 | `device-tablet-final-8/` |
| 실제 Gitleaks | 증거 메타데이터 형식 수정 후 PASS, 발견 0, 1,546ms | `secrets-final-7-formatfix/secrets.json` |
| LSP | Kotlin 언어 서버 미설치로 실행 불가 | 실제 Gradle 컴파일·Lint·JUnit으로 검증 |

최종 빌드는 현지 11:38:00, 휴대폰 검증은 11:43:20, 넓은 화면 검증은 11:45:38에
각각 exit 0을 기록했다. 일부 Windows/WSL 실행 연결은 실제 완료 후에도 남아
monitor 시간 제한에 걸렸다. 시험 자체의 종료 코드는 `run.json`과 JUnit으로 확인했다.
원래 CI 엔진 103건 통과 기록은 이전 단계의 결과이며 이번 앱 최종 시험으로 재실행한 것은 아니다.

## 직접 확인한 실제 앱 동작

아래 전체 읽기 전용 동작은 `/mnt/c/Android/AiPrGateManagementQa/live-final/`에 보존했다.
최종 APK의 추가 공개 조회는 `/mnt/c/Android/AiPrGateManagementQa/final-8/live-final/`에
별도로 보존한다. 두 디렉터리를 같은 실행으로 취급하지 않는다.

| 항목 | 실제 조작·결과 | 증거 파일 |
|---|---|---|
| 앱 시작·공개 조회 | MainActivity에서 jaezero/AIPRGATE PR #1 조회, 작성자·제목·검사 5개·실패 4개·승인 0명 표시 | `list.png`, `list-dark.png` |
| PR 상세 | 실제 본문·AI 사용 기록·브랜치·head SHA 조회 | `detail-state.png` |
| 변경 코드 | 실제 변경 60개 중 첫 30개와 patch 표시 | `files.png`, `files.xml` |
| 자동 검사 | app-check 통과와 pr-info 등 실패를 그대로 표시. 새 정책 이름 누락을 통과로 바꾸지 않음 | `checks.png`, `checks.xml` |
| 리뷰·병합 제한 | 실제 리뷰 0명, 비인증 상태의 승인 비활성화와 병합 불가 사유 표시 | `reviews.png`, `reviews.xml` |
| 샘플 보존 | 목록에서 SampleActivity 열림. 기존 `Android Lint 실행` 항목이 유지됨. 시스템 다크 모드 대응 | `sample-open-light.png`, `sample-dark.png`, `sample-open.xml` |

첫 `list.xml`과 `summary.xml`은 화면 조회 중 시점을 담는다.
같은 조작의 후속 PNG 또는 `detail-state.png`는 조회 완료 상태다.
서로 다른 순간의 XML과 PNG를 같은 원자적 스냅샷이라고 하지 않는다.
일부 초기 버튼 좌표는 목록 요약 갱신으로 이동하여 클릭되지 않았다.
접근성 트리를 다시 읽어 실제 샘플 진입을 확인했다.

## 합성 UI와 외부 쓰기 테스트의 구분

회귀 테스트는 fake GitHubService/전송 계층으로 SHA·review event·body·merge method·
오류·취소·중복 제출·자기 승인·변경 중 조회를 확인한다.
실제 계정의 리뷰·승인·병합을 실행한 기록이 아니다.
Android Keystore 암호화·새 인스턴스 복원·랜덤 IV·로그아웃·변조 거부는 실제 기기에서 검증한다.

화면 harness는 일반 앱의 모드가 아니라 androidTest 소스다.
15개 상태를 두 테마에서 캡처하고 스크롤 끝·승인/병합 확인창을 추가하여
기기 코드 값·브라우저 열기·취소 위치까지 포함하여 휴대폰 50장, 넓은 화면 50장의
행렬을 구성한다. `capture-manifest-final-8.json`에 100개 입력 PNG의 원래 크기를 기록했다.
92장은 전체 화면이고 8장은 의도적으로 확인창 노드만 캡처한 이미지다.
상태: 목록, 빈 목록, 오류, 로딩, 연결, 인증된 연결, 기기 코드,
요약, 파일, 검사, 실패 검사, 리뷰, 공개 리뷰, 제출 중, 자기 PR 리뷰.

최종 screenshot은 Compose/PixelCopy의 렌더 완료 신호를 사용한다.
고정 sleep으로 성공을 맞추지 않는다. 기기 window/transition/animator animation scale은
검증 중 0으로 설정하며 종료 때 원래 설정으로 복원한다.
따라서 캡처는 정지·감소된 모션 증거이며 일반 애니메이션 타이밍을 증명하지 않는다.
200% 글꼴 크기와 하드웨어 키보드 조작도 이번 정지 캡처로 검증했다고 하지 않는다.
초기 `device-1`, `device-phone-final`의 UiAutomation 전환 중/이전 프레임은 최종 판정에서 제외한다.

## 발견·수정한 실제 결함

익명 GET repository 응답에는 `permissions`, `allow_merge_commit`, `allow_squash_merge`,
`allow_rebase_merge`가 없었다. 이를 필수 필드로 읽던 코드 때문에 목록 전체가 오류였다.
`public-response-before-fix`는 해당 회귀 테스트의 실제 exit 1이다.
누락된 관리 권한은 허용하지 않되 공개 조회는 가능하게 고쳤다.
수정 후 전체 테스트가 통과했고 실제 PR #1도 표시됐다.

취소된 인증의 늦은 응답이 후속 로그인을 지우는 경합을
`auth-race-before-fix`에서 실패로 재현했다. 인증 세대와 작업 순서, 취소된 저장의 복구,
로그아웃 정리를 수정했다. 고정 대기 없이 deferred 이벤트로 회귀를 검증한다.
같은 base SHA를 가진 다른 브랜치로 PR이 변경되는 경우도 저장소·번호·head/base SHA·
base 브랜치의 불변 대상으로 비교하고 이전 확인창을 닫는다.

초기 source fingerprint의 파일명-keyed JSON은 소스 해시 10개를 Gitleaks의
generic key로 오탐했다(`secrets-final-7/secrets.json`). 해시 값·원래 실행 시각·시험 결과는
보존하고 수집 형식을 `{path, sha256}` 배열로 바꿨다. 정책·제외 규칙·스캐너는 약화하지 않았다.
후속 실제 스캔은 발견 0이다. CLI fixture의 예제 저장소와 SHA는 로컬 검사 문맥이며
실제 원격 PR 실행의 증거가 아니다.

독립 코드 검토는 차단 사항 없이 APPROVE다
(`../../../.omo/evidence/st_01a17612-code-review.md`).
검토자는 최종 소스를 읽었으나 당시 실행 증거는 implementation-7까지 읽었다.
위 implementation-8의 실제 성공 기록은 부모가 별도로 확인했다.

최종 100장의 독립 시각 검토는 PASS/APPROVE다
(`../../../.omo/evidence/st_01a1761f-gate-review.md`). 첫 판정의 두 차단 사항
(빈 `files-end`, 늘어난 검사 배지)은 묶음 이미지 읽기의 잘못된 대응이었다.
해당 12장을 한 장씩 다시 열어 정상 화면임을 확인한 뒤 철회했다.
200% 글꼴·하드웨어 키보드·일반 애니메이션 타이밍은 정지 캡처로 판정하지 않았다.

## 1인 운영 모드 추가 검증 (2026-10-26)

| 검사 | 관측 결과 | 증거 |
|---|---|---|
| 빌드·JVM·Lint | exit 0, JVM 75건 통과(자기 검토 3건 추가) | `implementation-9-solo/` |
| 휴대폰 계측 | exit 0, 18건 통과(자기 검토 확인창 1건 추가) | `device-phone-solo-9/` |
| 검사 도구 | bun 104건 통과, 1인 정책 `--solo` 오프라인 감사 configured | `tools/pr-gate/workflow.test.js` |
| 화면 | 자기 검토 버튼·확인창을 밝은/어두운 테마로 직접 확인 | `/mnt/c/Android/AiPrGateManagementQa/solo-9/phone/` |

넓은 화면 계측은 이번 변경 후 다시 실행하지 않았다.
실제 계정의 자기 검토 제출·병합과 원격 보호 설정 적용은 실행하지 않았다.

## 남은 실제 인수

- 배포용 GitHub App Client ID/device flow 설정 또는 본인이 앱 안에 입력할 개인 토큰.
- 실제 사용자 인증과 만료·권한 시나리오의 계정 인수.
- 작성자 외 사람이 앱에서 리뷰·수정 요청·승인을 제출하고 새 push 뒤 재승인을 확인.
- 최신 검사·보호 조건을 충족한 실제 병합.
- 원격의 이전 워크플로우와 로컬의 새 검사 이름/정책을 검토한 뒤 게시·보호 설정 적용.

자동화 코드나 가짜 승인 상태로 위 항목을 완료했다고 표시하지 않는다.
실제 계정 설정 변경·커밋·푸시·리뷰·병합은 도구로 실행하지 않았다.
