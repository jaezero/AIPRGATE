package com.example.aiprgate.github

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubApiTest {
    private val repo = RepoId("jaezero", "AIPRGATE")
    private val sha = "a".repeat(40)
    private val otherSha = "b".repeat(40)
    private val token = "test-only-invalid-token"

    private class FakeTransport(vararg responses: GitHubResponse) : GitHubTransport {
        private val responses = ArrayDeque(responses.toList())
        val requests = mutableListOf<GitHubRequest>()

        override suspend fun execute(request: GitHubRequest): GitHubResponse {
            requests += request
            check(responses.isNotEmpty()) { "Unexpected request" }
            return responses.removeFirst()
        }
    }

    @Test fun viewerUsesAuthenticatedVersionedGet() = runBlocking {
        // Given
        val transport = FakeTransport(ok("""{"login":"reviewer"}"""))
        // When
        val viewer = GitHubApi(transport).viewer(token)
        // Then
        assertEquals("reviewer", viewer.login)
        val request = transport.requests.single()
        assertEquals("GET", request.method)
        assertEquals("https://api.github.com/user", request.url)
        assertEquals("Bearer $token", request.headers["Authorization"])
        assertEquals("2022-11-28", request.headers["X-GitHub-Api-Version"])
        assertEquals("application/vnd.github+json", request.headers["Accept"])
        assertNull(request.body)
        assertFalse(request.toString().contains(token))
    }

    @Test fun publicRepositoryHasNoAuthorizationAndParsesCapabilities() = runBlocking {
        // Given
        val transport = FakeTransport(ok(
            """{"permissions":{"push":true},"allow_merge_commit":false,
                "allow_squash_merge":true,"allow_rebase_merge":true}""",
        ))
        // When
        val info = GitHubApi(transport).repository(repo, null)
        // Then
        assertEquals(repo, info.id)
        assertTrue(info.canPush)
        assertEquals(listOf("squash", "rebase"), info.mergeMethods)
        assertEquals("https://api.github.com/repos/jaezero/AIPRGATE", transport.requests.single().url)
        assertFalse(transport.requests.single().headers.containsKey("Authorization"))
    }

    @Test fun missingPermissionsNeverGrantPush() = runBlocking {
        // Given
        val transport = FakeTransport(ok(
            """{"allow_merge_commit":true,"allow_squash_merge":false,"allow_rebase_merge":false}""",
        ))
        // When
        val info = GitHubApi(transport).repository(repo, null)
        // Then
        assertFalse(info.canPush)
        assertEquals(listOf("merge"), info.mergeMethods)
    }

    @Test fun publicRepositoryWithoutAdministrativeCapabilitiesRemainsReadable() = runBlocking {
        // Anonymous GET /repos/jaezero/AIPRGATE omits permissions and allow_* settings.
        val transport = FakeTransport(ok("""{"full_name":"jaezero/AIPRGATE","private":false}"""))
        val info = GitHubApi(transport).repository(repo, null)
        assertEquals(repo, info.id)
        assertFalse(info.canPush)
        assertTrue(info.mergeMethods.isEmpty())
    }

    @Test fun pullListUsesThirtyItemsAndServerNextLink() = runBlocking {
        // Given
        val transport = FakeTransport(ok(JSONArray().put(pullJson()).toString(), next("pulls", 2)))
        // When
        val page = GitHubApi(transport).pulls(repo, null, "all", 1)
        // Then
        assertTrue(page.hasNext)
        assertEquals(17, page.items.single().number)
        assertEquals(sha, page.items.single().headSha)
        assertEquals(otherSha, page.items.single().baseSha)
        assertEquals("unknown", page.items.single().mergeableState)
        assertNull(page.items.single().mergeable)
        assertEquals(
            "https://api.github.com/repos/jaezero/AIPRGATE/pulls?state=all&sort=updated&direction=desc&per_page=30&page=1",
            transport.requests.single().url,
        )
    }

    @Test fun fullPageWithoutLinkDoesNotInventAnotherPage() = runBlocking {
        // Given
        val items = JSONArray()
        repeat(30) { items.put(pullJson().put("number", it + 1)) }
        val transport = FakeTransport(ok(items.toString()))
        // When
        val page = GitHubApi(transport).pulls(repo, null, "open", 3)
        // Then
        assertEquals(30, page.items.size)
        assertFalse(page.hasNext)
        assertTrue(transport.requests.single().url.endsWith("page=3"))
    }

    @Test fun detailPreservesMergeIdentityAndNullableBody() = runBlocking {
        // Given
        val json = pullJson().put("body", JSONObject.NULL).put("merged", true)
            .put("mergeable", false).put("mergeable_state", "blocked")
            .put("merge_commit_sha", otherSha).put("changed_files", 9)
        val transport = FakeTransport(ok(json.toString()))
        // When
        val pull = GitHubApi(transport).pull(repo, 17, token)
        // Then
        assertEquals("", pull.body)
        assertTrue(pull.merged)
        assertEquals(false, pull.mergeable)
        assertEquals("blocked", pull.mergeableState)
        assertEquals(otherSha, pull.mergeCommitSha)
        assertEquals(9, pull.changedFiles)
        assertEquals("https://api.github.com/repos/jaezero/AIPRGATE/pulls/17", transport.requests.single().url)
    }

    @Test fun filesPreservePatchAndMissingBinaryPatch() = runBlocking {
        // Given
        val patch = "@@ -1 +1 @@\n-old\n+new \"quoted\""
        val json = JSONObject().put("filename", "app/Example.kt").put("status", "modified")
            .put("additions", 1).put("deletions", 1).put("patch", patch)
            .put("blob_url", "https://github.com/jaezero/AIPRGATE/blob/$sha/app/Example.kt")
        val binary = JSONObject(json.toString()).apply { remove("patch") }
        val transport = FakeTransport(ok(JSONArray().put(json).put(binary).toString()))
        // When
        val page = GitHubApi(transport).files(repo, 17, null, 2)
        // Then
        assertEquals(patch, page.items[0].patch)
        assertNull(page.items[1].patch)
        assertEquals("app/Example.kt", page.items[0].name)
        assertFalse(page.hasNext)
        assertTrue(transport.requests.single().url.endsWith("/pulls/17/files?per_page=30&page=2"))
    }

    @Test fun checksReadAllPagesFilterShaAndDedupeByAppAndName() = runBlocking {
        // Given
        val first = JSONArray().put(checkJson(3, 10)).put(checkJson(4, 10, otherSha))
        val second = JSONArray().put(checkJson(8, 10)).put(checkJson(9, 11))
        val transport = FakeTransport(
            ok(JSONObject().put("check_runs", first).toString(), next("commits/$sha/check-runs", 2)),
            ok(JSONObject().put("check_runs", second).toString()),
        )
        // When
        val checks = GitHubApi(transport).checks(repo, sha, null)
        // Then
        assertEquals(listOf(8L, 9L), checks.map { it.id })
        assertEquals(listOf(10L, 11L), checks.map { it.appId })
        assertTrue(checks.all { it.headSha == sha && it.summary == "summary" })
        assertEquals(2, transport.requests.size)
        assertTrue(transport.requests[0].url.endsWith("/commits/$sha/check-runs?filter=latest&per_page=100&page=1"))
        assertTrue(transport.requests[1].url.endsWith("per_page=100&page=2"))
    }

    @Test fun statusesReadAllPagesAndChooseNewestIdForEachContext() = runBlocking {
        // Given
        val transport = FakeTransport(
            ok(JSONArray().put(statusJson(9, "build", "success")).toString(), next("commits/$sha/statuses", 2)),
            ok(JSONArray().put(statusJson(2, "build", "failure"))
                .put(statusJson(3, "lint", "pending")).toString()),
        )
        // When
        val statuses = GitHubApi(transport).statuses(repo, sha, token)
        // Then
        assertEquals(listOf(9L, 3L), statuses.map { it.id })
        assertEquals("success", statuses.first().state)
        assertEquals("", statuses.last().description)
        assertTrue(transport.requests[0].url.endsWith("/commits/$sha/statuses?per_page=100&page=1"))
        assertTrue(transport.requests[1].url.endsWith("page=2"))
    }

    @Test fun actionRunLinkPrefersDetailsUrlOverGenericCheckPage() = runBlocking {
        val runUrl = "https://github.com/jaezero/AIPRGATE/actions/runs/123/job/456"
        val check = checkJson(1, 15368).put("details_url", runUrl)
        val transport = FakeTransport(ok(JSONObject()
            .put("check_runs", JSONArray().put(check)).toString()))
        assertEquals(runUrl, GitHubApi(transport).checks(repo, sha, null).single().url)
    }

    @Test fun reviewsReadAllPagesWithoutCollapsingDifferentReviewEvents() = runBlocking {
        // Given
        val transport = FakeTransport(
            ok(JSONArray().put(reviewJson(1)).toString(), next("pulls/17/reviews", 2)),
            ok(JSONArray().put(reviewJson(1)).put(reviewJson(2).put("state", "DISMISSED")).toString()),
        )
        // When
        val reviews = GitHubApi(transport).reviews(repo, 17, null)
        // Then
        assertEquals(listOf(1L, 2L), reviews.map { it.id })
        assertEquals("DISMISSED", reviews.last().state)
        assertEquals(sha, reviews.last().commitSha)
        assertTrue(transport.requests.first().url.endsWith("/pulls/17/reviews?per_page=100&page=1"))
        assertEquals(2, transport.requests.size)
    }

    @Test fun explicitReviewPreservesEventHeadShaAndExactBody() = runBlocking {
        // Given
        val body = "Review \"quotes\"\nline two: \\path \uD55C\uAE00"
        val transport = FakeTransport(ok(reviewJson(4).put("body", body).toString()))
        // When
        val review = GitHubApi(transport).review(repo, 17, token, sha, ReviewEvent.REQUEST_CHANGES, body)
        // Then
        val request = transport.requests.single()
        val payload = JSONObject(request.body!!)
        assertEquals("POST", request.method)
        assertEquals("https://api.github.com/repos/jaezero/AIPRGATE/pulls/17/reviews", request.url)
        assertEquals("Bearer $token", request.headers["Authorization"])
        assertEquals("application/json; charset=utf-8", request.headers["Content-Type"])
        assertEquals(setOf("event", "commit_id", "body"), payload.keys().asSequence().toSet())
        assertEquals("REQUEST_CHANGES", payload.getString("event"))
        assertEquals(sha, payload.getString("commit_id"))
        assertEquals(body, payload.getString("body"))
        assertEquals(body, review.body)
    }

    @Test fun eachReviewEventIsExplicitRatherThanPending() = runBlocking {
        ReviewEvent.entries.forEach { event ->
            // Given
            val transport = FakeTransport(ok(reviewJson(1).toString()))
            // When
            GitHubApi(transport).review(repo, 17, token, sha, event, "")
            // Then
            assertEquals(event.name, JSONObject(transport.requests.single().body!!).getString("event"))
        }
    }

    @Test fun mergeSendsConfirmedShaAndMethodAndUsesServerMergedBoolean() = runBlocking {
        // Given
        val transport = FakeTransport(ok(
            JSONObject().put("merged", true).put("sha", otherSha).put("message", token).toString(),
        ))
        // When
        val result = GitHubApi(transport).merge(repo, 17, token, sha, "squash")
        // Then
        val request = transport.requests.single()
        val payload = JSONObject(request.body!!)
        assertEquals("PUT", request.method)
        assertEquals("https://api.github.com/repos/jaezero/AIPRGATE/pulls/17/merge", request.url)
        assertEquals("Bearer $token", request.headers["Authorization"])
        assertEquals(setOf("sha", "merge_method"), payload.keys().asSequence().toSet())
        assertEquals(sha, payload.getString("sha"))
        assertEquals("squash", payload.getString("merge_method"))
        assertTrue(result.merged)
        assertEquals(otherSha, result.sha)
        assertFalse(result.message.contains(token))
    }

    @Test fun successfulHttpDoesNotImplySuccessfulMerge() = runBlocking {
        // Given
        val transport = FakeTransport(ok("""{"merged":false,"message":"not ready","sha":null}"""))
        // When
        val result = GitHubApi(transport).merge(repo, 17, token, sha, "merge")
        // Then
        assertFalse(result.merged)
        assertNull(result.sha)
    }

    @Test fun httpErrorsHaveTypedSanitizedFailures() = runBlocking {
        val cases = listOf(
            401 to GitHubError.AUTHENTICATION, 403 to GitHubError.PERMISSION,
            404 to GitHubError.NOT_FOUND, 405 to GitHubError.CONFLICT,
            409 to GitHubError.CONFLICT, 422 to GitHubError.VALIDATION,
            429 to GitHubError.RATE_LIMIT, 500 to GitHubError.SERVER,
            503 to GitHubError.SERVER, 302 to GitHubError.INVALID_RESPONSE,
        )
        cases.forEach { (status, kind) ->
            // Given
            val transport = FakeTransport(GitHubResponse(status, """{"message":"$token"}"""))
            // When
            val error = failure { GitHubApi(transport).viewer(token) }
            // Then
            assertEquals(kind, error.kind)
            assertEquals(status, error.status)
            assertFalse(error.toString().contains(token))
            assertNull(error.cause)
            assertEquals(1, transport.requests.size)
        }
    }

    @Test fun rateLimit403IsDifferentFromPermission403() = runBlocking {
        val responses = listOf(
            GitHubResponse(403, "", mapOf("x-ratelimit-remaining" to "0")),
            GitHubResponse(403, "", mapOf("retry-after" to "60")),
            GitHubResponse(403, """{"message":"You have exceeded a secondary rate limit."}"""),
        )
        responses.forEach { response ->
            // Given
            val transport = FakeTransport(response)
            // When
            val error = failure { GitHubApi(transport).viewer(token) }
            // Then
            assertEquals(GitHubError.RATE_LIMIT, error.kind)
            assertEquals(403, error.status)
        }
    }

    @Test fun ioExceptionIsNetworkWithoutOriginalExceptionLeakage() = runBlocking {
        // Given
        val api = GitHubApi { throw IOException(token) }
        // When
        val error = failure { api.viewer(token) }
        // Then
        assertEquals(GitHubError.NETWORK, error.kind)
        assertNull(error.cause)
        assertFalse(error.toString().contains(token))
    }

    @Test fun malformedRequiredFieldsFailRatherThanCoerceOrDefault() = runBlocking {
        val invalid = listOf(
            """{"login":123}""", """{"login":null}""", "{}", "[]", "{broken",
        )
        invalid.forEach { body ->
            // Given
            val api = GitHubApi(FakeTransport(ok(body)))
            // When
            val error = failure { api.viewer(token) }
            // Then
            assertEquals(GitHubError.INVALID_RESPONSE, error.kind)
        }
    }

    @Test fun malformedPullFieldsFailRatherThanInventIdentity() = runBlocking {
        val invalid = listOf(
            pullJson().apply { remove("user") },
            pullJson().put("number", "17"),
            pullJson().put("draft", "false"),
            pullJson().put("head", JSONObject().put("sha", "main").put("ref", "main")),
            pullJson().put("changed_files", -1),
            pullJson().put("mergeable", "true"),
        )
        invalid.forEach { json ->
            // Given
            val api = GitHubApi(FakeTransport(ok(json.toString())))
            // When
            val error = failure { api.pull(repo, 17, null) }
            // Then
            assertEquals(GitHubError.INVALID_RESPONSE, error.kind)
        }
    }

    @Test fun malformedMergeCannotClaimSuccessWithoutSha() = runBlocking {
        // Given
        val api = GitHubApi(FakeTransport(ok("""{"merged":true,"sha":null}""")))
        // When
        val error = failure { api.merge(repo, 17, token, sha, "merge") }
        // Then
        assertEquals(GitHubError.INVALID_RESPONSE, error.kind)
    }

    @Test fun invalidBoundariesNeverReachTransport() = runBlocking {
        val transport = FakeTransport()
        val api = GitHubApi(transport)
        val actions: List<suspend () -> Unit> = listOf(
            { api.repository(RepoId("https://evil.invalid", "repo"), null) },
            { api.repository(RepoId("owner", ".."), null) },
            { api.checks(repo, "main", null) },
            { api.statuses(repo, "$sha/extra", null) },
            { api.pull(repo, 0, null) },
            { api.pulls(repo, null, "open&token=bad", 1) },
            { api.pulls(repo, null, "open", 0) },
            { api.files(repo, 17, null, -1) },
            { api.viewer(" ") },
            { api.viewer("bad\r\nHeader:bad") },
            { api.review(repo, 17, "", sha, ReviewEvent.APPROVE, "") },
            { api.merge(repo, 17, token, sha, "auto") },
            { api.merge(repo, 17, " ", sha, "merge") },
        )
        actions.forEach { action ->
            // Given the same empty transport; When
            val error = failure(action)
            // Then
            assertEquals(GitHubError.VALIDATION, error.kind)
        }
        assertTrue(transport.requests.isEmpty())
    }

    @Test fun foreignAndRepeatingPaginationLinksFailWithoutFollowingThem() = runBlocking {
        val links = listOf(
            """<https://evil.invalid/?page=2>; rel="next"""",
            """<http://api.github.com/?page=2>; rel="next"""",
            """<https://secret@api.github.com/?page=2>; rel="next"""",
            """<https://api.github.com/?page=1>; rel="next"""",
            "invalid",
        )
        links.forEach { link ->
            // Given
            val transport = FakeTransport(ok("[]", link))
            // When
            val error = failure { GitHubApi(transport).statuses(repo, sha, token) }
            // Then
            assertEquals(GitHubError.INVALID_RESPONSE, error.kind)
            assertEquals(1, transport.requests.size)
        }
    }

    @Test fun defaultTransportRejectsNonGitHubOriginsBeforeOpeningNetwork() = runBlocking {
        val urls = listOf(
            "http://api.github.com/user", "https://evil.invalid/user",
            "https://api.github.com.evil.invalid/user", "https://api.github.com:444/user",
            "https://secret@api.github.com/user", "https://api.github.com/user#fragment",
        )
        urls.forEach { url ->
            // Given
            val request = GitHubRequest("GET", url, mapOf("Authorization" to "Bearer $token"))
            // When
            val error = failure { UrlConnectionGitHubTransport().execute(request) }
            // Then
            assertEquals(GitHubError.VALIDATION, error.kind)
        }
    }

    @Test fun cancellationIsNotMappedToNetworkOrServerError() = runBlocking {
        withTimeout(5_000) {
            // Given
            val entered = CompletableDeferred<Unit>()
            val api = GitHubApi {
                entered.complete(Unit)
                awaitCancellation()
            }
            val result = async { api.viewer(token) }
            entered.await()
            // When
            result.cancel()
            // Then
            try {
                result.await()
                throw AssertionError("Expected cancellation")
            } catch (_: CancellationException) {
                assertTrue(result.isCancelled)
            }
        }
    }

    @Test fun aiReviewsTrustOnlyBotCommentsThatStartWithTheMarker() = runBlocking {
        // Given
        fun comment(id: Long, login: String, body: String) = JSONObject()
            .put("id", id).put("user", JSONObject().put("login", login)).put("body", body)
            .put("created_at", "2026-10-27T01:00:00Z")
            .put("html_url", "https://github.com/jaezero/AIPRGATE/pull/17#issuecomment-$id")
        val marker = "<!-- aiprgate:ai-review sha=$sha -->"
        val transport = FakeTransport(ok(JSONArray()
            .put(comment(1, "github-actions[bot]", "$marker\n### AI 리뷰 의견\n요약"))
            .put(comment(2, "someone", "$marker\n가짜 AI 의견"))
            .put(comment(3, "github-actions[bot]", "일반 봇 댓글 $marker"))
            .put(comment(4, "reviewer", "사람 의견"))
            .toString()))
        // When
        val reviews = GitHubApi(transport).aiReviews(repo, 17, null)
        // Then
        assertEquals(listOf(1L), reviews.map { it.id })
        assertEquals(sha, reviews.single().commitSha)
        assertEquals("### AI 리뷰 의견\n요약", reviews.single().body)
        assertTrue(transport.requests.single().url.endsWith("/issues/17/comments?per_page=100&page=1"))
    }

    private fun ok(body: String, link: String? = null): GitHubResponse =
        GitHubResponse(200, body, if (link == null) emptyMap() else mapOf("Link" to link))

    private fun next(path: String, page: Int): String =
        """<https://api.github.com/repos/jaezero/AIPRGATE/$path?per_page=100&page=$page>; rel="next""""

    private fun pullJson(): JSONObject = JSONObject()
        .put("number", 17).put("title", "API").put("body", "AI evidence")
        .put("user", JSONObject().put("login", "author")).put("state", "open").put("draft", false)
        .put("head", JSONObject().put("sha", sha).put("ref", "feature"))
        .put("base", JSONObject().put("sha", otherSha).put("ref", "main"))
        .put("html_url", "https://github.com/jaezero/AIPRGATE/pull/17")
        .put("updated_at", "2026-10-25T00:00:00Z").put("merged_at", JSONObject.NULL)

    private fun checkJson(id: Long, appId: Long, head: String = sha): JSONObject = JSONObject()
        .put("id", id).put("name", "build").put("head_sha", head).put("status", "completed")
        .put("conclusion", "success").put("html_url", "https://github.com/check/$id")
        .put("app", JSONObject().put("id", appId))
        .put("output", JSONObject().put("summary", "summary"))

    private fun statusJson(id: Long, context: String, state: String): JSONObject = JSONObject()
        .put("id", id).put("context", context).put("state", state)
        .put("description", JSONObject.NULL).put("target_url", JSONObject.NULL)

    private fun reviewJson(id: Long): JSONObject = JSONObject()
        .put("id", id).put("user", JSONObject().put("login", "reviewer"))
        .put("state", "APPROVED").put("body", "").put("commit_id", sha)
        .put("submitted_at", "2026-10-25T00:00:00Z")
        .put("html_url", "https://github.com/jaezero/AIPRGATE/pull/17#pullrequestreview-$id")

    private suspend fun failure(action: suspend () -> Unit): GitHubException {
        try {
            action()
        } catch (error: GitHubException) {
            return error
        }
        throw AssertionError("Expected GitHubException")
    }
}
