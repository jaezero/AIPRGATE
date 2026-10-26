package com.example.aiprgate.management

import com.example.aiprgate.auth.DeviceCode
import com.example.aiprgate.github.*

enum class ManagementScreen { LIST, CONNECTION, DETAIL }
enum class DetailTab { SUMMARY, FILES, CHECKS, REVIEWS }

data class PullTarget(
    val repository: RepoId,
    val number: Int,
    val headSha: String,
    val baseSha: String,
    val baseRef: String,
)

fun PullRequest.target(repository: RepoId) =
    PullTarget(repository, number, headSha, baseSha, baseRef)

sealed interface ManagementAction {
    data object Connect : ManagementAction
    data object Back : ManagementAction
    data object Refresh : ManagementAction
    data object MorePulls : ManagementAction
    data object MoreFiles : ManagementAction
    data object CancelLogin : ManagementAction
    data object Logout : ManagementAction
    data class Repository(val value: String) : ManagementAction
    data class Filter(val value: String) : ManagementAction
    data class OpenPull(val number: Int) : ManagementAction
    data class Tab(val value: DetailTab) : ManagementAction
    data class ReviewBody(val value: String) : ManagementAction
    data class TokenLogin(val token: String) : ManagementAction
    data class DeviceLogin(val clientId: String) : ManagementAction
    data class SubmitReview(val event: ReviewEvent, val target: PullTarget) : ManagementAction
    data class SelfReview(val target: PullTarget) : ManagementAction
    data class Merge(val target: PullTarget, val method: String) : ManagementAction
}

data class PullDetails(
    val pull: PullRequest,
    val files: List<PullFile>,
    val filePage: Int,
    val hasMoreFiles: Boolean,
    val checks: List<CheckRun>,
    val statuses: List<CommitStatus>,
    val reviews: List<PullReview>,
)

data class ManagementState(
    val screen: ManagementScreen = ManagementScreen.LIST,
    val repo: RepoId = RepoId("jaezero", "AIPRGATE"),
    val repository: RepositoryInfo? = null,
    val viewer: GitHubUser? = null,
    val pulls: List<PullRequest> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = false,
    val filter: String = "open",
    val detail: PullDetails? = null,
    val tab: DetailTab = DetailTab.SUMMARY,
    val loading: Boolean = false,
    val authenticating: Boolean = false,
    val writing: Boolean = false,
    val deviceCode: DeviceCode? = null,
    val error: String? = null,
    val notice: String? = null,
    val reviewBody: String = "",
    val summaries: Map<Int, PullSummary> = emptyMap(),
)

data class PullSummary(val checks: List<CheckRun>, val approvals: Int, val error: String? = null)

val REQUIRED_CHECKS = listOf("pr-info", "build-test", "quality", "secrets", "quality-gate")

fun currentChecks(detail: PullDetails): List<CheckRun> = detail.checks.filter {
    it.headSha == detail.pull.headSha || it.headSha == detail.pull.mergeCommitSha
}.groupBy { it.name }.values.map { checks -> checks.maxBy { it.id } }.sortedBy { it.name }

fun latestDecisiveReviews(reviews: List<PullReview>): List<PullReview> =
    reviews.filter { it.state in setOf("APPROVED", "CHANGES_REQUESTED", "DISMISSED") }
        .groupBy { it.author.lowercase() }.values.map { it.maxBy { review -> review.id } }

fun latestApprovals(detail: PullDetails): List<PullReview> =
    latestDecisiveReviews(detail.reviews).filter {
        it.state == "APPROVED" && it.commitSha == detail.pull.headSha &&
            !it.author.equals(detail.pull.author, ignoreCase = true)
    }

// Solo operation: GitHub rejects an author's APPROVE, so the author records a
// marked COMMENT review on the exact head commit instead. It is never counted
// as another reviewer's approval.
const val SELF_REVIEW_MARKER = "[AiPrGate 작성자 자기 검토]"
val SELF_REVIEW_CHECKLIST = listOf(
    "요구사항 적합성", "테스트 근거", "AI 사용 기록", "민감정보 없음", "권한·저장·예외 처리",
)

fun selfReviewBody(note: String): String = buildString {
    appendLine(SELF_REVIEW_MARKER)
    SELF_REVIEW_CHECKLIST.forEach { appendLine("- [x] $it") }
    if (note.isNotBlank()) append("\n").append(note.trim())
}.trim()

fun isSelfReview(review: PullReview, pull: PullRequest): Boolean =
    review.state == "COMMENTED" && review.body.startsWith(SELF_REVIEW_MARKER) &&
        review.author.equals(pull.author, ignoreCase = true)

fun latestSelfReviews(detail: PullDetails): List<PullReview> =
    detail.reviews.filter { isSelfReview(it, detail.pull) && it.commitSha == detail.pull.headSha }

private fun workflowRun(check: CheckRun): String? =
    Regex("^https://github\\.com/[^/]+/[^/]+/actions/runs/(\\d+)(?:/|$)")
        .find(check.url)?.groupValues?.get(1)

fun gateBlockers(detail: PullDetails): List<String> = buildList {
    val latest = currentChecks(detail).filter { it.appId == 15368L }.associateBy { it.name }
    for (name in REQUIRED_CHECKS) {
        val check = latest[name]
        if (check == null) add("$name 결과가 없습니다.")
        else if (check.status != "completed" || check.conclusion != "success") {
            add("$name 검사가 통과하지 않았습니다.")
        }
    }
    val runs = REQUIRED_CHECKS.mapNotNull { latest[it]?.let(::workflowRun) }
    if (runs.size != REQUIRED_CHECKS.size || runs.distinct().size != 1) {
        add("네 검사와 게이트가 동일한 실행인지 확인할 수 없습니다.")
    }
    if (detail.statuses.any { it.state !in setOf("success") }) {
        add("추가 커밋 상태 검사에 대기 또는 실패 결과가 있습니다.")
    }
}

// A finished PR is not "blocked": showing merge blockers after a successful
// merge would read as a failure, so the merge area reports the final state.
fun finishedPullNotice(pr: PullRequest): Pair<String, String>? = when {
    pr.merged -> "병합 완료" to "이 PR은 GitHub에서 병합되었습니다.\n병합 기록은 GitHub에서 확인하세요."
    pr.state != "open" -> "닫힌 PR" to "병합하지 않고 닫힌 PR입니다."
    else -> null
}

fun mergeBlockers(
    detail: PullDetails, viewer: GitHubUser?, repository: RepositoryInfo?,
): List<String> = buildList {
    val pr = detail.pull
    if (viewer == null) add("GitHub 사용자 인증이 필요합니다.")
    if (repository?.canPush != true) add("저장소 쓰기 권한을 확인할 수 없습니다.")
    if (pr.state != "open" || pr.merged) add("열린 PR만 병합할 수 있습니다.")
    if (pr.draft) add("초안 PR을 리뷰 가능한 상태로 전환하세요.")
    if (pr.mergeable != true) add("GitHub가 병합 가능 여부를 확인하지 못했거나 충돌이 있습니다.")
    if (pr.mergeableState != "clean") add("GitHub 병합 조건이 미충족입니다.")
    addAll(gateBlockers(detail))
    if (latestApprovals(detail).isEmpty() && latestSelfReviews(detail).isEmpty()) {
        add("최신 커밋에 다른 리뷰어 승인 또는 작성자 자기 검토가 필요합니다.")
    }
    if (latestDecisiveReviews(detail.reviews).any { it.state == "CHANGES_REQUESTED" }) {
        add("해결되지 않은 수정 요청이 있습니다.")
    }
    if (repository?.mergeMethods.isNullOrEmpty()) add("사용 가능한 병합 방법이 없습니다.")
}

fun githubMessage(error: Throwable): String = when (error) {
    is GitHubException -> when (error.kind) {
        GitHubError.AUTHENTICATION -> "인증이 만료되었거나 토큰이 올바르지 않습니다. 다시 연결하세요."
        GitHubError.PERMISSION -> "이 작업에 필요한 GitHub 권한이 없습니다. 저장소 접근 권한을 확인하세요."
        GitHubError.NOT_FOUND -> "저장소 또는 PR을 찾을 수 없습니다. 이름과 접근 권한을 확인하세요."
        GitHubError.RATE_LIMIT -> "GitHub 요청 한도에 도달했습니다. 잠시 후 새로고침하세요."
        GitHubError.CONFLICT -> "변경 내용이나 병합 조건이 달라졌습니다. 새로고침 후 다시 검토하세요."
        GitHubError.VALIDATION -> "GitHub가 요청을 받아들이지 않았습니다. 권한·리뷰·보호 조건을 확인하세요."
        GitHubError.NETWORK -> "GitHub에 연결하지 못했습니다. 인터넷 연결을 확인하고 다시 시도하세요."
        GitHubError.SERVER -> "GitHub 서버가 요청을 처리하지 못했습니다. 잠시 후 다시 시도하세요."
        GitHubError.INVALID_RESPONSE -> "GitHub 응답을 확인하지 못했습니다. 통과로 판단하지 않고 다시 조회하세요."
    }
    else -> "요청을 완료하지 못했습니다. 연결 설정을 확인하고 다시 시도하세요."
}
