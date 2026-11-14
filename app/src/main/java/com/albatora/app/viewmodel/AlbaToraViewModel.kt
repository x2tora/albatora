package com.albatora.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.albatora.app.data.AlbaToraStateHolder
import com.albatora.app.domain.LiveDashboardSnapshot
import com.albatora.app.domain.PayrollCalculator
import com.albatora.app.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

class AlbaToraViewModel(application: Application) : AndroidViewModel(application) {

    val stateHolder = AlbaToraStateHolder.getInstance(application)
    val state: StateFlow<AlbaToraDataState> = stateHolder.state

    private val _nowEpochMillis = MutableStateFlow(System.currentTimeMillis())
    val nowEpochMillis: StateFlow<Long> = _nowEpochMillis.asStateFlow()

    val liveDashboard: StateFlow<LiveDashboardSnapshot> = combine(
        state,
        nowEpochMillis
    ) { st, nowMs ->
        PayrollCalculator.evaluateLiveDashboard(st, nowMs)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = PayrollCalculator.evaluateLiveDashboard(state.value, System.currentTimeMillis())
    )

    init {
        viewModelScope.launch {
            stateHolder.syncForegroundService()
            while (isActive) {
                delay(100L) // Q4=B: 0.1초(100ms) 단위 끊김 없는 실시간 적립 틱
                _nowEpochMillis.value = System.currentTimeMillis()
            }
        }
    }

    fun upsertJob(job: Job) = stateHolder.upsertJob(job)
    fun deleteJob(jobId: String, deleteRecords: Boolean = false) = stateHolder.deleteJob(jobId, deleteRecords)

    fun upsertRecord(record: WorkRecord) = stateHolder.upsertRecord(record)
    fun deleteRecord(recordId: String) = stateHolder.deleteRecord(recordId)
    fun togglePaymentStatus(recordId: String) = stateHolder.toggleRecordPaymentStatus(recordId)

    fun extendOneHour(baseRecord: WorkRecord) = stateHolder.applyExtendOneHour(baseRecord)
    fun markEarlyLeave(baseRecord: WorkRecord, earlyMinutes: Int = 60) = stateHolder.applyEarlyLeave(baseRecord, earlyMinutes)
    fun markAbsent(baseRecord: WorkRecord) = stateHolder.applyAbsent(baseRecord)
    fun addSubstituteFromJob(
        job: Job,
        date: LocalDate,
        startMinute: Int,
        endMinute: Int,
        breakMinutes: Int,
        breakPayType: BreakPayType
    ) = stateHolder.addSubstituteFromJob(job, date, startMinute, endMinute, breakMinutes, breakPayType)

    fun upsertTemplate(template: ShortTermTemplate) = stateHolder.upsertTemplate(template)
    fun deleteTemplate(templateId: String) = stateHolder.deleteTemplate(templateId)
    fun stampTemplateOnDate(template: ShortTermTemplate, date: LocalDate) = stateHolder.stampTemplateOnDate(template, date)

    fun startStopwatch(
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
    ) = stateHolder.startManualStopwatch(
        title = title,
        emoji = emoji,
        kind = kind,
        payType = payType,
        unitAmount = unitAmount,
        jobId = jobId,
        colorHex = colorHex,
        plannedDurationMinutes = plannedDurationMinutes,
        unpaidBreakMinutes = unpaidBreakMinutes,
        breakPayType = breakPayType,
        fixedBreakStartMinute = fixedBreakStartMinute,
        fixedBreakEndMinute = fixedBreakEndMinute,
        allowance = allowance,
        taxMode = taxMode,
        customTaxRatePercent = customTaxRatePercent,
        dailyWorkerIncludeLocalTax = dailyWorkerIncludeLocalTax
    )

    fun toggleBreak(runtimeKey: String) = stateHolder.toggleBreakForShift(runtimeKey)
    fun clockOut(runtimeKey: String) = stateHolder.clockOutShift(runtimeKey)

    fun upsertTangibleItem(item: TangibleItem) = stateHolder.upsertTangibleItem(item)
    fun deleteTangibleItem(itemId: String) = stateHolder.deleteTangibleItem(itemId)
    fun selectTangibleItem(itemId: String) = stateHolder.selectTangibleItem(itemId)

    fun upsertWishlistGoal(goal: WishlistGoal) = stateHolder.upsertWishlistGoal(goal)
    fun deleteWishlistGoal(goalId: String) = stateHolder.deleteWishlistGoal(goalId)

    fun updateTheme(option: ThemeOption) = stateHolder.updateSettings { it.copy(themeOption = option) }
    fun updateMonet(enabled: Boolean) = stateHolder.updateSettings { it.copy(monetEnabled = enabled) }
    fun updateCustomKeyColor(colorHex: Long?) = stateHolder.updateSettings { it.copy(customKeyColorHex = colorHex) }
    fun updateOngoingNotification(enabled: Boolean) = stateHolder.updateSettings { it.copy(ongoingNotificationEnabled = enabled) }
    fun updateDefaultMinWage(wage: Long) = stateHolder.updateSettings { it.copy(defaultMinWage = wage.coerceAtLeast(1L)) }

    fun exportBackupJson(): String = stateHolder.exportBackupJson()
    fun importBackupJson(raw: String): Result<Unit> = stateHolder.importBackupJson(raw)
    fun resetAllData() = stateHolder.resetAllData()
}
