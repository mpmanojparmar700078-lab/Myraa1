package com.example

import com.example.brain.ConfidenceEngine
import com.example.brain.PrivacyFilter
import com.example.data.ExperienceEntity
import com.example.data.LearnedSkillEntity
import com.example.models.IntentType
import com.example.services.LocalCommandParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MyraBrainUnitTest {

    private val parser = LocalCommandParser()
    private val confidenceEngine = ConfidenceEngine()

    @Test
    fun testLocalCommandParser_YouTubeVariations() {
        val variations = listOf(
            "youtube kholo",
            "youtube chalao",
            "youtube open karo",
            "youtube चला दो",
            "mujhe youtube kholkar do",
            "kripya youtube chala do",
            "youtube"
        )

        for (query in variations) {
            val res = parser.parse(query)
            assertTrue("Expected handled for '$query'", res.handled)
            assertEquals("Expected OPEN_APP for '$query' but got ${res.intent?.type} (intent: ${res.intent})", IntentType.OPEN_APP, res.intent?.type)
            assertEquals("youtube", res.intent?.app?.lowercase())
        }
    }

    @Test
    fun testLocalCommandParser_NavigationAndControl() {
        // Back
        val backResult = parser.parse("peeche jao")
        assertTrue(backResult.handled)
        assertEquals(IntentType.GO_BACK, backResult.intent?.type)

        // Home
        val homeResult = parser.parse("home screen jao")
        assertTrue(homeResult.handled)
        assertEquals(IntentType.GO_HOME, homeResult.intent?.type)

        // Scroll
        val scrollDownResult = parser.parse("neeche karo")
        assertTrue(scrollDownResult.handled)
        assertEquals(IntentType.SCROLL_DOWN, scrollDownResult.intent?.type)

        val scrollUpResult = parser.parse("upar scroll karo")
        assertTrue(scrollUpResult.handled)
        assertEquals(IntentType.SCROLL_UP, scrollUpResult.intent?.type)

        // Cancel
        val cancelResult = parser.parse("cancel karo")
        assertTrue(cancelResult.handled)
        assertEquals(IntentType.CANCEL_REQUEST, cancelResult.intent?.type)
    }

    @Test
    fun testPrivacyFilter_RedactsAndDetects() {
        assertTrue(PrivacyFilter.isSensitive("my api key is AIzaSyD3x918347102938471928347192834"))
        assertTrue(PrivacyFilter.isSensitive("password: secretPassword123"))
        assertTrue(PrivacyFilter.isSensitive("your otp is 482910"))
        assertTrue(PrivacyFilter.isSensitive("mera password hai abcd123"))

        assertFalse(PrivacyFilter.isSensitive("YouTube kholo"))
        assertFalse(PrivacyFilter.isSensitive("Google par search karo taj mahal"))

        val redacted = PrivacyFilter.redactSensitive("password: mySecretPass123")
        assertTrue(redacted.contains("[REDACTED]"))
    }

    @Test
    fun testConfidenceEngine_Calculations() {
        val skill = LearnedSkillEntity(
            skillName = "SKILL_TEST",
            triggerPatternsJson = "[]",
            actionsJson = "[]",
            confidence = 0.85f,
            successCount = 3,
            failureCount = 0
        )
        val score = confidenceEngine.calculateSkillConfidence(skill)
        assertTrue(score >= 0.85f)
        assertTrue(confidenceEngine.isConfidentForLocalExecution(score))

        val exp = ExperienceEntity(
            userCommand = "open camera",
            normalizedCommand = "open camera",
            intentType = "OPEN_APP",
            confidence = 0.90f,
            successCount = 2,
            failureCount = 0
        )
        val expScore = confidenceEngine.calculateExperienceConfidence(exp)
        assertTrue(expScore >= 0.90f)
        assertTrue(confidenceEngine.isConfidentForLocalExecution(expScore))
    }
}
