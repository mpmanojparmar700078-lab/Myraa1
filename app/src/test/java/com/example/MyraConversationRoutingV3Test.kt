package com.example

import com.example.brain.ConversationContext
import com.example.brain.IntentClassifier
import com.example.brain.MyraResponseEngine
import com.example.brain.RecentRequestContext
import com.example.brain.TextNormalizer
import com.example.models.ApiKeyStatus
import com.example.models.IntentType
import com.example.models.MessageCategory
import com.example.services.LocalCommandParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MyraConversationRoutingV3Test {

    private val parser = LocalCommandParser()
    private val responseEngine = MyraResponseEngine(apiKeyStatusProvider = { ApiKeyStatus.NOT_CONFIGURED })

    private val genericCapabilityText = "मैं आपकी डिवाइस पर ऐप्स खोलने, सर्च करने और टास्क ऑटोमेट करने में मदद कर सकती हूँ"

    // ------------------------------------------------------------------------
    // Scenario 1: "hii"
    // ------------------------------------------------------------------------
    @Test
    fun testScenario1_Hii() {
        val raw = "hii"
        val norm = TextNormalizer.normalize(raw)
        val classification = IntentClassifier.classify(norm, raw)
        assertEquals(IntentType.GREETING, classification.intent)

        val localResult = parser.parse(raw)
        assertTrue(localResult.handled)
        assertEquals(IntentType.GREETING, localResult.intent?.type)
        assertEquals(MessageCategory.GREETING, localResult.category)
    }

    // ------------------------------------------------------------------------
    // Scenario 2: "api key lgi ya nhi"
    // ------------------------------------------------------------------------
    @Test
    fun testScenario2_ApiKeyStatusQuery() {
        val raw = "api key lgi ya nhi"
        val norm = TextNormalizer.normalize(raw)
        val classification = IntentClassifier.classify(norm, raw)
        assertEquals(IntentType.API_KEY_STATUS_QUERY, classification.intent)
        assertEquals(MessageCategory.API_KEY_STATUS_QUERY, classification.category)

        val localResult = parser.parse(raw)
        assertTrue(localResult.handled)
        assertEquals(IntentType.API_KEY_STATUS_QUERY, localResult.intent?.type)

        val response = responseEngine.generateApiKeyStatusResponse(ApiKeyStatus.NOT_CONFIGURED)
        assertTrue(response.contains("नहीं") && response.contains("API key"))
        assertFalse("Must NEVER return generic capability text", response.contains(genericCapabilityText))

        val configuredResponse = responseEngine.generateApiKeyStatusResponse(ApiKeyStatus.CONFIGURED)
        assertTrue(configuredResponse.contains("हाँ") && configuredResponse.contains("API key"))
    }

    // ------------------------------------------------------------------------
    // Scenario 3: "mene kya pucha"
    // ------------------------------------------------------------------------
    @Test
    fun testScenario3_MeneKyaPucha() {
        val raw = "mene kya pucha"
        val norm = TextNormalizer.normalize(raw)
        val classification = IntentClassifier.classify(norm, raw)
        assertEquals(IntentType.CONTEXT_QUERY, classification.intent)
        assertEquals(MessageCategory.CONTEXT_QUESTION, classification.category)

        val localResult = parser.parse(raw)
        assertTrue(localResult.handled)
        assertEquals(IntentType.CONTEXT_QUERY, localResult.intent?.type)
    }

    // ------------------------------------------------------------------------
    // Scenario 4: "mene kya bola tha"
    // ------------------------------------------------------------------------
    @Test
    fun testScenario4_MeneKyaBolaTha() {
        val raw = "mene kya bola tha"
        val norm = TextNormalizer.normalize(raw)
        val classification = IntentClassifier.classify(norm, raw)
        assertEquals(IntentType.RECALL_REQUEST, classification.intent)

        val localResult = parser.parse(raw)
        assertTrue(localResult.handled)
        assertTrue(
            localResult.intent?.type == IntentType.RECALL_RECENT_REQUESTS ||
            localResult.intent?.type == IntentType.RECALL_REQUEST
        )
    }

    // ------------------------------------------------------------------------
    // Scenario 5: "kyo"
    // ------------------------------------------------------------------------
    @Test
    fun testScenario5_Kyo() {
        val raw = "kyo"
        val norm = TextNormalizer.normalize(raw)
        val classification = IntentClassifier.classify(norm, raw)
        assertEquals(IntentType.WHY_QUERY, classification.intent)
        assertEquals(MessageCategory.WHY_QUESTION, classification.category)

        val localResult = parser.parse(raw)
        assertTrue(localResult.handled)
        assertEquals(IntentType.WHY_QUERY, localResult.intent?.type)
    }

    // ------------------------------------------------------------------------
    // Scenario 6: "tumne kya kiya"
    // ------------------------------------------------------------------------
    @Test
    fun testScenario6_TumneKyaKiya() {
        val raw = "tumne kya kiya"
        val norm = TextNormalizer.normalize(raw)
        val classification = IntentClassifier.classify(norm, raw)
        assertEquals(IntentType.EXECUTION_STATUS_QUERY, classification.intent)

        val localResult = parser.parse(raw)
        assertTrue(localResult.handled)
        assertEquals(IntentType.REPORT_LAST_EXECUTION, localResult.intent?.type)
    }

    // ------------------------------------------------------------------------
    // Scenario 7: "1 wale me tumne kya kiya"
    // ------------------------------------------------------------------------
    @Test
    fun testScenario7_ItemIndexQuery() {
        val raw = "1 wale me tumne kya kiya"
        val norm = TextNormalizer.normalize(raw)
        val classification = IntentClassifier.classify(norm, raw)
        assertEquals(IntentType.EXECUTION_STATUS_QUERY, classification.intent)
        assertEquals(1, classification.contextReference?.index)

        val localResult = parser.parse(raw)
        assertTrue(localResult.handled)
        assertEquals(IntentType.REPORT_LAST_EXECUTION, localResult.intent?.type)
        assertEquals(1, localResult.intent?.targetIndex)
    }

    // ------------------------------------------------------------------------
    // Scenario 8: "dobara karo"
    // ------------------------------------------------------------------------
    @Test
    fun testScenario8_DobaraKaro() {
        val raw = "dobara karo"
        val norm = TextNormalizer.normalize(raw)
        val classification = IntentClassifier.classify(norm, raw)
        assertEquals(IntentType.RETRY_REQUEST, classification.intent)

        val localResult = parser.parse(raw)
        assertTrue(localResult.handled)
        assertEquals(IntentType.RETRY_LAST_REQUEST, localResult.intent?.type)
    }

    // ------------------------------------------------------------------------
    // Scenario 9: "me kya bol rha hu or tum kya bol rhe ho"
    // ------------------------------------------------------------------------
    @Test
    fun testScenario9_MetaConversation() {
        val raw = "me kya bol rha hu or tum kya bol rhe ho"
        val norm = TextNormalizer.normalize(raw)
        val classification = IntentClassifier.classify(norm, raw)
        assertEquals(IntentType.META_CONVERSATION, classification.intent)
        assertEquals(MessageCategory.META_CONVERSATION, classification.category)

        val localResult = parser.parse(raw)
        assertTrue(localResult.handled)
        assertEquals(IntentType.META_CONVERSATION, localResult.intent?.type)

        val context = ConversationContext()
        context.recordUserMessage("api key lgi ya nhi")
        context.recordAssistantResponse("नहीं, API key configured नहीं है।", intent = IntentType.API_KEY_STATUS_QUERY)
        context.recordUserMessage(raw)

        val response = responseEngine.generateMetaConversationResponse(context)
        assertFalse("Must NEVER return generic capabilities", response.contains(genericCapabilityText))
        assertTrue(response.contains("बातचीत") || response.contains("जवाब") || response.contains("सवाल"))
    }

    // ------------------------------------------------------------------------
    // Scenario 10: "nahi hua or youtube par video play bhi nhi hua tha"
    // ------------------------------------------------------------------------
    @Test
    fun testScenario10_FailureFeedback() {
        val raw = "nahi hua or youtube par video play bhi nhi hua tha"
        val norm = TextNormalizer.normalize(raw)
        val classification = IntentClassifier.classify(norm, raw)
        assertEquals(IntentType.FAILURE_FEEDBACK, classification.intent)
        assertEquals(MessageCategory.FAILURE_FEEDBACK, classification.category)

        val localResult = parser.parse(raw)
        assertTrue(localResult.handled)
        assertEquals(MessageCategory.FAILURE_FEEDBACK, localResult.category)
    }

    // ------------------------------------------------------------------------
    // SEQUENCE A (The exact sequence from problem description):
    // User: hii
    // Myra: नमस्ते! मैं यहाँ हूँ। बताइए, क्या करना है?
    // User: api key lgi ya nhi
    // Myra: नहीं, API key configured नहीं है।
    // User: mene kya pucha
    // Myra: आपने अभी पूछा था: "api key lgi ya nhi"
    // User: mene kya bola tha
    // Myra: आपने अभी बोला था: "mene kya pucha"
    // User: kyo
    // Myra: क्योंकि सेटिंग्स में कोई Gemini API key दर्ज नहीं की गई है...
    // ------------------------------------------------------------------------
    @Test
    fun testSequenceA_ExactReproductionAndResolution() {
        val context = ConversationContext()
        val recentRequestContext = RecentRequestContext()

        // 1. Turn 1: "hii"
        val t1Input = "hii"
        context.recordUserMessage(t1Input)
        val t1Reply = "नमस्ते! मैं यहाँ हूँ। बताइए, क्या करना है?"
        context.recordAssistantResponse(t1Reply, intent = IntentType.GREETING)

        // 2. Turn 2: "api key lgi ya nhi"
        val t2Input = "api key lgi ya nhi"
        val t2Norm = TextNormalizer.normalize(t2Input)
        val t2Class = IntentClassifier.classify(t2Norm, t2Input)
        assertEquals(IntentType.API_KEY_STATUS_QUERY, t2Class.intent)

        context.recordUserMessage(t2Input)
        val t2Reply = responseEngine.generateApiKeyStatusResponse(ApiKeyStatus.NOT_CONFIGURED)
        assertFalse("Must not be generic", t2Reply.contains(genericCapabilityText))
        assertTrue("Must report API key status", t2Reply.contains("नहीं, API key configured नहीं है।"))
        context.recordAssistantResponse(t2Reply, intent = IntentType.API_KEY_STATUS_QUERY)

        // 3. Turn 3: "mene kya pucha"
        val t3Input = "mene kya pucha"
        val t3Norm = TextNormalizer.normalize(t3Input)
        val t3Class = IntentClassifier.classify(t3Norm, t3Input)
        assertEquals(IntentType.CONTEXT_QUERY, t3Class.intent)

        context.recordUserMessage(t3Input)
        // Must recall the previous user question ("api key lgi ya nhi"), NOT "हाल ही में कोई कमांड रिकॉर्ड नहीं हुआ है।"
        val t3Reply = context.formatPreviousUserQuestionResponse()
        assertTrue("Must recall 'api key lgi ya nhi': $t3Reply", t3Reply.contains("api key lgi ya nhi"))
        assertFalse("Must NOT say no command recorded", t3Reply.contains("कोई कमांड रिकॉर्ड नहीं"))
        context.recordAssistantResponse(t3Reply, intent = IntentType.CONTEXT_QUERY)

        // 4. Turn 4: "mene kya bola tha"
        val t4Input = "mene kya bola tha"
        val t4Norm = TextNormalizer.normalize(t4Input)
        val t4Class = IntentClassifier.classify(t4Norm, t4Input)
        assertEquals(IntentType.RECALL_REQUEST, t4Class.intent)

        context.recordUserMessage(t4Input)
        val t4Reply = context.formatPreviousUserMessageResponse()
        assertTrue("Must recall 'mene kya pucha': $t4Reply", t4Reply.contains("mene kya pucha"))
        assertFalse("Must NOT say no command recorded", t4Reply.contains("कोई कमांड रिकॉर्ड नहीं"))
        context.recordAssistantResponse(t4Reply, intent = IntentType.RECALL_REQUEST)

        // 5. Turn 5: "kyo"
        val t5Input = "kyo"
        val t5Norm = TextNormalizer.normalize(t5Input)
        val t5Class = IntentClassifier.classify(t5Norm, t5Input)
        assertEquals(IntentType.WHY_QUERY, t5Class.intent)

        context.recordUserMessage(t5Input)
        val t5Reply = responseEngine.generateWhyResponse(context, recentRequestContext)
        assertFalse("Must NEVER return generic capability text: $t5Reply", t5Reply.contains(genericCapabilityText))
        assertTrue("Must explain why: $t5Reply", t5Reply.contains("क्योंकि") || t5Reply.contains("API key") || t5Reply.contains("दर्ज"))
    }

    // ------------------------------------------------------------------------
    // SEQUENCE B: Command execution and recall
    // ------------------------------------------------------------------------
    @Test
    fun testSequenceB_CommandExecutionAndRecall() {
        val context = ConversationContext()
        val recentRequestContext = RecentRequestContext()

        // 1. "youtube par desi gamer ka video play kro"
        val cmdInput = "youtube par desi gamer ka video play kro"
        context.recordUserMessage(cmdInput)
        recentRequestContext.recordNewRequest(
            requestId = 1L,
            rawCommand = cmdInput,
            normalizedCommand = TextNormalizer.normalize(cmdInput),
            intent = com.example.models.ParsedIntent(type = IntentType.SEARCH_AND_PLAY, app = "YouTube", query = "desi gamer"),
            targetApp = "YouTube",
            query = "desi gamer"
        )
        recentRequestContext.updateRequestState(
            requestId = 1L,
            state = com.example.models.RequestState.SUCCESS,
            result = com.example.models.RequestResult(
                requestId = 1L,
                state = com.example.models.RequestState.SUCCESS,
                summary = "YouTube पर Desi Gamer का वीडियो चलने की पुष्टि हो गई।",
                isVerified = true
            )
        )
        context.recordAssistantResponse("YouTube पर Desi Gamer खोजा जा रहा है...", intent = IntentType.SEARCH_AND_PLAY, relatedRequestId = 1L)

        // 2. "tumne kya kiya"
        val statusInput = "tumne kya kiya"
        val statusClass = IntentClassifier.classify(TextNormalizer.normalize(statusInput), statusInput)
        assertEquals(IntentType.EXECUTION_STATUS_QUERY, statusClass.intent)

        context.recordUserMessage(statusInput)
        val statusReply = recentRequestContext.formatLastExecutionReport()
        assertTrue("Report must mention YouTube or desi gamer", statusReply.contains("YouTube", ignoreCase = true) || statusReply.contains("desi gamer", ignoreCase = true))

        // 3. "mene kya bola tha"
        val recallInput = "mene kya bola tha"
        context.recordUserMessage(recallInput)
        val recallReply = context.formatPreviousUserMessageResponse()
        assertTrue("Must recall 'tumne kya kiya': $recallReply", recallReply.contains("tumne kya kiya"))
    }

    // ------------------------------------------------------------------------
    // SEQUENCE C: API key, Why, Meta-conversation
    // ------------------------------------------------------------------------
    @Test
    fun testSequenceC_ApiKeyWhyMetaConversation() {
        val context = ConversationContext()
        val recentRequestContext = RecentRequestContext()

        // 1. "api key lgi ya nhi"
        val q1 = "api key lgi ya nhi"
        context.recordUserMessage(q1)
        val r1 = responseEngine.generateApiKeyStatusResponse(ApiKeyStatus.NOT_CONFIGURED)
        context.recordAssistantResponse(r1, intent = IntentType.API_KEY_STATUS_QUERY)

        // 2. "kyo"
        val q2 = "kyo"
        context.recordUserMessage(q2)
        val r2 = responseEngine.generateWhyResponse(context, recentRequestContext)
        assertFalse("Must not be generic", r2.contains(genericCapabilityText))
        assertTrue("Must explain why", r2.contains("क्योंकि"))
        context.recordAssistantResponse(r2, intent = IntentType.WHY_QUERY)

        // 3. "me kya bol rha hu or tum kya bol rhe ho"
        val q3 = "me kya bol rha hu or tum kya bol rhe ho"
        context.recordUserMessage(q3)
        val r3 = responseEngine.generateMetaConversationResponse(context)
        assertFalse("Must not be generic", r3.contains(genericCapabilityText))
        assertTrue(r3.contains("बातचीत") || r3.contains("सवाल") || r3.contains("जवाब"))
    }

    // ------------------------------------------------------------------------
    // SEQUENCE D: Meta-instruction followed by queries
    // ------------------------------------------------------------------------
    @Test
    fun testSequenceD_MetaInstructionFollowedByQueries() {
        val context = ConversationContext()
        val recentRequestContext = RecentRequestContext()

        // 1. "me chahta hu vo sare steps youtube par ho or vo tum khud kro"
        val metaInput = "me chahta hu vo sare steps youtube par ho or vo tum khud kro"
        val metaNorm = TextNormalizer.normalize(metaInput)
        val metaClass = IntentClassifier.classify(metaNorm, metaInput)
        assertEquals(IntentType.EXECUTION_PREFERENCE, metaClass.intent)

        context.recordUserMessage(metaInput)
        val metaReply = "समझ गई। अब से मैं उपलब्ध सभी स्टेप्स अपने आप (automatically) पूरे करने की कोशिश करूँगी।"
        context.recordAssistantResponse(metaReply, intent = IntentType.META_INSTRUCTION)

        // 2. "mene kya bola tha"
        val recallInput = "mene kya bola tha"
        context.recordUserMessage(recallInput)
        val recallReply = context.formatPreviousUserMessageResponse()
        assertTrue("Must recall meta instruction text", recallReply.contains("sare steps") || recallReply.contains("khud kro") || recallReply.contains("me chahta hu"))

        // 3. "tumne kya kiya"
        val statusInput = "tumne kya kiya"
        val statusNorm = TextNormalizer.normalize(statusInput)
        val statusClass = IntentClassifier.classify(statusNorm, statusInput)
        assertEquals(IntentType.EXECUTION_STATUS_QUERY, statusClass.intent)
    }
}
