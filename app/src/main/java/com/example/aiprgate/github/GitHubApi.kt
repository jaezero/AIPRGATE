package com.example.aiprgate.github

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Deliberately not a data class: credentials and payloads must not appear in toString(). */
class GitHubRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String? = null,
)

class GitHubResponse(
    val status: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap(),
) {
    fun header(name: String): String? = headers.entries
        .firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}

fun interface GitHubTransport {
    suspend fun execute(request: GitHubRequest): GitHubResponse
}

/** Never follows redirects, including same-origin redirects of authenticated requests. */
class UrlConnectionGitHubTransport : GitHubTransport {
    override suspend fun execute(request: GitHubRequest): GitHubResponse =
        runInterruptible(Dispatchers.IO) {
            val uri = try {
                URI(request.url)
            } catch (_: Exception) {
                throw GitHubException(GitHubError.VALIDATION)
            }
            if (!isGitHubOrigin(uri)) throw GitHubException(GitHubError.VALIDATION)
            val connection = URL(request.url).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 20_000
                connection.requestMethod = request.method
                connection.useCaches = false
                request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
                request.body?.let { body ->
                    val bytes = body.toByteArray(Charsets.UTF_8)
                    connection.doOutput = true
                    connection.setFixedLengthStreamingMode(bytes.size)
                    connection.outputStream.use { it.write(bytes) }
                }
                val status = connection.responseCode
                // An error body is used only to recognize headerless secondary rate limits.
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                val headers = listOf("Link", "X-RateLimit-Remaining", "Retry-After")
                    .mapNotNull { name -> connection.getHeaderField(name)?.let { name to it } }.toMap()
                GitHubResponse(status, body, headers)
            } finally {
                connection.disconnect()
            }
        }
}

private fun isGitHubOrigin(uri: URI): Boolean =
    uri.scheme == "https" && uri.host == "api.github.com" &&
        uri.port == -1 && uri.rawUserInfo == null && uri.rawFragment == null

class GitHubApi(
    private val transport: GitHubTransport = UrlConnectionGitHubTransport(),
) : GitHubService {
    override suspend fun viewer(token: String): GitHubUser = io {
        requireToken(token)
        GitHubUser(obj(request("GET", "/user", token)).string("login"))
    }

    override suspend fun repository(repo: RepoId, token: String?): RepositoryInfo = io {
        val json = obj(request("GET", repoPath(repo), token))
        val permissions = json.optionalObject("permissions")
        RepositoryInfo(
            repo,
            permissions?.optionalBoolean("push") ?: false,
            listOf("merge", "squash", "rebase").filter {
                // GitHub omits administrative capabilities from anonymous responses.
                json.optionalBoolean(if (it == "merge") "allow_merge_commit" else "allow_${it}_merge") == true
            },
        )
    }

    override suspend fun pulls(
        repo: RepoId, token: String?, state: String, page: Int,
    ): Page<PullRequest> = io {
        validate(state in setOf("open", "closed", "all"))
        validate(page > 0)
        val response = request(
            "GET", "${repoPath(repo)}/pulls?state=$state&sort=updated&direction=desc&per_page=30&page=$page",
            token,
        )
        Page(array(response).objects(::parsePull), hasNext(response, page))
    }

    override suspend fun pull(repo: RepoId, number: Int, token: String?): PullRequest = io {
        parsePull(obj(request("GET", pullPath(repo, number), token)))
    }

    override suspend fun files(
        repo: RepoId, number: Int, token: String?, page: Int,
    ): Page<PullFile> = io {
        validate(page > 0)
        val response = request(
            "GET", "${pullPath(repo, number)}/files?per_page=30&page=$page", token,
        )
        Page(array(response).objects { json ->
            PullFile(
                json.string("filename"), json.string("status"), json.nonnegativeInt("additions"),
                json.nonnegativeInt("deletions"), json.nullableString("patch"), json.string("blob_url"),
            )
        }, hasNext(response, page))
    }

    override suspend fun checks(repo: RepoId, sha: String, token: String?): List<CheckRun> = io {
        validateSha(sha)
        allPages("${repoPath(repo)}/commits/$sha/check-runs?filter=latest", token, "check_runs") { json ->
            val output = json.optionalObject("output")
            CheckRun(
                json.positiveLong("id"), json.string("name"), json.sha("head_sha"),
                json.string("status"), json.nullableString("conclusion"),
                json.nullableString("details_url")?.takeIf { it.isNotBlank() }
                    ?: json.nullableString("html_url") ?: "",
                listOfNotNull(output?.nullableString("title"), output?.nullableString("summary"),
                    output?.nullableString("text")).filter { it.isNotBlank() }.distinct().joinToString("\n\n"),
                json.getJSONObject("app").positiveLong("id"),
            )
        }.filter { it.headSha.equals(sha, ignoreCase = true) }
            .groupBy { it.appId to it.name }
            .values.map { runs -> runs.maxBy { it.id } }
    }

    override suspend fun statuses(repo: RepoId, sha: String, token: String?): List<CommitStatus> = io {
        validateSha(sha)
        allPages("${repoPath(repo)}/commits/$sha/statuses", token) { json ->
            CommitStatus(
                json.positiveLong("id"), json.string("context"), json.string("state"),
                json.nullableString("description") ?: "", json.nullableString("target_url") ?: "",
            )
        }.groupBy { it.context }.values.map { statuses -> statuses.maxBy { it.id } }
    }

    override suspend fun reviews(repo: RepoId, number: Int, token: String?): List<PullReview> = io {
        allPages("${pullPath(repo, number)}/reviews", token, parse = ::parseReview)
            .distinctBy { it.id }
    }

    override suspend fun review(
        repo: RepoId, number: Int, token: String, sha: String, event: ReviewEvent, body: String,
    ): PullReview = io {
        requireToken(token)
        validateSha(sha)
        val payload = JSONObject().put("commit_id", sha).put("event", event.name).put("body", body)
        parseReview(obj(request("POST", "${pullPath(repo, number)}/reviews", token, payload.toString())))
    }

    override suspend fun merge(
        repo: RepoId, number: Int, token: String, sha: String, method: String,
    ): MergeResult = io {
        requireToken(token)
        validateSha(sha)
        validate(method in setOf("merge", "squash", "rebase"))
        val payload = JSONObject().put("sha", sha).put("merge_method", method)
        val json = obj(request("PUT", "${pullPath(repo, number)}/merge", token, payload.toString()))
        val merged = json.boolean("merged")
        val resultSha = json.nullableString("sha")
        if (merged) {
            if (resultSha == null) invalid()
            responseSha(resultSha)
        }
        // Do not propagate server prose into UI/logs.
        MergeResult(merged, if (merged) "Merged" else "Not merged", resultSha)
    }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try {
            currentCoroutineContext().ensureActive()
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: GitHubException) {
            throw error
        } catch (_: JSONException) {
            throw GitHubException(GitHubError.INVALID_RESPONSE)
        } catch (_: IOException) {
            currentCoroutineContext().ensureActive()
            throw GitHubException(GitHubError.NETWORK)
        }
    }

    private suspend fun request(
        method: String, path: String, token: String?, body: String? = null,
    ): GitHubResponse {
        if (token != null) requireToken(token)
        val headers = mutableMapOf(
            "Accept" to "application/vnd.github+json",
            "X-GitHub-Api-Version" to "2022-11-28",
            "User-Agent" to "AiPrGate-Android",
        )
        token?.let { headers["Authorization"] = "Bearer $it" }
        if (body != null) headers["Content-Type"] = "application/json; charset=utf-8"
        currentCoroutineContext().ensureActive()
        val response = transport.execute(GitHubRequest(method, "https://api.github.com$path", headers, body))
        currentCoroutineContext().ensureActive()
        if (response.status !in 200..299) {
            val kind = when (response.status) {
                401 -> GitHubError.AUTHENTICATION
                403 -> if (response.header("X-RateLimit-Remaining") == "0" ||
                    response.header("Retry-After") != null || isRateLimit(response)
                ) GitHubError.RATE_LIMIT else GitHubError.PERMISSION
                404 -> GitHubError.NOT_FOUND
                405, 409 -> GitHubError.CONFLICT
                400, 422 -> GitHubError.VALIDATION
                429 -> GitHubError.RATE_LIMIT
                in 500..599 -> GitHubError.SERVER
                else -> GitHubError.INVALID_RESPONSE
            }
            throw GitHubException(kind, response.status)
        }
        return response
    }

    private suspend fun <T> allPages(
        path: String, token: String?, field: String? = null, parse: (JSONObject) -> T,
    ): List<T> {
        val result = mutableListOf<T>()
        var page = 1
        do {
            val separator = if ('?' in path) '&' else '?'
            val response = request("GET", "$path${separator}per_page=100&page=$page", token)
            val values = if (field == null) array(response) else obj(response).getJSONArray(field)
            result.addAll(values.objects(parse))
            val next = hasNext(response, page)
            if (!next) break
            if (page == Int.MAX_VALUE) invalid()
            page++
        } while (true)
        return result
    }

    /** Links are only pagination signals, never credential-bearing request destinations. */
    private fun hasNext(response: GitHubResponse, page: Int): Boolean {
        val link = response.header("Link") ?: return false
        var next = false
        link.split(',').forEach { part ->
            val match = Regex("""^\s*<([^>]+)>\s*;\s*rel="([^"]+)"\s*$""").matchEntire(part)
                ?: invalid()
            val uri = try {
                URI(match.groupValues[1])
            } catch (_: Exception) {
                invalid()
            }
            if (!isGitHubOrigin(uri)) invalid()
            if ("next" in match.groupValues[2].split(' ')) {
                val pages = uri.rawQuery?.split('&')?.filter { it.startsWith("page=") } ?: emptyList()
                if (next || page == Int.MAX_VALUE || pages.size != 1 ||
                    pages.single().removePrefix("page=").toIntOrNull() != page + 1
                ) invalid()
                next = true
            }
        }
        return next
    }

    private fun isRateLimit(response: GitHubResponse): Boolean {
        val message = try {
            JSONObject(response.body).opt("message") as? String
        } catch (_: JSONException) {
            null
        } ?: return false
        return message.contains("rate limit", ignoreCase = true) ||
            message.contains("abuse detection", ignoreCase = true)
    }

    private fun parsePull(json: JSONObject): PullRequest {
        val head = json.getJSONObject("head")
        val base = json.getJSONObject("base")
        val mergeCommit = json.nullableString("merge_commit_sha")
        mergeCommit?.let(::responseSha)
        val state = json.string("state")
        if (state !in setOf("open", "closed")) invalid()
        return PullRequest(
            json.positiveInt("number"), json.string("title", allowEmpty = true),
            json.nullableString("body") ?: "", json.getJSONObject("user").string("login"),
            state, json.boolean("draft"),
            json.optionalBoolean("merged") ?: (json.nullableString("merged_at") != null),
            head.sha("sha"), base.sha("sha"), head.string("ref"), base.string("ref"),
            json.string("html_url"), json.string("updated_at"), json.optionalBoolean("mergeable"),
            json.nullableString("mergeable_state") ?: "unknown", mergeCommit,
            if (json.has("changed_files")) json.nonnegativeInt("changed_files") else 0,
        )
    }

    private fun parseReview(json: JSONObject): PullReview = PullReview(
        json.positiveLong("id"), json.getJSONObject("user").string("login"), json.string("state"),
        json.nullableString("body") ?: "", json.sha("commit_id"),
        json.nullableString("submitted_at") ?: "", json.string("html_url"),
    )

    private fun repoPath(repo: RepoId): String {
        validate(listOf(repo.owner, repo.name).all {
            it.matches(Regex("[A-Za-z0-9_.-]+")) && it != "." && it != ".."
        })
        return "/repos/${repo.owner}/${repo.name}"
    }

    private fun pullPath(repo: RepoId, number: Int): String {
        validate(number > 0)
        return "${repoPath(repo)}/pulls/$number"
    }

    private fun requireToken(token: String) {
        // Tokens are opaque, printable ASCII without whitespace or header delimiters.
        validate(token.isNotBlank() && token.all { it.code in 33..126 })
    }

    private fun validateSha(sha: String) = validate(sha.matches(SHA))
    private fun responseSha(sha: String): String {
        if (!sha.matches(SHA)) invalid()
        return sha
    }

    private fun obj(response: GitHubResponse) = JSONObject(response.body)
    private fun array(response: GitHubResponse) = JSONArray(response.body)
    private fun <T> JSONArray.objects(parse: (JSONObject) -> T): List<T> =
        (0 until length()).map { parse(getJSONObject(it)) }

    private fun JSONObject.string(key: String, allowEmpty: Boolean = false): String {
        val value = get(key)
        if (value !is String || (!allowEmpty && value.isBlank())) invalid()
        return value
    }

    private fun JSONObject.nullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return string(key, allowEmpty = true)
    }

    private fun JSONObject.boolean(key: String): Boolean {
        val value = get(key)
        if (value !is Boolean) invalid()
        return value
    }

    private fun JSONObject.optionalBoolean(key: String): Boolean? =
        if (!has(key) || isNull(key)) null else boolean(key)

    private fun JSONObject.optionalObject(key: String): JSONObject? =
        if (!has(key) || isNull(key)) null else getJSONObject(key)

    private fun JSONObject.positiveLong(key: String): Long {
        val value = get(key)
        if (value !is Int && value !is Long) invalid()
        val number = (value as Number).toLong()
        if (number <= 0) invalid()
        return number
    }

    private fun JSONObject.nonnegativeInt(key: String): Int {
        val value = get(key)
        if (value !is Int && value !is Long) invalid()
        val number = (value as Number).toLong()
        if (number !in 0..Int.MAX_VALUE.toLong()) invalid()
        return number.toInt()
    }

    private fun JSONObject.positiveInt(key: String): Int =
        nonnegativeInt(key).also { if (it == 0) invalid() }

    private fun JSONObject.sha(key: String): String = responseSha(string(key))

    private fun validate(valid: Boolean) {
        if (!valid) throw GitHubException(GitHubError.VALIDATION)
    }

    private fun invalid(): Nothing = throw GitHubException(GitHubError.INVALID_RESPONSE)

    private companion object {
        val SHA = Regex("[a-fA-F0-9]{40}")
    }
}
