package com.example.services

import com.example.models.CandidateItem
import com.example.models.ConversationContext
import com.example.models.ConversationContextTracker
import com.example.models.ReferenceConfidence
import com.example.models.ResolvedReference
import com.example.models.TaskContext
import java.util.Locale

/**
 * ReferenceResolver — Resolves pronouns, relative positions, and contextual entity references
 * against short-term task and conversation state.
 */
object ReferenceResolver {

    /**
     * Resolves a user phrase to a target entity, query, app, or candidate item index.
     */
    fun resolve(
        rawInput: String,
        taskContext: TaskContext? = null,
        conversationContext: ConversationContext? = ConversationContextTracker.getActiveContext()
    ): ResolvedReference {
        val cleanInput = rawInput.trim().lowercase(Locale.ROOT)

        // 1. Positional Index Matching ("पहला वाला", "दूसरा", "first one", "last one", etc.)
        val positionalIndex = extractPositionalIndex(cleanInput, taskContext?.candidates?.size ?: 0)
        if (positionalIndex != null) {
            val candidates = taskContext?.candidates ?: emptyList()
            val matchedCandidate = candidates.getOrNull(positionalIndex)
            val entity = matchedCandidate?.title ?: taskContext?.currentEntity ?: conversationContext?.effectiveEntity
            return ResolvedReference(
                targetEntity = entity,
                targetQuery = matchedCandidate?.title ?: taskContext?.currentQuery ?: conversationContext?.query,
                targetApp = taskContext?.currentApp ?: conversationContext?.activeTaskApp,
                targetIndex = positionalIndex,
                confidence = if (matchedCandidate != null) ReferenceConfidence.HIGH else ReferenceConfidence.MEDIUM,
                matchedCandidate = matchedCandidate,
                reason = "Resolved positional index: $positionalIndex"
            )
        }

        // 2. Pronoun Matching ("इसे", "इसको", "use", "isko", "ise", "it", "this")
        val hasPronoun = isPronounOrFollowUp(cleanInput)
        if (hasPronoun) {
            // Check for ambiguity in conversation context
            if (conversationContext != null && conversationContext.isAmbiguousEntity && conversationContext.candidateEntities.size >= 2) {
                val e1 = conversationContext.candidateEntities[0]
                val e2 = conversationContext.candidateEntities[1]
                return ResolvedReference(
                    confidence = ReferenceConfidence.LOW,
                    disambiguationPrompt = "आप $e1 या $e2 में से किसके बारे में बात कर रहे हैं?",
                    reason = "Ambiguous entity candidates in context: $e1 vs $e2"
                )
            }

            // Check task context candidates for selection
            val selected = taskContext?.selectedResult ?: taskContext?.candidates?.firstOrNull()
            val entity = selected?.title ?: taskContext?.currentEntity ?: conversationContext?.effectiveEntity
            val query = selected?.title ?: taskContext?.currentQuery ?: conversationContext?.query
            val app = taskContext?.currentApp ?: conversationContext?.activeTaskApp

            if (!entity.isNullOrBlank() || !query.isNullOrBlank()) {
                return ResolvedReference(
                    targetEntity = entity,
                    targetQuery = query,
                    targetApp = app,
                    targetIndex = selected?.index ?: 0,
                    confidence = ReferenceConfidence.HIGH,
                    matchedCandidate = selected,
                    reason = "Resolved pronoun using active task/conversation context: ${entity ?: query}"
                )
            }
        }

        // 3. Entity Descriptor Matching (e.g. "Free Fire वाला", "song वाला", "trailer वाला")
        val descriptorRegex = Regex("""^(.+?)\s*(?:वाला|वाली|वाले|wala|wali|wale)$""", RegexOption.IGNORE_CASE)
        val mDesc = descriptorRegex.find(cleanInput)
        if (mDesc != null) {
            val keyword = mDesc.groupValues[1].trim()
            val candidates = taskContext?.candidates ?: emptyList()
            val matched = candidates.firstOrNull { it.title.contains(keyword, ignoreCase = true) }
            if (matched != null) {
                return ResolvedReference(
                    targetEntity = matched.title,
                    targetQuery = matched.title,
                    targetApp = taskContext?.currentApp ?: conversationContext?.activeTaskApp,
                    targetIndex = matched.index,
                    confidence = ReferenceConfidence.HIGH,
                    matchedCandidate = matched,
                    reason = "Matched candidate by keyword '$keyword': ${matched.title}"
                )
            } else if (keyword.isNotBlank()) {
                return ResolvedReference(
                    targetEntity = keyword,
                    targetQuery = keyword,
                    targetApp = taskContext?.currentApp ?: conversationContext?.activeTaskApp,
                    confidence = ReferenceConfidence.MEDIUM,
                    reason = "Resolved keyword descriptor: $keyword"
                )
            }
        }

        // 4. Fallback to existing active entity if nothing else matched
        val fallbackEntity = taskContext?.currentEntity ?: conversationContext?.effectiveEntity
        val fallbackQuery = taskContext?.currentQuery ?: conversationContext?.query
        if (!fallbackEntity.isNullOrBlank() || !fallbackQuery.isNullOrBlank()) {
            return ResolvedReference(
                targetEntity = fallbackEntity,
                targetQuery = fallbackQuery,
                targetApp = taskContext?.currentApp ?: conversationContext?.activeTaskApp,
                confidence = ReferenceConfidence.MEDIUM,
                reason = "Fallback to current context entity: ${fallbackEntity ?: fallbackQuery}"
            )
        }

        return ResolvedReference(
            confidence = ReferenceConfidence.UNKNOWN,
            reason = "No context or reference found"
        )
    }

    /**
     * Extracts numerical/positional index from Hindi, English, and Hinglish phrases.
     */
    fun extractPositionalIndex(lowerInput: String, candidateCount: Int = 0): Int? {
        val text = lowerInput.trim().replace(Regex("""[?!.,;।|]+$"""), "").trim()

        // 1st / First / पहला
        val firstPatterns = setOf(
            "पहला", "पहला वाला", "पहला वाला चलाओ", "pehla", "pehla wala", "first", "first one",
            "1st", "1st one", "top", "top one", "ऊपर वाला", "upar wala", "1 वाला", "1st wala"
        )
        if (firstPatterns.any { text == it || text.startsWith("$it ") || text.endsWith(" $it") }) {
            return 0
        }

        // 2nd / Second / दूसरा
        val secondPatterns = setOf(
            "दूसरा", "दूसरा वाला", "दूसरा वाला चलाओ", "dusra", "doosra", "dusra wala", "doosra wala",
            "second", "second one", "2nd", "2nd one", "2 वाला", "2nd wala"
        )
        if (secondPatterns.any { text == it || text.startsWith("$it ") || text.endsWith(" $it") }) {
            return 1
        }

        // 3rd / Third / तीसरा
        val thirdPatterns = setOf(
            "तीसरा", "तीसरा वाला", "teesra", "tisra", "teesra wala", "third", "third one",
            "3rd", "3rd one", "3 वाला", "3rd wala"
        )
        if (thirdPatterns.any { text == it || text.startsWith("$it ") || text.endsWith(" $it") }) {
            return 2
        }

        // 4th / Fourth / चौथा
        val fourthPatterns = setOf(
            "चौथा", "चौथा वाला", "chautha", "chautha wala", "fourth", "fourth one", "4th", "4th one"
        )
        if (fourthPatterns.any { text == it || text.startsWith("$it ") || text.endsWith(" $it") }) {
            return 3
        }

        // Last / Bottom / आखिरी / नीचे वाला
        val lastPatterns = setOf(
            "आखिरी", "आखिरी वाला", "aakhri", "aakhri wala", "last", "last one",
            "नीचे वाला", "neeche wala", "bottom", "bottom one"
        )
        if (lastPatterns.any { text == it || text.startsWith("$it ") || text.endsWith(" $it") }) {
            return if (candidateCount > 0) candidateCount - 1 else 0
        }

        return null
    }

    /**
     * Checks if input contains pronoun or reference triggers.
     */
    fun isPronounOrFollowUp(lowerInput: String): Boolean {
        val text = lowerInput.trim().replace(Regex("""[?!.,;।|]+$"""), "").trim()
        val triggers = listOf(
            "इसे", "इसको", "इसका", "इसकी", "इसमें", "उसको", "उसका", "उसे",
            "isko", "ise", "use", "usko", "iska", "iski", "isme", "usme",
            "it", "this", "that", "the video", "that video", "this video",
            "अब इसे", "ab ise", "ab isko", "isko play", "ise play", "play it", "play this",
            "वही", "same one", "again", "फिर से"
        )
        return triggers.any { text.contains(it) }
    }
}
