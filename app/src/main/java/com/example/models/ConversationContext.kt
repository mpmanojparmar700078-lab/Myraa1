package com.example.models

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Short-term Conversation Context for Myra V3.3.
 * Supports:
 * - Entity Context (e.g. activeTopicEntity = "Free Fire Craftland")
 * - Pronoun & contextual reference resolution ("iski", "iska", "it", "this")
 * - Follow-up Task Continuation ("अब इसे play करो", "play भी करो")
 * - Context Query answering ("maine kiski website open karne bola tha?")
 * - Ambiguity detection (e.g. ["Free Fire", "Minecraft"])
 *
 * Implements strict context safety and configurable 10-minute timeout.
 */
data class ConversationContext(
    val platform: String? = null,              // e.g. "YOUTUBE", "GOOGLE"
    val query: String? = null,                 // e.g. "Free Fire", "Free Fire Craftland official website"
    val lastAction: String? = null,            // e.g. "SEARCH", "PLAY", "OPEN_APP", "OPEN_WEBSITE"
    val lastIntent: IntentType? = null,        // e.g. WEB_SEARCH, YOUTUBE_SEARCH, OPEN_URL
    val activeTaskApp: String? = null,         // e.g. "YouTube", "Chrome"
    val activeTopicEntity: String? = null,     // e.g. "Free Fire Craftland"
    val lastEntity: String? = null,            // e.g. "Free Fire Craftland"
    val candidateEntities: List<String> = emptyList(), // For ambiguity check e.g. ["Free Fire", "Minecraft"]
    val lastWebsiteTarget: String? = null,     // e.g. "Free Fire Craftland official website"
    val lastWebsiteEntity: String? = null,     // e.g. "Free Fire Craftland"
    val timestamp: Long = System.currentTimeMillis()
) {
    val lastPlatform: String? get() = platform
    val lastQuery: String? get() = query
    /**
     * Checks if the short-term context has expired (default: 10 minutes).
     */
    fun isExpired(timeoutMs: Long = ConversationContextTracker.CONTEXT_TIMEOUT_MS): Boolean {
        return System.currentTimeMillis() - timestamp > timeoutMs
    }

    /**
     * Age of this context in seconds.
     */
    val ageSeconds: Long
        get() = ((System.currentTimeMillis() - timestamp) / 1000L).coerceAtLeast(0L)

    /**
     * Context safety check for YouTube continuation.
     */
    val isValidForYouTubeContinuation: Boolean
        get() {
            if (isExpired()) return false
            if (query.isNullOrBlank()) return false

            // Context safety: If user switched to another app (e.g. Chrome), do not reuse YouTube query
            if (activeTaskApp != null &&
                !activeTaskApp.equals("YouTube", ignoreCase = true) &&
                !activeTaskApp.equals("com.google.android.youtube", ignoreCase = true)
            ) {
                return false
            }
            return platform == null || platform.equals("YOUTUBE", ignoreCase = true)
        }

    /**
     * Best single resolved entity from context.
     */
    val effectiveEntity: String?
        get() {
            if (isExpired()) return null
            return activeTopicEntity?.takeIf { it.isNotBlank() }
                ?: lastEntity?.takeIf { it.isNotBlank() }
                ?: lastWebsiteEntity?.takeIf { it.isNotBlank() }
                ?: query?.takeIf { it.isNotBlank() }
        }

    /**
     * Checks if entity is ambiguous (multiple candidates exist).
     */
    val isAmbiguousEntity: Boolean
        get() {
            if (isExpired()) return false
            return candidateEntities.size > 1
        }
}

/**
 * Singleton tracker managing short-term conversation context for deterministic follow-ups.
 */
object ConversationContextTracker {
    // 10 minutes default configurable context timeout
    var CONTEXT_TIMEOUT_MS: Long = 10 * 60 * 1000L

    private val _currentContext = MutableStateFlow<ConversationContext?>(null)
    val currentContext: StateFlow<ConversationContext?> = _currentContext.asStateFlow()

    /**
     * Updates active conversation context.
     */
    fun updateContext(
        platform: String? = null,
        query: String? = null,
        lastAction: String? = null,
        lastIntent: IntentType? = null,
        activeTaskApp: String? = null,
        activeTopicEntity: String? = null,
        lastEntity: String? = null,
        candidateEntities: List<String>? = null,
        lastWebsiteTarget: String? = null,
        lastWebsiteEntity: String? = null
    ) {
        val prev = _currentContext.value
        val effectivePlatform = platform ?: if (activeTaskApp.equals("YouTube", ignoreCase = true)) "YOUTUBE" else prev?.platform
        val effectiveQuery = query ?: prev?.query
        val effectiveTopicEntity = activeTopicEntity ?: prev?.activeTopicEntity
        val effectiveLastEntity = lastEntity ?: activeTopicEntity ?: prev?.lastEntity
        val effectiveCandidates = candidateEntities ?: prev?.candidateEntities ?: emptyList()
        val effectiveWebsiteTarget = lastWebsiteTarget ?: prev?.lastWebsiteTarget
        val effectiveWebsiteEntity = lastWebsiteEntity ?: prev?.lastWebsiteEntity ?: activeTopicEntity ?: prev?.activeTopicEntity

        _currentContext.value = ConversationContext(
            platform = effectivePlatform,
            query = effectiveQuery,
            lastAction = lastAction,
            lastIntent = lastIntent ?: prev?.lastIntent,
            activeTaskApp = activeTaskApp,
            activeTopicEntity = effectiveTopicEntity,
            lastEntity = effectiveLastEntity,
            candidateEntities = effectiveCandidates,
            lastWebsiteTarget = effectiveWebsiteTarget,
            lastWebsiteEntity = effectiveWebsiteEntity,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Records entities discovered from user chat or queries.
     */
    fun recordDiscoveredTopic(rawInput: String) {
        val extracted = extractEntitiesFromInput(rawInput)
        if (extracted.isNotEmpty()) {
            if (extracted.size == 1) {
                val entity = extracted.first()
                updateContext(
                    activeTopicEntity = entity,
                    lastEntity = entity,
                    candidateEntities = listOf(entity)
                )
            } else {
                updateContext(
                    activeTopicEntity = null, // Mark as ambiguous
                    lastEntity = extracted.first(),
                    candidateEntities = extracted
                )
            }
        }
    }

    /**
     * Extracts potential topic entities from natural user speech/input.
     * Examples:
     * - "Free Fire Craftland ke bare mein batao" -> ["Free Fire Craftland"]
     * - "Free Fire aur Minecraft ke bare mein batao" -> ["Free Fire", "Minecraft"]
     * - "Tell me about Minecraft" -> ["Minecraft"]
     * - "Free Fire kya hai?" -> ["Free Fire"]
     */
    fun extractEntitiesFromInput(input: String): List<String> {
        val text = input.trim().replace(Regex("""[?!.,;।|]+$"""), "").trim()
        val lower = text.lowercase(Locale.ROOT)

        // Check for multiple entities joined by "aur", "and", "या", "or"
        // e.g. "Free Fire aur Minecraft ke bare mein batao" / "Free Fire and Minecraft ke bare me"
        val multiTopicRegex = Regex("""^(.+?)\s+(?:aur|and|या|or|\&)\s+(.+?)\s+(?:ke\s+bare\s+mein|ke\s+bare\s+me|के\s+बारे\s+में|kya\s+hai|about)(?:.*)$""", RegexOption.IGNORE_CASE)
        val mMulti = multiTopicRegex.find(text)
        if (mMulti != null) {
            val e1 = cleanEntityName(mMulti.groupValues[1])
            val e2 = cleanEntityName(mMulti.groupValues[2])
            if (e1.isNotBlank() && e2.isNotBlank()) {
                return listOf(e1, e2)
            }
        }

        // Pattern 1: "[Entity] ke bare mein / ke bare me batao / jankari do"
        val aboutRegexHi = Regex("""^(.+?)\s+(?:ke\s+bare\s+mein|ke\s+bare\s+me|के\s+बारे\s+में|ki\s+jankari|की\s+जानकारी)(?:\s+(?:batao|bataiye|do|दीजिए|kuch\s+batao|बताओ|बताइए))?$""", RegexOption.IGNORE_CASE)
        val mHi = aboutRegexHi.find(text)
        if (mHi != null) {
            val entity = cleanEntityName(mHi.groupValues[1])
            if (entity.isNotBlank() && !isGenericStopWord(entity)) {
                return listOf(entity)
            }
        }

        // Pattern 2: "[Entity] kya hai / kya hota hai"
        val whatIsRegexHi = Regex("""^(.+?)\s+(?:kya\s+hai|kya\s+hota\s+hai|क्या\s+है|क्या\s+होता\s+है)$""", RegexOption.IGNORE_CASE)
        val mWhat = whatIsRegexHi.find(text)
        if (mWhat != null) {
            val entity = cleanEntityName(mWhat.groupValues[1])
            if (entity.isNotBlank() && !isGenericStopWord(entity)) {
                return listOf(entity)
            }
        }

        // Pattern 3: "Tell me about [Entity]" / "What is [Entity]"
        val aboutRegexEn = Regex("""^(?:tell\s+me\s+about|what\s+is|info\s+about|information\s+about)\s+(.+)$""", RegexOption.IGNORE_CASE)
        val mEn = aboutRegexEn.find(text)
        if (mEn != null) {
            val entity = cleanEntityName(mEn.groupValues[1])
            if (entity.isNotBlank() && !isGenericStopWord(entity)) {
                return listOf(entity)
            }
        }

        return emptyList()
    }

    private fun cleanEntityName(raw: String): String {
        var clean = raw.trim()
        clean = clean.replace(Regex("""^(?:theek\s+hai|arre|bhai|sun|achha|ok|okay|please|pls|plz|yaar|dekho|think\s+rum)\s+""", RegexOption.IGNORE_CASE), "")
        clean = clean.replace(Regex("""^(?:mujhe|hume|mujhko|please|can\s+you)\s+""", RegexOption.IGNORE_CASE), "")
        return clean.trim()
    }

    private fun isGenericStopWord(word: String): Boolean {
        val lower = word.lowercase(Locale.ROOT).trim()
        val stopWords = setOf("kuch", "kya", "yeh", "woh", "this", "that", "it", "something", "anything")
        return stopWords.contains(lower)
    }

    /**
     * Gets valid active context if not expired.
     */
    fun getActiveContext(): ConversationContext? {
        val ctx = _currentContext.value ?: return null
        return if (ctx.isExpired(CONTEXT_TIMEOUT_MS)) {
            null
        } else {
            ctx
        }
    }

    /**
     * Clears current context.
     */
    fun clear() {
        _currentContext.value = null
    }
}

