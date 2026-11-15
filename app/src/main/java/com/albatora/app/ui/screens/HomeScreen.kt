package com.albatora.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import com.albatora.app.domain.*
import com.albatora.app.model.*
import com.albatora.app.ui.components.*
import com.albatora.app.ui.theme.AlbaIconCatalog
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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
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
    onConsumeStopwatchTrigger: () -> Unit,
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
    onDeleteWishlistGoal: (String) -> Unit,
    onArchiveWishlistGoal: (String, Long) -> Unit = { _, _ -> },
    onDeleteArchivedGoal: (String) -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onOpenCalculator: () -> Unit = {},
    onOpenWidgetStudio: () -> Unit = {},
    topBarInset: Dp = 0.dp,
    scrollToTopTrigger: Int = 0,
    onLiveHeaderCollapseChange: (Float) -> Unit = {},
    onHomeScrollChange: (Boolean) -> Unit = {}
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
    var showGoalArchiveSheet by remember { mutableStateOf(false) }

    LaunchedEffect(openStopwatchTrigger) {
        if (openStopwatchTrigger > 0) {
            showStopwatchSheet = true
            onConsumeStopwatchTrigger()
        }
    }

    val selectedTangible = state.tangibleItems.find { it.id == state.settings.selectedTangibleItemId }
        ?: state.tangibleItems.firstOrNull()

    val completelyEmpty = state.jobs.isEmpty() &&
        state.records.isEmpty() &&
        state.templates.isEmpty() &&
        liveDashboard.activeShifts.isEmpty()

    val currentWeekWorkMinutes = remember(state) {
        val today = LocalDate.now()
        val nowDt = LocalDateTime.now()
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        (0..6).sumOf { offset ->
            PayrollCalculator.resolveCalendarDayItems(state, weekStart.plusDays(offset.toLong()), nowDt)
                .filter { it.status != ShiftStatus.ABSENT }
                .sumOf { item ->
                    val rec = item.record
                    if (rec != null) {
                        val raw = PayrollCalculator.rawDurationMinutes(rec.startMinute, rec.endMinute)
                        if (rec.breakPayType == BreakPayType.UNPAID) (raw - rec.breakMinutes).coerceAtLeast(0) else raw
                    } else {
                        val slot = item.scheduleSlot
                        val raw = PayrollCalculator.rawDurationMinutes(item.startMinute, item.endMinute)
                        if (slot != null && slot.breakPayType == BreakPayType.UNPAID) (raw - slot.breakMinutes).coerceAtLeast(0) else raw
                    }
                }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 600.dp
        val density = LocalDensity.current
        val haptic = LocalHapticFeedback.current
        val coroutineScope = rememberCoroutineScope()
        val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val bottomPad = 116.dp + navBottomInset
        val phoneListState = rememberLazyListState()
        val wideLeftListState = rememberLazyListState()
        val wideRightListState = rememberLazyListState()
        val activeListState = if (isWide) wideLeftListState else phoneListState

        var expandedHeroHeightDp by remember { mutableStateOf(416.dp) }
        val collapseRangePx = with(density) { 210.dp.toPx() }.coerceAtLeast(1f)

        LaunchedEffect(scrollToTopTrigger) {
            if (scrollToTopTrigger > 0) {
                activeListState.animateScrollToItem(0)
                scrollBehavior.state.contentOffset = 0f
            }
        }

        val rawCollapseFraction by remember(liveDashboard.activeShifts.isNotEmpty(), activeListState, collapseRangePx) {
            derivedStateOf {
                if (liveDashboard.activeShifts.isEmpty()) {
                    0f
                } else if (activeListState.firstVisibleItemIndex > 0) {
                    1f
                } else {
                    (activeListState.firstVisibleItemScrollOffset / collapseRangePx).coerceIn(0f, 1f)
                }
            }
        }
        val isHomeScrolled by remember(activeListState, wideRightListState, isWide) {
            derivedStateOf {
                val mainScrolled = activeListState.firstVisibleItemIndex > 0 ||
                    activeListState.firstVisibleItemScrollOffset > 12
                val sideScrolled = isWide && (
                    wideRightListState.firstVisibleItemIndex > 0 ||
                        wideRightListState.firstVisibleItemScrollOffset > 12
                    )
                mainScrolled || sideScrolled
            }
        }

        LaunchedEffect(rawCollapseFraction) {
            onLiveHeaderCollapseChange(rawCollapseFraction)
        }
        LaunchedEffect(isHomeScrolled) {
            onHomeScrollChange(isHomeScrolled)
        }
        DisposableEffect(Unit) {
            onDispose {
                onLiveHeaderCollapseChange(0f)
                onHomeScrollChange(false)
            }
        }

        val focusSection: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
            if (liveDashboard.activeShifts.isNotEmpty()) {
                item(key = "live_focus_stage_spacer") {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(expandedHeroHeightDp)
                    )
                }
            } else if (completelyEmpty) {
                item(key = "empty_focus_welcome") {
                    EmptyOnboardingFocusCard(
                        defaultMinWage = state.settings.defaultMinWage,
                        onAddRegularJob = onAddRegularJob,
                        onQuickAddShortTerm = onQuickAddShortTerm,
                        onOpenStopwatch = { showStopwatchSheet = true },
                        modifier = Modifier.heightIn(min = 290.dp)
                    )
                }
            } else {
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

            if (!completelyEmpty) {
                item(key = "weekly_holiday_progress") {
                    WeeklyHolidayAndPaydayCard(
                        weekWorkedMinutes = currentWeekWorkMinutes,
                        nearestPayday = paydays.firstOrNull(),
                        unpaidAmount = unpaidPair.first,
                        unpaidCount = unpaidPair.second.size,
                        onOpenUnpaid = onOpenUnpaidRecords,
                        onOpenJob = { jobId -> onEditRegularJob(jobId) }
                    )
                }
            }
        }

        val quickActionHubSection: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
            item(key = "quick_action_hub") {
                SmallTitle(text = "자주 사용하는 기능")
                HomeActionHubGrid(
                    unpaidCount = unpaidPair.second.size,
                    onStopwatch = { showStopwatchSheet = true },
                    onQuickShortTerm = onQuickAddShortTerm,
                    onAddRegularJob = onAddRegularJob,
                    onOpenCalendar = onOpenCalendar,
                    onOpenUnpaid = onOpenUnpaidRecords,
                    onOpenCalculator = onOpenCalculator,
                    onOpenWidgetStudio = onOpenWidgetStudio,
                    onAddWishlist = {
                        editingGoal = null
                        showGoalSheet = true
                    }
                )
            }
        }

        val overviewAndUnpaidSection: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
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
                            if (selectedTangible != null && selectedTangible.unitPrice > 0L && livePlusMonth > 0L) {
                                val count = livePlusMonth.toDouble() / selectedTangible.unitPrice.toDouble()
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .squircleClip(8.dp)
                                        .clickable { showTangibleManager = true }
                                        .padding(vertical = 2.dp)
                                ) {
                                    MiuixEntityBadge(
                                        emoji = selectedTangible.emoji,
                                        fallbackIcon = MiuixIcons.Store,
                                        accentColor = MiuixTheme.colorScheme.primary,
                                        size = 24.dp,
                                        cornerRadius = 7.dp
                                    )
                                    Text(
                                        text = "이번 달 ${selectedTangible.name} 약 ${String.format(Locale.US, "%.1f", count)}개 상당",
                                        style = MiuixTheme.textStyles.footnote1.copy(fontFeatureSettings = "tnum"),
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    )
                                }
                            } else if (selectedTangible == null) {
                                SquirclePillBadge(
                                    text = "체감 항목 추가",
                                    backgroundColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    contentColor = MiuixTheme.colorScheme.primary,
                                    leadingIcon = MiuixIcons.Add,
                                    onClick = { showTangibleManager = true }
                                )
                            }
                        }
                    }
                }
            }

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
                                MiuixGlassIcon(
                                    icon = MiuixIcons.Alarm,
                                    contentDescription = null,
                                    accentColor = MiuixTheme.colorScheme.error,
                                    size = 36.dp,
                                    iconSize = 18.dp,
                                    cornerRadius = 11.dp
                                )
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
        }

        val wishlistAndJobsSection: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
            item(key = "wishlist_section") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmallTitle(text = "목표 금액 (위시리스트) 달성률")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.archivedGoals.isNotEmpty()) {
                            Text(
                                text = "아카이브 (${state.archivedGoals.size})",
                                style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Medium),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier
                                    .clickable { showGoalArchiveSheet = true }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
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
                            val animatedPct by animateFloatAsState(
                                targetValue = pct,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                label = "wishlist_pct_${goal.id}"
                            )
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize(),
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
                                                size = 38.dp,
                                                cornerRadius = 11.dp
                                            )
                                            Text(
                                                text = goal.title,
                                                style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = "${String.format(Locale.US, "%.1f", animatedPct * 100f)}%",
                                            style = MiuixTheme.textStyles.body2.copy(
                                                fontWeight = FontWeight.SemiBold,
                                                fontFeatureSettings = "tnum"
                                            ),
                                            color = MiuixTheme.colorScheme.primary
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = animatedPct,
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
                                    if (pct >= 1f) {
                                        SquirclePillBadge(
                                            text = "달성 완료 · 아카이브 보관",
                                            backgroundColor = Color(0xFF10B981).copy(alpha = 0.16f),
                                            contentColor = Color(0xFF059669),
                                            leadingIcon = MiuixIcons.Ok,
                                            onClick = {
                                                onArchiveWishlistGoal(goal.id, liveCumulative.toLong())
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

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
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        MiuixGlassIcon(
                                            icon = MiuixIcons.Months,
                                            contentDescription = null,
                                            accentColor = Color(info.colorHex)
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    LazyColumn(
                        state = wideLeftListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .overScrollVertical()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(top = topBarInset + 12.dp, bottom = bottomPad),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        focusSection()
                        overviewAndUnpaidSection()
                    }
                    if (liveDashboard.activeShifts.isNotEmpty() && !isFullscreenFocus) {
                        HomeFloatingLiveStage(
                            snapshot = liveDashboard,
                            selectedTab = selectedLiveTab,
                            onSelectTab = { selectedLiveTab = it },
                            selectedTangible = selectedTangible,
                            allTangibles = state.tangibleItems,
                            onSelectTangible = onSelectTangibleItem,
                            onManageTangibles = { showTangibleManager = true },
                            onToggleBreak = onToggleBreak,
                            onClockOut = onClockOut,
                            listState = activeListState,
                            scrollBehavior = scrollBehavior,
                            collapseFraction = rawCollapseFraction,
                            topBarInset = topBarInset,
                            horizontalBasePadding = 0.dp,
                            horizontalDockedPadding = 0.dp,
                            onMeasureExpandedHeightDp = { h -> expandedHeroHeightDp = h },
                            onScrollToTop = {
                                coroutineScope.launch {
                                    activeListState.animateScrollToItem(0)
                                    scrollBehavior.state.contentOffset = 0f
                                }
                            },
                            onOpenFullscreen = { onToggleFullscreenFocus(true) }
                        )
                    }
                }
                LazyColumn(
                    state = wideRightListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(top = topBarInset + 12.dp, bottom = bottomPad),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    quickActionHubSection()
                    wishlistAndJobsSection()
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = phoneListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = topBarInset + 12.dp,
                        bottom = bottomPad
                    ),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    focusSection()
                    quickActionHubSection()
                    overviewAndUnpaidSection()
                    wishlistAndJobsSection()
                }

                if (liveDashboard.activeShifts.isNotEmpty() && !isFullscreenFocus) {
                    HomeFloatingLiveStage(
                        snapshot = liveDashboard,
                        selectedTab = selectedLiveTab,
                        onSelectTab = { selectedLiveTab = it },
                        selectedTangible = selectedTangible,
                        allTangibles = state.tangibleItems,
                        onSelectTangible = onSelectTangibleItem,
                        onManageTangibles = { showTangibleManager = true },
                        onToggleBreak = onToggleBreak,
                        onClockOut = onClockOut,
                        listState = activeListState,
                        scrollBehavior = scrollBehavior,
                        collapseFraction = rawCollapseFraction,
                        topBarInset = topBarInset,
                        horizontalBasePadding = 20.dp,
                        horizontalDockedPadding = 12.dp,
                        onMeasureExpandedHeightDp = { h -> expandedHeroHeightDp = h },
                        onScrollToTop = {
                            coroutineScope.launch {
                                activeListState.animateScrollToItem(0)
                                scrollBehavior.state.contentOffset = 0f
                            }
                        },
                        onOpenFullscreen = { onToggleFullscreenFocus(true) }
                    )
                }
            }
        }

        // Smooth animated Fullscreen focus mode overlay with interactive pull-down-to-collapse gesture
        AnimatedVisibility(
            visible = isFullscreenFocus && liveDashboard.activeShifts.isNotEmpty(),
            enter = fadeIn(tween(260, easing = FastOutSlowInEasing)) +
                scaleIn(
                    initialScale = 0.88f,
                    transformOrigin = TransformOrigin(0.5f, 0.22f),
                    animationSpec = spring(
                        dampingRatio = 0.86f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ),
            exit = fadeOut(tween(200, easing = FastOutSlowInEasing)) +
                scaleOut(
                    targetScale = 0.88f,
                    transformOrigin = TransformOrigin(0.5f, 0.22f),
                    animationSpec = spring(
                        dampingRatio = 0.90f,
                        stiffness = Spring.StiffnessMedium
                    )
                )
        ) {
            var fsDragY by remember { mutableFloatStateOf(0f) }
            var isFsDragging by remember { mutableStateOf(false) }
            var fsHapticFired by remember { mutableStateOf(false) }
            val dismissThresholdPx = with(density) { 108.dp.toPx() }

            LaunchedEffect(isFullscreenFocus) {
                if (!isFullscreenFocus) {
                    fsDragY = 0f
                    isFsDragging = false
                    fsHapticFired = false
                }
            }

            val animatedFsDragY by animateFloatAsState(
                targetValue = if (isFsDragging) fsDragY else 0f,
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "fullscreen_drag_y"
            )
            val activeFsDrag = if (isFsDragging) fsDragY else animatedFsDragY
            val dismissProgress = (activeFsDrag / dismissThresholdPx).coerceIn(0f, 1.35f)
            val scrimAlpha = (1f - dismissProgress * 0.42f).coerceIn(0.35f, 1f)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.surface.copy(alpha = scrimAlpha))
                    .pointerInput(Unit) {
                        val slop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            var totalDx = 0f
                            var totalDy = 0f
                            var draggingDown = false
                            fsHapticFired = false

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: event.changes.firstOrNull()
                                    ?: break
                                if (!change.pressed) {
                                    break
                                }
                                val delta = change.positionChange()
                                totalDx += delta.x
                                totalDy += delta.y
                                tracker.addPosition(change.uptimeMillis, change.position)

                                if (!draggingDown) {
                                    if (totalDy > slop && totalDy > abs(totalDx)) {
                                        draggingDown = true
                                        isFsDragging = true
                                    } else if (abs(totalDx) > slop && abs(totalDx) > abs(totalDy)) {
                                        break
                                    } else if (totalDy < -slop) {
                                        break
                                    }
                                }

                                if (draggingDown) {
                                    change.consume()
                                    val nextY = totalDy.coerceAtLeast(0f)
                                    fsDragY = nextY
                                    if (!fsHapticFired && nextY >= dismissThresholdPx) {
                                        fsHapticFired = true
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }
                            }

                            if (draggingDown) {
                                val vy = tracker.calculateVelocity().y
                                val shouldCollapse = fsDragY >= dismissThresholdPx || (fsDragY > 36f && vy > 650f)
                                isFsDragging = false
                                if (shouldCollapse) {
                                    fsDragY = 0f
                                    onToggleFullscreenFocus(false)
                                } else {
                                    fsDragY = 0f
                                }
                            }
                        }
                    }
                    .systemBarsPadding()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = activeFsDrag * 0.88f
                            val scale = 1f - 0.10f * dismissProgress.coerceIn(0f, 1f)
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0.5f, 0.25f)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Intuitive pull-down cue at top of fullscreen focus mode
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(38.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (dismissProgress >= 1f) MiuixTheme.colorScheme.primary
                                    else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.22f)
                                )
                        )
                        Text(
                            text = if (dismissProgress >= 1f) "놓으면 축소됩니다" else "아래로 당겨 축소",
                            style = MiuixTheme.textStyles.footnote2.copy(fontSize = 11.sp),
                            color = if (dismissProgress >= 1f) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.78f)
                            }
                        )
                    }

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
                        collapseFraction = 0f,
                        onToggleFullscreen = { onToggleFullscreenFocus(false) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
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

        GoalArchiveBottomSheet(
            show = showGoalArchiveSheet,
            archivedGoals = state.archivedGoals,
            onDismiss = { showGoalArchiveSheet = false },
            onDeleteArchive = onDeleteArchivedGoal
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
private fun WeeklyHolidayAndPaydayCard(
    weekWorkedMinutes: Int,
    nearestPayday: PaydayCountdownInfo?,
    unpaidAmount: Long,
    unpaidCount: Int,
    onOpenUnpaid: () -> Unit,
    onOpenJob: (String) -> Unit
) {
    val weekHours = weekWorkedMinutes / 60f
    val progress = (weekWorkedMinutes / 900f).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "weekly_holiday_progress"
    )
    val achieved = weekWorkedMinutes >= 900
    val remainingMins = (900 - weekWorkedMinutes).coerceAtLeast(0)
    val holidayColor by animateColorAsState(
        targetValue = if (achieved) Color(0xFF16A34A) else MiuixTheme.colorScheme.primary,
        animationSpec = tween(250),
        label = "weekly_holiday_color"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    MiuixGlassIcon(
                        icon = MiuixIcons.Weeks,
                        contentDescription = null,
                        accentColor = holidayColor,
                        size = 38.dp,
                        iconSize = 18.dp,
                        cornerRadius = 11.dp
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "이번 주 근무 ${PayrollCalculator.formatDurationKorean(weekWorkedMinutes)}",
                            style = MiuixTheme.textStyles.body1.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFeatureSettings = "tnum"
                            )
                        )
                        Text(
                            text = if (achieved) "주 15시간 달성 · 주휴수당 발생 조건 충족"
                            else "주휴 15시간까지 ${PayrollCalculator.formatDurationKorean(remainingMins)} 남음",
                            style = MiuixTheme.textStyles.footnote2,
                            color = if (achieved) Color(0xFF16A34A) else MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
                SquirclePillBadge(
                    text = if (achieved) "15h 달성" else "${String.format(Locale.US, "%.1f", weekHours)}/15h",
                    backgroundColor = holidayColor.copy(alpha = 0.14f),
                    contentColor = holidayColor
                )
            }

            LinearProgressIndicator(
                progress = animatedProgress,
                modifier = Modifier.fillMaxWidth()
            )

            if (nearestPayday != null || unpaidAmount > 0L) {
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (nearestPayday != null) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .squircleBackground(MiuixTheme.colorScheme.surfaceContainer, 12.dp)
                                .squircleClip(12.dp)
                                .clickable { onOpenJob(nearestPayday.jobId) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MiuixGlassIcon(
                                icon = MiuixIcons.Months,
                                contentDescription = null,
                                accentColor = Color(nearestPayday.colorHex),
                                size = 30.dp,
                                iconSize = 15.dp,
                                cornerRadius = 9.dp
                            )
                            Column {
                                Text(
                                    text = "${nearestPayday.jobName} 월급",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (nearestPayday.dDay == 0L) "오늘 입금일" else "D-${nearestPayday.dDay}",
                                    style = MiuixTheme.textStyles.body2.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontFeatureSettings = "tnum"
                                    ),
                                    color = Color(nearestPayday.colorHex)
                                )
                            }
                        }
                    }
                    if (unpaidAmount > 0L) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .squircleBackground(MiuixTheme.colorScheme.errorContainer.copy(alpha = 0.45f), 12.dp)
                                .squircleClip(12.dp)
                                .clickable(onClick = onOpenUnpaid)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MiuixGlassIcon(
                                icon = MiuixIcons.Alarm,
                                contentDescription = null,
                                accentColor = MiuixTheme.colorScheme.error,
                                size = 30.dp,
                                iconSize = 15.dp,
                                cornerRadius = 9.dp
                            )
                            Column {
                                Text(
                                    text = "미수금 ${unpaidCount}건",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onErrorContainer,
                                    maxLines = 1
                                )
                                Text(
                                    text = PayrollCalculator.formatWon(unpaidAmount),
                                    style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Bold),
                                    color = MiuixTheme.colorScheme.error,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class HomeHubTileSpec(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color,
    val onClick: () -> Unit
)

@Composable
private fun HomeActionHubGrid(
    unpaidCount: Int,
    onStopwatch: () -> Unit,
    onQuickShortTerm: () -> Unit,
    onAddRegularJob: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenUnpaid: () -> Unit,
    onOpenCalculator: () -> Unit,
    onOpenWidgetStudio: () -> Unit,
    onAddWishlist: () -> Unit
) {
    val tiles = listOf(
        HomeHubTileSpec("즉시 출근", "실시간 타이머", MiuixIcons.Stopwatch, MiuixTheme.colorScheme.primary, onStopwatch),
        HomeHubTileSpec("단기·대타 등록", "10초 퀵 입력", MiuixIcons.Timer, Color(0xFF0EA5E9), onQuickShortTerm),
        HomeHubTileSpec("정규 알바 추가", "시급·주차 설정", MiuixIcons.Store, Color(0xFF10B981), onAddRegularJob),
        HomeHubTileSpec("근무 달력", "연장·조퇴·대타", MiuixIcons.Months, Color(0xFF6366F1), onOpenCalendar),
        HomeHubTileSpec(
            "미수금·정산",
            if (unpaidCount > 0) "대기 ${unpaidCount}건" else "급여 명세서",
            MiuixIcons.BankCards,
            if (unpaidCount > 0) MiuixTheme.colorScheme.error else Color(0xFFF59E0B),
            onOpenUnpaid
        ),
        HomeHubTileSpec("급여 계산기", "주휴·일용직·퇴직금", MiuixIcons.Tune, Color(0xFFEC4899), onOpenCalculator),
        HomeHubTileSpec("위젯 꾸미기", "OneUI 4종 설정", MiuixIcons.Layers, Color(0xFF14B8A6), onOpenWidgetStudio),
        HomeHubTileSpec("목표 추가", "위시리스트 게이지", MiuixIcons.FavoritesFill, Color(0xFF8B5CF6), onAddWishlist)
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { rowTiles ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowTiles.forEach { spec ->
                    Card(
                        modifier = Modifier.weight(1f),
                        cornerRadius = 16.dp,
                        insideMargin = PaddingValues(14.dp),
                        onClick = spec.onClick
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MiuixGlassIcon(
                                icon = spec.icon,
                                contentDescription = spec.title,
                                accentColor = spec.color,
                                size = 42.dp,
                                iconSize = 20.dp,
                                cornerRadius = 12.dp
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = spec.title,
                                    style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = spec.subtitle,
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
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
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
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
                            text = "아래 근무지를 누르면 바로 실시간 출근이 시작됩니다",
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
                val quickEntries: List<@Composable (Modifier) -> Unit> = buildList {
                    jobs.forEach { job ->
                        val wage = PayrollCalculator.effectiveJobWage(job, LocalDate.now())
                        add { mod ->
                            Row(
                                modifier = mod
                                    .squircleBackground(Color(job.colorHex).copy(alpha = 0.11f), 14.dp)
                                    .squircleBorder(1.dp, Color(job.colorHex).copy(alpha = 0.24f), 14.dp)
                                    .squircleClip(14.dp)
                                    .clickable { onQuickStartJob(job) }
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MiuixEntityBadge(
                                    emoji = job.emoji,
                                    fallbackIcon = MiuixIcons.Store,
                                    accentColor = Color(job.colorHex),
                                    size = 34.dp,
                                    cornerRadius = 10.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = job.name,
                                        style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.SemiBold),
                                        color = MiuixTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${job.payType.label} ${PayrollCalculator.formatWon(wage)}",
                                        style = MiuixTheme.textStyles.footnote2.copy(fontFeatureSettings = "tnum"),
                                        color = Color(job.colorHex),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    templates.forEach { tpl ->
                        add { mod ->
                            Row(
                                modifier = mod
                                    .squircleBackground(Color(tpl.colorHex).copy(alpha = 0.11f), 14.dp)
                                    .squircleBorder(1.dp, Color(tpl.colorHex).copy(alpha = 0.24f), 14.dp)
                                    .squircleClip(14.dp)
                                    .clickable { onQuickStartTemplate(tpl) }
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MiuixEntityBadge(
                                    emoji = tpl.emoji,
                                    fallbackIcon = MiuixIcons.Timer,
                                    accentColor = Color(tpl.colorHex),
                                    size = 34.dp,
                                    cornerRadius = 10.dp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tpl.name,
                                        style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.SemiBold),
                                        color = MiuixTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "프리셋 · ${PayrollCalculator.formatWon(tpl.unitAmount)}",
                                        style = MiuixTheme.textStyles.footnote2.copy(fontFeatureSettings = "tnum"),
                                        color = Color(tpl.colorHex),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    quickEntries.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pair.forEach { entryContent ->
                                entryContent(Modifier.weight(1f))
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            TextButton(
                text = "새 근무 직접 스톱워치 시작",
                onClick = onStartStopwatch,
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun HomeFloatingLiveStage(
    snapshot: LiveDashboardSnapshot,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    selectedTangible: TangibleItem?,
    allTangibles: List<TangibleItem>,
    onSelectTangible: (String) -> Unit,
    onManageTangibles: () -> Unit,
    onToggleBreak: (String) -> Unit,
    onClockOut: (String) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    scrollBehavior: ScrollBehavior,
    collapseFraction: Float,
    topBarInset: Dp,
    horizontalBasePadding: Dp,
    horizontalDockedPadding: Dp,
    onMeasureExpandedHeightDp: (Dp) -> Unit,
    onScrollToTop: () -> Unit,
    onOpenFullscreen: () -> Unit
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val thresholdPx = with(density) { 64.dp.toPx() }
    var pullDownPx by remember { mutableFloatStateOf(0f) }
    var isPulling by remember { mutableStateOf(false) }
    var pullHapticFired by remember { mutableStateOf(false) }

    val smoothCollapse = collapseFraction.coerceIn(0f, 1f).let { t -> t * t * (3f - 2f * t) }
    val isDocked = smoothCollapse >= 0.45f

    val animatedPullPx by animateFloatAsState(
        targetValue = if (isPulling) pullDownPx else 0f,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "home_pull_expand_px"
    )
    val activePullPx = if (isPulling) pullDownPx else animatedPullPx
    val pullRatio = (activePullPx / thresholdPx).coerceIn(0f, 1.35f)

    val pullNestedConn = remember(listState, thresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pullDownPx > 0f && available.y < 0f && source == NestedScrollSource.UserInput) {
                    val consumedY = maxOf(available.y, -pullDownPx)
                    pullDownPx = (pullDownPx + consumedY).coerceAtLeast(0f)
                    return Offset(0f, consumedY)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source == NestedScrollSource.UserInput &&
                    available.y > 0f &&
                    !listState.canScrollBackward
                ) {
                    isPulling = true
                    val next = (pullDownPx + available.y * 0.60f).coerceIn(0f, thresholdPx * 1.45f)
                    pullDownPx = next
                    if (!pullHapticFired && next >= thresholdPx) {
                        pullHapticFired = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (pullDownPx >= thresholdPx || (pullDownPx > 20f && available.y > 550f)) {
                    isPulling = false
                    pullDownPx = 0f
                    pullHapticFired = false
                    onOpenFullscreen()
                } else if (pullDownPx > 0f) {
                    isPulling = false
                    pullDownPx = 0f
                    pullHapticFired = false
                }
                return Velocity.Zero
            }
        }
    }

    val topPadding = topBarInset + androidx.compose.ui.unit.lerp(12.dp, 4.dp, smoothCollapse)
    val sidePadding = androidx.compose.ui.unit.lerp(horizontalBasePadding, horizontalDockedPadding, smoothCollapse)

    val gestureMod = if (!isDocked) {
        Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .nestedScroll(pullNestedConn)
            .scrollable(
                state = listState,
                orientation = Orientation.Vertical,
                reverseDirection = true
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    while (true) {
                        val ev = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Final)
                        if (ev.changes.all { !it.pressed }) {
                            if (isPulling || pullDownPx > 0f) {
                                if (pullDownPx >= thresholdPx) {
                                    isPulling = false
                                    pullDownPx = 0f
                                    pullHapticFired = false
                                    onOpenFullscreen()
                                } else {
                                    isPulling = false
                                    pullDownPx = 0f
                                    pullHapticFired = false
                                }
                            }
                            break
                        }
                    }
                }
            }
    } else {
        Modifier.pointerInput(thresholdPx) {
            val slop = viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)
                var totalDx = 0f
                var totalDy = 0f
                var pullingDown = false
                pullHapticFired = false

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                        ?: event.changes.firstOrNull()
                        ?: break
                    if (!change.pressed) break

                    val delta = change.positionChange()
                    totalDx += delta.x
                    totalDy += delta.y
                    tracker.addPosition(change.uptimeMillis, change.position)

                    if (!pullingDown) {
                        if (totalDy > slop && totalDy > abs(totalDx)) {
                            pullingDown = true
                            isPulling = true
                        } else if (abs(totalDx) > slop && abs(totalDx) > abs(totalDy)) {
                            break
                        } else if (totalDy < -slop) {
                            break
                        }
                    }

                    if (pullingDown) {
                        change.consume()
                        val next = totalDy.coerceAtLeast(0f) * 0.62f
                        pullDownPx = next.coerceIn(0f, thresholdPx * 1.45f)
                        if (!pullHapticFired && next >= thresholdPx) {
                            pullHapticFired = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                }

                if (pullingDown) {
                    val vy = tracker.calculateVelocity().y
                    val shouldOpen = pullDownPx >= thresholdPx || (pullDownPx > 24f && vy > 520f)
                    isPulling = false
                    pullDownPx = 0f
                    pullHapticFired = false
                    if (shouldOpen) {
                        onOpenFullscreen()
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding, start = sidePadding, end = sidePadding)
            .onSizeChanged { sz ->
                if (collapseFraction < 0.02f && sz.height > 0) {
                    onMeasureExpandedHeightDp(with(density) { sz.height.toDp() })
                }
            }
            .graphicsLayer {
                translationY = activePullPx * 0.34f
                val s = 1f + 0.036f * pullRatio.coerceIn(0f, 1f)
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0.5f, 0.15f)
            }
            .then(gestureMod)
            .then(
                if (isDocked) {
                    Modifier.clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = onScrollToTop
                    )
                } else Modifier
            )
    ) {
        FocusTimerStage(
            snapshot = snapshot,
            selectedTab = selectedTab,
            onSelectTab = onSelectTab,
            selectedTangible = selectedTangible,
            allTangibles = allTangibles,
            onSelectTangible = onSelectTangible,
            onManageTangibles = onManageTangibles,
            onToggleBreak = onToggleBreak,
            onClockOut = onClockOut,
            isFullscreen = false,
            collapseFraction = smoothCollapse,
            pullExpandRatio = pullRatio,
            onToggleFullscreen = onOpenFullscreen,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun FocusTimerStage(
    snapshot: LiveDashboardSnapshot,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    selectedTangible: TangibleItem?,
    allTangibles: List<TangibleItem>,
    onSelectTangible: (String) -> Unit,
    onManageTangibles: () -> Unit,
    onToggleBreak: (String) -> Unit,
    onClockOut: (String) -> Unit,
    isFullscreen: Boolean,
    collapseFraction: Float = 0f,
    pullExpandRatio: Float = 0f,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalIsDarkTheme.current
    val collapse = if (isFullscreen) 0f else collapseFraction.coerceIn(0f, 1f)
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

    val rawAccentColor = if (isCombinedView) MiuixTheme.colorScheme.primary else Color(focusedShift.colorHex)
    val accentColor by animateColorAsState(
        targetValue = rawAccentColor,
        animationSpec = tween(240),
        label = "focus_accent_color"
    )
    val isPaused = !isCombinedView && focusedShift.isPaused
    val statusDotColor by animateColorAsState(
        targetValue = if (isPaused) Color(0xFFD97706) else Color(0xFF16A34A),
        animationSpec = tween(220),
        label = "focus_status_dot"
    )
    val stageCornerRadius = if (isFullscreen) 24.dp else 22.dp
    val ringSize = if (isFullscreen) {
        292.dp
    } else {
        androidx.compose.ui.unit.lerp(222.dp, 74.dp, collapse)
    }
    val cardPadH = if (isFullscreen) 20.dp else androidx.compose.ui.unit.lerp(20.dp, 14.dp, collapse)
    val cardPadV = if (isFullscreen) 20.dp else androidx.compose.ui.unit.lerp(18.dp, 11.dp, collapse)
    val cardElevation = if (isFullscreen) 0.dp else (12f * ((collapse - 0.06f) / 0.7f).coerceIn(0f, 1f)).dp
    val cardBgTint = when {
        isFullscreen -> MiuixTheme.colorScheme.surfaceContainer
        collapse > 0.04f -> androidx.compose.ui.graphics.lerp(
            MiuixTheme.colorScheme.surfaceContainer,
            if (isDark) MiuixTheme.colorScheme.surfaceContainerHigh else Color.White,
            collapse.coerceIn(0f, 1f)
        )
        else -> MiuixTheme.colorScheme.surfaceContainer
    }
    val pullGlowAlpha = (pullExpandRatio * 0.45f).coerceIn(0f, 0.55f)
    val secondFraction = ((focusedShift.elapsedWorkMillis % 60_000L) / 60_000f).coerceIn(0f, 1f)
    var pendingBreakTarget by remember { mutableStateOf<LiveShiftSnapshot?>(null) }
    var pendingClockOutTarget by remember { mutableStateOf<LiveShiftSnapshot?>(null) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (cardElevation > 0.2.dp) {
                    Modifier.shadow(
                        elevation = cardElevation,
                        shape = RoundedCornerShape(stageCornerRadius),
                        ambientColor = Color.Black.copy(alpha = if (isDark) 0.42f else 0.14f),
                        spotColor = Color.Black.copy(alpha = if (isDark) 0.42f else 0.16f)
                    )
                } else Modifier
            )
            .miuixGlassSurface(cornerRadius = stageCornerRadius, tintColor = cardBgTint)
            .then(
                if (pullGlowAlpha > 0.02f) {
                    Modifier.squircleBorder(
                        width = 1.5.dp,
                        color = accentColor.copy(alpha = pullGlowAlpha),
                        cornerRadius = stageCornerRadius
                    )
                } else Modifier
            )
            .padding(horizontal = cardPadH, vertical = cardPadV)
    ) {
        val headerSubAlpha = if (isFullscreen) 1f else (1f - collapse * 2.2f).coerceIn(0f, 1f)
        val badgeSize = if (isFullscreen) 34.dp else androidx.compose.ui.unit.lerp(34.dp, 26.dp, collapse)
        val badgeCorner = if (isFullscreen) 10.dp else androidx.compose.ui.unit.lerp(10.dp, 8.dp, collapse)

        Column(
            modifier = if (isFullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
            verticalArrangement = if (isFullscreen) Arrangement.SpaceBetween else Arrangement.spacedBy(
                androidx.compose.ui.unit.lerp(10.dp, 8.dp, collapse)
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar: concurrent shift switcher, status, boost badge, and fullscreen toggle
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hasMultiple && (isFullscreen || collapse < 0.72f)) {
                    val tabAlpha = if (isFullscreen) 1f else (1f - collapse / 0.72f).coerceIn(0f, 1f)
                    val tabLabels = buildList {
                        add("통합 (${snapshot.activeShifts.size})")
                        snapshot.activeShifts.forEach { add(it.title) }
                    }
                    Box(modifier = Modifier.graphicsLayer { alpha = tabAlpha }) {
                        TabRowWithContour(
                            tabs = tabLabels,
                            selectedTabIndex = clampedTab,
                            onTabSelected = onSelectTab
                        )
                    }
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
                        if (!isCombinedView) {
                            MiuixEntityBadge(
                                emoji = focusedShift.emoji,
                                fallbackIcon = MiuixIcons.Stopwatch,
                                accentColor = accentColor,
                                size = badgeSize,
                                cornerRadius = badgeCorner
                            )
                        }
                        Column {
                            Text(
                                text = when {
                                    isCombinedView -> "${snapshot.activeShifts.size}개 알바 동시 진행 중"
                                    focusedShift.isPaused -> focusedShift.pauseReason ?: "휴게 시간"
                                    else -> focusedShift.title
                                },
                                style = MiuixTheme.textStyles.body1.copy(
                                    fontSize = if (isFullscreen) 16.sp else lerp(16f, 14f, collapse).sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (headerSubAlpha > 0.05f) {
                                Text(
                                    text = if (isCombinedView) "합산 실시간 포커스" else if (isPaused) "적립 일시정지" else "실시간 적립 중",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = if (isPaused) Color(0xFFD97706) else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.graphicsLayer { alpha = headerSubAlpha }
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedVisibility(
                            visible = showBoost && collapse < 0.7f,
                            enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.85f),
                            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.85f)
                        ) {
                            val multStr = if (boostMult == 1.5) "1.5" else String.format(Locale.US, "%.1f", boostMult)
                            SquirclePillBadge(
                                text = "${multStr}배 수당 부스트",
                                backgroundColor = Color(0xFFEA580C).copy(alpha = 0.14f),
                                contentColor = Color(0xFFEA580C)
                            )
                        }
                        AnimatedContent(
                            targetState = Pair(isFullscreen, pullExpandRatio >= 1f),
                            transitionSpec = {
                                (fadeIn(tween(180)) + scaleIn(initialScale = 0.92f))
                                    .togetherWith(fadeOut(tween(150)) + scaleOut(targetScale = 0.92f))
                            },
                            label = "fullscreen_toggle_label"
                        ) { (full, pullReady) ->
                            SquirclePillBadge(
                                text = when {
                                    full -> "축소"
                                    pullReady -> "놓으면 전체"
                                    else -> "전체 화면"
                                },
                                backgroundColor = if (pullReady) {
                                    accentColor.copy(alpha = 0.18f)
                                } else {
                                    MiuixTheme.colorScheme.surfaceContainer
                                },
                                contentColor = if (pullReady) {
                                    accentColor
                                } else {
                                    MiuixTheme.colorScheme.onSurfaceVariantActions
                                },
                                onClick = onToggleFullscreen
                            )
                        }
                    }
                }
            }

            if (isFullscreen) {
                // Fullscreen chronometer ring and 0.1s odometer
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(vertical = 8.dp)
                        .size(ringSize),
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
                                fontSize = 34.sp,
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

                FocusStageExpandedControls(
                    snapshot = snapshot,
                    focusedShift = focusedShift,
                    displayEarned = displayEarned,
                    accentColor = accentColor,
                    selectedTangible = selectedTangible,
                    allTangibles = allTangibles,
                    onSelectTangible = onSelectTangible,
                    onManageTangibles = onManageTangibles,
                    onRequestBreak = { pendingBreakTarget = it },
                    onRequestClockOut = { pendingClockOutTarget = it }
                )
            } else {
                // Continuous morphing stage: same ring & odometer seamlessly glide from centered hero -> docked top card
                val expandedControlsAlpha = (1f - (collapse / 0.45f)).coerceIn(0f, 1f)
                val dockedActionsAlpha = ((collapse - 0.50f) / 0.45f).coerceIn(0f, 1f)
                val odometerFontSp = lerp(29f, 20.5f, collapse)
                val labelAlpha = (1f - (collapse / 0.42f)).coerceIn(0f, 1f)

                Layout(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds(),
                    content = {
                        // 0: Chronometer ring canvas
                        FocusTimerRingCanvas(
                            progress = if (isCombinedView) null else focusedShift.progressFraction,
                            accentColor = accentColor,
                            trackColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            tickColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            isPaused = isPaused,
                            secondFraction = secondFraction,
                            modifier = Modifier.size(ringSize)
                        )

                        // 1: Odometer, rate & elapsed/progress readout
                        Column(
                            horizontalAlignment = if (collapse < 0.5f) Alignment.CenterHorizontally else Alignment.Start,
                            verticalArrangement = Arrangement.spacedBy(androidx.compose.ui.unit.lerp(5.dp, 2.dp, collapse))
                        ) {
                            if (labelAlpha > 0.04f) {
                                Text(
                                    text = if (isCombinedView) "통합 누적 수익" else "실시간 수익",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.graphicsLayer { alpha = labelAlpha }
                                )
                            }
                            OdometerCurrencyText(
                                amountWon = displayEarned,
                                textStyle = MiuixTheme.textStyles.title1.copy(
                                    fontSize = odometerFontSp.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (collapse < 0.65f) {
                                    String.format(Locale.US, "+%.2f원/초 · +%.0f원/분", displayRatePerSec, displayRatePerMin)
                                } else {
                                    String.format(Locale.US, "+%.2f원/초 · %s", displayRatePerSec, PayrollCalculator.formatCountdown(focusedShift.elapsedWorkMillis))
                                },
                                style = MiuixTheme.textStyles.footnote1.copy(
                                    fontSize = lerp(12.5f, 11.5f, collapse).sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = accentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (collapse < 0.65f) {
                                val progress = focusedShift.progressFraction
                                val remMs = focusedShift.remainingMillis
                                Box(modifier = Modifier.graphicsLayer { alpha = (1f - collapse / 0.65f).coerceIn(0f, 1f) }) {
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
                        }

                        // 2: Expanded bottom controls (Tangible + full buttons)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer { alpha = expandedControlsAlpha }
                        ) {
                            FocusStageExpandedControls(
                                snapshot = snapshot,
                                focusedShift = focusedShift,
                                displayEarned = displayEarned,
                                accentColor = accentColor,
                                selectedTangible = selectedTangible,
                                allTangibles = allTangibles,
                                onSelectTangible = onSelectTangible,
                                onManageTangibles = onManageTangibles,
                                onRequestBreak = { pendingBreakTarget = it },
                                onRequestClockOut = { pendingClockOutTarget = it }
                            )
                        }

                        // 3: Compact docked right action buttons
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.graphicsLayer {
                                alpha = dockedActionsAlpha
                                val shiftX = (1f - dockedActionsAlpha) * 16f
                                translationX = shiftX
                            }
                        ) {
                            SquirclePillBadge(
                                text = if (focusedShift.isManualPaused) "재개" else "휴게",
                                backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                                contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
                                onClick = { if (dockedActionsAlpha > 0.25f) pendingBreakTarget = focusedShift }
                            )
                            SquirclePillBadge(
                                text = "퇴근",
                                backgroundColor = MiuixTheme.colorScheme.primary,
                                contentColor = Color.White,
                                onClick = { if (dockedActionsAlpha > 0.25f) pendingClockOutTarget = focusedShift }
                            )
                        }
                    }
                ) { measurables, constraints ->
                    val maxW = constraints.maxWidth
                    val ringPlaceable = measurables[0].measure(Constraints())
                    val ringW = ringPlaceable.width
                    val ringH = ringPlaceable.height

                    val dockedActionPlaceable = measurables[3].measure(Constraints(maxWidth = maxW / 3))
                    val expandedControlsPlaceable = measurables[2].measure(
                        Constraints(minWidth = maxW, maxWidth = maxW)
                    )

                    val metricsMaxWAtHero = (ringW - 24.dp.roundToPx()).coerceAtLeast(120.dp.roundToPx())
                    val metricsMaxWAtDock = (maxW - ringW - dockedActionPlaceable.width - 20.dp.roundToPx())
                        .coerceAtLeast(100.dp.roundToPx())
                    val metricsMaxW = lerp(metricsMaxWAtHero.toFloat(), metricsMaxWAtDock.toFloat(), collapse)
                        .roundToInt()
                        .coerceIn(80, maxW)
                    val metricsPlaceable = measurables[1].measure(Constraints(maxWidth = metricsMaxW))

                    val expandedGapPx = 12.dp.roundToPx()
                    val expandedTotalH = ringH + expandedGapPx + expandedControlsPlaceable.height
                    val dockedTotalH = maxOf(ringH, metricsPlaceable.height, dockedActionPlaceable.height)
                    val currentH = lerp(expandedTotalH.toFloat(), dockedTotalH.toFloat(), collapse)
                        .roundToInt()
                        .coerceAtLeast(1)

                    layout(maxW, currentH) {
                        val ringX = lerp(((maxW - ringW) / 2f), 0f, collapse).roundToInt()
                        val ringY = lerp(0f, ((dockedTotalH - ringH) / 2f), collapse).roundToInt()
                        ringPlaceable.placeRelative(ringX, ringY)

                        val metricsXHero = (maxW - metricsPlaceable.width) / 2f
                        val metricsXDock = (ringW + 10.dp.roundToPx()).toFloat()
                        val metricsYHero = (ringH - metricsPlaceable.height) / 2f
                        val metricsYDock = (dockedTotalH - metricsPlaceable.height) / 2f
                        metricsPlaceable.placeRelative(
                            lerp(metricsXHero, metricsXDock, collapse).roundToInt(),
                            lerp(metricsYHero, metricsYDock, collapse).roundToInt()
                        )

                        if (expandedControlsAlpha > 0.01f) {
                            expandedControlsPlaceable.placeRelative(0, ringH + expandedGapPx)
                        }
                        if (dockedActionsAlpha > 0.01f) {
                            dockedActionPlaceable.placeRelative(
                                maxW - dockedActionPlaceable.width,
                                ((dockedTotalH - dockedActionPlaceable.height) / 2f).roundToInt()
                            )
                        }
                    }
                }

                // Bottom pull-down-to-fullscreen affordance grabber
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(androidx.compose.ui.unit.lerp(28.dp, 34.dp, pullExpandRatio.coerceIn(0f, 1f)))
                            .height(3.5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (pullExpandRatio >= 1f) accentColor
                                else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.16f)
                            )
                    )
                }
            }
        }

        val breakTarget = pendingBreakTarget
        if (breakTarget != null) {
            ConfirmActionDialog(
                show = true,
                title = if (breakTarget.isManualPaused) "휴게 종료" else "휴게 시작",
                summary = if (breakTarget.isManualPaused) {
                    "'${breakTarget.title}' 휴게를 종료하고 근무를 재개할까요?"
                } else {
                    "'${breakTarget.title}' 휴게를 시작할까요? 휴게 중에는 근무 시간이 일시 정지됩니다."
                },
                confirmText = if (breakTarget.isManualPaused) "휴게 종료" else "휴게 시작",
                onDismiss = { pendingBreakTarget = null },
                onConfirm = {
                    val key = breakTarget.runtimeKey
                    pendingBreakTarget = null
                    onToggleBreak(key)
                }
            )
        }

        val clockOutTarget = pendingClockOutTarget
        if (clockOutTarget != null) {
            ConfirmActionDialog(
                show = true,
                title = "퇴근 완료",
                summary = "'${clockOutTarget.title}' 근무를 종료하고 현재까지의 급여(${PayrollCalculator.formatWon(clockOutTarget.liveEarnedWon.toLong())})를 기록으로 저장할까요?",
                confirmText = "퇴근 완료",
                onDismiss = { pendingClockOutTarget = null },
                onConfirm = {
                    val key = clockOutTarget.runtimeKey
                    pendingClockOutTarget = null
                    onClockOut(key)
                }
            )
        }
    }
}

@Composable
private fun FocusStageExpandedControls(
    snapshot: LiveDashboardSnapshot,
    focusedShift: LiveShiftSnapshot,
    displayEarned: Double,
    accentColor: Color,
    selectedTangible: TangibleItem?,
    allTangibles: List<TangibleItem>,
    onSelectTangible: (String) -> Unit,
    onManageTangibles: () -> Unit,
    onRequestBreak: (LiveShiftSnapshot) -> Unit,
    onRequestClockOut: (LiveShiftSnapshot) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (selectedTangible != null) {
            val qty = if (selectedTangible.unitPrice > 0L) displayEarned / selectedTangible.unitPrice else 0.0
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MiuixEntityBadge(
                            emoji = selectedTangible.emoji,
                            fallbackIcon = MiuixIcons.Store,
                            accentColor = accentColor,
                            size = 24.dp,
                            cornerRadius = 7.dp
                        )
                        Text(
                            text = "${selectedTangible.name} ${String.format(Locale.US, "%.2f", qty)}개",
                            style = MiuixTheme.textStyles.body2.copy(
                                fontWeight = FontWeight.Medium,
                                fontFeatureSettings = "tnum"
                            ),
                            color = MiuixTheme.colorScheme.onSurface
                        )
                    }
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
                            text = item.name,
                            backgroundColor = if (active) accentColor else MiuixTheme.colorScheme.secondaryContainer,
                            contentColor = if (active) Color.White else MiuixTheme.colorScheme.onSecondaryContainer,
                            leadingIcon = AlbaIconCatalog.resolveIcon(item.emoji, MiuixIcons.Store),
                            onClick = { onSelectTangible(item.id) }
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SquirclePillBadge(
                    text = "체감 항목 추가",
                    backgroundColor = accentColor.copy(alpha = 0.14f),
                    contentColor = accentColor,
                    leadingIcon = MiuixIcons.Add,
                    onClick = onManageTangibles
                )
                Text(
                    text = "시급 체감 환산",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextButton(
                text = if (focusedShift.isManualPaused) "휴게 종료" else "휴게 시작",
                onClick = { onRequestBreak(focusedShift) },
                modifier = Modifier.weight(1f)
            )
            TextButton(
                text = "퇴근 완료",
                onClick = { onRequestClockOut(focusedShift) },
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.weight(1f)
            )
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
                        SquirclePillBadge(
                            text = job.name,
                            backgroundColor = if (picked) Color(job.colorHex) else Color(job.colorHex).copy(alpha = 0.14f),
                            contentColor = if (picked) Color.White else Color(job.colorHex),
                            leadingIcon = AlbaIconCatalog.resolveIcon(job.emoji, MiuixIcons.Store),
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
    var emojiText by remember(show) { mutableStateOf("store") }
    var priceText by remember(show) { mutableStateOf("") }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var pendingDeleteItem by remember { mutableStateOf<TangibleItem?>(null) }
    var pendingEditItem by remember { mutableStateOf<TangibleItem?>(null) }

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
            if (items.isEmpty()) {
                Text(
                    text = "등록된 체감 항목이 없습니다. 아래에서 자주 사는 항목을 추가해 보세요.",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
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
                        MiuixEntityBadge(
                            emoji = item.emoji,
                            fallbackIcon = MiuixIcons.Store,
                            accentColor = MiuixTheme.colorScheme.primary,
                            size = 34.dp,
                            cornerRadius = 10.dp
                        )
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
                        IconButton(onClick = { pendingDeleteItem = item }) {
                            Icon(MiuixIcons.Delete, contentDescription = "삭제")
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
                        .squircleClip(12.dp)
                        .clickable { showEmojiPicker = true }
                ) {
                    MiuixEntityBadge(
                        emoji = emojiText,
                        fallbackIcon = MiuixIcons.Store,
                        accentColor = MiuixTheme.colorScheme.primary,
                        size = 52.dp,
                        cornerRadius = 12.dp
                    )
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
                            emoji = emojiText.ifBlank { "promotions" },
                            unitPrice = price,
                            isDefault = editingItem?.isDefault ?: false
                        )
                        if (editingItem != null) {
                            pendingEditItem = next
                        } else {
                            onUpsert(next)
                            editingItem = null
                            nameText = ""
                            priceText = ""
                        }
                    }
                },
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    MiuixIconPickerSheet(
        show = showEmojiPicker,
        title = "아이템 아이콘 선택",
        currentIconKey = emojiText,
        allowClear = false,
        onDismiss = { showEmojiPicker = false },
        onSelect = { emojiText = it.ifBlank { "promotions" } }
    )

    val itemToDelete = pendingDeleteItem
    if (itemToDelete != null) {
        ConfirmActionDialog(
            show = true,
            title = "체감 아이템 삭제",
            summary = "'${itemToDelete.name}' 항목을 삭제할까요?",
            confirmText = "삭제",
            isDestructive = true,
            onDismiss = { pendingDeleteItem = null },
            onConfirm = {
                onDelete(itemToDelete.id)
                if (editingItem?.id == itemToDelete.id) {
                    editingItem = null
                    nameText = ""
                    priceText = ""
                }
                pendingDeleteItem = null
            }
        )
    }

    val itemToEdit = pendingEditItem
    if (itemToEdit != null) {
        ConfirmActionDialog(
            show = true,
            title = "체감 아이템 수정",
            summary = "'${itemToEdit.name}' 항목의 변경 내용을 저장할까요?",
            confirmText = "저장",
            onDismiss = { pendingEditItem = null },
            onConfirm = {
                onUpsert(itemToEdit)
                editingItem = null
                nameText = ""
                priceText = ""
                pendingEditItem = null
            }
        )
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
    var emojiText by remember(show, existingGoal) { mutableStateOf(existingGoal?.emoji ?: "update") }
    var targetText by remember(show, existingGoal) { mutableStateOf(existingGoal?.targetAmount?.toString() ?: "") }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pendingEditGoal by remember { mutableStateOf<WishlistGoal?>(null) }

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
                        .squircleClip(14.dp)
                        .clickable { showEmojiPicker = true }
                ) {
                    MiuixEntityBadge(
                        emoji = emojiText,
                        fallbackIcon = MiuixIcons.Update,
                        accentColor = MiuixTheme.colorScheme.primary,
                        size = 54.dp,
                        cornerRadius = 14.dp
                    )
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
                text = "왼쪽 아이콘을 눌러 원하는 아이콘을 선택할 수 있습니다",
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
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.weight(0.8f)
                    )
                }
                TextButton(
                    text = "목표 저장",
                    onClick = {
                        val clean = titleText.trim()
                        val target = targetText.toLongOrNull() ?: 0L
                        if (clean.isNotEmpty() && target > 0L) {
                            val built = WishlistGoal(
                                id = existingGoal?.id ?: UUID.randomUUID().toString(),
                                title = clean,
                                emoji = emojiText.ifBlank { "update" },
                                targetAmount = target,
                                createdAtMillis = existingGoal?.createdAtMillis ?: System.currentTimeMillis()
                            )
                            if (existingGoal != null) {
                                pendingEditGoal = built
                            } else {
                                onSave(built)
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1.2f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    MiuixIconPickerSheet(
        show = showEmojiPicker,
        title = "목표 아이콘 선택",
        currentIconKey = emojiText,
        allowClear = false,
        onDismiss = { showEmojiPicker = false },
        onSelect = { emojiText = it.ifBlank { "update" } }
    )

    if (showDeleteConfirm && existingGoal != null) {
        ConfirmActionDialog(
            show = true,
            title = "목표 삭제",
            summary = "'${existingGoal.title}' 목표를 삭제할까요?",
            confirmText = "삭제",
            isDestructive = true,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                onDelete(existingGoal.id)
            }
        )
    }

    val goalToEdit = pendingEditGoal
    if (goalToEdit != null) {
        ConfirmActionDialog(
            show = true,
            title = "목표 수정",
            summary = "'${goalToEdit.title}' 목표의 변경 내용을 저장할까요?",
            confirmText = "저장",
            onDismiss = { pendingEditGoal = null },
            onConfirm = {
                pendingEditGoal = null
                onSave(goalToEdit)
            }
        )
    }
}

@Composable
private fun GoalArchiveBottomSheet(
    show: Boolean,
    archivedGoals: List<ArchivedGoalAchievement>,
    onDismiss: () -> Unit,
    onDeleteArchive: (String) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<ArchivedGoalAchievement?>(null) }

    OverlayBottomSheet(
        show = show,
        title = "목표 달성 아카이브",
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (archivedGoals.isEmpty()) {
                Text(
                    text = "아직 달성한 목표가 없습니다. 목표 100%를 달성하면 이곳에 보관할 수 있습니다.",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            } else {
                archivedGoals.forEach { item ->
                    val achievedDate = remember(item.achievedAtMillis) {
                        PayrollCalculator.formatDate(
                            Instant.ofEpochMilli(item.achievedAtMillis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .squircleBackground(MiuixTheme.colorScheme.surfaceContainer, 14.dp)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            MiuixEntityBadge(
                                emoji = item.emoji,
                                fallbackIcon = MiuixIcons.Favorites,
                                accentColor = Color(0xFF10B981),
                                size = 40.dp,
                                cornerRadius = 11.dp
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = item.title,
                                    style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${PayrollCalculator.formatWon(item.targetAmount)} · 약 ${String.format(Locale.US, "%.1f", item.estimatedWorkHours)}시간 근무로 달성",
                                    style = MiuixTheme.textStyles.footnote2.copy(fontFeatureSettings = "tnum"),
                                    color = MiuixTheme.colorScheme.primary
                                )
                                Text(
                                    text = "달성일 $achievedDate",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                        }
                        IconButton(onClick = { pendingDelete = item }) {
                            Icon(MiuixIcons.Delete, contentDescription = "아카이브 삭제")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    val targetDelete = pendingDelete
    if (targetDelete != null) {
        ConfirmActionDialog(
            show = true,
            title = "아카이브 기록 삭제",
            summary = "'${targetDelete.title}' 달성 기록을 삭제할까요?",
            confirmText = "삭제",
            isDestructive = true,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                onDeleteArchive(targetDelete.id)
                pendingDelete = null
            }
        )
    }
}
