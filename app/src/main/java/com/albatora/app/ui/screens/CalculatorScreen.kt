package com.albatora.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.albatora.app.domain.PayrollCalculator
import com.albatora.app.model.FeeType
import com.albatora.app.model.TaxMode
import com.albatora.app.ui.components.SquirclePillBadge
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToLong

@Composable
fun CalculatorScreen(
    defaultMinWage: Long,
    scrollBehavior: ScrollBehavior,
    onCreateJobWithWage: (Long) -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("시급·월급", "주휴수당", "일용직·순수익", "퇴직금")
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 116.dp + navBottomInset),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            TabRowWithContour(
                tabs = tabs,
                selectedTabIndex = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }

        item {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val forward = targetState >= initialState
                    (slideInHorizontally(animationSpec = tween(220)) { w -> if (forward) w / 8 else -w / 8 } +
                        fadeIn(tween(200)))
                        .togetherWith(
                            slideOutHorizontally(animationSpec = tween(180)) { w -> if (forward) -w / 8 else w / 8 } +
                                fadeOut(tween(150))
                        )
                },
                label = "calculator_subtab"
            ) { tab ->
                when (tab) {
                    0 -> HourlyMonthlyCalculator(defaultMinWage, onCreateJobWithWage)
                    1 -> WeeklyHolidayCalculator(defaultMinWage, onCreateJobWithWage)
                    2 -> DailyWorkerNetCalculator()
                    else -> SeveranceCalculator()
                }
            }
        }
    }
}

@Composable
private fun HourlyMonthlyCalculator(
    defaultMinWage: Long,
    onCreateJobWithWage: (Long) -> Unit
) {
    var hourlyText by remember { mutableStateOf(defaultMinWage.toString()) }
    var dailyHoursText by remember { mutableStateOf("8") }
    var weeklyDaysText by remember { mutableStateOf("5") }
    var includeWeeklyHoliday by remember { mutableStateOf(true) }
    var taxIdx by remember { mutableIntStateOf(0) }
    val taxModes = listOf(TaxMode.NONE, TaxMode.FREELANCE_3_3, TaxMode.INSURANCE_4)

    val hourly = hourlyText.toLongOrNull() ?: 0L
    val dailyHours = (dailyHoursText.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 24.0)
    val weeklyDays = (weeklyDaysText.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 7.0)
    val weeklyHours = dailyHours * weeklyDays
    val dailyPay = (hourly * dailyHours).roundToLong()
    val weeklyBase = (hourly * weeklyHours).roundToLong()
    val weeklyHolidayHours = if (includeWeeklyHoliday && weeklyHours >= 15.0) {
        (min(weeklyHours, 40.0) / 40.0) * 8.0
    } else 0.0
    val weeklyHolidayPay = (weeklyHolidayHours * hourly).roundToLong()
    val monthlyWeeks = 4.345
    val monthlyGross = ((weeklyBase + weeklyHolidayPay) * monthlyWeeks).roundToLong()
    val tax = PayrollCalculator.computeTaxAmount(monthlyGross, taxModes[taxIdx], 0.0, false)
    val monthlyNet = monthlyGross - tax

    val inputSection: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextField(
                        value = hourlyText,
                        onValueChange = { hourlyText = it.filter(Char::isDigit) },
                        label = "시급 (원, 2026 최저시급 ${PayrollCalculator.formatWon(defaultMinWage)})",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            SquirclePillBadge(
                                text = "최저시급 ${PayrollCalculator.formatWon(defaultMinWage)}",
                                backgroundColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.14f),
                                contentColor = MiuixTheme.colorScheme.primary,
                                onClick = { hourlyText = defaultMinWage.toString() }
                            )
                        }
                        item {
                            SquirclePillBadge(
                                text = "+1,000원",
                                backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                                contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
                                onClick = { hourlyText = ((hourlyText.toLongOrNull() ?: defaultMinWage) + 1000L).toString() }
                            )
                        }
                        item {
                            SquirclePillBadge(
                                text = "기본값 초기화",
                                backgroundColor = MiuixTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                onClick = {
                                    hourlyText = defaultMinWage.toString()
                                    dailyHoursText = "8"
                                    weeklyDaysText = "5"
                                }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextField(
                            value = dailyHoursText,
                            onValueChange = { dailyHoursText = it },
                            label = "일 근무 시간",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        TextField(
                            value = weeklyDaysText,
                            onValueChange = { weeklyDaysText = it },
                            label = "주 근무 일수",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    title = "주휴수당 포함 계산 (주 15시간 이상)",
                    summary = "주 ${String.format(Locale.US, "%.1f", weeklyHours)}시간 근무",
                    checked = includeWeeklyHoliday,
                    onCheckedChange = { includeWeeklyHoliday = it }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                OverlayDropdownPreference(
                    title = "세금 공제 선택",
                    summary = taxModes[taxIdx].label,
                    items = taxModes.map { it.label },
                    selectedIndex = taxIdx,
                    onSelectedIndexChange = { taxIdx = it }
                )
            }
        }
    }

    val resultSection: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer),
                insideMargin = PaddingValues(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("예상 월 실수령액 (평균 4.345주)", style = MiuixTheme.textStyles.footnote1)
                    Text(
                        PayrollCalculator.formatWon(monthlyNet),
                        style = MiuixTheme.textStyles.title2.copy(fontWeight = FontWeight.Bold),
                        color = MiuixTheme.colorScheme.primary
                    )
                    HorizontalDivider()
                    CalcLine("예상 일급", PayrollCalculator.formatWon(dailyPay))
                    CalcLine("예상 주급 (주휴 포함)", PayrollCalculator.formatWon(weeklyBase + weeklyHolidayPay))
                    CalcLine("주휴수당 (주)", PayrollCalculator.formatWon(weeklyHolidayPay))
                    CalcLine("세금 공제 (월)", "-${PayrollCalculator.formatWon(tax)}")
                }
            }
            if (hourly > 0L) {
                TextButton(
                    text = "이 시급(${PayrollCalculator.formatWon(hourly)})으로 새 알바 등록",
                    onClick = { onCreateJobWithWage(hourly) },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth >= 600.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(modifier = Modifier.weight(1.05f)) { inputSection() }
                Box(modifier = Modifier.weight(0.95f)) { resultSection() }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                inputSection()
                resultSection()
            }
        }
    }
}

@Composable
private fun WeeklyHolidayCalculator(
    defaultMinWage: Long,
    onCreateJobWithWage: (Long) -> Unit
) {
    var hourlyText by remember { mutableStateOf(defaultMinWage.toString()) }
    var weeklyHoursText by remember { mutableStateOf("20") }
    var forceInclude by remember { mutableStateOf(false) }

    val hourly = hourlyText.toLongOrNull() ?: 0L
    val hours = (weeklyHoursText.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 60.0)
    val eligible = hours >= 15.0 || (forceInclude && hours > 0.0)
    val capped = min(hours, 40.0)
    val whHours = if (eligible) (capped / 40.0) * 8.0 else 0.0
    val whPay = (whHours * hourly).roundToLong()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(
                    value = hourlyText,
                    onValueChange = { hourlyText = it.filter(Char::isDigit) },
                    label = "시급 (원)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextField(
                    value = weeklyHoursText,
                    onValueChange = { weeklyHoursText = it },
                    label = "1주 소정 근로시간 (예: 15, 20, 40)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        SquirclePillBadge(
                            text = "최저시급 ${PayrollCalculator.formatWon(defaultMinWage)}",
                            backgroundColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.14f),
                            contentColor = MiuixTheme.colorScheme.primary,
                            onClick = { hourlyText = defaultMinWage.toString() }
                        )
                    }
                    item {
                        SquirclePillBadge(
                            text = "주 15h",
                            backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                            contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
                            onClick = { weeklyHoursText = "15" }
                        )
                    }
                    item {
                        SquirclePillBadge(
                            text = "주 40h",
                            backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                            contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
                            onClick = { weeklyHoursText = "40" }
                        )
                    }
                }
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            SwitchPreference(
                title = "주 15시간 미만 강제 포함",
                summary = "법정 기준은 주 15시간 이상 개근 시 지급 (최대 40시간 캡)",
                checked = forceInclude,
                onCheckedChange = { forceInclude = it }
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer),
            insideMargin = PaddingValues(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (eligible) "이번 주 예상 주휴수당" else "주 15시간 미만 (미대상)",
                    style = MiuixTheme.textStyles.footnote1
                )
                Text(
                    PayrollCalculator.formatWon(whPay),
                    style = MiuixTheme.textStyles.title2.copy(fontWeight = FontWeight.Bold),
                    color = MiuixTheme.colorScheme.primary
                )
                CalcLine("적용 주휴시간", "${String.format(Locale.US, "%.2f", whHours)}시간 (상한 8시간)")
                CalcLine("실질 시급 환산", PayrollCalculator.formatWon(if (hours > 0) ((hours * hourly + whPay) / hours).roundToLong() else 0L))
            }
        }
        if (hourly > 0L) {
            TextButton(
                text = "이 시급(${PayrollCalculator.formatWon(hourly)})으로 새 알바 등록",
                onClick = { onCreateJobWithWage(hourly) },
                colors = ButtonDefaults.textButtonColorsPrimary(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DailyWorkerNetCalculator() {
    var dailyPayText by remember { mutableStateOf("180000") }
    var bonusText by remember { mutableStateOf("0") }
    var transportText by remember { mutableStateOf("3000") }
    var mealText by remember { mutableStateOf("0") }
    var feeText by remember { mutableStateOf("10") }
    var feeType by remember { mutableStateOf(FeeType.PERCENT) }
    var includeLocalTax by remember { mutableStateOf(true) }

    val daily = dailyPayText.toLongOrNull() ?: 0L
    val bonus = bonusText.toLongOrNull() ?: 0L
    val gross = daily + bonus
    val excess = (gross - 150_000L).coerceAtLeast(0L)
    val tax = PayrollCalculator.computeTaxAmount(gross, TaxMode.DAILY_WORKER, 0.0, includeLocalTax)
    val fee = PayrollCalculator.computeAgencyFee(gross, feeText.toDoubleOrNull() ?: 0.0, feeType)
    val expenses = (transportText.toLongOrNull() ?: 0L) + (mealText.toLongOrNull() ?: 0L)
    val net = gross - tax - fee - expenses

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextField(
                        value = dailyPayText,
                        onValueChange = { dailyPayText = it.filter(Char::isDigit) },
                        label = "일당/건당 총액",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextField(
                        value = bonusText,
                        onValueChange = { bonusText = it.filter(Char::isDigit) },
                        label = "현장 지원금(+)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        SquirclePillBadge(
                            text = "150,000원 (비과세 한도)",
                            backgroundColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.14f),
                            contentColor = MiuixTheme.colorScheme.primary,
                            onClick = { dailyPayText = "150000" }
                        )
                    }
                    item {
                        SquirclePillBadge(
                            text = "180,000원",
                            backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                            contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
                            onClick = { dailyPayText = "180000" }
                        )
                    }
                    item {
                        SquirclePillBadge(
                            text = "초기화",
                            backgroundColor = MiuixTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            onClick = {
                                dailyPayText = "180000"
                                bonusText = "0"
                                transportText = "3000"
                                mealText = "0"
                                feeText = "10"
                            }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextField(
                        value = transportText,
                        onValueChange = { transportText = it.filter(Char::isDigit) },
                        label = "교통비(-)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextField(
                        value = mealText,
                        onValueChange = { mealText = it.filter(Char::isDigit) },
                        label = "식비(-)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = feeText,
                        onValueChange = { feeText = it },
                        label = "수수료(-)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1.3f)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        TabRowWithContour(
                            tabs = FeeType.entries.map { it.label },
                            selectedTabIndex = FeeType.entries.indexOf(feeType),
                            onTabSelected = { feeType = FeeType.entries[it] }
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            SwitchPreference(
                title = "지방소득세 포함 실효 2.97%",
                summary = "일 15만 원 비과세 공제 초과분(${PayrollCalculator.formatWon(excess)}) × ${if (includeLocalTax) "2.97%" else "2.7%"}",
                checked = includeLocalTax,
                onCheckedChange = { includeLocalTax = it }
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer),
            insideMargin = PaddingValues(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("일용직 최종 순수익", style = MiuixTheme.textStyles.footnote1)
                Text(
                    PayrollCalculator.formatWon(net),
                    style = MiuixTheme.textStyles.title2.copy(fontWeight = FontWeight.Bold),
                    color = MiuixTheme.colorScheme.primary
                )
                HorizontalDivider()
                CalcLine("세전 총액", PayrollCalculator.formatWon(gross))
                CalcLine("비과세 공제", PayrollCalculator.formatWon(min(gross, 150_000L)))
                CalcLine("원천징수 세금", "-${PayrollCalculator.formatWon(tax)}", MiuixTheme.colorScheme.error)
                CalcLine("인력·플랫폼 수수료", "-${PayrollCalculator.formatWon(fee)}", MiuixTheme.colorScheme.error)
                CalcLine("교통비·식비 지출", "-${PayrollCalculator.formatWon(expenses)}", MiuixTheme.colorScheme.error)
                if (tax in 1L..999L) {
                    Text(
                        "참고: 산출 소득세가 1,000원 미만인 경우 현장 기준 소액부징수로 0원 공제될 수 있습니다.",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        }
    }
}

@Composable
private fun SeveranceCalculator() {
    var monthlyAvgText by remember { mutableStateOf("2156880") }
    var workedDaysText by remember { mutableStateOf("365") }
    var weeklyAvgHoursText by remember { mutableStateOf("40") }

    val monthlyAvg = monthlyAvgText.toLongOrNull() ?: 0L
    val workedDays = workedDaysText.toLongOrNull() ?: 0L
    val weeklyHours = weeklyAvgHoursText.toDoubleOrNull() ?: 0.0
    val (eligible, reason, severance) = PayrollCalculator.calculateSeverancePay(monthlyAvg, workedDays, weeklyHours)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(
                    value = monthlyAvgText,
                    onValueChange = { monthlyAvgText = it.filter(Char::isDigit) },
                    label = "최근 3개월 월 평균 급여 (원)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextField(
                        value = workedDaysText,
                        onValueChange = { workedDaysText = it.filter(Char::isDigit) },
                        label = "총 계속근로일수 (일)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    TextField(
                        value = weeklyAvgHoursText,
                        onValueChange = { weeklyAvgHoursText = it },
                        label = "주 평균 근무시간",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        val resultContainerColor by animateColorAsState(
            targetValue = if (eligible) MiuixTheme.colorScheme.secondaryContainer else MiuixTheme.colorScheme.errorContainer,
            label = "severance_container"
        )
        val resultAccentColor by animateColorAsState(
            targetValue = if (eligible) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.error,
            label = "severance_accent"
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            colors = CardDefaults.defaultColors(color = resultContainerColor),
            insideMargin = PaddingValues(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("예상 법정 퇴직금 (세전)", style = MiuixTheme.textStyles.footnote1)
                Text(
                    PayrollCalculator.formatWon(severance),
                    style = MiuixTheme.textStyles.title2.copy(fontWeight = FontWeight.Bold),
                    color = resultAccentColor
                )
                Text(
                    text = reason,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
    }
}

@Composable
private fun CalcLine(
    label: String,
    value: String,
    valueColor: Color = MiuixTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Text(value, style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold), color = valueColor)
    }
}
