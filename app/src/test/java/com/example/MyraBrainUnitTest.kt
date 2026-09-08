package com.example

import com.example.accessibility.YouTubeResultAnalyzer
import com.example.brain.ConfidenceEngine
import com.example.brain.ConversationContext
import com.example.brain.MyraResponseEngine
import com.example.brain.PrivacyFilter
import com.example.brain.RecentRequestContext
import com.example.brain.RecordedTask
import com.example.data.ExperienceEntity
import com.example.data.LearnedSkillEntity
import com.example.models.ActionResult
import com.example.models.DetailedExecutionResult
import com.example.models.ExecutionStatus
import com.example.models.IntentType
import com.example.models.MessageCategory
import com.example.models.ParsedIntent
import com.example.models.PendingConfirmationType
import com.example.models.RequestResult
import com.example.models.RequestState
import com.example.models.ResponseType
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

    // Test 13: Meta Instruction ("me chahta hu vo sare steps youtube par ho or vo tum khud kro")
    // Must NOT be classified as SEARCH_YOUTUBE or launch search!
    @Test
    fun test13_MetaInstruction_NotSearchingYouTube() {
        val metaCommand = "me chahta hu vo sare steps youtube par ho or vo tum khud kro"
        val res = parser.parse(metaCommand)
        assertTrue("Expected handled", res.handled)
        assertNotNull(res.intent)
        assertEquals("Must be classified as META_INSTRUCTION", IntentType.META_INSTRUCTION, res.intent?.type)
        assertFalse("Must NOT be SEARCH_AND_PLAY", res.intent?.type == IntentType.SEARCH_AND_PLAY)
        assertFalse("Must NOT be YOUTUBE_SEARCH", res.intent?.type == IntentType.YOUTUBE_SEARCH)
    }

    // Test 14: Context Recall for Specific Index ("1 wale me tumne kya kiya")
    @Test
    fun test14_ContextResolution_1WaleMeTumneKyaKiya() {
        val cmd = "1 wale me tumne kya kiya"
        val res = parser.parse(cmd)
        assertTrue("Expected handled", res.handled)
        assertNotNull(res.intent)
        assertEquals(IntentType.REPORT_LAST_EXECUTION, res.intent?.type)
        assertEquals(1, res.intent?.targetIndex)

        // Test RecentRequestContext lookup with index
        val context = RecentRequestContext()
        context.recordNewRequest(
            requestId = 101L,
            rawCommand = "youtube par video play karo",
            normalizedCommand = "youtube par video play karo",
            intent = ParsedIntent(type = IntentType.SEARCH_AND_PLAY, app = "youtube", query = "video"),
            targetApp = "youtube",
            query = "video",
            plannedActions = listOf("OPEN_YOUTUBE")
        )
        context.updateRequestState(
            requestId = 101L,
            state = RequestState.PARTIAL_SUCCESS,
            result = RequestResult(
                requestId = 101L,
                state = RequestState.PARTIAL_SUCCESS,
                summary = "मैंने YouTube पर 'video' खोजने की कोशिश की, लेकिन वीडियो play होने की पुष्टि नहीं हुई।",
                isVerified = false
            ),
            isSearchOnlyStarted = true
        )

        val report = context.formatExecutionReportForIndex(1)
        assertTrue("Report must reference request #1", report.contains("अनुरोध #1"))
        assertTrue("Report must truthfully report partial search state", report.contains("खोज शुरू की") || report.contains("पुष्टि नहीं हुई"))
    }

    // Test 15: Failure Reporting ("nahi hua")
    // Must NOT be treated as a web or video search query!
    @Test
    fun test15_UserFailureReporting_NahiHua() {
        val res = parser.parse("nahi hua")
        assertTrue("Expected handled", res.handled)
        assertNotNull(res.intent)
        assertEquals(IntentType.REPORT_FAILURE, res.intent?.type)

        val resDev = parser.parse("नहीं हुआ")
        assertTrue("Expected handled", resDev.handled)
        assertEquals(IntentType.REPORT_FAILURE, resDev.intent?.type)
    }

    // Test 16: User Correction ("maine search karne ko nahi kaha tha")
    @Test
    fun test16_UserCorrection_MaineSearchKarneKoNahiKahaTha() {
        val res = parser.parse("maine search karne ko nahi kaha tha")
        assertTrue("Expected handled", res.handled)
        assertNotNull(res.intent)
        assertEquals(IntentType.CORRECT_PREVIOUS_RESULT, res.intent?.type)
    }

    // Test 17: Autonomous Challenge Request (6+ steps)
    @Test
    fun test17_ChallengeRequest_AutonomousWorkflow() {
        val res = parser.parse("tumhari marji se koi random target pura karo jisme kam se kam 6 steps hona chahiye")
        assertTrue("Expected handled", res.handled)
        assertNotNull(res.intent)
        assertEquals(IntentType.CHALLENGE_REQUEST, res.intent?.type)
    }

    // =========================================================================
    // CHAT & RESPONSE BRAIN V1 SPECIFIC TESTS
    // =========================================================================

    // Test 18: Greeting classification ("Hii", "Namaste", "Hello")
    @Test
    fun test18_GreetingClassification() {
        val greetings = listOf("Hii", "hi", "hello", "hey", "namaste", "नमस्ते", "हेलो")
        for (g in greetings) {
            val res = parser.parse(g)
            assertTrue("Expected handled for '$g'", res.handled)
            assertEquals("Expected GREETING category for '$g'", MessageCategory.GREETING, res.category)
            assertEquals("Expected GREETING intent for '$g'", IntentType.GREETING, res.intent?.type)
        }

        val responseEngine = MyraResponseEngine()
        val context = ConversationContext()
        val res = parser.parse("Hii")
        val response = responseEngine.generateResponse(res.category, res.intent!!, context)
        assertEquals(ResponseType.CONVERSATION, response.responseType)
        assertTrue(response.text.contains("नमस्ते") || response.text.contains("यहाँ हूँ"))
        assertFalse("Must NOT be an action search", response.text.contains("खोजा जा रहा"))
    }

    // Test 19: Conversation classification ("Kaise ho", "Theek hai")
    @Test
    fun test19_ConversationClassification() {
        val kaiseHo = parser.parse("Kaise ho")
        assertTrue(kaiseHo.handled)
        assertEquals(MessageCategory.GENERAL_CONVERSATION, kaiseHo.category)

        val theekHai = parser.parse("Theek hai")
        assertTrue(theekHai.handled)
        assertEquals(MessageCategory.GENERAL_CONVERSATION, theekHai.category)

        val responseEngine = MyraResponseEngine()
        val context = ConversationContext()
        val resp1 = responseEngine.generateResponse(kaiseHo.category, kaiseHo.intent!!, context)
        assertTrue(resp1.text.contains("ठीक हूँ"))

        val resp2 = responseEngine.generateResponse(theekHai.category, theekHai.intent!!, context)
        assertTrue(resp2.text.contains("बताइए आगे") || resp2.text.contains("जी"))
    }

    // Test 20: Context question ("Mene kya bola") -> MessageCategory.CONTEXT_QUESTION
    @Test
    fun test20_ContextQuestionCategory() {
        val res = parser.parse("mene kya bola")
        assertTrue(res.handled)
        assertEquals(MessageCategory.CONTEXT_QUESTION, res.category)
        assertEquals(IntentType.RECALL_RECENT_REQUESTS, res.intent?.type)
    }

    // Test 21: Execution status ("1 wale me tumne kya kiya", "Kyu nahi hua")
    @Test
    fun test21_ExecutionStatusCategory() {
        val res1 = parser.parse("1 wale me tumne kya kiya")
        assertTrue(res1.handled)
        assertEquals(MessageCategory.EXECUTION_STATUS, res1.category)
        assertEquals(1, res1.intent?.targetIndex)

        val res2 = parser.parse("kyu nahi hua")
        assertTrue(res2.handled)
        assertEquals(MessageCategory.EXECUTION_STATUS, res2.category)
        assertEquals(IntentType.EXPLAIN_LAST_FAILURE, res2.intent?.type)
    }

    // Test 22: Failure feedback ("Nahi hua") -> MessageCategory.FAILURE_FEEDBACK
    @Test
    fun test22_FailureFeedbackCategory() {
        val res = parser.parse("nahi hua")
        assertTrue(res.handled)
        assertEquals(MessageCategory.FAILURE_FEEDBACK, res.category)
        assertEquals(IntentType.REPORT_FAILURE, res.intent?.type)

        val responseEngine = MyraResponseEngine()
        val context = ConversationContext()
        val response = responseEngine.generateResponse(res.category, res.intent!!, context)
        assertTrue(response.text.contains("सफल नहीं हुई") || response.text.contains("असफलता"))
        assertNotNull(response.pendingConfirmation)
        assertEquals(PendingConfirmationType.RETRY_PREVIOUS, response.pendingConfirmation?.type)
    }

    // Test 23: Retry request ("Dobara karo", "Phir se karo")
    @Test
    fun test23_RetryRequestCategory() {
        val res = parser.parse("dobara karo")
        assertTrue(res.handled)
        assertEquals(MessageCategory.RETRY_REQUEST, res.category)
        assertEquals(IntentType.RETRY_LAST_REQUEST, res.intent?.type)

        val res2 = parser.parse("phir se karo")
        assertTrue(res2.handled)
        assertEquals(MessageCategory.RETRY_REQUEST, res2.category)
    }

    // Test 24: Cancel request ("Ruk jao", "Cancel", "Rok do")
    @Test
    fun test24_CancelRequestCategory() {
        val res = parser.parse("ruk jao")
        assertTrue(res.handled)
        assertEquals(MessageCategory.CANCEL_REQUEST, res.category)
        assertEquals(IntentType.CANCEL_REQUEST, res.intent?.type)

        val res2 = parser.parse("cancel")
        assertTrue(res2.handled)
        assertEquals(MessageCategory.CANCEL_REQUEST, res2.category)
    }

    // Test 25: Confirmation and Denial ("Haan", "Nahi")
    @Test
    fun test25_ConfirmationAndDenialCategories() {
        val haan = parser.parse("haan")
        assertTrue(haan.handled)
        assertEquals(MessageCategory.CONFIRMATION, haan.category)

        val nahi = parser.parse("nahi")
        assertTrue(nahi.handled)
        assertEquals(MessageCategory.DENIAL, nahi.category)
    }

    // Test 26: Correction ("Nahi mera matlab kuch aur tha", "ye nahi bola tha")
    @Test
    fun test26_CorrectionCategory() {
        val res = parser.parse("nahi mera matlab ye nahi tha")
        assertTrue(res.handled)
        assertEquals(MessageCategory.CORRECTION, res.category)
        assertEquals(IntentType.CORRECT_PREVIOUS_RESULT, res.intent?.type)
    }

    // Test 27: Meta instruction ("Me chahta hu vo tum khud karo")
    @Test
    fun test27_MetaInstructionCategory() {
        val res = parser.parse("me chahta hu vo sare steps youtube par ho or vo tum khud kro")
        assertTrue(res.handled)
        assertEquals(MessageCategory.META_INSTRUCTION, res.category)
        assertEquals(IntentType.META_INSTRUCTION, res.intent?.type)
    }

    // Test 28: Memory query ("Tumhe kya yaad hai")
    @Test
    fun test28_MemoryQueryCategory() {
        val res = parser.parse("tumhe kya yaad hai")
        assertTrue(res.handled)
        assertEquals(MessageCategory.MEMORY_QUERY, res.category)
        assertEquals(IntentType.MEMORY_QUERY, res.intent?.type)
    }

    // Test 29: New command ("YouTube par desi gamer ka video play kro")
    @Test
    fun test29_NewCommandCategory() {
        val res = parser.parse("youtube par desi gamer ka video play kro")
        assertTrue(res.handled)
        assertEquals(MessageCategory.NEW_COMMAND, res.category)
        assertEquals(IntentType.SEARCH_AND_PLAY, res.intent?.type)
        assertEquals("desi gamer", res.intent?.query?.lowercase()?.trim())
    }

    // Test 30: Truthful Response Rule in MyraResponseEngine
    // NEVER says "काम पूरा हुआ" unless actual verification is true!
    @Test
    fun test30_TruthfulResponseRule() {
        val responseEngine = MyraResponseEngine()
        val context = ConversationContext()

        // Unverified execution result (e.g. search opened, but video playback was not verified)
        val unverifiedResult = DetailedExecutionResult(
            requestId = 101L,
            userCommand = "youtube par desi gamer play karo",
            intent = ParsedIntent(type = IntentType.SEARCH_AND_PLAY, app = "youtube", query = "desi gamer"),
            finalStatus = ExecutionStatus.PARTIAL_SUCCESS,
            verificationStatus = false,
            summary = "YouTube पर 'desi gamer' खोजा गया, लेकिन वीडियो चलने की पुष्टि नहीं हो सकी।"
        )

        val unverifiedResponse = responseEngine.generateResponse(
            category = MessageCategory.NEW_COMMAND,
            intent = unverifiedResult.intent,
            context = context,
            executionResult = unverifiedResult
        )

        assertFalse("Must NEVER claim 'काम पूरा हुआ' when unverified", unverifiedResponse.text.contains("काम पूरा हुआ"))
        assertFalse("Must NEVER claim 'सफलतापूर्वक पूरा' when unverified", unverifiedResponse.text.contains("सफलतापूर्वक पूरा"))
        assertTrue("Must truthfully mention search or partial verification",
            unverifiedResponse.text.contains("खोजा गया") || unverifiedResponse.text.contains("पुष्टि नहीं"))

        // Verified execution result
        val verifiedResult = DetailedExecutionResult(
            requestId = 102L,
            userCommand = "youtube par desi gamer play karo",
            intent = ParsedIntent(type = IntentType.SEARCH_AND_PLAY, app = "youtube", query = "desi gamer"),
            finalStatus = ExecutionStatus.SUCCESS,
            verificationStatus = true,
            summary = "YouTube पर 'desi gamer' का वीडियो चल रहा है।"
        )

        val verifiedResponse = responseEngine.generateResponse(
            category = MessageCategory.NEW_COMMAND,
            intent = verifiedResult.intent,
            context = context,
            executionResult = verifiedResult
        )

        assertTrue("Verified response must confirm playback", verifiedResponse.text.contains("चल रहा है") || verifiedResponse.text.contains("पूरा हुआ"))
    }
}
