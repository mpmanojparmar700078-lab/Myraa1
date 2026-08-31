package com.example.platform.android

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
import com.example.models.ActionResult

data class InstalledAppInfo(
    val appName: String,
    val packageName: String,
    val isSystemApp: Boolean = false
)

class AppLauncher(private val context: Context) {

    companion object {
        const val PKG_YOUTUBE = "com.google.android.youtube"
        const val PKG_CHROME = "com.android.chrome"
        const val PKG_MAPS = "com.google.android.apps.maps"
        const val PKG_PLAY_STORE = "com.android.vending"
        const val PKG_WHATSAPP = "com.whatsapp"
        const val PKG_GMAIL = "com.google.android.gm"
        const val PKG_INSTAGRAM = "com.instagram.android"
        const val PKG_TELEGRAM = "org.telegram.messenger"
        const val PKG_SPOTIFY = "com.spotify.music"

        // Common Hindi / Hinglish / English aliases for popular apps
        val APP_ALIASES = mapOf(
            "whatsapp" to listOf("whatsapp", "व्हाट्सएप", "वाट्सएप", "whatsap", "watsapp", "wa"),
            "instagram" to listOf("instagram", "इंस्टाग्राम", "insta", "ig"),
            "youtube" to listOf("youtube", "यूट्यूब", "युट्यूब", "yt"),
            "chrome" to listOf("chrome", "क्रोम", "google chrome", "गूगल क्रोम"),
            "calculator" to listOf("calculator", "कैलकुलेटर", "calc", "हिसाब"),
            "free fire" to listOf("free fire", "freefire", "फ्री फायर", "free fire max", "freefiremax"),
            "telegram" to listOf("telegram", "टेलीग्राम", "tg"),
            "spotify" to listOf("spotify", "स्पॉटिफाई"),
            "play store" to listOf("play store", "playstore", "google play", "प्ले स्टोर"),
            "maps" to listOf("maps", "google maps", "मैप्स", "गूगल मैप्स", "नक्शा"),
            "gmail" to listOf("gmail", "email", "ईमेल", "मेल"),
            "photos" to listOf("photos", "gallery", "गैलरी", "फ़ोटो", "फोटो"),
            "clock" to listOf("clock", "alarm", "अलार्म", "घड़ी"),
            "messages" to listOf("messages", "message", "मैसेज", "sms")
        )
    }

    fun launchAppByName(appName: String): ActionResult {
        val cleanName = appName.trim().replace(Regex("""^(?:the\s+|app\s+)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""(?i)\s+(?:app|ऐप|वाला\s*ऐप|वाला\s*app)$"""), "").trim()
        val normalized = cleanName.lowercase()

        // 1. Direct System Actions
        if (normalized == "camera" || normalized == "कैमरा") {
            return launchCamera()
        }
        if (normalized.startsWith("setting") || normalized.startsWith("सेटिंग")) {
            return launchSettings()
        }
        if (normalized == "phone" || normalized == "dialer" || normalized == "डायलर" || normalized == "फोन") {
            return launchDialer()
        }
        if (normalized == "google" || normalized == "गूगल") {
            return openUrl("https://www.google.com")
        }

        // 2. Discover launchable installed applications
        val installedApps = getInstalledApps()
        val pm = context.packageManager

        // Case A: Exact match on App Name or Package Name
        val exactMatch = installedApps.firstOrNull {
            it.appName.equals(cleanName, ignoreCase = true) ||
            it.packageName.equals(cleanName, ignoreCase = true)
        }
        if (exactMatch != null) {
            return launchInstalledApp(exactMatch)
        }

        // Case B: Canonical Alias Matching (e.g. "व्हाट्सएप" -> "WhatsApp", "कैलकुलेटर" -> "Calculator")
        for ((canonicalKey, aliases) in APP_ALIASES) {
            if (aliases.any { it.equals(normalized, ignoreCase = true) }) {
                // Check if any installed app matches this canonical alias
                val aliasMatch = installedApps.firstOrNull { app ->
                    app.appName.equals(canonicalKey, ignoreCase = true) ||
                    aliases.any { alias -> app.appName.equals(alias, ignoreCase = true) || app.packageName.contains(canonicalKey.replace(" ", "")) }
                }
                if (aliasMatch != null) {
                    return launchInstalledApp(aliasMatch)
                }
            }
        }

        // Case C: Space-insensitive / Normalized match (e.g. "freefire" -> "Free Fire", "playstore" -> "Play Store")
        val strippedQuery = normalized.replace(" ", "")
        val normMatches = installedApps.filter {
            it.appName.lowercase().replace(" ", "") == strippedQuery
        }
        if (normMatches.size == 1) {
            return launchInstalledApp(normMatches.first())
        }

        // Case D: Substring / Fuzzy match
        val candidates = installedApps.filter {
            it.appName.lowercase().contains(normalized) ||
            normalized.contains(it.appName.lowercase())
        }

        if (candidates.size == 1) {
            return launchInstalledApp(candidates.first())
        } else if (candidates.size > 1) {
            // Check if one candidate is an exact match
            val singleExact = candidates.firstOrNull { it.appName.equals(cleanName, ignoreCase = true) }
            if (singleExact != null) {
                return launchInstalledApp(singleExact)
            }
            // Ambiguous match: ask for clarification without blindly opening one
            val appNamesList = candidates.take(4).joinToString(", ") { it.appName }
            return ActionResult(
                success = false,
                message = "कौन सा app खोलना है? ($appNamesList)",
                error = "Ambiguous app name: $cleanName"
            )
        }

        // Case E: Check if it's a direct package that can be launched directly
        val directIntent = pm.getLaunchIntentForPackage(cleanName)
        if (directIntent != null) {
            directIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(directIntent)
            return ActionResult(
                success = true,
                message = "$cleanName खोला जा रहा है (Launching $cleanName)",
                launchedTarget = cleanName
            )
        }

        // Case F: App is NOT installed -> Return concise local response without crashing or launching random apps
        return ActionResult(
            success = false,
            message = "$cleanName आपके फोन में installed नहीं है।",
            error = "App not installed: $cleanName"
        )
    }

    private fun launchInstalledApp(appInfo: InstalledAppInfo): ActionResult {
        return try {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(appInfo.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                ActionResult(
                    success = true,
                    message = "${appInfo.appName} खोला जा रहा है (Launching ${appInfo.appName})",
                    launchedTarget = appInfo.packageName
                )
            } else {
                val fallbackIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    `package` = appInfo.packageName
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                ActionResult(
                    success = true,
                    message = "${appInfo.appName} खोला जा रहा है (Launching ${appInfo.appName})",
                    launchedTarget = appInfo.packageName
                )
            }
        } catch (e: Exception) {
            ActionResult(
                success = false,
                message = "${appInfo.appName} खोलने में त्रुटि: ${e.localizedMessage}",
                error = e.localizedMessage
            )
        }
    }

    fun getInstalledApps(): List<InstalledAppInfo> {
        return try {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(intent, 0)
            val appList = mutableListOf<InstalledAppInfo>()
            val seenPackages = mutableSetOf<String>()

            for (resolveInfo in resolveInfos) {
                val pkg = resolveInfo.activityInfo.packageName
                if (pkg.isNullOrBlank() || seenPackages.contains(pkg)) continue
                seenPackages.add(pkg)
                val name = resolveInfo.loadLabel(pm)?.toString()?.trim() ?: pkg
                val isSystem = (resolveInfo.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                appList.add(InstalledAppInfo(appName = name, packageName = pkg, isSystemApp = isSystem))
            }
            appList.sortedBy { it.appName.lowercase() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun launchPackage(packageName: String): ActionResult {
        return try {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                ActionResult(
                    success = true,
                    message = "$packageName खोला जा रहा है (Launching $packageName)",
                    launchedTarget = packageName
                )
            } else {
                ActionResult(
                    success = false,
                    message = "ऐप पैकेज '$packageName' लॉन्च नहीं हो सका।",
                    error = "Launch intent not found for $packageName"
                )
            }
        } catch (e: Exception) {
            ActionResult(
                success = false,
                message = "त्रुटि: ${e.localizedMessage}",
                error = e.localizedMessage
            )
        }
    }

    fun launchCamera(): ActionResult {
        return try {
            val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult(
                success = true,
                message = "कैमरा खोला जा रहा है (Camera opened)",
                launchedTarget = "action_camera"
            )
        } catch (e: Exception) {
            ActionResult(
                success = false,
                message = "कैमरा खोलने में समस्या आई: ${e.localizedMessage}",
                error = e.localizedMessage
            )
        }
    }

    fun launchDialer(): ActionResult {
        return try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult(
                success = true,
                message = "फोन डायलर खोला जा रहा है (Dialer opened)",
                launchedTarget = "action_dialer"
            )
        } catch (e: Exception) {
            ActionResult(
                success = false,
                message = "फोन डायलर खोलने में समस्या आई: ${e.localizedMessage}",
                error = e.localizedMessage
            )
        }
    }

    fun launchPackageWithFallback(packageName: String, fallbackUrl: String, appName: String): ActionResult {
        return try {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                ActionResult(
                    success = true,
                    message = "$appName खोला जा रहा है ($appName launched)",
                    launchedTarget = packageName
                )
            } else {
                // Fallback to browser URL
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
                ActionResult(
                    success = true,
                    message = "$appName ऐप इंस्टॉल नहीं मिला। वेब ब्राउज़र में खोला गया ($appName web opened)",
                    launchedTarget = fallbackUrl
                )
            }
        } catch (e: Exception) {
            ActionResult(
                success = false,
                message = "Error launching $appName: ${e.localizedMessage}",
                error = e.localizedMessage
            )
        }
    }

    fun launchSettings(): ActionResult {
        return try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult(
                success = true,
                message = "सेटिंग्स खोली जा रही हैं (Settings opened)",
                launchedTarget = "android.settings"
            )
        } catch (e: Exception) {
            ActionResult(
                success = false,
                message = "सेटिंग्स खोलने में त्रुटि (Failed to open settings): ${e.localizedMessage}",
                error = e.localizedMessage
            )
        }
    }

    fun openUrl(rawUrl: String): ActionResult {
        return try {
            val formattedUrl = if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
                "https://$rawUrl"
            } else {
                rawUrl
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(formattedUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult(
                success = true,
                message = "लिंक खोला जा रहा है (Opening URL)",
                launchedTarget = formattedUrl
            )
        } catch (e: ActivityNotFoundException) {
            ActionResult(
                success = false,
                message = "वेब ब्राउज़र नहीं मिला (No web browser found on device)",
                error = e.localizedMessage
            )
        } catch (e: Exception) {
            ActionResult(
                success = false,
                message = "URL खोलने में त्रुटि (Failed to open URL): ${e.localizedMessage}",
                error = e.localizedMessage
            )
        }
    }

    fun performWebSearch(query: String): ActionResult {
        return try {
            // First try web search intent
            val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (searchIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(searchIntent)
                ActionResult(
                    success = true,
                    message = "Google पर '$query' खोजा जा रहा है (Searching Google for '$query')",
                    launchedTarget = "search://$query"
                )
            } else {
                // Fallback to direct Google search URL
                val googleUrl = "https://www.google.com/search?q=" + Uri.encode(query)
                openUrl(googleUrl)
            }
        } catch (e: Exception) {
            // Fallback to browser url
            val googleUrl = "https://www.google.com/search?q=" + Uri.encode(query)
            openUrl(googleUrl)
        }
    }

    fun searchYouTube(query: String): ActionResult {
        val cleanQuery = query.trim()
        val webFallbackUrl = "https://www.youtube.com/results?search_query=" + Uri.encode(cleanQuery)
        return try {
            // Try YouTube App Search Intent
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage(PKG_YOUTUBE)
                putExtra("query", cleanQuery)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pm = context.packageManager
            if (intent.resolveActivity(pm) != null) {
                context.startActivity(intent)
                ActionResult(
                    success = true,
                    message = "YouTube पर '$cleanQuery' खोजा जा रहा है (Searching YouTube for '$cleanQuery')",
                    launchedTarget = "youtube://results?search_query=$cleanQuery"
                )
            } else {
                // Fallback to browser YouTube URL
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webFallbackUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
                ActionResult(
                    success = true,
                    message = "YouTube पर '$cleanQuery' खोजा जा रहा है (Searching YouTube for '$cleanQuery')",
                    launchedTarget = webFallbackUrl
                )
            }
        } catch (e: Exception) {
            openUrl(webFallbackUrl)
            ActionResult(
                success = true,
                message = "YouTube पर '$cleanQuery' खोला जा रहा है (Opening YouTube search for '$cleanQuery')",
                launchedTarget = webFallbackUrl
            )
        }
    }
}
