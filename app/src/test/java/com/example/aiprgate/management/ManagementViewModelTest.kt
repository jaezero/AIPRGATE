package com.example.aiprgate.management

import com.example.aiprgate.auth.*
import com.example.aiprgate.github.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

private val head = "a".repeat(40)
private val base = "b".repeat(40)
private val nextHead = "c".repeat(40)
private val repo = RepoId("jaezero", "AIPRGATE")
private val confirmedTarget = PullTarget(repo, 1, head, base, "main")

internal fun testPull() = PullRequest(
    1, "Review a change", "Purpose and test evidence", "author", "open", false, false,
    head, base, "feature", "main", "https://github.com/jaezero/AIPRGATE/pull/1",
    "2026-10-25T10:00:00Z", true, "clean", null, 1,
)

internal fun passingChecks() = REQUIRED_CHECKS.mapIndexed { index, name ->
    CheckRun(index + 1L, name, head, "completed", "success",
        "https://github.com/jaezero/AIPRGATE/actions/runs/100/job/${index + 1}",
        "", 15368)
}

private fun approval() = PullReview(
    1, "reviewer", "APPROVED", "", head, "2026-10-25T10:00:00Z", "https://github.com/review",
)

private class MemorySessions(var value: String? = null) : SessionStore {
    var failSave = false
    var clears = 0
    override fun readToken() = value
    override fun saveToken(token: String) {
        if (failSave) error("storage-failure")
        value = token
    }
    override fun clearToken() { clears++; value = null }
}

private class FakeDevice : DeviceAuth {
    var polls = 0
    var starts = 0
    var firstStartGate: CompletableDeferred<Unit>? = null
    var result: DevicePoll = DevicePoll.Authorized("invalid-test-token")
    override suspend fun start(clientId: String): DeviceCode {
        starts++
        if (starts == 1) firstStartGate?.let { gate ->
            // A transport may finish an in-flight blocking request after cancellation.
            withContext(NonCancellable) { gate.await() }
        }
        return DeviceCode("private-code", "TEST-CODE", "https://github.com/login/device", 5, 60)
    }
    override suspend fun poll(clientId: String, deviceCode: String): DevicePoll {
        polls++
        return result
    }
}

private class FakeGitHub : GitHubService {
    var pr = testPull()
    var user = "reviewer"
    var viewerFailure = false
    var viewerCalls = 0
    var pullFailure = false
    var changedOnFiles = false
    var checks = passingChecks()
    var reviewList = listOf(approval())
    var writes = 0
    var lastSha: String? = null
    var lastEvent: ReviewEvent? = null
    var lastBody: String? = null
    var firstListSignal: CompletableDeferred<Unit>? = null
    var blockFirstList: CompletableDeferred<Unit>? = null
    override suspend fun viewer(token: String): GitHubUser {
        viewerCalls++
        if (viewerFailure) throw GitHubException(GitHubError.AUTHENTICATION)
        return GitHubUser(user)
    }
    override suspend fun repository(repo: RepoId, token: String?) =
        RepositoryInfo(repo, token != null, listOf("merge", "squash"))
    override suspend fun pulls(repo: RepoId, token: String?, state: String, page: Int): Page<PullRequest> {
        if (repo.name == "AIPRGATE") {
            firstListSignal?.complete(Unit)
            blockFirstList?.await()
        }
        return Page(listOf(pr), false)
    }
    override suspend fun pull(repo: RepoId, number: Int, token: String?): PullRequest {
        if (pullFailure) throw GitHubException(GitHubError.NETWORK)
        return pr
    }
    override suspend fun files(repo: RepoId, number: Int, token: String?, page: Int): Page<PullFile> {
        if (changedOnFiles) pr = pr.copy(headSha = nextHead)
        return Page(listOf(PullFile("App.kt", "modified", 1, 1, "@@\n-old\n+new", "https://github.com/file")), false)
    }
    override suspend fun checks(repo: RepoId, sha: String, token: String?) =
        checks.filter { it.headSha == sha }
    override suspend fun statuses(repo: RepoId, sha: String, token: String?) = emptyList<CommitStatus>()
    override suspend fun reviews(repo: RepoId, number: Int, token: String?) = reviewList
    override suspend fun review(
        repo: RepoId, number: Int, token: String, sha: String, event: ReviewEvent, body: String,
    ): PullReview {
        writes++
        lastSha = sha; lastEvent = event; lastBody = body
        val result = approval().copy(id = 2, author = user, state = when (event) {
            ReviewEvent.COMMENT -> "COMMENTED"
            ReviewEvent.REQUEST_CHANGES -> "CHANGES_REQUESTED"
            ReviewEvent.APPROVE -> "APPROVED"
        }, body = body)
        reviewList += result
        return result
    }
    override suspend fun merge(repo: RepoId, number: Int, token: String, sha: String, method: String): MergeResult {
        writes++
        lastSha = sha
        pr = pr.copy(merged = true, state = "closed")
        return MergeResult(true, "merged", base)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ManagementViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }
    private fun model(
        api: FakeGitHub = FakeGitHub(), store: MemorySessions = MemorySessions(),
        device: FakeDevice = FakeDevice(), clock: () -> Long = { 0 },
    ) = ManagementViewModel(api, store, device, dispatcher, clock)

    @Test fun publicListAndDetailsDoNotRequireCredentialOrPerformWrites() = runTest(dispatcher) {
        val api = FakeGitHub()
        val model = model(api)
        advanceUntilIdle()
        assertEquals(1, model.state.value.pulls.size)
        assertNull(model.state.value.viewer)
        model.openPull(1)
        advanceUntilIdle()
        assertEquals(head, model.state.value.detail!!.pull.headSha)
        assertEquals(5, model.state.value.detail!!.checks.size)
        assertEquals(0, api.writes)
    }

    @Test fun verifiedTokenIsStoredAndLogoutDeletesSession() = runTest(dispatcher) {
        val store = MemorySessions()
        val model = model(store = store)
        advanceUntilIdle()
        model.loginWithToken("invalid-test-token")
        advanceUntilIdle()
        assertEquals("reviewer", model.state.value.viewer!!.login)
        assertEquals("invalid-test-token", store.value)
        model.logout()
        advanceUntilIdle()
        assertNull(store.value)
        assertNull(model.state.value.viewer)
    }

    @Test fun rejectedTokenAndFailedStorageNeverCreateAuthenticatedState() = runTest(dispatcher) {
        val api = FakeGitHub().apply { viewerFailure = true }
        val store = MemorySessions()
        val model = model(api, store)
        advanceUntilIdle()
        model.loginWithToken("invalid-test-token")
        advanceUntilIdle()
        assertNull(store.value)
        assertNull(model.state.value.viewer)
        api.viewerFailure = false
        store.failSave = true
        model.loginWithToken("invalid-test-token")
        advanceUntilIdle()
        assertNull(store.value)
        assertNull(model.state.value.viewer)
    }

    @Test fun pushDuringDetailFetchInvalidatesSnapshot() = runTest(dispatcher) {
        val api = FakeGitHub().apply { changedOnFiles = true }
        val model = model(api)
        advanceUntilIdle()
        model.openPull(1)
        advanceUntilIdle()
        assertNull(model.state.value.detail)
        assertNotNull(model.state.value.error)
    }

    @Test fun failedInitialDetailCanBeRetriedWithoutReturningToList() = runTest(dispatcher) {
        val api = FakeGitHub().apply { pullFailure = true }
        val model = model(api)
        advanceUntilIdle()
        model.openPull(1)
        advanceUntilIdle()
        assertNotNull(model.state.value.error)
        api.pullFailure = false
        model.refresh()
        advanceUntilIdle()
        assertEquals(ManagementScreen.DETAIL, model.state.value.screen)
        assertNotNull(model.state.value.detail)
    }

    @Test fun staleWriteIsRejectedAndDoesNotSubmitReview() = runTest(dispatcher) {
        val api = FakeGitHub()
        val model = model(api, MemorySessions("invalid-test-token"))
        advanceUntilIdle()
        model.openPull(1)
        advanceUntilIdle()
        api.pr = api.pr.copy(headSha = nextHead)
        model.submitReview(ReviewEvent.APPROVE, confirmedTarget)
        advanceUntilIdle()
        assertEquals(0, api.writes)
        assertNotNull(model.state.value.error)
    }

    @Test fun reviewIsExplicitAndPreservesBodyAndConfirmedCommit() = runTest(dispatcher) {
        val api = FakeGitHub()
        val model = model(api, MemorySessions("invalid-test-token"))
        advanceUntilIdle()
        model.openPull(1)
        advanceUntilIdle()
        model.reviewBody("Evidence verified")
        model.submitReview(ReviewEvent.REQUEST_CHANGES, confirmedTarget)
        advanceUntilIdle()
        assertEquals(1, api.writes)
        assertEquals(head, api.lastSha)
        assertEquals(ReviewEvent.REQUEST_CHANGES, api.lastEvent)
        assertEquals("Evidence verified", api.lastBody)
        assertEquals("", model.state.value.reviewBody)
    }

    @Test fun authorCannotApproveOrRequestChangesOnOwnPull() = runTest(dispatcher) {
        val api = FakeGitHub().apply { user = "AUTHOR" }
        val model = model(api, MemorySessions("invalid-test-token"))
        advanceUntilIdle()
        model.openPull(1)
        advanceUntilIdle()
        model.submitReview(ReviewEvent.APPROVE, confirmedTarget)
        advanceUntilIdle()
        assertEquals(0, api.writes)
    }

    @Test fun authorSelfReviewIsMarkedCommentOnHeadAndSatisfiesSoloMerge() = runTest(dispatcher) {
        val api = FakeGitHub().apply { user = "author"; reviewList = emptyList() }
        val model = model(api, MemorySessions("invalid-test-token"))
        advanceUntilIdle()
        model.openPull(1)
        advanceUntilIdle()
        val before = model.state.value
        assertTrue(mergeBlockers(before.detail!!, before.viewer, before.repository).isNotEmpty())
        model.submitSelfReview(confirmedTarget)
        advanceUntilIdle()
        assertEquals(ReviewEvent.COMMENT, api.lastEvent)
        assertEquals(head, api.lastSha)
        assertTrue(api.lastBody!!.startsWith(SELF_REVIEW_MARKER))
        val after = model.state.value
        assertEquals(emptyList<String>(), mergeBlockers(after.detail!!, after.viewer, after.repository))
        assertTrue(latestApprovals(after.detail!!).isEmpty())
    }

    @Test fun onlyTheAuthorCanRecordASelfReview() = runTest(dispatcher) {
        val api = FakeGitHub().apply { reviewList = emptyList() }
        val model = model(api, MemorySessions("invalid-test-token"))
        advanceUntilIdle()
        model.openPull(1)
        advanceUntilIdle()
        model.submitSelfReview(confirmedTarget)
        advanceUntilIdle()
        assertEquals(0, api.writes)
    }

    @Test fun selfReviewCountsOnlyWhenMarkedByAuthorOnCurrentHead() {
        val marked = PullReview(5, "author", "COMMENTED", selfReviewBody(""), head,
            "2026-10-25T10:00:00Z", "https://github.com/review")
        val detail = PullDetails(testPull(), emptyList(), 1, false, passingChecks(), emptyList(), listOf(marked))
        assertEquals(1, latestSelfReviews(detail).size)
        listOf(marked.copy(commitSha = nextHead), marked.copy(author = "reviewer"),
            marked.copy(body = "looks fine"), marked.copy(state = "APPROVED")).forEach {
            assertTrue(latestSelfReviews(detail.copy(reviews = listOf(it))).isEmpty())
        }
    }

    @Test fun mergeRevalidatesChecksAndRequiresLatestApproval() = runTest(dispatcher) {
        val api = FakeGitHub()
        val model = model(api, MemorySessions("invalid-test-token"))
        advanceUntilIdle()
        model.openPull(1)
        advanceUntilIdle()
        api.checks = api.checks.map { it.copy(conclusion = "failure") }
        model.merge(confirmedTarget, "merge")
        advanceUntilIdle()
        assertEquals(0, api.writes)
    }

    @Test fun validMergeUsesExpectedHeadAndWaitsForServerResult() = runTest(dispatcher) {
        val api = FakeGitHub()
        val model = model(api, MemorySessions("invalid-test-token"))
        advanceUntilIdle()
        model.openPull(1)
        advanceUntilIdle()
        model.merge(confirmedTarget, "squash")
        advanceUntilIdle()
        assertEquals(1, api.writes)
        assertEquals(head, api.lastSha)
        assertTrue(model.state.value.detail!!.pull.merged)
        assertFalse(model.state.value.writing)
    }

    @Test fun duplicateWriteAndNavigationCannotChangeInFlightTarget() = runTest(dispatcher) {
        val api = FakeGitHub()
        val model = model(api, MemorySessions("invalid-test-token"))
        advanceUntilIdle()
        model.openPull(1)
        advanceUntilIdle()
        model.submitReview(ReviewEvent.APPROVE, confirmedTarget)
        model.submitReview(ReviewEvent.APPROVE, confirmedTarget)
        model.selectRepository("another/repository")
        model.logout()
        advanceUntilIdle()
        assertEquals(1, api.writes)
        assertEquals(repo, model.state.value.repo)
        assertNotNull(model.state.value.viewer)
    }

    @Test fun cancelledOldRepositoryCannotOverwriteNewList() = runTest(dispatcher) {
        val api = FakeGitHub().apply {
            firstListSignal = CompletableDeferred()
            blockFirstList = CompletableDeferred()
        }
        val model = model(api)
        runCurrent()
        assertTrue(api.firstListSignal!!.isCompleted)
        model.selectRepository("another/repository")
        advanceUntilIdle()
        assertEquals("another/repository", model.state.value.repository!!.id.fullName)
        api.blockFirstList!!.complete(Unit)
        advanceUntilIdle()
        assertEquals("another/repository", model.state.value.repo.fullName)
    }

    @Test fun deviceFlowWaitsProtocolIntervalBeforeFirstPollAndCanCancel() = runTest(dispatcher) {
        val device = FakeDevice()
        val store = MemorySessions()
        val model = model(store = store, device = device, clock = { testScheduler.currentTime * 1_000_000 })
        advanceUntilIdle()
        model.startDeviceLogin("Iv1.publicClient")
        runCurrent()
        assertNotNull(model.state.value.deviceCode)
        assertEquals(0, device.polls)
        advanceTimeBy(4999)
        runCurrent()
        assertEquals(0, device.polls)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, device.polls)
        assertEquals("invalid-test-token", store.value)
        model.logout()
        advanceUntilIdle()
        device.result = DevicePoll.Pending
        model.startDeviceLogin("Iv1.publicClient")
        runCurrent()
        model.cancelAuthentication()
        advanceUntilIdle()
        assertNull(store.value)
        assertFalse(model.state.value.authenticating)
    }

    @Test fun cancelledLoginCannotClearANewerAuthenticationAttempt() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val device = FakeDevice().apply { firstStartGate = gate; result = DevicePoll.Pending }
        val model = model(device = device, clock = { testScheduler.currentTime * 1_000_000 })
        advanceUntilIdle()
        model.startDeviceLogin("Iv1.first")
        runCurrent()
        model.cancelAuthentication()
        model.startDeviceLogin("Iv1.second")
        runCurrent()
        gate.complete(Unit)
        runCurrent()
        try {
            assertTrue(model.state.value.authenticating)
            assertNotNull(model.state.value.deviceCode)
        } finally {
            model.cancelAuthentication()
            advanceUntilIdle()
        }
    }

    @Test fun retargetedBranchAtSameBaseShaCannotReceiveConfirmedMerge() = runTest(dispatcher) {
        val api = FakeGitHub()
        val model = model(api, MemorySessions("invalid-test-token"))
        advanceUntilIdle()
        model.openPull(1)
        advanceUntilIdle()
        api.pr = api.pr.copy(baseRef = "release")
        model.merge(confirmedTarget, "merge")
        advanceUntilIdle()
        assertEquals(0, api.writes)
        assertNotNull(model.state.value.error)
    }

    @Test fun duplicateLoginIsBlockedBeforeCoroutineDispatch() = runTest(dispatcher) {
        val api = FakeGitHub()
        val model = model(api)
        advanceUntilIdle()
        model.loginWithToken("invalid-first-token")
        model.loginWithToken("invalid-second-token")
        advanceUntilIdle()
        assertEquals(1, api.viewerCalls)
    }

    @Test fun logoutWaitsForCancelledAuthenticationBeforeClearingStorage() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val device = FakeDevice().apply { firstStartGate = gate; result = DevicePoll.Pending }
        val store = MemorySessions()
        val model = model(store = store, device = device)
        advanceUntilIdle()
        model.startDeviceLogin("Iv1.first")
        runCurrent()
        model.logout()
        runCurrent()
        try {
            assertEquals(0, store.clears)
            assertNull(model.state.value.viewer)
        } finally {
            gate.complete(Unit)
        }
        advanceUntilIdle()
        assertEquals(1, store.clears)
        assertNull(store.value)
        assertFalse(model.state.value.authenticating)
    }
}

class ManagementPolicyTest {
    private fun detail() = PullDetails(testPull(), emptyList(), 1, false, passingChecks(),
        emptyList(), listOf(approval()))

    @Test fun onlySameRunSuccessfulTrustedChecksAllowGate() {
        val detail = detail()
        assertTrue(gateBlockers(detail).isEmpty())
        val mixed = detail.copy(checks = detail.checks.mapIndexed { i, check ->
            if (i == 0) check.copy(url = check.url.replace("/100/", "/101/")) else check
        })
        assertFalse(gateBlockers(mixed).isEmpty())
        assertFalse(gateBlockers(detail.copy(checks = detail.checks.dropLast(1))).isEmpty())
        assertFalse(gateBlockers(detail.copy(checks = detail.checks.map { it.copy(appId = 1) })).isEmpty())
    }

    @Test fun newerFailedCheckCannotReuseEarlierSuccess() {
        val detail = detail()
        val rerun = detail.checks.first().copy(id = 100, conclusion = "failure")
        assertFalse(gateBlockers(detail.copy(checks = detail.checks + rerun)).isEmpty())
    }

    @Test fun oldOrSelfApprovalAndLaterChangesDoNotAllowMerge() {
        val detail = detail()
        val repository = RepositoryInfo(repo, true, listOf("merge"))
        assertTrue(mergeBlockers(detail, GitHubUser("reviewer"), repository).isEmpty())
        assertTrue(latestApprovals(detail.copy(reviews = listOf(approval().copy(commitSha = base)))).isEmpty())
        assertTrue(latestApprovals(detail.copy(reviews = listOf(approval().copy(author = "AUTHOR")))).isEmpty())
        assertFalse(mergeBlockers(detail.copy(reviews = detail.reviews +
            approval().copy(id = 2, state = "CHANGES_REQUESTED")), GitHubUser("reviewer"), repository).isEmpty())
    }

    @Test fun repoInputAcceptsProvidedRemoteButRejectsForeignOriginOrTraversal() {
        assertEquals(repo, RepoId.parse("https://github.com/jaezero/AIPRGATE.git"))
        assertNull(RepoId.parse("https://other.example/jaezero/AIPRGATE"))
        assertNull(RepoId.parse("../AIPRGATE"))
        assertNull(RepoId.parse("jaezero/AIPRGATE/issues"))
    }
}
