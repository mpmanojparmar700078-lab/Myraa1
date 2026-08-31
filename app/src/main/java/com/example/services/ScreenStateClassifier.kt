package com.example.services

import com.example.models.ScreenState
import com.example.models.ScreenStateCategory
import java.util.Locale

/**
 * ScreenStateClassifier — Evaluates the current accessibility screen hierarchy
 * to determine the high-level semantic screen category (Search, Results, Video Player, Dialog, Home, etc.).
 */
object ScreenStateClassifier {

    fun classify(screenState: ScreenState): ScreenStateCategory {
        val packageName = (screenState.packageName ?: "").lowercase(Locale.ROOT)
        val elements = screenState.elements

        if (elements.isEmpty()) {
            return ScreenStateCategory.APP_OPENING
        }

        // 1. Loading indicators
        val hasLoadingIndicator = elements.any { el ->
            val desc = (el.contentDescription ?: "").lowercase(Locale.ROOT)
            val text = (el.text ?: "").lowercase(Locale.ROOT)
            val className = (el.className ?: "").lowercase(Locale.ROOT)
            className.contains("progressbar") || className.contains("progressindicator") ||
                    desc.contains("loading") || desc.contains("लोड हो रहा") ||
                    text.contains("loading") || text.contains("लोड हो रहा")
        }
        if (hasLoadingIndicator && elements.size < 5) {
            return ScreenStateCategory.LOADING
        }

        // 2. Dialogs & Permission Popups
        val isPermissionPackage = packageName.contains("permissioncontroller") || packageName.contains("packageinstaller")
        val hasDialogMarkers = isPermissionPackage || elements.any { el ->
            val id = (el.viewId ?: "").lowercase(Locale.ROOT)
            val text = (el.text ?: "").lowercase(Locale.ROOT)
            id.contains("alertdialog") || id.contains("dialog_root") ||
                    text.contains("allow") || text.contains("deny") ||
                    text.contains("permission") || text.contains("अनुमति") ||
                    (text.contains("cancel") && elements.size < 8)
        }
        if (hasDialogMarkers && elements.size <= 8) {
            return ScreenStateCategory.DIALOG
        }

        // 3. YouTube specific classification
        if (packageName.contains("youtube")) {
            return classifyYouTubeScreen(screenState)
        }

        // 4. Chrome / Browser specific classification
        if (packageName.contains("chrome") || packageName.contains("browser")) {
            return classifyBrowserScreen(screenState)
        }

        // 5. Generic Search Screen vs Search Results vs Home vs Detail
        val editableElements = elements.filter { it.isEditable }
        val clickableElements = elements.filter { it.isClickable }

        if (editableElements.isNotEmpty()) {
            val focusedOrSearch = editableElements.any { el ->
                val id = (el.viewId ?: "").lowercase(Locale.ROOT)
                val text = (el.text ?: "").lowercase(Locale.ROOT)
                val desc = (el.contentDescription ?: "").lowercase(Locale.ROOT)
                id.contains("search") || text.contains("search") || desc.contains("search") ||
                        text.contains("खोज") || desc.contains("खोज")
            }
            if (focusedOrSearch) {
                return ScreenStateCategory.SEARCH_SCREEN
            }
        }

        // Check if many scrollable/clickable list items are present (Results)
        if (clickableElements.size >= 4) {
            val hasSearchContext = elements.any { el ->
                val text = (el.text ?: "").lowercase(Locale.ROOT)
                text.contains("results") || text.contains("परिणाम") || text.contains("filter")
            }
            if (hasSearchContext || clickableElements.size >= 6) {
                return ScreenStateCategory.SEARCH_RESULTS
            }
        }

        // Default to HOME_SCREEN if broad navigation is visible, or DETAIL_SCREEN
        return if (clickableElements.size > 2) ScreenStateCategory.HOME_SCREEN else ScreenStateCategory.DETAIL_SCREEN
    }

    private fun classifyYouTubeScreen(screenState: ScreenState): ScreenStateCategory {
        val elements = screenState.elements

        // A. Video Playing / Video Screen
        val hasVideoControls = elements.any { el ->
            val desc = (el.contentDescription ?: "").lowercase(Locale.ROOT)
            val id = (el.viewId ?: "").lowercase(Locale.ROOT)
            val text = (el.text ?: "").lowercase(Locale.ROOT)
            desc.contains("pause video") || desc.contains("play video") ||
                    desc.contains("pause") || desc.contains("play") ||
                    desc.contains("रोकें") || desc.contains("चलाएं") ||
                    id.contains("player_control") || id.contains("player_view") ||
                    id.contains("watch_panel") || id.contains("fullscreen_button") ||
                    text.contains("subscribe") || text.contains("सदस्यता लें")
        }

        val hasCommentsOrLikes = elements.any { el ->
            val desc = (el.contentDescription ?: "").lowercase(Locale.ROOT)
            val text = (el.text ?: "").lowercase(Locale.ROOT)
            desc.contains("like this video") || desc.contains("dislike") ||
                    text.contains("views") || text.contains("व्यू") ||
                    text.contains("comments") || text.contains("टिप्पणियां")
        }

        if (hasVideoControls && (hasCommentsOrLikes || elements.size > 3)) {
            return ScreenStateCategory.VIDEO_SCREEN
        }

        // B. Search Edit Screen (Search bar is focused or active keyboard)
        val hasActiveSearchBox = elements.any { el ->
            el.isEditable && (
                    (el.viewId ?: "").lowercase(Locale.ROOT).contains("search_edit_text") ||
                            (el.viewId ?: "").lowercase(Locale.ROOT).contains("search_src_text") ||
                            (el.text ?: "").lowercase(Locale.ROOT).contains("search youtube") ||
                            (el.contentDescription ?: "").lowercase(Locale.ROOT).contains("search query")
                    )
        }
        if (hasActiveSearchBox) {
            return ScreenStateCategory.SEARCH_SCREEN
        }

        // C. Search Results Screen
        val hasResults = elements.any { el ->
            val desc = (el.contentDescription ?: "").lowercase(Locale.ROOT)
            val text = (el.text ?: "").lowercase(Locale.ROOT)
            desc.contains("ago") || desc.contains("views") || desc.contains("मिनट") ||
                    desc.contains("घंटे पहले") || desc.contains("चैनल") || desc.contains("channel") ||
                    text.contains("filters") || text.contains("फ़िल्टर")
        }
        if (hasResults) {
            return ScreenStateCategory.SEARCH_RESULTS
        }

        // D. Home / Explore Feed
        val hasHomeTabs = elements.any { el ->
            val text = (el.text ?: "").lowercase(Locale.ROOT)
            val desc = (el.contentDescription ?: "").lowercase(Locale.ROOT)
            text.contains("home") || desc.contains("home") ||
                    text.contains("subscriptions") || desc.contains("subscriptions") ||
                    text.contains("shorts") || desc.contains("shorts")
        }
        if (hasHomeTabs) {
            return ScreenStateCategory.HOME_SCREEN
        }

        return ScreenStateCategory.HOME_SCREEN
    }

    private fun classifyBrowserScreen(screenState: ScreenState): ScreenStateCategory {
        val elements = screenState.elements

        val hasUrlBar = elements.any { el ->
            val id = (el.viewId ?: "").lowercase(Locale.ROOT)
            val text = (el.text ?: "").lowercase(Locale.ROOT)
            id.contains("url_bar") || id.contains("search_box") ||
                    text.contains("search or type web address") || text.contains("search or type url")
        }

        val hasSearchResults = elements.any { el ->
            val text = (el.text ?: "").lowercase(Locale.ROOT)
            text.contains("google.com/search") || (text.contains("all") && text.contains("images") && text.contains("news"))
        }

        return when {
            hasSearchResults -> ScreenStateCategory.SEARCH_RESULTS
            hasUrlBar && elements.size < 10 -> ScreenStateCategory.SEARCH_SCREEN
            elements.size > 5 -> ScreenStateCategory.DETAIL_SCREEN
            else -> ScreenStateCategory.HOME_SCREEN
        }
    }
}
