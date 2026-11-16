package com.albatora.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.albatora.app.domain.LiveDashboardSnapshot
import com.albatora.app.domain.PayrollCalculator
import com.albatora.app.model.NavBarStyle
import com.albatora.app.model.ThemeColorStyle
import com.albatora.app.service.BatteryOptimizationHelper
import com.albatora.app.ui.components.AppUpdateDialog
import com.albatora.app.ui.components.FloatingLiquidGlassNavBar
import com.albatora.app.ui.components.MiuixBottomEdgeBlur
import com.albatora.app.ui.components.MiuixFloatingScrollTopBar
import com.albatora.app.ui.components.SquirclePillBadge
import com.albatora.app.ui.components.StackedToastHost
import com.albatora.app.ui.components.StackedToastState
import com.albatora.app.ui.components.rememberStackedToastState
import com.albatora.app.ui.theme.AlbaIconCatalog
import com.albatora.app.ui.theme.AlbaMotion
import com.albatora.app.ui.theme.LocalAppBackdrop
import com.albatora.app.ui.theme.LocalIsDarkTheme
import com.albatora.app.ui.theme.LocalLiquidGlassEnabled
import com.albatora.app.ui.theme.LocalWideDualLayoutEnabled
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
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.LocalDate
import java.util.Locale

@Serializable
sealed interface AppRoute : NavKey {
    @Serializable
    data object Main : AppRoute

    @Serializable
    data object Settings : AppRoute

    @Serializable
    data object WidgetStudio : AppRoute

    @Serializable
    data class AddEditRegularJob(val jobId: String? = null, val prefillHourlyWage: Long? = null) : AppRoute

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

        CompositionLocalProvider(
            LocalAppBackdrop provides ambientBackdrop,
            LocalLiquidGlassEnabled provides (state.settings.navBarStyle == NavBarStyle.LIQUID_GLASS),
            LocalWideDualLayoutEnabled provides state.settings.wideDualLayoutEnabled
        ) {
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
                        onOpenAddRegularJob = { jobId, prefillWage ->
                            backStack.add(AppRoute.AddEditRegularJob(jobId, prefillWage))
                        },
                        onOpenQuickShortTerm = { recId, dateIso ->
                            backStack.add(AppRoute.QuickAddShortTerm(recId, dateIso))
                        },
                        onOpenSettings = {
                            backStack.add(AppRoute.Settings)
                        },
                        onOpenWidgetStudio = {
                            backStack.add(AppRoute.WidgetStudio)
                        }
                    )
                }

                entry<AppRoute.Settings> {
                    SettingsScreenHost(
                        viewModel = viewModel,
                        toastState = toastState,
                        onOpenWidgetStudio = { backStack.add(AppRoute.WidgetStudio) },
                        onBack = { backStack.removeAt(backStack.lastIndex) }
                    )
                }

                entry<AppRoute.WidgetStudio> {
                    WidgetStudioScreen(
                        state = state,
                        onUpdateWidgetConfig = viewModel::updateWidgetConfig,
                        onBack = { backStack.removeAt(backStack.lastIndex) },
                        onNotify = { toastState.show(it) },
                        toastState = toastState
                    )
                }

                entry<AppRoute.AddEditRegularJob> { route ->
                    val existing = route.jobId?.let { id -> state.jobs.find { it.id == id } }
                    AddEditRegularJobScreen(
                        existingJob = existing,
                        defaultMinWage = state.settings.defaultMinWage,
                        prefillHourlyWage = route.prefillHourlyWage,
                        onBack = { backStack.removeAt(backStack.lastIndex) },
                        onSave = { job ->
                            viewModel.upsertJob(job)
                            backStack.removeAt(backStack.lastIndex)
                        },
                        onDelete = { id ->
                            viewModel.deleteJob(id, deleteRecords = false)
                            backStack.removeAt(backStack.lastIndex)
                        },
                        onNotify = { toastState.show(it) }
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
                        },
                        onNotify = { toastState.show(it) }
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
    onOpenAddRegularJob: (String?, Long?) -> Unit,
    onOpenQuickShortTerm: (String?, String?) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWidgetStudio: () -> Unit
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
    var isHomeScrolled by remember { mutableStateOf(false) }
    var scrollHomeToTopTrigger by remember { mutableIntStateOf(0) }
    var measuredTopBarHeightDp by remember { mutableStateOf(0.dp) }
    val hostContext = LocalContext.current
    val density = LocalDensity.current
    var lastBackPressedAtMs by remember { mutableLongStateOf(0L) }

    BackHandler {
        when {
            showHomeQuickMenu -> {
                showHomeQuickMenu = false
            }
            isFullscreenFocus && selectedTab == 0 -> {
                isFullscreenFocus = false
            }
            selectedTab != 0 -> {
                selectedTab = 0
                recordsOpenUnpaid = false
                lastBackPressedAtMs = 0L
            }
            else -> {
                val now = SystemClock.elapsedRealtime()
                if (now - lastBackPressedAtMs <= 2_000L) {
                    (hostContext as? Activity)?.finish()
                } else {
                    lastBackPressedAtMs = now
                    toastState.show("뒤로가기를 한 번 더 누르면 앱이 종료됩니다.")
                }
            }
        }
    }

    LaunchedEffect(launchActionNonce) {
        if (launchActionNonce > 0) {
            isFullscreenFocus = false
            selectedTab = launchTargetTab.coerceIn(0, 3)
            showHomeQuickMenu = false
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

    val unpaidCount = remember(state.records, state.jobs) {
        PayrollCalculator.calculateGlobalUnpaidSummary(state).second.size
    }

    val tabScrollBehaviors = List(4) { MiuixScrollBehavior(state = rememberTopAppBarState()) }
    val activeScrollBehavior = tabScrollBehaviors[selectedTab]
    val showChrome = !(isFullscreenFocus && selectedTab == 0 && liveDashboard.activeShifts.isNotEmpty())
    val defaultTopBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 66.dp
    val resolvedTopBarInset = if (measuredTopBarHeightDp > 0.dp) measuredTopBarHeightDp else defaultTopBarHeight

    LaunchedEffect(showChrome) {
        if (!showChrome) {
            showHomeQuickMenu = false
        }
    }

    val quickMenuMorphProgress = remember { Animatable(0f) }
    var isQuickMenuMounted by remember { mutableStateOf(false) }
    LaunchedEffect(showHomeQuickMenu) {
        if (showHomeQuickMenu) {
            isQuickMenuMounted = true
            quickMenuMorphProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = 185f
                )
            )
        } else {
            quickMenuMorphProgress.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = 0.84f,
                    stiffness = 210f
                )
            )
            isQuickMenuMounted = false
        }
    }

    val tabTitles = listOf("알바토라", "근무 달력", "정산 · 명세서", "급여 계산기")

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            // ① Capture layer ONLY: surface background + scrolling pages (no glass dock / top bar inside!)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(barBackdrop)
                    .background(surfaceBg)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            AlbaMotion.tabTransition(forward = targetState >= initialState, slideDivisor = 10)
                        },
                        label = "main_tab_transition"
                    ) { tab ->
                        when (tab) {
                            0 -> HomeScreen(
                                state = state,
                                liveDashboard = liveDashboard,
                                scrollBehavior = tabScrollBehaviors[0],
                                isFullscreenFocus = isFullscreenFocus,
                                onToggleFullscreenFocus = { isFullscreenFocus = it },
                                openStopwatchTrigger = openStopwatchTrigger,
                                onConsumeStopwatchTrigger = { openStopwatchTrigger = 0 },
                                onAddRegularJob = { onOpenAddRegularJob(null, null) },
                                onEditRegularJob = { jobId -> onOpenAddRegularJob(jobId, null) },
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
                                onDeleteWishlistGoal = viewModel::deleteWishlistGoal,
                                onArchiveWishlistGoal = { id, amt ->
                                    viewModel.archiveWishlistGoal(id, amt)
                                    notify("목표 달성을 아카이브에 보관했습니다")
                                },
                                onDeleteArchivedGoal = viewModel::deleteArchivedGoal,
                                topBarInset = resolvedTopBarInset,
                                scrollToTopTrigger = scrollHomeToTopTrigger,
                                onHomeScrollChange = { isHomeScrolled = it }
                            )
                            1 -> CalendarScreen(
                                state = state,
                                selectedDate = calendarSelectedDate,
                                onSelectedDateChange = { calendarSelectedDate = it },
                                scrollBehavior = tabScrollBehaviors[1],
                                topInset = resolvedTopBarInset,
                                onQuickAddShortTermForDate = { dateIso ->
                                    onOpenQuickShortTerm(null, dateIso)
                                },
                                onEditRecord = { rec ->
                                    if (rec.id.startsWith("cal_") || rec.id.startsWith("virtual_")) {
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
                                onUndoStampRecord = viewModel::deleteRecord,
                                onNotify = notify
                            )
                            2 -> RecordsScreen(
                                state = state,
                                initialOnlyUnpaid = recordsOpenUnpaid,
                                scrollBehavior = tabScrollBehaviors[2],
                                topInset = resolvedTopBarInset,
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
                                scrollBehavior = tabScrollBehaviors[3],
                                topInset = resolvedTopBarInset,
                                onCreateJobWithWage = { wage -> onOpenAddRegularJob(null, wage) }
                            )
                        }
                    }
                }
            }

            // ② Floating scroll-responsive top bar (background slides down cleanly when content scrolls)
            AnimatedVisibility(
                visible = showChrome,
                enter = slideInVertically(
                    animationSpec = AlbaMotion.OffsetSpring,
                    initialOffsetY = { -it }
                ) + fadeIn(tween(320)),
                exit = slideOutVertically(
                    animationSpec = AlbaMotion.ExitOffsetSpring,
                    targetOffsetY = { -it }
                ) + fadeOut(tween(240)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .onSizeChanged {
                        if (it.height > 0) {
                            val measured = with(density) { it.height.toDp() }
                            if (measured > 0.dp) measuredTopBarHeightDp = measured
                        }
                    }
            ) {
                MiuixFloatingScrollTopBar(
                    title = tabTitles[selectedTab],
                    scrollBehavior = activeScrollBehavior,
                    isScrolledOverride = if (selectedTab == 0) isHomeScrolled else null,
                    backdrop = barBackdrop,
                    maxContentWidth = if (state.settings.wideDualLayoutEnabled) Dp.Unspecified else 620.dp,
                    actions = {
                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Settings,
                                contentDescription = "더보기 및 설정",
                                tint = MiuixTheme.colorScheme.onSurface
                            )
                        }
                    }
                )
            }

            // ③ Quick-add dim scrim positioned BELOW the navigation dock so the lens on '+' stays crisp & lit
            if (isQuickMenuMounted) {
                val scrimAlpha = 0.44f * quickMenuMorphProgress.value.coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = scrimAlpha))
                        .pointerInput(showHomeQuickMenu) {
                            if (showHomeQuickMenu) {
                                detectTapGestures { showHomeQuickMenu = false }
                            }
                        }
                )
            }

            // ④ Bottom navigation bar (Miuix default or Floating Liquid Glass) & Toasts (strictly OUTSIDE layerBackdrop capture box)
            val isLiquidGlassNav = state.settings.navBarStyle == NavBarStyle.LIQUID_GLASS
            AnimatedVisibility(
                visible = showChrome,
                enter = slideInVertically(
                    animationSpec = AlbaMotion.OffsetSpring,
                    initialOffsetY = { it }
                ) + fadeIn(tween(320)),
                exit = slideOutVertically(
                    animationSpec = AlbaMotion.ExitOffsetSpring,
                    targetOffsetY = { it }
                ) + fadeOut(tween(240)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    MiuixBottomEdgeBlur(
                        backdrop = barBackdrop,
                        extraHeight = 72.dp,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )

                    if (isLiquidGlassNav) {
                        FloatingLiquidGlassNavBar(
                            backdrop = barBackdrop,
                            selectedTab = selectedTab,
                            isCenterAddSelected = showHomeQuickMenu,
                            hasActiveShift = liveDashboard.activeShifts.isNotEmpty(),
                            unpaidCount = unpaidCount,
                            onSelectTab = { tab ->
                                showHomeQuickMenu = false
                                if (tab == 2) recordsOpenUnpaid = false
                                selectedTab = tab
                            },
                            onCenterAddClick = { showHomeQuickMenu = !showHomeQuickMenu },
                            useLensTint = state.settings.resolvedColorStyle() != ThemeColorStyle.DEFAULT,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .widthIn(max = 384.dp)
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(MiuixTheme.colorScheme.surface)
                        ) {
                            HorizontalDivider(modifier = Modifier.align(Alignment.TopCenter))
                            NavigationBar(
                                showDivider = false,
                                defaultWindowInsetsPadding = true,
                                mode = NavigationBarDisplayMode.IconAndText,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .widthIn(max = 560.dp)
                                    .fillMaxWidth()
                            ) {
                            NavigationBarItem(
                                selected = !showHomeQuickMenu && selectedTab == 0,
                                onClick = {
                                    showHomeQuickMenu = false
                                    selectedTab = 0
                                },
                                icon = MiuixIcons.Regular.Home,
                                label = "홈",
                                badge = if (liveDashboard.activeShifts.isNotEmpty()) {
                                    {
                                        Box(modifier = Modifier.offset(x = 6.dp, y = (-4).dp)) {
                                            Badge(modifier = Modifier.size(7.dp))
                                        }
                                    }
                                } else null
                            )
                            NavigationBarItem(
                                selected = !showHomeQuickMenu && selectedTab == 1,
                                onClick = {
                                    showHomeQuickMenu = false
                                    selectedTab = 1
                                },
                                icon = MiuixIcons.Regular.Months,
                                label = "달력"
                            )
                            val addInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            val addPressed by addInteraction.collectIsPressedAsState()
                            val addRotation by animateFloatAsState(
                                targetValue = if (showHomeQuickMenu) 45f else 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                label = "miuixAddRotation"
                            )
                            val addContentColor = MiuixTheme.colorScheme.onSurface.copy(
                                alpha = when {
                                    showHomeQuickMenu && addPressed -> NavigationBarDefaults.SelectedPressedAlpha
                                    showHomeQuickMenu -> 1f
                                    addPressed -> NavigationBarDefaults.UnselectedPressedAlpha
                                    else -> NavigationBarDefaults.UnselectedAlpha
                                }
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(NavigationBarDefaults.ItemHeight)
                                    .clickable(
                                        interactionSource = addInteraction,
                                        indication = null,
                                        role = androidx.compose.ui.semantics.Role.Tab,
                                        onClick = { showHomeQuickMenu = !showHomeQuickMenu }
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Top
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Add,
                                    contentDescription = "추가",
                                    tint = addContentColor,
                                    modifier = Modifier
                                        .padding(top = NavigationBarDefaults.IconTopPadding)
                                        .size(NavigationBarDefaults.IconSize)
                                        .graphicsLayer { rotationZ = addRotation }
                                )
                                Text(
                                    text = "추가",
                                    color = addContentColor,
                                    fontSize = NavigationBarDefaults.LabelFontSize,
                                    fontWeight = if (showHomeQuickMenu) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(bottom = NavigationBarDefaults.BottomPadding)
                                )
                            }
                            NavigationBarItem(
                                selected = !showHomeQuickMenu && selectedTab == 2,
                                onClick = {
                                    showHomeQuickMenu = false
                                    recordsOpenUnpaid = false
                                    selectedTab = 2
                                },
                                icon = MiuixIcons.Regular.Notes,
                                label = "기록",
                                badge = if (unpaidCount > 0) {
                                    {
                                        Box(modifier = Modifier.offset(x = 6.dp, y = (-3).dp)) {
                                            Badge { Text(unpaidCount.toString()) }
                                        }
                                    }
                                } else null
                            )
                            NavigationBarItem(
                                selected = !showHomeQuickMenu && selectedTab == 3,
                                onClick = {
                                    showHomeQuickMenu = false
                                    selectedTab = 3
                                },
                                icon = MiuixIcons.Regular.Tune,
                                label = "계산기"
                            )
                        }
                        }
                    }
                }
            }

            // ⑤ Morphing Quick-Add menu expanding from the center '+' button
            if (isQuickMenuMounted) {
                ExpandableQuickAddMenuOverlay(
                    morphProgress = quickMenuMorphProgress.value,
                    interactive = showHomeQuickMenu,
                    isLiquidGlass = isLiquidGlassNav,
                    onAddRegularJob = {
                        showHomeQuickMenu = false
                        onOpenAddRegularJob(null, null)
                    },
                    onQuickAddShortTerm = {
                        showHomeQuickMenu = false
                        val prefillDate = if (selectedTab == 1) calendarSelectedDate else LocalDate.now()
                        onOpenQuickShortTerm(null, PayrollCalculator.formatDate(prefillDate))
                    },
                    onStartStopwatch = {
                        showHomeQuickMenu = false
                        selectedTab = 0
                        openStopwatchTrigger++
                    }
                )
            }

            val toastBottomPadding by animateDpAsState(
                targetValue = when {
                    !showChrome -> 24.dp
                    isLiquidGlassNav -> 104.dp
                    else -> 94.dp
                },
                animationSpec = spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow),
                label = "toast_bottom_offset"
            )
            StackedToastHost(
                state = toastState,
                backdrop = barBackdrop,
                isLiquidGlass = isLiquidGlassNav,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = toastBottomPadding)
            )

            AppUpdateDialog(
                uiState = updateUiState,
                onDismiss = viewModel::dismissUpdateDialog,
                onStartDownload = viewModel::startUpdateDownloadAndInstall,
                onRetryInstall = viewModel::retryApkInstall,
                onOpenPermissionSettings = viewModel::openInstallUnknownSourcesSettings
            )

            val dismissedRecoveryKeys = remember { mutableStateListOf<String>() }
            val recoverableCandidate = remember(state, liveDashboard.activeShifts.size, dismissedRecoveryKeys.size) {
                PayrollCalculator.findRecoverableClockedOutShifts(
                    state = state,
                    nowEpochMillis = System.currentTimeMillis()
                ).firstOrNull { it.runtimeKey !in dismissedRecoveryKeys }
            }
            if (recoverableCandidate != null && !updateUiState.showDialog) {
                OverlayDialog(
                    show = true,
                    title = "근무 시간 복구",
                    summary = "실수로 '${recoverableCandidate.title}' (${recoverableCandidate.dateIso}) 퇴근 버튼을 누르셨나요?\n아래 버튼을 눌러 진행 중인 근무 시간을 복구할 수 있습니다.",
                    onDismissRequest = { dismissedRecoveryKeys.add(recoverableCandidate.runtimeKey) }
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(
                            text = "닫기",
                            onClick = { dismissedRecoveryKeys.add(recoverableCandidate.runtimeKey) },
                            modifier = Modifier.weight(0.8f)
                        )
                        TextButton(
                            text = "여기를 눌러 근무 복구",
                            onClick = {
                                viewModel.restoreScheduledShift(recoverableCandidate.runtimeKey)
                                toastState.show("'${recoverableCandidate.title}' 근무 시간이 복구되었습니다.")
                            },
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            modifier = Modifier.weight(1.4f)
                        )
                    }
                }
            } else if (!state.settings.hasShownBatteryPrompt &&
                !updateUiState.showDialog &&
                !BatteryOptimizationHelper.isIgnoringBatteryOptimizations(hostContext)
            ) {
                OverlayDialog(
                    show = true,
                    title = "백그라운드 항상 실행 허용",
                    summary = "상단바 실시간 알림을 꺼두더라도 자동 방해금지, 위젯, 실시간 급여 계산이 백그라운드에서 끊기지 않도록 배터리 최적화 예외(제한 없음) 권한을 허용해 주세요.",
                    onDismissRequest = { viewModel.markBatteryPromptShown() }
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(
                            text = "나중에",
                            onClick = { viewModel.markBatteryPromptShown() },
                            modifier = Modifier.weight(0.9f)
                        )
                        TextButton(
                            text = "권한 허용",
                            onClick = {
                                viewModel.markBatteryPromptShown()
                                BatteryOptimizationHelper.requestIgnoreBatteryOptimizations(hostContext)
                            },
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            modifier = Modifier.weight(1.1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreenHost(
    viewModel: AlbaToraViewModel,
    toastState: StackedToastState,
    onOpenWidgetStudio: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val updateUiState by viewModel.updateUiState.collectAsStateWithLifecycle()
    val scrollBehavior = MiuixScrollBehavior(state = rememberTopAppBarState())
    val settingsBackdrop = rememberLayerBackdrop()
    val surfaceBg = MiuixTheme.colorScheme.surface
    val notify: (String) -> Unit = { msg -> toastState.show(msg) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            MiuixFloatingScrollTopBar(
                title = "더보기 · 설정",
                scrollBehavior = scrollBehavior,
                backdrop = settingsBackdrop,
                maxContentWidth = 620.dp,
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = "뒤로가기"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(settingsBackdrop)
                    .background(surfaceBg)
            ) {
                MoreSettingsScreen(
                    state = state,
                    scrollBehavior = scrollBehavior,
                    topInset = paddingValues.calculateTopPadding(),
                    updateUiState = updateUiState,
                    onUpdateTheme = viewModel::updateTheme,
                    onUpdateThemeColorStyle = viewModel::updateThemeColorStyle,
                    onUpdateNavBarStyle = viewModel::updateNavBarStyle,
                    onUpdateWideDualLayout = viewModel::updateWideDualLayout,
                    onUpdateCustomKeyColor = viewModel::updateCustomKeyColor,
                    onUpdateOngoingNotification = viewModel::updateOngoingNotification,
                    onUpdateAutoDnd = viewModel::updateAutoDnd,
                    onUpdateDefaultMinWage = viewModel::updateDefaultMinWage,
                    onUpsertTemplate = viewModel::upsertTemplate,
                    onDeleteTemplate = viewModel::deleteTemplate,
                    onExportJson = viewModel::exportBackupJson,
                    onPreviewBackupJson = viewModel::previewBackupJson,
                    onImportJson = viewModel::importBackupJson,
                    onResetAllData = viewModel::resetAllData,
                    onCheckForUpdate = { viewModel.checkForUpdate(manual = true, onFeedback = notify) },
                    onOpenWidgetStudio = onOpenWidgetStudio,
                    onNotify = notify
                )
            }

            MiuixBottomEdgeBlur(
                backdrop = settingsBackdrop,
                extraHeight = 36.dp,
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            StackedToastHost(
                state = toastState,
                backdrop = settingsBackdrop,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 400.dp)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
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

@Composable
private fun ExpandableQuickAddMenuOverlay(
    morphProgress: Float,
    interactive: Boolean,
    isLiquidGlass: Boolean,
    onAddRegularJob: () -> Unit,
    onQuickAddShortTerm: () -> Unit,
    onStartStopwatch: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val density = LocalDensity.current
    val raw = morphProgress.coerceAtLeast(0f)
    val clamped = raw.coerceIn(0f, 1f)
    val liftProgress = smoothStep(0f, 0.72f, clamped) + (raw - clamped) * 0.35f
    val sizeProgress = smoothStep(0.02f, 0.85f, clamped) + (raw - clamped) * 0.18f
    val colorBlend = smoothStep(0.06f, 0.58f, clamped)
    val contentFade = smoothStep(0.32f, 0.84f, clamped)

    val surfaceCardColor = if (isDark) MiuixTheme.colorScheme.surfaceContainerHigh else Color.White
    val cardColor = if (isLiquidGlass) {
        androidx.compose.ui.graphics.lerp(
            MiuixTheme.colorScheme.primary,
            surfaceCardColor,
            colorBlend
        )
    } else {
        surfaceCardColor.copy(alpha = smoothStep(0f, 0.30f, clamped))
    }
    val borderColor = MiuixTheme.colorScheme.onSurface.copy(
        alpha = lerpFloat(0f, if (isDark) 0.12f else 0.08f, clamped)
    )
    val bottomOffset = androidx.compose.ui.unit.lerp(
        if (isLiquidGlass) 22.dp else 36.dp,
        if (isLiquidGlass) 88.dp else 80.dp,
        liftProgress.coerceAtLeast(0f)
    )
    val cornerRadius = androidx.compose.ui.unit.lerp(
        if (isLiquidGlass) 22.dp else 18.dp,
        26.dp,
        clamped
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        val targetWidthDp = (maxWidth - 40.dp).coerceIn(264.dp, 340.dp)
        val targetWidthPx = with(density) { targetWidthDp.roundToPx() }
        val minWidthPx = with(density) { (if (isLiquidGlass) 50.dp else 84.dp).roundToPx() }
        val minHeightPx = with(density) { (if (isLiquidGlass) 44.dp else 36.dp).roundToPx() }
        val cardShape = RoundedCornerShape(cornerRadius)

        Box(
            modifier = Modifier
                .padding(bottom = bottomOffset)
                .shadow(
                    elevation = lerpFloat(2f, 22f, clamped).dp,
                    shape = cardShape,
                    ambientColor = Color.Black.copy(alpha = lerpFloat(0.08f, 0.28f, clamped)),
                    spotColor = Color.Black.copy(alpha = lerpFloat(0.10f, 0.32f, clamped))
                )
                .clip(cardShape)
                .background(cardColor)
                .squircleBorder(1.dp, borderColor, cornerRadius)
                .pointerInput(interactive) {
                    if (interactive) {
                        detectTapGestures { /* keep taps inside menu from falling through to dismiss scrim */ }
                    }
                }
                .layout { measurable, constraints ->
                    val childConstraints = constraints.copy(
                        minWidth = targetWidthPx,
                        maxWidth = targetWidthPx,
                        minHeight = 0
                    )
                    val placeable = measurable.measure(childConstraints)
                    val curW = lerpFloat(minWidthPx.toFloat(), targetWidthPx.toFloat(), sizeProgress)
                        .roundToInt()
                        .coerceAtLeast(1)
                    val curH = lerpFloat(minHeightPx.toFloat(), placeable.height.toFloat(), sizeProgress)
                        .roundToInt()
                        .coerceAtLeast(1)
                    layout(curW, curH) {
                        placeable.placeRelative(
                            x = (curW - placeable.width) / 2,
                            y = (curH - placeable.height) / 2
                        )
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .width(targetWidthDp)
                    .padding(horizontal = 8.dp, vertical = 10.dp)
                    .graphicsLayer {
                        alpha = contentFade
                    }
            ) {
                Text(
                    text = "빠른 추가",
                    style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.SemiBold),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 6.dp)
                )
                QuickAddMorphActionRow(
                    icon = MiuixIcons.Store,
                    iconTint = MiuixTheme.colorScheme.primary,
                    title = "고정 근무(매장) 추가",
                    subtitle = "반복 스케줄 · 주휴 · 세금",
                    revealProgress = smoothStep(0.28f, 0.78f, clamped),
                    enabled = interactive,
                    onClick = onAddRegularJob
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                QuickAddMorphActionRow(
                    icon = MiuixIcons.Timer,
                    iconTint = MiuixTheme.colorScheme.primary,
                    title = "단기 알바 · 일당 추가",
                    subtitle = "일당 · 대타 · 단기 기록",
                    revealProgress = smoothStep(0.36f, 0.86f, clamped),
                    enabled = interactive,
                    onClick = onQuickAddShortTerm
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                QuickAddMorphActionRow(
                    icon = MiuixIcons.Stopwatch,
                    iconTint = MiuixTheme.colorScheme.primary,
                    title = "즉석 스톱워치 출근",
                    subtitle = "지금 바로 시급 카운트",
                    revealProgress = smoothStep(0.44f, 0.94f, clamped),
                    enabled = interactive,
                    onClick = onStartStopwatch
                )
            }
        }
    }
}

@Composable
private fun QuickAddMorphActionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    revealProgress: Float,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val density = LocalDensity.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .graphicsLayer {
                alpha = revealProgress
                translationY = (1f - revealProgress) * with(density) { 12.dp.toPx() }
            }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = iconTint,
            modifier = Modifier.size(26.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.headline2.copy(fontWeight = FontWeight.SemiBold),
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = MiuixIcons.ChevronForward,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            modifier = Modifier.size(16.dp)
        )
    }
}

private fun lerpFloat(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction

private fun smoothStep(edge0: Float, edge1: Float, x: Float): Float {
    if (edge1 <= edge0) return if (x >= edge1) 1f else 0f
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
