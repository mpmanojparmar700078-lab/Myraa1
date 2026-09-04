package com.example.automation

import com.example.models.ScreenState
import com.example.services.MyraAccessibilityService

data class VerificationOutcome(
    val isVerified: Boolean,
    val explanation: String
)

/**
 * Action Verifier ensuring executed actions produced concrete, observable UI state changes.
 * Enforces rule: Never claim "Done" unless the action was actually executed and verified.
 */
class ActionVerifier {

    /**
     * Verifies action outcome by comparing pre-action and post-action screen states.
     */
    fun verifyAction(
        action: PlannedAction,
        screenBefore: ScreenState?,
        screenAfter: ScreenState?
    ): VerificationOutcome {
        if (screenAfter == null) {
            return VerificationOutcome(false, "Cannot capture post-action screen state for verification.")
        }

        return when (action.type) {
            PlannedActionType.OPEN_APP -> {
                val currentPkg = screenAfter.packageName ?: MyraAccessibilityService.currentActivePackage.value
                val matched = currentPkg != null && currentPkg.contains(action.target, ignoreCase = true)
                VerificationOutcome(
                    isVerified = matched,
                    explanation = if (matched) "Application ${action.target} is now visible in foreground." else "Package ${action.target} did not enter foreground."
                )
            }

            PlannedActionType.TYPE_TEXT -> {
                val textToFind = action.textToType ?: ""
                val textPresent = screenAfter.hasText(textToFind)
                VerificationOutcome(
                    isVerified = textPresent,
                    explanation = if (textPresent) "Verified entered text '$textToFind' in active screen elements." else "Entered text was not detected in post-action screen."
                )
            }

            PlannedActionType.TAP,
            PlannedActionType.CUSTOM_TOUCH,
            PlannedActionType.BACK,
            PlannedActionType.SCROLL,
            PlannedActionType.SWIPE,
            PlannedActionType.LONG_PRESS -> {
                val changed = screenBefore == null || screenBefore.hasChangedSignificantly(screenAfter)
                VerificationOutcome(
                    isVerified = changed,
                    explanation = if (changed) "Screen state transition verified." else "Screen content showed no detectable change."
                )
            }

            PlannedActionType.WAIT -> {
                VerificationOutcome(true, "Wait period completed.")
            }
        }
    }
}
