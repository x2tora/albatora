package com.albatora.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.albatora.app.domain.PayrollCalculator
import com.albatora.app.domain.ResolvedRecordItem
import com.albatora.app.model.*
import com.albatora.app.ui.components.EmptyDataBanner
import com.albatora.app.ui.components.MiuixEntityBadge
import com.albatora.app.ui.components.MiuixGlassIcon
import com.albatora.app.ui.components.SquirclePillBadge
import com.albatora.app.ui.theme.AlbaIconCatalog
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import java.time.YearMonth

@Composable
fun RecordsScreen(
    state: AlbaToraDataState,
    initialOnlyUnpaid: Boolean,
    scrollBehavior: ScrollBehavior,
    onEditRecord: (WorkRecord) -> Unit,
    onTogglePaymentStatus: (String) -> Unit,
    onAddShortTerm: () -> Unit,
    onNotify: (String) -> Unit
) {
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    var anchorMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedJobId by remember { mutableStateOf<String?>(null) } // null = 전체 합산
    var onlyUnpaid by remember(initialOnlyUnpaid) { mutableStateOf(initialOnlyUnpaid) }

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

    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 116.dp + navBottomInset)

    val filterTabsCard: @Composable () -> Unit = {
        TabRowWithContour(
            tabs = listOf("정산 기간 명세서", "미수금 모아보기 (${globalUnpaid.second.size})"),
            selectedTabIndex = if (onlyUnpaid) 1 else 0,
            onTabSelected = { onlyUnpaid = (it == 1) }
        )
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
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
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

    val metricsGridCard: @Composable () -> Unit = {
        val bonusTotal = summary.totalAllowancePay + summary.totalWeeklyHolidayPay + summary.totalSiteBonus
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RecordsMetricTile(
                    icon = MiuixIcons.BankCards,
                    accentColor = MiuixTheme.colorScheme.primary,
                    label = "예상 실수령",
                    value = PayrollCalculator.formatWon(summary.totalNetPay),
                    modifier = Modifier.weight(1f)
                )
                RecordsMetricTile(
                    icon = MiuixIcons.Stopwatch,
                    accentColor = Color(0xFF0EA5E9),
                    label = "총 근무시간",
                    value = "${PayrollCalculator.formatDurationKorean(summary.totalWorkMinutes)} (${summary.shiftCount}건)",
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RecordsMetricTile(
                    icon = MiuixIcons.FavoritesFill,
                    accentColor = Color(0xFF16A34A),
                    label = "주휴·가산수당",
                    value = "+${PayrollCalculator.formatWon(bonusTotal)}",
                    modifier = Modifier.weight(1f)
                )
                RecordsMetricTile(
                    icon = MiuixIcons.Alarm,
                    accentColor = if (summary.unpaidAmount > 0L) MiuixTheme.colorScheme.error else Color(0xFF64748B),
                    label = "기간 미수금",
                    value = if (summary.unpaidAmount > 0L) PayrollCalculator.formatWon(summary.unpaidAmount) else "없음",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    val statementCard: @Composable () -> Unit = {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            insideMargin = PaddingValues(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "최종 실수령 예상액 (순수익)",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Text(
                    text = PayrollCalculator.formatWon(summary.totalNetPay),
                    style = MiuixTheme.textStyles.title2.copy(fontWeight = FontWeight.Bold),
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
                    text = "정산 내역서 (급여명세서) 텍스트 복사",
                    onClick = {
                        val title = selectedJob?.name ?: "전체 근무지 합산"
                        val text = PayrollCalculator.buildPayslipText(title, summary)
                        clipboardManager.setText(AnnotatedString(text))
                        onNotify("급여 정산 명세서를 클립보드에 복사했습니다.")
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth()
                )
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
                    "아직 받지 못한 전체 미수금",
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
                    "총 ${globalUnpaid.second.size}건 · 각 항목의 상태 배지를 눌러 입금 완료 처리할 수 있습니다",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                )
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 600.dp

        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 116.dp + navBottomInset),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item { filterTabsCard() }
                    if (!onlyUnpaid) {
                        item { navigatorCard() }
                        item { metricsGridCard() }
                        item { statementCard() }
                    } else {
                        item { unpaidBannerCard() }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 116.dp + navBottomInset),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!onlyUnpaid) {
                        if (summary.items.isEmpty()) {
                            item {
                                EmptyDataBanner(
                                    icon = MiuixIcons.Notes,
                                    title = "선택한 기간에 근무 기록이 없어요",
                                    subtitle = "달력에서 날짜를 누르거나 단기 퀵 등록으로 근무를 추가해 보세요.",
                                    actionLabel = "단기/일용직 10초 퀵 등록",
                                    onAction = onAddShortTerm
                                )
                            }
                        } else {
                            item { SmallTitle(text = "상세 근무 내역 (${summary.items.size}건)") }
                            items(summary.items, key = { it.record.id }) { item ->
                                RecordDetailCard(
                                    item = item,
                                    modifier = Modifier.animateItem(),
                                    onEdit = { onEditRecord(item.record) },
                                    onTogglePayment = {
                                        if (!item.isVirtualSchedule) {
                                            onTogglePaymentStatus(item.record.id)
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        if (globalUnpaid.second.isEmpty()) {
                            item {
                                EmptyDataBanner(
                                    icon = MiuixIcons.Ok,
                                    title = "미수금이 없습니다",
                                    subtitle = "정산일이 지난 미수금이 없습니다",
                                    accentColor = Color(0xFF16A34A)
                                )
                            }
                        } else {
                            item { SmallTitle(text = "미수금 목록 (${globalUnpaid.second.size}건)") }
                            items(globalUnpaid.second, key = { it.record.id }) { item ->
                                RecordDetailCard(
                                    item = item,
                                    modifier = Modifier.animateItem(),
                                    onEdit = { onEditRecord(item.record) },
                                    onTogglePayment = { onTogglePaymentStatus(item.record.id) }
                                )
                            }
                        }
                        if (scheduledPending.second.isNotEmpty()) {
                            item {
                                SmallTitle(text = "정산일 예정 (${scheduledPending.second.size}건 · ${PayrollCalculator.formatWon(scheduledPending.first)})")
                            }
                            items(scheduledPending.second, key = { "sched_${it.record.id}" }) { item ->
                                RecordDetailCard(
                                    item = item,
                                    modifier = Modifier.animateItem(),
                                    onEdit = { onEditRecord(item.record) },
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
                contentPadding = listPadding,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { filterTabsCard() }
                if (!onlyUnpaid) {
                    item { navigatorCard() }
                    item { metricsGridCard() }
                    item { statementCard() }
                    if (summary.items.isEmpty()) {
                        item {
                            EmptyDataBanner(
                                icon = MiuixIcons.Notes,
                                title = "선택한 기간에 근무 기록이 없어요",
                                subtitle = "달력에서 날짜를 누르거나 단기 퀵 등록으로 근무를 추가해 보세요.",
                                actionLabel = "단기/일용직 10초 퀵 등록",
                                onAction = onAddShortTerm
                            )
                        }
                    } else {
                        item { SmallTitle(text = "상세 근무 내역 (${summary.items.size}건)") }
                        items(summary.items, key = { it.record.id }) { item ->
                            RecordDetailCard(
                                item = item,
                                modifier = Modifier.animateItem(),
                                onEdit = { onEditRecord(item.record) },
                                onTogglePayment = {
                                    if (!item.isVirtualSchedule) {
                                        onTogglePaymentStatus(item.record.id)
                                    }
                                }
                            )
                        }
                    }
                } else {
                    item { unpaidBannerCard() }
                    if (globalUnpaid.second.isEmpty()) {
                        item {
                            EmptyDataBanner(
                                icon = MiuixIcons.Ok,
                                title = "미수금이 없습니다",
                                subtitle = "정산일이 지난 미수금이 없습니다",
                                accentColor = Color(0xFF16A34A)
                            )
                        }
                    } else {
                        items(globalUnpaid.second, key = { it.record.id }) { item ->
                            RecordDetailCard(
                                item = item,
                                modifier = Modifier.animateItem(),
                                onEdit = { onEditRecord(item.record) },
                                onTogglePayment = { onTogglePaymentStatus(item.record.id) }
                            )
                        }
                    }
                    if (scheduledPending.second.isNotEmpty()) {
                        item {
                            SmallTitle(text = "정산일 예정 (${scheduledPending.second.size}건 · ${PayrollCalculator.formatWon(scheduledPending.first)})")
                        }
                        items(scheduledPending.second, key = { "sched_${it.record.id}" }) { item ->
                            RecordDetailCard(
                                item = item,
                                modifier = Modifier.animateItem(),
                                onEdit = { onEditRecord(item.record) },
                                onTogglePayment = { onTogglePaymentStatus(item.record.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordsMetricTile(
    icon: ImageVector,
    accentColor: Color,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        insideMargin = PaddingValues(13.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiuixGlassIcon(
                icon = icon,
                contentDescription = null,
                accentColor = accentColor,
                size = 36.dp,
                iconSize = 18.dp,
                cornerRadius = 11.dp
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = label,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1
                )
                Text(
                    text = value,
                    style = MiuixTheme.textStyles.body2.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
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
    onEdit: () -> Unit,
    onTogglePayment: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rec = item.record
    val br = item.breakdown
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
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
                                text = "자동정규",
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
                }
            }
        }
    }
}
