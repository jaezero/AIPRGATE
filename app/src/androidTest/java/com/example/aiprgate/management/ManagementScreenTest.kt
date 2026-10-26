package com.example.aiprgate.management

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.aiprgate.github.*
import com.example.aiprgate.auth.DeviceCode
import com.example.aiprgate.ui.theme.AiPrGateTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ManagementScreenTest {
    @get:Rule val compose = createComposeRule()
    private val sha = "a".repeat(40)
    private val target = PullTarget(RepoId("jaezero", "AIPRGATE"), 7, sha, "b".repeat(40), "main")
    private val pr = PullRequest(
        7, "AI 생성 코드의 최신 변경과 검사 근거 확인", "목적: 입력 검증 보완\n\nAI 사용: yes\n테스트: 단위 테스트 통과",
        "author", "open", false, false, sha, "b".repeat(40), "feature/input", "main",
        "https://github.com/jaezero/AIPRGATE/pull/7", "2026-10-25T10:00:00Z", true, "clean", null, 1,
    )
    private val checks = REQUIRED_CHECKS.mapIndexed { i, name ->
        CheckRun(i + 1L, name, sha, "completed", "success",
            "https://github.com/jaezero/AIPRGATE/actions/runs/42/job/${i + 1}",
            "합성 UI 검증 데이터 · 실제 GitHub 실행 결과가 아닙니다.", 15368)
    }
    private val detail = PullDetails(pr, listOf(PullFile(
        "app/src/main/java/com/example/aiprgate/Example.kt", "modified", 2, 1,
        "@@ -1,2 +1,3 @@\n-oldValue\n+newValue\n+require(value.isNotBlank())",
        "https://github.com/jaezero/AIPRGATE",
    )), 1, false, checks, emptyList(), listOf(PullReview(
        1, "reviewer", "APPROVED", "합성 UI 검증용 리뷰입니다.", sha,
        "2026-10-25T10:00:00Z", "https://github.com/jaezero/AIPRGATE",
    )))
    private fun base() = ManagementState(
        viewer = GitHubUser("reviewer"),
        repository = RepositoryInfo(RepoId("jaezero", "AIPRGATE"), true, listOf("merge", "squash")),
        pulls = listOf(pr), detail = detail,
        notice = "UI 시험 데이터입니다.\n실제 리뷰·병합은 실행하지 않습니다.",
    )

    @Test fun listNavigationAndSampleEmitActualActions() {
        val actions = mutableListOf<ManagementAction>()
        var sample = false
        compose.setContent {
            AiPrGateTheme(management = true) { ManagementScreen(base(), actions::add, {}, { sample = true }) }
        }
        compose.onNodeWithTag("pull-7").performClick()
        compose.runOnIdle { assertEquals(ManagementAction.OpenPull(7), actions.single()) }
        compose.onNodeWithTag("sample-open").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(sample) }
    }

    @Test fun approvalRequiresExplicitConfirmationAndUsesDisplayedSha() {
        val actions = mutableListOf<ManagementAction>()
        compose.setContent {
            AiPrGateTheme(management = true) {
                ManagementScreen(base().copy(screen = ManagementScreen.DETAIL, tab = DetailTab.REVIEWS),
                    actions::add, {}, {})
            }
        }
        compose.onNodeWithTag("review-approve").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
        compose.onNodeWithTag("review-confirm").performClick()
        compose.runOnIdle {
            assertEquals(ManagementAction.SubmitReview(ReviewEvent.APPROVE, target), actions.single())
        }
    }

    @Test fun authorRecordsSelfReviewOnlyAfterConfirmation() {
        val actions = mutableListOf<ManagementAction>()
        compose.setContent {
            AiPrGateTheme(management = true) {
                ManagementScreen(base().copy(screen = ManagementScreen.DETAIL, tab = DetailTab.REVIEWS,
                    viewer = GitHubUser("author")), actions::add, {}, {})
            }
        }
        compose.onNodeWithTag("review-approve").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("self-review-open").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
        compose.onNodeWithTag("self-review-confirm").performClick()
        compose.runOnIdle { assertEquals(ManagementAction.SelfReview(target), actions.single()) }
    }

    @Test fun mergeNeedsConfirmationAndFailedCheckDisablesIt() {
        val actions = mutableListOf<ManagementAction>()
        val state = mutableStateOf(base().copy(screen = ManagementScreen.DETAIL, tab = DetailTab.REVIEWS))
        compose.setContent {
            AiPrGateTheme(management = true) { ManagementScreen(state.value, actions::add, {}, {}) }
        }
        compose.onNodeWithTag("merge-open").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
        compose.onNodeWithTag("merge-confirm").performClick()
        compose.runOnIdle {
            assertEquals(ManagementAction.Merge(target, "merge"), actions.single())
            state.value = state.value.copy(detail = detail.copy(checks =
                checks.map { it.copy(conclusion = "failure") }))
        }
        compose.onNodeWithTag("merge-open").performScrollTo().assertIsNotEnabled()
    }

    @Test fun retargetingClosesOldMergeConfirmationWithoutSubmitting() {
        val actions = mutableListOf<ManagementAction>()
        val state = mutableStateOf(base().copy(screen = ManagementScreen.DETAIL, tab = DetailTab.REVIEWS))
        compose.setContent {
            AiPrGateTheme(management = true) { ManagementScreen(state.value, actions::add, {}, {}) }
        }
        compose.onNodeWithTag("merge-open").performScrollTo().performClick()
        compose.onNodeWithTag("merge-confirm").assertExists()
        compose.runOnIdle {
            state.value = state.value.copy(detail = detail.copy(pull = pr.copy(baseRef = "release")))
        }
        compose.onNodeWithTag("merge-confirm").assertDoesNotExist()
        compose.runOnIdle { assertTrue(actions.isEmpty()) }
    }

    @Test fun deviceCodeCanBeReadOpenedAndCancelledWithoutExternalAuthentication() {
        val actions = mutableListOf<ManagementAction>()
        val urls = mutableListOf<String>()
        val code = DeviceCode("invalid-device-code", "DEMO-CODE", "https://github.com/login/device", 5, 900)
        compose.setContent {
            AiPrGateTheme(management = true) {
                ManagementScreen(
                    base().copy(screen = ManagementScreen.CONNECTION, viewer = null,
                        authenticating = true, deviceCode = code),
                    actions::add, urls::add, {},
                )
            }
        }
        compose.onNodeWithText(code.userCode).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("GitHub 인증 화면 열기").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(code.verificationUri), urls) }
        compose.onNodeWithText("인증 취소").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(ManagementAction.CancelLogin), actions) }
    }

    @Test fun captureEveryManagementSurfaceInBothThemes() {
        val state = mutableStateOf(base())
        val dark = mutableStateOf(false)
        compose.setContent {
            AiPrGateTheme(darkTheme = dark.value, dynamicColor = false, management = true) {
                ManagementScreen(state.value, {}, {}, {})
            }
        }
        val variants = listOf(
            "list" to base(),
            "empty" to base().copy(pulls = emptyList(), detail = null),
            "error" to base().copy(error = "연결을 확인하고 새로고침하세요."),
            "loading" to base().copy(loading = true),
            "connection" to base().copy(screen = ManagementScreen.CONNECTION, viewer = null),
            "signed-in" to base().copy(screen = ManagementScreen.CONNECTION),
            "device-code" to base().copy(screen = ManagementScreen.CONNECTION, viewer = null,
                authenticating = true, deviceCode = DeviceCode(
                    "invalid-test-device", "DEMO-CODE", "https://github.com/login/device", 5, 900)),
            "summary" to base().copy(screen = ManagementScreen.DETAIL),
            "files" to base().copy(screen = ManagementScreen.DETAIL, tab = DetailTab.FILES),
            "checks" to base().copy(screen = ManagementScreen.DETAIL, tab = DetailTab.CHECKS),
            "failed-checks" to base().copy(screen = ManagementScreen.DETAIL, tab = DetailTab.CHECKS,
                detail = detail.copy(checks = checks.map { it.copy(conclusion = "failure") })),
            "reviews" to base().copy(screen = ManagementScreen.DETAIL, tab = DetailTab.REVIEWS),
            "public-reviews" to base().copy(screen = ManagementScreen.DETAIL, tab = DetailTab.REVIEWS,
                viewer = null, repository = base().repository!!.copy(canPush = false)),
            "writing" to base().copy(screen = ManagementScreen.DETAIL, tab = DetailTab.REVIEWS, writing = true),
            "self-review" to base().copy(screen = ManagementScreen.DETAIL, tab = DetailTab.REVIEWS,
                viewer = GitHubUser("author")),
        )
        for (isDark in listOf(false, true)) {
            for ((name, value) in variants) {
                compose.runOnIdle { dark.value = isDark; state.value = value }
                compose.waitForIdle()
                compose.onNodeWithTag("management-content").performScrollToIndex(0)
                capture("$name-${if (isDark) "dark" else "light"}")
                if (name in setOf("connection", "files", "checks", "failed-checks", "reviews", "public-reviews")) {
                    if (name == "connection") compose.onNodeWithTag("token-connect").performScrollTo()
                    else compose.onNodeWithTag("management-content").performScrollToIndex(lastIndex(name, value))
                    compose.waitForIdle()
                    capture("$name-end-${if (isDark) "dark" else "light"}")
                }
                if (name == "reviews") {
                    compose.onNodeWithTag("review-approve").performScrollTo().performClick()
                    capture("approval-dialog-${if (isDark) "dark" else "light"}")
                    compose.onNodeWithTag("review-confirm").performClick()
                    compose.onNodeWithTag("merge-open").performScrollTo().performClick()
                    capture("merge-dialog-${if (isDark) "dark" else "light"}")
                    compose.onNodeWithTag("merge-confirm").performClick()
                }
                if (name == "self-review") {
                    compose.onNodeWithTag("self-review-open").performScrollTo().performClick()
                    capture("self-review-dialog-${if (isDark) "dark" else "light"}")
                    compose.onNodeWithText("취소").performClick()
                }
                if (name == "device-code") {
                    compose.onNodeWithText("DEMO-CODE").performScrollTo().assertIsDisplayed()
                    capture("device-code-value-${if (isDark) "dark" else "light"}")
                    compose.onNodeWithText("인증 취소").performScrollTo().assertIsDisplayed()
                    capture("device-code-end-${if (isDark) "dark" else "light"}")
                    compose.onNodeWithText("인증 취소").performScrollTo().assertIsDisplayed()
                    capture("device-code-end-${if (isDark) "dark" else "light"}")
                }
            }
        }
    }

    private fun lastIndex(name: String, value: ManagementState): Int {
        val notices = (if (value.notice != null) 1 else 0) + (if (value.error != null) 1 else 0)
        return notices + when (name) {
            "connection" -> 0
            "files" -> 1
            "checks", "failed-checks" -> checks.size + 1
            "reviews", "public-reviews" -> 4
            else -> 0
        }
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "management-evidence").apply { mkdirs() }
        compose.waitForIdle()
        // PixelCopy through Compose waits for the rendered frame; UiAutomation
        // screenshots can still contain the previous compositor buffer.
        val dialog = compose.onAllNodes(isDialog()).fetchSemanticsNodes().isNotEmpty()
        val node = if (dialog) compose.onNode(isDialog()) else compose.onRoot()
        val image = node.captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use {
            check(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
