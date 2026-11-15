package com.albatora.app.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.albatora.app.model.*
import com.albatora.app.ui.components.MiuixColorPickerDialog
import com.albatora.app.ui.components.MiuixBottomEdgeBlur
import com.albatora.app.ui.components.MiuixFloatingScrollTopBar
import com.albatora.app.ui.components.MiuixGlassIcon
import com.albatora.app.ui.components.SquirclePillBadge
import com.albatora.app.ui.components.StackedToastHost
import com.albatora.app.ui.components.StackedToastState
import com.albatora.app.ui.components.formatRgbHex
import com.albatora.app.ui.theme.JobAccentPalette
import com.albatora.app.ui.theme.LocalIsDarkTheme
import com.albatora.app.widget.CompactLiveWidgetProvider
import com.albatora.app.widget.LiveEarningsWidgetProvider
import com.albatora.app.widget.MonthlySummaryWidgetProvider
import com.albatora.app.widget.WeeklyScheduleWidgetProvider
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.AddCircle
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.BankCards
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun WidgetStudioScreen(
    state: AlbaToraDataState,
    onUpdateWidgetConfig: ((WidgetConfig) -> WidgetConfig) -> Unit,
    onBack: () -> Unit,
    onNotify: (String) -> Unit,
    toastState: StackedToastState? = null,
    confirmActionText: String? = null,
    onConfirmFinish: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    val cfg = state.settings.widgetConfig
    val isDarkApp = LocalIsDarkTheme.current
    var previewTab by remember { mutableIntStateOf(0) }
    var showAccentPicker by remember { mutableStateOf(false) }

    val effectiveDarkCard = when (cfg.themeStyle) {
        WidgetThemeStyle.AUTO -> isDarkApp
        WidgetThemeStyle.LIGHT -> false
        WidgetThemeStyle.DARK -> true
        WidgetThemeStyle.GLASS -> isDarkApp
    }
    val effectiveAlpha = if (cfg.themeStyle == WidgetThemeStyle.GLASS) {
        (cfg.opacity.percent.coerceAtMost(72) / 100f)
    } else {
        cfg.opacity.percent / 100f
    }
    val previewCardColor = when {
        effectiveDarkCard && cfg.themeStyle == WidgetThemeStyle.GLASS -> Color(0xFF161922).copy(alpha = effectiveAlpha)
        effectiveDarkCard -> Color(0xFF1C1F27).copy(alpha = effectiveAlpha)
        cfg.themeStyle == WidgetThemeStyle.GLASS -> Color(0xFFF7F9FC).copy(alpha = effectiveAlpha)
        else -> Color.White.copy(alpha = effectiveAlpha)
    }
    val previewTextPrimary = if (effectiveDarkCard) Color(0xFFF3F4F6) else Color(0xFF111827)
    val previewTextSecondary = if (effectiveDarkCard) Color(0xFFA5AEBC) else Color(0xFF6B7280)
    val previewPillInner = if (effectiveDarkCard) Color(0xFF2B303C) else Color(0xFFECEFF4)
    val previewStroke = if (effectiveDarkCard) Color.White.copy(alpha = 0.22f) else Color(0xFF111827).copy(alpha = 0.12f)

    val previewAccent = if (cfg.useAppAccent) {
        MiuixTheme.colorScheme.primary
    } else {
        Color(cfg.customAccentHex)
    }
    val previewOnAccent = if (previewAccent.luminance() > 0.62f) Color(0xFF111827) else Color.White

    val surfaceBg = MiuixTheme.colorScheme.surface
    val barBackdrop = rememberLayerBackdrop {
        drawRect(surfaceBg)
        drawContent()
    }

    Scaffold(
        topBar = {
            MiuixFloatingScrollTopBar(
                title = "바탕화면 위젯 꾸미기",
                scrollBehavior = scrollBehavior,
                backdrop = barBackdrop,
                maxContentWidth = 620.dp,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.Back, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    if (onConfirmFinish != null) {
                        TextButton(
                            text = confirmActionText ?: "완료",
                            onClick = onConfirmFinish,
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val sidePad = ((maxWidth - 620.dp) / 2f).coerceAtLeast(16.dp)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(barBackdrop)
                    .background(surfaceBg)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .overScrollVertical(),
            contentPadding = PaddingValues(
                start = sidePad,
                end = sidePad,
                top = paddingValues.calculateTopPadding() + 10.dp,
                bottom = paddingValues.calculateBottomPadding() + 64.dp
            ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Live OneUI 8~9 Preview Stage
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TabRowWithContour(
                        tabs = listOf("실시간 4×2", "슬림 바 4×1", "주간 일정", "월간 요약"),
                        selectedTabIndex = previewTab,
                        onTabSelected = { previewTab = it }
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .squircleClip(24.dp)
                    ) {
                        // Simulated OneUI wallpaper backdrop to make opacity & glass effects obvious
                        Canvas(modifier = Modifier.matchParentSize()) {
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = if (isDarkApp) {
                                        listOf(Color(0xFF14243E), Color(0xFF281B3D), Color(0xFF19323B))
                                    } else {
                                        listOf(Color(0xFFCFE2FF), Color(0xFFE6DCFF), Color(0xFFD5F1EA))
                                    },
                                    start = Offset.Zero,
                                    end = Offset(size.width, size.height)
                                )
                            )
                            drawCircle(
                                color = previewAccent.copy(alpha = 0.25f),
                                radius = size.minDimension * 0.45f,
                                center = Offset(size.width * 0.85f, size.height * 0.2f)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                                .animateContentSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = previewTab,
                                transitionSpec = {
                                    (fadeIn(tween(200)) + scaleIn(initialScale = 0.96f, animationSpec = tween(220)))
                                        .togetherWith(fadeOut(tween(150)))
                                },
                                label = "widget_preview_tab"
                            ) { tab ->
                                when (tab) {
                                    0 -> OneUiLivePreviewCard(
                                        cfg = cfg,
                                        cardBg = previewCardColor,
                                        stroke = previewStroke,
                                        pillInner = previewPillInner,
                                        textPrimary = previewTextPrimary,
                                        textSecondary = previewTextSecondary,
                                        accent = previewAccent,
                                        onAccent = previewOnAccent,
                                        quickJobName = state.jobs.find { it.id == cfg.defaultQuickJobId }?.name
                                    )
                                    1 -> OneUiCompactPreviewBar(
                                        cfg = cfg,
                                        cardBg = previewCardColor,
                                        stroke = previewStroke,
                                        pillInner = previewPillInner,
                                        textPrimary = previewTextPrimary,
                                        textSecondary = previewTextSecondary,
                                        accent = previewAccent,
                                        onAccent = previewOnAccent
                                    )
                                    2 -> OneUiWeeklyPreviewCard(
                                        cfg = cfg,
                                        cardBg = previewCardColor,
                                        stroke = previewStroke,
                                        textPrimary = previewTextPrimary,
                                        textSecondary = previewTextSecondary,
                                        accent = previewAccent
                                    )
                                    else -> OneUiMonthlyPreviewCard(
                                        cfg = cfg,
                                        cardBg = previewCardColor,
                                        stroke = previewStroke,
                                        textPrimary = previewTextPrimary,
                                        textSecondary = previewTextSecondary,
                                        accent = previewAccent
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "OneUI 8·9 표준 26dp 곡률과 다이나믹 투명도를 기준으로 모든 홈 화면 위젯에 즉시 반영됩니다.",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }

            // 2. Pin to Home Screen (API 26+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                item {
                    SmallTitle(text = "바탕화면에 위젯 바로 추가")
                    val pinOptions = listOf(
                        Triple("실시간 타이머 (4×2)", LiveEarningsWidgetProvider::class.java, MiuixIcons.Stopwatch),
                        Triple("슬림 타이머 바 (4×1)", CompactLiveWidgetProvider::class.java, MiuixIcons.Tune),
                        Triple("이번 주 스케줄", WeeklyScheduleWidgetProvider::class.java, MiuixIcons.Months),
                        Triple("이번 달 누적 (2×2)", MonthlySummaryWidgetProvider::class.java, MiuixIcons.BankCards)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(pinOptions) { (label, cls, icon) ->
                            SquirclePillBadge(
                                text = "$label 추가",
                                backgroundColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.14f),
                                contentColor = MiuixTheme.colorScheme.primary,
                                leadingIcon = icon,
                                onClick = {
                                    val mgr = AppWidgetManager.getInstance(context)
                                    val provider = ComponentName(context, cls)
                                    if (mgr.isRequestPinAppWidgetSupported) {
                                        val ok = mgr.requestPinAppWidget(provider, null, null)
                                        onNotify(
                                            if (ok) "'$label' 위젯 추가 창을 열었습니다"
                                            else "홈 화면 위젯 서랍에서 직접 추가해 주세요"
                                        )
                                    } else {
                                        onNotify("현재 런처에서는 홈 화면에서 길게 눌러 위젯을 추가해 주세요")
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 3. Appearance Controls
            item {
                SmallTitle(text = "외관 및 투명도 (OneUI 스타일)")
                val styles = WidgetThemeStyle.entries
                val opacities = WidgetOpacity.entries
                Card(modifier = Modifier.fillMaxWidth()) {
                    OverlayDropdownPreference(
                        title = "위젯 배경 스타일",
                        summary = "${cfg.themeStyle.label} · ${cfg.themeStyle.description}",
                        items = styles.map { "${it.label} (${it.description})" },
                        selectedIndex = styles.indexOf(cfg.themeStyle).coerceAtLeast(0),
                        onSelectedIndexChange = { idx ->
                            val next = styles.getOrNull(idx) ?: WidgetThemeStyle.AUTO
                            onUpdateWidgetConfig { it.copy(themeStyle = next) }
                        },
                        startAction = {
                            MiuixGlassIcon(
                                icon = MiuixIcons.Layers,
                                contentDescription = null,
                                accentColor = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 6.dp),
                                size = 38.dp,
                                iconSize = 18.dp,
                                cornerRadius = 11.dp
                            )
                        }
                    )
                    HorizontalDivider()
                    OverlayDropdownPreference(
                        title = "카드 배경 불투명도",
                        summary = "현재 ${cfg.opacity.label}",
                        items = opacities.map { it.label },
                        selectedIndex = opacities.indexOf(cfg.opacity).coerceAtLeast(0),
                        onSelectedIndexChange = { idx ->
                            val next = opacities.getOrNull(idx) ?: WidgetOpacity.HIGH
                            onUpdateWidgetConfig { it.copy(opacity = next) }
                        },
                        startAction = {
                            MiuixGlassIcon(
                                icon = MiuixIcons.Tune,
                                contentDescription = null,
                                accentColor = Color(0xFF0EA5E9),
                                modifier = Modifier.padding(end = 6.dp),
                                size = 38.dp,
                                iconSize = 18.dp,
                                cornerRadius = 11.dp
                            )
                        }
                    )
                    HorizontalDivider()
                    SwitchPreference(
                        title = "앱 테마 포인트 색상 따라가기",
                        summary = if (cfg.useAppAccent) "앱 테마 설정과 동일한 색상을 위젯에 사용" else "위젯 전용 커스텀 포인트 색상 사용",
                        checked = cfg.useAppAccent,
                        onCheckedChange = { follow ->
                            onUpdateWidgetConfig { it.copy(useAppAccent = follow) }
                        },
                        startAction = {
                            MiuixGlassIcon(
                                icon = MiuixIcons.Theme,
                                contentDescription = null,
                                accentColor = previewAccent,
                                modifier = Modifier.padding(end = 6.dp),
                                size = 38.dp,
                                iconSize = 18.dp,
                                cornerRadius = 11.dp
                            )
                        }
                    )

                    AnimatedVisibility(
                        visible = !cfg.useAppAccent,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            HorizontalDivider()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "위젯 전용 포인트 색상 (${formatRgbHex(cfg.customAccentHex)})",
                                        style = MiuixTheme.textStyles.body2.copy(fontWeight = FontWeight.Medium)
                                    )
                                    SquirclePillBadge(
                                        text = "직접 지정",
                                        backgroundColor = Color(cfg.customAccentHex).copy(alpha = 0.16f),
                                        contentColor = Color(cfg.customAccentHex),
                                        onClick = { showAccentPicker = true }
                                    )
                                }
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(JobAccentPalette) { (_, hex) ->
                                        val selected = (hex and 0x00FFFFFFL) == (cfg.customAccentHex and 0x00FFFFFFL)
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(Color(hex))
                                                .then(
                                                    if (selected) Modifier.border(2.5.dp, MiuixTheme.colorScheme.onSurface, CircleShape)
                                                    else Modifier
                                                )
                                                .clickable {
                                                    onUpdateWidgetConfig { it.copy(customAccentHex = hex) }
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Widget Content & Quick Clock-in Behavior
            item {
                SmallTitle(text = "표시 요소 및 동작 설정")
                val jobOptions = listOf<Pair<String?, String>>(null to "앱 내 즉시 출근 창 열기") +
                    state.jobs.map { it.id to "${it.name} (원탭 바로 출근)" }
                val currentJobIdx = jobOptions.indexOfFirst { it.first == cfg.defaultQuickJobId }.coerceAtLeast(0)

                Card(modifier = Modifier.fillMaxWidth()) {
                    SwitchPreference(
                        title = "체감 물가 환산 함께 표시",
                        summary = "실시간 타이머 위젯 하단에 커피·식사 환산 개수 표시",
                        checked = cfg.showTangibleConversion,
                        onCheckedChange = { enabled ->
                            onUpdateWidgetConfig { it.copy(showTangibleConversion = enabled) }
                        }
                    )
                    HorizontalDivider()
                    SwitchPreference(
                        title = "하단 빠른 실행 버튼 표시",
                        summary = "휴게·퇴근·바로 출근 버튼을 위젯 안에서 바로 조작",
                        checked = cfg.showQuickActions,
                        onCheckedChange = { enabled ->
                            onUpdateWidgetConfig { it.copy(showQuickActions = enabled) }
                        }
                    )
                    HorizontalDivider()
                    SwitchPreference(
                        title = "주간 스케줄에서 지난 요일 숨기기",
                        summary = "오늘 이후 남은 이번 주 일정만 깔끔하게 표시",
                        checked = cfg.weeklyHidePastDays,
                        onCheckedChange = { hide ->
                            onUpdateWidgetConfig { it.copy(weeklyHidePastDays = hide) }
                        }
                    )
                    HorizontalDivider()
                    SwitchPreference(
                        title = "월간 위젯 미수금·월급날 배지 표시",
                        summary = "입금 대기 미수금 금액 또는 다음 월급 D-Day 배지 노출",
                        checked = cfg.showMonthlyUnpaidBadge,
                        onCheckedChange = { showBadge ->
                            onUpdateWidgetConfig { it.copy(showMonthlyUnpaidBadge = showBadge) }
                        }
                    )
                    HorizontalDivider()
                    OverlayDropdownPreference(
                        title = "대기 상태 '바로 출근' 대상",
                        summary = jobOptions.getOrNull(currentJobIdx)?.second ?: "앱 내 즉시 출근 창 열기",
                        items = jobOptions.map { it.second },
                        selectedIndex = currentJobIdx,
                        onSelectedIndexChange = { idx ->
                            val pickedId = jobOptions.getOrNull(idx)?.first
                            onUpdateWidgetConfig { it.copy(defaultQuickJobId = pickedId) }
                        }
                    )
                }
            }
            }

            MiuixBottomEdgeBlur(
                backdrop = barBackdrop,
                extraHeight = 36.dp,
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            if (toastState != null) {
                StackedToastHost(
                    state = toastState,
                    backdrop = barBackdrop,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .widthIn(max = 400.dp)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 24.dp)
                )
            }
        }
    }

    MiuixColorPickerDialog(
        show = showAccentPicker,
        title = "위젯 전용 포인트 색상",
        initialColorHex = cfg.customAccentHex,
        onDismiss = { showAccentPicker = false },
        onConfirm = { pickedHex ->
            onUpdateWidgetConfig { it.copy(useAppAccent = false, customAccentHex = pickedHex) }
        }
    )
}

@Composable
private fun OneUiLivePreviewCard(
    cfg: WidgetConfig,
    cardBg: Color,
    stroke: Color,
    pillInner: Color,
    textPrimary: Color,
    textSecondary: Color,
    accent: Color,
    onAccent: Color,
    quickJobName: String?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(cardBg)
            .border(1.dp, stroke, RoundedCornerShape(22.dp))
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF16A34A))
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = "${quickJobName ?: "카페 알바"} · 근무 중",
                style = MiuixTheme.textStyles.body1.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                color = textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF16A34A).copy(alpha = 0.16f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "+206원/분",
                    style = MiuixTheme.textStyles.footnote1.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                    color = Color(0xFF16A34A)
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = "37,080원",
                fontSize = 31.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            Text(
                text = if (cfg.showTangibleConversion) "실근무 03:00:00 · 아메리카노 8.2개" else "실근무 03:00:00",
                style = MiuixTheme.textStyles.footnote1.copy(fontSize = 13.5.sp),
                color = textSecondary
            )
        }
        if (cfg.showQuickActions) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(CircleShape)
                        .background(pillInner),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "휴게 시작",
                        style = MiuixTheme.textStyles.body2.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        color = textPrimary
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(CircleShape)
                        .background(accent),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "퇴근 완료",
                        style = MiuixTheme.textStyles.body2.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        color = onAccent
                    )
                }
            }
        }
    }
}

@Composable
private fun OneUiCompactPreviewBar(
    cfg: WidgetConfig,
    cardBg: Color,
    stroke: Color,
    pillInner: Color,
    textPrimary: Color,
    textSecondary: Color,
    accent: Color,
    onAccent: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(cardBg)
            .border(1.dp, stroke, RoundedCornerShape(22.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(Color(0xFF16A34A))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "37,080원",
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )
            Text(
                text = "카페 알바 · 03:00:00",
                style = MiuixTheme.textStyles.footnote1.copy(fontSize = 12.5.sp),
                color = textSecondary
            )
        }
        if (cfg.showQuickActions) {
            Box(
                modifier = Modifier
                    .height(38.dp)
                    .clip(CircleShape)
                    .background(pillInner)
                    .padding(horizontal = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "휴게",
                    style = MiuixTheme.textStyles.footnote1.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    color = textPrimary
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .height(38.dp)
                    .clip(CircleShape)
                    .background(accent)
                    .padding(horizontal = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "퇴근",
                    style = MiuixTheme.textStyles.footnote1.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    color = onAccent
                )
            }
        }
    }
}

@Composable
private fun OneUiWeeklyPreviewCard(
    cfg: WidgetConfig,
    cardBg: Color,
    stroke: Color,
    textPrimary: Color,
    textSecondary: Color,
    accent: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(cardBg)
            .border(1.dp, stroke, RoundedCornerShape(22.dp))
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "이번 주 스케줄",
                style = MiuixTheme.textStyles.body1.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                color = textPrimary
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f))
                    .padding(horizontal = 11.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "4건",
                    style = MiuixTheme.textStyles.footnote1.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                    color = accent
                )
            }
        }
        Text(
            text = "이번 주 근무 일정",
            style = MiuixTheme.textStyles.footnote1.copy(fontSize = 12.5.sp),
            color = textSecondary
        )
        if (!cfg.weeklyHidePastDays) {
            Text(
                "어제(완) · 카페 알바 · 14:00~19:00",
                style = MiuixTheme.textStyles.body2.copy(fontSize = 14.sp),
                color = textSecondary
            )
        }
        Text(
            "오늘 · 카페 알바 · 14:00~20:00 · 진행 중",
            style = MiuixTheme.textStyles.body2.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            color = textPrimary
        )
        Text(
            "토 · 편의점 대타 · 09:00~15:00",
            style = MiuixTheme.textStyles.body2.copy(fontSize = 14.sp),
            color = textSecondary
        )
    }
}

@Composable
private fun OneUiMonthlyPreviewCard(
    cfg: WidgetConfig,
    cardBg: Color,
    stroke: Color,
    textPrimary: Color,
    textSecondary: Color,
    accent: Color
) {
    Column(
        modifier = Modifier
            .widthIn(max = 220.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(cardBg)
            .border(1.dp, stroke, RoundedCornerShape(22.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "11월 예상 누적",
            style = MiuixTheme.textStyles.body2.copy(fontSize = 13.5.sp, fontWeight = FontWeight.Bold),
            color = textSecondary
        )
        Text(
            text = "1,482,000원",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = textPrimary
        )
        Text(
            text = "완료 14건 · 98시간 30분",
            style = MiuixTheme.textStyles.footnote1.copy(fontSize = 12.5.sp),
            color = textSecondary
        )
        if (cfg.showMonthlyUnpaidBadge) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f))
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "월급 D-4",
                    style = MiuixTheme.textStyles.footnote1.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                    color = accent
                )
            }
        }
    }
}
