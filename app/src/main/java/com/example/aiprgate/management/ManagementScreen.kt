package com.example.aiprgate.management

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.aiprgate.github.*
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ManagementScreen(
    state: ManagementState,
    onAction: (ManagementAction) -> Unit,
    onOpenUrl: (String) -> Unit,
    onSample: () -> Unit,
) {
    val busy = state.writing || state.authenticating
    BackHandler(state.screen != ManagementScreen.LIST) {
        if (!state.writing) onAction(ManagementAction.Back)
    }
    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
        Column(
            modifier = Modifier.align(Alignment.TopCenter).fillMaxHeight().widthIn(max = 840.dp)
                .fillMaxWidth().padding(horizontal = 20.dp).testTag("management-root"),
        ) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when (state.screen) {
                            ManagementScreen.LIST -> "PR 관리"
                            ManagementScreen.CONNECTION -> "GitHub 연결"
                            ManagementScreen.DETAIL -> state.detail?.pull?.number?.let { "PR #$it" } ?: "PR 상세"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(state.repo.fullName, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.screen != ManagementScreen.LIST) {
                    TextButton(enabled = !state.writing, colors = quietActionColors(),
                        onClick = { onAction(ManagementAction.Back) }) {
                        Text("목록으로")
                    }
                } else {
                    TextButton(enabled = !busy, colors = quietActionColors(),
                        onClick = { onAction(ManagementAction.Connect) }) {
                        Text(if (state.viewer == null) "연결 설정" else "@${state.viewer.login}")
                    }
                }
            }
            if (state.screen != ManagementScreen.CONNECTION) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (state.viewer == null) "공개 저장소 · 읽기 전용" else "GitHub 사용자로 연결됨",
                        style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f),
                    )
                    TextButton(enabled = !busy && !state.loading, colors = quietActionColors(), onClick = {
                        onAction(ManagementAction.Refresh)
                    }) { Text("새로고침") }
                }
            }
            if (state.screen == ManagementScreen.LIST) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("open" to "열림", "closed" to "닫힘·병합", "all" to "전체").forEach { (key, title) ->
                        FilterChip(selected = state.filter == key, enabled = !busy && !state.loading,
                            colors = choiceColors(),
                            onClick = { onAction(ManagementAction.Filter(key)) }, label = { Text(title) })
                    }
                }
            }
            if (state.screen == ManagementScreen.DETAIL && state.detail != null) {
                PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
                    DetailTab.entries.forEach { tab ->
                        Tab(selected = state.tab == tab, onClick = {
                            onAction(ManagementAction.Tab(tab))
                        }, text = { Text(tabTitle(tab)) })
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("management-content"),
                contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.error != null) item {
                    NoticeCard("요청을 완료하지 못했습니다", state.error, error = true)
                }
                if (state.notice != null) item { NoticeCard("처리 결과", state.notice) }
                if (state.writing) item { NoticeCard("GitHub에 요청 중", "중복 제출하지 말고 결과를 기다려 주세요.") }
                if (state.loading) item {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("GitHub의 최신 상태를 가져오는 중입니다.", style = MaterialTheme.typography.bodyMedium)
                }
                when (state.screen) {
                    ManagementScreen.LIST -> pullList(state, onAction, onSample)
                    ManagementScreen.CONNECTION -> item { ConnectionForm(state, onAction, onOpenUrl) }
                    ManagementScreen.DETAIL -> {
                        val detail = state.detail
                        if (detail != null) when (state.tab) {
                            DetailTab.SUMMARY -> summary(detail, onOpenUrl)
                            DetailTab.FILES -> files(detail, state.loading, onAction, onOpenUrl)
                            DetailTab.CHECKS -> checks(detail, onOpenUrl)
                            DetailTab.REVIEWS -> reviews(state, detail, onAction, onOpenUrl)
                        } else if (!state.loading) item {
                            NoticeCard("상세 정보를 불러오지 못했습니다", "목록으로 돌아가 PR을 다시 열어 주세요.")
                        }
                    }
                }
            }
        }
        }
    }
}

private fun tabTitle(tab: DetailTab) = when (tab) {
    DetailTab.SUMMARY -> "요약"
    DetailTab.FILES -> "변경"
    DetailTab.CHECKS -> "검사"
    DetailTab.REVIEWS -> "리뷰"
}

@Composable
private fun actionColors(outlined: Boolean = false): ButtonColors {
    val colors = MaterialTheme.colorScheme
    return if (outlined) ButtonDefaults.outlinedButtonColors(
        disabledContentColor = colors.onSurfaceVariant,
        disabledContainerColor = colors.surfaceContainerHighest,
    ) else ButtonDefaults.buttonColors(
        disabledContentColor = colors.onSurfaceVariant,
        disabledContainerColor = colors.surfaceContainerHighest,
    )
}

@Composable
private fun quietActionColors() = ButtonDefaults.textButtonColors(
    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
private fun choiceColors() = FilterChipDefaults.filterChipColors(
    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    disabledSelectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
)

@Composable
private fun readableFieldColors() = OutlinedTextFieldDefaults.colors(
    disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledBorderColor = MaterialTheme.colorScheme.outline,
)

@Composable
private fun NoticeCard(title: String, text: String, error: Boolean = false) {
    Card(colors = CardDefaults.cardColors(
        containerColor = if (error) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSurface,
    )) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            SelectionContainer { Text(text, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

@Composable
private fun StatusLabel(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge)
    }
}

private fun pullStatus(pr: PullRequest) = when {
    pr.merged -> "병합됨"
    pr.state == "closed" -> "닫힘"
    pr.draft -> "초안"
    else -> "열림"
}

private fun localTime(iso: String): String = try {
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
        isLenient = false
    }
    parser.parse(iso)?.let {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.KOREA).format(it)
    } ?: "시간 확인 불가"
} catch (_: ParseException) {
    "시간 확인 불가"
}

private fun LazyListScope.pullList(
    state: ManagementState, onAction: (ManagementAction) -> Unit, onSample: () -> Unit,
) {
    if (!state.loading && state.pulls.isEmpty() && state.error == null) item {
        NoticeCard("표시할 PR이 없습니다", "다른 상태 필터를 선택하거나 GitHub에서 PR을 만든 뒤 새로고침하세요.")
    }
    items(state.pulls, key = { it.number }) { pr ->
        Card(onClick = { onAction(ManagementAction.OpenPull(pr.number)) },
            modifier = Modifier.testTag("pull-${pr.number}")) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("#${pr.number}", style = MaterialTheme.typography.labelLarge)
                    StatusLabel(pullStatus(pr))
                }
                Text(pr.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("@${pr.author} · ${localTime(pr.updatedAt)}",
                    style = MaterialTheme.typography.bodyMedium)
                val summary = state.summaries[pr.number]
                Text(when {
                    summary == null -> "검사·승인 요약 확인 중"
                    summary.error != null -> "요약 조회 실패 · 상세에서 다시 확인"
                    else -> {
                        val failed = summary.checks.count {
                            it.status == "completed" && it.conclusion !in setOf("success", "neutral", "skipped")
                        }
                        val running = summary.checks.count { it.status != "completed" }
                        "검사 ${summary.checks.size}개 · 실패 $failed · 진행 $running\n최신 승인 ${summary.approvals}명"
                    }
                }, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (state.hasMore) item {
        OutlinedButton(enabled = !state.loading, colors = actionColors(outlined = true),
            onClick = { onAction(ManagementAction.MorePulls) }) {
            Text("PR 더 불러오기")
        }
    }
    item {
        HorizontalDivider()
        TextButton(onClick = onSample, enabled = !state.writing, colors = quietActionColors(),
            modifier = Modifier.testTag("sample-open")) {
            Text("검증 대상 · 할 일 샘플 열기")
        }
    }
}

@Composable
private fun ConnectionForm(
    state: ManagementState, onAction: (ManagementAction) -> Unit, onOpenUrl: (String) -> Unit,
) {
    var repository by rememberSaveable(state.repo.fullName) { mutableStateOf(state.repo.fullName) }
    var clientId by rememberSaveable { mutableStateOf("") }
    // Credentials never enter saved instance state or ordinary preferences.
    var credential by remember { mutableStateOf("") }
    LaunchedEffect(state.viewer) { if (state.viewer != null) credential = "" }
    Column(Modifier.fillMaxWidth().widthIn(max = 640.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        NoticeCard("저장소와 사용자 연결",
            "공개 PR은 로그인 없이 봅니다.\n리뷰·승인·병합은 본인 인증이 필요합니다.")
        OutlinedTextField(value = repository, onValueChange = { repository = it },
            label = { Text("저장소") }, placeholder = { Text("owner/repository") },
            colors = readableFieldColors(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true, enabled = !state.authenticating, modifier = Modifier.fillMaxWidth())
        Button(enabled = !state.authenticating, colors = actionColors(), onClick = {
            onAction(ManagementAction.Repository(repository))
        }) { Text("저장소 열기") }
        HorizontalDivider()
        if (state.viewer != null) {
            Text("@${state.viewer.login} 사용자로 인증됨", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(enabled = !state.authenticating, colors = actionColors(outlined = true),
                onClick = { onAction(ManagementAction.Logout) }) { Text("이 기기에서 로그아웃") }
        } else {
            Text("GitHub 기기 코드 로그인", style = MaterialTheme.typography.titleMedium)
            Text("등록한 GitHub App의 공개 ID입니다.\ndevice flow가 켜져 있어야 합니다.",
                style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(value = clientId, onValueChange = { clientId = it },
                label = { Text("GitHub App Client ID") }, singleLine = true,
                colors = readableFieldColors(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                enabled = !state.authenticating, modifier = Modifier.fillMaxWidth())
            Button(enabled = !state.authenticating && clientId.isNotBlank(), colors = actionColors(), onClick = {
                credential = ""
                onAction(ManagementAction.DeviceLogin(clientId))
            }) { Text("로그인 코드 요청") }
            state.deviceCode?.let { code ->
                NoticeCard("GitHub에서 인증", "GitHub에 코드를 입력하세요.\n본인 계정으로 허용하세요.")
                SelectionContainer {
                    Text(code.userCode, style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Monospace)
                }
                Button(onClick = { onOpenUrl(code.verificationUri) }) { Text("GitHub 인증 화면 열기") }
            }
            if (state.authenticating) {
                Text("GitHub 인증 결과를 기다리고 있습니다.", style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = { onAction(ManagementAction.CancelLogin) }) { Text("인증 취소") }
            }
            HorizontalDivider()
            Text("개인 토큰으로 연결", style = MaterialTheme.typography.titleMedium)
            Text("개인 토큰으로 바로 연결합니다.\n이 기기의 Keystore에 암호화합니다.",
                style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(value = credential, onValueChange = { credential = it },
                label = { Text("본인의 GitHub access token") }, singleLine = true,
                colors = readableFieldColors(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(),
                enabled = !state.authenticating, modifier = Modifier.fillMaxWidth())
            Button(enabled = !state.authenticating && credential.isNotBlank(), colors = actionColors(),
                modifier = Modifier.testTag("token-connect"), onClick = {
                val supplied = credential
                credential = ""
                onAction(ManagementAction.TokenLogin(supplied))
            }) { Text("토큰 확인 후 연결") }
            Text("저장소 조회에는 읽기 권한, 리뷰에는 Pull requests 쓰기, 병합에는 Contents 쓰기 권한이 필요합니다.",
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun LazyListScope.summary(detail: PullDetails, onOpenUrl: (String) -> Unit) {
    val pr = detail.pull
    item {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusLabel(pullStatus(pr))
            SelectionContainer {
                Text(pr.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Text("@${pr.author} · ${localTime(pr.updatedAt)}", style = MaterialTheme.typography.bodyLarge)
            Text("${pr.headRef} → ${pr.baseRef}", style = MaterialTheme.typography.bodyLarge)
            SelectionContainer {
                Text("최신 커밋 ${pr.headSha}", style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace)
            }
            TextButton(onClick = { onOpenUrl(pr.url) }) { Text("GitHub에서 PR 열기") }
        }
    }
    item {
        Text("PR 본문 · AI 사용·테스트 근거", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        SelectionContainer {
            Text(pr.body.ifBlank { "본문이 없습니다. 작성자에게 목적과 AI 사용·테스트 근거를 요청하세요." },
                style = MaterialTheme.typography.bodyLarge)
        }
    }
    item {
        NoticeCard("승인과 검사는 별개입니다",
            "검사 결과와 최신 커밋의 변경 코드를 확인한 뒤 리뷰 탭에서 의견·수정 요청·승인을 제출하세요.")
    }
}

private fun LazyListScope.files(
    detail: PullDetails, loading: Boolean, onAction: (ManagementAction) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    item {
        Text("변경 파일 ${detail.pull.changedFiles}개 · ${detail.files.size}개 표시",
            style = MaterialTheme.typography.titleMedium)
        Text("diff는 가로로 스크롤할 수 있습니다.", style = MaterialTheme.typography.bodyMedium)
    }
    if (detail.files.isEmpty()) item { NoticeCard("변경 파일 없음", "GitHub 원문에서 변경 내역을 확인하세요.") }
    items(detail.files, key = { it.name }) { file ->
        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectionContainer {
                    Text(file.name, style = MaterialTheme.typography.titleMedium)
                }
                Text("${fileStatus(file.status)} · +${file.additions} / -${file.deletions}",
                    style = MaterialTheme.typography.bodyMedium)
                if (file.patch != null) {
                    SelectionContainer {
                        Text(file.patch, Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace,
                            softWrap = false)
                    }
                } else {
                    Text("바이너리 또는 생략된 diff입니다. GitHub 원문에서 확인하세요.",
                        style = MaterialTheme.typography.bodyLarge)
                }
                TextButton(onClick = { onOpenUrl(file.url) }) { Text("변경 파일 원문 열기") }
            }
        }
    }
    if (detail.hasMoreFiles) item {
        OutlinedButton(enabled = !loading, colors = actionColors(outlined = true),
            onClick = { onAction(ManagementAction.MoreFiles) }) {
            Text("변경 파일 더 불러오기")
        }
    }
}

private fun fileStatus(value: String) = when (value) {
    "added" -> "추가"
    "removed" -> "삭제"
    "renamed" -> "이름 변경"
    "modified" -> "수정"
    else -> "변경"
}

private fun checkLabel(check: CheckRun): String = when {
    check.status == "queued" -> "대기"
    check.status == "in_progress" -> "실행 중"
    check.status != "completed" -> "상태 확인 필요"
    else -> when (check.conclusion) {
        "success" -> "통과"
        "failure" -> "실패"
        "cancelled" -> "취소"
        "timed_out" -> "시간 초과"
        "skipped" -> "생략"
        "neutral" -> "참고 결과"
        "action_required" -> "조치 필요"
        else -> "결과 확인 필요"
    }
}

private fun LazyListScope.checks(detail: PullDetails, onOpenUrl: (String) -> Unit) {
    val checks = currentChecks(detail)
    val blockers = gateBlockers(detail)
    item {
        NoticeCard(if (blockers.isEmpty()) "자동 검사 통과" else "자동 검사 확인 필요",
            if (blockers.isEmpty()) "사람의 리뷰·승인은 별도로 필요합니다."
            else blockers.joinToString("\n"))
    }
    item {
        SelectionContainer {
            Text("기준 커밋 ${detail.pull.headSha}", style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace)
        }
    }
    items(checks, key = { it.id }) { check ->
        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(check.name, style = MaterialTheme.typography.titleMedium)
                StatusLabel(checkLabel(check))
                Text(if (check.headSha == detail.pull.headSha) "PR 최신 커밋 검사" else "현재 병합 예상 커밋 검사",
                    style = MaterialTheme.typography.bodyMedium)
                SelectionContainer {
                    Text(check.summary.ifBlank {
                        "상세 메시지가 없습니다. GitHub 실행 기록에서 실패 단계와 로그를 확인하세요."
                    }, style = MaterialTheme.typography.bodyLarge)
                }
                TextButton(onClick = { onOpenUrl(check.url) }) { Text("검사 근거·로그 열기") }
            }
        }
    }
    items(detail.statuses, key = { "status-${it.id}" }) { status ->
        NoticeCard(status.context, when (status.state) {
            "success" -> "통과"
            "pending" -> "대기"
            "failure", "error" -> "실패"
            else -> "상태 확인 필요"
        } + "\n" + status.description)
        if (status.url.isNotBlank()) TextButton(onClick = { onOpenUrl(status.url) }) { Text("추가 검사 원문 열기") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.reviews(
    state: ManagementState, detail: PullDetails,
    onAction: (ManagementAction) -> Unit, onOpenUrl: (String) -> Unit,
) {
    item {
        NoticeCard("최신 승인 ${latestApprovals(detail).size}명\n자기 검토 " +
            if (latestSelfReviews(detail).isEmpty()) "없음" else "있음",
            "승인은 작성자 외 리뷰어가 현재 커밋에 제출한 것만 집계합니다.\n" +
                "1인 운영에서는 현재 커밋의 작성자 자기 검토도 병합 조건으로 봅니다.")
    }
    item { ReviewForm(state, detail, onAction) }
    item { MergeForm(state, detail, onAction) }
    item { Text("리뷰 이력", style = MaterialTheme.typography.titleLarge) }
    if (detail.reviews.isEmpty()) item { NoticeCard("리뷰가 없습니다", "변경 코드와 검사 결과를 검토한 뒤 의견을 남겨 주세요.") }
    items(detail.reviews.sortedByDescending { it.id }, key = { it.id }) { review ->
        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("@${review.author} · " + if (isSelfReview(review, detail.pull)) "자기 검토"
                    else reviewLabel(review.state), style = MaterialTheme.typography.titleMedium)
                Text(localTime(review.submittedAt), style = MaterialTheme.typography.bodyMedium)
                StatusLabel(if (review.commitSha == detail.pull.headSha) "현재 커밋 대상" else "이전 커밋 대상")
                SelectionContainer {
                    Text(review.body.ifBlank { "작성된 의견이 없습니다." }, style = MaterialTheme.typography.bodyLarge)
                }
                TextButton(onClick = { onOpenUrl(review.url) }) { Text("GitHub 리뷰 열기") }
            }
        }
    }
}

private fun reviewLabel(value: String) = when (value) {
    "APPROVED" -> "승인"
    "CHANGES_REQUESTED" -> "수정 요청"
    "DISMISSED" -> "승인 해제"
    "COMMENTED" -> "의견"
    "PENDING" -> "작성 중"
    else -> "리뷰"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewForm(
    state: ManagementState, detail: PullDetails, onAction: (ManagementAction) -> Unit,
) {
    var confirm by remember(detail.pull.target(state.repo)) {
        mutableStateOf<Pair<ReviewEvent, PullTarget>?>(null)
    }
    var selfConfirm by remember(detail.pull.target(state.repo)) { mutableStateOf<PullTarget?>(null) }
    val enabled = state.viewer != null && !state.writing && !state.loading && state.error == null &&
        detail.pull.state == "open" && !detail.pull.merged
    val self = state.viewer?.login.equals(detail.pull.author, ignoreCase = true)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("리뷰 제출", style = MaterialTheme.typography.titleLarge)
        if (state.viewer == null) {
            Text("리뷰를 제출하려면 GitHub에 연결하세요.", style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = { onAction(ManagementAction.Connect) }) { Text("GitHub 연결하기") }
        }
        OutlinedTextField(value = state.reviewBody, onValueChange = {
            onAction(ManagementAction.ReviewBody(it))
        }, label = { Text("리뷰 의견") }, minLines = 3, enabled = enabled,
            colors = readableFieldColors(), modifier = Modifier.fillMaxWidth())
        if (self) Text("본인 PR은 승인 대신 자기 검토를 기록합니다.", style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = enabled && state.reviewBody.isNotBlank(),
                colors = actionColors(outlined = true), onClick = {
                confirm = ReviewEvent.COMMENT to detail.pull.target(state.repo)
            }) { Text("의견 제출") }
            OutlinedButton(enabled = enabled && !self && state.reviewBody.isNotBlank(),
                colors = actionColors(outlined = true), onClick = {
                confirm = ReviewEvent.REQUEST_CHANGES to detail.pull.target(state.repo)
            }) { Text("수정 요청") }
            Button(enabled = enabled && !self, colors = actionColors(), onClick = {
                confirm = ReviewEvent.APPROVE to detail.pull.target(state.repo)
            },
                modifier = Modifier.testTag("review-approve")) { Text("승인") }
            if (self) Button(enabled = enabled, colors = actionColors(), onClick = {
                selfConfirm = detail.pull.target(state.repo)
            }, modifier = Modifier.testTag("self-review-open")) { Text("자기 검토 완료") }
        }
    }
    selfConfirm?.let { target ->
        AlertDialog(onDismissRequest = { selfConfirm = null }, title = { Text("자기 검토 확인") },
            text = { Text("PR #${target.number} · ${target.headSha.take(12)}\n" +
                SELF_REVIEW_CHECKLIST.joinToString("\n") { "• $it" } +
                "\n위 항목을 확인했다고 기록합니다.\n다른 사람의 승인으로 집계하지 않습니다.") },
            confirmButton = {
                TextButton(enabled = enabled, colors = quietActionColors(), onClick = {
                    selfConfirm = null
                    onAction(ManagementAction.SelfReview(target))
                }, modifier = Modifier.testTag("self-review-confirm")) { Text("자기 검토 기록") }
            },
            dismissButton = { TextButton(onClick = { selfConfirm = null }) { Text("취소") } })
    }
    confirm?.let { (event, target) ->
        val verb = when (event) {
            ReviewEvent.COMMENT -> "의견 제출"
            ReviewEvent.REQUEST_CHANGES -> "수정 요청"
            ReviewEvent.APPROVE -> "승인 제출"
        }
        AlertDialog(onDismissRequest = { confirm = null }, title = { Text("$verb 확인") },
            text = { Text("PR #${target.number} · ${target.headSha.take(12)}\n본인 계정으로 제출합니다.") },
            confirmButton = {
                TextButton(enabled = enabled, colors = quietActionColors(), onClick = {
                    confirm = null
                    onAction(ManagementAction.SubmitReview(event, target))
                }, modifier = Modifier.testTag("review-confirm")) { Text(verb) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("취소") } })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MergeForm(
    state: ManagementState, detail: PullDetails, onAction: (ManagementAction) -> Unit,
) {
    val methods = state.repository?.mergeMethods.orEmpty()
    var method by remember(detail.pull.headSha, methods) { mutableStateOf(methods.firstOrNull() ?: "merge") }
    var confirm by remember(detail.pull.target(state.repo)) {
        mutableStateOf<Pair<PullTarget, String>?>(null)
    }
    val finished = finishedPullNotice(detail.pull)
    if (finished != null) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("병합", style = MaterialTheme.typography.titleLarge)
            NoticeCard(finished.first, finished.second)
        }
        return
    }
    val blockers = mergeBlockers(detail, state.viewer, state.repository)
    val enabled = blockers.isEmpty() && !state.writing && !state.loading && state.error == null
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("병합", style = MaterialTheme.typography.titleLarge)
        NoticeCard(if (blockers.isEmpty()) "병합 요청 준비됨" else "병합할 수 없습니다",
            if (blockers.isEmpty()) "최신 커밋과 조건을 확인합니다.\n최종 판정은 GitHub가 수행합니다."
            else blockers.joinToString("\n"))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            methods.forEach { value ->
                FilterChip(selected = method == value, enabled = !state.writing, colors = choiceColors(),
                    onClick = { method = value }, label = { Text(when (value) {
                        "squash" -> "하나로 합치기"
                        "rebase" -> "리베이스"
                        else -> "병합 커밋"
                    }) })
            }
        }
        OutlinedButton(enabled = enabled, colors = actionColors(outlined = true),
            onClick = { confirm = detail.pull.target(state.repo) to method },
            modifier = Modifier.testTag("merge-open")) { Text("병합 요청") }
    }
    confirm?.let { (target, confirmedMethod) ->
        AlertDialog(onDismissRequest = { confirm = null }, title = { Text("PR 병합 확인") },
        text = { Text("PR #${target.number} · ${target.headSha.take(12)}\n대상 브랜치: ${target.baseRef}\n확인한 커밋을 병합 요청합니다.") },
        confirmButton = {
            TextButton(enabled = enabled, colors = quietActionColors(), onClick = {
                confirm = null
                onAction(ManagementAction.Merge(target, confirmedMethod))
            }, modifier = Modifier.testTag("merge-confirm")) { Text("확인한 커밋 병합") }
        },
        dismissButton = { TextButton(onClick = { confirm = null }) { Text("취소") } })
    }
}
