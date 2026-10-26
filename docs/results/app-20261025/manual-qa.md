# 수동 앱 QA — 2026-10-25

환경: Windows SDK ADB, Pixel_9_ASCII, Android 15/API 35.
앱 코드는 APK 빌드 이후 변경하지 않았다.
이 문서는 조작 결과 기록이며 자동 테스트 실행 로그와 구분한다.

| 확인 | 실제 결과 | 증거 |
|---|---|---|
| 빈 제목 제출 | 한국어 오류 표시, 등록 없음 | `error-light.png`, `error-dark.png` |
| 앞뒤 공백 입력 | `  Persist Task  ` → 목록 `Persist Task` | `reopened-light.png` |
| 완료·취소 | checked false → true → false → true | ADB UI 트리 확인, `completed.png` |
| 선택 삭제 | `Delete Me` 삭제 후 다른 항목 유지 | 재시작 후 UI 트리 확인 |
| 프로세스 재시작 | 남긴 제목·완료 및 삭제 상태 유지 | `reopened-light.png`, `reopened-dark.png` |
| 긴 제목·가로 스크롤 | 줄바꿈, 마지막 항목의 삭제 접근 가능 | `long-dark.png`, `landscape-end-*.png` |
| 고정 IME 세로 | 입력·추가 노출, 추가 버튼·화면 완료 키 등록 성공 | `ime-docked-portrait-*.png` |
| 고정 IME 가로 | 입력 포커스 및 스크롤 후 추가 접근, 추가·화면 완료 키 등록 성공 | `ime-docked-landscape*.png`, `ime-landscape-submissions.png` |

재시작 명령:

```text
adb shell am force-stop com.example.aiprgate
adb shell am start -W -n com.example.aiprgate/.MainActivity
adb shell uiautomator dump /sdcard/aiprgate-window.xml
```

`am start -W`는 Status: ok, LaunchState: COLD로 완료됐다.
UI 트리의 항목별 content-desc와 checked 값으로 동작을 확인했다.
UI 트리는 다음 캡처에서 갱신되므로 `window.xml`은 최종 관측 스냅샷이다.

초기 Gboard 도구막대와 충돌한 좌표 입력, 첫 고정 IME 전환 실패,
목록 끝으로 스크롤하기 전에 보이지 않았던 추가 항목은 성공으로 세지 않는다.
실제 화면과 UI 트리를 확인한 뒤 올바른 설정·스크롤 위치에서 재검증했다.
고정 IME 검증은 앱 코드를 바꾸지 않고 Gboard의 물리 키보드 옵션으로 해결했다.

공식 키보드가 아닌 모든 플로팅 창의 가림 방지, 모든 기종·폰트 크기·TalkBack,
API 24/37까지 확인했다는 뜻은 아니다.

검증용으로 추가한 6개 항목은 화면의 개별 삭제 버튼으로 정리했다.
재실행 후 빈 목록을 확인했으며 앱 데이터 전체 삭제나 DB 파일 제거는 하지 않았다.
기기 방향·스타일러스·Gboard 옵션은 처음 관측한 값으로 복원했다.
