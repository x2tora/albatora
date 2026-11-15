package com.albatora.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.albatora.app.domain.CalendarDayScheduleItem
import com.albatora.app.domain.PayrollCalculator
import com.albatora.app.model.*
import com.albatora.app.ui.components.ConfirmActionDialog
import com.albatora.app.ui.components.MiuixEntityBadge
import com.albatora.app.ui.components.MiuixGlassIcon
import com.albatora.app.ui.components.MiuixTimePickerDialog
import com.albatora.app.ui.components.SquirclePillBadge
import com.albatora.app.ui.components.dayOfWeekKorean
import com.albatora.app.ui.theme.AlbaIconCatalog
import com.albatora.app.ui.theme.LocalWideDualLayoutEnabled
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import java.time.DayOfWeek
import kotlin.math.PI
import kotlin.math.sin
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.math.abs

private const val CALENDAR_PAGER_CENTER = 600
private const val CALENDAR_PAGER_COUNT = 1201

@Composable
fun CalendarScreen(
    state: AlbaToraDataState,
    selectedDate: LocalDate,
    onSelectedDateChange: (LocalDate) -> Unit,
    scrollBehavior: ScrollBehavior,
    topInset: Dp = 0.dp,
    onQuickAddShortTermForDate: (String) -> Unit,
    onEditRecord: (WorkRecord) -> Unit,
    onTogglePaymentStatus: (String) -> Unit,
    onExtendOneHour: (WorkRecord) -> Unit,
    onMarkEarlyLeave: (WorkRecord) -> Unit,
    onMarkAbsent: (WorkRecord) -> Unit,
    onAddSubstituteFromJob: (Job, LocalDate, Int, Int, Int, BreakPayType) -> Unit,
    onStampTemplateOnDate: (ShortTermTemplate, LocalDate) -> String = { _, _ -> "" },
    onUndoStampRecord: (String) -> Unit = {},
    onNotify: (String) -> Unit
) {
    val baseMonth = remember { YearMonth.now() }
    val pagerState = rememberPagerState(
        initialPage = CALENDAR_PAGER_CENTER,
        pageCount = { CALENDAR_PAGER_COUNT }
    )
    val coroutineScope = rememberCoroutineScope()
    val displayMonth = remember(pagerState.currentPage, baseMonth) {
        baseMonth.plusMonths((pagerState.currentPage - CALENDAR_PAGER_CENTER).toLong())
    }
    var activeStampTemplate by remember { mutableStateOf<ShortTermTemplate?>(null) }
    var lastStampedRecordId by remember { mutableStateOf<String?>(null) }
    var lastStampedDesc by remember { mutableStateOf("") }
    var showMonthPickerDialog by remember { mutableStateOf(false) }
    var showSubstituteDialog by remember { mutableStateOf(false) }
    var pendingEarlyLeaveRecord by remember { mutableStateOf<WorkRecord?>(null) }
    var pendingAbsentRecord by remember { mutableStateOf<WorkRecord?>(null) }

    val nowDt = remember { LocalDateTime.now() }
    val selectedItems = remember(state, selectedDate) {
        PayrollCalculator.resolveCalendarDayItems(state, selectedDate, nowDt)
    }
    val monthTotal = remember(state, displayMonth) {
        PayrollCalculator.calculateSettlementSummary(
            state,
            displayMonth.atDay(1),
            displayMonth.atEndOfMonth()
        ).totalNetPay
    }
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val listBottomPadding = 116.dp + navBottomInset

    val leftContent: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
        // Month header + Net Summary
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                            }
                        }
                    ) {
                        Icon(MiuixIcons.ChevronBackward, contentDescription = "이전 달")
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .squircleClip(10.dp)
                            .clickable { showMonthPickerDialog = true }
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        val todayDate = LocalDate.now()
                        val isAtToday = displayMonth == baseMonth && selectedDate == todayDate
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${displayMonth.year}년 ${displayMonth.monthValue}월 ▾",
                                style = MiuixTheme.textStyles.title4.copy(fontWeight = FontWeight.Bold)
                            )
                            if (!isAtToday) {
                                SquirclePillBadge(
                                    text = "오늘",
                                    backgroundColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.14f),
                                    contentColor = MiuixTheme.colorScheme.primary,
                                    onClick = {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(CALENDAR_PAGER_CENTER)
                                        }
                                        onSelectedDateChange(todayDate)
                                    }
                                )
                            }
                        }
                        Text(
                            text = "월 정산 예상 ${PayrollCalculator.formatWon(monthTotal)}",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(CALENDAR_PAGER_COUNT - 1))
                            }
                        }
                    ) {
                        Icon(MiuixIcons.ChevronForward, contentDescription = "다음 달")
                    }
                }
            }
        }

        // One-tap stamp mode for short-term presets
        if (state.templates.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (activeStampTemplate != null) {
                                "원탭 도장 활성화: 달력 날짜를 누르면 '${activeStampTemplate!!.name}'이 즉시 등록됩니다"
                            } else {
                                "단기 프리셋 원탭 도장 (선택 후 날짜를 탭하세요)"
                            },
                            style = MiuixTheme.textStyles.footnote1,
                            color = if (activeStampTemplate != null) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.weight(1f)
                        )
                        val undoId = lastStampedRecordId
                        if (undoId != null) {
                            SquirclePillBadge(
                                text = "실행 취소",
                                backgroundColor = MiuixTheme.colorScheme.error.copy(alpha = 0.14f),
                                contentColor = MiuixTheme.colorScheme.error,
                                onClick = {
                                    onUndoStampRecord(undoId)
                                    val desc = lastStampedDesc
                                    lastStampedRecordId = null
                                    lastStampedDesc = ""
                                    onNotify("${desc} 도장 등록을 취소했습니다.")
                                }
                            )
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.templates, key = { it.id }) { tpl ->
                            val picked = activeStampTemplate?.id == tpl.id
                            SquirclePillBadge(
                                text = if (picked) "${tpl.name} · 선택됨" else tpl.name,
                                backgroundColor = if (picked) Color(tpl.colorHex) else Color(tpl.colorHex).copy(alpha = 0.16f),
                                contentColor = if (picked) Color.White else Color(tpl.colorHex),
                                leadingIcon = AlbaIconCatalog.resolveIcon(tpl.emoji, MiuixIcons.Timer),
                                onClick = {
                                    activeStampTemplate = if (picked) null else tpl
                                }
                            )
                        }
                    }
                }
            }
        } else {
            item {
                Text(
                    text = "단기 프리셋 원탭 도장: 설정 > 단기 알바 프리셋 관리에서 자주 쓰는 단기 근무를 등록해 두면 달력 날짜를 탭해 1초 만에 찍을 수 있습니다.",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }

        // Swipeable Monthly Grid Pager (6 fixed rows so height never shifts; card-stack cover/uncover with spacing)
        item {
            val density = LocalDensity.current
            var pagerWidthPx by remember { mutableFloatStateOf(0f) }
            val pageSpacingDp = 16.dp
            val pageSpacingPx = with(density) { pageSpacingDp.toPx() }
            val isDarkSurface = MiuixTheme.colorScheme.onSurface.red > 0.5f

            HorizontalPager(
                state = pagerState,
                pageSpacing = pageSpacingDp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .onSizeChanged { pagerWidthPx = it.width.toFloat() },
                beyondViewportPageCount = 1
            ) { page ->
                val pageMonth = remember(page, baseMonth) {
                    baseMonth.plusMonths((page - CALENDAR_PAGER_CENTER).toLong())
                }
                val monthDates = remember(pageMonth) { buildMonthGridDates(pageMonth) }
                val dayItemsMap = remember(state, pageMonth) {
                    val map = mutableMapOf<LocalDate, List<CalendarDayScheduleItem>>()
                    val days = (1..pageMonth.lengthOfMonth()).map { pageMonth.atDay(it) }
                    days.forEach { d ->
                        map[d] = PayrollCalculator.resolveCalendarDayItems(state, d, nowDt)
                    }
                    map
                }

                val relPos = (page - pagerState.currentPage) - pagerState.currentPageOffsetFraction
                val stepPx = (pagerWidthPx + pageSpacingPx).coerceAtLeast(1f)
                val coverFraction = (-relPos).coerceIn(0f, 1f)
                val topElevation = if (relPos > 0.005f && relPos < 0.995f) {
                    (14f * sin(relPos * PI).toFloat()).dp
                } else {
                    0.dp
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .zIndex(page.toFloat())
                        .graphicsLayer {
                            if (relPos < 0f && pagerWidthPx > 0f) {
                                // Underlying month stays gently anchored in depth while next month covers or uncovers it
                                translationX = coverFraction * stepPx * 0.80f
                                val s = 1f - coverFraction * 0.06f
                                scaleX = s
                                scaleY = s
                                val baseAlpha = 1f - coverFraction * 0.72f
                                alpha = (if (coverFraction > 0.92f) {
                                    baseAlpha * ((1f - coverFraction) / 0.08f).coerceIn(0f, 1f)
                                } else {
                                    baseAlpha
                                }).coerceIn(0f, 1f)
                            }
                        }
                        .then(
                            if (topElevation > 0.2.dp) {
                                Modifier.shadow(
                                    elevation = topElevation,
                                    shape = RoundedCornerShape(20.dp),
                                    ambientColor = Color.Black.copy(alpha = if (isDarkSurface) 0.48f else 0.14f),
                                    spotColor = Color.Black.copy(alpha = if (isDarkSurface) 0.48f else 0.18f)
                                )
                            } else Modifier
                        ),
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

                                        val targetCellBg = when {
                                            isSelected -> MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
                                            isToday -> MiuixTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                            else -> Color.Transparent
                                        }
                                        val cellBg by animateColorAsState(
                                            targetValue = targetCellBg,
                                            animationSpec = tween(durationMillis = 180),
                                            label = "calendar_cell_bg"
                                        )

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
                                                    onSelectedDateChange(cellDate)
                                                    val stamp = activeStampTemplate
                                                    if (stamp != null) {
                                                        val newId = onStampTemplateOnDate(stamp, cellDate)
                                                        if (newId.isNotBlank()) {
                                                            lastStampedRecordId = newId
                                                            lastStampedDesc = "${cellDate.monthValue}/${cellDate.dayOfMonth} ${stamp.name}"
                                                        }
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
        }
    }

    val rightContent: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
        // Quick actions bar for the selected date
        item {
            val actionableItems = selectedItems.filter { it.record != null && it.status != ShiftStatus.ABSENT }
            var selectedTargetId by remember(selectedDate) { mutableStateOf<String?>(null) }
            val primaryItem = actionableItems.find { it.record?.id == selectedTargetId } ?: actionableItems.firstOrNull()
            SmallTitle(text = "${selectedDate.monthValue}월 ${selectedDate.dayOfMonth}일 (${dayOfWeekKorean(selectedDate.dayOfWeek.value)}) 원탭 퀵 액션")
            if (actionableItems.size > 1) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    items(actionableItems, key = { it.record?.id ?: it.title }) { candidate ->
                        val cid = candidate.record?.id
                        val isTarget = primaryItem?.record?.id == cid
                        SquirclePillBadge(
                            text = if (isTarget) "선택됨 · ${candidate.title}" else candidate.title,
                            backgroundColor = if (isTarget) Color(candidate.colorHex) else Color(candidate.colorHex).copy(alpha = 0.14f),
                            contentColor = if (isTarget) Color.White else Color(candidate.colorHex),
                            onClick = { selectedTargetId = cid }
                        )
                    }
                }
            }
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
                                pendingEarlyLeaveRecord = targetRec
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
                                pendingAbsentRecord = targetRec
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
            item(key = "calendar_empty") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .animateContentSize(),
                    insideMargin = PaddingValues(18.dp),
                    onClick = { onQuickAddShortTermForDate(PayrollCalculator.formatDate(selectedDate)) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
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
                        MiuixGlassIcon(
                            icon = MiuixIcons.AddCircle,
                            contentDescription = null,
                            accentColor = MiuixTheme.colorScheme.primary,
                            size = 38.dp,
                            iconSize = 19.dp
                        )
                    }
                }
            }
        } else {
            items(
                items = selectedItems,
                key = { item -> item.record?.id ?: "virtual_${item.jobId}_${item.title}_${item.startMinute}_${item.endMinute}_${item.isVirtualFuture}" }
            ) { item ->
                CalendarDayShiftCard(
                    item = item,
                    modifier = Modifier.animateItem(),
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
                    contentPadding = PaddingValues(top = topInset + 12.dp, bottom = listBottomPadding),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = leftContent
                )
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(top = topInset + 12.dp, bottom = listBottomPadding),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = rightContent
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(start = singleSidePad, end = singleSidePad, top = topInset + 12.dp, bottom = listBottomPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                leftContent()
                rightContent()
            }
        }
    }

    if (showMonthPickerDialog) {
        YearMonthJumpDialog(
            currentMonth = displayMonth,
            onDismiss = { showMonthPickerDialog = false },
            onSelectMonth = { pickedMonth ->
                val monthOffset = (pickedMonth.year - baseMonth.year) * 12 + (pickedMonth.monthValue - baseMonth.monthValue)
                val targetPage = (CALENDAR_PAGER_CENTER + monthOffset).coerceIn(0, CALENDAR_PAGER_COUNT - 1)
                coroutineScope.launch {
                    pagerState.animateScrollToPage(targetPage)
                }
                val clampedDay = selectedDate.dayOfMonth.coerceAtMost(pickedMonth.lengthOfMonth())
                onSelectedDateChange(pickedMonth.atDay(clampedDay))
                showMonthPickerDialog = false
            },
            onJumpToday = {
                val todayDate = LocalDate.now()
                coroutineScope.launch {
                    pagerState.animateScrollToPage(CALENDAR_PAGER_CENTER)
                }
                onSelectedDateChange(todayDate)
                showMonthPickerDialog = false
            }
        )
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

    val earlyRec = pendingEarlyLeaveRecord
    if (earlyRec != null) {
        ConfirmActionDialog(
            show = true,
            title = "조퇴 처리",
            summary = "'${earlyRec.title}' (${earlyRec.date}) 근무를 조퇴(-1시간) 처리할까요?",
            confirmText = "처리",
            onDismiss = { pendingEarlyLeaveRecord = null },
            onConfirm = {
                onMarkEarlyLeave(earlyRec)
                onNotify("${earlyRec.title} 조퇴 처리 완료")
                pendingEarlyLeaveRecord = null
            }
        )
    }

    val absentRec = pendingAbsentRecord
    if (absentRec != null) {
        ConfirmActionDialog(
            show = true,
            title = "결근 처리",
            summary = "'${absentRec.title}' (${absentRec.date}) 근무를 결근 처리할까요? 주휴수당 제외 규칙이 자동 연동됩니다.",
            confirmText = "결근 처리",
            isDestructive = true,
            onDismiss = { pendingAbsentRecord = null },
            onConfirm = {
                onMarkAbsent(absentRec)
                onNotify("${absentRec.title} 결근 처리 (주휴수당 제외 규칙 자동 연동)")
                pendingAbsentRecord = null
            }
        )
    }
}

@Composable
private fun CalendarDayShiftCard(
    item: CalendarDayScheduleItem,
    onClick: () -> Unit,
    onTogglePayment: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
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
                MiuixEntityBadge(
                    emoji = item.emoji,
                    fallbackIcon = if (item.isCurrentlyLive) MiuixIcons.Stopwatch else MiuixIcons.Notes,
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
                        text = pStatus.label,
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
                } else if (item.record != null && item.record.id.startsWith("cal_") && !item.isVirtualFuture && item.status != ShiftStatus.ABSENT) {
                    SquirclePillBadge(
                        text = "자동 스케줄",
                        backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                        contentColor = MiuixTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun YearMonthJumpDialog(
    currentMonth: YearMonth,
    onDismiss: () -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
    onJumpToday: () -> Unit
) {
    var year by remember(currentMonth) { mutableIntStateOf(currentMonth.year) }

    OverlayDialog(
        show = true,
        title = "연·월 빠른 이동",
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { year = (year - 1).coerceAtLeast(2000) }) {
                    Icon(MiuixIcons.ChevronBackward, contentDescription = "이전 연도")
                }
                Text(
                    text = "${year}년",
                    style = MiuixTheme.textStyles.title3.copy(fontWeight = FontWeight.Bold)
                )
                IconButton(onClick = { year = (year + 1).coerceAtMost(2100) }) {
                    Icon(MiuixIcons.ChevronForward, contentDescription = "다음 연도")
                }
            }

            for (row in 0 until 3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (col in 1..4) {
                        val m = row * 4 + col
                        val candidate = YearMonth.of(year, m)
                        val selected = candidate == currentMonth
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .squircleBackground(
                                    if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.secondaryContainer,
                                    10.dp
                                )
                                .squircleClip(10.dp)
                                .clickable { onSelectMonth(candidate) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${m}월",
                                style = MiuixTheme.textStyles.body2.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(text = "닫기", onClick = onDismiss, modifier = Modifier.weight(1f))
                TextButton(
                    text = "오늘로 이동",
                    onClick = onJumpToday,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
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
    // Always pad to 6 rows (42 cells) so 4/5/6-week months keep identical calendar height
    while (list.size < 42) {
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
