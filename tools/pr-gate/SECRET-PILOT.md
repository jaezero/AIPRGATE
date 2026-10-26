# Independent secret-scan pilot

Verified locally on 2026-10-25, Linux x64, Bun 1.4.3. This is scanner
evidence, not evidence of a GitHub merge block or human approval.

## Reproduce

From the project root, with Bun installed:

```sh
bun test ./tools/pr-gate/secrets.test.js
bun ./tools/pr-gate/bootstrap-gitleaks.mjs
```

This workstation has no standalone `bun` command. The single test invocation
used the supplied runtime's Bun CLI mode:

```sh
BUN_BE_BUN=1 /home/jaeyeong/.omo/binary-runtime/5.1.40/omo test ./tools/pr-gate/secrets.test.js
```

Result: exit **0**, **18 passed**, **0 failed**, 36 assertions, 8.34 seconds.
No test rerun was needed. Binary:
`/tmp/aiprgate-tools-1000/gitleaks-8.30.1/gitleaks`.
The official Linux x64 archive SHA-256 was verified before archive parsing:
`551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb`.
Cache hits are also verified; the binary is rebuilt from verified archive bytes.
The bootstrap CLI printed only the binary path.

## Observations

- A generated invalid Kotlin literal produced exit **1** and exactly
  `{ruleId:"generic-api-key", file:"Credential.kt", line:1}`.
  It was still detected with an inline `gitleaks:allow` comment and PR-local
  `.gitleaks.toml` / `.gitleaksignore` suppression attempts.
- The similar `System.getenv("SERVICE_API_KEY")` reference produced exit **0**
  and no findings. No credential value or source line is retained here.
- Generated-directory fixtures were excluded; `app/src/Credential.kt` remained
  detectable. The added path exclusions match only complete directory segments
  `.git`, `.gradle`, `.idea`, `.kotlin`, and `build`, at any depth. These directory
  names are assumed to contain generated metadata, caches, or outputs; do not
  put authored source there. There is no blanket source/test/docs exclusion.
- Actual invalid trusted TOML failed execution. Subprocess boundary tests also
  covered malformed/missing reports, inconsistent exits, unsupported critical
  flags, invalid metadata, path escapes, missing binaries, timeout termination,
  nonempty trusted ignores, metadata-only projection, and temporary cleanup.

## Contract and limits

`scanSecrets({root, executable, config, ignorePath, timeoutMs})` returns only
`{exitCode, findings:[{ruleId,file,line}], elapsedMs, toolVersion}`. Paths are
root-relative. Exit 1 plus a valid nonempty report means detection; execution,
timeout, report, and exit/report consistency errors throw.

The caller must supply the executable/config/ignore from a trusted gate
checkout, not the PR. Defaults are this module's sibling policy files. The
ignore must be empty; trusted inputs are copied to a private run directory.
The scan uses `dir`, explicit config/ignore paths, `--ignore-gitleaks-allow`,
and `--redact=100`. Unsupported critical flags fail rather than falling back.
Tool diagnostics are never forwarded; reports live only in OS temp and are
removed in `finally`. No binary or raw report is saved in the project.

Configuration extends the pinned binary's official defaults, including its
inherited rule allowlists. This pilot supports selecting **generic-api-key**
as the parent's block rule; it does not establish other rules' blocking quality.
Directory scanning does not inspect Git history; archives/encoded content use
upstream defaults. Synthetic fixtures are injected tests, not observed AI
output or real credentials. This does not prove all secrets are detectable.
JavaScript LSP diagnostics could not run because the language server is absent;
the actual Bun tests passed. Production, app, workflow, and policy-engine
integration are outside this independent tooling task.

Official references consulted: Gitleaks README via Context7
`/gitleaks/gitleaks` (CLI flags, extending defaults, global allowlists), and
Bun's archive documentation (`Bun.Archive.files()`); no npm dependency was added.
