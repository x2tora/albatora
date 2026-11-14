package com.albatora.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import kotlin.math.roundToInt
import androidx.core.content.ContextCompat
import com.albatora.app.MainActivity
import com.albatora.app.R
import com.albatora.app.data.AlbaToraStateHolder
import com.albatora.app.domain.PayrollCalculator
import com.albatora.app.model.AlbaToraDataState
import com.albatora.app.model.ShiftStatus
import com.albatora.app.model.ThemeColorStyle
import com.albatora.app.model.WidgetThemeStyle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class WidgetVisualPalette(
    val cardBgColor: Int,
    val cardAlpha: Int,
    val strokeColor: Int,
    val strokeAlpha: Int,
    val innerPillBg: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val accent: Int,
    val accentSoftAlpha: Int,
    val onAccent: Int,
    val green: Int,
    val amber: Int,
    val red: Int
)

object AlbaToraWidgetUpdater {

    const val EXTRA_OPEN_TAB = "extra_open_tab"
    const val EXTRA_OPEN_STOPWATCH = "extra_open_stopwatch"

    fun updateAll(context: Context) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        updateLiveEarningsWidgets(appContext, manager)
        updateCompactLiveWidgets(appContext, manager)
        updateWeeklyScheduleWidgets(appContext, manager)
        updateMonthlySummaryWidgets(appContext, manager)
    }

    fun updateLiveEarningsOnly(context: Context) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        updateLiveEarningsWidgets(appContext, manager)
        updateCompactLiveWidgets(appContext, manager)
    }

    fun resolvePalette(context: Context, state: AlbaToraDataState): WidgetVisualPalette {
        val cfg = state.settings.widgetConfig
        val systemNight = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        val darkCard = when (cfg.themeStyle) {
            WidgetThemeStyle.AUTO -> systemNight
            WidgetThemeStyle.LIGHT -> false
            WidgetThemeStyle.DARK -> true
            WidgetThemeStyle.GLASS -> systemNight
        }

        val effectiveOpacityPercent = if (cfg.themeStyle == WidgetThemeStyle.GLASS) {
            cfg.opacity.percent.coerceAtMost(72)
        } else {
            cfg.opacity.percent
        }
        val cardAlpha = ((effectiveOpacityPercent * 255) / 100).coerceIn(45, 255)

        val accentArgb: Int = if (cfg.useAppAccent) {
            when (state.settings.resolvedColorStyle()) {
                ThemeColorStyle.DEFAULT -> 0xFF3482FF.toInt()
                ThemeColorStyle.CUSTOM -> (state.settings.customKeyColorHex ?: 0xFF3482FFL).toInt() or 0xFF000000.toInt()
                ThemeColorStyle.MATERIAL_YOU -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val colorRes = if (darkCard) android.R.color.system_accent1_300 else android.R.color.system_accent1_600
                        ContextCompat.getColor(context, colorRes)
                    } else {
                        ContextCompat.getColor(context, R.color.widget_accent)
                    }
                }
            }
        } else {
            cfg.customAccentHex.toInt() or 0xFF000000.toInt()
        }

        val onAccent = if (calculateLuminance(accentArgb) > 0.62) 0xFF111827.toInt() else Color.WHITE

        return if (darkCard) {
            WidgetVisualPalette(
                cardBgColor = if (cfg.themeStyle == WidgetThemeStyle.GLASS) 0xFF161922.toInt() else 0xFF1C1F27.toInt(),
                cardAlpha = cardAlpha,
                strokeColor = Color.WHITE,
                strokeAlpha = if (cfg.themeStyle == WidgetThemeStyle.GLASS) 52 else 36,
                innerPillBg = 0xFF2B303C.toInt(),
                textPrimary = 0xFFF3F4F6.toInt(),
                textSecondary = 0xFFA5AEBC.toInt(),
                accent = accentArgb,
                accentSoftAlpha = 52,
                onAccent = onAccent,
                green = 0xFF4ADE80.toInt(),
                amber = 0xFFFBBF24.toInt(),
                red = 0xFFF87171.toInt()
            )
        } else {
            WidgetVisualPalette(
                cardBgColor = if (cfg.themeStyle == WidgetThemeStyle.GLASS) 0xFFF7F9FC.toInt() else 0xFFFFFFFF.toInt(),
                cardAlpha = cardAlpha,
                strokeColor = 0xFF111827.toInt(),
                strokeAlpha = if (cfg.themeStyle == WidgetThemeStyle.GLASS) 32 else 22,
                innerPillBg = 0xFFECEFF4.toInt(),
                textPrimary = 0xFF111827.toInt(),
                textSecondary = 0xFF6B7280.toInt(),
                accent = accentArgb,
                accentSoftAlpha = 36,
                onAccent = onAccent,
                green = 0xFF16A34A.toInt(),
                amber = 0xFFD97706.toInt(),
                red = 0xFFDC2626.toInt()
            )
        }
    }

    private fun calculateLuminance(color: Int): Double {
        val r = Color.red(color) / 255.0
        val g = Color.green(color) / 255.0
        val b = Color.blue(color) / 255.0
        return 0.299 * r + 0.587 * g + 0.114 * b
    }

    private fun applyCardSurface(
        views: RemoteViews,
        bgFillId: Int,
        bgStrokeId: Int,
        palette: WidgetVisualPalette
    ) {
        views.setInt(bgFillId, "setColorFilter", palette.cardBgColor)
        views.setInt(bgFillId, "setImageAlpha", palette.cardAlpha)
        views.setInt(bgStrokeId, "setColorFilter", palette.strokeColor)
        views.setInt(bgStrokeId, "setImageAlpha", palette.strokeAlpha)
    }

    private fun applyPillTint(
        views: RemoteViews,
        bgId: Int,
        colorRgb: Int,
        alpha: Int = 255
    ) {
        views.setInt(bgId, "setColorFilter", colorRgb)
        views.setInt(bgId, "setImageAlpha", alpha)
    }

    fun updateLiveEarningsWidgets(
        context: Context,
        manager: AppWidgetManager = AppWidgetManager.getInstance(context),
        appWidgetIds: IntArray? = null
    ) {
        val ids = appWidgetIds ?: manager.getAppWidgetIds(
            ComponentName(context, LiveEarningsWidgetProvider::class.java)
        )
        if (ids.isEmpty()) return

        val state = AlbaToraStateHolder.getInstance(context).state.value
        val cfg = state.settings.widgetConfig
        val palette = resolvePalette(context, state)
        val snapshot = PayrollCalculator.evaluateLiveDashboard(state, System.currentTimeMillis())
        val activeShifts = snapshot.activeShifts

        val amountStr: String
        val titleText: String
        val rateText: String
        val subText: String
        val dotColor: Int
        val rateTextColor: Int
        val rateBgColor: Int
        val btn1Text: String
        val btn1Bg: Int
        val btn1Alpha: Int
        val btn1TextColor: Int
        val btn1Intent: PendingIntent
        val btn2Text: String
        val btn2Bg: Int
        val btn2Alpha: Int
        val btn2TextColor: Int
        val btn2Intent: PendingIntent

        if (activeShifts.isNotEmpty()) {
            val first = activeShifts.first()
            val allPaused = activeShifts.all { it.isPaused }
            dotColor = if (allPaused) palette.amber else palette.green
            titleText = when {
                activeShifts.size > 1 -> "동시 근무 ${activeShifts.size}건 진행 중"
                allPaused -> "${first.title} · 휴게 중"
                else -> "${first.title} · 근무 중"
            }

            val perMinute = (snapshot.totalPerSecondRateWon * 60.0).toLong()
            val boostText = if (snapshot.anyBoostActive) " · ${snapshot.maxBoostMultiplier}배" else ""
            rateText = if (allPaused) "일시정지" else "+${PayrollCalculator.formatWon(perMinute)}/분$boostText"
            rateTextColor = dotColor
            rateBgColor = dotColor

            val elapsedSec = activeShifts.maxOf { it.elapsedWorkMillis } / 1000L
            val elapsedFormatted = formatElapsed(elapsedSec)
            val selectedTangible = state.tangibleItems.find { it.id == state.settings.selectedTangibleItemId }
                ?: state.tangibleItems.firstOrNull()
            val tangibleSuffix = if (cfg.showTangibleConversion && selectedTangible != null && selectedTangible.unitPrice > 0L) {
                val qty = snapshot.totalEarnedWon / selectedTangible.unitPrice.toDouble()
                " · ${selectedTangible.name} ${String.format(Locale.US, "%.1f", qty)}개"
            } else ""

            amountStr = PayrollCalculator.formatWon(snapshot.totalEarnedWon.toLong())
            subText = "실근무 $elapsedFormatted$tangibleSuffix"
            btn1Text = if (allPaused) "휴게 종료" else "휴게 시작"
            btn1Bg = palette.innerPillBg
            btn1Alpha = 235
            btn1TextColor = palette.textPrimary
            btn1Intent = actionBroadcastIntent(context, 311, AlbaToraStateHolder.ACTION_TOGGLE_BREAK)
            btn2Text = "퇴근 완료"
            btn2Bg = palette.accent
            btn2Alpha = 255
            btn2TextColor = palette.onAccent
            btn2Intent = actionBroadcastIntent(context, 312, AlbaToraStateHolder.ACTION_CLOCK_OUT)
        } else {
            val month = YearMonth.now()
            val monthSummary = PayrollCalculator.calculateSettlementSummary(state, month.atDay(1), month.atEndOfMonth())
            val quickJob = state.jobs.find { it.id == cfg.defaultQuickJobId }

            dotColor = palette.accent
            titleText = "알바토라 · 대기 중"
            rateText = "${month.monthValue}월 ${monthSummary.shiftCount}건"
            rateTextColor = palette.accent
            rateBgColor = palette.accent
            amountStr = PayrollCalculator.formatWon(monthSummary.totalNetPay)
            subText = if (quickJob != null) {
                "이번 달 예상 누적 · '${quickJob.name}' 원탭 출근 대기"
            } else {
                "이번 달 예상 누적 · 즉시 출근으로 타이머 시작"
            }

            btn1Bg = palette.accent
            btn1Alpha = 255
            btn1TextColor = palette.onAccent
            if (quickJob != null) {
                btn1Text = "${quickJob.name} 출근"
                btn1Intent = actionBroadcastIntent(context, 315, AlbaToraStateHolder.ACTION_QUICK_CLOCK_IN)
            } else {
                btn1Text = "즉시 출근"
                btn1Intent = openAppIntent(context, requestCode = 313, tab = 0, openStopwatch = true)
            }
            btn2Text = "달력 보기"
            btn2Bg = palette.innerPillBg
            btn2Alpha = 235
            btn2TextColor = palette.textPrimary
            btn2Intent = openAppIntent(context, requestCode = 314, tab = 1)
        }

        for (widgetId in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_live_earnings)
            applyCardSurface(views, R.id.widget_bg_fill, R.id.widget_bg_stroke, palette)
            views.setTextColor(R.id.widget_live_title, palette.textPrimary)
            views.setTextColor(R.id.widget_live_amount, palette.textPrimary)
            views.setTextColor(R.id.widget_live_sub, palette.textSecondary)
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context, requestCode = 301, tab = 0))
            views.setViewVisibility(R.id.widget_actions_row, if (cfg.showQuickActions) View.VISIBLE else View.GONE)

            applyPillTint(views, R.id.widget_live_dot, dotColor)
            views.setTextViewText(R.id.widget_live_title, titleText)
            views.setTextViewText(R.id.widget_live_rate, rateText)
            views.setTextColor(R.id.widget_live_rate, rateTextColor)
            applyPillTint(views, R.id.widget_live_rate_bg, rateBgColor, palette.accentSoftAlpha)

            views.setTextViewText(R.id.widget_live_amount, amountStr)
            views.setTextViewText(R.id.widget_live_sub, subText)

            applyPillTint(views, R.id.widget_btn_primary_bg, btn1Bg, btn1Alpha)
            views.setTextColor(R.id.widget_btn_primary, btn1TextColor)
            views.setTextViewText(R.id.widget_btn_primary, btn1Text)
            views.setOnClickPendingIntent(R.id.widget_btn_primary_wrap, btn1Intent)

            applyPillTint(views, R.id.widget_btn_secondary_bg, btn2Bg, btn2Alpha)
            views.setTextColor(R.id.widget_btn_secondary, btn2TextColor)
            views.setTextViewText(R.id.widget_btn_secondary, btn2Text)
            views.setOnClickPendingIntent(R.id.widget_btn_secondary_wrap, btn2Intent)

            applyResponsiveLiveSizing(context, manager, widgetId, views, amountStr)
            manager.updateAppWidget(widgetId, views)
        }
    }

    fun updateCompactLiveWidgets(
        context: Context,
        manager: AppWidgetManager = AppWidgetManager.getInstance(context),
        appWidgetIds: IntArray? = null
    ) {
        val ids = appWidgetIds ?: manager.getAppWidgetIds(
            ComponentName(context, CompactLiveWidgetProvider::class.java)
        )
        if (ids.isEmpty()) return

        val state = AlbaToraStateHolder.getInstance(context).state.value
        val cfg = state.settings.widgetConfig
        val palette = resolvePalette(context, state)
        val snapshot = PayrollCalculator.evaluateLiveDashboard(state, System.currentTimeMillis())
        val activeShifts = snapshot.activeShifts

        val dotColor: Int
        val amountStr: String
        val subText: String
        val showBtn1: Boolean
        val showBtn2: Boolean
        val btn1Text: String
        val btn1Intent: PendingIntent
        val btn2Text: String
        val btn2Intent: PendingIntent

        if (activeShifts.isNotEmpty()) {
            val first = activeShifts.first()
            val allPaused = activeShifts.all { it.isPaused }
            dotColor = if (allPaused) palette.amber else palette.green
            val elapsedFormatted = formatElapsed(activeShifts.maxOf { it.elapsedWorkMillis } / 1000L)
            subText = if (activeShifts.size > 1) {
                "동시 ${activeShifts.size}건 · $elapsedFormatted"
            } else if (allPaused) {
                "${first.title} (휴게) · $elapsedFormatted"
            } else {
                "${first.title} · $elapsedFormatted"
            }
            amountStr = PayrollCalculator.formatWon(snapshot.totalEarnedWon.toLong())
            showBtn1 = cfg.showQuickActions
            showBtn2 = cfg.showQuickActions
            btn1Text = if (allPaused) "재개" else "휴게"
            btn1Intent = actionBroadcastIntent(context, 341, AlbaToraStateHolder.ACTION_TOGGLE_BREAK)
            btn2Text = "퇴근"
            btn2Intent = actionBroadcastIntent(context, 342, AlbaToraStateHolder.ACTION_CLOCK_OUT)
        } else {
            val month = YearMonth.now()
            val monthSummary = PayrollCalculator.calculateSettlementSummary(state, month.atDay(1), month.atEndOfMonth())
            val quickJob = state.jobs.find { it.id == cfg.defaultQuickJobId }

            dotColor = palette.accent
            amountStr = PayrollCalculator.formatWon(monthSummary.totalNetPay)
            subText = if (quickJob != null) "${month.monthValue}월 누적 · '${quickJob.name}' 대기" else "${month.monthValue}월 예상 누적"
            showBtn1 = false
            showBtn2 = cfg.showQuickActions
            btn1Text = "휴게"
            btn1Intent = actionBroadcastIntent(context, 341, AlbaToraStateHolder.ACTION_TOGGLE_BREAK)
            if (quickJob != null) {
                btn2Text = "바로 출근"
                btn2Intent = actionBroadcastIntent(context, 343, AlbaToraStateHolder.ACTION_QUICK_CLOCK_IN)
            } else {
                btn2Text = "출근"
                btn2Intent = openAppIntent(context, requestCode = 344, tab = 0, openStopwatch = true)
            }
        }

        for (widgetId in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_compact_live)
            applyCardSurface(views, R.id.widget_compact_bg_fill, R.id.widget_compact_bg_stroke, palette)
            views.setTextColor(R.id.widget_compact_amount, palette.textPrimary)
            views.setTextColor(R.id.widget_compact_sub, palette.textSecondary)
            views.setOnClickPendingIntent(R.id.widget_compact_root, openAppIntent(context, requestCode = 340, tab = 0))

            applyPillTint(views, R.id.widget_compact_dot, dotColor)
            views.setTextViewText(R.id.widget_compact_amount, amountStr)
            views.setTextViewText(R.id.widget_compact_sub, subText)

            views.setViewVisibility(R.id.widget_compact_btn1_wrap, if (showBtn1) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.widget_compact_btn2_wrap, if (showBtn2) View.VISIBLE else View.GONE)

            applyPillTint(views, R.id.widget_compact_btn1_bg, palette.innerPillBg, 235)
            views.setTextColor(R.id.widget_compact_btn1, palette.textPrimary)
            views.setTextViewText(R.id.widget_compact_btn1, btn1Text)
            views.setOnClickPendingIntent(R.id.widget_compact_btn1_wrap, btn1Intent)

            applyPillTint(views, R.id.widget_compact_btn2_bg, palette.accent, 255)
            views.setTextColor(R.id.widget_compact_btn2, palette.onAccent)
            views.setTextViewText(R.id.widget_compact_btn2, btn2Text)
            views.setOnClickPendingIntent(R.id.widget_compact_btn2_wrap, btn2Intent)

            applyResponsiveCompactSizing(context, manager, widgetId, views, amountStr)
            manager.updateAppWidget(widgetId, views)
        }
    }

    fun updateWeeklyScheduleWidgets(
        context: Context,
        manager: AppWidgetManager = AppWidgetManager.getInstance(context),
        appWidgetIds: IntArray? = null
    ) {
        val ids = appWidgetIds ?: manager.getAppWidgetIds(
            ComponentName(context, WeeklyScheduleWidgetProvider::class.java)
        )
        if (ids.isEmpty()) return

        val state = AlbaToraStateHolder.getInstance(context).state.value
        val cfg = state.settings.widgetConfig
        val palette = resolvePalette(context, state)
        val today = LocalDate.now()
        val nowDt = LocalDateTime.now()
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekEnd = weekStart.plusDays(6)

        val upcomingLines = mutableListOf<String>()
        var totalCount = 0

        for (offset in 0..6) {
            val day = weekStart.plusDays(offset.toLong())
            val items = PayrollCalculator.resolveCalendarDayItems(state, day, nowDt)
                .filter { it.status != ShiftStatus.ABSENT }
            totalCount += items.size
            if (!cfg.weeklyHidePastDays || day >= today) {
                items.forEach { item ->
                    val dayTag = when {
                        day == today -> "오늘"
                        day < today -> "${day.monthValue}/${day.dayOfMonth}(완)"
                        else -> "${day.monthValue}/${day.dayOfMonth}(${dayLabel(day.dayOfWeek)})"
                    }
                    val timeRange = "${formatMinute(item.startMinute)}~${formatMinute(item.endMinute)}"
                    val liveTag = if (item.isCurrentlyLive) " · 진행 중" else ""
                    upcomingLines += "$dayTag · ${item.title} · $timeRange$liveTag"
                }
            }
        }

        val lineIds = intArrayOf(
            R.id.widget_weekly_line1,
            R.id.widget_weekly_line2,
            R.id.widget_weekly_line3,
            R.id.widget_weekly_line4
        )
        val displayLines = if (upcomingLines.isEmpty()) {
            listOf(
                "이번 주 예정된 근무가 없습니다",
                "달력에서 원탭으로 단기·대타를 등록할 수 있어요"
            )
        } else {
            upcomingLines.take(lineIds.size)
        }

        for (widgetId in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_weekly_schedule)
            applyCardSurface(views, R.id.widget_weekly_bg_fill, R.id.widget_weekly_bg_stroke, palette)
            views.setTextColor(R.id.widget_weekly_title, palette.textPrimary)
            views.setTextColor(R.id.widget_weekly_range, palette.textSecondary)
            views.setTextColor(R.id.widget_weekly_badge, palette.accent)
            applyPillTint(views, R.id.widget_weekly_badge_bg, palette.accent, palette.accentSoftAlpha)

            views.setOnClickPendingIntent(R.id.widget_weekly_root, openAppIntent(context, requestCode = 321, tab = 1))
            views.setTextViewText(R.id.widget_weekly_badge, "${totalCount}건")
            views.setTextViewText(
                R.id.widget_weekly_range,
                "${weekStart.monthValue}/${weekStart.dayOfMonth} ~ ${weekEnd.monthValue}/${weekEnd.dayOfMonth} 근무 일정"
            )

            lineIds.forEachIndexed { idx, viewId ->
                views.setTextColor(viewId, if (idx == 0) palette.textPrimary else palette.textSecondary)
                val lineText = displayLines.getOrNull(idx)
                if (lineText != null) {
                    views.setViewVisibility(viewId, View.VISIBLE)
                    views.setTextViewText(viewId, lineText)
                } else {
                    views.setViewVisibility(viewId, View.GONE)
                }
            }

            applyResponsiveWeeklySizing(context, manager, widgetId, views, displayLines.size)
            manager.updateAppWidget(widgetId, views)
        }
    }

    fun updateMonthlySummaryWidgets(
        context: Context,
        manager: AppWidgetManager = AppWidgetManager.getInstance(context),
        appWidgetIds: IntArray? = null
    ) {
        val ids = appWidgetIds ?: manager.getAppWidgetIds(
            ComponentName(context, MonthlySummaryWidgetProvider::class.java)
        )
        if (ids.isEmpty()) return

        val state = AlbaToraStateHolder.getInstance(context).state.value
        val cfg = state.settings.widgetConfig
        val palette = resolvePalette(context, state)
        val month = YearMonth.now()
        val summary = PayrollCalculator.calculateSettlementSummary(state, month.atDay(1), month.atEndOfMonth())
        val live = PayrollCalculator.evaluateLiveDashboard(state, System.currentTimeMillis())
        val totalWithLive = summary.totalNetPay + live.totalEarnedWon.toLong()
        val unpaid = PayrollCalculator.calculateGlobalUnpaidSummary(state)
        val paydays = PayrollCalculator.calculateNextPaydays(state, LocalDate.now())
        val nearestPayday = paydays.firstOrNull()

        val totalFormatted = PayrollCalculator.formatWon(totalWithLive)
        val metaText = "완료 ${summary.shiftCount}건 · ${PayrollCalculator.formatDurationKorean(summary.totalWorkMinutes)}"
        val badgeText: String
        val badgeColor: Int
        if (unpaid.first > 0L) {
            badgeText = "미수금 ${unpaid.second.size}건 · ${PayrollCalculator.formatWon(unpaid.first)}"
            badgeColor = palette.red
        } else if (nearestPayday != null) {
            val dDay = nearestPayday.dDay.coerceAtLeast(0L)
            badgeText = if (dDay == 0L) "오늘 월급날" else "월급 D-$dDay"
            badgeColor = palette.accent
        } else {
            badgeText = "미수금 없음"
            badgeColor = palette.accent
        }

        for (widgetId in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_monthly_summary)
            applyCardSurface(views, R.id.widget_monthly_bg_fill, R.id.widget_monthly_bg_stroke, palette)
            views.setTextColor(R.id.widget_monthly_title, palette.textSecondary)
            views.setTextColor(R.id.widget_monthly_total, palette.textPrimary)
            views.setTextColor(R.id.widget_monthly_meta, palette.textSecondary)

            views.setOnClickPendingIntent(R.id.widget_monthly_root, openAppIntent(context, requestCode = 331, tab = 2))
            views.setTextViewText(R.id.widget_monthly_title, "${month.monthValue}월 예상 누적")
            views.setTextViewText(R.id.widget_monthly_total, totalFormatted)
            views.setTextViewText(R.id.widget_monthly_meta, metaText)

            views.setViewVisibility(
                R.id.widget_monthly_unpaid_wrap,
                if (cfg.showMonthlyUnpaidBadge) View.VISIBLE else View.GONE
            )
            views.setTextViewText(R.id.widget_monthly_unpaid, badgeText)
            views.setTextColor(R.id.widget_monthly_unpaid, badgeColor)
            applyPillTint(views, R.id.widget_monthly_unpaid_bg, badgeColor, palette.accentSoftAlpha)

            applyResponsiveMonthlySizing(context, manager, widgetId, views, totalFormatted)
            manager.updateAppWidget(widgetId, views)
        }
    }

    private fun readWidgetSizeDp(manager: AppWidgetManager, widgetId: Int): Pair<Float, Float>? {
        val opts = manager.getAppWidgetOptions(widgetId)
        val minW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val minH = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        if (minW <= 0 || minH <= 0) return null
        return minW.toFloat() to minH.toFloat()
    }

    private fun dpToPx(context: Context, dp: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics)
            .roundToInt()
            .coerceAtLeast(0)

    private fun applyResponsiveLiveSizing(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        views: RemoteViews,
        amountText: String
    ) {
        val (w, h) = readWidgetSizeDp(manager, widgetId) ?: return
        val wScale = (w / 270f).coerceIn(0.92f, 1.32f)
        val hScale = (h / 122f).coerceIn(0.94f, 1.38f)
        val scale = (0.55f * wScale + 0.45f * hScale).coerceIn(0.94f, 1.35f)
        val hPad = dpToPx(context, (w * 0.042f).coerceIn(10f, 18f))
        val vPad = dpToPx(context, (h * 0.075f).coerceIn(9f, 16f))
        views.setViewPadding(R.id.widget_live_content, hPad, vPad, hPad, vPad)

        val charBudgetWidth = (w - 28f).coerceAtLeast(120f)
        val maxAmountForWidth = charBudgetWidth / (amountText.length.coerceAtLeast(4) * 0.62f)
        val amountSp = minOf(32f * scale, maxAmountForWidth).coerceIn(24f, 44f)

        views.setTextViewTextSize(R.id.widget_live_title, TypedValue.COMPLEX_UNIT_SP, (15f * scale).coerceIn(14f, 19f))
        views.setTextViewTextSize(R.id.widget_live_rate, TypedValue.COMPLEX_UNIT_SP, (12.5f * scale).coerceIn(11.5f, 15.5f))
        views.setTextViewTextSize(R.id.widget_live_amount, TypedValue.COMPLEX_UNIT_SP, amountSp)
        views.setTextViewTextSize(R.id.widget_live_sub, TypedValue.COMPLEX_UNIT_SP, (13.5f * scale).coerceIn(12.5f, 16.5f))
        val btnSp = (14f * scale).coerceIn(13f, 17f)
        views.setTextViewTextSize(R.id.widget_btn_primary, TypedValue.COMPLEX_UNIT_SP, btnSp)
        views.setTextViewTextSize(R.id.widget_btn_secondary, TypedValue.COMPLEX_UNIT_SP, btnSp)
    }

    private fun applyResponsiveCompactSizing(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        views: RemoteViews,
        amountText: String
    ) {
        val (w, h) = readWidgetSizeDp(manager, widgetId) ?: return
        val wScale = (w / 270f).coerceIn(0.90f, 1.25f)
        val hScale = (h / 56f).coerceIn(0.94f, 1.25f)
        val scale = minOf(wScale, hScale).coerceIn(0.92f, 1.24f)
        val hPad = dpToPx(context, (w * 0.038f).coerceIn(9f, 15f))
        val vPad = dpToPx(context, (h * 0.10f).coerceIn(5f, 11f))
        views.setViewPadding(R.id.widget_compact_content, hPad, vPad, (hPad * 0.85f).roundToInt(), vPad)

        val charBudgetWidth = (w * 0.48f).coerceAtLeast(90f)
        val maxAmountForWidth = charBudgetWidth / (amountText.length.coerceAtLeast(4) * 0.62f)
        val amountSp = minOf(21f * scale, maxAmountForWidth).coerceIn(17.5f, 26f)

        views.setTextViewTextSize(R.id.widget_compact_amount, TypedValue.COMPLEX_UNIT_SP, amountSp)
        views.setTextViewTextSize(R.id.widget_compact_sub, TypedValue.COMPLEX_UNIT_SP, (12.5f * scale).coerceIn(11.5f, 15f))
        val btnSp = (13f * scale).coerceIn(12f, 15.5f)
        views.setTextViewTextSize(R.id.widget_compact_btn1, TypedValue.COMPLEX_UNIT_SP, btnSp)
        views.setTextViewTextSize(R.id.widget_compact_btn2, TypedValue.COMPLEX_UNIT_SP, btnSp)
    }

    private fun applyResponsiveWeeklySizing(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        views: RemoteViews,
        visibleLineCount: Int
    ) {
        val (w, h) = readWidgetSizeDp(manager, widgetId) ?: return
        val wScale = (w / 270f).coerceIn(0.94f, 1.28f)
        val hScale = (h / 122f).coerceIn(0.94f, 1.38f)
        val scale = (0.5f * wScale + 0.5f * hScale).coerceIn(0.94f, 1.32f)
        val sparseBoost = if (visibleLineCount <= 2) 1.07f else 1.0f
        val hPad = dpToPx(context, (w * 0.042f).coerceIn(10f, 18f))
        val vPad = dpToPx(context, (h * 0.075f).coerceIn(9f, 16f))
        views.setViewPadding(R.id.widget_weekly_content, hPad, vPad, hPad, vPad)

        views.setTextViewTextSize(R.id.widget_weekly_title, TypedValue.COMPLEX_UNIT_SP, (16f * scale).coerceIn(15f, 20f))
        views.setTextViewTextSize(R.id.widget_weekly_badge, TypedValue.COMPLEX_UNIT_SP, (12.5f * scale).coerceIn(12f, 15.5f))
        views.setTextViewTextSize(R.id.widget_weekly_range, TypedValue.COMPLEX_UNIT_SP, (12.5f * scale).coerceIn(12f, 15.5f))
        views.setTextViewTextSize(
            R.id.widget_weekly_line1,
            TypedValue.COMPLEX_UNIT_SP,
            (14.5f * scale * sparseBoost).coerceIn(13.5f, 18.5f)
        )
        val subLineSp = (14f * scale * sparseBoost).coerceIn(13f, 17.5f)
        views.setTextViewTextSize(R.id.widget_weekly_line2, TypedValue.COMPLEX_UNIT_SP, subLineSp)
        views.setTextViewTextSize(R.id.widget_weekly_line3, TypedValue.COMPLEX_UNIT_SP, subLineSp)
        views.setTextViewTextSize(R.id.widget_weekly_line4, TypedValue.COMPLEX_UNIT_SP, subLineSp)
    }

    private fun applyResponsiveMonthlySizing(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        views: RemoteViews,
        totalText: String
    ) {
        val (w, h) = readWidgetSizeDp(manager, widgetId) ?: return
        val wScale = (w / 135f).coerceIn(0.94f, 1.38f)
        val hScale = (h / 122f).coerceIn(0.94f, 1.38f)
        val scale = (0.55f * wScale + 0.45f * hScale).coerceIn(0.94f, 1.34f)
        val hPad = dpToPx(context, (w * 0.075f).coerceIn(9f, 16f))
        val vPad = dpToPx(context, (h * 0.075f).coerceIn(9f, 16f))
        views.setViewPadding(R.id.widget_monthly_content, hPad, vPad, hPad, vPad)

        val charBudgetWidth = (w - 22f).coerceAtLeast(80f)
        val maxTotalForWidth = charBudgetWidth / (totalText.length.coerceAtLeast(4) * 0.60f)
        val totalSp = minOf(26f * scale, maxTotalForWidth).coerceIn(21f, 36f)

        views.setTextViewTextSize(R.id.widget_monthly_title, TypedValue.COMPLEX_UNIT_SP, (14f * scale).coerceIn(13f, 18f))
        views.setTextViewTextSize(R.id.widget_monthly_total, TypedValue.COMPLEX_UNIT_SP, totalSp)
        views.setTextViewTextSize(R.id.widget_monthly_meta, TypedValue.COMPLEX_UNIT_SP, (12.5f * scale).coerceIn(12f, 16f))
        views.setTextViewTextSize(R.id.widget_monthly_unpaid, TypedValue.COMPLEX_UNIT_SP, (12.5f * scale).coerceIn(12f, 16f))
    }

    private fun formatElapsed(elapsedSec: Long): String {
        val hours = elapsedSec / 3600L
        val mins = (elapsedSec % 3600L) / 60L
        val secs = elapsedSec % 60L
        return String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)
    }

    private fun openAppIntent(
        context: Context,
        requestCode: Int,
        tab: Int,
        openStopwatch: Boolean = false
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_TAB, tab)
            putExtra(EXTRA_OPEN_STOPWATCH, openStopwatch)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun actionBroadcastIntent(
        context: Context,
        requestCode: Int,
        action: String
    ): PendingIntent {
        val intent = Intent(context, com.albatora.app.service.TrackerNotificationReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun formatMinute(minuteOfDay: Int): String {
        val normalized = ((minuteOfDay % 1440) + 1440) % 1440
        return String.format(Locale.US, "%02d:%02d", normalized / 60, normalized % 60)
    }

    private fun dayLabel(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
        DayOfWeek.MONDAY -> "월"
        DayOfWeek.TUESDAY -> "화"
        DayOfWeek.WEDNESDAY -> "수"
        DayOfWeek.THURSDAY -> "목"
        DayOfWeek.FRIDAY -> "금"
        DayOfWeek.SATURDAY -> "토"
        DayOfWeek.SUNDAY -> "일"
    }
}
