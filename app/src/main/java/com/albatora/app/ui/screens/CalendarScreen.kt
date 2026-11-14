package com.albatora.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.albatora.app.domain.CalendarDayScheduleItem
import com.albatora.app.domain.PayrollCalculator
import com.albatora.app.model.*
import com.albatora.app.ui.components.MiuixGlassIcon
import com.albatora.app.ui.components.MiuixTimePickerDialog
import com.albatora.app.ui.components.SquirclePillBadge
import com.albatora.app.ui.components.dayOfWeekKorean
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.math.abs

@Composable
fun CalendarScreen(
    state: AlbaToraDataState,
    scrollBehavior: ScrollBehavior,
    onQuickAddShortTermForDate: (String) -> Unit,
    onEditRecord: (WorkRecord) -> Unit,
    onTogglePaymentStatus: (String) -> Unit,
    onExtendOneHour: (WorkRecord) -> Unit,
    onMarkEarlyLeave: (WorkRecord) -> Unit,
    onMarkAbsent: (WorkRecord) -> Unit,
    onAddSubstituteFromJob: (Job, LocalDate, Int, Int, Int, BreakPayType) -> Unit,
    onStampTemplateOnDate: (ShortTermTemplate, LocalDate) -> Unit,
    onNotify: (String) -> Unit
) {
    var displayMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var activeStampTemplate by remember { mutableStateOf<ShortTermTemplate?>(null) }
    var showSubstituteDialog by remember { mutableStateOf(false) }

    val nowDt = remember { LocalDateTime.now() }
    val monthDates = remember(displayMonth) { buildMonthGridDates(displayMonth) }

    val dayItemsMap = remember(state, displayMonth) {
        val map = mutableMapOf<LocalDate, List<CalendarDayScheduleItem>>()
        val days = (1..displayMonth.lengthOfMonth()).map { displayMonth.atDay(it) }
        days.forEach { d ->
            map[d] = PayrollCalculator.resolveCalendarDayItems(state, d, nowDt)
        }
        map
    }

    val selectedItems = dayItemsMap[selectedDate] ?: PayrollCalculator.resolveCalendarDayItems(state, selectedDate, nowDt)
    val monthTotal = remember(state, displayMonth) {
        PayrollCalculator.calculateSettlementSummary(
            state,
            displayMonth.atDay(1),
            displayMonth.atEndOfMonth()
        ).totalNetPay
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Month header + Net Summary
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { displayMonth = displayMonth.minusMonths(1) }) {
                        Icon(MiuixIcons.ChevronBackward, contentDescription = "이전 달")
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${displayMonth.year}년 ${displayMonth.monthValue}월",
                            style = MiuixTheme.textStyles.title4.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "월 정산 예상 ${PayrollCalculator.formatWon(monthTotal)}",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { displayMonth = displayMonth.plusMonths(1) }) {
                        Icon(MiuixIcons.ChevronForward, contentDescription = "다음 달")
                    }
                }
            }
        }

        // Q8-2: 1-tap Stamp Mode for Short-term Presets
        if (state.templates.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (activeStampTemplate != null) {
                            "⚡ 원탭 도장 모드 켜짐: 달력에서 날짜를 누르면 '${activeStampTemplate!!.name}'이 즉시 등록됩니다"
                        } else {
                            "단기 프리셋 원탭 도장 (선택 후 날짜를 탭하세요)"
                        },
                        style = MiuixTheme.textStyles.footnote1,
                        color = if (activeStampTemplate != null) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.templates, key = { it.id }) { tpl ->
                            val picked = activeStampTemplate?.id == tpl.id
                            SquirclePillBadge(
                                text = if (picked) "✔ ${tpl.name}" else "⚡ ${tpl.name}",
                                backgroundColor = if (picked) Color(tpl.colorHex) else Color(tpl.colorHex).copy(alpha = 0.16f),
                                contentColor = if (picked) Color.White else Color(tpl.colorHex),
                                onClick = {
                                    activeStampTemplate = if (picked) null else tpl
                                }
                            )
                        }
                    }
                }
            }
        }

        // Monthly Grid Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Week Header (Sun..Sat)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { idx, label ->
                            val col = when (idx) {
                                0 -> MiuixTheme.colorScheme.error
                                6 -> MiuixTheme.colorScheme.primary
                                else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                            }
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MiuixTheme.textStyles.footnote2.copy(fontWeight = FontWeight.SemiBold),
                                    color = col
                                )
                            }
                        }
                    }

                    val weeks = monthDates.chunked(7)
                    val today = remember { LocalDate.now() }
                    weeks.forEach { week ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            week.forEach { cellDate ->
                                if (cellDate == null) {
                                    Spacer(modifier = Modifier.weight(1f).height(54.dp))
                                } else {
                                    val items = dayItemsMap[cellDate].orEmpty()
                                    val isSelected = cellDate == selectedDate
                                    val isToday = cellDate == today
                                    val isHolidayOrSun = cellDate.dayOfWeek == DayOfWeek.SUNDAY ||
                                        PayrollCalculator.isKoreanPublicHoliday(cellDate)
                                    val dayNet = items.filter { it.status != ShiftStatus.ABSENT }.sumOf { it.estimatedNetPay }
                                    val hasUnpaid = items.any { it.paymentStatus == PaymentStatus.UNPAID }

                                    val cellBg = when {
                                        isSelected -> MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
                                        isToday -> MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                        else -> Color.Transparent
                                    }

                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(54.dp)
                                            .squircleBackground(cellBg, 10.dp)
                                            .then(
                                                if (isSelected) Modifier.border(
                                                    1.5.dp,
                                                    MiuixTheme.colorScheme.primary,
                                                    androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                                                ) else Modifier
                                            )
                                            .squircleClip(10.dp)
                                            .clickable {
                                                selectedDate = cellDate
                                                val stamp = activeStampTemplate
                                                if (stamp != null) {
                                                    onStampTemplateOnDate(stamp, cellDate)
                                                    onNotify("${cellDate.monthValue}/${cellDate.dayOfMonth} '${stamp.name}' 원탭 등록 완료")
                                                }
                                            }
                                            .padding(2.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = cellDate.dayOfMonth.toString(),
                                            style = MiuixTheme.textStyles.footnote1.copy(
                                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = when {
                                                isHolidayOrSun -> MiuixTheme.colorScheme.error
                                                cellDate.dayOfWeek == DayOfWeek.SATURDAY -> MiuixTheme.colorScheme.primary
                                                else -> MiuixTheme.colorScheme.onSurface
                                            }
                                        )

                                        if (items.isNotEmpty()) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                                items.take(3).forEach { itItem ->
                                                    Box(
                                                        modifier = Modifier
                                                            .size(5.dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                if (itItem.status == ShiftStatus.ABSENT) {
                                                                    MiuixTheme.colorScheme.outline
                                                                } else {
                                                                    Color(itItem.colorHex)
                                                                }
                                                            )
                                                    )
                                                }
                                            }
                                            Text(
                                                text = compactManWon(dayNet, hasUnpaid),
                                                style = MiuixTheme.textStyles.footnote2.copy(
                                                    fontSize = MiuixTheme.textStyles.footnote2.fontSize * 0.82f
                                                ),
                                                color = if (hasUnpaid) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Clip
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.height(10.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Q15-3: 1-Tap Quick Actions Bar for Selected Date
        item {
            val primaryItem = selectedItems.firstOrNull { it.record != null && it.status != ShiftStatus.ABSENT }
            SmallTitle(text = "${selectedDate.monthValue}월 ${selectedDate.dayOfMonth}일 (${dayOfWeekKorean(selectedDate.dayOfWeek.value)}) 원탭 퀵 액션")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    SquirclePillBadge(
                        text = "+1시간 연장",
                        backgroundColor = Color(0xFF3482FF).copy(alpha = 0.16f),
                        contentColor = Color(0xFF3482FF),
                        onClick = {
                            val targetRec = primaryItem?.record
                            if (targetRec != null) {
                                onExtendOneHour(targetRec)
                                onNotify("${targetRec.title} +1시간 연장 반영")
                            } else {
                                onNotify("연장할 근무를 먼저 등록하거나 선택된 날짜의 근무가 필요합니다.")
                            }
                        }
                    )
                }
                item {
                    SquirclePillBadge(
                        text = "대타 추가",
                        backgroundColor = Color(0xFF10B981).copy(alpha = 0.16f),
                        contentColor = Color(0xFF059669),
                        onClick = {
                            if (state.jobs.isEmpty()) {
                                onQuickAddShortTermForDate(PayrollCalculator.formatDate(selectedDate))
                            } else {
                                showSubstituteDialog = true
                            }
                        }
                    )
                }
                item {
                    SquirclePillBadge(
                        text = "조퇴 (-1h)",
                        backgroundColor = Color(0xFFF59E0B).copy(alpha = 0.16f),
                        contentColor = Color(0xFFD97706),
                        onClick = {
                            val targetRec = primaryItem?.record
                            if (targetRec != null) {
                                onMarkEarlyLeave(targetRec)
                                onNotify("${targetRec.title} 조퇴 처리 완료")
                            } else {
                                onNotify("조퇴 처리할 근무가 해당 날짜에 없습니다.")
                            }
                        }
                    )
                }
                item {
                    SquirclePillBadge(
                        text = "결근 처리",
                        backgroundColor = MiuixTheme.colorScheme.error.copy(alpha = 0.16f),
                        contentColor = MiuixTheme.colorScheme.error,
                        onClick = {
                            val targetRec = primaryItem?.record
                            if (targetRec != null) {
                                onMarkAbsent(targetRec)
                                onNotify("${targetRec.title} 결근 처리 (주휴수당 제외 규칙 자동 연동)")
                            } else {
                                onNotify("결근 처리할 스케줄/근무가 해당 날짜에 없습니다.")
                            }
                        }
                    )
                }
                item {
                    SquirclePillBadge(
                        text = "+ 단기 퀵 추가",
                        backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                        contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
                        onClick = {
                            onQuickAddShortTermForDate(PayrollCalculator.formatDate(selectedDate))
                        }
                    )
                }
            }
        }

        // Selected Day Shift Cards
        if (selectedItems.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(18.dp),
                    onClick = { onQuickAddShortTermForDate(PayrollCalculator.formatDate(selectedDate)) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "${selectedDate.monthValue}월 ${selectedDate.dayOfMonth}일 근무 기록 없음",
                                style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                "탭하여 이 날짜에 단기·대타 근무를 10초 만에 등록하세요",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                        Icon(MiuixIcons.AddCircle, contentDescription = null, tint = MiuixTheme.colorScheme.primary)
                    }
                }
            }
        } else {
            items(selectedItems) { item ->
                CalendarDayShiftCard(
                    item = item,
                    onClick = {
                        item.record?.let { rec ->
                            onEditRecord(rec)
                        }
                    },
                    onTogglePayment = {
                        val rec = item.record
                        if (rec != null && !rec.id.startsWith("cal_") && !rec.id.startsWith("virtual_")) {
                            onTogglePaymentStatus(rec.id)
                        }
                    }
                )
            }
        }
    }

    if (showSubstituteDialog && state.jobs.isNotEmpty()) {
        SubstituteQuickDialog(
            jobs = state.jobs,
            date = selectedDate,
            onDismiss = { showSubstituteDialog = false },
            onConfirm = { job, sMin, eMin, brkMins ->
                onAddSubstituteFromJob(job, selectedDate, sMin, eMin, brkMins, BreakPayType.UNPAID)
                showSubstituteDialog = false
                onNotify("${job.name} 대타 근무 추가 완료")
            }
        )
    }
}

@Composable
private fun CalendarDayShiftCard(
    item: CalendarDayScheduleItem,
    onClick: () -> Unit,
    onTogglePayment: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
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
                    icon = if (item.isCurrentlyLive) MiuixIcons.Stopwatch else MiuixIcons.Notes,
                    contentDescription = null,
                    accentColor = Color(item.colorHex)
                )
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = item.title,
                            style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.SemiBold)
                        )
                        if (item.status != ShiftStatus.NORMAL) {
                            SquirclePillBadge(
                                text = item.status.label,
                                backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                                contentColor = MiuixTheme.colorScheme.onSecondaryContainer
                            )
                        } else if (item.isVirtualFuture) {
                            SquirclePillBadge(
                                text = "예정",
                                backgroundColor = Color(item.colorHex).copy(alpha = 0.14f),
                                contentColor = Color(item.colorHex)
                            )
                        }
                    }
                    Text(
                        text = "${PayrollCalculator.formatMinutes(item.startMinute)} ~ ${PayrollCalculator.formatMinutes(item.endMinute)}",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (item.status == ShiftStatus.ABSENT) "0원" else PayrollCalculator.formatWon(item.estimatedNetPay),
                    style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Bold),
                    color = if (item.status == ShiftStatus.ABSENT) MiuixTheme.colorScheme.outline else MiuixTheme.colorScheme.onSurface
                )
                val pStatus = item.paymentStatus
                if (pStatus != null && item.record != null && !item.record.id.startsWith("cal_")) {
                    SquirclePillBadge(
                        text = "${pStatus.badgeEmoji} ${pStatus.label}",
                        backgroundColor = if (pStatus == PaymentStatus.UNPAID) {
                            MiuixTheme.colorScheme.error.copy(alpha = 0.14f)
                        } else {
                            Color(0xFF10B981).copy(alpha = 0.14f)
                        },
                        contentColor = if (pStatus == PaymentStatus.UNPAID) {
                            MiuixTheme.colorScheme.error
                        } else {
                            Color(0xFF059669)
                        },
                        onClick = onTogglePayment
                    )
                }
            }
        }
    }
}

@Composable
private fun SubstituteQuickDialog(
    jobs: List<Job>,
    date: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (Job, Int, Int, Int) -> Unit
) {
    var selectedJob by remember { mutableStateOf(jobs.first()) }
    var startMin by remember { mutableIntStateOf(selectedJob.schedules.firstOrNull()?.startMinute ?: 14 * 60) }
    var endMin by remember { mutableIntStateOf(selectedJob.schedules.firstOrNull()?.endMinute ?: 20 * 60) }
    var pickMode by remember { mutableStateOf<String?>(null) }

    val dur = PayrollCalculator.rawDurationMinutes(startMin, endMin)
    val brk = PayrollCalculator.recommendedBreakMinutes(dur)

    OverlayDialog(
        show = true,
        title = "${date.monthValue}/${date.dayOfMonth} 대타 근무 추가",
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(jobs, key = { it.id }) { j ->
                    val active = j.id == selectedJob.id
                    SquirclePillBadge(
                        text = j.name,
                        backgroundColor = if (active) Color(j.colorHex) else Color(j.colorHex).copy(alpha = 0.14f),
                        contentColor = if (active) Color.White else Color(j.colorHex),
                        onClick = { selectedJob = j }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .squircleBackground(MiuixTheme.colorScheme.secondaryContainer, 10.dp)
                        .clickable { pickMode = "s" }
                        .padding(10.dp)
                ) {
                    Text("출근 ${PayrollCalculator.formatMinutes(startMin)}", style = MiuixTheme.textStyles.body2)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .squircleBackground(MiuixTheme.colorScheme.secondaryContainer, 10.dp)
                        .clickable { pickMode = "e" }
                        .padding(10.dp)
                ) {
                    Text("퇴근 ${PayrollCalculator.formatMinutes(endMin)}", style = MiuixTheme.textStyles.body2)
                }
            }
            Text(
                "시급 ${PayrollCalculator.formatWon(PayrollCalculator.effectiveJobWage(selectedJob, date))} · 자동 휴게 ${brk}분 적용",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(text = "취소", onClick = onDismiss, modifier = Modifier.weight(1f))
                TextButton(
                    text = "대타 등록",
                    onClick = { onConfirm(selectedJob, startMin, endMin, brk) },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (pickMode != null) {
            MiuixTimePickerDialog(
                show = true,
                title = "시각 선택",
                initialMinuteOfDay = if (pickMode == "s") startMin else endMin,
                onDismiss = { pickMode = null },
                onConfirm = {
                    if (pickMode == "s") startMin = it else endMin = it
                    pickMode = null
                }
            )
        }
    }
}

private fun buildMonthGridDates(month: YearMonth): List<LocalDate?> {
    val firstDay = month.atDay(1)
    // DayOfWeek: Mon=1..Sun=7 -> Sunday-first column index = 0..6
    val leadingBlanks = firstDay.dayOfWeek.value % 7
    val list = mutableListOf<LocalDate?>()
    repeat(leadingBlanks) { list.add(null) }
    for (d in 1..month.lengthOfMonth()) {
        list.add(month.atDay(d))
    }
    while (list.size % 7 != 0) {
        list.add(null)
    }
    return list
}

private fun compactManWon(won: Long, unpaid: Boolean): String {
    if (won == 0L) return "0"
    val absVal = abs(won)
    val prefix = if (unpaid) "\uD83D\uDD34" else ""
    return when {
        absVal >= 10_000L -> {
            val man = absVal / 10_000.0
            if (absVal % 10_000L == 0L) "$prefix${absVal / 10_000}만" else "$prefix${String.format("%.1f", man)}만"
        }
        else -> "$prefix${absVal / 1000}천"
    }
}
