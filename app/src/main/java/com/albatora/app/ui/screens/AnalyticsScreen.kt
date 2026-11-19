package com.albatora.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.albatora.app.domain.AnalyticsCalculator
import com.albatora.app.domain.MonthlyTrendPoint
import com.albatora.app.domain.PayrollCalculator
import com.albatora.app.model.AlbaToraDataState
import com.albatora.app.ui.components.EmptyDataBanner
import com.albatora.app.ui.components.MiuixGlassIcon
import com.albatora.app.ui.components.SquirclePillBadge
import com.albatora.app.ui.theme.AlbaMotion
import com.albatora.app.ui.theme.LocalWideDualLayoutEnabled
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.BankCards
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import java.time.YearMonth
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AnalyticsScreen(
    state: AlbaToraDataState,
    scrollBehavior: ScrollBehavior,
    topInset: Dp = 0.dp,
    onUpdateMonthlyGoal: (Long) -> Unit,
    onUpdateAnnualThreshold: (Long) -> Unit,
    onNotify: (String) -> Unit
) {
    var monthCount by remember { mutableIntStateOf(6) }
    val anchorMonth = remember { YearMonth.now() }
    val snapshot = remember(state, anchorMonth, monthCount) {
        AnalyticsCalculator.evaluate(state, anchorMonth, monthCount)
    }
    var selectedMonthIndex by remember(monthCount) {
        mutableIntStateOf((snapshot.months.size - 1).coerceAtLeast(0))
    }
    var showGoalDialog by remember { mutableStateOf(false) }

    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val wideDualEnabled = LocalWideDualLayoutEnabled.current

    val summaryHeroCard: @Composable () -> Unit = {
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MiuixGlassIcon(
                            icon = MiuixIcons.BankCards,
                            contentDescription = null,
                            accentColor = Color(0xFF3482FF),
                            size = 40.dp,
                            iconSize = 20.dp,
                            cornerRadius = 12.dp
                        )
                        Column {
                            Text(
                                text = "최근 ${monthCount}개월 실수령 합계",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                            Text(
                                text = PayrollCalculator.formatWon(snapshot.totalNetWon),
                                style = MiuixTheme.textStyles.title2.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = MiuixTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(6, 12).forEach { count ->
                            val selected = monthCount == count
                            SquirclePillBadge(
                                text = "${count}개월",
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
                                onClick = { monthCount = count }
                            )
                        }
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricColumn(
                        label = "누적 근무 시간",
                        value = PayrollCalculator.formatDurationKorean(snapshot.totalWorkMinutes)
                    )
                    MetricColumn(
                        label = "평균 실효 시급",
                        value = if (snapshot.averageEffectiveHourlyWon > 0L) {
                            PayrollCalculator.formatWon(snapshot.averageEffectiveHourlyWon)
                        } else {
                            "-"
                        },
                        valueColor = MiuixTheme.colorScheme.primary
                    )
                    MetricColumn(
                        label = "${anchorMonth.year}년 누적",
                        value = PayrollCalculator.formatWon(snapshot.goalInsight.annualNetWon)
                    )
                }
            }
        }
    }

    val trendChartCard: @Composable () -> Unit = {
        val points = snapshot.months
        val selectedPoint = points.getOrNull(selectedMonthIndex.coerceIn(0, (points.size - 1).coerceAtLeast(0)))
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "월별 실수령 추이",
                        style = MiuixTheme.textStyles.subtitle.copy(fontWeight = FontWeight.Bold),
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "막대를 탭해 세부 보기",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }

                MonthlyBarChart(
                    points = points,
                    selectedIndex = selectedMonthIndex,
                    onSelectIndex = { selectedMonthIndex = it }
                )

                if (selectedPoint != null) {
                    AnimatedContent(
                        targetState = selectedPoint,
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "selected_month_detail"
                    ) { pt ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .squircleClip(14.dp)
                                .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "${pt.month.year}년 ${pt.month.monthValue}월 (${pt.shiftCount}건)",
                                    style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.SemiBold),
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${PayrollCalculator.formatDurationKorean(pt.workMinutes)} · 실효 ${PayrollCalculator.formatWon(pt.effectiveHourlyWon)}/h",
                                    style = MiuixTheme.textStyles.footnote2.copy(fontFeatureSettings = "tnum"),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                            Text(
                                text = PayrollCalculator.formatWon(pt.netPay),
                                style = MiuixTheme.textStyles.body1.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = MiuixTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }

    val goalAndThresholdCard: @Composable () -> Unit = {
        val goal = snapshot.goalInsight
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "목표 & 연간 기준선 트래커",
                        style = MiuixTheme.textStyles.subtitle.copy(fontWeight = FontWeight.Bold),
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    SquirclePillBadge(
                        text = "목표 설정",
                        backgroundColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.14f),
                        contentColor = MiuixTheme.colorScheme.primary,
                        onClick = { showGoalDialog = true }
                    )
                }

                if (goal.monthTargetWon > 0L) {
                    ProgressGaugeRow(
                        title = "${anchorMonth.monthValue}월 목표 수입",
                        valueText = "${PayrollCalculator.formatWon(goal.monthCurrentWon)} / ${PayrollCalculator.formatWon(goal.monthTargetWon)}",
                        fraction = goal.monthProgressFraction,
                        barColor = Color(0xFF10B981),
                        helperText = goal.estimatedRemainingHours?.let { hrs ->
                            String.format(Locale.KOREA, "목표까지 평균 실효시급 기준 약 %.1f시간 남음", hrs)
                        } ?: "이번 달 목표를 달성했습니다!"
                    )
                } else {
                    Text(
                        text = "이번 달 목표 금액을 설정하면 달성률과 남은 필요 근무 시간을 자동 계산합니다.",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }

                HorizontalDivider()

                ProgressGaugeRow(
                    title = "${anchorMonth.year}년 연간 소득 기준선",
                    valueText = "${PayrollCalculator.formatWon(goal.annualNetWon)} / ${PayrollCalculator.formatWon(goal.annualThresholdWon)}",
                    fraction = goal.annualThresholdFraction,
                    barColor = if (goal.annualThresholdFraction >= 0.9f) Color(0xFFF59E0B) else Color(0xFF3482FF),
                    helperText = if (goal.annualFreelanceGrossWon > 0L) {
                        "연간 3.3% 사업소득 합계: ${PayrollCalculator.formatWon(goal.annualFreelanceGrossWon)} (참고용)"
                    } else {
                        "개인 연간 기준선·피부양자 한도 등을 설정해 추적하세요 (참고용)"
                    }
                )
            }
        }
    }

    val workplaceShareCard: @Composable () -> Unit = {
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "근무지별 수익 비중",
                    style = MiuixTheme.textStyles.subtitle.copy(fontWeight = FontWeight.Bold),
                    color = MiuixTheme.colorScheme.onSurface
                )
                if (snapshot.workplaceShares.isEmpty()) {
                    Text(
                        text = "표시할 근무 기록이 아직 없습니다.",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                } else {
                    snapshot.workplaceShares.forEach { share ->
                        val animFrac by animateFloatAsState(
                            targetValue = share.shareFraction,
                            animationSpec = AlbaMotion.FluidSpring,
                            label = "share_${share.key}"
                        )
                        val color = Color(share.colorHex)
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${share.title} (${(share.shareFraction * 100f).roundToInt()}%)",
                                    style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${PayrollCalculator.formatWon(share.netPay)} · ${PayrollCalculator.formatWon(share.effectiveHourlyWon)}/h",
                                    style = MiuixTheme.textStyles.footnote1.copy(fontFeatureSettings = "tnum"),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .squircleClip(4.dp)
                                    .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(animFrac.coerceAtLeast(0.02f))
                                        .fillMaxHeight()
                                        .squircleClip(4.dp)
                                        .background(color)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val breakdownCard: @Composable () -> Unit = {
        val br = snapshot.payBreakdown
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "최근 ${monthCount}개월 급여 구성",
                    style = MiuixTheme.textStyles.subtitle.copy(fontWeight = FontWeight.Bold),
                    color = MiuixTheme.colorScheme.onSurface
                )
                HorizontalDivider()
                BreakdownLine("기본 급여", PayrollCalculator.formatWon(br.basePay))
                BreakdownLine("주휴수당", "+${PayrollCalculator.formatWon(br.weeklyHolidayPay)}", Color(0xFF10B981))
                BreakdownLine("야간 · 연장 · 휴일 수당", "+${PayrollCalculator.formatWon(br.allowancePay)}", Color(0xFF3482FF))
                if (br.siteBonus > 0L) {
                    BreakdownLine("현장 지원금 · 보너스", "+${PayrollCalculator.formatWon(br.siteBonus)}", Color(0xFF10B981))
                }
                BreakdownLine("세금 · 수수료 공제", "-${PayrollCalculator.formatWon(br.taxAndFeeDeductions)}", MiuixTheme.colorScheme.error)
                if (br.expenses > 0L) {
                    BreakdownLine("교통 · 식비 지출", "-${PayrollCalculator.formatWon(br.expenses)}", MiuixTheme.colorScheme.error)
                }
                HorizontalDivider()
                BreakdownLine("최종 실수령액", PayrollCalculator.formatWon(br.netPay), MiuixTheme.colorScheme.primary, bold = true)
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideDual = wideDualEnabled && maxWidth >= 600.dp
        val singleSidePad = ((maxWidth - 600.dp) / 2f).coerceAtLeast(20.dp)

        if (snapshot.totalNetWon == 0L && snapshot.workplaceShares.isEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = singleSidePad,
                    end = singleSidePad,
                    top = topInset + 12.dp,
                    bottom = 48.dp + navBottomInset
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { summaryHeroCard() }
                item {
                    EmptyDataBanner(
                        icon = MiuixIcons.Notes,
                        title = "분석할 근무 데이터가 아직 없어요",
                        subtitle = "근무를 기록하면 월별 수입 추이와 근무지별 실효 시급이 자동으로 분석됩니다."
                    )
                }
            }
        } else if (isWideDual) {
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
                    contentPadding = PaddingValues(top = topInset + 12.dp, bottom = 48.dp + navBottomInset),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item { summaryHeroCard() }
                    item { trendChartCard() }
                    item { goalAndThresholdCard() }
                }
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(top = topInset + 12.dp, bottom = 48.dp + navBottomInset),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item { workplaceShareCard() }
                    item { breakdownCard() }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = singleSidePad,
                    end = singleSidePad,
                    top = topInset + 12.dp,
                    bottom = 48.dp + navBottomInset
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { summaryHeroCard() }
                item { trendChartCard() }
                item { goalAndThresholdCard() }
                item { workplaceShareCard() }
                item { breakdownCard() }
            }
        }
    }

    GoalThresholdEditDialog(
        show = showGoalDialog,
        initialMonthGoal = state.settings.monthlyIncomeGoalWon,
        initialAnnualThreshold = state.settings.annualIncomeThresholdWon,
        onDismiss = { showGoalDialog = false },
        onSave = { mGoal, aThresh ->
            onUpdateMonthlyGoal(mGoal)
            onUpdateAnnualThreshold(aThresh)
            showGoalDialog = false
            onNotify("목표 및 연간 기준선이 저장되었습니다")
        }
    )
}

@Composable
private fun MonthlyBarChart(
    points: List<MonthlyTrendPoint>,
    selectedIndex: Int,
    onSelectIndex: (Int) -> Unit
) {
    val maxPay = (points.maxOfOrNull { it.netPay } ?: 0L).coerceAtLeast(1L)
    val primaryColor = MiuixTheme.colorScheme.primary
    val inactiveColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.28f)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .pointerInput(points.size) {
                    detectTapGestures { offset ->
                        if (points.isNotEmpty()) {
                            val slotWidth = size.width.toFloat() / points.size.toFloat()
                            val idx = (offset.x / slotWidth).toInt().coerceIn(0, points.lastIndex)
                            onSelectIndex(idx)
                        }
                    }
                }
        ) {
            if (points.isEmpty()) return@Canvas
            val count = points.size
            val slotW = size.width / count.toFloat()
            val barW = (slotW * 0.56f).coerceAtMost(36.dp.toPx())
            val maxBarH = size.height - 8.dp.toPx()

            points.forEachIndexed { idx, pt ->
                val frac = (pt.netPay.toFloat() / maxPay.toFloat()).coerceIn(0f, 1f)
                val barH = (maxBarH * frac).coerceAtLeast(if (pt.netPay > 0L) 6.dp.toPx() else 2.dp.toPx())
                val left = idx * slotW + (slotW - barW) / 2f
                val top = size.height - barH
                drawRoundRect(
                    color = if (idx == selectedIndex) primaryColor else inactiveColor,
                    topLeft = Offset(left, top),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            points.forEachIndexed { idx, pt ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelectIndex(idx) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = pt.label,
                        style = MiuixTheme.textStyles.footnote2.copy(
                            fontWeight = if (idx == selectedIndex) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (idx == selectedIndex) primaryColor else MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricColumn(
    label: String,
    value: String,
    valueColor: Color = MiuixTheme.colorScheme.onSurface
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2.copy(
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum"
            ),
            color = valueColor
        )
    }
}

@Composable
private fun ProgressGaugeRow(
    title: String,
    valueText: String,
    fraction: Float,
    barColor: Color,
    helperText: String
) {
    val animFrac by animateFloatAsState(
        targetValue = fraction,
        animationSpec = AlbaMotion.FluidSpring,
        label = "gauge_$title"
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium),
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = valueText,
                style = MiuixTheme.textStyles.footnote1.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum"
                ),
                color = barColor
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .squircleClip(4.dp)
                .background(MiuixTheme.colorScheme.surfaceContainerHigh)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animFrac.coerceAtLeast(0.02f))
                    .fillMaxHeight()
                    .squircleClip(4.dp)
                    .background(barColor)
            )
        }
        Text(
            text = helperText,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

@Composable
private fun BreakdownLine(
    label: String,
    value: String,
    valueColor: Color = MiuixTheme.colorScheme.onSurface,
    bold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2.copy(fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal),
            color = if (bold) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2.copy(
                fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
                fontFeatureSettings = "tnum"
            ),
            color = valueColor
        )
    }
}

@Composable
private fun GoalThresholdEditDialog(
    show: Boolean,
    initialMonthGoal: Long,
    initialAnnualThreshold: Long,
    onDismiss: () -> Unit,
    onSave: (monthGoal: Long, annualThreshold: Long) -> Unit
) {
    var monthText by remember(show, initialMonthGoal) {
        mutableStateOf(if (initialMonthGoal > 0L) initialMonthGoal.toString() else "")
    }
    var annualText by remember(show, initialAnnualThreshold) {
        mutableStateOf(initialAnnualThreshold.toString())
    }
    OverlayDialog(
        show = show,
        title = "목표 및 기준선 설정",
        summary = "월 목표 수입(0원=미설정)과 연간 소득 기준선을 입력하세요.",
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextField(
                value = monthText,
                onValueChange = { monthText = it.filter(Char::isDigit).take(9) },
                label = "월 목표 수입 (원)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = annualText,
                onValueChange = { annualText = it.filter(Char::isDigit).take(10) },
                label = "연간 소득 기준선 (원)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(
                    text = "취소",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "저장",
                    onClick = {
                        onSave(
                            monthText.toLongOrNull() ?: 0L,
                            annualText.toLongOrNull() ?: 12_000_000L
                        )
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
