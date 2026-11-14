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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.albatora.app.domain.PayrollCalculator
import com.albatora.app.model.JobKind
import com.albatora.app.model.WorkRecord
import com.albatora.app.ui.theme.LocalAppBackdrop
import com.albatora.app.ui.theme.LocalIsDarkTheme
import com.albatora.app.viewmodel.AlbaToraViewModel
import kotlinx.coroutines.launch
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
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDate

@Serializable
sealed interface AppRoute : NavKey {
    @Serializable
    data object Main : AppRoute

    @Serializable
    data class AddEditRegularJob(val jobId: String? = null) : AppRoute

    @Serializable
    data class QuickAddShortTerm(val recordId: String? = null, val initialDateIso: String? = null) : AppRoute
}

@Composable
fun MainScaffoldScreen(
    viewModel: AlbaToraViewModel
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

    val surfaceBg = MiuixTheme.colorScheme.surface
    val primaryAmbient = MiuixTheme.colorScheme.primary.copy(alpha = 0.08f)
    val secondaryAmbient = MiuixTheme.colorScheme.secondary.copy(alpha = 0.06f)

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
                .background(
                    Brush.verticalGradient(
                        colors = listOf(surfaceBg, primaryAmbient, secondaryAmbient, surfaceBg)
                    )
                )
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
                        onOpenAddRegularJob = { jobId ->
                            backStack.add(AppRoute.AddEditRegularJob(jobId))
                        },
                        onOpenQuickShortTerm = { recId, dateIso ->
                            backStack.add(AppRoute.QuickAddShortTerm(recId, dateIso))
                        }
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
    onOpenAddRegularJob: (String?) -> Unit,
    onOpenQuickShortTerm: (String?, String?) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val liveDashboard by viewModel.liveDashboard.collectAsStateWithLifecycle()
    val isDark = LocalIsDarkTheme.current

    var selectedTab by remember { mutableIntStateOf(0) } // 0=홈, 1=달력, 2=기록, 3=계산기, 4=더보기
    var recordsOpenUnpaid by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val notify: (String) -> Unit = { msg ->
        coroutineScope.launch {
            snackbarHostState.showSnackbar(msg)
        }
    }

    val unpaidCount = remember(state.records) {
        PayrollCalculator.calculateGlobalUnpaidSummary(state).second.size
    }

    val scrollBehavior = MiuixScrollBehavior(state = rememberTopAppBarState())
    val useShader = isRuntimeShaderSupported()
    val barOverlayColor = MiuixTheme.colorScheme.surface.copy(alpha = if (useShader) 0.72f else 0.96f)

    val tabTitles = listOf("알바토라", "근무 달력", "정산 · 명세서", "급여 계산기", "더보기")

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(state = snackbarHostState) },
        topBar = {
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
                        if (selectedTab == 0 || selectedTab == 1 || selectedTab == 2) {
                            IconButton(
                                onClick = {
                                    onOpenQuickShortTerm(null, PayrollCalculator.formatDate(LocalDate.now()))
                                }
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.AddCircle,
                                    contentDescription = "단기 10초 퀵 등록",
                                    tint = MiuixTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            val bottomBlurMod = if (useShader) {
                Modifier.progressiveTextureBlur(
                    backdrop = barBackdrop,
                    shape = RectangleShape,
                    blurRadius = 16f,
                    gradient = ProgressiveBlur.Bottom,
                    colors = BlurDefaults.blurColors(
                        blendColors = listOf(BlendColorEntry(barOverlayColor, BlurBlendMode.SrcOver))
                    )
                )
            } else {
                Modifier.background(barOverlayColor)
            }
            Box(modifier = bottomBlurMod) {
                NavigationBar(
                    color = Color.Transparent,
                    mode = NavigationBarDisplayMode.IconAndText
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = MiuixIcons.Regular.Home,
                        label = "홈",
                        badge = {
                            if (liveDashboard.activeShifts.isNotEmpty()) {
                                Badge()
                            }
                        }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = MiuixIcons.Regular.Months,
                        label = "달력"
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = {
                            recordsOpenUnpaid = false
                            selectedTab = 2
                        },
                        icon = MiuixIcons.Regular.Notes,
                        label = "기록",
                        badge = {
                            if (unpaidCount > 0) {
                                Badge { Text(unpaidCount.toString()) }
                            }
                        }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = MiuixIcons.Regular.Tune,
                        label = "계산기"
                    )
                    NavigationBarItem(
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        icon = MiuixIcons.Regular.Settings,
                        label = "더보기"
                    )
                }
            }
        }
    ) { paddingValues ->
        val contentBackdropModifier = Modifier
            .fillMaxSize()
            .layerBackdrop(barBackdrop)

        Box(
            modifier = contentBackdropModifier.padding(top = paddingValues.calculateTopPadding())
        ) {
            when (selectedTab) {
                0 -> HomeScreen(
                    state = state,
                    liveDashboard = liveDashboard,
                    scrollBehavior = scrollBehavior,
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
                    onStartStopwatch = { title, kind, payType, unitAmt, jobId, colorHex, plannedMins, breakMins, breakPayType, allowance ->
                        viewModel.startStopwatch(
                            title = title,
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
                        notify("'$title' 스톱워치 출근이 시작되었습니다!")
                    },
                    onSelectTangibleItem = viewModel::selectTangibleItem,
                    onUpsertTangibleItem = viewModel::upsertTangibleItem,
                    onDeleteTangibleItem = viewModel::deleteTangibleItem,
                    onUpsertWishlistGoal = viewModel::upsertWishlistGoal,
                    onDeleteWishlistGoal = viewModel::deleteWishlistGoal
                )
                1 -> CalendarScreen(
                    state = state,
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
                3 -> CalculatorScreen(
                    defaultMinWage = state.settings.defaultMinWage,
                    scrollBehavior = scrollBehavior
                )
                4 -> MoreSettingsScreen(
                    state = state,
                    scrollBehavior = scrollBehavior,
                    onUpdateTheme = viewModel::updateTheme,
                    onUpdateMonet = viewModel::updateMonet,
                    onUpdateOngoingNotification = viewModel::updateOngoingNotification,
                    onUpdateDefaultMinWage = viewModel::updateDefaultMinWage,
                    onDeleteTemplate = viewModel::deleteTemplate,
                    onExportJson = viewModel::exportBackupJson,
                    onImportJson = viewModel::importBackupJson,
                    onResetAllData = viewModel::resetAllData,
                    onAddRegularJob = { onOpenAddRegularJob(null) },
                    onAddShortTerm = {
                        onOpenQuickShortTerm(null, PayrollCalculator.formatDate(LocalDate.now()))
                    },
                    onNotify = notify
                )
            }
        }
    }
}
