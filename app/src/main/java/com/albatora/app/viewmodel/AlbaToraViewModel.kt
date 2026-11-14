package com.albatora.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.albatora.app.BuildConfig
import com.albatora.app.data.AlbaToraStateHolder
import com.albatora.app.domain.LiveDashboardSnapshot
import com.albatora.app.domain.PayrollCalculator
import com.albatora.app.model.*
import com.albatora.app.ui.components.AppUpdatePhase
import com.albatora.app.ui.components.AppUpdateUiState
import com.albatora.app.update.AppUpdater
import com.albatora.app.update.InstallLaunchOutcome
import com.albatora.app.update.UpdateCheckOutcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
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

    private val _updateUiState = MutableStateFlow(
        AppUpdateUiState(currentVersionName = BuildConfig.VERSION_NAME)
    )
    val updateUiState: StateFlow<AppUpdateUiState> = _updateUiState.asStateFlow()
    private var pendingDownloadedApk: File? = null

    init {
        AppUpdater.cleanupCache(application)
        checkForUpdate(manual = false)
        viewModelScope.launch {
            stateHolder.syncForegroundService()
            while (isActive) {
                delay(100L)
                _nowEpochMillis.value = System.currentTimeMillis()
            }
        }
    }

    fun checkForUpdate(manual: Boolean = false, onFeedback: ((String) -> Unit)? = null) {
        if (_updateUiState.value.isChecking) return
        viewModelScope.launch {
            _updateUiState.update {
                it.copy(
                    isChecking = true,
                    lastStatusNote = if (manual) "최신 버전을 확인하는 중..." else it.lastStatusNote
                )
            }
            when (val outcome = AppUpdater.checkLatestRelease(BuildConfig.VERSION_NAME)) {
                is UpdateCheckOutcome.UpdateAvailable -> {
                    _updateUiState.update {
                        it.copy(
                            isChecking = false,
                            showDialog = true,
                            phase = AppUpdatePhase.Available(outcome.release),
                            lastStatusNote = "새 버전 v${outcome.release.versionName} 사용 가능"
                        )
                    }
                }
                is UpdateCheckOutcome.UpToDate -> {
                    val msg = "현재 최신 버전(v${BuildConfig.VERSION_NAME})을 사용 중입니다."
                    _updateUiState.update {
                        it.copy(
                            isChecking = false,
                            lastStatusNote = msg
                        )
                    }
                    if (manual) onFeedback?.invoke(msg)
                }
                is UpdateCheckOutcome.Failed -> {
                    val msg = if (manual) "업데이트 확인 실패: ${outcome.message}" else null
                    _updateUiState.update {
                        it.copy(
                            isChecking = false,
                            lastStatusNote = msg ?: it.lastStatusNote
                        )
                    }
                    if (manual && msg != null) onFeedback?.invoke(msg)
                }
            }
        }
    }

    fun startUpdateDownloadAndInstall() {
        val release = when (val p = _updateUiState.value.phase) {
            is AppUpdatePhase.Available -> p.release
            is AppUpdatePhase.Error -> p.release
            else -> null
        } ?: return

        viewModelScope.launch {
            _updateUiState.update {
                it.copy(
                    showDialog = true,
                    phase = AppUpdatePhase.Downloading(
                        release = release,
                        progress = 0f,
                        downloadedBytes = 0L,
                        totalBytes = release.selectedAsset.sizeBytes
                    )
                )
            }
            val result = AppUpdater.downloadApk(
                context = getApplication(),
                asset = release.selectedAsset
            ) { progress, downloaded, total ->
                _updateUiState.update { st ->
                    if (st.phase is AppUpdatePhase.Downloading) {
                        st.copy(
                            phase = AppUpdatePhase.Downloading(
                                release = release,
                                progress = progress,
                                downloadedBytes = downloaded,
                                totalBytes = total
                            )
                        )
                    } else st
                }
            }

            result.fold(
                onSuccess = { apkFile ->
                    pendingDownloadedApk = apkFile
                    when (AppUpdater.launchApkInstaller(getApplication(), apkFile)) {
                        InstallLaunchOutcome.LAUNCHED_INSTALLER -> {
                            _updateUiState.update {
                                it.copy(
                                    showDialog = true,
                                    phase = AppUpdatePhase.ReadyToInstall(release, needsPermission = false)
                                )
                            }
                        }
                        InstallLaunchOutcome.NEEDS_UNKNOWN_SOURCE_PERMISSION -> {
                            _updateUiState.update {
                                it.copy(
                                    showDialog = true,
                                    phase = AppUpdatePhase.ReadyToInstall(release, needsPermission = true)
                                )
                            }
                        }
                        InstallLaunchOutcome.FAILED -> {
                            _updateUiState.update {
                                it.copy(
                                    showDialog = true,
                                    phase = AppUpdatePhase.Error(release, "설치 화면을 실행하지 못했습니다.")
                                )
                            }
                        }
                    }
                },
                onFailure = { err ->
                    _updateUiState.update {
                        it.copy(
                            showDialog = true,
                            phase = AppUpdatePhase.Error(
                                release = release,
                                message = err.localizedMessage ?: "다운로드 중 오류가 발생했습니다."
                            )
                        )
                    }
                }
            )
        }
    }

    fun openInstallUnknownSourcesSettings() {
        AppUpdater.openInstallPermissionSettings(getApplication())
    }

    fun retryApkInstall() {
        val file = pendingDownloadedApk ?: return
        val release = when (val p = _updateUiState.value.phase) {
            is AppUpdatePhase.ReadyToInstall -> p.release
            is AppUpdatePhase.Available -> p.release
            else -> null
        } ?: return

        when (AppUpdater.launchApkInstaller(getApplication(), file)) {
            InstallLaunchOutcome.LAUNCHED_INSTALLER -> {
                _updateUiState.update {
                    it.copy(phase = AppUpdatePhase.ReadyToInstall(release, needsPermission = false))
                }
            }
            InstallLaunchOutcome.NEEDS_UNKNOWN_SOURCE_PERMISSION -> {
                _updateUiState.update {
                    it.copy(phase = AppUpdatePhase.ReadyToInstall(release, needsPermission = true))
                }
            }
            InstallLaunchOutcome.FAILED -> {
                _updateUiState.update {
                    it.copy(phase = AppUpdatePhase.Error(release, "설치 화면을 실행하지 못했습니다."))
                }
            }
        }
    }

    fun dismissUpdateDialog() {
        _updateUiState.update { it.copy(showDialog = false) }
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
    fun restoreScheduledShift(runtimeKey: String) = stateHolder.restoreScheduledShift(runtimeKey)

    fun upsertTangibleItem(item: TangibleItem) = stateHolder.upsertTangibleItem(item)
    fun deleteTangibleItem(itemId: String) = stateHolder.deleteTangibleItem(itemId)
    fun selectTangibleItem(itemId: String) = stateHolder.selectTangibleItem(itemId)

    fun upsertWishlistGoal(goal: WishlistGoal) = stateHolder.upsertWishlistGoal(goal)
    fun deleteWishlistGoal(goalId: String) = stateHolder.deleteWishlistGoal(goalId)

    fun updateTheme(option: ThemeOption) = stateHolder.updateSettings { it.copy(themeOption = option) }
    fun updateThemeColorStyle(style: ThemeColorStyle) = stateHolder.updateSettings {
        it.copy(
            themeColorStyle = style,
            monetEnabled = style != ThemeColorStyle.DEFAULT,
            customKeyColorHex = if (style == ThemeColorStyle.CUSTOM) (it.customKeyColorHex ?: 0xFF3482FF) else it.customKeyColorHex
        )
    }
    fun updateCustomKeyColor(colorHex: Long?) = stateHolder.updateSettings {
        it.copy(
            customKeyColorHex = colorHex,
            themeColorStyle = if (colorHex != null) ThemeColorStyle.CUSTOM else it.themeColorStyle,
            monetEnabled = if (colorHex != null) true else it.monetEnabled
        )
    }
    fun updateOngoingNotification(enabled: Boolean) = stateHolder.updateSettings { it.copy(ongoingNotificationEnabled = enabled) }
    fun updateDefaultMinWage(wage: Long) = stateHolder.updateSettings { it.copy(defaultMinWage = wage.coerceAtLeast(1L)) }
    fun updateWidgetConfig(transform: (WidgetConfig) -> WidgetConfig) = stateHolder.updateSettings {
        it.copy(widgetConfig = transform(it.widgetConfig))
    }

    fun exportBackupJson(): String = stateHolder.exportBackupJson()
    fun importBackupJson(raw: String): Result<Unit> = stateHolder.importBackupJson(raw)
    fun resetAllData() = stateHolder.resetAllData()
}
