package com.albatora.app.data

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.albatora.app.domain.PayrollCalculator
import com.albatora.app.model.*
import com.albatora.app.service.AutoDndController
import com.albatora.app.service.LiveTrackerForegroundService
import com.albatora.app.widget.AlbaToraWidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.*
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

class AlbaToraStateHolder private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val repository = AlbaToraRepository(appContext)

    private val _state = MutableStateFlow(repository.load())
    val state: StateFlow<AlbaToraDataState> = _state.asStateFlow()

    private fun updateAndPersist(transform: (AlbaToraDataState) -> AlbaToraDataState) {
        var latestState: AlbaToraDataState? = null
        _state.update { current ->
            transform(current).also { next ->
                latestState = next
            }
        }
        latestState?.let { next ->
            repository.save(next)
            syncForegroundService(next)
            AlbaToraWidgetUpdater.updateAll(appContext)
        }
    }

    fun syncForegroundService(currentState: AlbaToraDataState = _state.value) {
        val live = PayrollCalculator.evaluateLiveDashboard(currentState, System.currentTimeMillis())
        AutoDndController.sync(appContext, currentState.settings, live)
        if (!currentState.settings.ongoingNotificationEnabled && !currentState.settings.autoDndEnabled) {
            LiveTrackerForegroundService.stop(appContext)
            return
        }
        if (live.activeShifts.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                if (!granted) return
            }
            LiveTrackerForegroundService.startOrUpdate(appContext)
        } else {
            LiveTrackerForegroundService.stop(appContext)
        }
    }

    // Jobs
    fun upsertJob(job: Job) {
        updateAndPersist { st ->
            val exists = st.jobs.any { it.id == job.id }
            val nextJobs = if (exists) {
                st.jobs.map { if (it.id == job.id) job else it }
            } else {
                st.jobs + job
            }
            st.copy(jobs = nextJobs)
        }
    }

    fun deleteJob(jobId: String, deleteRecords: Boolean = false) {
        updateAndPersist { st ->
            st.copy(
                jobs = st.jobs.filterNot { it.id == jobId },
                records = if (deleteRecords) st.records.filterNot { it.jobId == jobId } else st.records,
                activeStopwatches = st.activeStopwatches.filterNot { it.jobId == jobId },
                scheduledRuntimes = st.scheduledRuntimes.filterNot { it.jobId == jobId }
            )
        }
    }

    // Work records
    fun upsertRecord(record: WorkRecord) {
        updateAndPersist { st ->
            val exists = st.records.any { it.id == record.id }
            val next = if (exists) {
                st.records.map { if (it.id == record.id) record else it }
            } else {
                st.records + record
            }
            st.copy(records = next)
        }
    }

    fun deleteRecord(recordId: String) {
        updateAndPersist { st ->
            st.copy(records = st.records.filterNot { it.id == recordId })
        }
    }

    fun toggleRecordPaymentStatus(recordId: String) {
        val todayIso = PayrollCalculator.formatDate(LocalDate.now())
        updateAndPersist { st ->
            val next = st.records.map { r ->
                if (r.id == recordId) {
                    val nextStatus = if (r.paymentStatus == PaymentStatus.UNPAID) PaymentStatus.PAID else PaymentStatus.UNPAID
                    r.copy(
                        paymentStatus = nextStatus,
                        paidAtDate = if (nextStatus == PaymentStatus.PAID) todayIso else null
                    )
                } else r
            }
            st.copy(records = next)
        }
    }

    // Calendar quick actions
    fun applyExtendOneHour(baseRecord: WorkRecord) {
        val dur = PayrollCalculator.rawDurationMinutes(baseRecord.startMinute, baseRecord.endMinute)
        val newDur = dur + 60
        val newEndMinute = (baseRecord.startMinute + newDur) % 1440
        val nextId = if (baseRecord.id.startsWith("cal_") || baseRecord.id.startsWith("virtual_")) {
            UUID.randomUUID().toString()
        } else {
            baseRecord.id
        }
        val updated = baseRecord.copy(
            id = nextId,
            endMinute = newEndMinute,
            status = ShiftStatus.OVERTIME,
            isScheduledOverride = true
        )
        upsertRecord(updated)
    }

    fun applyEarlyLeave(baseRecord: WorkRecord, earlyMinutes: Int = 60) {
        val dur = PayrollCalculator.rawDurationMinutes(baseRecord.startMinute, baseRecord.endMinute)
        val reducedDur = max(30, dur - earlyMinutes)
        val newEndMinute = (baseRecord.startMinute + reducedDur) % 1440
        val nextId = if (baseRecord.id.startsWith("cal_") || baseRecord.id.startsWith("virtual_")) {
            UUID.randomUUID().toString()
        } else {
            baseRecord.id
        }
        val updated = baseRecord.copy(
            id = nextId,
            endMinute = newEndMinute,
            status = ShiftStatus.EARLY_LEAVE,
            isScheduledOverride = true
        )
        upsertRecord(updated)
    }

    fun applyAbsent(baseRecord: WorkRecord) {
        val nextId = if (baseRecord.id.startsWith("cal_") || baseRecord.id.startsWith("virtual_")) {
            UUID.randomUUID().toString()
        } else {
            baseRecord.id
        }
        val updated = baseRecord.copy(
            id = nextId,
            status = ShiftStatus.ABSENT,
            isScheduledOverride = true
        )
        upsertRecord(updated)
    }

    fun addSubstituteFromJob(
        job: Job,
        date: LocalDate,
        startMinute: Int,
        endMinute: Int,
        breakMinutes: Int,
        breakPayType: BreakPayType
    ) {
        val wage = PayrollCalculator.effectiveJobWage(job, date)
        val rec = WorkRecord(
            id = UUID.randomUUID().toString(),
            jobId = job.id,
            title = "${job.name} (대타)",
            emoji = job.emoji,
            kind = job.kind,
            colorHex = job.colorHex,
            date = PayrollCalculator.formatDate(date),
            startMinute = startMinute,
            endMinute = endMinute,
            status = ShiftStatus.SUBSTITUTE,
            payType = job.payType,
            unitAmount = wage,
            breakMinutes = breakMinutes,
            breakPayType = breakPayType,
            taxMode = job.taxMode,
            customTaxRatePercent = job.customTaxRatePercent,
            dailyWorkerIncludeLocalTax = job.dailyWorkerIncludeLocalTax,
            allowance = job.allowance,
            paymentStatus = if (job.kind == JobKind.SHORT_TERM) PaymentStatus.UNPAID else PaymentStatus.PAID
        )
        upsertRecord(rec)
    }

    // Short-term templates
    fun upsertTemplate(template: ShortTermTemplate) {
        updateAndPersist { st ->
            val exists = st.templates.any { it.id == template.id }
            val next = if (exists) {
                st.templates.map { if (it.id == template.id) template else it }
            } else {
                st.templates + template
            }
            st.copy(templates = next)
        }
    }

    fun deleteTemplate(templateId: String) {
        updateAndPersist { st ->
            st.copy(templates = st.templates.filterNot { it.id == templateId })
        }
    }

    fun stampTemplateOnDate(template: ShortTermTemplate, date: LocalDate): String {
        val newId = UUID.randomUUID().toString()
        val rec = WorkRecord(
            id = newId,
            jobId = null,
            title = template.name,
            emoji = template.emoji,
            kind = JobKind.SHORT_TERM,
            colorHex = template.colorHex,
            date = PayrollCalculator.formatDate(date),
            startMinute = template.startMinute,
            endMinute = template.endMinute,
            status = ShiftStatus.NORMAL,
            payType = template.payType,
            unitAmount = template.unitAmount,
            breakMinutes = template.breakMinutes,
            breakPayType = template.breakPayType,
            transportExpense = template.transportExpense,
            mealExpense = template.mealExpense,
            siteBonus = template.siteBonus,
            agencyFeeValue = template.agencyFeeValue,
            agencyFeeType = template.agencyFeeType,
            taxMode = template.taxMode,
            customTaxRatePercent = template.customTaxRatePercent,
            dailyWorkerIncludeLocalTax = template.dailyWorkerIncludeLocalTax,
            allowance = template.allowance,
            paymentStatus = PaymentStatus.UNPAID,
            memo = template.memo
        )
        upsertRecord(rec)
        return newId
    }

    // Live tracker actions
    fun startManualStopwatch(
        title: String,
        emoji: String = "",
        kind: JobKind,
        payType: PayType,
        unitAmount: Long,
        jobId: String? = null,
        colorHex: Long = 0xFF3482FF,
        plannedDurationMinutes: Int? = null,
        unpaidBreakMinutes: Int = 0,
        breakPayType: BreakPayType = BreakPayType.UNPAID,
        fixedBreakStartMinute: Int? = null,
        fixedBreakEndMinute: Int? = null,
        allowance: AllowanceSettings = AllowanceSettings(),
        taxMode: TaxMode = TaxMode.NONE,
        customTaxRatePercent: Double = 0.0,
        dailyWorkerIncludeLocalTax: Boolean = true
    ) {
        val now = System.currentTimeMillis()
        val plannedEnd = plannedDurationMinutes?.takeIf { it > 0 }?.let { now + it * 60_000L }
        val session = ActiveStopwatchSession(
            id = UUID.randomUUID().toString(),
            jobId = jobId,
            title = title,
            emoji = emoji,
            kind = kind,
            colorHex = colorHex,
            payType = payType,
            unitAmount = unitAmount,
            startEpochMillis = now,
            plannedEndEpochMillis = plannedEnd,
            unpaidBreakMinutes = unpaidBreakMinutes,
            breakPayType = breakPayType,
            fixedBreakStartMinute = fixedBreakStartMinute,
            fixedBreakEndMinute = fixedBreakEndMinute,
            allowance = allowance,
            taxMode = taxMode,
            customTaxRatePercent = customTaxRatePercent,
            dailyWorkerIncludeLocalTax = dailyWorkerIncludeLocalTax
        )
        updateAndPersist { st ->
            st.copy(activeStopwatches = st.activeStopwatches + session)
        }
    }

    fun toggleBreakForShift(runtimeKey: String) {
        val now = System.currentTimeMillis()
        updateAndPersist { st ->
            val stopwatch = st.activeStopwatches.find { it.id == runtimeKey }
            if (stopwatch != null) {
                val updatedList = st.activeStopwatches.map { s ->
                    if (s.id != runtimeKey) s
                    else if (!s.isPaused) {
                        s.copy(isPaused = true, pausedAtEpochMillis = now)
                    } else {
                        val pauseDuration = (now - (s.pausedAtEpochMillis ?: now)).coerceAtLeast(0L)
                        s.copy(
                            isPaused = false,
                            pausedAtEpochMillis = null,
                            accumulatedManualPauseMillis = s.accumulatedManualPauseMillis + pauseDuration
                        )
                    }
                }
                st.copy(activeStopwatches = updatedList)
            } else {
                // Auto-scheduled runtime key = "${jobId}_${startEpochMillis}"
                val jobId = runtimeKey.substringBeforeLast("_")
                val existing = st.scheduledRuntimes.find { it.key == runtimeKey }
                val nextRuntime = if (existing == null) {
                    ScheduledShiftRuntime(
                        key = runtimeKey,
                        jobId = jobId,
                        isManualPaused = true,
                        manualPausedAtEpochMillis = now
                    )
                } else if (!existing.isManualPaused) {
                    existing.copy(isManualPaused = true, manualPausedAtEpochMillis = now)
                } else {
                    val pauseDuration = (now - (existing.manualPausedAtEpochMillis ?: now)).coerceAtLeast(0L)
                    existing.copy(
                        isManualPaused = false,
                        manualPausedAtEpochMillis = null,
                        accumulatedManualPauseMillis = existing.accumulatedManualPauseMillis + pauseDuration
                    )
                }
                val filtered = st.scheduledRuntimes.filterNot { it.key == runtimeKey }
                st.copy(scheduledRuntimes = filtered + nextRuntime)
            }
        }
    }

    fun restoreScheduledShift(runtimeKey: String) {
        updateAndPersist { st ->
            val nextRuntimes = st.scheduledRuntimes.map { rt ->
                if (rt.key == runtimeKey) {
                    rt.copy(
                        clockedOutAtEpochMillis = null,
                        isManualPaused = false,
                        manualPausedAtEpochMillis = null
                    )
                } else {
                    rt
                }
            }
            st.copy(scheduledRuntimes = nextRuntimes)
        }
    }

    fun clockOutShift(runtimeKey: String) {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        updateAndPersist { st ->
            val stopwatch = st.activeStopwatches.find { it.id == runtimeKey }
            if (stopwatch != null) {
                val effectiveEndMs = if (stopwatch.isPaused && stopwatch.pausedAtEpochMillis != null) {
                    stopwatch.pausedAtEpochMillis
                } else now
                val extraPause = if (stopwatch.isPaused && stopwatch.pausedAtEpochMillis != null) {
                    (now - stopwatch.pausedAtEpochMillis).coerceAtLeast(0L)
                } else 0L
                val startDt = LocalDateTime.ofInstant(Instant.ofEpochMilli(stopwatch.startEpochMillis), zone)
                val endDt = LocalDateTime.ofInstant(Instant.ofEpochMilli(max(stopwatch.startEpochMillis + 60_000L, effectiveEndMs)), zone)
                val startMin = startDt.hour * 60 + startDt.minute
                var endMin = endDt.hour * 60 + endDt.minute
                if (endMin == startMin) endMin = (startMin + 1) % 1440
                val manualPauseMins = ((stopwatch.accumulatedManualPauseMillis + extraPause) / 60_000.0).roundToInt()
                val totalBreakMins = (stopwatch.unpaidBreakMinutes + manualPauseMins).coerceAtLeast(0)
                val rec = WorkRecord(
                    id = UUID.randomUUID().toString(),
                    jobId = stopwatch.jobId,
                    title = stopwatch.title,
                    emoji = stopwatch.emoji,
                    kind = stopwatch.kind,
                    colorHex = stopwatch.colorHex,
                    date = PayrollCalculator.formatDate(startDt.toLocalDate()),
                    startMinute = startMin,
                    endMinute = endMin,
                    status = ShiftStatus.NORMAL,
                    payType = stopwatch.payType,
                    unitAmount = stopwatch.unitAmount,
                    breakMinutes = totalBreakMins,
                    breakPayType = if (totalBreakMins > 0 && stopwatch.breakPayType == BreakPayType.PAID && manualPauseMins > 0) BreakPayType.UNPAID else stopwatch.breakPayType,
                    fixedBreakStartMinute = stopwatch.fixedBreakStartMinute,
                    fixedBreakEndMinute = stopwatch.fixedBreakEndMinute,
                    transportExpense = stopwatch.transportExpense,
                    mealExpense = stopwatch.mealExpense,
                    siteBonus = stopwatch.siteBonus,
                    agencyFeeValue = stopwatch.agencyFeeValue,
                    agencyFeeType = stopwatch.agencyFeeType,
                    taxMode = stopwatch.taxMode,
                    customTaxRatePercent = stopwatch.customTaxRatePercent,
                    dailyWorkerIncludeLocalTax = stopwatch.dailyWorkerIncludeLocalTax,
                    allowance = stopwatch.allowance,
                    paymentStatus = if (stopwatch.kind == JobKind.SHORT_TERM) PaymentStatus.UNPAID else PaymentStatus.PAID
                )
                st.copy(
                    activeStopwatches = st.activeStopwatches.filterNot { it.id == runtimeKey },
                    records = st.records + rec
                )
            } else {
                // Early clock-out of auto-scheduled regular shift
                val jobId = runtimeKey.substringBeforeLast("_")
                val startMs = runtimeKey.substringAfterLast("_").toLongOrNull() ?: now
                val job = st.jobs.find { it.id == jobId }
                val startDt = LocalDateTime.ofInstant(Instant.ofEpochMilli(startMs), zone)
                val endDt = LocalDateTime.ofInstant(Instant.ofEpochMilli(max(startMs + 60_000L, now)), zone)
                val startMin = startDt.hour * 60 + startDt.minute
                var endMin = endDt.hour * 60 + endDt.minute
                if (endMin == startMin) endMin = (startMin + 1) % 1440

                val existingRt = st.scheduledRuntimes.find { it.key == runtimeKey }
                val nextRt = (existingRt ?: ScheduledShiftRuntime(runtimeKey, jobId)).copy(
                    isManualPaused = false,
                    clockedOutAtEpochMillis = now
                )
                val nextRecords = if (job != null) {
                    val manualPauseMins = ((existingRt?.accumulatedManualPauseMillis ?: 0L) / 60_000.0).roundToInt()
                    val rec = WorkRecord(
                        id = UUID.randomUUID().toString(),
                        jobId = job.id,
                        title = job.name,
                        emoji = job.emoji,
                        kind = JobKind.REGULAR,
                        colorHex = job.colorHex,
                        date = PayrollCalculator.formatDate(startDt.toLocalDate()),
                        startMinute = startMin,
                        endMinute = endMin,
                        status = ShiftStatus.EARLY_LEAVE,
                        payType = job.payType,
                        unitAmount = PayrollCalculator.effectiveJobWage(job, startDt.toLocalDate()),
                        breakMinutes = manualPauseMins,
                        breakPayType = BreakPayType.UNPAID,
                        taxMode = job.taxMode,
                        customTaxRatePercent = job.customTaxRatePercent,
                        dailyWorkerIncludeLocalTax = job.dailyWorkerIncludeLocalTax,
                        allowance = job.allowance,
                        isScheduledOverride = true,
                        paymentStatus = PaymentStatus.PAID
                    )
                    st.records + rec
                } else st.records

                st.copy(
                    scheduledRuntimes = st.scheduledRuntimes.filterNot { it.key == runtimeKey } + nextRt,
                    records = nextRecords
                )
            }
        }
    }

    fun handleNotificationAction(action: String) {
        if (action == ACTION_QUICK_CLOCK_IN) {
            val st = _state.value
            val jobId = st.settings.widgetConfig.defaultQuickJobId
            val targetJob = st.jobs.find { it.id == jobId } ?: st.jobs.firstOrNull()
            if (targetJob != null) {
                val wage = PayrollCalculator.effectiveJobWage(targetJob, LocalDate.now())
                startManualStopwatch(
                    title = targetJob.name,
                    emoji = targetJob.emoji,
                    kind = targetJob.kind,
                    payType = targetJob.payType,
                    unitAmount = wage,
                    jobId = targetJob.id,
                    colorHex = targetJob.colorHex,
                    allowance = targetJob.allowance,
                    taxMode = targetJob.taxMode,
                    customTaxRatePercent = targetJob.customTaxRatePercent,
                    dailyWorkerIncludeLocalTax = targetJob.dailyWorkerIncludeLocalTax
                )
            }
            return
        }
        val live = PayrollCalculator.evaluateLiveDashboard(_state.value, System.currentTimeMillis())
        val target = live.activeShifts.firstOrNull() ?: return
        when (action) {
            ACTION_TOGGLE_BREAK -> toggleBreakForShift(target.runtimeKey)
            ACTION_CLOCK_OUT -> clockOutShift(target.runtimeKey)
        }
    }

    // Tangible items
    fun upsertTangibleItem(item: TangibleItem) {
        updateAndPersist { st ->
            val exists = st.tangibleItems.any { it.id == item.id }
            val next = if (exists) {
                st.tangibleItems.map { if (it.id == item.id) item else it }
            } else {
                st.tangibleItems + item
            }
            val selectedId = if (next.any { it.id == st.settings.selectedTangibleItemId }) {
                st.settings.selectedTangibleItemId
            } else {
                item.id
            }
            st.copy(
                tangibleItems = next,
                settings = st.settings.copy(selectedTangibleItemId = selectedId)
            )
        }
    }

    fun deleteTangibleItem(itemId: String) {
        updateAndPersist { st ->
            val filtered = st.tangibleItems.filterNot { it.id == itemId }
            val selectedId = if (st.settings.selectedTangibleItemId == itemId) {
                filtered.firstOrNull()?.id.orEmpty()
            } else {
                st.settings.selectedTangibleItemId
            }
            st.copy(
                tangibleItems = filtered,
                settings = st.settings.copy(selectedTangibleItemId = selectedId)
            )
        }
    }

    fun selectTangibleItem(itemId: String) {
        updateAndPersist { st ->
            st.copy(settings = st.settings.copy(selectedTangibleItemId = itemId))
        }
    }

    // Wishlist goals
    fun upsertWishlistGoal(goal: WishlistGoal) {
        updateAndPersist { st ->
            val exists = st.wishlistGoals.any { it.id == goal.id }
            val next = if (exists) {
                st.wishlistGoals.map { if (it.id == goal.id) goal else it }
            } else {
                st.wishlistGoals + goal
            }
            st.copy(wishlistGoals = next)
        }
    }

    fun deleteWishlistGoal(goalId: String) {
        updateAndPersist { st ->
            st.copy(wishlistGoals = st.wishlistGoals.filterNot { it.id == goalId })
        }
    }

    fun archiveWishlistGoal(goalId: String, achievedAmount: Long) {
        updateAndPersist { st ->
            val goal = st.wishlistGoals.find { it.id == goalId } ?: return@updateAndPersist st
            val totalMinutes = st.records.sumOf {
                PayrollCalculator.calculateShiftFinancials(it).paidWorkMinutes
            }
            val totalNet = st.records.sumOf {
                PayrollCalculator.calculateShiftFinancials(it).netPay
            }
            val effectiveHourly = if (totalMinutes > 0 && totalNet > 0L) {
                (totalNet.toDouble() / (totalMinutes / 60.0)).coerceAtLeast(1.0)
            } else {
                st.settings.defaultMinWage.toDouble().coerceAtLeast(1.0)
            }
            val estimatedHours = (goal.targetAmount.toDouble() / effectiveHourly).coerceAtLeast(0.1)
            val archived = ArchivedGoalAchievement(
                id = UUID.randomUUID().toString(),
                title = goal.title,
                emoji = goal.emoji,
                targetAmount = goal.targetAmount,
                achievedAmount = achievedAmount.coerceAtLeast(goal.targetAmount),
                estimatedWorkHours = estimatedHours,
                achievedAtMillis = System.currentTimeMillis()
            )
            st.copy(
                wishlistGoals = st.wishlistGoals.filterNot { it.id == goalId },
                archivedGoals = listOf(archived) + st.archivedGoals
            )
        }
    }

    fun deleteArchivedGoal(archiveId: String) {
        updateAndPersist { st ->
            st.copy(archivedGoals = st.archivedGoals.filterNot { it.id == archiveId })
        }
    }

    // Settings and backup
    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        updateAndPersist { st ->
            st.copy(settings = transform(st.settings))
        }
    }

    fun exportBackupJson(): String = repository.exportToJson(_state.value)

    fun previewBackupJson(rawJson: String): Result<AlbaToraDataState> = repository.importFromJson(rawJson)

    fun importBackupJson(rawJson: String): Result<Unit> {
        val result = repository.importFromJson(rawJson)
        return result.map { imported ->
            _state.value = imported
            repository.save(imported)
            syncForegroundService(imported)
            AlbaToraWidgetUpdater.updateAll(appContext)
        }
    }

    fun resetAllData() {
        val fresh = AlbaToraDataState.initialEmptyState().copy(
            settings = _state.value.settings
        )
        _state.value = fresh
        repository.save(fresh)
        syncForegroundService(fresh)
        AlbaToraWidgetUpdater.updateAll(appContext)
    }

    companion object {
        const val ACTION_TOGGLE_BREAK = "com.albatora.app.ACTION_TOGGLE_BREAK"
        const val ACTION_CLOCK_OUT = "com.albatora.app.ACTION_CLOCK_OUT"
        const val ACTION_QUICK_CLOCK_IN = "com.albatora.app.ACTION_QUICK_CLOCK_IN"

        @Volatile
        private var INSTANCE: AlbaToraStateHolder? = null

        fun getInstance(context: Context): AlbaToraStateHolder =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AlbaToraStateHolder(context).also { INSTANCE = it }
            }
    }
}
