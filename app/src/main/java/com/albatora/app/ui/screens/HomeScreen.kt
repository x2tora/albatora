package com.albatora.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.albatora.app.domain.*
import com.albatora.app.model.*
import com.albatora.app.ui.components.*
import com.albatora.app.ui.theme.LocalIsDarkTheme
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import java.util.UUID

@Composable
fun HomeScreen(
    state: AlbaToraDataState,
    liveDashboard: LiveDashboardSnapshot,
    scrollBehavior: ScrollBehavior,
    isFullscreenFocus: Boolean,
    onToggleFullscreenFocus: (Boolean) -> Unit,
    openStopwatchTrigger: Int,
    onAddRegularJob: () -> Unit,
    onEditRegularJob: (String) -> Unit,
    onQuickAddShortTerm: () -> Unit,
    onOpenUnpaidRecords: () -> Unit,
    onToggleBreak: (String) -> Unit,
    onClockOut: (String) -> Unit,
    onStartStopwatch: (
        title: String,
        emoji: String,
        kind: JobKind,
        payType: PayType,
        unitAmount: Long,
        jobId: String?,
        colorHex: Long,
        plannedDurationMinutes: Int?,
        unpaidBreakMinutes: Int,
        breakPayType: BreakPayType,
        allowance: AllowanceSettings
    ) -> Unit,
    onSelectTangibleItem: (String) -> Unit,
    onUpsertTangibleItem: (TangibleItem) -> Unit,
    onDeleteTangibleItem: (String) -> Unit,
    onUpsertWishlistGoal: (WishlistGoal) -> Unit,
    onDeleteWishlistGoal: (String) -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val currentMonth = remember { YearMonth.now() }
    val monthStart = remember(currentMonth) { currentMonth.atDay(1) }
    val monthEnd = remember(currentMonth) { currentMonth.atEndOfMonth() }

    val monthSummary = remember(state, currentMonth) {
        PayrollCalculator.calculateSettlementSummary(state, monthStart, monthEnd)
    }
    val unpaidPair = remember(state.records, state.jobs) {
        PayrollCalculator.calculateGlobalUnpaidSummary(state)
    }
    val paydays = remember(state) {
        PayrollCalculator.calculateNextPaydays(state)
    }

    var selectedLiveTab by remember(liveDashboard.activeShifts.size) { mutableIntStateOf(0) }
    var showStopwatchSheet by remember { mutableStateOf(false) }
    var showTangibleManager by remember { mutableStateOf(false) }
    var editingGoal by remember { mutableStateOf<WishlistGoal?>(null) }
    var showGoalSheet by remember { mutableStateOf(false) }

    LaunchedEffect(openStopwatchTrigger) {
        if (openStopwatchTrigger > 0) {
            showStopwatchSheet = true
        }
    }

    val selectedTangible = state.tangibleItems.find { it.id == state.settings.selectedTangibleItemId }
        ?: state.tangibleItems.first()

    val completelyEmpty = state.jobs.isEmpty() &&
        state.records.isEmpty() &&
        state.templates.isEmpty() &&
        liveDashboard.activeShifts.isEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 116.dp + navBottomInset),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Live Earnings Focus Timer Stage (fills primary viewport when active)
            if (liveDashboard.activeShifts.isNotEmpty()) {
                item(key = "live_focus_stage") {
                    FocusTimerStage(
                        snapshot = liveDashboard,
                        selectedTab = selectedLiveTab,
                        onSelectTab = { selectedLiveTab = it },
                        selectedTangible = selectedTangible,
                        allTangibles = state.tangibleItems,
                        onSelectTangible = onSelectTangibleItem,
                        onManageTangibles = { showTangibleManager = true },
                        onToggleBreak = onToggleBreak,
                        onClockOut = onClockOut,
                        isFullscreen = false,
                        onToggleFullscreen = { onToggleFullscreenFocus(true) },
                        modifier = Modifier.fillParentMaxHeight(0.84f)
                    )
                }
            } else if (completelyEmpty) {
                // Single clean empty onboarding stage without duplicate launcher grid below
                item(key = "empty_focus_welcome") {
                    EmptyOnboardingFocusCard(
                        defaultMinWage = state.settings.defaultMinWage,
                        onAddRegularJob = onAddRegularJob,
                        onQuickAddShortTerm = onQuickAddShortTerm,
                        onOpenStopwatch = { showStopwatchSheet = true },
                        modifier = Modifier.fillParentMaxHeight(0.68f)
                    )
                }
            } else {
                // Standby Focus Card when user has jobs/records but no active shift right now
                item(key = "standby_focus_card") {
                    StandbyFocusCard(
                        jobs = state.jobs,
                        templates = state.templates,
                        onStartStopwatch = { showStopwatchSheet = true },
                        onQuickStartJob = { job ->
                            val wage = PayrollCalculator.effectiveJobWage(job, LocalDate.now())
                            onStartStopwatch(
                                job.name,
                                job.emoji,
                                job.kind,
                                job.payType,
                                wage,
                                job.id,
                                job.colorHex,
                                null,
                                0,
                                BreakPayType.PAID,
                                job.allowance
                            )
                        },
                        onQuickStartTemplate = { tpl ->
                            val span = if (tpl.endMinute > tpl.startMinute) {
                                tpl.endMinute - tpl.startMinute
                            } else {
                                (24 * 60 - tpl.startMinute) + tpl.endMinute
                            }.coerceAtLeast(60)
                            onStartStopwatch(
                                tpl.name,
                                tpl.emoji,
                                JobKind.SHORT_TERM,
                                tpl.payType,
                                tpl.unitAmount,
                                null,
                                tpl.colorHex,
                                span,
                                if (tpl.breakPayType == BreakPayType.UNPAID) tpl.breakMinutes else 0,
                                tpl.breakPayType,
                                AllowanceSettings()
                            )
                        }
                    )
                }
            }

            // 2. Monthly Earnings Summary (only when data exists)
            if (!completelyEmpty) {
                item(key = "monthly_overview") {
                    val livePlusMonth = monthSummary.totalNetPay + liveDashboard.totalEarnedWon.toLong()
                    SmallTitle(text = "${currentMonth.monthValue}월 누적 수익 현황")
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "실수령 예상 누적 (실시간 포함)",
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    )
                                    Text(
                                        text = PayrollCalculator.formatWon(livePlusMonth),
                                        style = MiuixTheme.textStyles.title2.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontFeatureSettings = "tnum"
                                        ),
                                        color = MiuixTheme.colorScheme.primary
                                    )
                                }
                                MiuixGlassIcon(
                                    icon = MiuixIcons.BankCards,
                                    contentDescription = null,
                                    accentColor = MiuixTheme.colorScheme.primary
                                )
                            }
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "완료 근무 ${monthSummary.shiftCount}건 (${PayrollCalculator.formatDurationKorean(monthSummary.totalWorkMinutes)})",
                                    style = MiuixTheme.textStyles.footnote1.copy(fontFeatureSettings = "tnum"),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                                if (monthSummary.totalWeeklyHolidayPay > 0L) {
                                    Text(
                                        text = "주휴 +${PayrollCalculator.formatWon(monthSummary.totalWeeklyHolidayPay)}",
                                        style = MiuixTheme.textStyles.footnote1.copy(
                                            fontWeight = FontWeight.Medium,
                                            fontFeatureSettings = "tnum"
                                        ),
                                        color = Color(0xFF16A34A)
                                    )
                                }
                            }
                            if (selectedTangible.unitPrice > 0L && livePlusMonth > 0L) {
                                val count = livePlusMonth.toDouble() / selectedTangible.unitPrice.toDouble()
                                Text(
                                    text = "${selectedTangible.emoji} 이번 달 ${selectedTangible.name} 약 ${String.format(Locale.US, "%.1f", count)}개 상당",
                                    style = MiuixTheme.textStyles.footnote1.copy(fontFeatureSettings = "tnum"),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                        }
                    }
                }
            }

            // 3. Unpaid Alert Card
            if (unpaidPair.first > 0L) {
                item(key = "unpaid_alert") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.errorContainer.copy(alpha = if (isDark) 0.45f else 0.72f)
                        ),
                        onClick = onOpenUnpaidRecords,
                        insideMargin = PaddingValues(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LiveStatusDot(color = MiuixTheme.colorScheme.error, size = 8.dp)
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "입금 대기 중인 미수금 ${unpaidPair.second.size}건",
                                        style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold),
                                        color = MiuixTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        text = "탭하여 미수금 내역 확인 및 입금 완료 처리",
                                        style = MiuixTheme.textStyles.footnote2,
                                        color = MiuixTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                            Text(
                                text = PayrollCalculator.formatWon(unpaidPair.first),
                                style = MiuixTheme.textStyles.title4.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = MiuixTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // 4. Wishlist Goals Live Progress Gauge
            item(key = "wishlist_section") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmallTitle(text = "목표 금액 (위시리스트) 달성률")
                    Text(
                        text = "+ 목표 추가",
                        style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Medium),
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable {
                                editingGoal = null
                                showGoalSheet = true
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                if (state.wishlistGoals.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            editingGoal = null
                            showGoalSheet = true
                        },
                        insideMargin = PaddingValues(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MiuixGlassIcon(
                                icon = MiuixIcons.Favorites,
                                contentDescription = null,
                                accentColor = MiuixTheme.colorScheme.primary
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    "갖고 싶은 목표 금액을 설정해 보세요",
                                    style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    "실시간 수익이 오를 때마다 목표 달성률이 함께 채워집니다",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                        }
                    }
                } else {
                    val liveCumulative = (monthSummary.totalNetPay + liveDashboard.totalEarnedWon).coerceAtLeast(0.0)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.wishlistGoals.forEach { goal ->
                            val pct = if (goal.targetAmount > 0L) {
                                (liveCumulative / goal.targetAmount.toDouble()).toFloat().coerceIn(0f, 1f)
                            } else 0f
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    editingGoal = goal
                                    showGoalSheet = true
                                },
                                insideMargin = PaddingValues(16.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            MiuixEntityBadge(
                                                emoji = goal.emoji,
                                                fallbackIcon = MiuixIcons.Favorites,
                                                accentColor = MiuixTheme.colorScheme.primary,
                                                size = 36.dp,
                                                cornerRadius = 10.dp
                                            )
                                            Text(
                                                text = goal.title,
                                                style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = "${String.format(Locale.US, "%.1f", pct * 100f)}%",
                                            style = MiuixTheme.textStyles.body2.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontFeatureSettings = "tnum"
                                            ),
                                            color = MiuixTheme.colorScheme.primary
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = pct,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = PayrollCalculator.formatWon(liveCumulative.toLong()),
                                            style = MiuixTheme.textStyles.footnote2.copy(fontFeatureSettings = "tnum"),
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                        )
                                        Text(
                                            text = "목표 ${PayrollCalculator.formatWon(goal.targetAmount)}",
                                            style = MiuixTheme.textStyles.footnote2.copy(fontFeatureSettings = "tnum"),
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Payday D-Day & Holiday Adjustment Cards
            if (paydays.isNotEmpty()) {
                item(key = "paydays_section") {
                    SmallTitle(text = "다가오는 월급날 D-Day")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        paydays.forEach { info ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onEditRegularJob(info.jobId) },
                                insideMargin = PaddingValues(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = info.jobName,
                                                style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium)
                                            )
                                            if (info.wasAdjusted) {
                                                SquirclePillBadge(
                                                    text = "주말·공휴일 보정",
                                                    backgroundColor = Color(0xFFD97706).copy(alpha = 0.14f),
                                                    contentColor = Color(0xFFD97706)
                                                )
                                            }
                                        }
                                        val dateText = if (info.wasAdjusted) {
                                            "예정 ${info.nominalPayday} → 실제 지급 ${info.adjustedPayday}"
                                        } else {
                                            "지급 예정일 ${info.adjustedPayday}"
                                        }
                                        Text(
                                            text = "$dateText · 예상 ${PayrollCalculator.formatWon(info.estimatedPeriodNetPay)}",
                                            style = MiuixTheme.textStyles.footnote2.copy(fontFeatureSettings = "tnum"),
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                        )
                                    }
                                    SquirclePillBadge(
                                        text = if (info.dDay == 0L) "D-DAY" else "D-${info.dDay}",
                                        backgroundColor = Color(info.colorHex).copy(alpha = 0.14f),
                                        contentColor = Color(info.colorHex)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. Registered Jobs list
            if (state.jobs.isNotEmpty()) {
                item(key = "registered_jobs") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SmallTitle(text = "내 알바 목록 (${state.jobs.size})")
                        Text(
                            text = "+ 정규 알바",
                            style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Medium),
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable(onClick = onAddRegularJob)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.jobs.forEach { job ->
                            val currentWage = PayrollCalculator.effectiveJobWage(job, LocalDate.now())
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onEditRegularJob(job.id) },
                                insideMargin = PaddingValues(16.dp)
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
                                        MiuixEntityBadge(
                                            emoji = job.emoji,
                                            fallbackIcon = MiuixIcons.Store,
                                            accentColor = Color(job.colorHex)
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = job.name,
                                                style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium)
                                            )
                                            val daysLabel = if (job.schedules.isEmpty()) "스케줄 없음 (스톱워치 전용)"
                                            else job.schedules.joinToString(",") { dayOfWeekKorean(it.dayOfWeek) }
                                            Text(
                                                text = "${job.payType.label} ${PayrollCalculator.formatWon(currentWage)} · $daysLabel",
                                                style = MiuixTheme.textStyles.footnote2.copy(fontFeatureSettings = "tnum"),
                                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = MiuixIcons.ChevronForward,
                                        contentDescription = "수정",
                                        tint = MiuixTheme.colorScheme.onSurfaceVariantActions
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // True 100% Fullscreen Focus Mode Overlay when expanded
        if (isFullscreenFocus && liveDashboard.activeShifts.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .squircleBackground(MiuixTheme.colorScheme.surface, 0.dp)
                    .systemBarsPadding()
                    .padding(16.dp)
            ) {
                FocusTimerStage(
                    snapshot = liveDashboard,
                    selectedTab = selectedLiveTab,
                    onSelectTab = { selectedLiveTab = it },
                    selectedTangible = selectedTangible,
                    allTangibles = state.tangibleItems,
                    onSelectTangible = onSelectTangibleItem,
                    onManageTangibles = { showTangibleManager = true },
                    onToggleBreak = onToggleBreak,
                    onClockOut = onClockOut,
                    isFullscreen = true,
                    onToggleFullscreen = { onToggleFullscreenFocus(false) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Stopwatch Starter Sheet
        ManualStopwatchBottomSheet(
            show = showStopwatchSheet,
            jobs = state.jobs,
            defaultMinWage = state.settings.defaultMinWage,
            onDismiss = { showStopwatchSheet = false },
            onStart = { title, emoji, kind, payType, unitAmt, jobId, colorHex, plannedMins, breakMins, breakPayType, allowance ->
                onStartStopwatch(
                    title, emoji, kind, payType, unitAmt, jobId, colorHex,
                    plannedMins, breakMins, breakPayType, allowance
                )
                showStopwatchSheet = false
            }
        )

        // Tangible Conversion Manager Sheet
        TangibleItemsManagerSheet(
            show = showTangibleManager,
            items = state.tangibleItems,
            selectedId = state.settings.selectedTangibleItemId,
            onDismiss = { showTangibleManager = false },
            onSelect = {
                onSelectTangibleItem(it)
                showTangibleManager = false
            },
            onUpsert = onUpsertTangibleItem,
            onDelete = onDeleteTangibleItem
        )

        // Wishlist Goal Sheet
        WishlistGoalEditSheet(
            show = showGoalSheet,
            existingGoal = editingGoal,
            onDismiss = { showGoalSheet = false },
            onSave = {
                onUpsertWishlistGoal(it)
                showGoalSheet = false
            },
            onDelete = { id ->
                onDeleteWishlistGoal(id)
                showGoalSheet = false
            }
        )
    }
}

@Composable
private fun EmptyOnboardingFocusCard(
    defaultMinWage: Long,
    onAddRegularJob: () -> Unit,
    onQuickAddShortTerm: () -> Unit,
    onOpenStopwatch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .miuixGlassSurface(cornerRadius = 24.dp)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MiuixGlassIcon(
                icon = MiuixIcons.Stopwatch,
                contentDescription = null,
                accentColor = MiuixTheme.colorScheme.primary,
                size = 64.dp,
                iconSize = 30.dp,
                cornerRadius = 18.dp
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "실시간 급여 포커스 타이머",
                    style = MiuixTheme.textStyles.title3.copy(fontWeight = FontWeight.SemiBold),
                    color = MiuixTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "2026년 최저시급 ${PayrollCalculator.formatWon(defaultMinWage)}이 기본 설정되어 있습니다.\n알바를 등록하면 출근 시간마다 타이머가 자동으로 작동합니다.",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(
                    text = "정규 알바 추가",
                    onClick = onAddRegularJob,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "단기 10초 등록",
                    onClick = onQuickAddShortTerm,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = "등록 없이 바로 스톱워치 출근",
                style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Medium),
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier
                    .squircleClip(8.dp)
                    .clickable(onClick = onOpenStopwatch)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun StandbyFocusCard(
    jobs: List<Job>,
    templates: List<ShortTermTemplate>,
    onStartStopwatch: () -> Unit,
    onQuickStartJob: (Job) -> Unit,
    onQuickStartTemplate: (ShortTermTemplate) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        insideMargin = PaddingValues(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiveStatusDot(
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.45f),
                        size = 8.dp
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "현재 예정된 자동 근무 없음",
                            style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "대타·연장·단기 출근 시 즉시 스톱워치를 켤 수 있습니다",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
                MiuixGlassIcon(
                    icon = MiuixIcons.Stopwatch,
                    contentDescription = null,
                    accentColor = MiuixTheme.colorScheme.primary,
                    size = 40.dp
                )
            }

            if (jobs.isNotEmpty() || templates.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(jobs, key = { "job_${it.id}" }) { job ->
                        val prefix = if (job.emoji.isNotBlank()) "${job.emoji} " else ""
                        SquirclePillBadge(
                            text = "$prefix${job.name} 바로 출근",
                            backgroundColor = Color(job.colorHex).copy(alpha = 0.14f),
                            contentColor = Color(job.colorHex),
                            onClick = { onQuickStartJob(job) }
                        )
                    }
                    items(templates, key = { "tpl_${it.id}" }) { tpl ->
                        val prefix = if (tpl.emoji.isNotBlank()) "${tpl.emoji} " else ""
                        SquirclePillBadge(
                            text = "$prefix${tpl.name} 프리셋 출근",
                            backgroundColor = Color(tpl.colorHex).copy(alpha = 0.14f),
                            contentColor = Color(tpl.colorHex),
                            onClick = { onQuickStartTemplate(tpl) }
                        )
                    }
                }
            }

            TextButton(
                text = "즉시 출근 스톱워치 시작",
                onClick = onStartStopwatch,
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun FocusTimerStage(
    snapshot: LiveDashboardSnapshot,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    selectedTangible: TangibleItem,
    allTangibles: List<TangibleItem>,
    onSelectTangible: (String) -> Unit,
    onManageTangibles: () -> Unit,
    onToggleBreak: (String) -> Unit,
    onClockOut: (String) -> Unit,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasMultiple = snapshot.activeShifts.size >= 2
    val clampedTab = if (hasMultiple) selectedTab.coerceIn(0, snapshot.activeShifts.size) else 0
    val isCombinedView = hasMultiple && clampedTab == 0
    val focusedShift = if (hasMultiple && clampedTab > 0) {
        snapshot.activeShifts[clampedTab - 1]
    } else {
        snapshot.activeShifts.first()
    }

    val displayEarned = if (isCombinedView) snapshot.totalEarnedWon else focusedShift.liveEarnedWon
    val displayRatePerSec = if (isCombinedView) snapshot.totalPerSecondRateWon else focusedShift.perSecondRateWon
    val displayRatePerMin = if (isCombinedView) snapshot.totalPerMinuteRateWon else focusedShift.perMinuteRateWon
    val showBoost = if (isCombinedView) snapshot.anyBoostActive else focusedShift.isBoostActive
    val boostMult = if (isCombinedView) snapshot.maxBoostMultiplier else focusedShift.boostMultiplier

    val accentColor = if (isCombinedView) MiuixTheme.colorScheme.primary else Color(focusedShift.colorHex)
    val isPaused = !isCombinedView && focusedShift.isPaused
    val statusDotColor = if (isPaused) Color(0xFFD97706) else Color(0xFF16A34A)
    val secondFraction = ((focusedShift.elapsedWorkMillis % 60_000L) / 60_000f).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .miuixGlassSurface(cornerRadius = if (isFullscreen) 24.dp else 22.dp)
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar: concurrent switcher + shift status + boost + fullscreen focus toggle
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (hasMultiple) {
                    val tabLabels = buildList {
                        add("통합 (${snapshot.activeShifts.size})")
                        snapshot.activeShifts.forEach {
                            val prefix = if (it.emoji.isNotBlank()) "${it.emoji} " else ""
                            add("$prefix${it.title}")
                        }
                    }
                    TabRowWithContour(
                        tabs = tabLabels,
                        selectedTabIndex = clampedTab,
                        onTabSelected = onSelectTab
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        LiveStatusDot(color = statusDotColor, size = 8.dp)
                        if (!isCombinedView && focusedShift.emoji.isNotBlank()) {
                            Text(text = focusedShift.emoji, fontSize = 18.sp)
                        }
                        Column {
                            Text(
                                text = when {
                                    isCombinedView -> "${snapshot.activeShifts.size}개 알바 동시 진행 중"
                                    focusedShift.isPaused -> focusedShift.pauseReason ?: "휴게 시간"
                                    else -> focusedShift.title
                                },
                                style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isCombinedView) "합산 실시간 포커스" else if (isPaused) "적립 일시정지" else "실시간 적립 중",
                                style = MiuixTheme.textStyles.footnote2,
                                color = if (isPaused) Color(0xFFD97706) else MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (showBoost) {
                            val multStr = if (boostMult == 1.5) "1.5" else String.format(Locale.US, "%.1f", boostMult)
                            SquirclePillBadge(
                                text = "${multStr}배 수당 부스트",
                                backgroundColor = Color(0xFFEA580C).copy(alpha = 0.14f),
                                contentColor = Color(0xFFEA580C)
                            )
                        }
                        SquirclePillBadge(
                            text = if (isFullscreen) "축소" else "전체 화면",
                            backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
                            contentColor = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            onClick = onToggleFullscreen
                        )
                    }
                }
            }

            // Centerpiece: Large Precision Focus Chronometer Ring + 0.1s Tabular Odometer
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(vertical = 8.dp)
                    .size(if (isFullscreen) 292.dp else 256.dp),
                contentAlignment = Alignment.Center
            ) {
                FocusTimerRingCanvas(
                    progress = if (isCombinedView) null else focusedShift.progressFraction,
                    accentColor = accentColor,
                    trackColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    tickColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    isPaused = isPaused,
                    secondFraction = secondFraction,
                    modifier = Modifier.fillMaxSize()
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Text(
                        text = if (isCombinedView) "통합 누적 수익" else "실시간 수익",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    OdometerCurrencyText(
                        amountWon = displayEarned,
                        textStyle = MiuixTheme.textStyles.title1.copy(
                            fontSize = if (isFullscreen) 34.sp else 30.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Text(
                        text = String.format(
                            Locale.US,
                            "+%.2f원/초 · +%.0f원/분",
                            displayRatePerSec,
                            displayRatePerMin
                        ),
                        style = MiuixTheme.textStyles.footnote1.copy(
                            fontWeight = FontWeight.Medium,
                            fontFeatureSettings = "tnum"
                        ),
                        color = accentColor
                    )

                    val progress = focusedShift.progressFraction
                    val remMs = focusedShift.remainingMillis
                    if (!isCombinedView && progress != null && remMs != null) {
                        SquirclePillBadge(
                            text = "${String.format(Locale.US, "%.1f", progress * 100f)}% · 남음 ${PayrollCalculator.formatCountdown(remMs)}",
                            backgroundColor = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            contentColor = MiuixTheme.colorScheme.onSecondaryContainer
                        )
                    } else {
                        SquirclePillBadge(
                            text = "실근무 ${PayrollCalculator.formatCountdown(focusedShift.elapsedWorkMillis)}",
                            backgroundColor = MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            contentColor = MiuixTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Bottom Section: Tangible conversion + Break / Clock-out Ergonomic Action Bar
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val qty = if (selectedTangible.unitPrice > 0L) displayEarned / selectedTangible.unitPrice else 0.0
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${selectedTangible.emoji} ${selectedTangible.name} ${String.format(Locale.US, "%.2f", qty)}개",
                            style = MiuixTheme.textStyles.body2.copy(
                                fontWeight = FontWeight.Medium,
                                fontFeatureSettings = "tnum"
                            ),
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "체감 항목 관리",
                            style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Medium),
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier
                                .squircleClip(8.dp)
                                .clickable(onClick = onManageTangibles)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(allTangibles, key = { it.id }) { item ->
                            val active = item.id == selectedTangible.id
                            SquirclePillBadge(
                                text = "${item.emoji} ${item.name}",
                                backgroundColor = if (active) accentColor else MiuixTheme.colorScheme.secondaryContainer,
                                contentColor = if (active) Color.White else MiuixTheme.colorScheme.onSecondaryContainer,
                                onClick = { onSelectTangible(item.id) }
                            )
                        }
                    }
                }

                val actionTarget = focusedShift
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        text = if (actionTarget.isManualPaused) "휴게 종료" else "휴게 시작",
                        onClick = { onToggleBreak(actionTarget.runtimeKey) },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "퇴근 완료",
                        onClick = { onClockOut(actionTarget.runtimeKey) },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ManualStopwatchBottomSheet(
    show: Boolean,
    jobs: List<Job>,
    defaultMinWage: Long,
    onDismiss: () -> Unit,
    onStart: (
        title: String,
        emoji: String,
        kind: JobKind,
        payType: PayType,
        unitAmount: Long,
        jobId: String?,
        colorHex: Long,
        plannedDurationMinutes: Int?,
        unpaidBreakMinutes: Int,
        breakPayType: BreakPayType,
        allowance: AllowanceSettings
    ) -> Unit
) {
    var selectedJobId by remember(show) { mutableStateOf<String?>(null) }
    var title by remember(show) { mutableStateOf("") }
    var emoji by remember(show) { mutableStateOf("") }
    var colorHex by remember(show) { mutableLongStateOf(0xFF3482FF) }
    var kind by remember(show) { mutableStateOf(JobKind.SHORT_TERM) }
    var payType by remember(show) { mutableStateOf(PayType.HOURLY) }
    var amountText by remember(show) { mutableStateOf(defaultMinWage.toString()) }
    var plannedHoursText by remember(show) { mutableStateOf("") }
    var unpaidBreakText by remember(show) { mutableStateOf("0") }
    var nightBoost by remember(show) { mutableStateOf(false) }

    OverlayBottomSheet(
        show = show,
        title = "즉시 출근 스톱워치",
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (jobs.isNotEmpty()) {
                Text(
                    "등록된 알바에서 바로 불러오기 (연장·대타)",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(jobs, key = { it.id }) { job ->
                        val picked = selectedJobId == job.id
                        val prefix = if (job.emoji.isNotBlank()) "${job.emoji} " else ""
                        SquirclePillBadge(
                            text = "$prefix${job.name}",
                            backgroundColor = if (picked) Color(job.colorHex) else Color(job.colorHex).copy(alpha = 0.14f),
                            contentColor = if (picked) Color.White else Color(job.colorHex),
                            onClick = {
                                selectedJobId = job.id
                                title = job.name
                                emoji = job.emoji
                                colorHex = job.colorHex
                                kind = job.kind
                                payType = job.payType
                                amountText = PayrollCalculator.effectiveJobWage(job, LocalDate.now()).toString()
                                nightBoost = job.allowance.nightAllowanceEnabled
                            }
                        )
                    }
                }
            }

            TextField(
                value = title,
                onValueChange = {
                    title = it
                    selectedJobId = null
                },
                label = "근무 이름 (예: 카페 대타, 연장 근무)",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            EmojiAndColorIdentityRow(
                emoji = emoji,
                fallbackIcon = MiuixIcons.Stopwatch,
                colorHex = colorHex,
                onEmojiChange = { emoji = it },
                onColorChange = { colorHex = it }
            )

            TabRowWithContour(
                tabs = PayType.entries.map { it.label },
                selectedTabIndex = PayType.entries.indexOf(payType),
                onTabSelected = { payType = PayType.entries[it] }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter(Char::isDigit) },
                    label = "${payType.label} (원)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TextField(
                    value = plannedHoursText,
                    onValueChange = { plannedHoursText = it.filter(Char::isDigit) },
                    label = "목표 시간 (선택)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = unpaidBreakText,
                    onValueChange = { unpaidBreakText = it.filter(Char::isDigit) },
                    label = "비례 무급휴게(분)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Box(modifier = Modifier.weight(1f).align(Alignment.CenterVertically)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("야간 1.5배", style = MiuixTheme.textStyles.body2)
                        Switch(checked = nightBoost, onCheckedChange = { nightBoost = it })
                    }
                }
            }

            TextButton(
                text = "스톱워치 출근 시작",
                onClick = {
                    val amt = amountText.toLongOrNull() ?: defaultMinWage
                    val plannedMins = plannedHoursText.toIntOrNull()?.takeIf { it > 0 }?.times(60)
                    val breakMins = unpaidBreakText.toIntOrNull() ?: 0
                    val chosenJob = jobs.find { it.id == selectedJobId }
                    onStart(
                        title.trim().ifEmpty { "즉시 출근 알바" },
                        emoji,
                        kind,
                        payType,
                        amt,
                        selectedJobId,
                        colorHex,
                        plannedMins,
                        breakMins,
                        if (breakMins > 0) BreakPayType.UNPAID else BreakPayType.PAID,
                        chosenJob?.allowance ?: AllowanceSettings(nightAllowanceEnabled = nightBoost)
                    )
                },
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TangibleItemsManagerSheet(
    show: Boolean,
    items: List<TangibleItem>,
    selectedId: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onUpsert: (TangibleItem) -> Unit,
    onDelete: (String) -> Unit
) {
    var editingItem by remember(show) { mutableStateOf<TangibleItem?>(null) }
    var nameText by remember(show) { mutableStateOf("") }
    var emojiText by remember(show) { mutableStateOf("🍕") }
    var priceText by remember(show) { mutableStateOf("") }
    var showEmojiPicker by remember { mutableStateOf(false) }

    OverlayBottomSheet(
        show = show,
        title = "체감 환산 아이템 관리",
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items.forEach { item ->
                val isSelected = item.id == selectedId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .squircleBackground(
                            if (isSelected) MiuixTheme.colorScheme.secondaryContainer else MiuixTheme.colorScheme.surfaceContainer,
                            12.dp
                        )
                        .squircleClip(12.dp)
                        .clickable { onSelect(item.id) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(item.emoji, fontSize = 20.sp)
                        Column {
                            Text(
                                item.name,
                                style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                PayrollCalculator.formatWon(item.unitPrice),
                                style = MiuixTheme.textStyles.footnote2.copy(fontFeatureSettings = "tnum"),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }
                    Row {
                        IconButton(onClick = {
                            editingItem = item
                            nameText = item.name
                            emojiText = item.emoji
                            priceText = item.unitPrice.toString()
                        }) {
                            Icon(MiuixIcons.Edit, contentDescription = "수정")
                        }
                        if (items.size > 1) {
                            IconButton(onClick = { onDelete(item.id) }) {
                                Icon(MiuixIcons.Delete, contentDescription = "삭제")
                            }
                        }
                    }
                }
            }

            HorizontalDivider()
            Text(
                if (editingItem == null) "새 체감 아이템 추가" else "아이템 수정",
                style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .squircleBackground(MiuixTheme.colorScheme.secondaryContainer, 12.dp)
                        .squircleClip(12.dp)
                        .clickable { showEmojiPicker = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(emojiText.ifBlank { "🎁" }, fontSize = 24.sp)
                }
                TextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = "품목 이름",
                    singleLine = true,
                    modifier = Modifier.weight(1.2f)
                )
                TextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter(Char::isDigit) },
                    label = "가격(원)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            TextButton(
                text = if (editingItem == null) "커스텀 아이템 추가" else "수정 저장",
                onClick = {
                    val clean = nameText.trim()
                    val price = priceText.toLongOrNull() ?: 0L
                    if (clean.isNotEmpty() && price > 0L) {
                        val next = TangibleItem(
                            id = editingItem?.id ?: UUID.randomUUID().toString(),
                            name = clean,
                            emoji = emojiText.ifBlank { "🎁" },
                            unitPrice = price,
                            isDefault = editingItem?.isDefault ?: false
                        )
                        onUpsert(next)
                        editingItem = null
                        nameText = ""
                        priceText = ""
                    }
                },
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    MiuixEmojiPickerSheet(
        show = showEmojiPicker,
        title = "아이템 이모지 선택",
        currentEmoji = emojiText,
        allowClear = false,
        onDismiss = { showEmojiPicker = false },
        onSelect = { emojiText = it.ifBlank { "🎁" } }
    )
}

@Composable
private fun WishlistGoalEditSheet(
    show: Boolean,
    existingGoal: WishlistGoal?,
    onDismiss: () -> Unit,
    onSave: (WishlistGoal) -> Unit,
    onDelete: (String) -> Unit
) {
    var titleText by remember(show, existingGoal) { mutableStateOf(existingGoal?.title ?: "") }
    var emojiText by remember(show, existingGoal) { mutableStateOf(existingGoal?.emoji ?: "🎯") }
    var targetText by remember(show, existingGoal) { mutableStateOf(existingGoal?.targetAmount?.toString() ?: "") }
    var showEmojiPicker by remember { mutableStateOf(false) }

    OverlayBottomSheet(
        show = show,
        title = if (existingGoal == null) "목표 금액 추가" else "목표 금액 수정",
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .squircleBackground(MiuixTheme.colorScheme.secondaryContainer, 14.dp)
                        .squircleClip(14.dp)
                        .clickable { showEmojiPicker = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text(emojiText.ifBlank { "🎯" }, fontSize = 24.sp)
                }
                TextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = "목표 (예: 일본 여행, 에어팟)",
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = "왼쪽 아이콘을 눌러 원하는 이모지를 선택할 수 있습니다",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            TextField(
                value = targetText,
                onValueChange = { targetText = it.filter(Char::isDigit) },
                label = "목표 금액 (원)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (existingGoal != null) {
                    TextButton(
                        text = "삭제",
                        onClick = { onDelete(existingGoal.id) },
                        modifier = Modifier.weight(0.8f)
                    )
                }
                TextButton(
                    text = "목표 저장",
                    onClick = {
                        val clean = titleText.trim()
                        val target = targetText.toLongOrNull() ?: 0L
                        if (clean.isNotEmpty() && target > 0L) {
                            onSave(
                                WishlistGoal(
                                    id = existingGoal?.id ?: UUID.randomUUID().toString(),
                                    title = clean,
                                    emoji = emojiText.ifBlank { "🎯" },
                                    targetAmount = target,
                                    createdAtMillis = existingGoal?.createdAtMillis ?: System.currentTimeMillis()
                                )
                            )
                        }
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1.2f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    MiuixEmojiPickerSheet(
        show = showEmojiPicker,
        title = "목표 이모지 선택",
        currentEmoji = emojiText,
        allowClear = false,
        onDismiss = { showEmojiPicker = false },
        onSelect = { emojiText = it.ifBlank { "🎯" } }
    )
}
