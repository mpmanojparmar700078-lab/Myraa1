package com.example.services

import com.example.models.ActionChain
import com.example.models.ActionStep
import com.example.models.ChainActionType
import com.example.models.IntentType
import com.example.models.ParsedIntent
import com.example.models.ReferenceConfidence
import com.example.models.ScreenStateCategory
import com.example.models.TaskContext
import com.example.models.VerificationRule
import java.util.UUID

/**
 * ActionChainPlanner — Deconstructs natural language commands and ParsedIntents
 * into fully structured, verified ActionChains.
 */
class ActionChainPlanner(
    private val localCommandParser: LocalCommandParser = LocalCommandParser()
) {

    /**
     * Plans an ActionChain from a ParsedIntent.
     */
    fun planFromIntent(
        intent: ParsedIntent,
        originalCommand: String,
        taskContext: TaskContext? = null
    ): ActionChain {
        val chain = ActionChain(
            chainId = UUID.randomUUID().toString(),
            taskTitle = generateTaskTitle(intent, originalCommand),
            originalCommand = originalCommand,
            contextSnapshot = taskContext
        )

        when (intent.type) {
            IntentType.MULTI_ACTION -> {
                var prevStepId: String? = null
                for (subIntent in intent.actions) {
                    val subSteps = buildStepsForSingleIntent(subIntent, prevStepId)
                    for (step in subSteps) {
                        chain.steps.add(step)
                        prevStepId = step.id
                    }
                }
            }

            IntentType.YOUTUBE_SEARCH_AND_PLAY -> {
                val query = intent.query ?: intent.target ?: ""
                val targetIndex = intent.durationMs?.toInt() ?: 0

                val openYtStep = ActionStep(
                    type = ChainActionType.OPEN_APP,
                    target = "YouTube",
                    parameters = mapOf("app" to "YouTube", "package" to "com.google.android.youtube"),
                    expectedPackage = "com.google.android.youtube",
                    expectedScreenCategory = ScreenStateCategory.HOME_SCREEN,
                    verificationRule = VerificationRule.PackageChanged("com.google.android.youtube"),
                    description = "YouTube खोलें"
                )

                val searchStep = ActionStep(
                    type = ChainActionType.SEARCH,
                    target = query,
                    parameters = mapOf("query" to query, "package" to "com.google.android.youtube"),
                    expectedPackage = "com.google.android.youtube",
                    expectedScreenCategory = ScreenStateCategory.SEARCH_RESULTS,
                    verificationRule = VerificationRule.SearchResultsLoaded,
                    dependsOnStepId = openYtStep.id,
                    description = "YouTube पर '$query' खोजें"
                )

                val playStep = ActionStep(
                    type = ChainActionType.PLAY_VIDEO,
                    target = query,
                    parameters = mapOf("query" to query, "targetIndex" to targetIndex, "package" to "com.google.android.youtube"),
                    expectedPackage = "com.google.android.youtube",
                    expectedScreenCategory = ScreenStateCategory.VIDEO_SCREEN,
                    verificationRule = VerificationRule.VideoPlaying,
                    dependsOnStepId = searchStep.id,
                    description = "उपयुक्त वीडियो चलाएं"
                )

                chain.steps.addAll(listOf(openYtStep, searchStep, playStep))
            }

            IntentType.YOUTUBE_SEARCH -> {
                val query = intent.query ?: intent.target ?: ""
                val openYtStep = ActionStep(
                    type = ChainActionType.OPEN_APP,
                    target = "YouTube",
                    parameters = mapOf("app" to "YouTube", "package" to "com.google.android.youtube"),
                    expectedPackage = "com.google.android.youtube",
                    expectedScreenCategory = ScreenStateCategory.HOME_SCREEN,
                    verificationRule = VerificationRule.PackageChanged("com.google.android.youtube"),
                    description = "YouTube खोलें"
                )
                val searchStep = ActionStep(
                    type = ChainActionType.SEARCH,
                    target = query,
                    parameters = mapOf("query" to query, "package" to "com.google.android.youtube"),
                    expectedPackage = "com.google.android.youtube",
                    expectedScreenCategory = ScreenStateCategory.SEARCH_RESULTS,
                    verificationRule = VerificationRule.SearchResultsLoaded,
                    dependsOnStepId = openYtStep.id,
                    description = "YouTube पर '$query' खोजें"
                )
                chain.steps.addAll(listOf(openYtStep, searchStep))
            }

            IntentType.WEB_SEARCH -> {
                val query = intent.query ?: intent.target ?: ""
                val openChromeStep = ActionStep(
                    type = ChainActionType.OPEN_APP,
                    target = "Chrome",
                    parameters = mapOf("app" to "Chrome", "package" to "com.android.chrome"),
                    expectedPackage = "com.android.chrome",
                    expectedScreenCategory = ScreenStateCategory.HOME_SCREEN,
                    verificationRule = VerificationRule.PackageChanged("com.android.chrome"),
                    description = "Chrome खोलें"
                )
                val searchStep = ActionStep(
                    type = ChainActionType.SEARCH,
                    target = query,
                    parameters = mapOf("query" to query, "package" to "com.android.chrome"),
                    expectedPackage = "com.android.chrome",
                    expectedScreenCategory = ScreenStateCategory.SEARCH_RESULTS,
                    verificationRule = VerificationRule.SearchResultsLoaded,
                    dependsOnStepId = openChromeStep.id,
                    description = "Google पर '$query' खोजें"
                )
                chain.steps.addAll(listOf(openChromeStep, searchStep))
            }

            IntentType.OPEN_APP -> {
                val app = intent.app ?: "App"
                val step = ActionStep(
                    type = ChainActionType.OPEN_APP,
                    target = app,
                    parameters = mapOf("app" to app),
                    description = "$app खोलें"
                )
                chain.steps.add(step)
            }

            IntentType.OPEN_SETTINGS -> {
                val step = ActionStep(
                    type = ChainActionType.OPEN_APP,
                    target = "Settings",
                    parameters = mapOf("app" to "Settings"),
                    expectedPackage = "com.android.settings",
                    verificationRule = VerificationRule.PackageChanged("com.android.settings"),
                    description = "Settings खोलें"
                )
                chain.steps.add(step)
            }

            IntentType.BACK -> {
                val step = ActionStep(
                    type = ChainActionType.BACK,
                    verificationRule = VerificationRule.SignatureChanged,
                    description = "वापस जाएं"
                )
                chain.steps.add(step)
            }

            IntentType.WAIT -> {
                val duration = intent.durationMs ?: 1000L
                val step = ActionStep(
                    type = ChainActionType.WAIT,
                    parameters = mapOf("durationMs" to duration),
                    description = "${duration}ms प्रतीक्षा करें"
                )
                chain.steps.add(step)
            }

            else -> {
                val step = ActionStep(
                    type = ChainActionType.CUSTOM,
                    parameters = mapOf("type" to intent.type.name),
                    description = intent.responseText ?: "क्रिया निष्पादित करें"
                )
                chain.steps.add(step)
            }
        }

        return chain
    }

    /**
     * Plans an ActionChain directly from raw user input, checking ReferenceResolver and multi-action decomposition.
     */
    fun planFromRawInput(
        rawInput: String,
        taskContext: TaskContext? = null
    ): ActionChain {
        // 1. Check for positional/pronoun follow-up references
        val resolvedRef = ReferenceResolver.resolve(rawInput, taskContext)
        if (resolvedRef.confidence == ReferenceConfidence.HIGH && resolvedRef.targetQuery != null) {
            val query = resolvedRef.targetQuery
            val app = resolvedRef.targetApp ?: "YouTube"
            val index = resolvedRef.targetIndex ?: 0

            if (app.equals("YouTube", ignoreCase = true) || app.contains("youtube", ignoreCase = true)) {
                return planFromIntent(
                    intent = ParsedIntent(
                        type = IntentType.YOUTUBE_SEARCH_AND_PLAY,
                        query = query,
                        durationMs = index.toLong(),
                        responseText = "YouTube पर '$query' का $index वीडियो चलाया जा रहा है…"
                    ),
                    originalCommand = rawInput,
                    taskContext = taskContext
                )
            }
        }

        // 2. Parse via LocalCommandParser
        val localResult = localCommandParser.parse(rawInput)
        if (localResult.recognized && localResult.parsedIntent != null) {
            return planFromIntent(localResult.parsedIntent, rawInput, taskContext)
        }

        // 3. Default fallback single step chain
        return ActionChain(
            taskTitle = "Execute Command",
            originalCommand = rawInput,
            steps = mutableListOf(
                ActionStep(
                    type = ChainActionType.CUSTOM,
                    parameters = mapOf("raw" to rawInput),
                    description = rawInput
                )
            ),
            contextSnapshot = taskContext
        )
    }

    private fun buildStepsForSingleIntent(
        intent: ParsedIntent,
        dependsOnId: String?
    ): List<ActionStep> {
        val steps = mutableListOf<ActionStep>()
        when (intent.type) {
            IntentType.OPEN_APP -> {
                steps.add(
                    ActionStep(
                        type = ChainActionType.OPEN_APP,
                        target = intent.app ?: "App",
                        parameters = mapOf("app" to (intent.app ?: "App")),
                        dependsOnStepId = dependsOnId,
                        description = "${intent.app ?: "App"} खोलें"
                    )
                )
            }
            IntentType.OPEN_SETTINGS -> {
                steps.add(
                    ActionStep(
                        type = ChainActionType.OPEN_APP,
                        target = "Settings",
                        parameters = mapOf("app" to "Settings"),
                        expectedPackage = "com.android.settings",
                        verificationRule = VerificationRule.PackageChanged("com.android.settings"),
                        dependsOnStepId = dependsOnId,
                        description = "Settings खोलें"
                    )
                )
            }
            IntentType.WEB_SEARCH -> {
                val query = intent.query ?: intent.target ?: ""
                steps.add(
                    ActionStep(
                        type = ChainActionType.SEARCH,
                        target = query,
                        parameters = mapOf("query" to query, "package" to "com.android.chrome"),
                        expectedPackage = "com.android.chrome",
                        verificationRule = VerificationRule.SearchResultsLoaded,
                        dependsOnStepId = dependsOnId,
                        description = "Google पर '$query' खोजें"
                    )
                )
            }
            IntentType.YOUTUBE_SEARCH -> {
                val query = intent.query ?: intent.target ?: ""
                steps.add(
                    ActionStep(
                        type = ChainActionType.SEARCH,
                        target = query,
                        parameters = mapOf("query" to query, "package" to "com.google.android.youtube"),
                        expectedPackage = "com.google.android.youtube",
                        verificationRule = VerificationRule.SearchResultsLoaded,
                        dependsOnStepId = dependsOnId,
                        description = "YouTube पर '$query' खोजें"
                    )
                )
            }
            IntentType.YOUTUBE_SEARCH_AND_PLAY -> {
                val query = intent.query ?: intent.target ?: ""
                val searchStep = ActionStep(
                    type = ChainActionType.SEARCH,
                    target = query,
                    parameters = mapOf("query" to query, "package" to "com.google.android.youtube"),
                    expectedPackage = "com.google.android.youtube",
                    expectedScreenCategory = ScreenStateCategory.SEARCH_RESULTS,
                    verificationRule = VerificationRule.SearchResultsLoaded,
                    dependsOnStepId = dependsOnId,
                    description = "YouTube पर '$query' खोजें"
                )
                val playStep = ActionStep(
                    type = ChainActionType.PLAY_VIDEO,
                    target = query,
                    parameters = mapOf("query" to query, "package" to "com.google.android.youtube"),
                    expectedPackage = "com.google.android.youtube",
                    expectedScreenCategory = ScreenStateCategory.VIDEO_SCREEN,
                    verificationRule = VerificationRule.VideoPlaying,
                    dependsOnStepId = searchStep.id,
                    description = "वीडियो चलाएं"
                )
                steps.add(searchStep)
                steps.add(playStep)
            }
            IntentType.BACK -> {
                steps.add(
                    ActionStep(
                        type = ChainActionType.BACK,
                        verificationRule = VerificationRule.SignatureChanged,
                        dependsOnStepId = dependsOnId,
                        description = "वापस जाएं"
                    )
                )
            }
            IntentType.WAIT -> {
                val duration = intent.durationMs ?: 1000L
                steps.add(
                    ActionStep(
                        type = ChainActionType.WAIT,
                        parameters = mapOf("durationMs" to duration),
                        dependsOnStepId = dependsOnId,
                        description = "${duration}ms प्रतीक्षा करें"
                    )
                )
            }
            else -> {
                steps.add(
                    ActionStep(
                        type = ChainActionType.CUSTOM,
                        parameters = mapOf("type" to intent.type.name),
                        dependsOnStepId = dependsOnId,
                        description = intent.responseText ?: "क्रिया निष्पादित करें"
                    )
                )
            }
        }
        return steps
    }

    private fun generateTaskTitle(intent: ParsedIntent, originalCommand: String): String {
        return when (intent.type) {
            IntentType.MULTI_ACTION -> "Multi-Step Task (${intent.actions.size} actions)"
            IntentType.YOUTUBE_SEARCH_AND_PLAY -> "Search & Play '${intent.query ?: intent.target}'"
            IntentType.YOUTUBE_SEARCH -> "Search YouTube '${intent.query ?: intent.target}'"
            IntentType.WEB_SEARCH -> "Search Google '${intent.query ?: intent.target}'"
            IntentType.OPEN_APP -> "Open ${intent.app ?: "App"}"
            IntentType.OPEN_SETTINGS -> "Open Settings"
            IntentType.BACK -> "Navigate Back"
            else -> originalCommand.take(30)
        }
    }
}
