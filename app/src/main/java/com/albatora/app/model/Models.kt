package com.albatora.app.model

import kotlinx.serialization.Serializable

const val DEFAULT_2026_MIN_WAGE = 10320L

@Serializable
enum class ThemeOption(val label: String) {
    SYSTEM("시스템 설정"),
    LIGHT("라이트 모드"),
    DARK("다크 모드")
}

@Serializable
enum class JobKind(val label: String) {
    REGULAR("정규/고정"),
    SHORT_TERM("단기/일용직")
}

@Serializable
enum class PayType(val label: String) {
    HOURLY("시급"),
    DAILY("일급(일당)"),
    PER_TASK("건당")
}

@Serializable
enum class TaxMode(val label: String, val shortLabel: String) {
    NONE("무공제 (0%)", "무공제"),
    FREELANCE_3_3("사업소득 3.3%", "3.3%"),
    INSURANCE_4("4대보험 (9.4%)", "4대보험"),
    DAILY_WORKER("일용근로소득 원천징수 (일 15만 비과세 후 2.97%)", "일용직 원천징수"),
    CUSTOM("직접 입력 (%)", "직접입력")
}

@Serializable
enum class BreakPayType(val label: String) {
    UNPAID("무급 휴게"),
    PAID("유급 휴게")
}

@Serializable
enum class FeeType(val label: String) {
    PERCENT("%"),
    FIXED("원")
}

@Serializable
enum class PaymentStatus(val label: String, val badgeEmoji: String) {
    UNPAID("입금 대기", "\uD83D\uDD34"),
    PAID("입금 완료", "\uD83D\uDFE2")
}

@Serializable
enum class ShiftStatus(val label: String) {
    NORMAL("정상 근무"),
    OVERTIME("연장 근무"),
    SUBSTITUTE("대타 근무"),
    EARLY_LEAVE("조퇴"),
    ABSENT("결근")
}

@Serializable
enum class SettlementPeriodMode(val label: String) {
    FIRST_TO_END("전월 1일 ~ 말일"),
    PREV_N_PLUS_1_TO_N("전월 N+1일 ~ 당월 N일"),
    CUSTOM_RANGE("사용자 직접 기준일 설정")
}

@Serializable
enum class PaydayHolidayRule(val label: String) {
    PREV_BUSINESS_DAY("전 평일 (앞당겨 지급)"),
    NEXT_BUSINESS_DAY("다음 평일 (미뤄서 지급)"),
    EXACT_DAY("당일 그대로")
}

@Serializable
data class WageRevision(
    val effectiveFromDate: String, // yyyy-MM-dd
    val hourlyOrBaseAmount: Long
)

@Serializable
data class ScheduleSlot(
    val dayOfWeek: Int, // 1 = Mon .. 7 = Sun (java.time.DayOfWeek.value)
    val startMinute: Int, // 0..1439
    val endMinute: Int,   // can be > startMinute or < startMinute if overnight, normalized in minutes (0..1439)
    val autoRecommendBreak: Boolean = true,
    val breakMinutes: Int = 0,
    val breakPayType: BreakPayType = BreakPayType.UNPAID,
    val fixedBreakStartMinute: Int? = null,
    val fixedBreakEndMinute: Int? = null
)

@Serializable
data class AllowanceSettings(
    val nightAllowanceEnabled: Boolean = false,    // 22:00~06:00 -> 1.5x
    val overtimeAllowanceEnabled: Boolean = false, // >8h/day or OVERTIME -> 1.5x
    val holidayAllowanceEnabled: Boolean = false,  // public holiday / weekend -> 1.5x
    val holidayIncludesWeekends: Boolean = true
)

@Serializable
data class WeeklyHolidayRule(
    val enabled: Boolean = true,                     // 주 15h 이상 자동 계산 (40h 캡)
    val excludeOnAbsenceOrEarlyLeave: Boolean = true, // 결근/조퇴 시 해당 주 주휴수당 제외
    val forceIncludeUnder15h: Boolean = false         // 주 15시간 미만 강제 포함
)

@Serializable
data class Job(
    val id: String,
    val name: String,
    val emoji: String = "",
    val kind: JobKind = JobKind.REGULAR,
    val colorHex: Long = 0xFF3482FF,
    val payType: PayType = PayType.HOURLY,
    val baseAmount: Long = DEFAULT_2026_MIN_WAGE,
    val wageHistory: List<WageRevision> = emptyList(),
    val scheduleStartDate: String = "", // yyyy-MM-dd
    val schedules: List<ScheduleSlot> = emptyList(),
    val taxMode: TaxMode = TaxMode.NONE,
    val customTaxRatePercent: Double = 0.0,
    val dailyWorkerIncludeLocalTax: Boolean = true, // true = 2.97%, false = 2.7%
    val allowance: AllowanceSettings = AllowanceSettings(),
    val weeklyHoliday: WeeklyHolidayRule = WeeklyHolidayRule(),
    val settlementMode: SettlementPeriodMode = SettlementPeriodMode.FIRST_TO_END,
    val settlementCutoffDay: Int = 25,
    val settlementCustomStartDay: Int = 1,
    val settlementCustomEndDay: Int = 31,
    val settlementPrevMonthCycle: Boolean = true, // true = 전월 구간 -> 이번달 월급날
    val paydayDay: Int = 10, // 1..31 (31 = 말일)
    val paydayHolidayRule: PaydayHolidayRule = PaydayHolidayRule.PREV_BUSINESS_DAY,
    val memo: String = "",
    val isArchived: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Serializable
data class WorkRecord(
    val id: String,
    val jobId: String? = null,
    val title: String,
    val emoji: String = "",
    val kind: JobKind = JobKind.REGULAR,
    val colorHex: Long = 0xFF3482FF,
    val date: String, // yyyy-MM-dd
    val startMinute: Int,
    val endMinute: Int, // if <= startMinute, treated as overnight (+24h)
    val status: ShiftStatus = ShiftStatus.NORMAL,
    val payType: PayType = PayType.HOURLY,
    val unitAmount: Long = DEFAULT_2026_MIN_WAGE,
    val breakMinutes: Int = 0,
    val breakPayType: BreakPayType = BreakPayType.UNPAID,
    val fixedBreakStartMinute: Int? = null,
    val fixedBreakEndMinute: Int? = null,
    val transportExpense: Long = 0L,
    val mealExpense: Long = 0L,
    val siteBonus: Long = 0L,
    val agencyFeeValue: Double = 0.0,
    val agencyFeeType: FeeType = FeeType.PERCENT,
    val taxMode: TaxMode = TaxMode.NONE,
    val customTaxRatePercent: Double = 0.0,
    val dailyWorkerIncludeLocalTax: Boolean = true,
    val allowance: AllowanceSettings = AllowanceSettings(),
    val forceHolidayPay: Boolean = false,
    val paymentStatus: PaymentStatus = PaymentStatus.PAID,
    val paidAtDate: String? = null,
    val isScheduledOverride: Boolean = false,
    val memo: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Serializable
data class ShortTermTemplate(
    val id: String,
    val name: String,
    val emoji: String = "",
    val colorHex: Long = 0xFF10B981,
    val startMinute: Int = 9 * 60,
    val endMinute: Int = 18 * 60,
    val payType: PayType = PayType.DAILY,
    val unitAmount: Long = 130000L,
    val breakMinutes: Int = 60,
    val breakPayType: BreakPayType = BreakPayType.UNPAID,
    val transportExpense: Long = 0L,
    val mealExpense: Long = 0L,
    val siteBonus: Long = 0L,
    val agencyFeeValue: Double = 0.0,
    val agencyFeeType: FeeType = FeeType.PERCENT,
    val taxMode: TaxMode = TaxMode.DAILY_WORKER,
    val customTaxRatePercent: Double = 0.0,
    val dailyWorkerIncludeLocalTax: Boolean = true,
    val allowance: AllowanceSettings = AllowanceSettings(),
    val memo: String = ""
)

@Serializable
data class TangibleItem(
    val id: String,
    val name: String,
    val emoji: String,
    val unitPrice: Long,
    val isDefault: Boolean = false
)

@Serializable
data class WishlistGoal(
    val id: String,
    val title: String,
    val emoji: String,
    val targetAmount: Long,
    val isCompleted: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Serializable
data class ActiveStopwatchSession(
    val id: String,
    val jobId: String? = null,
    val title: String,
    val emoji: String = "",
    val kind: JobKind = JobKind.SHORT_TERM,
    val colorHex: Long = 0xFF3482FF,
    val payType: PayType = PayType.HOURLY,
    val unitAmount: Long = DEFAULT_2026_MIN_WAGE,
    val startEpochMillis: Long,
    val plannedEndEpochMillis: Long? = null,
    val isPaused: Boolean = false,
    val pausedAtEpochMillis: Long? = null,
    val accumulatedManualPauseMillis: Long = 0L,
    val unpaidBreakMinutes: Int = 0,
    val breakPayType: BreakPayType = BreakPayType.UNPAID,
    val fixedBreakStartMinute: Int? = null,
    val fixedBreakEndMinute: Int? = null,
    val allowance: AllowanceSettings = AllowanceSettings(),
    val taxMode: TaxMode = TaxMode.NONE,
    val customTaxRatePercent: Double = 0.0,
    val dailyWorkerIncludeLocalTax: Boolean = true,
    val transportExpense: Long = 0L,
    val mealExpense: Long = 0L,
    val siteBonus: Long = 0L,
    val agencyFeeValue: Double = 0.0,
    val agencyFeeType: FeeType = FeeType.PERCENT
)

@Serializable
data class ScheduledShiftRuntime(
    val key: String, // "${jobId}_${startEpochMillis}"
    val jobId: String,
    val isManualPaused: Boolean = false,
    val manualPausedAtEpochMillis: Long? = null,
    val accumulatedManualPauseMillis: Long = 0L,
    val clockedOutAtEpochMillis: Long? = null
)

@Serializable
data class AppSettings(
    val themeOption: ThemeOption = ThemeOption.SYSTEM,
    val monetEnabled: Boolean = false,
    val customKeyColorHex: Long? = null,
    val ongoingNotificationEnabled: Boolean = true,
    val defaultMinWage: Long = DEFAULT_2026_MIN_WAGE,
    val selectedTangibleItemId: String = "tangible_americano"
)

@Serializable
data class AlbaToraDataState(
    val jobs: List<Job> = emptyList(),
    val records: List<WorkRecord> = emptyList(),
    val templates: List<ShortTermTemplate> = emptyList(),
    val tangibleItems: List<TangibleItem> = defaultTangibleItems(),
    val wishlistGoals: List<WishlistGoal> = emptyList(),
    val activeStopwatches: List<ActiveStopwatchSession> = emptyList(),
    val scheduledRuntimes: List<ScheduledShiftRuntime> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val schemaVersion: Int = 1
) {
    companion object {
        fun defaultTangibleItems(): List<TangibleItem> = listOf(
            TangibleItem(
                id = "tangible_americano",
                name = "아메리카노",
                emoji = "\u2615",
                unitPrice = 4500L,
                isDefault = true
            ),
            TangibleItem(
                id = "tangible_gukbap",
                name = "국밥",
                emoji = "\uD83C\uDF5C",
                unitPrice = 10000L,
                isDefault = true
            ),
            TangibleItem(
                id = "tangible_chicken",
                name = "치킨",
                emoji = "\uD83C\uDF57",
                unitPrice = 22000L,
                isDefault = true
            )
        )

        fun initialEmptyState(): AlbaToraDataState = AlbaToraDataState(
            jobs = emptyList(),
            records = emptyList(),
            templates = emptyList(),
            tangibleItems = defaultTangibleItems(),
            wishlistGoals = emptyList(),
            activeStopwatches = emptyList(),
            scheduledRuntimes = emptyList(),
            settings = AppSettings()
        )
    }
}
