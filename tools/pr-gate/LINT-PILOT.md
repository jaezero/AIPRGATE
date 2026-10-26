# Actual Android Lint pilot

## Scope and provenance

The fixtures are **manually constructed defect injection and a normal
counterexample**, not evidence of an AI spontaneously generating insecure code.
They stay outside normal app sources. Neither fixture opens a connection, uses
credentials, or runs a server. The unsafe fixture has exactly one empty
`checkServerTrusted`; `checkClientTrusted` rejects certificates. The safe fixture
uses the platform-default `TrustManagerFactory`, initialized with the default
trust store, without implementing a custom trust manager.

This is local static-analysis evidence, not proof of a protected PR, a review,
or a merge. It does not establish general TLS security.

## Verified rule

Official Android source, checked 2026-10-25:

- https://android.googlesource.com/platform/tools/base/+/mirror-goog-studio-main/lint/libs/lint-checks/src/main/java/com/android/tools/lint/checks/X509TrustManagerDetector.java

`TRUSTS_ALL` has exact issue ID `TrustAllX509TrustManager`, category SECURITY,
and default severity WARNING. Its source scanner checks implementations of
`javax.net.ssl.X509TrustManager`, inspecting `checkServerTrusted` and
`checkClientTrusted` for effectively empty bodies. The detector explicitly
describes its limited empty-body analysis; this is not a general proof that a
nonempty trust implementation validates certificates correctly.

Only this issue ID is proposed as BLOCK in the parent's quality parser.
Other findings, including `CustomX509TrustManager`, remain WARN. The pilot does
not change Gradle lint severity, suppress findings, create a lint baseline, or
implement that parser. A nonzero tool exit without a validated selected-rule
violation is an execution ERROR, not a successful policy block. Missing reports,
malformed XML, `LintError`, or missing compiled fixture bytecode also fail the
pilot's execution validation.

## Reproduce on this Windows machine

From the project root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\pr-gate\pilot-lint.ps1
```

The script selects `C:\Program Files\Android\Android Studio1\jbr` for its process
and restores its environment afterward. The required SDK is `C:\Android\Sdk`.
Existing Gradle dependencies must be cached: the pilot uses `--offline`.
It runs this exact command once per case:

```text
gradlew.bat ":app:compileDebugKotlin" ":app:lintDebug" "-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=COMPAT" "--no-configuration-cache" "--no-daemon" "--offline" "--console=plain" 2>&1
```

It creates its own GUID-named ASCII sandbox under Windows system temp, falling
back to `C:\Android\AiPrGateLintPilot-<guid>` if temp contains non-ASCII characters.
Only root build settings, Gradle wrapper/configuration, app build settings,
current app sources/resources/tests, and Room schemas are copied into a frozen
baseline snapshot. The SDK locator is generated without copying arbitrary local
properties. Existing project build outputs/caches are not copied. Source hashes
in `baseline-inputs.json` identify the actual baseline used by both cases.

Each case gets a fresh copy of that snapshot, with exactly one fixture injected
as `app/src/main/java/com/example/aiprgate/gatepilot/TrustPolicy.kt`.
Explicit `compileDebugKotlin` plus bytecode presence proves both fixtures compile,
rather than relying on source-only lint analysis. The script captures actual
Gradle exit codes, console output, XML/text/HTML reports, fixture copies/hashes,
all issue counts, and non-selected findings. Reports retain original sandbox
paths; source hashes and copied fixtures preserve provenance after cleanup.

Generated evidence is saved in a unique run directory under
`docs/results/pr-gate-20261025/lint-pilot/`. No evidence is overwritten on rerun.
The script removes only its own sandbox in `finally`; `-KeepSandbox` retains it
for inspection. It never deletes the original app or Gradle/SDK caches.
Script exit 0 means both expected pilot observations were obtained, not that
the unsafe case should pass policy. Script exit 2 means execution validation or
expected detection failed. Per-case `run.json` preserves the actual tool exits.

## Observed execution

Executed 2026-10-25, 18:38:25-18:39:35 +09:00 with Windows JDK
`C:\Program Files\Android\Android Studio1\jbr`. Both cases ran exactly once;
both logs show `compileDebugKotlin`, `lintAnalyzeDebug`, and `lintDebug`,
`28 actionable tasks: 28 executed`, and `BUILD SUCCESSFUL in 34s`.
Both injected `TrustPolicy.class` files existed after compilation.
The XML reports identify the analyzer as `lint 9.4.1`.

Evidence run directory, relative to the project root:

```text
docs/results/pr-gate-20261025/lint-pilot/20261025-183824-2ffa5fe647724ef7b7dec7505358030e/
```

| Case | Actual Gradle exit | Selected-rule findings | Other warnings | Total errors / warnings | Parent policy interpretation |
|---|---:|---:|---:|---|---|
| unsafe | 0 | 1 | 10 | 0 / 11 | BLOCK for the validated selected finding |
| safe | 0 | 0 | 9 | 0 / 9 | WARN only; no selected-rule block |

The unsafe selected finding is `TrustAllX509TrustManager` at injected
`app/src/main/java/com/example/aiprgate/gatepilot/TrustPolicy.kt:10`
(`checkServerTrusted`). Its additional `CustomX509TrustManager` warning is at
line 9 and remains WARN. Gradle exit 0 for unsafe is expected: the selected rule
is an Android Lint warning, and policy blocking is the parent's responsibility.
The pilot script itself exited 0 because the risky/normal expectations and
execution validations both passed.

Shared baseline findings in both reports are:

| Rule | Count | Original project location |
|---|---:|---|
| `RedundantLabel` | 1 | `app/src/main/AndroidManifest.xml:18` |
| `GradleDependency` | 1 | `gradle/libs.versions.toml:3`, core-ktx version notice |
| `UnusedResources` | 7 | `app/src/main/res/values/colors.xml:3-9` |

These nine findings were left unchanged. This offline run's dependency notices
are not an exhaustive current dependency audit; the historical app run recorded
15 warnings under different execution conditions. No standalone baseline-only
lint invocation was added: the shared baseline is the unchanged snapshot and
the identical non-fixture findings in the risky/normal pair.
Both build logs also retain the existing experimental
`android.overridePathCheck=true` configuration warning.

Exact preserved report paths under the evidence run directory:

```text
unsafe/lint-results-debug.xml
unsafe/lint-results-debug.txt
unsafe/lint-results-debug.html
unsafe/gradle.log
unsafe/run.json
unsafe/TrustPolicy.kt.fixture
safe/lint-results-debug.xml
safe/lint-results-debug.txt
safe/lint-results-debug.html
safe/gradle.log
safe/run.json
safe/TrustPolicy.kt.fixture
baseline-inputs.json
summary.json
```

The actual case working directories were
`C:\Android\AiPrGateLintPilot-5a198b1e292945e3b3ad8a0927bfe495\unsafe`
and its sibling `safe`; the exact shared Gradle command is given above and in
each `run.json`. Windows temp contained non-ASCII characters, so the fallback
was used. Cleanup completed and that owned sandbox no longer exists.
The original app, build configuration, policy JSON, and workflows were not
edited. No Git commands or external writes were performed.
