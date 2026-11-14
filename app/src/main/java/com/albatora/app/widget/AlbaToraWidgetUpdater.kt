package com.albatora.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.albatora.app.MainActivity
import com.albatora.app.R
import com.albatora.app.data.AlbaToraStateHolder
import com.albatora.app.domain.PayrollCalculator
import com.albatora.app.model.ShiftStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.util.Locale

object AlbaToraWidgetUpdater {

    const val EXTRA_OPEN_TAB = "extra_open_tab"
    const val EXTRA_OPEN_STOPWATCH = "extra_open_stopwatch"

    fun updateAll(context: Context) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        updateLiveEarningsWidgets(appContext, manager)
        updateWeeklyScheduleWidgets(appContext, manager)
        updateMonthlySummaryWidgets(appContext, manager)
    }

    fun updateLiveEarningsOnly(context: Context) {
        val appContext = context.applicationContext
        val manager = AppWidgetManager.getInstance(appContext)
        updateLiveEarningsWidgets(appContext, manager)
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
        val snapshot = PayrollCalculator.evaluateLiveDashboard(state, System.currentTimeMillis())
        val activeShifts = snapshot.activeShifts

        val views = RemoteViews(context.packageName, R.layout.widget_live_earnings)
        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context, requestCode = 301, tab = 0))

        if (activeShifts.isNotEmpty()) {
            val first = activeShifts.first()
            val allPaused = activeShifts.all { it.isPaused }
            val emojiPrefix = if (first.emoji.isNotBlank()) "${first.emoji} " else ""
            val title = when {
                activeShifts.size > 1 -> "동시 근무 ${activeShifts.size}건 진행 중"
                allPaused -> "$emojiPrefix${first.title} · 휴게 중"
                else -> "$emojiPrefix${first.title} · 근무 중"
            }

            val perMinute = (snapshot.totalPerSecondRateWon * 60.0).toLong()
            val boostText = if (snapshot.anyBoostActive) " · ${snapshot.maxBoostMultiplier}배" else ""
            val rateText = if (allPaused) "일시정지" else "+${PayrollCalculator.formatWon(perMinute)}/분$boostText"

            val elapsedSec = activeShifts.maxOf { it.elapsedWorkMillis } / 1000L
            val hours = elapsedSec / 3600L
            val mins = (elapsedSec % 3600L) / 60L
            val secs = elapsedSec % 60L
            val elapsedFormatted = String.format(Locale.US, "%02d:%02d:%02d", hours, mins, secs)

            val selectedTangible = state.tangibleItems.find { it.id == state.settings.selectedTangibleItemId }
                ?: state.tangibleItems.firstOrNull()
            val tangibleSuffix = if (selectedTangible != null && selectedTangible.unitPrice > 0L) {
                val qty = snapshot.totalEarnedWon / selectedTangible.unitPrice.toDouble()
                " · ${selectedTangible.name} ${String.format(Locale.US, "%.1f", qty)}개"
            } else ""

            views.setTextViewText(R.id.widget_live_title, title)
            views.setTextViewText(R.id.widget_live_rate, rateText)
            views.setTextColor(
                R.id.widget_live_rate,
                ContextCompat.getColor(context, if (allPaused) R.color.widget_amber else R.color.widget_green)
            )
            views.setTextViewText(
                R.id.widget_live_amount,
                PayrollCalculator.formatWon(snapshot.totalEarnedWon.toLong())
            )
            views.setTextViewText(R.id.widget_live_sub, "실근무 $elapsedFormatted$tangibleSuffix")

            views.setTextViewText(R.id.widget_btn_primary, if (allPaused) "휴게 종료" else "휴게 시작")
            views.setOnClickPendingIntent(
                R.id.widget_btn_primary,
                actionBroadcastIntent(context, 311, AlbaToraStateHolder.ACTION_TOGGLE_BREAK)
            )

            views.setTextViewText(R.id.widget_btn_secondary, "퇴근 완료")
            views.setOnClickPendingIntent(
                R.id.widget_btn_secondary,
                actionBroadcastIntent(context, 312, AlbaToraStateHolder.ACTION_CLOCK_OUT)
            )
        } else {
            val month = YearMonth.now()
            val monthSummary = PayrollCalculator.calculateSettlementSummary(
                state,
                month.atDay(1),
                month.atEndOfMonth()
            )
            views.setTextViewText(R.id.widget_live_title, "알바토라 · 대기 중")
            views.setTextViewText(
                R.id.widget_live_rate,
                "${month.monthValue}월 ${monthSummary.shiftCount}건"
            )
            views.setTextColor(
                R.id.widget_live_rate,
                ContextCompat.getColor(context, R.color.widget_accent)
            )
            views.setTextViewText(
                R.id.widget_live_amount,
                PayrollCalculator.formatWon(monthSummary.totalNetPay)
            )
            views.setTextViewText(
                R.id.widget_live_sub,
                "이번 달 예상 누적 · 즉시 출근으로 타이머 시작"
            )

            views.setTextViewText(R.id.widget_btn_primary, "즉시 출근")
            views.setOnClickPendingIntent(
                R.id.widget_btn_primary,
                openAppIntent(context, requestCode = 313, tab = 0, openStopwatch = true)
            )

            views.setTextViewText(R.id.widget_btn_secondary, "달력 보기")
            views.setOnClickPendingIntent(
                R.id.widget_btn_secondary,
                openAppIntent(context, requestCode = 314, tab = 1)
            )
        }

        manager.updateAppWidget(ids, views)
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
            if (day >= today) {
                items.forEach { item ->
                    val dayTag = if (day == today) "오늘" else "${day.monthValue}/${day.dayOfMonth}(${dayLabel(day.dayOfWeek)})"
                    val emoji = if (item.emoji.isNotBlank()) "${item.emoji} " else ""
                    val timeRange = "${formatMinute(item.startMinute)}~${formatMinute(item.endMinute)}"
                    val liveTag = if (item.isCurrentlyLive) " · 진행 중" else ""
                    upcomingLines += "$dayTag · $emoji${item.title} · $timeRange$liveTag"
                }
            }
        }

        val views = RemoteViews(context.packageName, R.layout.widget_weekly_schedule)
        views.setOnClickPendingIntent(R.id.widget_weekly_root, openAppIntent(context, requestCode = 321, tab = 1))
        views.setTextViewText(R.id.widget_weekly_badge, "${totalCount}건")
        views.setTextViewText(
            R.id.widget_weekly_range,
            "${weekStart.monthValue}/${weekStart.dayOfMonth} ~ ${weekEnd.monthValue}/${weekEnd.dayOfMonth} 근무 일정"
        )

        val lineIds = intArrayOf(
            R.id.widget_weekly_line1,
            R.id.widget_weekly_line2,
            R.id.widget_weekly_line3,
            R.id.widget_weekly_line4
        )
        if (upcomingLines.isEmpty()) {
            views.setTextViewText(R.id.widget_weekly_line1, "남은 이번 주 예정된 근무가 없습니다")
            views.setTextViewText(R.id.widget_weekly_line2, "달력에서 원탭으로 단기·대타를 등록할 수 있어요")
            views.setTextViewText(R.id.widget_weekly_line3, "")
            views.setTextViewText(R.id.widget_weekly_line4, "")
        } else {
            lineIds.forEachIndexed { idx, viewId ->
                views.setTextViewText(viewId, upcomingLines.getOrElse(idx) { "" })
            }
        }

        manager.updateAppWidget(ids, views)
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
        val month = YearMonth.now()
        val summary = PayrollCalculator.calculateSettlementSummary(
            state,
            month.atDay(1),
            month.atEndOfMonth()
        )
        val live = PayrollCalculator.evaluateLiveDashboard(state, System.currentTimeMillis())
        val totalWithLive = summary.totalNetPay + live.totalEarnedWon.toLong()
        val unpaid = PayrollCalculator.calculateGlobalUnpaidSummary(state)

        val views = RemoteViews(context.packageName, R.layout.widget_monthly_summary)
        views.setOnClickPendingIntent(R.id.widget_monthly_root, openAppIntent(context, requestCode = 331, tab = 2))
        views.setTextViewText(R.id.widget_monthly_title, "${month.monthValue}월 예상 누적")
        views.setTextViewText(R.id.widget_monthly_total, PayrollCalculator.formatWon(totalWithLive))
        views.setTextViewText(
            R.id.widget_monthly_meta,
            "완료 ${summary.shiftCount}건 · ${PayrollCalculator.formatDurationKorean(summary.totalWorkMinutes)}"
        )

        if (unpaid.first > 0L) {
            views.setTextViewText(
                R.id.widget_monthly_unpaid,
                "미수금 ${unpaid.second.size}건 · ${PayrollCalculator.formatWon(unpaid.first)}"
            )
            views.setTextColor(R.id.widget_monthly_unpaid, ContextCompat.getColor(context, R.color.widget_red))
        } else {
            views.setTextViewText(R.id.widget_monthly_unpaid, "미수금 없음")
            views.setTextColor(R.id.widget_monthly_unpaid, ContextCompat.getColor(context, R.color.widget_accent))
        }

        manager.updateAppWidget(ids, views)
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
