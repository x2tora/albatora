package com.albatora.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.albatora.app.domain.PayrollCalculator
import com.albatora.app.model.*
import com.albatora.app.ui.components.ConfirmActionDialog
import com.albatora.app.ui.components.EmojiAndColorIdentityRow
import com.albatora.app.ui.components.MiuixDatePickerDialog
import com.albatora.app.ui.components.MiuixBottomEdgeBlur
import com.albatora.app.ui.components.MiuixFloatingScrollTopBar
import com.albatora.app.ui.components.MiuixTopBarActionDivider
import com.albatora.app.ui.components.MiuixTimePickerDialog
import com.albatora.app.ui.components.SquirclePillBadge
import com.albatora.app.ui.components.dayOfWeekKorean
import com.albatora.app.ui.theme.AlbaMotion
import com.albatora.app.ui.theme.JobAccentPalette
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun AddEditRegularJobScreen(
    existingJob: Job?,
    defaultMinWage: Long,
    prefillHourlyWage: Long? = null,
    onBack: () -> Unit,
    onSave: (Job) -> Unit,
    onDelete: ((String) -> Unit)? = null,
    onNotify: (String) -> Unit = {}
) {
    val isEditing = existingJob != null
    val today = remember { LocalDate.now() }

    var name by remember { mutableStateOf(existingJob?.name ?: "") }
    var emoji by remember { mutableStateOf(existingJob?.emoji ?: "") }
    var colorHex by remember { mutableLongStateOf(existingJob?.colorHex ?: JobAccentPalette.first().second) }
    var payType by remember { mutableStateOf(existingJob?.payType ?: PayType.HOURLY) }
    var baseAmountText by remember { mutableStateOf((existingJob?.baseAmount ?: prefillHourlyWage ?: defaultMinWage).toString()) }
    var scheduleStartDate by remember {
        mutableStateOf(
            existingJob?.scheduleStartDate?.takeIf { it.isNotBlank() }
                ?: PayrollCalculator.formatDate(today)
        )
    }

    // Wage revisions
    var wageHistory by remember { mutableStateOf(existingJob?.wageHistory ?: emptyList()) }
    var showAddWageDialog by remember { mutableStateOf(false) }

    // Schedules
    var schedules by remember { mutableStateOf(existingJob?.schedules ?: emptyList()) }
    var editingSlotDay by remember { mutableStateOf<Int?>(null) }

    // Weekly holiday rules
    var whEnabled by remember { mutableStateOf(existingJob?.weeklyHoliday?.enabled ?: true) }
    var whExcludeAbsence by remember { mutableStateOf(existingJob?.weeklyHoliday?.excludeOnAbsenceOrEarlyLeave ?: true) }
    var whForceUnder15h by remember { mutableStateOf(existingJob?.weeklyHoliday?.forceIncludeUnder15h ?: false) }

    // Allowance boosts
    var nightEnabled by remember { mutableStateOf(existingJob?.allowance?.nightAllowanceEnabled ?: false) }
    var overtimeEnabled by remember { mutableStateOf(existingJob?.allowance?.overtimeAllowanceEnabled ?: false) }
    var holidayEnabled by remember { mutableStateOf(existingJob?.allowance?.holidayAllowanceEnabled ?: false) }
    var holidayIncludesWeekends by remember { mutableStateOf(existingJob?.allowance?.holidayIncludesWeekends ?: true) }

    // Tax and settlement
    var taxMode by remember { mutableStateOf(existingJob?.taxMode ?: TaxMode.NONE) }
    var customTaxText by remember { mutableStateOf((existingJob?.customTaxRatePercent ?: 3.3).toString()) }
    var dailyIncludeLocalTax by remember { mutableStateOf(existingJob?.dailyWorkerIncludeLocalTax ?: true) }

    var settlementMode by remember { mutableStateOf(existingJob?.settlementMode ?: SettlementPeriodMode.FIRST_TO_END) }
    var settlementCutoffText by remember { mutableStateOf((existingJob?.settlementCutoffDay ?: 25).toString()) }
    var settlementCustomStartText by remember { mutableStateOf((existingJob?.settlementCustomStartDay ?: 1).toString()) }
    var settlementCustomEndText by remember { mutableStateOf((existingJob?.settlementCustomEndDay ?: 31).toString()) }
    var settlementPrevMonthCycle by remember { mutableStateOf(existingJob?.settlementPrevMonthCycle ?: true) }
    var paydayDayText by remember { mutableStateOf((existingJob?.paydayDay ?: 10).toString()) }
    var paydayHolidayRule by remember { mutableStateOf(existingJob?.paydayHolidayRule ?: PaydayHolidayRule.PREV_BUSINESS_DAY) }
    var memo by remember { mutableStateOf(existingJob?.memo ?: "") }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var pendingEditJob by remember { mutableStateOf<Job?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val initialSignature = remember(existingJob?.id) {
        listOf(
            existingJob?.name ?: "",
            existingJob?.emoji ?: "",
            (existingJob?.colorHex ?: JobAccentPalette.first().second).toString(),
            (existingJob?.payType ?: PayType.HOURLY).name,
            (existingJob?.baseAmount ?: prefillHourlyWage ?: defaultMinWage).toString(),
            existingJob?.scheduleStartDate?.takeIf { it.isNotBlank() } ?: PayrollCalculator.formatDate(today),
            (existingJob?.wageHistory ?: emptyList()).toString(),
            (existingJob?.schedules ?: emptyList()).toString(),
            (existingJob?.weeklyHoliday?.enabled ?: true).toString(),
            (existingJob?.weeklyHoliday?.excludeOnAbsenceOrEarlyLeave ?: true).toString(),
            (existingJob?.weeklyHoliday?.forceIncludeUnder15h ?: false).toString(),
            (existingJob?.allowance?.nightAllowanceEnabled ?: false).toString(),
            (existingJob?.allowance?.overtimeAllowanceEnabled ?: false).toString(),
            (existingJob?.allowance?.holidayAllowanceEnabled ?: false).toString(),
            (existingJob?.allowance?.holidayIncludesWeekends ?: true).toString(),
            (existingJob?.taxMode ?: TaxMode.NONE).name,
            (existingJob?.customTaxRatePercent ?: 3.3).toString(),
            (existingJob?.dailyWorkerIncludeLocalTax ?: true).toString(),
            (existingJob?.settlementMode ?: SettlementPeriodMode.FIRST_TO_END).name,
            (existingJob?.settlementCutoffDay ?: 25).toString(),
            (existingJob?.settlementCustomStartDay ?: 1).toString(),
            (existingJob?.settlementCustomEndDay ?: 31).toString(),
            (existingJob?.settlementPrevMonthCycle ?: true).toString(),
            (existingJob?.paydayDay ?: 10).toString(),
            (existingJob?.paydayHolidayRule ?: PaydayHolidayRule.PREV_BUSINESS_DAY).name,
            existingJob?.memo ?: ""
        ).joinToString("|")
    }
    val currentSignature = listOf(
        name, emoji, colorHex.toString(), payType.name, baseAmountText, scheduleStartDate,
        wageHistory.toString(), schedules.toString(), whEnabled.toString(),
        whExcludeAbsence.toString(), whForceUnder15h.toString(), nightEnabled.toString(),
        overtimeEnabled.toString(), holidayEnabled.toString(), holidayIncludesWeekends.toString(),
        taxMode.name, customTaxText, dailyIncludeLocalTax.toString(), settlementMode.name,
        settlementCutoffText, settlementCustomStartText, settlementCustomEndText,
        settlementPrevMonthCycle.toString(), paydayDayText, paydayHolidayRule.name, memo
    ).joinToString("|")
    val isDirty = currentSignature != initialSignature

    BackHandler(enabled = isDirty) {
        showDiscardConfirm = true
    }

    val handleBackAttempt: () -> Unit = {
        if (isDirty) showDiscardConfirm = true else onBack()
    }

    fun handleSubmit() {
        val trimmed = name.trim()
        val baseAmt = baseAmountText.toLongOrNull() ?: 0L
        if (trimmed.isEmpty()) {
            val msg = "근무지 이름을 입력해 주세요."
            errorMsg = msg
            onNotify(msg)
            coroutineScope.launch { listState.animateScrollToItem(0) }
            return
        }
        if (baseAmt <= 0L) {
            val msg = "올바른 급여 금액을 입력해 주세요."
            errorMsg = msg
            onNotify(msg)
            coroutineScope.launch { listState.animateScrollToItem(0) }
            return
        }
        errorMsg = null
        val built = Job(
            id = existingJob?.id ?: UUID.randomUUID().toString(),
            name = trimmed,
            emoji = emoji,
            kind = JobKind.REGULAR,
            colorHex = colorHex,
            payType = payType,
            baseAmount = baseAmt,
            wageHistory = wageHistory.sortedBy { it.effectiveFromDate },
            scheduleStartDate = scheduleStartDate,
            schedules = schedules.sortedBy { it.dayOfWeek },
            taxMode = taxMode,
            customTaxRatePercent = customTaxText.toDoubleOrNull() ?: 0.0,
            dailyWorkerIncludeLocalTax = dailyIncludeLocalTax,
            allowance = AllowanceSettings(
                nightAllowanceEnabled = nightEnabled,
                overtimeAllowanceEnabled = overtimeEnabled,
                holidayAllowanceEnabled = holidayEnabled,
                holidayIncludesWeekends = holidayIncludesWeekends
            ),
            weeklyHoliday = WeeklyHolidayRule(
                enabled = whEnabled,
                excludeOnAbsenceOrEarlyLeave = whExcludeAbsence,
                forceIncludeUnder15h = whForceUnder15h
            ),
            settlementMode = settlementMode,
            settlementCutoffDay = (settlementCutoffText.toIntOrNull() ?: 25).coerceIn(1, 28),
            settlementCustomStartDay = (settlementCustomStartText.toIntOrNull() ?: 1).coerceIn(1, 31),
            settlementCustomEndDay = (settlementCustomEndText.toIntOrNull() ?: 31).coerceIn(1, 31),
            settlementPrevMonthCycle = settlementPrevMonthCycle,
            paydayDay = (paydayDayText.toIntOrNull() ?: 10).coerceIn(1, 31),
            paydayHolidayRule = paydayHolidayRule,
            memo = memo.trim(),
            isArchived = existingJob?.isArchived ?: false,
            createdAtMillis = existingJob?.createdAtMillis ?: System.currentTimeMillis()
        )
        if (isEditing) {
            pendingEditJob = built
        } else {
            onSave(built)
        }
    }

    val scrollBehavior = MiuixScrollBehavior(state = rememberTopAppBarState())
    val surfaceBg = MiuixTheme.colorScheme.surface
    val barBackdrop = rememberLayerBackdrop {
        drawRect(surfaceBg)
        drawContent()
    }

    Scaffold(
        topBar = {
            MiuixFloatingScrollTopBar(
                title = if (isEditing) "정규 알바 수정" else "정규/고정 알바 추가",
                scrollBehavior = scrollBehavior,
                backdrop = barBackdrop,
                maxContentWidth = 620.dp,
                navigationIcon = {
                    IconButton(
                        onClick = handleBackAttempt,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "뒤로가기"
                        )
                    }
                },
                actions = {
                    if (isEditing && onDelete != null) {
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Delete,
                                contentDescription = "삭제",
                                tint = MiuixTheme.colorScheme.error
                            )
                        }
                        MiuixTopBarActionDivider()
                    }
                    IconButton(
                        onClick = ::handleSubmit,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Ok,
                            contentDescription = "저장",
                            tint = MiuixTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val sidePad = ((maxWidth - 620.dp) / 2f).coerceAtLeast(16.dp)
            LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(barBackdrop)
                .background(surfaceBg)
                .imePadding()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = sidePad,
                end = sidePad,
                top = paddingValues.calculateTopPadding() + 12.dp,
                bottom = paddingValues.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "error_banner") {
                var stickyErrorMsg by remember { mutableStateOf("") }
                errorMsg?.let { stickyErrorMsg = it }
                AnimatedVisibility(
                    visible = errorMsg != null,
                    enter = AlbaMotion.expandEnter(),
                    exit = AlbaMotion.shrinkExit()
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.errorContainer
                        ),
                        insideMargin = PaddingValues(14.dp)
                    ) {
                        Text(
                            text = stickyErrorMsg,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            item {
                SmallTitle(text = "기본 정보 및 급여")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextField(
                            value = name,
                            onValueChange = {
                                name = it
                                errorMsg = null
                            },
                            label = "근무지 이름 (예: 스타벅스, CU 편의점)",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = "아이콘(이모지) 및 구분 색상",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        EmojiAndColorIdentityRow(
                            emoji = emoji,
                            fallbackIcon = MiuixIcons.Store,
                            colorHex = colorHex,
                            onEmojiChange = { emoji = it },
                            onColorChange = { colorHex = it }
                        )

                        val payTypes = PayType.entries
                        TabRowWithContour(
                            tabs = payTypes.map { it.label },
                            selectedTabIndex = payTypes.indexOf(payType),
                            onTabSelected = { payType = payTypes[it] }
                        )

                        TextField(
                            value = baseAmountText,
                            onValueChange = { baseAmountText = it.filter(Char::isDigit) },
                            label = when (payType) {
                                PayType.HOURLY -> "기본 시급 (2026 최저시급 ${PayrollCalculator.formatWon(defaultMinWage)})"
                                PayType.DAILY -> "기본 일급 (원)"
                                PayType.PER_TASK -> "기본 건당 금액 (원)"
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .squircleBackground(MiuixTheme.colorScheme.secondaryContainer, 12.dp)
                                .clickable { showStartDatePicker = true }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "정규 스케줄 적용 시작일",
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "이 날짜 이후의 스케줄부터 정산에 자동 반영됩니다",
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                            Text(
                                text = scheduleStartDate,
                                style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.SemiBold),
                                color = MiuixTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Wage history
            item {
                SmallTitle(text = "시급/급여 변경 이력 (과거 기록 보존)")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "적용 시작일을 지정해 시급 인상 이력을 추가하면, 인상 전 날짜의 급여는 기존 시급으로 정확히 보존됩니다.",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        wageHistory.sortedBy { it.effectiveFromDate }.forEachIndexed { idx, rev ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .squircleBackground(MiuixTheme.colorScheme.surfaceVariant, 10.dp)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${rev.effectiveFromDate} 부터 적용",
                                        style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = PayrollCalculator.formatWon(rev.hourlyOrBaseAmount),
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = MiuixTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        wageHistory = wageHistory.filterNot {
                                            it.effectiveFromDate == rev.effectiveFromDate &&
                                                it.hourlyOrBaseAmount == rev.hourlyOrBaseAmount
                                        }
                                    }
                                ) {
                                    Icon(MiuixIcons.Delete, contentDescription = "이력 삭제")
                                }
                            }
                        }
                        TextButton(
                            text = "+ 변경 이력 추가",
                            onClick = { showAddWageDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Weekly schedules and break rules
            item {
                SmallTitle(text = "정규 근무 요일 · 시간 및 휴게시간")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "요일을 누르면 해당 요일의 근무·휴게 시간을 설정할 수 있습니다. (휴게: 4시간당 30분 / 8시간당 1시간 자동 추천)",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            (1..7).forEach { dow ->
                                val hasSlot = schedules.any { it.dayOfWeek == dow }
                                val bg = if (hasSlot) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.secondaryContainer
                                val fg = if (hasSlot) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSecondaryContainer
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .squircleBackground(bg, 10.dp)
                                        .squircleClip(10.dp)
                                        .clickable { editingSlotDay = dow },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dayOfWeekKorean(dow),
                                        style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Bold),
                                        color = fg
                                    )
                                }
                            }
                        }

                        if (schedules.isEmpty()) {
                            Text(
                                text = "아직 등록된 요일 스케줄이 없습니다. 위 요일을 탭해 추가하거나 즉시 출근(스톱워치)만으로도 사용할 수 있습니다.",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        } else {
                            schedules.sortedBy { it.dayOfWeek }.forEach { slot ->
                                val rawDur = PayrollCalculator.rawDurationMinutes(slot.startMinute, slot.endMinute)
                                val breakMins = if (slot.autoRecommendBreak) {
                                    PayrollCalculator.recommendedBreakMinutes(rawDur)
                                } else {
                                    slot.breakMinutes
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .squircleBackground(MiuixTheme.colorScheme.surfaceVariant, 12.dp)
                                        .clickable { editingSlotDay = slot.dayOfWeek }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${dayOfWeekKorean(slot.dayOfWeek)}요일 · ${PayrollCalculator.formatMinutes(slot.startMinute)} ~ ${PayrollCalculator.formatMinutes(slot.endMinute)}",
                                            style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        val fixedNote = if (slot.fixedBreakStartMinute != null && slot.fixedBreakEndMinute != null) {
                                            " · 고정휴게 ${PayrollCalculator.formatMinutes(slot.fixedBreakStartMinute)}~${PayrollCalculator.formatMinutes(slot.fixedBreakEndMinute)}"
                                        } else ""
                                        Text(
                                            text = "휴게 ${breakMins}분 (${slot.breakPayType.label})$fixedNote",
                                            style = MiuixTheme.textStyles.footnote2,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                        )
                                    }
                                    IconButton(
                                        onClick = { schedules = schedules.filterNot { it.dayOfWeek == slot.dayOfWeek } }
                                    ) {
                                        Icon(MiuixIcons.Delete, contentDescription = "요일 삭제")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Weekly holiday and allowance boosts
            item {
                SmallTitle(text = "주휴수당 및 1.5배 가산수당")
                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = "주휴수당 자동 계산 (주 15시간 이상, 40시간 캡)",
                        summary = "1주 소정근로 15시간 이상 시 최대 40시간 기준으로 주휴수당 자동 가산",
                        checked = whEnabled,
                        onCheckedChange = { whEnabled = it }
                    )
                    AnimatedVisibility(
                        visible = whEnabled,
                        enter = AlbaMotion.expandEnter(),
                        exit = AlbaMotion.shrinkExit()
                    ) {
                        Column {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SwitchPreference(
                                title = "결근·조퇴 시 해당 주 주휴수당 자동 제외",
                                summary = "지정 근무일에 결근 또는 조퇴가 발생하면 해당 주 주휴수당을 제외합니다",
                                checked = whExcludeAbsence,
                                onCheckedChange = { whExcludeAbsence = it }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SwitchPreference(
                                title = "주 15시간 미만도 주휴수당 강제 포함",
                                summary = "매장별 별도 약정 시 주 15시간 미만 주에도 비례 주휴수당 포함",
                                checked = whForceUnder15h,
                                onCheckedChange = { whForceUnder15h = it }
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SwitchPreference(
                        title = "야간수당 1.5배 (22:00 ~ 06:00)",
                        summary = "켜두면 야간 시간대 진입 시 실시간 부스트 배지 및 속도 가속",
                        checked = nightEnabled,
                        onCheckedChange = { nightEnabled = it }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SwitchPreference(
                        title = "연장수당 1.5배 (일 8시간 초과·연장근무)",
                        summary = "일 8시간 초과 또는 +1시간 연장 시 1.5배 가산 적용",
                        checked = overtimeEnabled,
                        onCheckedChange = { overtimeEnabled = it }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SwitchPreference(
                        title = "휴일수당 1.5배 (공휴일·휴일)",
                        summary = "법정 공휴일/지정 휴일 근무 시 1.5배 가산",
                        checked = holidayEnabled,
                        onCheckedChange = { holidayEnabled = it }
                    )
                    AnimatedVisibility(
                        visible = holidayEnabled,
                        enter = AlbaMotion.expandEnter(),
                        exit = AlbaMotion.shrinkExit()
                    ) {
                        Column {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SwitchPreference(
                                title = "토요일·일요일 주말도 휴일수당 적용",
                                summary = "공휴일 외 주말 근무에도 휴일수당 부스트를 적용하려면 켜세요",
                                checked = holidayIncludesWeekends,
                                onCheckedChange = { holidayIncludesWeekends = it }
                            )
                        }
                    }
                }
            }

            // Tax, settlement, and payday
            item {
                SmallTitle(text = "세금 공제 · 정산 기간 · 월급날 보정")
                Card(modifier = Modifier.fillMaxWidth()) {
                    val taxOptions = TaxMode.entries
                    OverlayDropdownPreference(
                        title = "세금/보험 공제 방식",
                        summary = taxMode.label,
                        items = taxOptions.map { it.label },
                        selectedIndex = taxOptions.indexOf(taxMode),
                        onSelectedIndexChange = { taxMode = taxOptions[it] }
                    )
                    AnimatedVisibility(
                        visible = taxMode == TaxMode.CUSTOM,
                        enter = AlbaMotion.expandEnter(),
                        exit = AlbaMotion.shrinkExit()
                    ) {
                        TextField(
                            value = customTaxText,
                            onValueChange = { customTaxText = it },
                            label = "직접 입력 공제율 (%)",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    AnimatedVisibility(
                        visible = taxMode == TaxMode.DAILY_WORKER,
                        enter = AlbaMotion.expandEnter(),
                        exit = AlbaMotion.shrinkExit()
                    ) {
                        Column {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SwitchPreference(
                                title = "지방소득세 포함 (2.97%)",
                                summary = "끄면 소득세만 2.7% 적용 (일 15만 원 비과세 공제)",
                                checked = dailyIncludeLocalTax,
                                onCheckedChange = { dailyIncludeLocalTax = it }
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    val periodOptions = SettlementPeriodMode.entries
                    OverlayDropdownPreference(
                        title = "급여 정산 산정 기간",
                        summary = settlementMode.label,
                        items = periodOptions.map { it.label },
                        selectedIndex = periodOptions.indexOf(settlementMode),
                        onSelectedIndexChange = { settlementMode = periodOptions[it] }
                    )
                    AnimatedVisibility(
                        visible = settlementMode == SettlementPeriodMode.PREV_N_PLUS_1_TO_N,
                        enter = AlbaMotion.expandEnter(),
                        exit = AlbaMotion.shrinkExit()
                    ) {
                        TextField(
                            value = settlementCutoffText,
                            onValueChange = { settlementCutoffText = it.filter(Char::isDigit) },
                            label = "정산 마감 기준일 N (예: 25 입력 시 전월 26일 ~ 당월 25일)",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    AnimatedVisibility(
                        visible = settlementMode == SettlementPeriodMode.CUSTOM_RANGE,
                        enter = AlbaMotion.expandEnter(),
                        exit = AlbaMotion.shrinkExit()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TextField(
                                value = settlementCustomStartText,
                                onValueChange = { settlementCustomStartText = it.filter(Char::isDigit) },
                                label = "시작일(1~31)",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            TextField(
                                value = settlementCustomEndText,
                                onValueChange = { settlementCustomEndText = it.filter(Char::isDigit) },
                                label = "종료일(1~31)",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SwitchPreference(
                        title = "전월 근무 급여를 이번 달 월급날 지급",
                        summary = if (settlementPrevMonthCycle) "예: 지난달 1일~말일 급여 -> 이번 달 월급날 지급" else "당월 산정 기간 급여를 이번 달 말에 지급",
                        checked = settlementPrevMonthCycle,
                        onCheckedChange = { settlementPrevMonthCycle = it }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    TextField(
                        value = paydayDayText,
                        onValueChange = { paydayDayText = it.filter(Char::isDigit) },
                        label = "월급날 (1~31, 31 입력 시 매월 말일)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    val paydayRules = PaydayHolidayRule.entries
                    OverlayDropdownPreference(
                        title = "월급날이 주말·공휴일일 때",
                        summary = paydayHolidayRule.label,
                        items = paydayRules.map { it.label },
                        selectedIndex = paydayRules.indexOf(paydayHolidayRule),
                        onSelectedIndexChange = { paydayHolidayRule = paydayRules[it] }
                    )
                }
            }

            item {
                SmallTitle(text = "메모")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(16.dp)
                ) {
                    TextField(
                        value = memo,
                        onValueChange = { memo = it },
                        label = "매장 연락처, 참고사항 등",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    text = if (isEditing) "정규 알바 수정 완료" else "정규 알바 등록 완료",
                    onClick = ::handleSubmit,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Start Date Picker Dialog
        MiuixDatePickerDialog(
            show = showStartDatePicker,
            title = "스케줄 적용 시작일 선택",
            initialDate = PayrollCalculator.parseDate(scheduleStartDate, today),
            onDismiss = { showStartDatePicker = false },
            onConfirm = { picked ->
                scheduleStartDate = PayrollCalculator.formatDate(picked)
                showStartDatePicker = false
            }
        )

        // Add Wage History Dialog
        if (showAddWageDialog) {
            AddWageRevisionDialog(
                defaultAmount = baseAmountText.toLongOrNull() ?: defaultMinWage,
                onDismiss = { showAddWageDialog = false },
                onAdd = { rev ->
                    wageHistory = wageHistory.filterNot { it.effectiveFromDate == rev.effectiveFromDate } + rev
                    showAddWageDialog = false
                }
            )
        }

        // Edit Day Schedule Slot Dialog
        val slotDay = editingSlotDay
        if (slotDay != null) {
            val currentSlot = schedules.find { it.dayOfWeek == slotDay } ?: ScheduleSlot(
                dayOfWeek = slotDay,
                startMinute = 9 * 60,
                endMinute = 18 * 60,
                autoRecommendBreak = true,
                breakMinutes = 60
            )
            EditScheduleSlotDialog(
                slot = currentSlot,
                onDismiss = { editingSlotDay = null },
                onSaveSlot = { updated ->
                    schedules = schedules.filterNot { it.dayOfWeek == updated.dayOfWeek } + updated
                    editingSlotDay = null
                },
                onRemoveSlot = {
                    schedules = schedules.filterNot { it.dayOfWeek == slotDay }
                    editingSlotDay = null
                }
            )
        }

        if (showDeleteConfirm && existingJob != null && onDelete != null) {
            OverlayDialog(
                show = true,
                title = "알바 삭제",
                summary = "'${existingJob.name}' 근무지를 삭제할까요?",
                onDismissRequest = { showDeleteConfirm = false }
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(
                        text = "취소",
                        onClick = { showDeleteConfirm = false },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "삭제",
                        onClick = {
                            showDeleteConfirm = false
                            onDelete(existingJob.id)
                        },
                        colors = ButtonDefaults.textButtonColors(
                            color = MiuixTheme.colorScheme.error,
                            textColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        val editCandidate = pendingEditJob
        if (editCandidate != null) {
            ConfirmActionDialog(
                show = true,
                title = "알바 정보 수정",
                summary = "'${editCandidate.name}' 근무지 변경 사항을 저장할까요?",
                confirmText = "저장",
                onDismiss = { pendingEditJob = null },
                onConfirm = {
                    pendingEditJob = null
                    onSave(editCandidate)
                }
            )
        }

        if (showDiscardConfirm) {
            ConfirmActionDialog(
                show = true,
                title = "변경 사항이 저장되지 않았습니다",
                summary = "입력하거나 수정한 내용이 사라집니다. 저장하지 않고 나가시겠습니까?",
                confirmText = "나가기",
                isDestructive = true,
                onDismiss = { showDiscardConfirm = false },
                onConfirm = {
                    showDiscardConfirm = false
                    onBack()
                }
            )
        }

            MiuixBottomEdgeBlur(
                backdrop = barBackdrop,
                extraHeight = 36.dp,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun AddWageRevisionDialog(
    defaultAmount: Long,
    onDismiss: () -> Unit,
    onAdd: (WageRevision) -> Unit
) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var amountText by remember { mutableStateOf(defaultAmount.toString()) }
    var showDatePicker by remember { mutableStateOf(false) }

    OverlayDialog(
        show = true,
        title = "급여 변경 이력 추가",
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .squircleBackground(MiuixTheme.colorScheme.secondaryContainer, 10.dp)
                    .clickable { showDatePicker = true }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("적용 시작일", style = MiuixTheme.textStyles.body2)
                Text(
                    PayrollCalculator.formatDate(date),
                    style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Bold),
                    color = MiuixTheme.colorScheme.primary
                )
            }
            TextField(
                value = amountText,
                onValueChange = { amountText = it.filter(Char::isDigit) },
                label = "적용 급여 (원)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(text = "취소", onClick = onDismiss, modifier = Modifier.weight(1f))
                TextButton(
                    text = "추가",
                    onClick = {
                        val amt = amountText.toLongOrNull() ?: 0L
                        if (amt > 0L) {
                            onAdd(WageRevision(PayrollCalculator.formatDate(date), amt))
                        }
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        MiuixDatePickerDialog(
            show = showDatePicker,
            title = "시급 적용 시작일",
            initialDate = date,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                date = it
                showDatePicker = false
            }
        )
    }
}

@Composable
private fun EditScheduleSlotDialog(
    slot: ScheduleSlot,
    onDismiss: () -> Unit,
    onSaveSlot: (ScheduleSlot) -> Unit,
    onRemoveSlot: () -> Unit
) {
    var startMin by remember { mutableIntStateOf(slot.startMinute) }
    var endMin by remember { mutableIntStateOf(slot.endMinute) }
    var autoRec by remember { mutableStateOf(slot.autoRecommendBreak) }
    var manualBreakText by remember { mutableStateOf(slot.breakMinutes.toString()) }
    var breakPayType by remember { mutableStateOf(slot.breakPayType) }
    var useFixedBreak by remember { mutableStateOf(slot.fixedBreakStartMinute != null && slot.fixedBreakEndMinute != null) }
    var fixedStart by remember { mutableIntStateOf(slot.fixedBreakStartMinute ?: 12 * 60) }
    var fixedEnd by remember { mutableIntStateOf(slot.fixedBreakEndMinute ?: 13 * 60) }

    var pickTimeTarget by remember { mutableStateOf<String?>(null) }

    val rawDur = PayrollCalculator.rawDurationMinutes(startMin, endMin)
    val recBreak = PayrollCalculator.recommendedBreakMinutes(rawDur)

    OverlayDialog(
        show = true,
        title = "${dayOfWeekKorean(slot.dayOfWeek)}요일 스케줄",
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .squircleBackground(MiuixTheme.colorScheme.secondaryContainer, 10.dp)
                        .clickable { pickTimeTarget = "start" }
                        .padding(10.dp)
                ) {
                    Column {
                        Text("출근", style = MiuixTheme.textStyles.footnote2)
                        Text(
                            PayrollCalculator.formatMinutes(startMin),
                            style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .squircleBackground(MiuixTheme.colorScheme.secondaryContainer, 10.dp)
                        .clickable { pickTimeTarget = "end" }
                        .padding(10.dp)
                ) {
                    Column {
                        Text("퇴근", style = MiuixTheme.textStyles.footnote2)
                        Text(
                            PayrollCalculator.formatMinutes(endMin),
                            style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("휴게 자동 추천 (추천 ${recBreak}분)", style = MiuixTheme.textStyles.body2)
                Switch(checked = autoRec, onCheckedChange = { autoRec = it })
            }

            if (!autoRec) {
                TextField(
                    value = manualBreakText,
                    onValueChange = { manualBreakText = it.filter(Char::isDigit) },
                    label = "휴게 시간 직접 입력 (분)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            TabRowWithContour(
                tabs = BreakPayType.entries.map { it.label },
                selectedTabIndex = BreakPayType.entries.indexOf(breakPayType),
                onTabSelected = { breakPayType = BreakPayType.entries[it] }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("고정 휴게 시각 지정 (자동 일시정지)", style = MiuixTheme.textStyles.footnote1)
                Switch(checked = useFixedBreak, onCheckedChange = { useFixedBreak = it })
            }

            if (useFixedBreak) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .squircleBackground(MiuixTheme.colorScheme.surfaceVariant, 8.dp)
                            .clickable { pickTimeTarget = "bStart" }
                            .padding(8.dp)
                    ) {
                        Text("휴게 시작 ${PayrollCalculator.formatMinutes(fixedStart)}", style = MiuixTheme.textStyles.footnote1)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .squircleBackground(MiuixTheme.colorScheme.surfaceVariant, 8.dp)
                            .clickable { pickTimeTarget = "bEnd" }
                            .padding(8.dp)
                    ) {
                        Text("휴게 종료 ${PayrollCalculator.formatMinutes(fixedEnd)}", style = MiuixTheme.textStyles.footnote1)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = "삭제",
                    onClick = onRemoveSlot,
                    modifier = Modifier.weight(0.8f)
                )
                TextButton(
                    text = "저장",
                    onClick = {
                        val finalBreak = if (autoRec) recBreak else (manualBreakText.toIntOrNull() ?: 0)
                        onSaveSlot(
                            ScheduleSlot(
                                dayOfWeek = slot.dayOfWeek,
                                startMinute = startMin,
                                endMinute = endMin,
                                autoRecommendBreak = autoRec,
                                breakMinutes = finalBreak,
                                breakPayType = breakPayType,
                                fixedBreakStartMinute = if (useFixedBreak) fixedStart else null,
                                fixedBreakEndMinute = if (useFixedBreak) fixedEnd else null
                            )
                        )
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1.2f)
                )
            }
        }

        if (pickTimeTarget != null) {
            val init = when (pickTimeTarget) {
                "start" -> startMin
                "end" -> endMin
                "bStart" -> fixedStart
                else -> fixedEnd
            }
            MiuixTimePickerDialog(
                show = true,
                title = "시각 선택",
                initialMinuteOfDay = init,
                onDismiss = { pickTimeTarget = null },
                onConfirm = { chosen ->
                    when (pickTimeTarget) {
                        "start" -> startMin = chosen
                        "end" -> endMin = chosen
                        "bStart" -> fixedStart = chosen
                        "bEnd" -> fixedEnd = chosen
                    }
                    pickTimeTarget = null
                }
            )
        }
    }
}
