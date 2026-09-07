package com.example

import com.example.accessibility.YouTubeResultAnalyzer
import com.example.brain.ConfidenceEngine
import com.example.brain.PrivacyFilter
import com.example.brain.RecentRequestContext
import com.example.brain.RecordedTask
import com.example.data.ExperienceEntity
import com.example.data.LearnedSkillEntity
import com.example.models.ActionResult
import com.example.models.IntentType
import com.example.models.ParsedIntent
import com.example.models.RequestResult
import com.example.models.RequestState
import com.example.models.ScreenNodeInfo
import com.example.services.LocalCommandParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MyraBrainUnitTest {

    private val parser = LocalCommandParser()
    private val confidenceEngine = ConfidenceEngine()
    private val youTubeAnalyzer = YouTubeResultAnalyzer()

    // Test 1: "youtube par desi gamer ka video play kro"
    @Test
    fun test1_YouTubeParDesiGamerKaVideoPlayKro() {
        val res = parser.parse("youtube par desi gamer ka video play kro")
        assertTrue("Expected handled", res.handled)
        assertNotNull(res.intent)
        assertEquals(IntentType.SEARCH_AND_PLAY, res.intent?.type)
        assertEquals("youtube", res.intent?.app?.lowercase())
        assertEquals("desi gamer", res.intent?.query?.trim()?.lowercase())
    }

    // Test 2: "YouTube par Desi Gamer ka video play karo"
    @Test
    fun test2_YouTubeParDesiGamerKaVideoPlayKaro() {
        val res = parser.parse("YouTube par Desi Gamer ka video play karo")
        assertTrue("Expected handled", res.handled)
        assertNotNull(res.intent)
        assertEquals(IntentType.SEARCH_AND_PLAY, res.intent?.type)
        assertEquals("youtube", res.intent?.app?.lowercase())
        assertEquals("desi gamer", res.intent?.query?.trim()?.lowercase())
    }

    // Test 3: "YouTube pe Desi Gamer ka video chalao"
    @Test
    fun test3_YouTubePeDesiGamerKaVideoChalao() {
        val res = parser.parse("YouTube pe Desi Gamer ka video chalao")
        assertTrue("Expected handled", res.handled)
        assertNotNull(res.intent)
        assertEquals(IntentType.SEARCH_AND_PLAY, res.intent?.type)
        assertEquals("youtube", res.intent?.app?.lowercase())
        assertEquals("desi gamer", res.intent?.query?.trim()?.lowercase())
    }

    // Test 4: "YouTube mein Desi Gamer ka video चलाओ"
    @Test
    fun test4_YouTubeMeinDesiGamerKaVideoChalaoHindi() {
        val res = parser.parse("YouTube mein Desi Gamer ka video चलाओ")
        assertTrue("Expected handled", res.handled)
        assertNotNull(res.intent)
        assertEquals(IntentType.SEARCH_AND_PLAY, res.intent?.type)
        assertEquals("youtube", res.intent?.app?.lowercase())
        assertEquals("desi gamer", res.intent?.query?.trim()?.lowercase())
    }

    // Test 5: "YouTube पर Desi Gamer का वीडियो प्ले करो"
    @Test
    fun test5_YouTubeDevanagariDesiGamerPlay() {
        val res = parser.parse("YouTube पर Desi Gamer का वीडियो प्ले करो")
        assertTrue("Expected handled", res.handled)
        assertNotNull(res.intent)
        assertEquals(IntentType.SEARCH_AND_PLAY, res.intent?.type)
        assertEquals("youtube", res.intent?.app?.lowercase())
        assertEquals("desi gamer", res.intent?.query?.trim()?.lowercase())
    }

    // Test 6: "Desi Gamer ka video YouTube par chala do"
    @Test
    fun test6_DesiGamerKaVideoYouTubeParChalaDo() {
        val res = parser.parse("Desi Gamer ka video YouTube par chala do")
        assertTrue("Expected handled", res.handled)
        assertNotNull(res.intent)
        assertEquals(IntentType.SEARCH_AND_PLAY, res.intent?.type)
        assertEquals("youtube", res.intent?.app?.lowercase())
        assertEquals("desi gamer", res.intent?.query?.trim()?.lowercase())
    }

    // Test 7: "Desi Gamer ki video YouTube pe play kar do"
    @Test
    fun test7_DesiGamerKiVideoYouTubePePlayKarDo() {
        val res = parser.parse("Desi Gamer ki video YouTube pe play kar do")
        assertTrue("Expected handled", res.handled)
        assertNotNull(res.intent)
        assertEquals(IntentType.SEARCH_AND_PLAY, res.intent?.type)
        assertEquals("youtube", res.intent?.app?.lowercase())
        assertEquals("desi gamer", res.intent?.query?.trim()?.lowercase())
    }

    // Test 8: "mene kya karne ko bola" -> RECALL_RECENT_REQUESTS
    @Test
    fun test8_ContextQuestion_RecallRecentRequests() {
        val variations = listOf(
            "mene kya karne ko bola",
            "maine kya karne ko bola",
            "maine kya kaha tha",
            "maine kya bola",
            "mene kya bola",
            "what did i ask you to do",
            "मैंने क्या बोला था",
            "मैंने क्या करने को बोला"
        )

        for (q in variations) {
            val res = parser.parse(q)
            assertTrue("Expected handled for '$q'", res.handled)
            assertEquals("Expected RECALL_RECENT_REQUESTS for '$q'", IntentType.RECALL_RECENT_REQUESTS, res.intent?.type)
        }

        // Test answering from local history without Gemini
        val context = RecentRequestContext()
        context.recordNewRequest(
            requestId = 1L,
            rawCommand = "YouTube par Desi Gamer ka video play karo",
            normalizedCommand = "youtube par desi gamer ka video play karo",
            intent = ParsedIntent(type = IntentType.SEARCH_AND_PLAY, app = "youtube", query = "Desi Gamer"),
            targetApp = "youtube",
            query = "Desi Gamer",
            plannedActions = listOf("OPEN_YOUTUBE", "SEARCH", "PLAY")
        )
        val recallMsg = context.formatRecallMessage()
        assertTrue("Should contain recent command", recallMsg.contains("YouTube par Desi Gamer ka video play karo"))
    }

    // Test 9: "tumne kya kiya" -> REPORT_LAST_EXECUTION
    @Test
    fun test9_ContextQuestion_ReportLastExecution() {
        val variations = listOf(
            "tumne kya kiya",
            "kya kiya tumne",
            "hua kya",
            "kaam hua",
            "status kya hai",
            "what did you do",
            "तुमने क्या किया"
        )

        for (q in variations) {
            val res = parser.parse(q)
            assertTrue("Expected handled for '$q'", res.handled)
            assertEquals("Expected REPORT_LAST_EXECUTION for '$q'", IntentType.REPORT_LAST_EXECUTION, res.intent?.type)
        }

        val context = RecentRequestContext()
        context.recordNewRequest(
            requestId = 1L,
            rawCommand = "youtube par desi gamer play karo",
            normalizedCommand = "youtube par desi gamer play karo",
            intent = ParsedIntent(type = IntentType.SEARCH_AND_PLAY, app = "youtube", query = "desi gamer"),
            targetApp = "youtube",
            query = "desi gamer",
            plannedActions = listOf("OPEN_YOUTUBE", "SEARCH", "PLAY")
        )
        context.updateRequestState(
            requestId = 1L,
            state = RequestState.SUCCESS,
            result = RequestResult(
                requestId = 1L,
                state = RequestState.SUCCESS,
                isVerified = true,
                summary = "YouTube पर 'desi gamer' का वीडियो चालू हो गया है।"
            )
        )
        val report = context.formatLastExecutionReport()
        assertTrue("Report must indicate success", report.contains("काम पूरा हुआ") || report.contains("सफलतापूर्वक"))
    }

    // Test 10: False success scenario: YouTube opens, playback not verified -> Must NOT claim "video chal gaya"
    @Test
    fun test10_FalseSuccessAvoidance() {
        val context = RecentRequestContext()
        context.recordNewRequest(
            requestId = 1L,
            rawCommand = "youtube par desi gamer play karo",
            normalizedCommand = "youtube par desi gamer play karo",
            intent = ParsedIntent(type = IntentType.SEARCH_AND_PLAY, app = "youtube", query = "desi gamer"),
            targetApp = "youtube",
            query = "desi gamer",
            plannedActions = listOf("OPEN_YOUTUBE", "SEARCH_QUERY", "SELECT_VIDEO", "VERIFY_PLAYBACK")
        )

        // Intermediate action succeeded (YouTube opened), but playback failed / not verified
        val intermediateResult = ActionResult(
            success = false,
            partial = true,
            isVerified = false,
            message = "मैंने YouTube पर 'desi gamer' खोजने की कोशिश की, लेकिन वीडियो playback verify नहीं हो पाया।"
        )

        // Update context with partial success / unverified state
        context.updateRequestState(
            requestId = 1L,
            state = RequestState.PARTIAL_SUCCESS,
            result = RequestResult(
                requestId = 1L,
                state = RequestState.PARTIAL_SUCCESS,
                isVerified = false,
                summary = intermediateResult.message,
                failureReason = "Playback could not be verified"
            ),
            completedAction = "OPEN_YOUTUBE",
            failedAction = "VERIFY_PLAYBACK"
        )

        val report = context.formatLastExecutionReport()

        // Critical verification: Must NOT say "काम पूरा हुआ" or "चालू हो गया" or "वीडियो चल गया"
        assertFalse("Must NOT falsely claim video started playing", report.contains("चालू हो गया") || report.contains("चल गया"))
        assertTrue("Must report partial verification", report.contains("verify नहीं") || report.contains("अधूरा"))
    }

    // Test 11: Multi-command handling
    @Test
    fun test11_MultiCommandExecution() {
        val command = "Desi Gamer ka video YouTube par play karo aur Chrome mein Free Fire Craftland kholo"
        val res = parser.parse(command)
        assertTrue("Expected handled", res.handled)
        assertEquals(IntentType.MULTI_ACTION, res.intent?.type)

        val subIntents = res.intent?.actions ?: emptyList()
        assertEquals(2, subIntents.size)

        assertEquals(IntentType.SEARCH_AND_PLAY, subIntents[0].type)
        assertEquals("desi gamer", subIntents[0].query?.lowercase()?.trim())

        assertEquals(IntentType.OPEN_PAGE, subIntents[1].type)
        assertEquals("chrome", subIntents[1].app?.lowercase())
        assertEquals("Free Fire Craftland", subIntents[1].query)
    }

    // Test 12: YouTube Result Analyzer semantic ranking
    @Test
    fun test12_YouTubeResultAnalyzerSemanticMatching() {
        val nodes = listOf(
            ScreenNodeInfo(contentDescription = "Ad · Download Game Now", isClickable = true),
            ScreenNodeInfo(contentDescription = "Desi Gamers Live - 100K views - 2 hours ago", isClickable = true, text = "Desi Gamers Live"),
            ScreenNodeInfo(contentDescription = "Minecraft Gameplay By Random Guy", isClickable = true, text = "Minecraft")
        )

        val matched = youTubeAnalyzer.findBestMatchingVideo(nodes, "desi gamer")
        assertNotNull("Should find matching video", matched)
        assertTrue(matched?.contentDescription?.contains("Desi Gamers Live") == true)

        // Test playback started verification
        val playingNodes = listOf(
            ScreenNodeInfo(contentDescription = "Pause video", isClickable = true, resourceId = "com.google.android.youtube:id/pause_button")
        )
        assertTrue(youTubeAnalyzer.verifyPlaybackStarted(playingNodes))

        val pausedNodes = listOf(
            ScreenNodeInfo(contentDescription = "Play video", isClickable = true, resourceId = "com.google.android.youtube:id/play_button")
        )
        assertFalse(youTubeAnalyzer.verifyPlaybackStarted(pausedNodes))
    }

    @Test
    fun testLocalCommandParser_YouTubeOpenVariations() {
        val variations = listOf(
            "youtube kholo",
            "youtube open karo",
            "youtube चला दो",
            "mujhe youtube kholkar do",
            "kripya youtube chala do",
            "open youtube"
        )

        for (query in variations) {
            val res = parser.parse(query)
            assertTrue("Expected handled for '$query'", res.handled)
            assertEquals("Expected OPEN_APP for '$query'", IntentType.OPEN_APP, res.intent?.type)
            assertEquals("youtube", res.intent?.app?.lowercase())
        }
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
}
