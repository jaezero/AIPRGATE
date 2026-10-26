# 개발 환경 점검 — 2026-09-21

> 최신 앱 구현·검증은 2026-10-25 B단계이며 [개발 진행 기록](development-progress.md)을 참고한다.
> 아래 2026-09-21 환경표와 실행 결과는 당시 관측을 보존한 것이다.

## 2026-10-25 B단계 재확인

- Windows JDK `C:\Program Files\Android\Android Studio1\jbr`로 실제 빌드·검사를 실행했다.
  Gradle 9.6.0, Launcher 25.0.3, Java 25 daemon 조건을 확인했다.
- 기존 Gradle·AGP·Kotlin·SDK 설정을 유지했다.
  Room 2.8.5/KSP 2.3.12/lifecycle 2.9.4/coroutines-test 1.10.2를 추가한 뒤
  KSP 코드 생성·debug/계측 APK 빌드와 테스트 통과로 호환성을 확인했다.
- 기본 옵션에서는 테스트 클래스 3개의 ClassNotFoundException이 관측됐다.
  과거의 GradleWorkerMain 오류와 메시지가 완전히 같다고 기록하지 않는다.
  COMPAT + `--no-configuration-cache`에서 한국어 문자열을 다루는 새 테스트를 포함해
  단위 12건을 실행했고 실패·생략은 없었다. 기본 옵션 문제가 사라진 것은 아니다.
- `scripts/verify-local.ps1 -Mode compat`는 이 PC의 JDK를 프로세스 환경에만 지정하고
  전체 빌드·단위 테스트·Lint·계측 APK를 실행한다.
  `-Mode baseline`은 기본 옵션 오류 재현, `-Mode device`는 기기 테스트다.
  시스템 JAVA_HOME이나 전역 인코딩은 변경하지 않았다.
- SDK의 설치된 Android 35 이미지를 사용하는 `Pixel_9_ASCII`에서 계측 5건을 통과했다.
  `Pixel_9_GA36`은 선언된 이미지 경로가 없어 실패했고 기존 AVD 파일을 수정하지 않았다.
- Kotlin LSP가 설치되어 있지 않아 LSP 진단은 실행할 수 없었다.
  Kotlin/KSP 컴파일, 테스트, Android Lint 결과를 근거로 사용했다.
- 재현 로그·종료 코드·JUnit XML·Lint 보고서·APK 및 소스 해시는
  `docs/results/app-20261025/`에 보존했다.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-local.ps1 -Mode compat
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-local.ps1 -Mode device
```

두 번째 명령은 Device Manager에서 에뮬레이터를 부팅하거나 실물 기기를 연결한 뒤 실행한다.
일반 Android Studio 앱 Run은 기존 `app` 구성으로 진행하며, IDE GUI 자체의 Sync·디버깅은
이번 에이전트가 직접 확인하지 않았다.

---

상태: 기존 프로젝트의 Windows debug APK 빌드 확인. 기본 JVM 테스트 실행에는 로컬 인코딩 우회가 필요하다. IDE 실행·디버깅, 기기 테스트, CI는 이 점검으로 완료 처리하지 않는다.

## 1. 실제 확인한 환경

| 항목 | 값·근거 |
|---|---|
| 프로젝트 | `C:\Users\박재영\Desktop\기업 모둠학습\AiPrGate` |
| OS | Gradle 출력: Windows 11 10.0 amd64 |
| 설치된 Android Studio | `Android Studio1/product-info.json`: `AI-261.26222.65.2614.16204760`, dataDirectory `AndroidStudio2026.1.4`. 현재 열린 IDE 프로세스 버전은 별도 미확인 |
| 실제 사용한 JDK | `C:\Program Files\Android\Android Studio1\jbr`, JetBrains 25.0.3, runtime `25.0.3+-15898627-b508.16` |
| 기존 JAVA_HOME | `C:\Program Files\Android\Android Studio\jbr`. 해당 `bin/java.exe` 없음. 이번 명령의 프로세스 환경에서만 올바른 경로로 지정 |
| Gradle Wrapper | 9.6.0, 배포 ZIP SHA-256 지정됨 |
| Gradle daemon 조건 | `gradle/gradle-daemon-jvm.properties`에서 Java 25, vendor 제한 없음 |
| AGP | 9.4.1 |
| Kotlin Compose 플러그인 | version catalog의 2.2.10. `gradlew --version`의 Kotlin 2.3.21은 Gradle 내장 Kotlin이므로 앱 컴파일러 버전과 혼동하지 않음 |
| 앱 Kotlin 구성 | AGP built-in Kotlin 사용 task 확인. 별도 `org.jetbrains.kotlin.android` 플러그인 없음. 정확한 내장 컴파일러 해석 버전은 후속 dependency 조사 대상 |
| Java 소스·타깃 수준 | `JavaVersion.VERSION_11`. Gradle 실행 JDK 25와 역할이 다름 |
| SDK 위치 | `local.properties`: `C:\Android\Sdk` |
| compile / target / min SDK | 37 / 37 / 24 |
| 설치된 플랫폼 | `platforms/android-37.0`, SDK Platform 17, revision 2, API 37.0 |
| 설치된 Build Tools | 34.0.0, 35.0.0, 36.0.0, 36.1.0, 37.0.0. 앱에서 명시 버전 미지정; 실제 선택 버전 별도 미확인 |
| Compose BOM | 2026.02.01 |
| 주요 선언 의존성 | activity-compose 1.13.0, core-ktx 1.10.1, lifecycle-runtime-ktx 2.6.1, JUnit 4.13.2 |
| Room / ViewModel | Room 미추가, 앱 ViewModel·Repository·DAO 미구현. 이번 단계에 추가하지 않음 |
| 예시 AVD 설정 | `C:\Android\avd\Medium_Phone.ini`: android-37.1, x86_64, google_apis_playstore_ps16k. 설정 존재만 확인, 부팅·앱 실행 미검증 |
| GPU | nvidia-smi: NVIDIA GeForce RTX 3050, 8192 MiB, driver 560.94 |
| Git / CI | `git rev-parse` 실패: 현재 프로젝트는 Git 저장소 아님. GitHub remote·보호 규칙·Linux CI 없음/미확인 |

전체 라이브러리의 해석 버전 및 Linux 환경 호환성까지 검증한 표가 아니다. 현재 설치 환경에서 APK 생성에 사용한 조합을 기록한다.

## 2. 재현 명령

Android Studio Terminal의 PowerShell에서 프로젝트 루트로 이동한 후 실행한다. JAVA_HOME 지정은 현재 터미널에만 적용되며 시스템 환경 변수를 바꾸지 않는다.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio1\jbr'
.\gradlew.bat --version
.\gradlew.bat :app:assembleDebug --console=plain
```

현재 한글 경로에서 예제 JVM 테스트를 확인한 진단 명령:

```powershell
.\gradlew.bat :app:testDebugUnitTest '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=COMPAT' --no-configuration-cache --console=plain
```

`COMPAT`는 이번 PC의 native encoding을 따르는 로컬 진단 우회다. 전역 기본값이나 Linux CI 표준으로 채택하지 않았다. 앞으로 UTF-8 파일을 읽는 테스트·Room 테스트에서도 검증 없이 안전하다고 가정하지 않는다.

## 3. 실제 결과와 한계

| 실행 | 실제 결과 |
|---|---|
| `--version` | Gradle 9.6.0, Launcher JDK 25.0.3 확인 |
| `assembleDebug testDebugUnitTest --rerun-tasks` | APK 생성 완료. 테스트 worker 시작 실패로 전체 exit 1, BUILD FAILED in 10s |
| 기본 테스트 `--info` 진단 | `GradleWorkerMain` ClassNotFoundException 재현, exit 1 |
| COMPAT 테스트 진단 | exit 0, BUILD SUCCESSFUL in 12s. XML: 테스트 1건, 실패 0, 오류 0, skipped 0 |

통과한 테스트는 템플릿의 `addition_isCorrect`(2+2=4)뿐이다. 앱 기능 테스트·Room 검증·커버리지 달성을 뜻하지 않는다. 기존 instrumented 예제는 실행하지 않았다.

원자료: [실행 로그와 결과 폴더](results/environment-20260921/). `run.json`의 exit 1은 첫 실행의 사실이며 이후 성공으로 덮어쓰지 않았다. 성공 XML은 별도 보존했다.

APK: `app/build/outputs/apk/debug/app-debug.apk`  
이번 생성 파일 SHA-256: `31141756FD465F6B3744304060A5971495CD38F4319FB7B2CE8A0DF7908BAAD1`

## 4. 확인된 환경 문제

1. 기존 JAVA_HOME이 존재하지 않는 JDK 경로를 가리킨다. 현재 터미널은 위 명령으로 지정하고 IDE의 Gradle JDK도 실제 설치 경로인지 확인해야 한다.
2. 이전 단계에서 추가한 `android.overridePathCheck=true`는 AGP의 한글 경로 사전 검사만 우회한다. 경고는 숨기지 않았다.
3. 이번 PC의 `native.encoding=MS949`, JVM `file.encoding=UTF-8`, Gradle worker args 파일의 UTF-8 한글 경로를 확인했다. 기본 실행 실패와 COMPAT 실행 성공의 차이는 경로·인코딩 관련 원인이라는 강한 근거다. Gradle의 관련 공개 이슈와 유사하지만 동일 버그의 확정 진단이라고 단정하지 않는다.
4. 장기적으로 앱 경로와 Gradle 사용자 캐시를 모두 ASCII 경로로 사용하는 방안을 권장한다. 앱 폴더만 바꿔도 한글 사용자 캐시 경로가 남을 수 있다. 이번에는 파일 이동·캐시 삭제·전역 인코딩 변경·IDE 설정 변경을 하지 않았다.

참고: [Android JDK 선택](https://developer.android.com/build/jdks), [Gradle 비ASCII 경로 테스트 이슈](https://github.com/gradle/gradle/issues/30391), [Java 25 실행 명령·인자 파일](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html). 공식 자료 확인일 2026-09-21. 현재 PC 결과는 외부 자료 대신 보존한 로그를 우선한다.

### 한글 경로의 Gradle 증분 빌드 (2026-10-27 관찰)

`git merge`로 소스가 바뀐 뒤 `assembleDebug`가 `compileDebugKotlin UP-TO-DATE`로 끝나
이전 코드의 APK가 만들어졌다. APK의 dex에 새 코드가 없는 것으로 확인했다.
`--no-configuration-cache --no-watch-fs --rerun-tasks`로 다시 빌드해 해결했다.
설치 전에 새 코드가 들어갔는지 확인한다. GitHub Actions(Linux, ASCII 경로)에서는 관찰되지 않았다.

## 5. Android Studio에서 확인할 순서

1. 기존 `AiPrGate` 프로젝트를 연다.
2. Gradle JDK가 실제 설치된 `Android Studio1\jbr` 또는 해당 Java 25 실행 환경인지 확인한다.
3. Gradle Sync를 실행하고 경로 우회 경고와 실제 실패를 구분한다.
4. 에뮬레이터를 선택해 Run을 실행한다. `Hello Android!` 표시 여부를 확인한다.
5. 기본 예제 테스트 실행이 실패하면 위 진단 명령의 결과와 비교한다. IDE에서 기본 실행도 통과했다고 기록하지 않는다.
6. 이후 로컬 경로/인코딩 정리와 Git 기준 커밋 확보 후 기능 개발로 진행한다.
