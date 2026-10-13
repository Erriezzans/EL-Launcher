package com.minimo.launcher.utils

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import java.util.concurrent.ConcurrentHashMap

enum class AutoCategory(val title: String) {
    Games("Games"),
    Social("Social"),
    Communication("Communication"),
    Music("Music"),
    Video("Video"),
    Photos("Photos"),
    News("News"),
    Travel("Maps & Travel"),
    Productivity("Productivity"),
    Finance("Finance"),
    Shopping("Shopping"),
    Internet("Internet"),
    Tools("Tools"),
    Other("Other")
}

/**
 * Decides which automatic category an app belongs to.
 *
 * 1. Android's own app category (ApplicationInfo.category). This is the category the developer / Play
 *    Store declares for the app, so it is the closest thing to "checking Play Store" that works
 *    offline.
 * 2. If the app has no declared category, keywords in the package name are used.
 * 3. Otherwise the app goes to "Other".
 */
object AppCategorizer {
    private val cache = ConcurrentHashMap<String, AutoCategory>()

    fun categorize(pm: PackageManager, packageName: String): AutoCategory =
        cache.getOrPut(packageName) { compute(pm, packageName) }

    private fun compute(pm: PackageManager, packageName: String): AutoCategory {
        val info = try {
            pm.getApplicationInfo(packageName, 0)
        } catch (_: Exception) {
            null
        }
        val fromSystem = when (info?.category) {
            ApplicationInfo.CATEGORY_GAME -> AutoCategory.Games
            ApplicationInfo.CATEGORY_AUDIO -> AutoCategory.Music
            ApplicationInfo.CATEGORY_VIDEO -> AutoCategory.Video
            ApplicationInfo.CATEGORY_IMAGE -> AutoCategory.Photos
            ApplicationInfo.CATEGORY_SOCIAL -> AutoCategory.Social
            ApplicationInfo.CATEGORY_NEWS -> AutoCategory.News
            ApplicationInfo.CATEGORY_MAPS -> AutoCategory.Travel
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> AutoCategory.Productivity
            else -> null
        }
        return fromSystem ?: byKeyword(packageName.lowercase()) ?: AutoCategory.Other
    }

    private val keywords: List<Pair<AutoCategory, List<String>>> = listOf(
        AutoCategory.Games to listOf("game", "puzzle", "racing", "chess"),
        AutoCategory.Finance to listOf(
            "bank", "wallet", "finance", "paypal", "crypto", "binance", "coinbase", "invest",
            "trading", "money", "gcash", "paymaya", "revolut"
        ),
        AutoCategory.Communication to listOf(
            "whatsapp", "telegram", "thoughtcrime", "messag", "dialer", "contacts", "incallui",
            "mail", "skype", "viber", "discord", "zoom", "sms", "mms"
        ),
        AutoCategory.Social to listOf(
            "facebook", "instagram", "twitter", "snapchat", "tiktok", "reddit", "linkedin",
            "pinterest", "threads", "mastodon", "bluesky"
        ),
        AutoCategory.Music to listOf("music", "spotify", "audio", "podcast", "radio", "soundcloud", "deezer", "shazam"),
        AutoCategory.Video to listOf("youtube", "netflix", "video", "vlc", "hulu", "disney", "twitch", "primevideo"),
        AutoCategory.Photos to listOf("camera", "gallery", "photo", "snapseed", "image"),
        AutoCategory.News to listOf("news", "feedly", "flipboard"),
        AutoCategory.Travel to listOf("maps", "navigation", "uber", "grab", "lyft", "taxi", "booking", "airbnb", "flight", "waze", "transit"),
        AutoCategory.Shopping to listOf("amazon", "shop", "ebay", "aliexpress", "lazada", "temu", "marketplace"),
        AutoCategory.Internet to listOf("browser", "chrome", "firefox", "opera", "brave", "duckduckgo", "vivaldi"),
        AutoCategory.Productivity to listOf(
            "docs", "sheets", "slides", "office", "notes", "calendar", "drive", "dropbox", "todo",
            "task", "pdf", "onenote", "evernote", "notion"
        ),
        AutoCategory.Tools to listOf(
            "calculator", "clock", "settings", "filemanager", "files", "flashlight", "weather",
            "compass", "recorder", "clean", "vpn", "torch", "backup", "utility", "tools"
        )
    )

    private fun byKeyword(packageName: String): AutoCategory? {
        for ((category, words) in keywords) {
            if (words.any { packageName.contains(it) }) return category
        }
        return null
    }
}
