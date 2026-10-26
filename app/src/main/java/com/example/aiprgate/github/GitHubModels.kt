package com.example.aiprgate.github

data class RepoId(val owner: String, val name: String) {
    val fullName: String get() = "$owner/$name"

    companion object {
        fun parse(input: String): RepoId? {
            val value = input.trim().removePrefix("https://github.com/").removeSuffix("/")
                .removeSuffix(".git")
            val parts = value.split("/")
            if (parts.size != 2 || parts.any { !it.matches(Regex("[A-Za-z0-9_.-]+")) ||
                    it == "." || it == ".." }) return null
            return RepoId(parts[0], parts[1])
        }
    }
}

data class GitHubUser(val login: String)

data class RepositoryInfo(
    val id: RepoId,
    val canPush: Boolean,
    val mergeMethods: List<String>,
)

data class PullRequest(
    val number: Int,
    val title: String,
    val body: String,
    val author: String,
    val state: String,
    val draft: Boolean,
    val merged: Boolean,
    val headSha: String,
    val baseSha: String,
    val headRef: String,
    val baseRef: String,
    val url: String,
    val updatedAt: String,
    val mergeable: Boolean?,
    val mergeableState: String,
    val mergeCommitSha: String?,
    val changedFiles: Int,
)

data class PullFile(
    val name: String,
    val status: String,
    val additions: Int,
    val deletions: Int,
    val patch: String?,
    val url: String,
)

data class CheckRun(
    val id: Long,
    val name: String,
    val headSha: String,
    val status: String,
    val conclusion: String?,
    val url: String,
    val summary: String,
    val appId: Long,
)

data class CommitStatus(
    val id: Long,
    val context: String,
    val state: String,
    val description: String,
    val url: String,
)

data class PullReview(
    val id: Long,
    val author: String,
    val state: String,
    val body: String,
    val commitSha: String,
    val submittedAt: String,
    val url: String,
)

data class Page<T>(val items: List<T>, val hasNext: Boolean)
data class MergeResult(val merged: Boolean, val message: String, val sha: String?)
enum class ReviewEvent { COMMENT, REQUEST_CHANGES, APPROVE }

enum class GitHubError {
    AUTHENTICATION, PERMISSION, NOT_FOUND, RATE_LIMIT, CONFLICT, VALIDATION,
    NETWORK, SERVER, INVALID_RESPONSE,
}

class GitHubException(val kind: GitHubError, val status: Int? = null) : Exception(kind.name)

interface GitHubService {
    suspend fun viewer(token: String): GitHubUser
    suspend fun repository(repo: RepoId, token: String?): RepositoryInfo
    suspend fun pulls(repo: RepoId, token: String?, state: String, page: Int): Page<PullRequest>
    suspend fun pull(repo: RepoId, number: Int, token: String?): PullRequest
    suspend fun files(repo: RepoId, number: Int, token: String?, page: Int): Page<PullFile>
    suspend fun checks(repo: RepoId, sha: String, token: String?): List<CheckRun>
    suspend fun statuses(repo: RepoId, sha: String, token: String?): List<CommitStatus>
    suspend fun reviews(repo: RepoId, number: Int, token: String?): List<PullReview>
    suspend fun review(
        repo: RepoId, number: Int, token: String, sha: String, event: ReviewEvent, body: String,
    ): PullReview
    suspend fun merge(repo: RepoId, number: Int, token: String, sha: String, method: String): MergeResult
}
