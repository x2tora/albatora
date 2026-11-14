package com.albatora.app.ui.theme

import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Alarm
import top.yukonga.miuix.kmp.icon.extended.AppRecording
import top.yukonga.miuix.kmp.icon.extended.BankCards
import top.yukonga.miuix.kmp.icon.extended.CloudFill
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Create
import top.yukonga.miuix.kmp.icon.extended.Email
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.Location
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Mic
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Phone
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.icon.extended.Promotions
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.icon.extended.ScreenMirroring
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.icon.extended.Tasks
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.icon.extended.Timer
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.icon.extended.Update
import top.yukonga.miuix.kmp.icon.extended.Weeks
import top.yukonga.miuix.kmp.icon.extended.WorldClock

data class AlbaIconSpec(
    val id: String,
    val label: String,
    val icon: ImageVector
)

data class AlbaIconCategory(
    val label: String,
    val items: List<AlbaIconSpec>
)

/**
 * Central Miuix icon registry replacing system Unicode emojis across the app while maintaining
 * backwards-compatible resolution for saved legacy emoji values in user data and JSON backups.
 */
object AlbaIconCatalog {

    val categories: List<AlbaIconCategory> = listOf(
        AlbaIconCategory(
            label = "매장",
            items = listOf(
                AlbaIconSpec("store", "매장·카페", MiuixIcons.Store),
                AlbaIconSpec("bank_cards", "결제·카운터", MiuixIcons.BankCards),
                AlbaIconSpec("promotions", "할인·이벤트", MiuixIcons.Promotions),
                AlbaIconSpec("favorites", "식음료·인기", MiuixIcons.Favorites),
                AlbaIconSpec("favorites_fill", "스페셜·VIP", MiuixIcons.FavoritesFill),
                AlbaIconSpec("community", "접객·팀", MiuixIcons.Community),
                AlbaIconSpec("home", "생활·홈케어", MiuixIcons.Home),
                AlbaIconSpec("music", "음악·라운지", MiuixIcons.Music)
            )
        ),
        AlbaIconCategory(
            label = "현장",
            items = listOf(
                AlbaIconSpec("stopwatch", "실시간·교대", MiuixIcons.Stopwatch),
                AlbaIconSpec("timer", "단기·타임", MiuixIcons.Timer),
                AlbaIconSpec("alarm", "오픈·마감", MiuixIcons.Alarm),
                AlbaIconSpec("world_clock", "야간·풀타임", MiuixIcons.WorldClock),
                AlbaIconSpec("location", "배달·현장", MiuixIcons.Location),
                AlbaIconSpec("scan", "물류·검수", MiuixIcons.Scan),
                AlbaIconSpec("layers", "창고·재고", MiuixIcons.Layers),
                AlbaIconSpec("app_recording", "촬영·미디어", MiuixIcons.AppRecording)
            )
        ),
        AlbaIconCategory(
            label = "사무",
            items = listOf(
                AlbaIconSpec("notes", "문서·사무", MiuixIcons.Notes),
                AlbaIconSpec("tasks", "체크·관리", MiuixIcons.Tasks),
                AlbaIconSpec("create", "작성·편집", MiuixIcons.Create),
                AlbaIconSpec("phone", "상담·콜센터", MiuixIcons.Phone),
                AlbaIconSpec("email", "메일·행정", MiuixIcons.Email),
                AlbaIconSpec("contacts", "과외·튜터", MiuixIcons.Contacts),
                AlbaIconSpec("screen_mirroring", "IT·원격", MiuixIcons.ScreenMirroring),
                AlbaIconSpec("tune", "세팅·정비", MiuixIcons.Tune)
            )
        ),
        AlbaIconCategory(
            label = "목표",
            items = listOf(
                AlbaIconSpec("months", "월급·정산", MiuixIcons.Months),
                AlbaIconSpec("weeks", "주휴·주간", MiuixIcons.Weeks),
                AlbaIconSpec("photos", "여행·취미", MiuixIcons.Photos),
                AlbaIconSpec("theme", "패션·디자인", MiuixIcons.Theme),
                AlbaIconSpec("cloud", "클라우드·기기", MiuixIcons.CloudFill),
                AlbaIconSpec("mic", "방송·공연", MiuixIcons.Mic),
                AlbaIconSpec("lock", "저축·비상금", MiuixIcons.Lock),
                AlbaIconSpec("update", "목표·달성", MiuixIcons.Update)
            )
        )
    )

    val allSpecs: List<AlbaIconSpec> = categories.flatMap { it.items }

    private val byId: Map<String, AlbaIconSpec> = allSpecs.associateBy { it.id }

    private val legacyEmojiMap: Map<String, String> = mapOf(
        "☕" to "store",
        "🍵" to "store",
        "🧋" to "store",
        "🥐" to "store",
        "🍰" to "favorites",
        "🍔" to "favorites",
        "🍟" to "favorites",
        "🍕" to "favorites",
        "🍗" to "favorites_fill",
        "🍜" to "promotions",
        "🍲" to "promotions",
        "🍱" to "promotions",
        "🏪" to "store",
        "🛒" to "store",
        "🛍️" to "promotions",
        "💳" to "bank_cards",
        "🧾" to "bank_cards",
        "📦" to "scan",
        "🚚" to "location",
        "🛵" to "location",
        "🚲" to "location",
        "🏗️" to "layers",
        "🔧" to "tune",
        "🔨" to "tune",
        "🦺" to "layers",
        "🧹" to "home",
        "💻" to "screen_mirroring",
        "⌨️" to "screen_mirroring",
        "📊" to "tasks",
        "📝" to "notes",
        "📚" to "contacts",
        "🎓" to "contacts",
        "✏️" to "create",
        "🎨" to "theme",
        "🎧" to "music",
        "📞" to "phone",
        "🏢" to "community",
        "💼" to "tasks",
        "🎯" to "update",
        "✈️" to "photos",
        "🏝️" to "photos",
        "📱" to "cloud",
        "⌚" to "stopwatch",
        "🎮" to "music",
        "🏠" to "home",
        "🚘" to "location",
        "💎" to "favorites_fill",
        "💰" to "bank_cards",
        "🎁" to "promotions",
        "🌟" to "favorites_fill",
        "🔥" to "timer",
        "💪" to "update"
    )

    fun normalizeId(keyOrEmoji: String): String {
        val cleaned = keyOrEmoji.trim().removePrefix("icon:")
        if (cleaned.isEmpty()) return ""
        if (byId.containsKey(cleaned)) return cleaned
        return legacyEmojiMap[cleaned] ?: ""
    }

    fun resolveIcon(keyOrEmoji: String, fallback: ImageVector = MiuixIcons.Store): ImageVector {
        val id = normalizeId(keyOrEmoji)
        return byId[id]?.icon ?: fallback
    }

    fun resolveLabel(keyOrEmoji: String): String {
        val id = normalizeId(keyOrEmoji)
        return byId[id]?.label ?: ""
    }
}
