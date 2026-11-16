package com.albatora.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.albatora.app.ui.theme.AlbaIconCatalog
import com.albatora.app.ui.theme.AlbaMotion
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.AddCircle
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun QuickAddShortTermScreen(
    existingRecord: WorkRecord?,
    initialDateIso: String?,
    templates: List<ShortTermTemplate>,
    defaultMinWage: Long,
    onBack: () -> Unit,
    onSave: (WorkRecord, ShortTermTemplate?) -> Unit,
    onDelete: ((String) -> Unit)? = null,
    onNotify: (String) -> Unit = {}
) {
    val isEditing = existingRecord != null
    val fallbackDate = remember { LocalDate.now() }

    var title by remember { mutableStateOf(existingRecord?.title ?: "") }
    var emoji by remember { mutableStateOf(existingRecord?.emoji ?: "") }
    var colorHex by remember { mutableLongStateOf(existingRecord?.colorHex ?: 0xFF10B981) }
    var dateIso by remember {
        mutableStateOf(
            existingRecord?.date ?: initialDateIso ?: PayrollCalculator.formatDate(fallbackDate)
        )
    }
    var startMin by remember { mutableIntStateOf(existingRecord?.startMinute ?: 9 * 60) }
    var endMin by remember { mutableIntStateOf(existingRecord?.endMinute ?: 18 * 60) }
    var payType by remember { mutableStateOf(existingRecord?.payType ?: PayType.DAILY) }
    var unitAmountText by remember {
        mutableStateOf(
            existingRecord?.unitAmount?.toString()
                ?: if (payType == PayType.HOURLY) defaultMinWage.toString() else "140000"
        )
    }

    val rawDur = PayrollCalculator.rawDurationMinutes(startMin, endMin)
    var autoRecBreak by remember { mutableStateOf(existingRecord == null) }
    var breakMinText by remember {
        mutableStateOf(
            (existingRecord?.breakMinutes ?: PayrollCalculator.recommendedBreakMinutes(rawDur)).toString()
        )
    }
    var breakPayType by remember { mutableStateOf(existingRecord?.breakPayType ?: BreakPayType.UNPAID) }

    // Expenses and agency fee
    var transportText by remember { mutableStateOf((existingRecord?.transportExpense ?: 0L).toString()) }
    var mealText by remember { mutableStateOf((existingRecord?.mealExpense ?: 0L).toString()) }
    var siteBonusText by remember { mutableStateOf((existingRecord?.siteBonus ?: 0L).toString()) }
    var agencyFeeText by remember { mutableStateOf(if ((existingRecord?.agencyFeeValue ?: 0.0) > 0.0) existingRecord!!.agencyFeeValue.toString() else "") }
    var agencyFeeType by remember { mutableStateOf(existingRecord?.agencyFeeType ?: FeeType.PERCENT) }

    // Daily-worker tax
    var taxMode by remember { mutableStateOf(existingRecord?.taxMode ?: TaxMode.DAILY_WORKER) }
    var customTaxText by remember { mutableStateOf((existingRecord?.customTaxRatePercent ?: 3.3).toString()) }
    var includeLocalTax by remember { mutableStateOf(existingRecord?.dailyWorkerIncludeLocalTax ?: true) }

    // Payment status
    var paymentStatus by remember { mutableStateOf(existingRecord?.paymentStatus ?: PaymentStatus.UNPAID) }

    // Optional allowances
    var nightEnabled by remember { mutableStateOf(existingRecord?.allowance?.nightAllowanceEnabled ?: false) }
    var overtimeEnabled by remember { mutableStateOf(existingRecord?.allowance?.overtimeAllowanceEnabled ?: false) }
    var holidayEnabled by remember { mutableStateOf(existingRecord?.allowance?.holidayAllowanceEnabled ?: false) }

    var saveAsTemplate by remember { mutableStateOf(false) }
    var memo by remember { mutableStateOf(existingRecord?.memo ?: "") }

    var showDatePicker by remember { mutableStateOf(false) }
    var pickTimeMode by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var pendingEditSave by remember { mutableStateOf<Pair<WorkRecord, ShortTermTemplate?>?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val initialSignature = remember(existingRecord?.id, initialDateIso) {
        val initDate = existingRecord?.date ?: initialDateIso ?: PayrollCalculator.formatDate(fallbackDate)
        val initPayType = existingRecord?.payType ?: PayType.DAILY
        val initUnitAmt = existingRecord?.unitAmount?.toString()
            ?: if (initPayType == PayType.HOURLY) defaultMinWage.toString() else "140000"
        val initRawDur = PayrollCalculator.rawDurationMinutes(
            existingRecord?.startMinute ?: 9 * 60,
            existingRecord?.endMinute ?: 18 * 60
        )
        listOf(
            existingRecord?.title ?: "",
            existingRecord?.emoji ?: "",
            (existingRecord?.colorHex ?: 0xFF10B981).toString(),
            initDate,
            (existingRecord?.startMinute ?: 9 * 60).toString(),
            (existingRecord?.endMinute ?: 18 * 60).toString(),
            initPayType.name,
            initUnitAmt,
            (existingRecord == null).toString(),
            (existingRecord?.breakMinutes ?: PayrollCalculator.recommendedBreakMinutes(initRawDur)).toString(),
            (existingRecord?.breakPayType ?: BreakPayType.UNPAID).name,
            (existingRecord?.transportExpense ?: 0L).toString(),
            (existingRecord?.mealExpense ?: 0L).toString(),
            (existingRecord?.siteBonus ?: 0L).toString(),
            if ((existingRecord?.agencyFeeValue ?: 0.0) > 0.0) existingRecord!!.agencyFeeValue.toString() else "",
            (existingRecord?.agencyFeeType ?: FeeType.PERCENT).name,
            (existingRecord?.taxMode ?: TaxMode.DAILY_WORKER).name,
            (existingRecord?.customTaxRatePercent ?: 3.3).toString(),
            (existingRecord?.dailyWorkerIncludeLocalTax ?: true).toString(),
            (existingRecord?.paymentStatus ?: PaymentStatus.UNPAID).name,
            (existingRecord?.allowance?.nightAllowanceEnabled ?: false).toString(),
            (existingRecord?.allowance?.overtimeAllowanceEnabled ?: false).toString(),
            (existingRecord?.allowance?.holidayAllowanceEnabled ?: false).toString(),
            "false",
            existingRecord?.memo ?: ""
        ).joinToString("|")
    }
    val currentSignature = listOf(
        title, emoji, colorHex.toString(), dateIso, startMin.toString(), endMin.toString(),
        payType.name, unitAmountText, autoRecBreak.toString(), breakMinText, breakPayType.name,
        transportText, mealText, siteBonusText, agencyFeeText, agencyFeeType.name,
        taxMode.name, customTaxText, includeLocalTax.toString(), paymentStatus.name,
        nightEnabled.toString(), overtimeEnabled.toString(), holidayEnabled.toString(),
        saveAsTemplate.toString(), memo
    ).joinToString("|")
    val isDirty = currentSignature != initialSignature

    BackHandler(enabled = isDirty) {
        showDiscardConfirm = true
    }

    val handleBackAttempt: () -> Unit = {
        if (isDirty) showDiscardConfirm = true else onBack()
    }

    val effectiveBreakMins = if (autoRecBreak) PayrollCalculator.recommendedBreakMinutes(rawDur) else (breakMinText.toIntOrNull() ?: 0)

    val previewRecord = WorkRecord(
        id = "preview",
        title = title.ifBlank { "단기 근무" },
        emoji = emoji,
        kind = JobKind.SHORT_TERM,
        colorHex = colorHex,
        date = dateIso,
        startMinute = startMin,
        endMinute = endMin,
        payType = payType,
        unitAmount = unitAmountText.toLongOrNull() ?: 0L,
        breakMinutes = effectiveBreakMins,
        breakPayType = breakPayType,
        transportExpense = transportText.toLongOrNull() ?: 0L,
        mealExpense = mealText.toLongOrNull() ?: 0L,
        siteBonus = siteBonusText.toLongOrNull() ?: 0L,
        agencyFeeValue = agencyFeeText.toDoubleOrNull() ?: 0.0,
        agencyFeeType = agencyFeeType,
        taxMode = taxMode,
        customTaxRatePercent = customTaxText.toDoubleOrNull() ?: 0.0,
        dailyWorkerIncludeLocalTax = includeLocalTax,
        allowance = AllowanceSettings(
            nightAllowanceEnabled = nightEnabled,
            overtimeAllowanceEnabled = overtimeEnabled,
            holidayAllowanceEnabled = holidayEnabled
        ),
        paymentStatus = paymentStatus
    )
    val preview = PayrollCalculator.calculateShiftFinancials(previewRecord)
    val scrollBehavior = MiuixScrollBehavior(state = rememberTopAppBarState())
    val surfaceBg = MiuixTheme.colorScheme.surface
    val barBackdrop = rememberLayerBackdrop {
        drawRect(surfaceBg)
        drawContent()
    }

    fun handleSubmit() {
        val cleanTitle = title.trim()
        val unitAmt = unitAmountText.toLongOrNull() ?: 0L
        if (cleanTitle.isEmpty()) {
            val msg = "일자리 이름(예: 물류센터, 행사 스태프)을 입력해 주세요."
            errorMsg = msg
            onNotify(msg)
            coroutineScope.launch { listState.animateScrollToItem(0) }
            return
        }
        if (unitAmt <= 0L) {
            val msg = "급여 금액을 입력해 주세요."
            errorMsg = msg
            onNotify(msg)
            coroutineScope.launch { listState.animateScrollToItem(0) }
            return
        }
        errorMsg = null
        val finalRecord = WorkRecord(
            id = existingRecord?.id ?: UUID.randomUUID().toString(),
            jobId = existingRecord?.jobId,
            title = cleanTitle,
            emoji = emoji,
            kind = existingRecord?.kind ?: JobKind.SHORT_TERM,
            colorHex = colorHex,
            date = dateIso,
            startMinute = startMin,
            endMinute = endMin,
            status = existingRecord?.status ?: ShiftStatus.NORMAL,
            payType = payType,
            unitAmount = unitAmt,
            breakMinutes = effectiveBreakMins,
            breakPayType = breakPayType,
            transportExpense = transportText.toLongOrNull() ?: 0L,
            mealExpense = mealText.toLongOrNull() ?: 0L,
            siteBonus = siteBonusText.toLongOrNull() ?: 0L,
            agencyFeeValue = agencyFeeText.toDoubleOrNull() ?: 0.0,
            agencyFeeType = agencyFeeType,
            taxMode = taxMode,
            customTaxRatePercent = customTaxText.toDoubleOrNull() ?: 0.0,
            dailyWorkerIncludeLocalTax = includeLocalTax,
            allowance = AllowanceSettings(
                nightAllowanceEnabled = nightEnabled,
                overtimeAllowanceEnabled = overtimeEnabled,
                holidayAllowanceEnabled = holidayEnabled
            ),
            paymentStatus = paymentStatus,
            paidAtDate = if (paymentStatus == PaymentStatus.PAID) dateIso else null,
            memo = memo.trim(),
            createdAtMillis = existingRecord?.createdAtMillis ?: System.currentTimeMillis()
        )
        val templateToSave = if (saveAsTemplate) {
            ShortTermTemplate(
                id = UUID.randomUUID().toString(),
                name = cleanTitle,
                emoji = emoji,
                colorHex = colorHex,
                startMinute = startMin,
                endMinute = endMin,
                payType = payType,
                unitAmount = unitAmt,
                breakMinutes = effectiveBreakMins,
                breakPayType = breakPayType,
                transportExpense = transportText.toLongOrNull() ?: 0L,
                mealExpense = mealText.toLongOrNull() ?: 0L,
                siteBonus = siteBonusText.toLongOrNull() ?: 0L,
                agencyFeeValue = agencyFeeText.toDoubleOrNull() ?: 0.0,
                agencyFeeType = agencyFeeType,
                taxMode = taxMode,
                customTaxRatePercent = customTaxText.toDoubleOrNull() ?: 0.0,
                dailyWorkerIncludeLocalTax = includeLocalTax,
                allowance = AllowanceSettings(
                    nightAllowanceEnabled = nightEnabled,
                    overtimeAllowanceEnabled = overtimeEnabled,
                    holidayAllowanceEnabled = holidayEnabled
                ),
                memo = memo.trim()
            )
        } else null
        if (isEditing) {
            pendingEditSave = finalRecord to templateToSave
        } else {
            onSave(finalRecord, templateToSave)
        }
    }

    Scaffold(
        topBar = {
            MiuixFloatingScrollTopBar(
                title = if (isEditing) "근무 내역 수정" else "단기 근무 등록",
                scrollBehavior = scrollBehavior,
                backdrop = barBackdrop,
                maxContentWidth = 620.dp,
                navigationIcon = {
                    IconButton(
                        onClick = handleBackAttempt,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(MiuixIcons.Back, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    if (isEditing && onDelete != null) {
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                MiuixIcons.Delete,
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
                            MiuixIcons.Ok,
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
                        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.errorContainer),
                        insideMargin = PaddingValues(14.dp)
                    ) {
                        Text(stickyErrorMsg, color = MiuixTheme.colorScheme.onErrorContainer, style = MiuixTheme.textStyles.body2)
                    }
                }
            }

            // Saved templates bar
            if (templates.isNotEmpty() && !isEditing) {
                item {
                    SmallTitle(text = "단기 프리셋")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(templates, key = { it.id }) { tpl ->
                            SquirclePillBadge(
                                text = "${tpl.name} · ${PayrollCalculator.formatWon(tpl.unitAmount)}",
                                backgroundColor = Color(tpl.colorHex).copy(alpha = 0.16f),
                                contentColor = Color(tpl.colorHex),
                                leadingIcon = AlbaIconCatalog.resolveIcon(tpl.emoji, MiuixIcons.Timer),
                                onClick = {
                                    title = tpl.name
                                    emoji = tpl.emoji
                                    colorHex = tpl.colorHex
                                    startMin = tpl.startMinute
                                    endMin = tpl.endMinute
                                    payType = tpl.payType
                                    unitAmountText = tpl.unitAmount.toString()
                                    autoRecBreak = false
                                    breakMinText = tpl.breakMinutes.toString()
                                    breakPayType = tpl.breakPayType
                                    transportText = tpl.transportExpense.toString()
                                    mealText = tpl.mealExpense.toString()
                                    siteBonusText = tpl.siteBonus.toString()
                                    agencyFeeText = if (tpl.agencyFeeValue > 0) tpl.agencyFeeValue.toString() else ""
                                    agencyFeeType = tpl.agencyFeeType
                                    taxMode = tpl.taxMode
                                    customTaxText = tpl.customTaxRatePercent.toString()
                                    includeLocalTax = tpl.dailyWorkerIncludeLocalTax
                                    nightEnabled = tpl.allowance.nightAllowanceEnabled
                                    overtimeEnabled = tpl.allowance.overtimeAllowanceEnabled
                                    holidayEnabled = tpl.allowance.holidayAllowanceEnabled
                                    memo = tpl.memo
                                }
                            )
                        }
                    }
                }
            }

            // Core 10-second fields
            item {
                SmallTitle(text = "근무 정보")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextField(
                            value = title,
                            onValueChange = {
                                title = it
                                errorMsg = null
                            },
                            label = "현장/업무 이름 (예: 물류센터 상하차, 카페 대타)",
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        EmojiAndColorIdentityRow(
                            emoji = emoji,
                            fallbackIcon = MiuixIcons.AddCircle,
                            colorHex = colorHex,
                            onEmojiChange = { emoji = it },
                            onColorChange = { colorHex = it }
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .weight(1.1f)
                                    .squircleBackground(MiuixTheme.colorScheme.secondaryContainer, 12.dp)
                                    .clickable { showDatePicker = true }
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text("근무 날짜", style = MiuixTheme.textStyles.footnote2)
                                    Text(
                                        dateIso,
                                        style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Bold),
                                        color = MiuixTheme.colorScheme.primary
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(0.95f)
                                    .squircleBackground(MiuixTheme.colorScheme.secondaryContainer, 12.dp)
                                    .clickable { pickTimeMode = "start" }
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text("출근", style = MiuixTheme.textStyles.footnote2)
                                    Text(
                                        PayrollCalculator.formatMinutes(startMin),
                                        style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(0.95f)
                                    .squircleBackground(MiuixTheme.colorScheme.secondaryContainer, 12.dp)
                                    .clickable { pickTimeMode = "end" }
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text("퇴근", style = MiuixTheme.textStyles.footnote2)
                                    Text(
                                        PayrollCalculator.formatMinutes(endMin),
                                        style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }

                        val payTypes = PayType.entries
                        TabRowWithContour(
                            tabs = payTypes.map { it.label },
                            selectedTabIndex = payTypes.indexOf(payType),
                            onTabSelected = { idx ->
                                val next = payTypes[idx]
                                if (payType != next && unitAmountText == "140000" && next == PayType.HOURLY) {
                                    unitAmountText = defaultMinWage.toString()
                                }
                                payType = next
                            }
                        )

                        val parsedUnitAmt = unitAmountText.toLongOrNull() ?: 0L
                        TextField(
                            value = unitAmountText,
                            onValueChange = { unitAmountText = it.filter(Char::isDigit) },
                            label = "${payType.label} 금액${if (parsedUnitAmt > 0L) " · ${PayrollCalculator.formatWon(parsedUnitAmt)}" else " (원)"}",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "휴게시간 자동 추천 (${PayrollCalculator.recommendedBreakMinutes(rawDur)}분)",
                                style = MiuixTheme.textStyles.body2
                            )
                            Switch(checked = autoRecBreak, onCheckedChange = { autoRecBreak = it })
                        }
                        AnimatedVisibility(
                            visible = !autoRecBreak,
                            enter = AlbaMotion.expandEnter(),
                            exit = AlbaMotion.shrinkExit()
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextField(
                                    value = breakMinText,
                                    onValueChange = { breakMinText = it.filter(Char::isDigit) },
                                    label = "휴게 시간 (분)",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Box(modifier = Modifier.weight(1f).align(Alignment.CenterVertically)) {
                                    TabRowWithContour(
                                        tabs = BreakPayType.entries.map { if (it == BreakPayType.UNPAID) "무급" else "유급" },
                                        selectedTabIndex = BreakPayType.entries.indexOf(breakPayType),
                                        onTabSelected = { breakPayType = BreakPayType.entries[it] }
                                    )
                                }
                            }
                        }

                        // Payment status
                        Text(
                            "정산 입금 상태",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        TabRowWithContour(
                            tabs = PaymentStatus.entries.map { it.label },
                            selectedTabIndex = PaymentStatus.entries.indexOf(paymentStatus),
                            onTabSelected = { paymentStatus = PaymentStatus.entries[it] }
                        )
                    }
                }
            }

            // Expenses and fees
            item {
                SmallTitle(text = "부대비용 · 수수료")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TextField(
                                value = transportText,
                                onValueChange = { transportText = it.filter(Char::isDigit) },
                                label = "교통비 지출 (-)",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            TextField(
                                value = mealText,
                                onValueChange = { mealText = it.filter(Char::isDigit) },
                                label = "식비 지출 (-)",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        TextField(
                            value = siteBonusText,
                            onValueChange = { siteBonusText = it.filter(Char::isDigit) },
                            label = "현장 지원금 · 보너스 (+)",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextField(
                                value = agencyFeeText,
                                onValueChange = { agencyFeeText = it },
                                label = "인력·플랫폼 수수료 (-)",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1.4f)
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                TabRowWithContour(
                                    tabs = FeeType.entries.map { it.label },
                                    selectedTabIndex = FeeType.entries.indexOf(agencyFeeType),
                                    onTabSelected = { agencyFeeType = FeeType.entries[it] }
                                )
                            }
                        }
                    }
                }
            }

            // Tax and allowance boosts
            item {
                SmallTitle(text = "세금 · 가산수당")
                Card(modifier = Modifier.fillMaxWidth()) {
                    val taxOptions = TaxMode.entries
                    OverlayDropdownPreference(
                        title = "일용직/단기 세금 공제",
                        summary = taxMode.label,
                        items = taxOptions.map { it.label },
                        selectedIndex = taxOptions.indexOf(taxMode),
                        onSelectedIndexChange = { taxMode = taxOptions[it] }
                    )
                    AnimatedVisibility(
                        visible = taxMode == TaxMode.DAILY_WORKER,
                        enter = AlbaMotion.expandEnter(),
                        exit = AlbaMotion.shrinkExit()
                    ) {
                        Column {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SwitchPreference(
                                title = "지방소득세 포함 실효 2.97%",
                                summary = "일 15만 원 비과세 공제 후 초과분에 적용 (끄면 2.7%)",
                                checked = includeLocalTax,
                                onCheckedChange = { includeLocalTax = it }
                            )
                        }
                    }
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
                        visible = payType == PayType.HOURLY,
                        enter = AlbaMotion.expandEnter(),
                        exit = AlbaMotion.shrinkExit()
                    ) {
                        Column {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SwitchPreference(
                                title = "야간수당 1.5배 (22:00~06:00)",
                                checked = nightEnabled,
                                onCheckedChange = { nightEnabled = it }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SwitchPreference(
                                title = "연장수당 1.5배 (8시간 초과)",
                                checked = overtimeEnabled,
                                onCheckedChange = { overtimeEnabled = it }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                            SwitchPreference(
                                title = "휴일수당 1.5배",
                                checked = holidayEnabled,
                                onCheckedChange = { holidayEnabled = it }
                            )
                        }
                    }
                    if (!isEditing) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SwitchPreference(
                            title = "단기 프리셋으로 함께 저장",
                            checked = saveAsTemplate,
                            onCheckedChange = { saveAsTemplate = it }
                        )
                    }
                }
            }

            // Net profit summary card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "예상 순수익",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        Text(
                            text = PayrollCalculator.formatWon(preview.netPay),
                            style = MiuixTheme.textStyles.title2.copy(
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum"
                            ),
                            color = MiuixTheme.colorScheme.primary
                        )
                        HorizontalDivider()
                        Text(
                            text = "세전 ${PayrollCalculator.formatWon(preview.grossPay)} · 세금 -${PayrollCalculator.formatWon(preview.taxAmount)} · 수수료 -${PayrollCalculator.formatWon(preview.agencyFee)} · 지출 -${PayrollCalculator.formatWon(preview.transportExpense + preview.mealExpense)}",
                            style = MiuixTheme.textStyles.footnote2.copy(fontFeatureSettings = "tnum"),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
            }

            item {
                TextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = "메모 (선택)",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                TextButton(
                    text = if (isEditing) "수정 내용 저장" else "단기 근무 등록 완료",
                    onClick = ::handleSubmit,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        MiuixDatePickerDialog(
            show = showDatePicker,
            title = "근무 날짜 선택",
            initialDate = PayrollCalculator.parseDate(dateIso, fallbackDate),
            onDismiss = { showDatePicker = false },
            onConfirm = { picked ->
                dateIso = PayrollCalculator.formatDate(picked)
                showDatePicker = false
            }
        )

        if (pickTimeMode != null) {
            MiuixTimePickerDialog(
                show = true,
                title = if (pickTimeMode == "start") "출근 시각" else "퇴근 시각",
                initialMinuteOfDay = if (pickTimeMode == "start") startMin else endMin,
                onDismiss = { pickTimeMode = null },
                onConfirm = { chosen ->
                    if (pickTimeMode == "start") startMin = chosen else endMin = chosen
                    pickTimeMode = null
                }
            )
        }

        if (showDeleteConfirm && existingRecord != null && onDelete != null) {
            ConfirmActionDialog(
                show = true,
                title = "근무 기록 삭제",
                summary = "'${existingRecord.title}' (${existingRecord.date}) 기록을 삭제할까요?",
                confirmText = "삭제",
                isDestructive = true,
                onDismiss = { showDeleteConfirm = false },
                onConfirm = {
                    showDeleteConfirm = false
                    onDelete(existingRecord.id)
                }
            )
        }

        val editPair = pendingEditSave
        if (editPair != null) {
            ConfirmActionDialog(
                show = true,
                title = "근무 내역 수정",
                summary = "'${editPair.first.title}' (${editPair.first.date}) 변경 사항을 저장할까요?",
                confirmText = "저장",
                onDismiss = { pendingEditSave = null },
                onConfirm = {
                    pendingEditSave = null
                    onSave(editPair.first, editPair.second)
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
