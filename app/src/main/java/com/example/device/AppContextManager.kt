package com.example.device

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.example.services.MyraAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppCategory {
    STANDARD_APP,
    GAME,
    SYSTEM,
    UNKNOWN
}

data class DeviceAppContext(
    val packageName: String?,
    val appLabel: String?,
    val category: AppCategory,
    val isGame: Boolean,
    val windowTitle: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Tracks the current foreground application context and categorizes whether the user
 * is interacting with a standard Android UI application, a game engine (Unity/Unreal/OpenGL),
 * or system UI.
 */
class AppContextManager(private val context: Context) {

    companion object {
        private val KNOWN_GAME_PACKAGES = setOf(
            "com.dts.freefireth",
            "com.dts.freefiremax",
            "com.tencent.ig",
            "com.pubg.imobile",
            "com.pubg.krmobile",
            "com.roblox.client",
            "com.miHoYo.GenshinImpact",
            "com.mojang.minecraftpe",
            "com.kiloo.subwaysurf",
            "com.king.candycrushsaga",
            "com.supercell.clashofclans",
            "com.supercell.clashroyale",
            "com.supercell.brawlstars",
            "com.activision.callofduty.shooter",
            "com.ea.gp.apexlegendsmobilefps",
            "com.innersloth.spacemafia"
        )

        private val SYSTEM_PACKAGES = setOf(
            "com.android.systemui",
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher3",
            "com.android.settings"
        )
    }

    private val packageManager: PackageManager = context.packageManager

    private val _currentContext = MutableStateFlow(
        DeviceAppContext(
            packageName = null,
            appLabel = null,
            category = AppCategory.UNKNOWN,
            isGame = false
        )
    )
    val currentContext: StateFlow<DeviceAppContext> = _currentContext.asStateFlow()

    /**
     * Resolves the current foreground application context using AccessibilityService state.
     */
    fun refreshCurrentContext(): DeviceAppContext {
        val currentPkg = MyraAccessibilityService.currentActivePackage.value
        val screenState = MyraAccessibilityService.currentScreenState.value

        if (currentPkg.isNullOrBlank()) {
            val unknown = DeviceAppContext(
                packageName = null,
                appLabel = null,
                category = AppCategory.UNKNOWN,
                isGame = false
            )
            _currentContext.value = unknown
            return unknown
        }

        val appLabel = try {
            val appInfo = packageManager.getApplicationInfo(currentPkg, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            currentPkg
        }

        val isSystem = SYSTEM_PACKAGES.contains(currentPkg) || currentPkg.startsWith("com.android.") && currentPkg != "com.android.chrome"
        val isGame = isGamePackage(currentPkg)

        val category = when {
            isGame -> AppCategory.GAME
            isSystem -> AppCategory.SYSTEM
            else -> AppCategory.STANDARD_APP
        }

        val ctx = DeviceAppContext(
            packageName = currentPkg,
            appLabel = appLabel,
            category = category,
            isGame = isGame,
            windowTitle = screenState?.windowTitle
        )
        _currentContext.value = ctx
        return ctx
    }

    /**
     * Checks if a package corresponds to a game based on known lists or Android ApplicationInfo flags.
     */
    fun isGamePackage(packageName: String): Boolean {
        if (KNOWN_GAME_PACKAGES.contains(packageName.lowercase())) return true

        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                appInfo.category == ApplicationInfo.CATEGORY_GAME
            } else {
                (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
            }
        } catch (e: Exception) {
            false
        }
    }
}
