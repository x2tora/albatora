package com.albatora.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.albatora.app.domain.ExportManager
import com.albatora.app.domain.PayrollCalculator
import com.albatora.app.domain.ResolvedRecordItem
import com.albatora.app.model.*
import com.albatora.app.ui.components.ConfirmActionDialog
import com.albatora.app.ui.components.EmptyDataBanner
import com.albatora.app.ui.components.MiuixEntityBadge
import com.albatora.app.ui.components.MiuixGlassIcon
import com.albatora.app.ui.components.SquirclePillBadge
import com.albatora.app.ui.theme.AlbaIconCatalog
import com.albatora.app.ui.theme.AlbaMotion
import com.albatora.app.ui.theme.LocalWideDualLayoutEnabled
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import java.time.LocalDate
import java.time.YearMonth

private enum class RecordQuickFilter(val label: String) {
    ALL("전체"),
    REGULAR("정규"),
    SHORT_TERM("단기"),
    SUBSTITUTE("대타"),
    OVERTIME("연장")
}

private fun applyRecordFilters(
    items: List<ResolvedRecordItem>,
    query: String,
    quickFilter: RecordQuickFilter
): List<ResolvedRecordItem> {
    val q = query.trim()
    return items.filter { item ->
        val rec = item.record
        val matchesFilter = when (quickFilter) {
            RecordQuickFilter.ALL -> true
            RecordQuickFilter.REGULAR -> rec.kind == JobKind.REGULAR
            RecordQuickFilter.SHORT_TERM -> rec.kind == JobKind.SHORT_TERM
            RecordQuickFilter.SUBSTITUTE -> rec.status == ShiftStatus.SUBSTITUTE
            RecordQuickFilter.OVERTIME -> rec.status == ShiftStatus.OVERTIME
        }
        val matchesQuery = q.isEmpty() ||
            rec.title.contains(q, ignoreCase = true) ||
            rec.memo.contains(q, ignoreCase = true) ||
            rec.date.contains(q, ignoreCase = true)
        matchesFilter && matchesQuery
    }
}

@Composable
fun RecordsScreen(
    state: AlbaToraDataState,
    initialOnlyUnpaid: Boolean,
    scrollBehavior: ScrollBehavior,
    topInset: Dp = 0.dp,
    onEditRecord: (WorkRecord) -> Unit,
    onTogglePaymentStatus: (String) -> Unit,
    onBatchMarkPaymentStatus: (Set<String>, PaymentStatus) -> Unit = { _, _ -> },
    onBatchDeleteRecords: (Set<String>) -> Unit = {},
    onConfirmVirtualRecord: (WorkRecord) -> Unit = {},
    onAddShortTerm: () -> Unit,
    onOpenAnalytics: () -> Unit = {},
    onNotify: (String) -> Unit
) {
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    var anchorMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedJobId by remember { mutableStateOf<String?>(null) } // null = 전체 합산
    var onlyUnpaid by remember(initialOnlyUnpaid) { mutableStateOf(initialOnlyUnpaid) }
    var searchQuery by remember { mutableStateOf("") }
    var quickFilter by remember { mutableStateOf(RecordQuickFilter.ALL) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    val todayIso = remember { PayrollCalculator.formatDate(LocalDate.now()) }

    val selectedJob = remember(state.jobs, selectedJobId) {
        state.jobs.find { it.id == selectedJobId }
    }

    val periodRange = remember(selectedJob, anchorMonth) {
        if (selectedJob != null) {
            PayrollCalculator.getSettlementPeriod(selectedJob, anchorMonth)
        } else {
            anchorMonth.atDay(1) to anchorMonth.atEndOfMonth()
        }
    }

    val globalUnpaid = remember(state.records, state.jobs) {
        PayrollCalculator.calculateGlobalUnpaidSummary(state)
    }

    val scheduledPending = remember(state.records, state.jobs) {
        PayrollCalculator.calculateScheduledPendingUnpaidSummary(state)
    }

    val summary = remember(state, periodRange, selectedJobId, onlyUnpaid) {
        PayrollCalculator.calculateSettlementSummary(
            state = state,
            fromDate = periodRange.first,
            toDate = periodRange.second,
            filterJobId = selectedJobId,
            onlyUnpaid = onlyUnpaid
        )
    }

    val filteredSummaryItems = remember(summary.items, searchQuery, quickFilter) {
        applyRecordFilters(summary.items, searchQuery, quickFilter)
    }
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val csv = ExportManager.buildCsvContent(summary.items)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(csv.toByteArray(Charsets.UTF_8))
                } ?: error("파일을 열 수 없습니다.")
            }.onSuccess {
                onNotify("CSV 파일로 내보냈습니다")
            }.onFailure {
                onNotify("CSV 내보내기 실패: ${it.message}")
            }
        }
    }
    val filteredGlobalUnpaid = remember(globalUnpaid.second, searchQuery, quickFilter) {
        applyRecordFilters(globalUnpaid.second, searchQuery, quickFilter)
    }
    val filteredScheduledPending = remember(scheduledPending.second, searchQuery, quickFilter) {
        applyRecordFilters(scheduledPending.second, searchQuery, quickFilter)
    }

    val toggleSelection: (String) -> Unit = { id ->
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    val searchAndBatchCard: @Composable () -> Unit = {
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = "근무지 · 메모 · 날짜 검색",
                        modifier = Modifier.weight(1f)
                    )
                    SquirclePillBadge(
                        text = if (selectionMode) "완료" else "일괄 선택",
                        backgroundColor = if (selectionMode) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.secondaryContainer
                        },
                        contentColor = if (selectionMode) {
                            MiuixTheme.colorScheme.onPrimary
                        } else {
                            MiuixTheme.colorScheme.onSecondaryContainer
                        },
                        onClick = {
                            selectionMode = !selectionMode
                            if (!selectionMode) selectedIds = emptySet()
                        }
                    )
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(RecordQuickFilter.entries) { filter ->
                        val selected = quickFilter == filter
                        SquirclePillBadge(
                            text = filter.label,
                            backgroundColor = if (selected) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.secondaryContainer
                            },
                            contentColor = if (selected) {
                                MiuixTheme.colorScheme.onPrimary
                            } else {
                                MiuixTheme.colorScheme.onSecondaryContainer
                            },
                            onClick = { quickFilter = filter }
                        )
                    }
                }

                AnimatedVisibility(visible = selectionMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${selectedIds.size}건 선택",
                            style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.SemiBold),
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        SquirclePillBadge(
                            text = "입금 완료",
                            backgroundColor = Color(0xFF16A34A).copy(alpha = 0.14f),
                            contentColor = Color(0xFF16A34A),
                            onClick = {
                                if (selectedIds.isNotEmpty()) {
                                    onBatchMarkPaymentStatus(selectedIds, PaymentStatus.PAID)
                                    selectedIds = emptySet()
                                    selectionMode = false
                                }
                            }
                        )
                        SquirclePillBadge(
                            text = "입금 대기",
                            backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                            contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
                            onClick = {
                                if (selectedIds.isNotEmpty()) {
                                    onBatchMarkPaymentStatus(selectedIds, PaymentStatus.UNPAID)
                                    selectedIds = emptySet()
                                    selectionMode = false
                                }
                            }
                        )
                        SquirclePillBadge(
                            text = "삭제",
                            backgroundColor = MiuixTheme.colorScheme.error.copy(alpha = 0.14f),
                            contentColor = MiuixTheme.colorScheme.error,
                            onClick = {
                                if (selectedIds.isNotEmpty()) {
                                    showBatchDeleteConfirm = true
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listPadding = PaddingValues(start = 20.dp, end = 20.dp, top = topInset + 12.dp, bottom = 116.dp + navBottomInset)

    val filterTabsCard: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TabRowWithContour(
                tabs = listOf("정산 기간 명세서", "미수금 모아보기 (${globalUnpaid.second.size})"),
                selectedTabIndex = if (onlyUnpaid) 1 else 0,
                onTabSelected = { onlyUnpaid = (it == 1) }
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                onClick = onOpenAnalytics
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "월별 수입 추이 · 근무지 비중 · 목표 트래커",
                        style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.SemiBold),
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    SquirclePillBadge(
                        text = "통계 보기 →",
                        backgroundColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.14f),
                        contentColor = MiuixTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    val navigatorCard: @Composable () -> Unit = {
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { anchorMonth = anchorMonth.minusMonths(1) }) {
                        Icon(MiuixIcons.ChevronBackward, contentDescription = "이전 정산 기간")
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedContent(
                            targetState = "${anchorMonth}_${selectedJobId}_${periodRange.first}_${periodRange.second}",
                            transitionSpec = {
                                (fadeIn(tween(200)) + scaleIn(initialScale = 0.97f, animationSpec = AlbaMotion.FluidSpring))
                                    .togetherWith(fadeOut(tween(140)) + scaleOut(targetScale = 0.97f, animationSpec = AlbaMotion.SettleSpring))
                            },
                            label = "records_period_title"
                        ) { _ ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (selectedJob != null) "${anchorMonth.year}년 ${anchorMonth.monthValue}월 급여 정산분" else "${anchorMonth.year}년 ${anchorMonth.monthValue}월",
                                    style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "산정 기간: ${periodRange.first} ~ ${periodRange.second}",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                        }
                    }
                    IconButton(onClick = { anchorMonth = anchorMonth.plusMonths(1) }) {
                        Icon(MiuixIcons.ChevronForward, contentDescription = "다음 정산 기간")
                    }
                }

                if (state.jobs.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            SquirclePillBadge(
                                text = "전체 합산",
                                backgroundColor = if (selectedJobId == null) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.secondaryContainer,
                                contentColor = if (selectedJobId == null) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSecondaryContainer,
                                onClick = { selectedJobId = null }
                            )
                        }
                        items(state.jobs, key = { it.id }) { j ->
                            val active = selectedJobId == j.id
                            SquirclePillBadge(
                                text = j.name,
                                backgroundColor = if (active) Color(j.colorHex) else Color(j.colorHex).copy(alpha = 0.16f),
                                contentColor = if (active) Color.White else Color(j.colorHex),
                                leadingIcon = AlbaIconCatalog.resolveIcon(j.emoji, MiuixIcons.Store),
                                onClick = { selectedJobId = j.id }
                            )
                        }
                    }
                }
            }
        }
    }

    val statementCard: @Composable () -> Unit = {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = AlbaMotion.LayoutSizeSpring),
            insideMargin = PaddingValues(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "예상 실수령액",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Text(
                    text = PayrollCalculator.formatWon(summary.totalNetPay),
                    style = MiuixTheme.textStyles.title2.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = MiuixTheme.colorScheme.primary
                )
                HorizontalDivider()
                StatementRow("근무 횟수 · 시간", "${summary.shiftCount}건 · ${PayrollCalculator.formatDurationKorean(summary.totalWorkMinutes)}")
                StatementRow("기본 급여", PayrollCalculator.formatWon(summary.totalBasePay))
                if (summary.totalAllowancePay > 0L) {
                    StatementRow("가산 수당 (야간/연장/휴일)", "+${PayrollCalculator.formatWon(summary.totalAllowancePay)}", Color(0xFFE64A19))
                }
                if (summary.totalWeeklyHolidayPay > 0L) {
                    StatementRow("주휴수당 (주 15h+)", "+${PayrollCalculator.formatWon(summary.totalWeeklyHolidayPay)}", Color(0xFF059669))
                }
                if (summary.totalSiteBonus > 0L) {
                    StatementRow("현장 지원금 · 보너스", "+${PayrollCalculator.formatWon(summary.totalSiteBonus)}", Color(0xFF059669))
                }
                if (summary.totalTaxAmount > 0L) {
                    StatementRow("세금 공제", "-${PayrollCalculator.formatWon(summary.totalTaxAmount)}", MiuixTheme.colorScheme.error)
                }
                if (summary.totalAgencyFee > 0L) {
                    StatementRow("인력·플랫폼 수수료", "-${PayrollCalculator.formatWon(summary.totalAgencyFee)}", MiuixTheme.colorScheme.error)
                }
                if (summary.totalExpenses > 0L) {
                    StatementRow("교통비 · 식비 지출", "-${PayrollCalculator.formatWon(summary.totalExpenses)}", MiuixTheme.colorScheme.error)
                }
                if (summary.unpaidAmount > 0L) {
                    StatementRow("기간 내 미수금 (입금 대기)", "${PayrollCalculator.formatWon(summary.unpaidAmount)} (${summary.unpaidCount}건)", MiuixTheme.colorScheme.error)
                }

                Spacer(modifier = Modifier.height(6.dp))
                TextButton(
                    text = "급여명세서 복사",
                    onClick = {
                        val title = selectedJob?.name ?: "전체 근무지 합산"
                        val text = PayrollCalculator.buildPayslipText(title, summary)
                        clipboardManager.setText(AnnotatedString(text))
                        onNotify("급여명세서를 클립보드에 복사했습니다")
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val title = selectedJob?.name ?: "전체 근무지 합산"
                    SquirclePillBadge(
                        text = "이미지 공유",
                        backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                        contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            ExportManager.renderPayslipPngUri(context, title, summary)
                                .onSuccess { uri ->
                                    ExportManager.shareUri(context, uri, "image/png", "급여명세서 이미지 공유")
                                }
                                .onFailure { onNotify("이미지 생성 실패: ${it.message}") }
                        }
                    )
                    SquirclePillBadge(
                        text = "PDF 공유",
                        backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                        contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            ExportManager.renderPayslipPdfUri(context, title, summary)
                                .onSuccess { uri ->
                                    ExportManager.shareUri(context, uri, "application/pdf", "급여명세서 PDF 공유")
                                }
                                .onFailure { onNotify("PDF 생성 실패: ${it.message}") }
                        }
                    )
                    SquirclePillBadge(
                        text = "CSV 저장",
                        backgroundColor = Color(0xFF10B981).copy(alpha = 0.14f),
                        contentColor = Color(0xFF10B981),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            csvLauncher.launch("albatora_${anchorMonth}.csv")
                        }
                    )
                }
            }
        }
    }

    val unpaidBannerCard: @Composable () -> Unit = {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.errorContainer.copy(alpha = 0.65f)),
            insideMargin = PaddingValues(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "전체 미수금",
                    style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.SemiBold),
                    color = MiuixTheme.colorScheme.onErrorContainer
                )
                Text(
                    PayrollCalculator.formatWon(globalUnpaid.first),
                    style = MiuixTheme.textStyles.title2.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = MiuixTheme.colorScheme.error
                )
                Text(
                    "총 ${globalUnpaid.second.size}건 · 배지를 탭해 입금 완료 처리",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                )
            }
        }
    }

    val unpaidOverviewCard: @Composable () -> Unit = {
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "미입금 요약",
                    style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Bold)
                )
                HorizontalDivider()
                StatementRow(
                    "정산일 경과 미수금",
                    "${globalUnpaid.second.size}건 · ${PayrollCalculator.formatWon(globalUnpaid.first)}",
                    if (globalUnpaid.first > 0L) MiuixTheme.colorScheme.error else Color(0xFF059669)
                )
                StatementRow(
                    "정산일 예정 대기",
                    "${scheduledPending.second.size}건 · ${PayrollCalculator.formatWon(scheduledPending.first)}",
                    MiuixTheme.colorScheme.primary
                )
                HorizontalDivider()
                StatementRow(
                    "전체 미입금 합계",
                    PayrollCalculator.formatWon(globalUnpaid.first + scheduledPending.first),
                    MiuixTheme.colorScheme.onSurface
                )
            }
        }
    }

    val wideDualEnabled = LocalWideDualLayoutEnabled.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideDual = wideDualEnabled && maxWidth >= 600.dp
        val singleSidePad = ((maxWidth - 600.dp) / 2f).coerceAtLeast(20.dp)

        if (isWideDual) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(top = topInset + 12.dp, bottom = 116.dp + navBottomInset),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item { filterTabsCard() }
                    if (!onlyUnpaid) {
                        item { navigatorCard() }
                        item { statementCard() }
                    } else {
                        item { unpaidBannerCard() }
                        item { unpaidOverviewCard() }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(top = topInset + 12.dp, bottom = 116.dp + navBottomInset),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!onlyUnpaid) {
                        item { searchAndBatchCard() }
                        if (filteredSummaryItems.isEmpty()) {
                            item {
                                EmptyDataBanner(
                                    icon = MiuixIcons.Notes,
                                    title = if (summary.items.isEmpty()) "선택한 기간에 근무 기록이 없어요" else "조건에 맞는 근무 기록이 없어요",
                                    subtitle = if (summary.items.isEmpty()) "달력이나 하단 + 버튼에서 근무를 추가할 수 있습니다" else "검색어 또는 필터를 초기화해 보세요",
                                    actionLabel = if (summary.items.isEmpty()) "단기 근무 등록" else "필터 초기화",
                                    onAction = {
                                        if (summary.items.isEmpty()) onAddShortTerm()
                                        else {
                                            searchQuery = ""
                                            quickFilter = RecordQuickFilter.ALL
                                        }
                                    }
                                )
                            }
                        } else {
                            item { SmallTitle(text = "상세 근무 내역 (${filteredSummaryItems.size}건)") }
                            items(filteredSummaryItems, key = { it.record.id }) { item ->
                                RecordDetailCard(
                                    item = item,
                                    todayIso = todayIso,
                                    selectionMode = selectionMode && !item.isVirtualSchedule,
                                    isSelected = item.record.id in selectedIds,
                                    modifier = Modifier.animateItem(),
                                    onEdit = {
                                        if (selectionMode && !item.isVirtualSchedule) {
                                            toggleSelection(item.record.id)
                                        } else {
                                            onEditRecord(item.record)
                                        }
                                    },
                                    onTogglePayment = {
                                        if (!item.isVirtualSchedule) {
                                            onTogglePaymentStatus(item.record.id)
                                        }
                                    },
                                    onConfirmVirtual = { onConfirmVirtualRecord(item.record) }
                                )
                            }
                        }
                    } else {
                        item { searchAndBatchCard() }
                        if (filteredGlobalUnpaid.isEmpty()) {
                            item {
                                EmptyDataBanner(
                                    icon = MiuixIcons.Ok,
                                    title = "미수금이 없습니다",
                                    subtitle = if (filteredScheduledPending.isNotEmpty()) {
                                        "정산일이 지난 미수금은 없으며, 정산 예정 대기 ${filteredScheduledPending.size}건(${PayrollCalculator.formatWon(scheduledPending.first)})이 아래에 표시됩니다"
                                    } else {
                                        "정산일이 지난 미수금이 없습니다"
                                    },
                                    accentColor = Color(0xFF16A34A)
                                )
                            }
                        } else {
                            item { SmallTitle(text = "미수금 목록 (${filteredGlobalUnpaid.size}건)") }
                            items(filteredGlobalUnpaid, key = { it.record.id }) { item ->
                                RecordDetailCard(
                                    item = item,
                                    todayIso = todayIso,
                                    selectionMode = selectionMode,
                                    isSelected = item.record.id in selectedIds,
                                    modifier = Modifier.animateItem(),
                                    onEdit = {
                                        if (selectionMode) toggleSelection(item.record.id)
                                        else onEditRecord(item.record)
                                    },
                                    onTogglePayment = { onTogglePaymentStatus(item.record.id) }
                                )
                            }
                        }
                        if (filteredScheduledPending.isNotEmpty()) {
                            item {
                                SmallTitle(text = "정산일 예정 (${filteredScheduledPending.size}건 · ${PayrollCalculator.formatWon(scheduledPending.first)})")
                            }
                            items(filteredScheduledPending, key = { "sched_${it.record.id}" }) { item ->
                                RecordDetailCard(
                                    item = item,
                                    todayIso = todayIso,
                                    selectionMode = selectionMode,
                                    isSelected = item.record.id in selectedIds,
                                    modifier = Modifier.animateItem(),
                                    onEdit = {
                                        if (selectionMode) toggleSelection(item.record.id)
                                        else onEditRecord(item.record)
                                    },
                                    onTogglePayment = { onTogglePaymentStatus(item.record.id) }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(start = singleSidePad, end = singleSidePad, top = topInset + 12.dp, bottom = 116.dp + navBottomInset),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { filterTabsCard() }
                if (!onlyUnpaid) {
                    item { navigatorCard() }
                    item { statementCard() }
                    item { searchAndBatchCard() }
                    if (filteredSummaryItems.isEmpty()) {
                        item {
                            EmptyDataBanner(
                                icon = MiuixIcons.Notes,
                                title = if (summary.items.isEmpty()) "선택한 기간에 근무 기록이 없어요" else "조건에 맞는 근무 기록이 없어요",
                                subtitle = if (summary.items.isEmpty()) "달력이나 하단 + 버튼에서 근무를 추가할 수 있습니다" else "검색어 또는 필터를 초기화해 보세요",
                                actionLabel = if (summary.items.isEmpty()) "단기 근무 등록" else "필터 초기화",
                                onAction = {
                                    if (summary.items.isEmpty()) onAddShortTerm()
                                    else {
                                        searchQuery = ""
                                        quickFilter = RecordQuickFilter.ALL
                                    }
                                }
                            )
                        }
                    } else {
                        item { SmallTitle(text = "상세 근무 내역 (${filteredSummaryItems.size}건)") }
                        items(filteredSummaryItems, key = { it.record.id }) { item ->
                            RecordDetailCard(
                                item = item,
                                todayIso = todayIso,
                                selectionMode = selectionMode && !item.isVirtualSchedule,
                                isSelected = item.record.id in selectedIds,
                                modifier = Modifier.animateItem(),
                                onEdit = {
                                    if (selectionMode && !item.isVirtualSchedule) {
                                        toggleSelection(item.record.id)
                                    } else {
                                        onEditRecord(item.record)
                                    }
                                },
                                onTogglePayment = {
                                    if (!item.isVirtualSchedule) {
                                        onTogglePaymentStatus(item.record.id)
                                    }
                                },
                                onConfirmVirtual = { onConfirmVirtualRecord(item.record) }
                            )
                        }
                    }
                } else {
                    item { unpaidBannerCard() }
                    item { searchAndBatchCard() }
                    if (filteredGlobalUnpaid.isEmpty()) {
                        item {
                            EmptyDataBanner(
                                icon = MiuixIcons.Ok,
                                title = "미수금이 없습니다",
                                subtitle = if (filteredScheduledPending.isNotEmpty()) {
                                    "정산일이 지난 미수금은 없으며, 정산 예정 대기 ${filteredScheduledPending.size}건(${PayrollCalculator.formatWon(scheduledPending.first)})이 아래에 표시됩니다"
                                } else {
                                    "정산일이 지난 미수금이 없습니다"
                                },
                                accentColor = Color(0xFF16A34A)
                            )
                        }
                    } else {
                        items(filteredGlobalUnpaid, key = { it.record.id }) { item ->
                            RecordDetailCard(
                                item = item,
                                todayIso = todayIso,
                                selectionMode = selectionMode,
                                isSelected = item.record.id in selectedIds,
                                modifier = Modifier.animateItem(),
                                onEdit = {
                                    if (selectionMode) toggleSelection(item.record.id)
                                    else onEditRecord(item.record)
                                },
                                onTogglePayment = { onTogglePaymentStatus(item.record.id) }
                            )
                        }
                    }
                    if (filteredScheduledPending.isNotEmpty()) {
                        item {
                            SmallTitle(text = "정산일 예정 (${filteredScheduledPending.size}건 · ${PayrollCalculator.formatWon(scheduledPending.first)})")
                        }
                        items(filteredScheduledPending, key = { "sched_${it.record.id}" }) { item ->
                            RecordDetailCard(
                                item = item,
                                todayIso = todayIso,
                                selectionMode = selectionMode,
                                isSelected = item.record.id in selectedIds,
                                modifier = Modifier.animateItem(),
                                onEdit = {
                                    if (selectionMode) toggleSelection(item.record.id)
                                    else onEditRecord(item.record)
                                },
                                onTogglePayment = { onTogglePaymentStatus(item.record.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    ConfirmActionDialog(
        show = showBatchDeleteConfirm,
        title = "선택 기록 삭제",
        summary = "선택한 ${selectedIds.size}건의 근무 기록을 삭제할까요? 이 작업은 되돌릴 수 없습니다.",
        confirmText = "삭제",
        isDestructive = true,
        onDismiss = { showBatchDeleteConfirm = false },
        onConfirm = {
            onBatchDeleteRecords(selectedIds)
            selectedIds = emptySet()
            selectionMode = false
            showBatchDeleteConfirm = false
        }
    )
}

@Composable
private fun StatementRow(
    label: String,
    value: String,
    valueColor: Color = MiuixTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold),
            color = valueColor
        )
    }
}

@Composable
private fun RecordDetailCard(
    item: ResolvedRecordItem,
    todayIso: String = "",
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onEdit: () -> Unit,
    onTogglePayment: () -> Unit,
    onConfirmVirtual: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val rec = item.record
    val br = item.breakdown
    val canConfirmVirtual = item.isVirtualSchedule && (todayIso.isEmpty() || rec.date <= todayIso)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = AlbaMotion.LayoutSizeSpring),
        onClick = onEdit,
        insideMargin = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (selectionMode) {
                    Checkbox(
                        state = if (isSelected) ToggleableState.On else ToggleableState.Off,
                        onClick = { onEdit() }
                    )
                }
                MiuixEntityBadge(
                    emoji = rec.emoji,
                    fallbackIcon = MiuixIcons.Notes,
                    accentColor = Color(rec.colorHex),
                    size = 38.dp,
                    cornerRadius = 10.dp
                )
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = rec.title,
                            style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium)
                        )
                        if (rec.status != ShiftStatus.NORMAL) {
                            SquirclePillBadge(
                                text = rec.status.label,
                                backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                                contentColor = MiuixTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        if (item.isVirtualSchedule) {
                            SquirclePillBadge(
                                text = "자동 스케줄",
                                backgroundColor = Color(rec.colorHex).copy(alpha = 0.14f),
                                contentColor = Color(rec.colorHex)
                            )
                        }
                    }
                    Text(
                        text = "${rec.date} · ${PayrollCalculator.formatMinutes(rec.startMinute)}~${PayrollCalculator.formatMinutes(rec.endMinute)} (${PayrollCalculator.formatDurationKorean(br.paidWorkMinutes)})",
                        style = MiuixTheme.textStyles.footnote2.copy(fontFeatureSettings = "tnum"),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    if (br.allowancePay > 0L || br.taxAmount > 0L || br.agencyFee > 0L || (br.transportExpense + br.mealExpense) > 0L) {
                        val notes = buildList {
                            if (br.allowancePay > 0L) add("수당 +${PayrollCalculator.formatWon(br.allowancePay)}")
                            if (br.siteBonus > 0L) add("지원금 +${PayrollCalculator.formatWon(br.siteBonus)}")
                            if (br.taxAmount > 0L) add("세금 -${PayrollCalculator.formatWon(br.taxAmount)}")
                            if (br.agencyFee > 0L) add("수수료 -${PayrollCalculator.formatWon(br.agencyFee)}")
                            if (br.transportExpense + br.mealExpense > 0L) add("지출 -${PayrollCalculator.formatWon(br.transportExpense + br.mealExpense)}")
                        }.joinToString(" · ")
                        Text(
                            text = notes,
                            style = MiuixTheme.textStyles.footnote2.copy(fontFeatureSettings = "tnum"),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = PayrollCalculator.formatWon(br.netPay),
                    style = MiuixTheme.textStyles.body1.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = MiuixTheme.colorScheme.onSurface
                )
                if (!item.isVirtualSchedule) {
                    SquirclePillBadge(
                        text = rec.paymentStatus.label,
                        backgroundColor = if (rec.paymentStatus == PaymentStatus.UNPAID) {
                            MiuixTheme.colorScheme.error.copy(alpha = 0.14f)
                        } else {
                            Color(0xFF16A34A).copy(alpha = 0.14f)
                        },
                        contentColor = if (rec.paymentStatus == PaymentStatus.UNPAID) {
                            MiuixTheme.colorScheme.error
                        } else {
                            Color(0xFF16A34A)
                        },
                        onClick = onTogglePayment
                    )
                } else if (canConfirmVirtual) {
                    SquirclePillBadge(
                        text = "근무 확정",
                        backgroundColor = Color(rec.colorHex).copy(alpha = 0.16f),
                        contentColor = Color(rec.colorHex),
                        onClick = onConfirmVirtual
                    )
                }
            }
        }
    }
}
