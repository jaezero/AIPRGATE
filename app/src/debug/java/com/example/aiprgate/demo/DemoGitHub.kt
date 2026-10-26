package com.example.aiprgate.demo

import com.example.aiprgate.auth.DeviceAuth
import com.example.aiprgate.auth.DeviceCode
import com.example.aiprgate.auth.DevicePoll
import com.example.aiprgate.auth.SessionStore
import com.example.aiprgate.github.*
import com.example.aiprgate.management.REQUIRED_CHECKS
import com.example.aiprgate.management.selfReviewBody

// Debug-only sample data for screenshots. Nothing here talks to GitHub:
// reviews and merges only change this in-memory copy.
const val DEMO_USER = "jaezero"
private const val BASE = "https://github.com/jaezero/AIPRGATE"
private const val GATE_APP = 15368L

private fun sha(seed: Int) = java.security.MessageDigest.getInstance("SHA-1")
    .digest("demo-$seed".toByteArray()).joinToString("") { "%02x".format(it) }

private data class DemoPull(
    var pull: PullRequest,
    val files: List<PullFile>,
    val checks: (String) -> List<CheckRun>,
    val reviews: MutableList<PullReview>,
)

private fun checksFor(run: Int, head: String, results: Map<String, Pair<String, String?>>, summaries: Map<String, String>) =
    REQUIRED_CHECKS.mapIndexed { i, name ->
        val (status, conclusion) = results[name] ?: ("completed" to "success")
        CheckRun(run * 10L + i, name, head, status, conclusion,
            "$BASE/actions/runs/$run/job/${run * 10 + i}", summaries[name] ?: "", GATE_APP)
    }

private fun pr(
    number: Int, title: String, body: String, head: String, ref: String, updated: String,
    state: String = "open", merged: Boolean = false, mergeableState: String = "blocked", files: Int = 1,
) = PullRequest(number, title, body, DEMO_USER, state, false, merged, head, sha(1000 + number), ref, "main",
    "$BASE/pull/$number", updated, true, mergeableState, null, files)

private fun body(summary: String, req: String, test: String, ai: Boolean = true) = """
## 변경 요약
$summary

## 관련 요구사항
$req

## AI 사용 여부
- AI 사용: ${if (ai) "예" else "아니오"}

## 생성 기록 위치
${if (ai) "docs/ai-usage/" else "해당 없음"}

## 테스트 근거
$test

## 수동 리뷰 항목
- 입력 검증과 예외 처리
""".trim()

private fun file(name: String, status: String, add: Int, del: Int, patch: String) =
    PullFile(name, status, add, del, patch, "$BASE/blob/main/$name")

class DemoGitHub : GitHubService {
    private val pulls = mutableListOf<DemoPull>()
    private var reviewId = 500L

    init {
        val ok = mapOf("pr-info" to "PR 본문 필수 항목 7개 확인", "build-test" to "assembleDebug 성공 · JUnit 14건 통과",
            "quality" to "Lint 오류 0 · 차단 규칙 위반 없음", "secrets" to "Gitleaks 발견 0", "quality-gate" to "네 검사 모두 통과")
        val h12 = sha(12)
        pulls += DemoPull(pr(12, "할 일 마감일 입력 검증 추가", body(
            "마감일이 오늘 이전이면 저장하지 않고 오류 문구를 표시한다.", "APP-04, TST-02",
            "TaskViewModelTest 마감일 검증 3건 추가, 전체 JUnit 통과"), h12, "feature/due-date-validation",
            "2026-10-26T04:10:00Z", mergeableState = "clean", files = 2), listOf(
            file("app/src/main/java/com/example/aiprgate/TaskViewModel.kt", "modified", 9, 1,
                "@@ -42,7 +42,15 @@ fun addTask(title: String, due: LocalDate?) {\n-    repository.insert(Task(title, due))\n+    if (due != null && due.isBefore(today())) {\n+        error.value = \"마감일은 오늘 이후로 선택하세요.\"\n+        return\n+    }\n+    repository.insert(Task(title, due))"),
            file("app/src/test/java/com/example/aiprgate/TaskViewModelTest.kt", "modified", 18, 0,
                "@@ -80,0 +81,18 @@\n+@Test fun pastDueDateIsRejected() {\n+    model.addTask(\"보고서\", today().minusDays(1))\n+    assertEquals(\"마감일은 오늘 이후로 선택하세요.\", model.error.value)\n+}")),
            { head -> checksFor(9112, head, emptyMap(), ok) }, mutableListOf(
                PullReview(401, DEMO_USER, "COMMENTED", selfReviewBody("마감일 경계값(오늘)도 테스트로 확인했습니다."),
                    h12, "2026-10-26T04:30:00Z", "$BASE/pull/12")))

        pulls += DemoPull(pr(11, "PR 템플릿 항목 보완", "## 변경 요약\nPR 템플릿에 수동 리뷰 항목을 추가했다.\n\n## AI 사용 여부\n",
            sha(11), "docs/pr-template", "2026-10-25T09:20:00Z"), listOf(
            file(".github/pull_request_template.md", "modified", 4, 0,
                "@@ -18,0 +19,4 @@\n+## 수동 리뷰 항목\n+- [ ] 요구사항 적합성\n+- [ ] 민감정보 없음\n+- [ ] 예외 처리")),
            { head -> checksFor(9111, head, mapOf("pr-info" to ("completed" to "failure"),
                "quality-gate" to ("completed" to "failure")), ok + mapOf(
                "pr-info" to "필수 항목 누락: 관련 요구사항, AI 사용 여부(값 없음), 테스트 근거",
                "quality-gate" to "pr-info 실패로 차단")) }, mutableListOf())

        pulls += DemoPull(pr(10, "완료 토글 단위 테스트 추가", body("완료 상태 전환을 검증하는 테스트를 추가했다.",
            "TST-01", "로컬 JUnit 실행"), sha(10), "test/toggle-done", "2026-10-24T07:45:00Z"), listOf(
            file("app/src/test/java/com/example/aiprgate/TaskRepositoryTest.kt", "modified", 12, 0,
                "@@ -55,0 +56,12 @@\n+@Test fun toggleMarksTaskDone() {\n+    repository.toggle(task.id)\n+    assertTrue(repository.find(task.id).done)\n+}")),
            { head -> checksFor(9110, head, mapOf("build-test" to ("completed" to "failure"),
                "quality-gate" to ("completed" to "failure")), ok + mapOf(
                "build-test" to "JUnit 15건 중 1건 실패: TaskRepositoryTest.toggleMarksTaskDone\nexpected:<true> but was:<false>",
                "quality-gate" to "build-test 실패로 차단")) }, mutableListOf(
                PullReview(402, DEMO_USER, "COMMENTED", selfReviewBody("첫 커밋 검토"), sha(9010),
                    "2026-10-24T06:00:00Z", "$BASE/pull/10")))

        pulls += DemoPull(pr(9, "목록 화면 접근성 라벨 정리", body("아이콘 버튼에 설명을 추가하고 하드코딩 문자열을 정리했다.",
            "APP-07, NFR-02", "Android Lint 로컬 실행"), sha(9), "fix/a11y-labels", "2026-10-23T11:05:00Z"), listOf(
            file("app/src/main/res/layout/item_task.xml", "modified", 3, 2,
                "@@ -20,4 +20,5 @@\n-    android:contentDescription=\"@null\"\n+    android:text=\"삭제\"\n+    android:contentDescription=\"@string/delete_task\"")),
            { head -> checksFor(9109, head, mapOf("quality" to ("completed" to "failure"),
                "quality-gate" to ("completed" to "failure")), ok + mapOf(
                "quality" to "차단 규칙 HardcodedText 1건: item_task.xml:21",
                "quality-gate" to "quality 실패로 차단")) }, mutableListOf())

        pulls += DemoPull(pr(8, "GitHub 연동 설정 예시 추가", body("연동 설정 예시 파일을 추가했다.",
            "ENV-03", "해당 없음(문서)"), sha(8), "docs/setup-example", "2026-10-22T05:30:00Z"), listOf(
            file("docs/setup-example.properties", "added", 3, 0,
                "@@ -0,0 +1,3 @@\n+github.repo=jaezero/AIPRGATE\n+github.token=ghp_****************(예시 값 마스킹)\n+github.branch=main")),
            { head -> checksFor(9108, head, mapOf("secrets" to ("completed" to "failure"),
                "quality-gate" to ("completed" to "failure")), ok + mapOf(
                "secrets" to "Gitleaks github-pat 1건: docs/setup-example.properties:2 (예시 값)",
                "quality-gate" to "secrets 실패로 차단")) }, mutableListOf())

        pulls += DemoPull(pr(7, "삭제 확인 대화상자 추가", body("할 일 삭제 전에 확인 대화상자를 표시한다.",
            "APP-05", "UI 테스트 1건 추가"), sha(7), "feature/delete-confirm", "2026-10-26T05:00:00Z"), listOf(
            file("app/src/main/java/com/example/aiprgate/TaskScreen.kt", "modified", 14, 2,
                "@@ -120,2 +120,14 @@\n-IconButton(onClick = { onDelete(task) }) {\n+IconButton(onClick = { pending = task }) {")),
            { head -> checksFor(9107, head, mapOf("build-test" to ("in_progress" to null),
                "quality-gate" to ("queued" to null)), ok) }, mutableListOf())

        pulls += DemoPull(pr(6, "Room 저장소 초기 구성", body("Room 엔티티와 DAO, 저장소를 구성했다.",
            "APP-01~03", "DAO 단위 테스트 통과"), sha(6), "feature/room", "2026-10-21T08:00:00Z",
            state = "closed", merged = true, mergeableState = "unknown"), listOf(
            file("app/src/main/java/com/example/aiprgate/TaskDao.kt", "added", 24, 0, "@@ -0,0 +1,24 @@\n+@Dao\n+interface TaskDao {")),
            { head -> checksFor(9106, head, emptyMap(), ok) }, mutableListOf(
                PullReview(403, DEMO_USER, "COMMENTED", selfReviewBody(""), sha(6), "2026-10-21T07:40:00Z", "$BASE/pull/6")))

        pulls += DemoPull(pr(5, "실험용 브랜치 정리", body("사용하지 않는 실험 코드를 정리하려 했으나 범위를 다시 잡기로 했다.",
            "해당 없음", "해당 없음", ai = false), sha(5), "chore/cleanup", "2026-10-20T03:00:00Z",
            state = "closed", mergeableState = "unknown"), emptyList(),
            { head -> checksFor(9105, head, emptyMap(), ok) }, mutableListOf())
    }

    private fun find(number: Int) = pulls.firstOrNull { it.pull.number == number }
        ?: throw GitHubException(GitHubError.NOT_FOUND)

    override suspend fun viewer(token: String) = GitHubUser(DEMO_USER)
    override suspend fun repository(repo: RepoId, token: String?) =
        RepositoryInfo(repo, token != null, listOf("merge", "squash"))
    override suspend fun pulls(repo: RepoId, token: String?, state: String, page: Int): Page<PullRequest> =
        Page(if (page > 1) emptyList() else pulls.map { it.pull }
            .filter { state == "all" || it.state == state }.sortedByDescending { it.number }, false)
    override suspend fun pull(repo: RepoId, number: Int, token: String?) = find(number).pull
    override suspend fun files(repo: RepoId, number: Int, token: String?, page: Int) =
        Page(if (page > 1) emptyList() else find(number).files, false)
    override suspend fun checks(repo: RepoId, sha: String, token: String?) =
        pulls.firstOrNull { it.pull.headSha == sha }?.let { it.checks(sha) } ?: emptyList()
    override suspend fun statuses(repo: RepoId, sha: String, token: String?) = emptyList<CommitStatus>()
    override suspend fun reviews(repo: RepoId, number: Int, token: String?) = find(number).reviews.toList()

    override suspend fun review(
        repo: RepoId, number: Int, token: String, sha: String, event: ReviewEvent, body: String,
    ): PullReview {
        val target = find(number)
        if (event == ReviewEvent.APPROVE) throw GitHubException(GitHubError.VALIDATION)
        val review = PullReview(++reviewId, DEMO_USER,
            if (event == ReviewEvent.COMMENT) "COMMENTED" else "CHANGES_REQUESTED", body, sha,
            "2026-10-26T06:00:00Z", target.pull.url)
        target.reviews += review
        return review
    }

    override suspend fun merge(repo: RepoId, number: Int, token: String, sha: String, method: String): MergeResult {
        val target = find(number)
        if (target.pull.headSha != sha) throw GitHubException(GitHubError.CONFLICT)
        target.pull = target.pull.copy(state = "closed", merged = true, mergeableState = "unknown")
        return MergeResult(true, "Pull Request successfully merged", sha(2000 + number))
    }
}

class DemoSessions : SessionStore {
    private var token: String? = "demo-session"
    override fun readToken() = token
    override fun saveToken(token: String) { this.token = token }
    override fun clearToken() { token = null }
}

class DemoDeviceAuth : DeviceAuth {
    override suspend fun start(clientId: String): DeviceCode = throw GitHubException(GitHubError.VALIDATION)
    override suspend fun poll(clientId: String, deviceCode: String): DevicePoll =
        throw GitHubException(GitHubError.VALIDATION)
}
