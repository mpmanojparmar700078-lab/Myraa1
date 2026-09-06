package com.example.platform.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import com.example.models.ActionResult
import com.example.models.InstalledAppInfo

class AppLauncher(private val context: Context) {

    companion object {
        const val PKG_YOUTUBE = "com.google.android.youtube"
        const val PKG_CHROME = "com.android.chrome"
        const val PKG_MAPS = "com.google.android.apps.maps"
        const val PKG_WHATSAPP = "com.whatsapp"
    }

    fun launchAppByPackage(packageName: String): ActionResult {
        return try {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                ActionResult(
                    success = true,
                    message = "लॉन्च किया जा रहा है ($packageName)",
                    launchedTarget = packageName
                )
            } else {
                ActionResult(
                    success = false,
                    message = "ऐप पैकेज '$packageName' नहीं मिला।",
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
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult(
                success = true,
                message = "कैमरा खोला जा रहा है (Camera opened)",
                launchedTarget = "camera"
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
                launchedTarget = "dialer"
            )
        } catch (e: Exception) {
            ActionResult(
                success = false,
                message = "फोन डायलर खोलने में समस्या आई: ${e.localizedMessage}",
                error = e.localizedMessage
            )
        }
    }

    fun launchSettings(): ActionResult {
        return try {
            val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult(
                success = true,
                message = "सेटिंग्स खोली जा रही हैं (Settings opened)",
                launchedTarget = "settings"
            )
        } catch (e: Exception) {
            ActionResult(
                success = false,
                message = "सेटिंग्स खोलने में त्रुटि: ${e.localizedMessage}",
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
                message = "लिंक खोला जा रहा है: $formattedUrl",
                launchedTarget = formattedUrl
            )
        } catch (e: ActivityNotFoundException) {
            ActionResult(
                success = false,
                message = "वेब ब्राउज़र नहीं मिला",
                error = e.localizedMessage
            )
        } catch (e: Exception) {
            ActionResult(
                success = false,
                message = "URL खोलने में त्रुटि: ${e.localizedMessage}",
                error = e.localizedMessage
            )
        }
    }

    fun performWebSearch(query: String): ActionResult {
        return try {
            val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra("query", query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (searchIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(searchIntent)
                ActionResult(
                    success = true,
                    message = "Google पर '$query' खोजा जा रहा है",
                    launchedTarget = "search://$query"
                )
            } else {
                val googleUrl = "https://www.google.com/search?q=${Uri.encode(query)}"
                openUrl(googleUrl)
            }
        } catch (e: Exception) {
            val googleUrl = "https://www.google.com/search?q=${Uri.encode(query)}"
            openUrl(googleUrl)
        }
    }

    fun searchYouTube(query: String): ActionResult {
        val cleanQuery = query.trim()
        val webFallbackUrl = "https://www.youtube.com/results?search_query=${Uri.encode(cleanQuery)}"
        return try {
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                `package` = PKG_YOUTUBE
                putExtra("query", cleanQuery)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                ActionResult(
                    success = true,
                    message = "YouTube पर '$cleanQuery' खोजा जा रहा है",
                    launchedTarget = "youtube://results?search_query=$cleanQuery"
                )
            } else {
                openUrl(webFallbackUrl)
            }
        } catch (e: Exception) {
            openUrl(webFallbackUrl)
        }
    }

    fun getInstalledApps(): List<InstalledAppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfoList = pm.queryIntentActivities(intent, 0)
        val apps = mutableListOf<InstalledAppInfo>()

        for (resolveInfo in resolveInfoList) {
            val appInfo = resolveInfo.activityInfo.applicationInfo
            val appName = pm.getApplicationLabel(appInfo).toString()
            val packageName = resolveInfo.activityInfo.packageName
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val icon = resolveInfo.loadIcon(pm)
            apps.add(InstalledAppInfo(appName = appName, packageName = packageName, isSystemApp = isSystem, icon = icon))
        }

        return apps.distinctBy { it.packageName }.sortedBy { it.appName.lowercase() }
    }
}
