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
import androidx.compose.foundation.lazy.LazyRow
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
import com.albatora.app.data.AutoBackupManager
import com.albatora.app.domain.PayrollCalculator
import com.albatora.app.model.*
import com.albatora.app.service.AutoDndController
import com.albatora.app.service.BatteryOptimizationHelper
import com.albatora.app.service.LiveUpdateNotificationHelper
import com.albatora.app.ui.components.AppUpdateUiState
import com.albatora.app.ui.components.ConfirmActionDialog
import com.albatora.app.ui.components.MiuixColorPickerDialog
import com.albatora.app.ui.components.MiuixGlassIcon
import com.albatora.app.ui.components.SquirclePillBadge
import com.albatora.app.ui.theme.AlbaMotion
import com.albatora.app.ui.components.formatRgbHex
import com.albatora.app.ui.theme.JobAccentPalette
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
import java.util.UUID

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
    onUpdatePreShiftReminder: (Boolean) -> Unit,
    onUpdatePreShiftReminderMinutes: (Int) -> Unit,
    onUpdatePostShiftReminder: (Boolean) -> Unit,
    onUpdatePaydayReminder: (Boolean) -> Unit,
    onUpdateDefaultMinWage: (Long) -> Unit,
    onUpdateUserPersona: (UserPersona) -> Unit,
    onReopenOnboarding: () -> Unit,
    onUpsertTemplate: (ShortTermTemplate) -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onExportJson: () -> String,
    onPreviewBackupJson: (String) -> Result<AlbaToraDataState>,
    onImportJson: (String) -> Result<Unit>,
    onMergeJson: (String) -> Result<Unit> = { Result.success(Unit) },
    onUpdateAutoBackupTreeUri: (String?) -> Unit = {},
    onRunAutoBackupNow: () -> Result<String> = { Result.failure(IllegalStateException()) },
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
    var pendingDndAutoEnable by remember { mutableStateOf(false) }
    var pendingImportBackup by remember { mutableStateOf<Pair<String, AlbaToraDataState>?>(null) }
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
                if (pendingDndAutoEnable) {
                    pendingDndAutoEnable = false
                    if (hasDndAccess) {
                        onUpdateAutoDnd(true)
                        onNotify("방해금지 권한이 허용되어 자동 방해금지가 켜졌습니다.")
                    }
                }
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
                    onPreviewBackupJson(jsonText).onSuccess { parsed ->
                        pendingImportBackup = jsonText to parsed
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

    val backupTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            AutoBackupManager.persistTreePermission(context, treeUri)
            onUpdateAutoBackupTreeUri(treeUri.toString())
            onRunAutoBackupNow().onSuccess { fileName ->
                onNotify("백업 폴더가 연결되었고 '$fileName'을 저장했습니다")
            }.onFailure {
                onNotify("백업 폴더가 연결되었습니다")
            }
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
                    summary = colorStyle.label,
                    items = colorStyles.map { it.label },
                    selectedIndex = colorStyles.indexOf(colorStyle),
                    onSelectedIndexChange = { index ->
                        val chosen = colorStyles[index]
                        onUpdateThemeColorStyle(chosen)
                        onNotify("테마 컬러가 '${chosen.label}'으로 적용되었습니다")
                    },
                    startAction = {
                        PreferenceStartIcon(MiuixIcons.Favorites, Color(0xFF7C5CFF))
                    }
                )
                AnimatedVisibility(
                    visible = colorStyle == ThemeColorStyle.CUSTOM,
                    enter = AlbaMotion.expandEnter(),
                    exit = AlbaMotion.shrinkExit()
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
                    summary = state.settings.navBarStyle.label,
                    items = navStyles.map { it.label },
                    selectedIndex = navStyles.indexOf(state.settings.navBarStyle),
                    onSelectedIndexChange = { index ->
                        val chosen = navStyles[index]
                        onUpdateNavBarStyle(chosen)
                        onNotify("인터페이스 스타일이 '${chosen.label}'으로 적용되었습니다")
                    },
                    startAction = {
                        PreferenceStartIcon(MiuixIcons.Tune, Color(0xFF14B8A6))
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SwitchPreference(
                    title = "대화면 듀얼 칼럼 보기",
                    summary = "태블릿·폴더블에서 좌우 2단 분할 표시",
                    checked = state.settings.wideDualLayoutEnabled,
                    onCheckedChange = { enabled ->
                        onUpdateWideDualLayout(enabled)
                        onNotify(
                            if (enabled) "넓은 화면에서 듀얼 칼럼 레이아웃이 활성화되었습니다"
                            else "대화면에서도 중앙 단일 보기로 표시됩니다"
                        )
                    },
                    startAction = {
                        PreferenceStartIcon(MiuixIcons.ScreenMirroring, Color(0xFF3B82F6))
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ArrowPreference(
                    title = "바탕화면 위젯 꾸미기",
                    summary = "${state.settings.widgetConfig.themeStyle.label} · 투명도 ${state.settings.widgetConfig.opacity.label}",
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
                    checked = state.settings.ongoingNotificationEnabled,
                    onCheckedChange = onUpdateOngoingNotification,
                    startAction = {
                        PreferenceStartIcon(MiuixIcons.Alarm, Color(0xFFF97316))
                    }
                )
                if (android.os.Build.VERSION.SDK_INT >= 36) {
                    val canPromoted = LiveUpdateNotificationHelper.canPostPromoted(context)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ArrowPreference(
                        title = "Live Updates 상태바 칩",
                        summary = if (canPromoted) {
                            "상태바 칩 · Now Bar 승격 허용됨"
                        } else {
                            "시스템 설정에서 실시간 정보 승격 허용 필요"
                        },
                        onClick = { LiveUpdateNotificationHelper.openPromotionSettings(context) },
                        startAction = {
                            PreferenceStartIcon(
                                MiuixIcons.Stopwatch,
                                if (canPromoted) Color(0xFF10B981) else Color(0xFFF59E0B)
                            )
                        }
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SwitchPreference(
                    title = "알바 중 자동 방해금지",
                    summary = if (!hasDndAccess) "권한 허용 필요" else null,
                    checked = state.settings.autoDndEnabled && hasDndAccess,
                    onCheckedChange = { enabled ->
                        if (enabled && !AutoDndController.hasPolicyAccess(context)) {
                            showDndPermissionDialog = true
                        } else {
                            onUpdateAutoDnd(enabled)
                            onNotify(if (enabled) "알바 중 자동 방해금지가 켜졌습니다" else "알바 중 자동 방해금지가 꺼졌습니다")
                        }
                    },
                    startAction = {
                        PreferenceStartIcon(MiuixIcons.Timer, Color(0xFF6366F1))
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SwitchPreference(
                    title = "출근 전 스마트 알림",
                    summary = if (state.settings.preShiftReminderEnabled) {
                        "예정 근무 ${state.settings.preShiftReminderMinutes}분 전 알림 + 바로 시작 버튼"
                    } else {
                        "예정 근무 시작 전 알림"
                    },
                    checked = state.settings.preShiftReminderEnabled,
                    onCheckedChange = { enabled ->
                        onUpdatePreShiftReminder(enabled)
                        onNotify(if (enabled) "출근 전 스마트 알림이 켜졌습니다" else "출근 전 스마트 알림이 꺼졌습니다")
                    },
                    startAction = {
                        PreferenceStartIcon(MiuixIcons.Alarm, Color(0xFF0EA5E9))
                    }
                )
                if (state.settings.preShiftReminderEnabled) {
                    val leadOptions = listOf(10, 15, 30)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    OverlayDropdownPreference(
                        title = "출근 알림 시간",
                        items = leadOptions.map { "${it}분 전" },
                        selectedIndex = leadOptions.indexOf(state.settings.preShiftReminderMinutes).coerceAtLeast(1),
                        onSelectedIndexChange = { idx ->
                            onUpdatePreShiftReminderMinutes(leadOptions[idx])
                        }
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SwitchPreference(
                    title = "퇴근 후 기록 확정 알림",
                    summary = "예정 퇴근 15분 후 원탭 정상 근무 확정",
                    checked = state.settings.postShiftReminderEnabled,
                    onCheckedChange = { enabled ->
                        onUpdatePostShiftReminder(enabled)
                        onNotify(if (enabled) "퇴근 후 확정 알림이 켜졌습니다" else "퇴근 후 확정 알림이 꺼졌습니다")
                    },
                    startAction = {
                        PreferenceStartIcon(MiuixIcons.Ok, Color(0xFF10B981))
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SwitchPreference(
                    title = "월급날 입금 확인 알림",
                    summary = "월급날 오전 10시 입금 확인 및 완료 처리",
                    checked = state.settings.paydayReminderEnabled,
                    onCheckedChange = { enabled ->
                        onUpdatePaydayReminder(enabled)
                        onNotify(if (enabled) "월급날 알림이 켜졌습니다" else "월급날 알림이 꺼졌습니다")
                    },
                    startAction = {
                        PreferenceStartIcon(MiuixIcons.BankCards, Color(0xFFF59E0B))
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ArrowPreference(
                    title = "백그라운드 실행 예외",
                    summary = if (hasBatteryExemption) "허용됨" else "미허용 · 배터리 최적화 제외 필요",
                    onClick = {
                        if (hasBatteryExemption) {
                            onNotify("이미 백그라운드 항상 실행이 허용되어 있습니다")
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
                    summary = PayrollCalculator.formatWon(state.settings.defaultMinWage),
                    onClick = { showMinWageDialog = true },
                    startAction = {
                        PreferenceStartIcon(MiuixIcons.BankCards, Color(0xFF10B981))
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ArrowPreference(
                    title = "단기 알바 프리셋 관리",
                    summary = "${state.templates.size}개",
                    onClick = { showTemplatesDialog = true },
                    startAction = {
                        PreferenceStartIcon(MiuixIcons.Notes, Color(0xFF8B5CF6))
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                OverlayDropdownPreference(
                    title = "근무 스타일 (페르소나)",
                    summary = state.settings.userPersona.subtitle,
                    items = UserPersona.entries.map { it.label },
                    selectedIndex = UserPersona.entries.indexOf(state.settings.userPersona).coerceAtLeast(0),
                    onSelectedIndexChange = { idx ->
                        val chosen = UserPersona.entries[idx]
                        onUpdateUserPersona(chosen)
                        onNotify("근무 스타일이 '${chosen.label}'으로 변경되었습니다")
                    },
                    startAction = {
                        PreferenceStartIcon(MiuixIcons.Store, Color(0xFF3482FF))
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ArrowPreference(
                    title = "초기 온보딩 다시 보기",
                    summary = "근무 스타일 및 기본 설정 마법사 실행",
                    onClick = onReopenOnboarding,
                    startAction = {
                        PreferenceStartIcon(MiuixIcons.Stopwatch, Color(0xFF06B6D4))
                    }
                )
            }
        }

        item {
            SmallTitle(text = "백업 및 데이터")
            Card(modifier = Modifier.fillMaxWidth()) {
                ArrowPreference(
                    title = "JSON 백업 내보내기",
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
                    title = "JSON 백업 가져오기",
                    onClick = {
                        importFileLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                    startAction = {
                        PreferenceStartIcon(MiuixIcons.Import, Color(0xFF10B981))
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ArrowPreference(
                    title = "클립보드 백업 · 복원",
                    onClick = { showPasteRestoreDialog = true },
                    startAction = {
                        PreferenceStartIcon(MiuixIcons.Copy, Color(0xFF06B6D4))
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ArrowPreference(
                    title = "SAF 자동 백업 폴더",
                    summary = if (state.settings.autoBackupTreeUri != null) {
                        "연결됨 · 최근 백업 보관(최대 7개)"
                    } else {
                        "폴더를 선택해 기기 내 안전 백업"
                    },
                    onClick = { backupTreeLauncher.launch(null) },
                    startAction = {
                        PreferenceStartIcon(MiuixIcons.Backup, Color(0xFF8B5CF6))
                    }
                )
                if (state.settings.autoBackupTreeUri != null) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ArrowPreference(
                        title = "지금 자동 백업 실행",
                        summary = "연결된 폴더에 즉시 JSON 스냅샷 저장",
                        onClick = {
                            onRunAutoBackupNow().onSuccess { fileName ->
                                onNotify("백업 완료: $fileName")
                            }.onFailure {
                                onNotify("자동 백업 실패: ${it.message}")
                            }
                        },
                        startAction = {
                            PreferenceStartIcon(MiuixIcons.Ok, Color(0xFF10B981))
                        }
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ArrowPreference(
                    title = "모든 데이터 초기화",
                    summary = "테마·기본 설정은 유지됩니다",
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
                        updateUiState.isChecking -> "확인 중"
                        !updateUiState.lastStatusNote.isNullOrBlank() -> "v${BuildConfig.VERSION_NAME} · ${updateUiState.lastStatusNote}"
                        else -> "v${BuildConfig.VERSION_NAME}"
                    },
                    onClick = onCheckForUpdate,
                    startAction = {
                        PreferenceStartIcon(MiuixIcons.Store, Color(0xFF3B82F6))
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                BasicComponent(
                    title = "AlbaTora",
                    summary = "v${BuildConfig.VERSION_NAME}",
                    startAction = {
                        PreferenceStartIcon(MiuixIcons.Info, Color(0xFF64748B))
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
                pendingDndAutoEnable = true
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
            val parsedWage = wageText.toLongOrNull() ?: 0L
            TextField(
                value = wageText,
                onValueChange = { wageText = it.filter(Char::isDigit) },
                label = "시급${if (parsedWage > 0L) " · ${PayrollCalculator.formatWon(parsedWage)}" else " (원)"}",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    SquirclePillBadge(
                        text = "2026 최저 (10,320원)",
                        backgroundColor = MiuixTheme.colorScheme.primary.copy(alpha = 0.14f),
                        contentColor = MiuixTheme.colorScheme.primary,
                        onClick = { wageText = DEFAULT_2026_MIN_WAGE.toString() }
                    )
                }
                item {
                    SquirclePillBadge(
                        text = "+100원",
                        backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                        contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
                        onClick = { wageText = ((wageText.toLongOrNull() ?: DEFAULT_2026_MIN_WAGE) + 100L).toString() }
                    )
                }
                item {
                    SquirclePillBadge(
                        text = "+500원",
                        backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                        contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
                        onClick = { wageText = ((wageText.toLongOrNull() ?: DEFAULT_2026_MIN_WAGE) + 500L).toString() }
                    )
                }
            }
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

    // Saved short-term templates list & inline creator
    var pendingDeleteTemplate by remember { mutableStateOf<ShortTermTemplate?>(null) }
    var showAddTemplateForm by remember(showTemplatesDialog) { mutableStateOf(false) }
    var newTplName by remember(showTemplatesDialog, showAddTemplateForm) { mutableStateOf("") }
    var newTplAmount by remember(showTemplatesDialog, showAddTemplateForm) { mutableStateOf("140000") }
    var newTplPayType by remember(showTemplatesDialog, showAddTemplateForm) { mutableStateOf(PayType.DAILY) }
    OverlayDialog(
        show = showTemplatesDialog,
        title = "단기 알바 프리셋 관리",
        onDismissRequest = { showTemplatesDialog = false }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.templates.isEmpty()) {
                Text(
                    "저장된 프리셋이 없습니다. 아래 '+ 새 프리셋'으로 추가해 보세요",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            } else {
                state.templates.forEach { tpl ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
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

            AnimatedVisibility(
                visible = showAddTemplateForm,
                enter = AlbaMotion.expandEnter(),
                exit = AlbaMotion.shrinkExit()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider()
                    TextField(
                        value = newTplName,
                        onValueChange = { newTplName = it },
                        label = "프리셋 이름 (예: 물류 상하차)",
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SquirclePillBadge(
                            text = if (newTplPayType == PayType.DAILY) "일급 기준" else "시급 기준",
                            backgroundColor = MiuixTheme.colorScheme.secondaryContainer,
                            contentColor = MiuixTheme.colorScheme.onSecondaryContainer,
                            onClick = {
                                newTplPayType = if (newTplPayType == PayType.DAILY) PayType.HOURLY else PayType.DAILY
                                if (newTplPayType == PayType.HOURLY && newTplAmount == "140000") {
                                    newTplAmount = state.settings.defaultMinWage.toString()
                                } else if (newTplPayType == PayType.DAILY && newTplAmount == state.settings.defaultMinWage.toString()) {
                                    newTplAmount = "140000"
                                }
                            }
                        )
                        val parsedTplAmt = newTplAmount.toLongOrNull() ?: 0L
                        TextField(
                            value = newTplAmount,
                            onValueChange = { newTplAmount = it.filter(Char::isDigit) },
                            label = "금액${if (parsedTplAmt > 0L) " · ${PayrollCalculator.formatWon(parsedTplAmt)}" else " (원)"}",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    TextButton(
                        text = "새 프리셋 저장",
                        onClick = {
                            val clean = newTplName.trim()
                            val amt = newTplAmount.toLongOrNull() ?: 0L
                            if (clean.isEmpty() || amt <= 0L) {
                                onNotify("프리셋 이름과 금액을 입력해 주세요.")
                                return@TextButton
                            }
                            onUpsertTemplate(
                                ShortTermTemplate(
                                    id = UUID.randomUUID().toString(),
                                    name = clean,
                                    emoji = "timer",
                                    colorHex = JobAccentPalette.random().second,
                                    startMinute = 9 * 60,
                                    endMinute = 18 * 60,
                                    payType = newTplPayType,
                                    unitAmount = amt,
                                    breakMinutes = 60,
                                    breakPayType = BreakPayType.UNPAID,
                                    taxMode = TaxMode.DAILY_WORKER
                                )
                            )
                            showAddTemplateForm = false
                            onNotify("'$clean' 프리셋을 추가했습니다.")
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(
                    text = if (showAddTemplateForm) "입력 닫기" else "+ 새 프리셋",
                    onClick = { showAddTemplateForm = !showAddTemplateForm },
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "닫기",
                    onClick = { showTemplatesDialog = false },
                    modifier = Modifier.weight(1f)
                )
            }
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
                        onPreviewBackupJson(pasteJson).onSuccess { parsed ->
                            showPasteRestoreDialog = false
                            pendingImportBackup = pasteJson to parsed
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

    val importPair = pendingImportBackup
    if (importPair != null) {
        val (rawJsonToRestore, preview) = importPair
        OverlayDialog(
            show = true,
            title = "백업 데이터 복원 방식 선택",
            summary = "백업 내용:\n" +
                "• 정규/고정 알바 ${preview.jobs.size}개\n" +
                "• 근무 기록 ${preview.records.size}건\n" +
                "• 단기 프리셋 ${preview.templates.size}개\n" +
                "• 목표 ${preview.wishlistGoals.size}개",
            onDismissRequest = { pendingImportBackup = null }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    text = "현재 데이터와 병합 (유실 없이 추가)",
                    onClick = {
                        pendingImportBackup = null
                        onMergeJson(rawJsonToRestore).onSuccess {
                            onNotify("백업 데이터를 현재 데이터와 병합했습니다.")
                        }.onFailure {
                            onNotify("백업 병합 중 오류가 발생했습니다.")
                        }
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    text = "덮어쓰고 전체 복원",
                    onClick = {
                        pendingImportBackup = null
                        onImportJson(rawJsonToRestore).onSuccess {
                            onNotify("백업 데이터를 복원했습니다.")
                        }.onFailure {
                            onNotify("백업 복원 중 오류가 발생했습니다.")
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        color = MiuixTheme.colorScheme.error,
                        textColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    text = "취소",
                    onClick = { pendingImportBackup = null },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    ConfirmActionDialog(
        show = showResetConfirm,
        title = "전체 데이터 초기화",
        summary = "모든 알바와 근무 기록, 프리셋, 목표 금액이 영구 삭제됩니다. 계속할까요?",
        confirmText = "초기화",
        isDestructive = true,
        onDismiss = { showResetConfirm = false },
        onConfirm = {
            onResetAllData()
            showResetConfirm = false
            onNotify("모든 데이터가 초기화되었습니다.")
        }
    )
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
