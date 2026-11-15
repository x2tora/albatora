package com.albatora.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.albatora.app.BuildConfig
import com.albatora.app.domain.PayrollCalculator
import com.albatora.app.model.*
import com.albatora.app.service.AutoDndController
import com.albatora.app.service.BatteryOptimizationHelper
import com.albatora.app.ui.components.AppUpdateUiState
import com.albatora.app.ui.components.ConfirmActionDialog
import com.albatora.app.ui.components.MiuixColorPickerDialog
import com.albatora.app.ui.components.MiuixGlassIcon
import com.albatora.app.ui.components.formatRgbHex
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import java.time.LocalDate

@Composable
fun MoreSettingsScreen(
    state: AlbaToraDataState,
    scrollBehavior: ScrollBehavior,
    updateUiState: AppUpdateUiState,
    onUpdateTheme: (ThemeOption) -> Unit,
    onUpdateThemeColorStyle: (ThemeColorStyle) -> Unit,
    onUpdateNavBarStyle: (NavBarStyle) -> Unit,
    onUpdateWideDualLayout: (Boolean) -> Unit,
    onUpdateCustomKeyColor: (Long?) -> Unit,
    onUpdateOngoingNotification: (Boolean) -> Unit,
    onUpdateAutoDnd: (Boolean) -> Unit,
    onUpdateDefaultMinWage: (Long) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onExportJson: () -> String,
    onImportJson: (String) -> Result<Unit>,
    onResetAllData: () -> Unit,
    onCheckForUpdate: () -> Unit,
    onOpenWidgetStudio: () -> Unit,
    onNotify: (String) -> Unit,
    topInset: Dp = 0.dp
) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current

    var showMinWageDialog by remember { mutableStateOf(false) }
    var showAppColorDialog by remember { mutableStateOf(false) }
    var showTemplatesDialog by remember { mutableStateOf(false) }
    var showPasteRestoreDialog by remember { mutableStateOf(false) }
    var showDndPermissionDialog by remember { mutableStateOf(false) }
    var hasDndAccess by remember { mutableStateOf(AutoDndController.hasPolicyAccess(context)) }
    var hasBatteryExemption by remember {
        mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasDndAccess = AutoDndController.hasPolicyAccess(context)
                hasBatteryExemption = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var showResetConfirm by remember { mutableStateOf(false) }

    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(onExportJson().toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                onNotify("JSON 백업 파일을 저장했습니다.")
            }.onFailure {
                onNotify("백업 파일 저장에 실패했습니다.")
            }
        }
    }

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader(Charsets.UTF_8).readText()
                } ?: error("파일을 읽을 수 없습니다.")
            }.fold(
                onSuccess = { jsonText ->
                    onImportJson(jsonText).onSuccess {
                        onNotify("백업 데이터를 복원했습니다.")
                    }.onFailure {
                        onNotify("올바른 알바토라 백업 파일이 아닙니다.")
                    }
                },
                onFailure = {
                    onNotify("백업 파일 읽기에 실패했습니다.")
                }
            )
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val sidePad = ((maxWidth - 620.dp) / 2f).coerceAtLeast(16.dp)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(start = sidePad, end = sidePad, top = topInset + 8.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        item {
            SmallTitle(text = "화면 · 테마 설정")
            Card(modifier = Modifier.fillMaxWidth()) {
                val options = ThemeOption.entries
                OverlayDropdownPreference(
                    title = "테마 모드",
                    summary = state.settings.themeOption.label,
                    items = options.map { it.label },
                    selectedIndex = options.indexOf(state.settings.themeOption),
                    onSelectedIndexChange = { onUpdateTheme(options[it]) },
                    startAction = {
                        PreferenceStartIcon(MiuixIcons.Theme, MiuixTheme.colorScheme.primary)
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                val colorStyles = ThemeColorStyle.entries
                val colorStyle = state.settings.resolvedColorStyle()
                OverlayDropdownPreference(
                    title = "테마 컬러 스타일",
                    summary = "${colorStyle.label} · ${colorStyle.description}",
                    items = colorStyles.map { it.label },
                    selectedIndex = colorStyles.indexOf(colorStyle),
                    onSelectedIndexChange = { index ->
                        val chosen = colorStyles[index]
                        onUpdateThemeColorStyle(chosen)
                        onNotify("테마 컬러가 '${chosen.label}'으로 적용되었습니다.")
                    },
                    startAction = {
                        PreferenceStartIcon(MiuixIcons.Favorites, Color(0xFF7C5CFF))
                    }
                )
                AnimatedVisibility(
                    visible = colorStyle == ThemeColorStyle.CUSTOM,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    val previewHex = state.settings.customKeyColorHex ?: 0xFF3482FF
                    Column {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        ArrowPreference(
                            title = "커스텀 포인트 색상 지정",
                            summary = "현재 ${formatRgbHex(previewHex)} 적용 중 · 탭하여 변경",
                            onClick = { showAppColorDialog = true },
                            startAction = {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(Color(previewHex))
                                        .border(1.dp, MiuixTheme.colorScheme.onSurface.copy(alpha = 0.16f), CircleShape)
                                )
                            }
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                val navStyles = NavBarStyle.entries
                OverlayDropdownPreference(
                    title = "인터페이스 스타일",
                    summary = "${state.settings.navBarStyle.label} · ${state.settings.navBarStyle.description}",
                    items = navStyles.map { it.label },
                    selectedIndex = navStyles.indexOf(state.settings.navBarStyle),
                    onSelectedIndexChange = { index ->
                        val chosen = navStyles[index]
                        onUpdateNavBarStyle(chosen)
                        onNotify("인터페이스 스타일이 '${chosen.label}'으로 적용되었습니다.")
                    },
                    startAction = {
                        PreferenceStartIcon(MiuixIcons.Tune, Color(0xFF14B8A6))
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SwitchPreference(
                    title = "대화면 듀얼 칼럼 보기",
                    summary = "태블릿·폴더블 등 넓은 화면에서 좌우 2단 분할 레이아웃 사용 (기본: 중앙 단일 보기)",
                    checked = state.settings.wideDualLayoutEnabled,
                    onCheckedChange = { enabled ->
                        onUpdateWideDualLayout(enabled)
                        onNotify(
                            if (enabled) "넓은 화면에서 듀얼 칼럼 레이아웃이 활성화되었습니다."
                            else "대화면에서도 모바일과 동일한 중앙 단일 보기로 표시됩니다."
                        )
                    },
                    startAction = {
                        PreferenceStartIcon(MiuixIcons.ScreenMirroring, Color(0xFF3482FF))
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ArrowPreference(
                    title = "바탕화면 위젯 꾸미기",
                    summary = "${state.settings.widgetConfig.themeStyle.label} · 투명도 ${state.settings.widgetConfig.opacity.label} (위젯 4종)",
                    onClick = onOpenWidgetStudio,
                    startAction = {
                        PreferenceStartIcon(MiuixIcons.Layers, Color(0xFF0EA5E9))
                    }
                )
            }
        }

        item {
            SmallTitle(text = "실시간 알림 · 급여 · 프리셋")
            Card(modifier = Modifier.fillMaxWidth()) {
                SwitchPreference(
                    title = "상단바 실시간 수익 알림",
                    summary = "근무 중 실시간 누적 금액과 [휴게] / [퇴근] 빠른 버튼 표시",
                    checked = state.settings.ongoingNotificationEnabled,
                    onCheckedChange = onUpdateOngoingNotification,
                    startAction = {
                        PreferenceStartIcon(MiuixIcons.Alarm, Color(0xFFFF6B4A))
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SwitchPreference(
                    title = "알바 중 자동 방해금지(무음)",
                    summary = when {
                        !hasDndAccess -> "방해금지 권한을 허용하면 근무 시작 시 자동 무음 전환됩니다"
                        state.settings.autoDndEnabled -> "근무 중 방해금지 자동 켜기 · 휴게/퇴근 시 원래 상태로 복원"
                        else -> "근무 중 알림 방해를 자동으로 차단하고 퇴근 시 해제합니다"
                    },
                    checked = state.settings.autoDndEnabled && hasDndAccess,
                    onCheckedChange = { enabled ->
                        if (enabled && !AutoDndController.hasPolicyAccess(context)) {
                            showDndPermissionDialog = true
                        } else {
                            onUpdateAutoDnd(enabled)
                            onNotify(if (enabled) "알바 중 자동 방해금지가 켜졌습니다." else "알바 중 자동 방해금지가 꺼졌습니다.")
                        }
                    },
                    startAction = {
                        PreferenceStartIcon(MiuixIcons.Timer, Color(0xFF6366F1))
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ArrowPreference(
                    title = "백그라운드 항상 실행 (배터리 최적화 제외)",
                    summary = if (hasBatteryExemption) {
                        "허용됨 · 상단바 알림이 꺼져 있어도 백그라운드 동작 유지"
                    } else {
                        "미허용 · 탭하여 배터리 최적화 제외(제한 없음) 권한 허용"
                    },
                    onClick = {
                        if (hasBatteryExemption) {
                            onNotify("이미 백그라운드 항상 실행이 허용되어 있습니다.")
                        } else {
                            BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(context)
                        }
                    },
                    startAction = {
                        PreferenceStartIcon(
                            MiuixIcons.Lock,
                            if (hasBatteryExemption) Color(0xFF10B981) else Color(0xFFF59E0B)
                        )
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ArrowPreference(
                    title = "기본 최저시급 설정",
                    summary = "현재 기본값: ${PayrollCalculator.formatWon(state.settings.defaultMinWage)} (2026 기준)",
                    onClick = { showMinWageDialog = true },
                    startAction = {
                        PreferenceStartIcon(MiuixIcons.BankCards, Color(0xFF10B981))
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ArrowPreference(
                    title = "단기 알바 프리셋(템플릿) 관리",
                    summary = "저장된 프리셋 ${state.templates.size}개",
                    onClick = { showTemplatesDialog = true },
                    startAction = {
                        PreferenceStartIcon(MiuixIcons.Notes, MiuixTheme.colorScheme.primary)
                    }
                )
            }
        }

        item {
            SmallTitle(text = "로컬 데이터 JSON 백업 및 복원")
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = "JSON 파일로 내보내기 (백업)",
                    summary = "모든 알바, 근무 기록, 프리셋, 설정을 JSON 파일로 저장",
                    onClick = {
                        val today = PayrollCalculator.formatDate(LocalDate.now()).replace("-", "")
                        exportFileLauncher.launch("albatora-backup-$today.json")
                    },
                    startAction = {
                        PreferenceStartIcon(MiuixIcons.Backup, Color(0xFF3482FF))
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ArrowPreference(
                    title = "JSON 파일에서 가져오기 (복원)",
                    summary = "저장해 둔 JSON 백업 파일을 불러와 데이터를 복원합니다",
                    onClick = {
                        importFileLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                    startAction = {
                        PreferenceStartIcon(MiuixIcons.Import, Color(0xFF10B981))
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ArrowPreference(
                    title = "클립보드 백업 텍스트 복사 / 붙여넣기 복원",
                    summary = "파일 없이 JSON 텍스트로 바로 백업하거나 복원합니다",
                    onClick = { showPasteRestoreDialog = true },
                    startAction = {
                        PreferenceStartIcon(MiuixIcons.Copy, Color(0xFF06B6D4))
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ArrowPreference(
                    title = "모든 데이터 초기화",
                    summary = "등록된 모든 알바와 기록을 비웁니다 (테마/기본 설정 유지)",
                    onClick = { showResetConfirm = true },
                    startAction = {
                        PreferenceStartIcon(MiuixIcons.Reset, MiuixTheme.colorScheme.error)
                    }
                )
            }
        }

        item {
            SmallTitle(text = "앱 정보 및 업데이트")
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = "앱 업데이트 확인",
                    summary = when {
                        updateUiState.isChecking -> "GitHub 릴리즈에서 최신 버전을 확인하는 중..."
                        !updateUiState.lastStatusNote.isNullOrBlank() -> "현재 v${BuildConfig.VERSION_NAME} · ${updateUiState.lastStatusNote}"
                        else -> "현재 v${BuildConfig.VERSION_NAME} · 앱 실행 시 자동 확인 (탭하여 지금 확인)"
                    },
                    onClick = onCheckForUpdate,
                    startAction = {
                        PreferenceStartIcon(MiuixIcons.Store, MiuixTheme.colorScheme.primary)
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                BasicComponent(
                    title = "알바토라 (AlbaTora) v${BuildConfig.VERSION_NAME}",
                    summary = "실시간 하이브리드 수익 트래커 · 정규/단기 일용직 올인원 급여 관리",
                    startAction = {
                        PreferenceStartIcon(MiuixIcons.Info, MiuixTheme.colorScheme.primary)
                    }
                )
            }
        }
    }
    }

    MiuixColorPickerDialog(
        show = showAppColorDialog,
        title = "커스텀 테마 포인트 색상 선택",
        initialColorHex = state.settings.customKeyColorHex ?: 0xFF3482FF,
        onDismiss = { showAppColorDialog = false },
        onConfirm = { chosenHex ->
            onUpdateCustomKeyColor(chosenHex)
            onNotify("커스텀 포인트 색상이 ${formatRgbHex(chosenHex)}로 적용되었습니다.")
        }
    )

    if (showDndPermissionDialog) {
        ConfirmActionDialog(
            show = true,
            title = "방해금지 권한 필요",
            summary = "알바 중 자동 방해금지(무음) 기능을 사용하려면 시스템 설정에서 알바토라의 '방해 금지 권한'을 허용해 주세요.",
            confirmText = "설정으로 이동",
            onDismiss = { showDndPermissionDialog = false },
            onConfirm = {
                showDndPermissionDialog = false
                AutoDndController.openPolicyAccessSettings(context)
            }
        )
    }

    // Default minimum wage editor
    var wageText by remember(showMinWageDialog, state.settings.defaultMinWage) {
        mutableStateOf(state.settings.defaultMinWage.toString())
    }
    OverlayDialog(
        show = showMinWageDialog,
        title = "기본 최저시급 설정",
        summary = "2026년 대한민국 법정 최저시급은 10,320원입니다.",
        onDismissRequest = { showMinWageDialog = false }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextField(
                value = wageText,
                onValueChange = { wageText = it.filter(Char::isDigit) },
                label = "시급 (원)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(text = "취소", onClick = { showMinWageDialog = false }, modifier = Modifier.weight(1f))
                TextButton(
                    text = "저장",
                    onClick = {
                        val w = wageText.toLongOrNull() ?: DEFAULT_2026_MIN_WAGE
                        onUpdateDefaultMinWage(w)
                        showMinWageDialog = false
                        onNotify("기본 최저시급이 ${PayrollCalculator.formatWon(w)}으로 저장되었습니다.")
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // Saved short-term templates list
    var pendingDeleteTemplate by remember { mutableStateOf<ShortTermTemplate?>(null) }
    OverlayDialog(
        show = showTemplatesDialog,
        title = "단기 알바 프리셋 관리",
        onDismissRequest = { showTemplatesDialog = false }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.templates.isEmpty()) {
                Text(
                    "저장된 프리셋이 없습니다. [단기/일용직 10초 퀵 등록]에서 '프리셋으로 저장'을 켜고 등록해 보세요.",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            } else {
                state.templates.forEach { tpl ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tpl.name, style = MiuixTheme.textStyles.body2)
                            Text(
                                "${tpl.payType.label} ${PayrollCalculator.formatWon(tpl.unitAmount)} · ${tpl.taxMode.shortLabel}",
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                        IconButton(onClick = { pendingDeleteTemplate = tpl }) {
                            Icon(MiuixIcons.Delete, contentDescription = "프리셋 삭제")
                        }
                    }
                }
            }
            TextButton(
                text = "닫기",
                onClick = { showTemplatesDialog = false },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    val tplToDelete = pendingDeleteTemplate
    if (tplToDelete != null) {
        ConfirmActionDialog(
            show = true,
            title = "프리셋 삭제",
            summary = "'${tplToDelete.name}' 프리셋을 삭제할까요?",
            confirmText = "삭제",
            isDestructive = true,
            onDismiss = { pendingDeleteTemplate = null },
            onConfirm = {
                onDeleteTemplate(tplToDelete.id)
                pendingDeleteTemplate = null
            }
        )
    }

    // Clipboard JSON export/restore
    var pasteJson by remember(showPasteRestoreDialog) { mutableStateOf("") }
    OverlayDialog(
        show = showPasteRestoreDialog,
        title = "클립보드 JSON 백업 / 복원",
        onDismissRequest = { showPasteRestoreDialog = false }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(
                text = "현재 데이터 JSON 클립보드로 복사",
                onClick = {
                    clipboardManager.setText(AnnotatedString(onExportJson()))
                    onNotify("백업 JSON을 클립보드에 복사했습니다.")
                },
                modifier = Modifier.fillMaxWidth()
            )
            HorizontalDivider()
            TextField(
                value = pasteJson,
                onValueChange = { pasteJson = it },
                label = "복원할 JSON 텍스트 붙여넣기",
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(text = "취소", onClick = { showPasteRestoreDialog = false }, modifier = Modifier.weight(1f))
                TextButton(
                    text = "복원 실행",
                    onClick = {
                        onImportJson(pasteJson).onSuccess {
                            showPasteRestoreDialog = false
                            onNotify("클립보드 데이터로 복원을 완료했습니다.")
                        }.onFailure {
                            onNotify("유효한 JSON 형식이 아닙니다.")
                        }
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    OverlayDialog(
        show = showResetConfirm,
        title = "전체 데이터 초기화",
        summary = "모든 알바와 근무 기록, 프리셋, 목표 금액이 영구 삭제됩니다. 계속할까요?",
        onDismissRequest = { showResetConfirm = false }
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton(text = "취소", onClick = { showResetConfirm = false }, modifier = Modifier.weight(1f))
            TextButton(
                text = "초기화",
                onClick = {
                    onResetAllData()
                    showResetConfirm = false
                    onNotify("모든 데이터가 초기화되었습니다.")
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

@Composable
private fun PreferenceStartIcon(
    icon: ImageVector,
    accentColor: Color
) {
    MiuixGlassIcon(
        icon = icon,
        contentDescription = null,
        accentColor = accentColor,
        modifier = Modifier.padding(end = 6.dp),
        size = 38.dp,
        iconSize = 18.dp,
        cornerRadius = 11.dp
    )
}
