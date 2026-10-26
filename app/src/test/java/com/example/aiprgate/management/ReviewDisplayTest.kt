package com.example.aiprgate.management

import com.example.aiprgate.github.GitHubUser
import com.example.aiprgate.github.RepoId
import com.example.aiprgate.github.RepositoryInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewDisplayTest {
    private val repository = RepositoryInfo(RepoId("jaezero", "AIPRGATE"), true, listOf("merge"))

    private fun blockers(state: String) = mergeBlockers(
        PullDetails(testPull().copy(mergeableState = state), emptyList(), 1, false,
            passingChecks(), emptyList(), emptyList()),
        GitHubUser("reviewer"), repository,
    )

    @Test
    fun behindBranchExplainsThatMainMustBeMergedIn() {
        val behind = blockers("behind")

        assertTrue(behind.any { it.startsWith("main의 최신 변경이 반영되지 않았습니다") })
        assertFalse(behind.contains("GitHub 병합 조건이 미충족입니다."))
        assertTrue(blockers("blocked").contains("GitHub 병합 조건이 미충족입니다."))
    }

    @Test
    fun aiCommentMarkdownIsShownAsPlainText() {
        assertEquals("AI 리뷰 의견\n판단 요약: 문제 없음",
            plainMarkdown("<h3>AI 리뷰 의견</h3>\n**판단 요약**: 문제 없음"))
        assertEquals("변경 코드 요약", plainMarkdown("### 변경 코드 요약"))
    }
}
