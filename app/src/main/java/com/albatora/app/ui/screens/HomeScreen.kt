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
import androidx.compose.ui.unit.dp
import com.albatora.app.domain.*
import com.albatora.app.model.*
import com.albatora.app.ui.components.*
import com.albatora.app.ui.theme.LocalIsDarkTheme
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.squircle.squircleBackground
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
    onAddRegularJob: () -> Unit,
    onEditRegularJob: (String) -> Unit,
    onQuickAddShortTerm: () -> Unit,
    onOpenUnpaidRecords: () -> Unit,
    onToggleBreak: (String) -> Unit,
    onClockOut: (String) -> Unit,
    onStartStopwatch: (
        title: String,
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

    val selectedTangible = state.tangibleItems.find { it.id == state.settings.selectedTangibleItemId }
        ?: state.tangibleItems.first()

    val completelyEmpty = state.jobs.isEmpty() &&
        state.records.isEmpty() &&
        liveDashboard.activeShifts.isEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Live Earnings Hero Glass Card (when >=1 active regular OR manual stopwatch shift)
            if (liveDashboard.activeShifts.isNotEmpty()) {
                item(key = "live_tracker_hero") {
                    LiveTrackerHeroCard(
                        snapshot = liveDashboard,
                        selectedTab = selectedLiveTab,
                        onSelectTab = { selectedLiveTab = it },
                        selectedTangible = selectedTangible,
                        allTangibles = state.tangibleItems,
                        onSelectTangible = onSelectTangibleItem,
                        onManageTangibles = { showTangibleManager = true },
                        onToggleBreak = onToggleBreak,
                        onClockOut = onClockOut
                    )
                }
            }

            // 2. Empty State OR Action Launcher Bar
            if (completelyEmpty) {
                item(key = "empty_welcome") {
                    EmptyDataBanner(
                        icon = MiuixIcons.Stopwatch,
                        title = "아직 등록된 알바가 없어요",
                        subtitle = "2026년 최저시급 ${PayrollCalculator.formatWon(state.settings.defaultMinWage)}이 기본 설정되어 있습니다.\n정규 알바나 단기·대타 알바를 추가해 실시간 수익을 확인해 보세요!",
                        actionLabel = "정규/고정 알바 추가하기",
                        onAction = onAddRegularJob
                    )
                }
            }

            // Separated registration + instant stopwatch controls (Q3=C, Q7=B)
            item(key = "quick_launchers") {
                SmallTitle(text = "빠른 근무 등록 · 즉시 출근")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionTile(
                        title = "정규 알바 추가",
                        subtitle = "고정 스케줄·주휴",
                        icon = MiuixIcons.Store,
                        accent = Color(0xFF3482FF),
                        modifier = Modifier.weight(1f),
                        onClick = onAddRegularJob
                    )
                    QuickActionTile(
                        title = "단기 10초 등록",
                        subtitle = "일당·세금·미수금",
                        icon = MiuixIcons.AddCircle,
                        accent = Color(0xFF10B981),
                        modifier = Modifier.weight(1f),
                        onClick = onQuickAddShortTerm
                    )
                    QuickActionTile(
                        title = "즉시 출근",
                        subtitle = "스톱워치 시작",
                        icon = MiuixIcons.Stopwatch,
                        accent = Color(0xFFFF6B4A),
                        modifier = Modifier.weight(1f),
                        onClick = { showStopwatchSheet = true }
                    )
                }
            }

            // 3. Monthly Earnings & Unpaid Summary
            if (!completelyEmpty) {
                item(key = "monthly_overview") {
                    val livePlusMonth = monthSummary.totalNetPay + liveDashboard.totalEarnedWon.toLong()
                    SmallTitle(text = "${currentMonth.monthValue}월 누적 수익 현황")
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        insideMargin = PaddingValues(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "실수령 예상 누적 (실시간 포함)",
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    )
                                    Text(
                                        text = PayrollCalculator.formatWon(livePlusMonth),
                                        style = MiuixTheme.textStyles.title2.copy(fontWeight = FontWeight.Bold),
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
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "완료 근무 ${monthSummary.shiftCount}건 (${PayrollCalculator.formatDurationKorean(monthSummary.totalWorkMinutes)})",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                                if (monthSummary.totalWeeklyHolidayPay > 0L) {
                                    Text(
                                        text = "주휴 +${PayrollCalculator.formatWon(monthSummary.totalWeeklyHolidayPay)}",
                                        style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color(0xFF10B981)
                                    )
                                }
                            }
                            if (selectedTangible.unitPrice > 0L && livePlusMonth > 0L) {
                                val count = livePlusMonth.toDouble() / selectedTangible.unitPrice.toDouble()
                                Text(
                                    text = "${selectedTangible.emoji} 이번 달 ${selectedTangible.name} 약 ${String.format(Locale.US, "%.1f", count)}개 상당",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // 4. Unpaid Alert Card (Q8-1)
            if (unpaidPair.first > 0L) {
                item(key = "unpaid_alert") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.errorContainer.copy(alpha = if (isDark) 0.45f else 0.7f)
                        ),
                        onClick = onOpenUnpaidRecords,
                        insideMargin = PaddingValues(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "\uD83D\uDD34 아직 못 받은 미수금 (${unpaidPair.second.size}건)",
                                    style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Bold),
                                    color = MiuixTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "탭하여 미수금 내역 확인 및 입금 완료 체크",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                )
                            }
                            Text(
                                text = PayrollCalculator.formatWon(unpaidPair.first),
                                style = MiuixTheme.textStyles.title4.copy(fontWeight = FontWeight.Bold),
                                color = MiuixTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // 5. Wishlist Goals Live Progress Gauge (Q15-1)
            item(key = "wishlist_section") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmallTitle(text = "목표 금액 (위시리스트) 달성률")
                    Text(
                        text = "+ 목표 추가",
                        style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.SemiBold),
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
                                accentColor = Color(0xFFF59E0B)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "갖고 싶은 목표 금액을 설정해 보세요",
                                    style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    "실시간 수익이 오를 때마다 목표 달성률(%) 게이지가 함께 채워집니다.",
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
                                        Text(
                                            text = "${goal.emoji} ${goal.title}",
                                            style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            text = "${String.format(Locale.US, "%.1f", pct * 100f)}%",
                                            style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Bold),
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
                                            style = MiuixTheme.textStyles.footnote2,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                        )
                                        Text(
                                            text = "목표 ${PayrollCalculator.formatWon(goal.targetAmount)}",
                                            style = MiuixTheme.textStyles.footnote2,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 6. Payday D-Day & Holiday Adjustment Cards (Q17-3)
            if (paydays.isNotEmpty()) {
                item(key = "paydays_section") {
                    SmallTitle(text = "다가오는 월급날 D-Day (공휴일·주말 보정)")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        paydays.forEach { info ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onEditRegularJob(info.jobId) },
                                insideMargin = PaddingValues(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = info.jobName,
                                                style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                            if (info.wasAdjusted) {
                                                SquirclePillBadge(
                                                    text = "주말·공휴일 보정",
                                                    backgroundColor = Color(0xFFF59E0B).copy(alpha = 0.16f),
                                                    contentColor = Color(0xFFD97706)
                                                )
                                            }
                                        }
                                        val dateText = if (info.wasAdjusted) {
                                            "예정 ${info.nominalPayday} -> 실제 지급 ${info.adjustedPayday}"
                                        } else {
                                            "지급 예정일 ${info.adjustedPayday}"
                                        }
                                        Text(
                                            text = "$dateText · 예상 ${PayrollCalculator.formatWon(info.estimatedPeriodNetPay)}",
                                            style = MiuixTheme.textStyles.footnote2,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                        )
                                    }
                                    SquirclePillBadge(
                                        text = if (info.dDay == 0L) "D-DAY" else "D-${info.dDay}",
                                        backgroundColor = Color(info.colorHex).copy(alpha = 0.15f),
                                        contentColor = Color(info.colorHex)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 7. Registered Jobs list
            if (state.jobs.isNotEmpty()) {
                item(key = "registered_jobs") {
                    SmallTitle(text = "내 알바 목록 (${state.jobs.size}개)")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.jobs.forEach { job ->
                            val currentWage = PayrollCalculator.effectiveJobWage(job, LocalDate.now())
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onEditRegularJob(job.id) },
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
                                        MiuixGlassIcon(
                                            icon = MiuixIcons.Store,
                                            contentDescription = null,
                                            accentColor = Color(job.colorHex)
                                        )
                                        Column {
                                            Text(
                                                text = job.name,
                                                style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.SemiBold)
                                            )
                                            val daysLabel = if (job.schedules.isEmpty()) "스케줄 없음 (스톱워치 전용)"
                                            else job.schedules.joinToString(",") { dayOfWeekKorean(it.dayOfWeek) }
                                            Text(
                                                text = "${job.payType.label} ${PayrollCalculator.formatWon(currentWage)} · $daysLabel",
                                                style = MiuixTheme.textStyles.footnote2,
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

        // Stopwatch Starter Sheet (Q3=C)
        ManualStopwatchBottomSheet(
            show = showStopwatchSheet,
            jobs = state.jobs,
            defaultMinWage = state.settings.defaultMinWage,
            onDismiss = { showStopwatchSheet = false },
            onStart = { title, kind, payType, unitAmt, jobId, colorHex, plannedMins, breakMins, breakPayType, allowance ->
                onStartStopwatch(
                    title, kind, payType, unitAmt, jobId, colorHex,
                    plannedMins, breakMins, breakPayType, allowance
                )
                showStopwatchSheet = false
            }
        )

        // Tangible Conversion Manager Sheet (Q19=A)
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

        // Wishlist Goal Sheet (Q15-1)
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
private fun LiveTrackerHeroCard(
    snapshot: LiveDashboardSnapshot,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    selectedTangible: TangibleItem,
    allTangibles: List<TangibleItem>,
    onSelectTangible: (String) -> Unit,
    onManageTangibles: () -> Unit,
    onToggleBreak: (String) -> Unit,
    onClockOut: (String) -> Unit
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .miuixGlassSurface(
                cornerRadius = 22.dp,
                tintColor = accentColor.copy(alpha = 0.14f),
                useBigHighlight = true
            )
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Concurrent job switcher chips/tabs (Q18=A)
            if (hasMultiple) {
                val tabLabels = buildList {
                    add("통합 합산 (${snapshot.activeShifts.size})")
                    snapshot.activeShifts.forEach { add(it.title) }
                }
                TabRowWithContour(
                    tabs = tabLabels,
                    selectedTabIndex = clampedTab,
                    onTabSelected = onSelectTab
                )
            }

            // Header + Conditional 1.5x Boost Badge (Q5-3)
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
                    SquirclePillBadge(
                        text = when {
                            isCombinedView -> "🟢 ${snapshot.activeShifts.size}개 동시 근무 중"
                            focusedShift.isPaused -> "⏸ ${focusedShift.pauseReason ?: "휴게 중"}"
                            else -> "🟢 ${focusedShift.title}"
                        },
                        backgroundColor = if (!isCombinedView && focusedShift.isPaused) {
                            Color(0xFFF59E0B).copy(alpha = 0.2f)
                        } else {
                            Color(0xFF10B981).copy(alpha = 0.2f)
                        },
                        contentColor = if (!isCombinedView && focusedShift.isPaused) {
                            Color(0xFFD97706)
                        } else {
                            Color(0xFF059669)
                        }
                    )
                }

                if (showBoost) {
                    val multStr = if (boostMult == 1.5) "1.5" else String.format(Locale.US, "%.1f", boostMult)
                    SquirclePillBadge(
                        text = "\uD83D\uDD25 ${multStr}배 수당 부스트 적용 중",
                        backgroundColor = Color(0xFFFF5722).copy(alpha = 0.2f),
                        contentColor = Color(0xFFE64A19)
                    )
                }
            }

            // 0.1-second odometer currency display (Q4=B)
            Column {
                Text(
                    text = if (isCombinedView) "통합 실시간 누적 수익" else "지금까지 번 금액",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                OdometerCurrencyText(
                    amountWon = displayEarned,
                    textStyle = MiuixTheme.textStyles.title1.copy(fontWeight = FontWeight.ExtraBold),
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = String.format(
                        Locale.US,
                        "+%.2f원/초 · +%.0f원/분",
                        displayRatePerSec,
                        displayRatePerMin
                    ),
                    style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold),
                    color = accentColor
                )
            }

            // Progress bar + Remaining Countdown (Q5-1)
            val progress = focusedShift.progressFraction
            val remMs = focusedShift.remainingMillis
            if (!isCombinedView && progress != null && remMs != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "현재 시프트 진행률 ${String.format(Locale.US, "%.1f", progress * 100f)}%",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        Text(
                            text = "퇴근까지 ${PayrollCalculator.formatCountdown(remMs)} 남음",
                            style = MiuixTheme.textStyles.footnote2.copy(fontWeight = FontWeight.SemiBold),
                            color = MiuixTheme.colorScheme.onSurface
                        )
                    }
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Text(
                    text = "실근무 누적: ${PayrollCalculator.formatCountdown(focusedShift.elapsedWorkMillis)}",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }

            // Tangible item conversion (Q5-4, Q19=A)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val qty = if (selectedTangible.unitPrice > 0L) displayEarned / selectedTangible.unitPrice else 0.0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedTangible.emoji} ${selectedTangible.name} ${String.format(Locale.US, "%.2f", qty)}개 획득!",
                        style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold),
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "항목 관리",
                        style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.SemiBold),
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable(onClick = onManageTangibles)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(allTangibles, key = { it.id }) { item ->
                        val active = item.id == selectedTangible.id
                        SquirclePillBadge(
                            text = "${item.emoji} ${item.name}",
                            backgroundColor = if (active) accentColor else MiuixTheme.colorScheme.surface.copy(alpha = 0.6f),
                            contentColor = if (active) Color.White else MiuixTheme.colorScheme.onSurface,
                            onClick = { onSelectTangible(item.id) }
                        )
                    }
                }
            }

            // Manual Break & Clock-out Action Buttons (Q5-5)
            val actionTarget = focusedShift
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    text = if (actionTarget.isManualPaused) "▶ 휴게 종료" else "⏸ 휴게 시작",
                    onClick = { onToggleBreak(actionTarget.runtimeKey) },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "⏹ 퇴근 완료",
                    onClick = { onClockOut(actionTarget.runtimeKey) },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(12.dp),
        onClick = onClick
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MiuixGlassIcon(
                icon = icon,
                contentDescription = title,
                accentColor = accent,
                size = 36.dp,
                iconSize = 18.dp,
                cornerRadius = 10.dp
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1
                )
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
    var kind by remember(show) { mutableStateOf(JobKind.SHORT_TERM) }
    var payType by remember(show) { mutableStateOf(PayType.HOURLY) }
    var amountText by remember(show) { mutableStateOf(defaultMinWage.toString()) }
    var plannedHoursText by remember(show) { mutableStateOf("") }
    var unpaidBreakText by remember(show) { mutableStateOf("0") }
    var nightBoost by remember(show) { mutableStateOf(false) }

    OverlayBottomSheet(
        show = show,
        title = "즉시 출근 (스톱워치 시작)",
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (jobs.isNotEmpty()) {
                Text(
                    "등록된 알바에서 바로 선택 (연장/대타 등)",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(jobs, key = { it.id }) { job ->
                        val picked = selectedJobId == job.id
                        SquirclePillBadge(
                            text = job.name,
                            backgroundColor = if (picked) Color(job.colorHex) else Color(job.colorHex).copy(alpha = 0.16f),
                            contentColor = if (picked) Color.White else Color(job.colorHex),
                            onClick = {
                                selectedJobId = job.id
                                title = job.name
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
                label = "근무 이름 (예: 대타 알바, 연장 근무, 단기 스태프)",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
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

            Spacer(modifier = Modifier.height(6.dp))
            TextButton(
                text = "▶ 스톱워치 출근 시작",
                onClick = {
                    val amt = amountText.toLongOrNull() ?: defaultMinWage
                    val plannedMins = plannedHoursText.toIntOrNull()?.takeIf { it > 0 }?.times(60)
                    val breakMins = unpaidBreakText.toIntOrNull() ?: 0
                    val chosenJob = jobs.find { it.id == selectedJobId }
                    onStart(
                        title.trim().ifEmpty { "즉시 출근 알바" },
                        kind,
                        payType,
                        amt,
                        selectedJobId,
                        chosenJob?.colorHex ?: 0xFFFF6B4A,
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
    var nameText by remember { mutableStateOf("") }
    var emojiText by remember { mutableStateOf("🍕") }
    var priceText by remember { mutableStateOf("") }

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
                            if (isSelected) MiuixTheme.colorScheme.secondaryContainer else MiuixTheme.colorScheme.surfaceVariant,
                            12.dp
                        )
                        .clickable { onSelect(item.id) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${item.emoji} ${item.name}",
                            style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            PayrollCalculator.formatWon(item.unitPrice),
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
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
                style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Bold)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = emojiText,
                    onValueChange = { if (it.length <= 4) emojiText = it },
                    label = "이모지",
                    singleLine = true,
                    modifier = Modifier.weight(0.6f)
                )
                TextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = "품목 이름",
                    singleLine = true,
                    modifier = Modifier.weight(1.4f)
                )
                TextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter(Char::isDigit) },
                    label = "가격(원)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1.1f)
                )
            }
            TextButton(
                text = if (editingItem == null) "+ 커스텀 아이템 추가" else "수정 저장",
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
    var emojiText by remember(show, existingGoal) { mutableStateOf(existingGoal?.emoji ?: "\uD83C\uDFAF") }
    var targetText by remember(show, existingGoal) { mutableStateOf(existingGoal?.targetAmount?.toString() ?: "") }

    OverlayBottomSheet(
        show = show,
        title = if (existingGoal == null) "목표 금액 추가" else "목표 금액 수정",
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = emojiText,
                    onValueChange = { if (it.length <= 4) emojiText = it },
                    label = "이모지",
                    singleLine = true,
                    modifier = Modifier.weight(0.55f)
                )
                TextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = "목표 (예: 일본 여행, 에어팟)",
                    singleLine = true,
                    modifier = Modifier.weight(1.45f)
                )
            }
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
                                    emoji = emojiText.ifBlank { "\uD83C\uDFAF" },
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
}
