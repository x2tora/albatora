package com.albatora.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.albatora.app.domain.PayrollCalculator
import com.albatora.app.ui.components.AppUpdateDialog
import com.albatora.app.ui.components.FloatingLiquidGlassNavBar
import com.albatora.app.ui.components.StackedToastHost
import com.albatora.app.ui.components.StackedToastState
import com.albatora.app.ui.components.rememberStackedToastState
import com.albatora.app.ui.theme.LocalAppBackdrop
import com.albatora.app.viewmodel.AlbaToraViewModel
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDate

@Serializable
sealed interface AppRoute : NavKey {
    @Serializable
    data object Main : AppRoute

    @Serializable
    data object Settings : AppRoute

    @Serializable
    data class AddEditRegularJob(val jobId: String? = null) : AppRoute

    @Serializable
    data class QuickAddShortTerm(val recordId: String? = null, val initialDateIso: String? = null) : AppRoute
}

@Composable
fun MainScaffoldScreen(
    viewModel: AlbaToraViewModel,
    launchActionNonce: Int = 0,
    launchTargetTab: Int = 0,
    launchOpenStopwatch: Boolean = false
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val liveDashboard by viewModel.liveDashboard.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack<AppRoute>(AppRoute.Main)

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.stateHolder.syncForegroundService()
        }
    }

    LaunchedEffect(liveDashboard.activeShifts.size, state.settings.ongoingNotificationEnabled) {
        if (state.settings.ongoingNotificationEnabled &&
            liveDashboard.activeShifts.isNotEmpty() &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            val hasPerm = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!hasPerm) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val toastState = rememberStackedToastState()
    val surfaceBg = MiuixTheme.colorScheme.surface

    // Keep card/icon glass blur strictly separate from scrollable content barBackdrop to avoid cyclic RenderNode graphs.
    val ambientBackdrop = rememberLayerBackdrop {
        drawRect(surfaceBg)
        drawContent()
    }
    val barBackdrop = rememberLayerBackdrop {
        drawRect(surfaceBg)
        drawContent()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(ambientBackdrop)
                .background(surfaceBg)
        )

        CompositionLocalProvider(LocalAppBackdrop provides ambientBackdrop) {
            NavDisplay(
                backStack = backStack,
                onBack = {
                    if (backStack.size > 1) {
                        backStack.removeAt(backStack.lastIndex)
                    }
                },
                transition = NavTransitions.MiuixDefault
            ) {
                entry<AppRoute.Main> {
                    MainTabsHost(
                        viewModel = viewModel,
                        barBackdrop = barBackdrop,
                        toastState = toastState,
                        launchActionNonce = launchActionNonce,
                        launchTargetTab = launchTargetTab,
                        launchOpenStopwatch = launchOpenStopwatch,
                        onOpenAddRegularJob = { jobId ->
                            backStack.add(AppRoute.AddEditRegularJob(jobId))
                        },
                        onOpenQuickShortTerm = { recId, dateIso ->
                            backStack.add(AppRoute.QuickAddShortTerm(recId, dateIso))
                        },
                        onOpenSettings = {
                            backStack.add(AppRoute.Settings)
                        }
                    )
                }

                entry<AppRoute.Settings> {
                    SettingsScreenHost(
                        viewModel = viewModel,
                        toastState = toastState,
                        onBack = { backStack.removeAt(backStack.lastIndex) }
                    )
                }

                entry<AppRoute.AddEditRegularJob> { route ->
                    val existing = route.jobId?.let { id -> state.jobs.find { it.id == id } }
                    AddEditRegularJobScreen(
                        existingJob = existing,
                        defaultMinWage = state.settings.defaultMinWage,
                        onBack = { backStack.removeAt(backStack.lastIndex) },
                        onSave = { job ->
                            viewModel.upsertJob(job)
                            backStack.removeAt(backStack.lastIndex)
                        },
                        onDelete = { id ->
                            viewModel.deleteJob(id, deleteRecords = false)
                            backStack.removeAt(backStack.lastIndex)
                        }
                    )
                }

                entry<AppRoute.QuickAddShortTerm> { route ->
                    val existing = route.recordId?.let { id -> state.records.find { it.id == id } }
                    QuickAddShortTermScreen(
                        existingRecord = existing,
                        initialDateIso = route.initialDateIso,
                        templates = state.templates,
                        defaultMinWage = state.settings.defaultMinWage,
                        onBack = { backStack.removeAt(backStack.lastIndex) },
                        onSave = { rec, optionalTemplate ->
                            viewModel.upsertRecord(rec)
                            if (optionalTemplate != null) {
                                viewModel.upsertTemplate(optionalTemplate)
                            }
                            backStack.removeAt(backStack.lastIndex)
                        },
                        onDelete = { id ->
                            viewModel.deleteRecord(id)
                            backStack.removeAt(backStack.lastIndex)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MainTabsHost(
    viewModel: AlbaToraViewModel,
    barBackdrop: LayerBackdrop,
    toastState: StackedToastState,
    launchActionNonce: Int,
    launchTargetTab: Int,
    launchOpenStopwatch: Boolean,
    onOpenAddRegularJob: (String?) -> Unit,
    onOpenQuickShortTerm: (String?, String?) -> Unit,
    onOpenSettings: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val liveDashboard by viewModel.liveDashboard.collectAsStateWithLifecycle()
    val updateUiState by viewModel.updateUiState.collectAsStateWithLifecycle()
    val surfaceBg = MiuixTheme.colorScheme.surface

    var selectedTab by remember { mutableIntStateOf(0) } // 0=홈, 1=달력, 2=기록, 3=계산기
    var calendarSelectedDate by remember { mutableStateOf(LocalDate.now()) }
    var recordsOpenUnpaid by remember { mutableStateOf(false) }
    var isFullscreenFocus by remember { mutableStateOf(false) }
    var showHomeQuickMenu by remember { mutableStateOf(false) }
    var openStopwatchTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(launchActionNonce) {
        if (launchActionNonce > 0) {
            selectedTab = launchTargetTab.coerceIn(0, 3)
            if (launchOpenStopwatch) {
                selectedTab = 0
                openStopwatchTrigger++
            }
        }
    }

    LaunchedEffect(liveDashboard.activeShifts.isEmpty()) {
        if (liveDashboard.activeShifts.isEmpty()) {
            isFullscreenFocus = false
        }
    }

    val notify: (String) -> Unit = { msg ->
        toastState.show(msg)
    }

    val unpaidCount = remember(state.records) {
        PayrollCalculator.calculateGlobalUnpaidSummary(state).second.size
    }

    val scrollBehavior = MiuixScrollBehavior(state = rememberTopAppBarState())
    val useShader = isRuntimeShaderSupported()
    val barOverlayColor = MiuixTheme.colorScheme.surface.copy(alpha = if (useShader) 0.72f else 0.96f)
    val showChrome = !(isFullscreenFocus && selectedTab == 0 && liveDashboard.activeShifts.isNotEmpty())

    val tabTitles = listOf("알바토라", "근무 달력", "정산 · 명세서", "급여 계산기")

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            if (showChrome) {
                val topBlurMod = if (useShader) {
                    Modifier.progressiveTextureBlur(
                        backdrop = barBackdrop,
                        shape = RectangleShape,
                        blurRadius = 16f,
                        gradient = ProgressiveBlur.Top,
                        colors = BlurDefaults.blurColors(
                            blendColors = listOf(BlendColorEntry(barOverlayColor, BlurBlendMode.SrcOver))
                        )
                    )
                } else {
                    Modifier.background(barOverlayColor)
                }
                Box(modifier = topBlurMod) {
                    SmallTopAppBar(
                        title = tabTitles[selectedTab],
                        color = Color.Transparent,
                        scrollBehavior = scrollBehavior,
                        actions = {
                            IconButton(onClick = onOpenSettings) {
                                Icon(
                                    imageVector = MiuixIcons.Regular.Settings,
                                    contentDescription = "더보기 및 설정",
                                    tint = MiuixTheme.colorScheme.onSurface
                                )
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            // ① Capture layer ONLY: surface background + scrolling pages (no glass dock inside!)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(barBackdrop)
                    .background(surfaceBg)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .let {
                            if (showChrome) {
                                it.padding(top = paddingValues.calculateTopPadding())
                            } else {
                                it
                            }
                        }
                ) {
            when (selectedTab) {
                0 -> HomeScreen(
                    state = state,
                    liveDashboard = liveDashboard,
                    scrollBehavior = scrollBehavior,
                    isFullscreenFocus = isFullscreenFocus,
                    onToggleFullscreenFocus = { isFullscreenFocus = it },
                    openStopwatchTrigger = openStopwatchTrigger,
                    onAddRegularJob = { onOpenAddRegularJob(null) },
                    onEditRegularJob = { jobId -> onOpenAddRegularJob(jobId) },
                    onQuickAddShortTerm = {
                        onOpenQuickShortTerm(null, PayrollCalculator.formatDate(LocalDate.now()))
                    },
                    onOpenUnpaidRecords = {
                        recordsOpenUnpaid = true
                        selectedTab = 2
                    },
                    onToggleBreak = viewModel::toggleBreak,
                    onClockOut = viewModel::clockOut,
                    onStartStopwatch = { title, emoji, kind, payType, unitAmt, jobId, colorHex, plannedMins, breakMins, breakPayType, allowance ->
                        viewModel.startStopwatch(
                            title = title,
                            emoji = emoji,
                            kind = kind,
                            payType = payType,
                            unitAmount = unitAmt,
                            jobId = jobId,
                            colorHex = colorHex,
                            plannedDurationMinutes = plannedMins,
                            unpaidBreakMinutes = breakMins,
                            breakPayType = breakPayType,
                            allowance = allowance
                        )
                        notify("'$title' 스톱워치 출근이 시작되었습니다")
                    },
                    onSelectTangibleItem = viewModel::selectTangibleItem,
                    onUpsertTangibleItem = viewModel::upsertTangibleItem,
                    onDeleteTangibleItem = viewModel::deleteTangibleItem,
                    onUpsertWishlistGoal = viewModel::upsertWishlistGoal,
                    onDeleteWishlistGoal = viewModel::deleteWishlistGoal
                )
                1 -> CalendarScreen(
                    state = state,
                    selectedDate = calendarSelectedDate,
                    onSelectedDateChange = { calendarSelectedDate = it },
                    scrollBehavior = scrollBehavior,
                    onQuickAddShortTermForDate = { dateIso ->
                        onOpenQuickShortTerm(null, dateIso)
                    },
                    onEditRecord = { rec ->
                        if (rec.id.startsWith("cal_") || rec.id.startsWith("virtual_")) {
                            // Virtual scheduled slot -> materialize as editable record
                            val realized = rec.copy(
                                id = java.util.UUID.randomUUID().toString(),
                                isScheduledOverride = true
                            )
                            viewModel.upsertRecord(realized)
                            onOpenQuickShortTerm(realized.id, realized.date)
                        } else {
                            onOpenQuickShortTerm(rec.id, rec.date)
                        }
                    },
                    onTogglePaymentStatus = viewModel::togglePaymentStatus,
                    onExtendOneHour = viewModel::extendOneHour,
                    onMarkEarlyLeave = { viewModel.markEarlyLeave(it, 60) },
                    onMarkAbsent = viewModel::markAbsent,
                    onAddSubstituteFromJob = viewModel::addSubstituteFromJob,
                    onStampTemplateOnDate = viewModel::stampTemplateOnDate,
                    onNotify = notify
                )
                2 -> RecordsScreen(
                    state = state,
                    initialOnlyUnpaid = recordsOpenUnpaid,
                    scrollBehavior = scrollBehavior,
                    onEditRecord = { rec ->
                        if (rec.isScheduledOverride || !rec.id.startsWith("virtual_")) {
                            onOpenQuickShortTerm(rec.id, rec.date)
                        } else {
                            val realized = rec.copy(
                                id = java.util.UUID.randomUUID().toString(),
                                isScheduledOverride = true
                            )
                            viewModel.upsertRecord(realized)
                            onOpenQuickShortTerm(realized.id, realized.date)
                        }
                    },
                    onTogglePaymentStatus = viewModel::togglePaymentStatus,
                    onAddShortTerm = {
                        onOpenQuickShortTerm(null, PayrollCalculator.formatDate(LocalDate.now()))
                    },
                    onNotify = notify
                )
                else -> CalculatorScreen(
                    defaultMinWage = state.settings.defaultMinWage,
                    scrollBehavior = scrollBehavior
                )
            }
            }
            }

            // ② Floating overlay layer (strictly OUTSIDE layerBackdrop capture box)
            if (showChrome) {
                StackedToastHost(
                    state = toastState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 92.dp)
                )

                FloatingLiquidGlassNavBar(
                    backdrop = barBackdrop,
                    selectedTab = selectedTab,
                    hasActiveShift = liveDashboard.activeShifts.isNotEmpty(),
                    unpaidCount = unpaidCount,
                    onSelectTab = { tab ->
                        if (tab == 2) recordsOpenUnpaid = false
                        selectedTab = tab
                    },
                    onCenterAddClick = { showHomeQuickMenu = true },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                )
            } else {
                StackedToastHost(
                    state = toastState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                )
            }

            // Mount Miuix OverlayDialogs inside Scaffold scope so PopupHost is always active
            OverlayDialog(
                show = showHomeQuickMenu,
                title = "빠른 등록 · 즉시 출근",
                onDismissRequest = { showHomeQuickMenu = false }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        text = "정규/고정 알바 추가",
                        onClick = {
                            showHomeQuickMenu = false
                            onOpenAddRegularJob(null)
                        },
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        text = "단기/일용직 10초 등록",
                        onClick = {
                            showHomeQuickMenu = false
                            val prefillDate = if (selectedTab == 1) calendarSelectedDate else LocalDate.now()
                            onOpenQuickShortTerm(null, PayrollCalculator.formatDate(prefillDate))
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        text = "즉시 출근 스톱워치 시작",
                        onClick = {
                            showHomeQuickMenu = false
                            selectedTab = 0
                            openStopwatchTrigger++
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            AppUpdateDialog(
                uiState = updateUiState,
                onDismiss = viewModel::dismissUpdateDialog,
                onStartDownload = viewModel::startUpdateDownloadAndInstall,
                onRetryInstall = viewModel::retryApkInstall,
                onOpenPermissionSettings = viewModel::openInstallUnknownSourcesSettings
            )
        }
    }
}

@Composable
private fun SettingsScreenHost(
    viewModel: AlbaToraViewModel,
    toastState: StackedToastState,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val updateUiState by viewModel.updateUiState.collectAsStateWithLifecycle()
    val scrollBehavior = MiuixScrollBehavior(state = rememberTopAppBarState())
    val notify: (String) -> Unit = { msg -> toastState.show(msg) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            SmallTopAppBar(
                title = "더보기 · 설정",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "뒤로가기"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            MoreSettingsScreen(
                state = state,
                scrollBehavior = scrollBehavior,
                updateUiState = updateUiState,
                onUpdateTheme = viewModel::updateTheme,
                onUpdateThemeColorStyle = viewModel::updateThemeColorStyle,
                onUpdateCustomKeyColor = viewModel::updateCustomKeyColor,
                onUpdateOngoingNotification = viewModel::updateOngoingNotification,
                onUpdateDefaultMinWage = viewModel::updateDefaultMinWage,
                onDeleteTemplate = viewModel::deleteTemplate,
                onExportJson = viewModel::exportBackupJson,
                onImportJson = viewModel::importBackupJson,
                onResetAllData = viewModel::resetAllData,
                onCheckForUpdate = { viewModel.checkForUpdate(manual = true, onFeedback = notify) },
                onNotify = notify
            )

            StackedToastHost(
                state = toastState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            )

            AppUpdateDialog(
                uiState = updateUiState,
                onDismiss = viewModel::dismissUpdateDialog,
                onStartDownload = viewModel::startUpdateDownloadAndInstall,
                onRetryInstall = viewModel::retryApkInstall,
                onOpenPermissionSettings = viewModel::openInstallUnknownSourcesSettings
            )
        }
    }
}
