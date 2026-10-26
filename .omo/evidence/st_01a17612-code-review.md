# Code Review — st_01a17612

## Decision

- **codeQualityStatus:** CLEAR
- **recommendation:** APPROVE
- **blockers:** None in the reviewed source.

## Scope and evidence inspected

Read-only review of:

- `app/src/main/java/com/example/aiprgate/management/ManagementViewModel.kt`
- `app/src/main/java/com/example/aiprgate/management/ManagementState.kt`
- `app/src/main/java/com/example/aiprgate/management/ManagementScreen.kt`
- `app/src/test/java/com/example/aiprgate/management/ManagementViewModelTest.kt`
- Necessary contracts/implementations in `github/GitHubApi.kt`, `github/GitHubModels.kt`, `auth/GitHubDeviceAuth.kt`, and `auth/SessionStore.kt`
- Prior build evidence: `docs/results/management-app/implementation-7/run.json` and `docs/results/management-app/implementation-7/gradle.log`

The checkout has no `.git` directory, so a historical/full Git diff could not be independently reconstructed. The decision is based on the current named sources and the preserved implementation-7 evidence. Per task constraints, I did not run builds, device tests, external authentication, review, or merge writes.

## Findings by severity

### CRITICAL

None.

### HIGH

None.

### MEDIUM

None.

### LOW

None requiring changes. The absence of Git metadata limits change-provenance verification, but does not reveal a source defect in the bounded review.

## Correctness review

### Real GitHub authentication and serialized cancellation/logout

The app uses real token verification through `api.viewer`, persists only after verification, and rolls storage back on cancellation (`ManagementViewModel.kt:292-310`). Authentication operations are serialized by retaining the previous job, incrementing an auth generation, and joining the prior job before starting the next operation (`ManagementViewModel.kt:348-365`). Late completion can no longer clear a newer login because state cleanup is generation-guarded (`ManagementViewModel.kt:361-364`).

Logout cancels prior authentication, waits for it under `NonCancellable`, then clears storage before allowing a later authentication job to proceed (`ManagementViewModel.kt:376-403`). The regression tests exercise both late canceled authentication and logout ordering (`ManagementViewModelTest.kt:339-357`, `ManagementViewModelTest.kt:383-402`).

The real device-flow implementation posts to GitHub's device and token endpoints and validates bearer responses (`auth/GitHubDeviceAuth.kt:13-65`); PAT login is validated against GitHub before session persistence.

### Immutable confirmation target and fresh preflight

`PullTarget` is an immutable data class containing repository, PR number, head SHA, base SHA, and base branch (`ManagementState.kt:9-18`). Both review and merge dialogs retain the exact confirmed target, rather than re-reading mutable screen state at confirmation (`ManagementScreen.kt:517-560`, `ManagementScreen.kt:574-604`). The merge dialog also displays the captured target branch (`ManagementScreen.kt:600`).

Before any write, `writeAction` fetches a fresh detail snapshot and requires its complete target to equal the confirmed target (`ManagementViewModel.kt:436-459`). The same-SHA base-branch retarget regression is specifically covered (`ManagementViewModelTest.kt:360-371`).

### Latest checks/reviews and guarded writes

Detail loading fetches reviews, commit statuses, head checks, and merge-commit checks, then re-fetches the PR and rejects a changed target or update timestamp (`ManagementViewModel.kt:199-221`). Policy code selects the latest check by ID, requires the trusted GitHub Actions app and one workflow run for all required checks, and derives approvals from each reviewer's latest decisive review on the current head SHA (`ManagementState.kt:74-107`). Merge blockers combine those checks with fresh repository permissions, mergeability, unresolved change requests, and latest approval requirements (`ManagementState.kt:110-125`).

Review submission sends GitHub's explicit `commit_id`; merge sends GitHub's expected `sha` (`github/GitHubApi.kt:164-180`). The ViewModel passes the captured target SHA to both writes (`ManagementViewModel.kt:420`, `ManagementViewModel.kt:430`). Tests verify explicit review payload preservation, fresh merge-check rejection, and expected-head merge behavior (`ManagementViewModelTest.kt:227-242`, `ManagementViewModelTest.kt:254-278`).

## Test relevance and maintainability perspectives

The requested `remove-ai-slops` and `programming` skill files were not available in the registered skill set, so I applied the criteria supplied in the task directly.

- **remove-ai-slops perspective:** No violation found. The new regressions are behavioral concurrency/target-integrity tests, not deletion-only tests, tautologies, or tests that merely mirror constants. Production code does not add unrelated extraction, parsing, or normalization.
- **programming perspective:** No violation found. Tests assert observable state and API-write effects rather than prompt/copy wording. No untyped escape hatch, needless abstraction, or out-of-scope boundary parsing was introduced. `PullTarget` and the auth-generation/job sequencing are proportionate to the demonstrated races.

## Verification evidence assessment

`docs/results/management-app/implementation-7/run.json` records exit code 0 for assemble, JVM unit tests, lint, and Android-test APK assembly. Its `gradle.log` ends with `BUILD SUCCESSFUL`; it proves the implementation-7 build/test/lint command completed, but it does not independently prove the claimed device17 execution count. Implementation8 was described as changing only two Korean copy strings and still building; no completed implementation8 artifact was present, so that build remains unverified here.

## Residual unverified scope

- Actual authenticated device-flow/PAT use against GitHub was not executed in this review.
- Actual two-account review, approval, branch-protection enforcement, and merge were not executed and remain the explicitly outstanding acceptance scope.
- No external GitHub write was authorized or attempted.
- Implementation8 build completion was not available as evidence at review time.
