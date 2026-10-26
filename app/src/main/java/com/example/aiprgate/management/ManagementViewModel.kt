package com.example.aiprgate.management

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiprgate.auth.*
import com.example.aiprgate.github.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ManagementViewModel(
    private val api: GitHubService,
    private val sessions: SessionStore,
    private val deviceAuth: DeviceAuth,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val nanoTime: () -> Long = System::nanoTime,
) : ViewModel() {
    private val mutable = MutableStateFlow(ManagementState())
    val state = mutable.asStateFlow()
    private var token: String? = null
    private var readJob: Job? = null
    private var authJob: Job? = null
    private var authGeneration = 0L
    private var selectedNumber: Int? = null

    fun onAction(action: ManagementAction) {
        when (action) {
            ManagementAction.Connect -> connection()
            ManagementAction.Back -> back()
            ManagementAction.Refresh -> refresh()
            ManagementAction.MorePulls -> morePulls()
            ManagementAction.MoreFiles -> moreFiles()
            ManagementAction.CancelLogin -> cancelAuthentication()
            ManagementAction.Logout -> logout()
            is ManagementAction.Repository -> selectRepository(action.value)
            is ManagementAction.Filter -> filter(action.value)
            is ManagementAction.OpenPull -> openPull(action.number)
            is ManagementAction.Tab -> tab(action.value)
            is ManagementAction.ReviewBody -> reviewBody(action.value)
            is ManagementAction.TokenLogin -> loginWithToken(action.token)
            is ManagementAction.DeviceLogin -> startDeviceLogin(action.clientId)
            is ManagementAction.SubmitReview -> submitReview(action.event, action.target)
            is ManagementAction.SelfReview -> submitSelfReview(action.target)
            is ManagementAction.Merge -> merge(action.target, action.method)
        }
    }

    init {
        val generation = authGeneration
        authJob = viewModelScope.launch {
            mutable.update { it.copy(authenticating = true) }
            try {
                val stored = withContext(io) { sessions.readToken() }
                if (stored != null) {
                    val user = api.viewer(stored)
                    currentCoroutineContext().ensureActive()
                    token = stored
                    mutable.update { it.copy(viewer = user) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutable.update { it.copy(notice =
                    "저장된 인증을 확인하지 못해 읽기 전용으로 시작합니다. 연결 설정에서 다시 인증하세요.") }
            } finally {
                if (generation == authGeneration) mutable.update { it.copy(authenticating = false) }
            }
            refresh()
        }
    }

    fun connection() {
        if (mutable.value.writing || mutable.value.authenticating) return
        readJob?.cancel()
        selectedNumber = null
        mutable.update {
            it.copy(screen = ManagementScreen.CONNECTION, loading = false, error = null, notice = null)
        }
    }

    fun back() {
        if (mutable.value.writing) return
        cancelAuthentication()
        readJob?.cancel()
        selectedNumber = null
        mutable.update {
            it.copy(screen = ManagementScreen.LIST, detail = null, error = null, notice = null,
                loading = false, reviewBody = "")
        }
        refresh()
    }

    fun selectRepository(input: String) {
        if (mutable.value.writing || mutable.value.authenticating) return
        val repo = RepoId.parse(input)
        if (repo == null) {
            mutable.update { it.copy(error = "저장소를 owner/name 또는 GitHub 주소로 입력하세요.") }
            return
        }
        readJob?.cancel()
        mutable.update {
            ManagementState(repo = repo, viewer = it.viewer, notice = "저장소를 연결했습니다.")
        }
        refresh()
    }

    fun filter(value: String) {
        if (value !in setOf("open", "closed", "all") || mutable.value.writing ||
            mutable.value.authenticating) return
        mutable.update { it.copy(filter = value, pulls = emptyList(), summaries = emptyMap(), page = 0) }
        refresh()
    }

    fun refresh() {
        val current = mutable.value
        if (current.writing || current.authenticating || current.screen == ManagementScreen.CONNECTION) return
        if (current.screen == ManagementScreen.DETAIL && selectedNumber != null) {
            loadDetail(requireNotNull(selectedNumber))
        } else {
            loadList(false)
        }
    }

    fun morePulls() {
        if (!mutable.value.loading && mutable.value.hasMore) loadList(true)
    }

    private fun loadList(append: Boolean) {
        readJob?.cancel()
        readJob = viewModelScope.launch {
            val before = mutable.value
            val repo = before.repo
            val credential = token
            val page = if (append) before.page + 1 else 1
            mutable.update { it.copy(loading = true, error = null) }
            try {
                val (repository, pageResult) = coroutineScope {
                    val info = async { api.repository(repo, credential) }
                    val pulls = async { api.pulls(repo, credential, before.filter, page) }
                    info.await() to pulls.await()
                }
                mutable.update {
                    it.copy(repository = repository,
                        pulls = (if (append) it.pulls + pageResult.items else pageResult.items)
                            .distinctBy { pr -> pr.number },
                        summaries = if (append) it.summaries else emptyMap(),
                        page = page, hasMore = pageResult.hasNext, loading = false)
                }
                // The list stays usable while each optional summary is fetched.
                for (pr in pageResult.items) {
                    val summary = try {
                        coroutineScope {
                            val checks = async { api.checks(repo, pr.headSha, credential) }
                            val reviews = async { api.reviews(repo, pr.number, credential) }
                            val approvals = latestDecisiveReviews(reviews.await()).count {
                                it.state == "APPROVED" && it.commitSha == pr.headSha &&
                                    !it.author.equals(pr.author, ignoreCase = true)
                            }
                            PullSummary(checks.await(), approvals)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        PullSummary(emptyList(), 0, githubMessage(error))
                    }
                    mutable.update { it.copy(summaries = it.summaries + (pr.number to summary)) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutable.update { it.copy(loading = false, error = githubMessage(error)) }
            }
        }
    }

    fun openPull(number: Int) {
        if (mutable.value.writing || mutable.value.authenticating) return
        selectedNumber = number
        mutable.update {
            it.copy(screen = ManagementScreen.DETAIL, detail = null, tab = DetailTab.SUMMARY,
                reviewBody = "", notice = null)
        }
        loadDetail(number)
    }

    private suspend fun details(repo: RepoId, number: Int, credential: String?): PullDetails =
        coroutineScope {
            val pr = api.pull(repo, number, credential)
            val files = async { api.files(repo, number, credential, 1) }
            val reviews = async { api.reviews(repo, number, credential) }
            // AI opinions are advisory: failing to read them must not hide the PR.
            val aiReviews = async {
                try {
                    api.aiReviews(repo, number, credential)
                } catch (error: GitHubException) {
                    emptyList()
                }
            }
            val statuses = async { api.statuses(repo, pr.headSha, credential) }
            val headChecks = async { api.checks(repo, pr.headSha, credential) }
            val mergeChecks = async {
                val mergeSha = pr.mergeCommitSha
                if (!pr.merged && mergeSha != null && mergeSha != pr.headSha) {
                    api.checks(repo, mergeSha, credential)
                } else emptyList()
            }
            val page = files.await()
            val checks = (headChecks.await() + mergeChecks.await()).distinctBy { it.id }
            val statusResults = statuses.await()
            val reviewResults = reviews.await()
            // A push during the fetch invalidates the whole snapshot, including files and reviews.
            val latest = api.pull(repo, number, credential)
            if (latest.target(repo) != pr.target(repo) ||
                latest.updatedAt != pr.updatedAt) throw GitHubException(GitHubError.CONFLICT)
            PullDetails(
                latest, page.items, 1, page.hasNext,
                checks, statusResults, reviewResults, aiReviews.await(),
            )
        }

    private fun loadDetail(number: Int) {
        readJob?.cancel()
        readJob = viewModelScope.launch {
            val before = mutable.value
            mutable.update { it.copy(loading = true, error = null) }
            try {
                val credential = token
                val (repository, detail) = coroutineScope {
                    val info = async { api.repository(before.repo, credential) }
                    val snapshot = async { details(before.repo, number, credential) }
                    info.await() to snapshot.await()
                }
                mutable.update { it.copy(repository = repository, detail = detail, loading = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutable.update { it.copy(loading = false, error = githubMessage(error)) }
            }
        }
    }

    fun moreFiles() {
        val before = mutable.value
        val detail = before.detail ?: return
        if (before.loading || before.writing || !detail.hasMoreFiles) return
        readJob?.cancel()
        readJob = viewModelScope.launch {
            mutable.update { it.copy(loading = true, error = null) }
            try {
                val latest = api.pull(before.repo, detail.pull.number, token)
                if (latest.target(before.repo) != detail.pull.target(before.repo)) {
                    throw GitHubException(GitHubError.CONFLICT)
                }
                val next = api.files(before.repo, detail.pull.number, token, detail.filePage + 1)
                val after = api.pull(before.repo, detail.pull.number, token)
                if (after.target(before.repo) != latest.target(before.repo)) {
                    throw GitHubException(GitHubError.CONFLICT)
                }
                mutable.update { it.copy(loading = false, detail = detail.copy(
                    files = (detail.files + next.items).distinctBy { file -> file.name },
                    filePage = detail.filePage + 1, hasMoreFiles = next.hasNext,
                )) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutable.update { it.copy(loading = false, error = githubMessage(error)) }
            }
        }
    }

    fun tab(tab: DetailTab) {
        mutable.update { it.copy(tab = tab) }
    }

    fun reviewBody(value: String) {
        if (!mutable.value.writing) mutable.update { it.copy(reviewBody = value) }
    }

    fun loginWithToken(input: String) {
        if (mutable.value.writing || mutable.value.authenticating) return
        if (input.isBlank() || input.any { it.isWhitespace() }) {
            mutable.update { it.copy(error = "공백 없는 개인 access token을 입력하세요.") }
            return
        }
        authenticate { acceptToken(input) }
    }

    private suspend fun acceptToken(credential: String) {
        val previous = token
        val viewer = api.viewer(credential)
        currentCoroutineContext().ensureActive()
        try {
            withContext(io) { sessions.saveToken(credential) }
            currentCoroutineContext().ensureActive()
        } catch (cancelled: CancellationException) {
            // Complete rollback before a newer auth/logout operation may touch storage.
            withContext(NonCancellable + io) {
                if (previous == null) sessions.clearToken() else sessions.saveToken(previous)
            }
            throw cancelled
        }
        token = credential
        mutable.update { it.copy(viewer = viewer, deviceCode = null,
            repository = null, summaries = emptyMap(), detail = null,
            notice = "${viewer.login} 사용자로 연결했습니다.", error = null) }
    }

    fun startDeviceLogin(clientId: String) {
        if (mutable.value.writing || mutable.value.authenticating) return
        if (!clientId.trim().matches(Regex("[A-Za-z0-9_.-]+"))) {
            mutable.update { it.copy(error = "등록한 GitHub App의 공개 Client ID를 입력하세요.") }
            return
        }
        authenticate {
                val code = deviceAuth.start(clientId.trim())
                currentCoroutineContext().ensureActive()
                mutable.update { it.copy(deviceCode = code) }
                val deadline = nanoTime() + code.expiresInSeconds * 1_000_000_000L
                var interval = code.intervalSeconds.toLong()
                while (nanoTime() < deadline) {
                    // GitHub's device-flow protocol specifies this minimum polling interval.
                    delay(interval * 1000)
                    if (nanoTime() >= deadline) break
                    val result = deviceAuth.poll(clientId.trim(), code.deviceCode)
                    currentCoroutineContext().ensureActive()
                    when (result) {
                        DevicePoll.Pending -> Unit
                        DevicePoll.SlowDown -> interval += 5
                        is DevicePoll.Authorized -> {
                            acceptToken(result.token)
                            return@authenticate
                        }
                        DevicePoll.Denied -> {
                            mutable.update { it.copy(error = "GitHub 인증이 거절되었습니다. 다시 연결하세요.") }
                            return@authenticate
                        }
                        DevicePoll.Expired -> break
                    }
                }
                mutable.update { it.copy(error = "인증 코드가 만료되었습니다. 새 코드를 요청하세요.") }
        }
    }

    private fun authenticate(action: suspend () -> Unit) {
        readJob?.cancel()
        val previous = authJob
        val generation = ++authGeneration
        mutable.update { it.copy(authenticating = true, deviceCode = null, error = null, notice = null) }
        authJob = viewModelScope.launch {
            try {
                previous?.cancelAndJoin()
                currentCoroutineContext().ensureActive()
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == authGeneration) mutable.update { it.copy(error = githubMessage(error)) }
            } finally {
                if (generation == authGeneration) {
                    mutable.update { it.copy(authenticating = false, deviceCode = null) }
                }
            }
        }
    }

    fun cancelAuthentication() {
        authGeneration++
        authJob?.cancel()
        mutable.update { it.copy(authenticating = false, deviceCode = null) }
    }

    fun logout() {
        if (mutable.value.writing) return
        val previous = authJob
        cancelAuthentication()
        readJob?.cancel()
        token = null
        val generation = ++authGeneration
        mutable.update { ManagementState(repo = it.repo, screen = ManagementScreen.CONNECTION, authenticating = true) }
        authJob = viewModelScope.launch {
            try {
                // Once logout starts, finish local cleanup even if the screen closes.
                // A subsequent login joins this job before saving a new credential.
                withContext(NonCancellable) {
                    previous?.cancelAndJoin()
                    withContext(io) { sessions.clearToken() }
                }
                if (generation != authGeneration) return@launch
                mutable.update { ManagementState(repo = it.repo, notice = "이 기기에서 로그아웃했습니다.") }
                refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == authGeneration) {
                    mutable.update { it.copy(error = "저장된 인증 정보를 지우지 못했습니다. 다시 시도하세요.") }
                }
            } finally {
                if (generation == authGeneration) mutable.update { it.copy(authenticating = false) }
            }
        }
    }

    fun submitReview(event: ReviewEvent, target: PullTarget) {
        writeAction(target) { before, snapshot, credential ->
            val user = before.viewer ?: throw GitHubException(GitHubError.AUTHENTICATION)
            if (snapshot.pull.state != "open" || snapshot.pull.merged) {
                throw GitHubException(GitHubError.VALIDATION)
            }
            if (event != ReviewEvent.COMMENT &&
                user.login.equals(snapshot.pull.author, ignoreCase = true)) {
                throw GitHubException(GitHubError.PERMISSION)
            }
            if (event != ReviewEvent.APPROVE && before.reviewBody.isBlank()) {
                throw GitHubException(GitHubError.VALIDATION)
            }
            api.review(before.repo, snapshot.pull.number, credential, target.headSha, event, before.reviewBody)
            mutable.update { it.copy(reviewBody = "", notice = "GitHub에 리뷰를 제출했습니다.") }
        }
    }

    fun submitSelfReview(target: PullTarget) {
        writeAction(target) { before, snapshot, credential ->
            val user = before.viewer ?: throw GitHubException(GitHubError.AUTHENTICATION)
            if (snapshot.pull.state != "open" || snapshot.pull.merged) {
                throw GitHubException(GitHubError.VALIDATION)
            }
            if (!user.login.equals(snapshot.pull.author, ignoreCase = true)) {
                throw GitHubException(GitHubError.PERMISSION)
            }
            api.review(before.repo, snapshot.pull.number, credential, target.headSha,
                ReviewEvent.COMMENT, selfReviewBody(before.reviewBody))
            mutable.update { it.copy(reviewBody = "", notice = "작성자 자기 검토를 GitHub에 기록했습니다.") }
        }
    }

    fun merge(target: PullTarget, method: String) {
        writeAction(target) { before, snapshot, credential ->
            val repository = api.repository(before.repo, credential)
            if (mergeBlockers(snapshot, before.viewer, repository).isNotEmpty() ||
                method !in repository.mergeMethods) throw GitHubException(GitHubError.VALIDATION)
            val result = api.merge(before.repo, snapshot.pull.number, credential, target.headSha, method)
            if (!result.merged) throw GitHubException(GitHubError.CONFLICT)
            mutable.update { it.copy(notice = "GitHub에서 병합을 완료했습니다.") }
        }
    }

    private fun writeAction(
        target: PullTarget,
        action: suspend (ManagementState, PullDetails, String) -> Unit,
    ) {
        val before = mutable.value
        val displayed = before.detail ?: return
        if (before.writing || before.loading || before.authenticating || before.error != null) return
        val credential = token
        if (credential == null || before.viewer == null) {
            mutable.update { it.copy(error = "리뷰와 병합에는 GitHub 사용자 인증이 필요합니다.") }
            return
        }
        if (displayed.pull.target(before.repo) != target) {
            mutable.update { it.copy(error = githubMessage(GitHubException(GitHubError.CONFLICT))) }
            return
        }
        readJob?.cancel()
        mutable.update { it.copy(writing = true, error = null, notice = null) }
        viewModelScope.launch {
            var submitted = false
            try {
                val snapshot = details(before.repo, displayed.pull.number, credential)
                if (snapshot.pull.target(before.repo) != target) {
                    throw GitHubException(GitHubError.CONFLICT)
                }
                action(before, snapshot, credential)
                submitted = true
                val refreshed = details(before.repo, displayed.pull.number, credential)
                mutable.update { it.copy(detail = refreshed, writing = false) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutable.update { it.copy(writing = false, error = if (submitted)
                    "제출 후 최신 상태를 가져오지 못했습니다. GitHub에서 결과를 확인한 뒤 새로고침하세요."
                    else if (error is GitHubException && error.kind == GitHubError.NETWORK)
                        "요청 결과를 확인하지 못했습니다. 다시 제출하기 전에 GitHub에서 처리 여부를 확인하세요."
                    else githubMessage(error)) }
            }
        }
    }
}
