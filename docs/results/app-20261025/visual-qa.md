# 실제 Android 화면 검토

판정: 제공된 Android 15/API 35 범위에서 PASS.
앱 코드가 마지막으로 변경된 뒤 생성한 실제 기기 캡처를 사용했다.
웹/React 화면, 이미지로 위장한 UI 또는 별도 모형 화면을 검증한 것이 아니다.

## 검토 이력

- 시각 검토: 실제 캡처 10개와 화면·테마 코드를 읽고 PASS. 제품 결함 없음.
- 동작·접근성 검토: 제품 결함은 없지만 고정 IME 증거 부족으로 REVISE.
- 추가 검증: Gboard 화면 키보드 옵션으로 실제 고정 IME를 확보,
  세로·가로에서 추가 버튼과 실제 완료 키 제출·스크롤을 확인.
- 새 독립 검토: 기존 10개와 새 6개, 총 16개 캡처를 직접 확인하고 PASS.
  제품·증거 차단 항목 없음. 이 검토자들은 AI이며 사람이 직접 승인했다고 표시하지 않는다.

## 항목별 증거

| 항목 | 판정 | 증거 |
|---|---|---|
| 빈 목록과 한국어 안내 | PASS | `cold-start.png` |
| 밝은/어두운 테마·완료 체크와 취소선 | PASS | `reopened-light.png`, `reopened-dark.png` |
| 긴 제목 줄바꿈·삭제 버튼 | PASS | `long-dark.png` |
| 입력 오류와 한국어 문구 가독성 | PASS | `error-light.png`, `error-dark.png` |
| 가로 안전 영역·단일 스크롤·마지막 항목 접근 | PASS | `landscape-light.png`, `landscape-end-light.png`, `landscape-end-dark.png` |
| 고정 키보드 세로: 입력과 추가 노출 | PASS | `ime-docked-portrait-light.png`, `ime-docked-portrait-dark.png` |
| 고정 키보드 가로: 입력 포커스 및 추가까지 스크롤 | PASS | `ime-docked-landscape-dark.png`, `ime-docked-landscape-scrolled-light.png`, `ime-docked-landscape-scrolled-dark.png` |
| 고정 키보드에서 버튼·Done 제출 결과 | PASS | `ime-landscape-submissions.png`, 수동 조작 기록 |
| 실제 Room 데이터 경로 | PASS | `TaskApplication`, `RoomTaskRepository`, 실제 Room 계측 테스트 |
| 이전 플로팅 키보드 캡처 | 진단용 | `docked-keyboard-dark.png`는 이름과 달리 플로팅 IME. 고정 IME 통과 근거에서 제외 |

## 한계

독립 검토는 저장된 화면·소스·실행 기록을 읽은 것이며 기기를 다시 조작하거나
테스트를 별도로 재실행한 것이 아니다. 실물 기기, 다른 API·밀도·폰트 배율,
TalkBack 실제 탐색, 한국어 IME 조합 입력, 측정한 색 대비 비율,
모든 플로팅 키보드에 대한 검증은 수행하지 않았다.

Android Studio에서 추가 확인하려면 앱을 실행하고 한국어 키보드로 제목을 입력한 뒤
완료 키·추가 버튼을 각각 사용한다. 시스템 글자 크기를 확대하고 세로·가로에서
입력·오류·체크박스·삭제에 접근 가능한지, TalkBack이 항목별 동작과 완료 상태를
읽는지 확인한다. 이 절차를 안내한 것을 실행 완료로 세지 않는다.
