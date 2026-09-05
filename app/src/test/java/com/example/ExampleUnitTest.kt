package com.example

import com.example.actions.ActionManager
import com.example.models.IntentType
import com.example.models.ParsedIntent
import com.example.models.ScreenState
import com.example.models.UIElement
import com.example.models.VideoCandidateType
import com.example.platform.android.AppLauncher
import com.example.services.LocalCommandParser
import com.example.services.YouTubeResultAnalyzer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testYouTubeSearchAndPlayParsing() {
        val parser = LocalCommandParser()

        // 1. YouTube Search & Play complex Hindi commands
        val res1 = parser.parse("YouTube पर VK Bhuriya का song search करो और relevant video play करो")
        assertTrue("Should be recognized locally", res1.recognized)
        assertEquals("Intent must be YOUTUBE_SEARCH_AND_PLAY", IntentType.YOUTUBE_SEARCH_AND_PLAY, res1.intent)
        assertTrue("Query should contain VK Bhuriya song", res1.parsedIntent?.query?.contains("VK Bhuriya") == true)

        // 2. Play songs command
        val res2 = parser.parse("YouTube पर Arijit Singh ke gaane chalao")
        assertTrue(res2.recognized)
        assertEquals(IntentType.YOUTUBE_SEARCH_AND_PLAY, res2.intent)
        assertTrue(res2.parsedIntent?.query?.contains("Arijit Singh") == true)

        // 3. Search only command should NOT be search and play
        val res3 = parser.parse("YouTube par gamini search kro")
        assertTrue(res3.recognized)
        assertEquals(IntentType.YOUTUBE_SEARCH, res3.intent)

        // 4. Multi-step Open YouTube and play
        val res4 = parser.parse("Open YouTube and play Hanuman Chalisa")
        assertTrue(res4.recognized)
        assertEquals(IntentType.YOUTUBE_SEARCH_AND_PLAY, res4.intent)
        assertTrue(res4.parsedIntent?.query?.contains("Hanuman Chalisa") == true)

        // 5. Hindi multi-step Open YouTube and play
        val res5 = parser.parse("YouTube खोलो और अरिजीत सिंह के गाने चलाओ")
        assertTrue(res5.recognized)
        assertEquals(IntentType.YOUTUBE_SEARCH_AND_PLAY, res5.intent)

        // 6. Suffix play on YouTube
        val res6 = parser.parse("Hanuman Chalisa chalao youtube par")
        assertTrue(res6.recognized)
        assertEquals(IntentType.YOUTUBE_SEARCH_AND_PLAY, res6.intent)
        assertTrue(res6.parsedIntent?.query?.contains("Hanuman Chalisa") == true)
    }

    @Test
    fun testYouTubeResultAnalyzerFilteringAndSelection() {
        val testElements = listOf(
            UIElement(
                text = "Sponsored • Best Online Deals",
                contentDescription = "Ad • Buy electronics now",
                isClickable = true
            ),
            UIElement(
                text = "#Shorts VK Bhuriya viral dance",
                contentDescription = "Shorts • 15 seconds",
                isClickable = true
            ),
            UIElement(
                text = "VK Bhuriya Official Channel",
                contentDescription = "VK Bhuriya • 1.2M subscribers • Subscribe",
                isClickable = true
            ),
            UIElement(
                text = "VK Bhuriya Top Hits 2024 (Full Album Audio)",
                contentDescription = "VK Bhuriya Top Hits 2024 (Full Album Audio) by Desi Beats 45 minutes 2.1M views",
                isClickable = true
            ),
            UIElement(
                text = "Random Cooking Recipe",
                contentDescription = "How to make pasta in 5 minutes",
                isClickable = true
            )
        )

        val screenState = ScreenState(elements = testElements)
        val result = YouTubeResultAnalyzer.analyzeSearchResults(screenState, "VK Bhuriya song")

        assertNotNull("Should find a selected video", result.selected)
        assertEquals(VideoCandidateType.VIDEO, result.selected?.type)
        assertTrue(
            "Selected video should be the VK Bhuriya full album video",
            result.selected?.title?.contains("VK Bhuriya Top Hits") == true
        )
        assertTrue(
            "Relevance score should be high",
            (result.selected?.relevanceScore ?: 0f) > 0.5f
        )
    }

    @Test
    fun test_TEST_1_EntityContext_PronounResolution() {
        val parser = LocalCommandParser()
        com.example.models.ConversationContextTracker.clear()

        // User: "Free Fire Craftland ke bare mein batao"
        parser.parse("Free Fire Craftland ke bare mein batao")

        // Then: "iski official website kholo"
        val result = parser.parse("iski official website kholo")

        // Expected: Resolve "iski" to "Free Fire Craftland".
        // Do NOT search for: "iski official website"
        // Expected intent: WEB_SEARCH or OPEN_URL
        // Gemini: 0 (recognized locally)
        assertTrue("TEST 1 should be recognized locally (Gemini: 0)", result.recognized)
        assertTrue("TEST 1 intent must be WEB_SEARCH or OPEN_URL",
            result.intent == IntentType.WEB_SEARCH || result.intent == IntentType.OPEN_URL)
        val query = result.parsedIntent?.query ?: ""
        assertTrue("Query must contain 'Free Fire Craftland', was: '$query'", query.contains("Free Fire Craftland"))
        assertTrue("Query must contain 'official website', was: '$query'", query.contains("official website"))
        assertFalse("Query must NOT contain 'iski'", query.contains("iski", ignoreCase = true))
        assertNotEquals("Must NOT be classified as OPEN_APP", IntentType.OPEN_APP, result.intent)
    }

    @Test
    fun test_TEST_2_FollowUp_YouTubePlay_LocalResolution() {
        val parser = LocalCommandParser()
        com.example.models.ConversationContextTracker.clear()

        // User: "YouTube par Free Fire search kro"
        val searchResult = parser.parse("YouTube par Free Fire search kro")
        assertTrue(searchResult.recognized)
        assertEquals(IntentType.YOUTUBE_SEARCH, searchResult.intent)
        assertEquals("Free Fire", searchResult.parsedIntent?.query)

        // Then: "ab play kro"
        val playResult = parser.parse("ab play kro")

        // Expected:
        // Platform = YouTube
        // Query = Free Fire
        // Action = PLAY
        // Do NOT send "ab play kro" to Google. Do NOT call Gemini when local context is sufficient.
        assertTrue("TEST 2 should be recognized locally (Gemini: 0)", playResult.recognized)
        assertEquals("Intent must be YOUTUBE_SEARCH_AND_PLAY", IntentType.YOUTUBE_SEARCH_AND_PLAY, playResult.intent)
        assertEquals("Free Fire", playResult.parsedIntent?.query)
        val ctx = com.example.models.ConversationContextTracker.getActiveContext()
        assertEquals("YOUTUBE", ctx?.lastPlatform)
        assertEquals("Free Fire", ctx?.lastQuery)
        assertEquals("PLAY", ctx?.lastAction)
    }

    @Test
    fun test_TEST_3_WebsiteContextQuery_LocalResponse() {
        val parser = LocalCommandParser()
        com.example.models.ConversationContextTracker.clear()

        // User: "Free Fire Craftland ki official website kholo"
        val websiteCmdResult = parser.parse("Free Fire Craftland ki official website kholo")
        assertTrue(websiteCmdResult.recognized)
        assertEquals(IntentType.WEB_SEARCH, websiteCmdResult.intent)

        // Then: "maine kiski website open karne bola tha?"
        val queryResult = parser.parse("maine kiski website open karne bola tha?")

        // Expected local response: "आपने Free Fire Craftland की official website खोलने को कहा था।"
        // Gemini: 0
        assertTrue("TEST 3 should be recognized locally (Gemini: 0)", queryResult.recognized)
        assertEquals(IntentType.GENERAL_CHAT, queryResult.intent)
        val responseText = queryResult.parsedIntent?.responseText ?: ""
        assertEquals("आपने Free Fire Craftland की official website खोलने को कहा था।", responseText)
    }

    @Test
    fun test_ContextSafety_AmbiguousEntities() {
        val parser = LocalCommandParser()
        com.example.models.ConversationContextTracker.clear()

        // User: "Free Fire aur Minecraft ke bare mein batao"
        parser.parse("Free Fire aur Minecraft ke bare mein batao")

        // Then: "iski website kholo"
        val result = parser.parse("iski website kholo")

        // Safety expectation: Ask which one the user means (do not guess)
        assertTrue("Should be handled locally without guessing", result.recognized)
        assertEquals(IntentType.GENERAL_CHAT, result.intent)
        val responseText = result.parsedIntent?.responseText ?: ""
        assertTrue("Should mention Free Fire", responseText.contains("Free Fire"))
        assertTrue("Should mention Minecraft", responseText.contains("Minecraft"))
        assertTrue("Should ask clarification", responseText.contains("किसकी") || responseText.contains("website"))
    }

    @Test
    fun test_ContextTimeout_Expiration() {
        val parser = LocalCommandParser()
        com.example.models.ConversationContextTracker.clear()

        // Set context
        parser.parse("YouTube par Free Fire search kro")
        assertNotNull(com.example.models.ConversationContextTracker.getActiveContext())

        // Simulate context timeout by setting CONTEXT_TIMEOUT_MS to 0
        val originalTimeout = com.example.models.ConversationContextTracker.CONTEXT_TIMEOUT_MS
        try {
            com.example.models.ConversationContextTracker.CONTEXT_TIMEOUT_MS = -1L // Expired immediately
            assertNull("Context should be expired", com.example.models.ConversationContextTracker.getActiveContext())

            val followUpResult = parser.parse("ab play kro")
            val reply = followUpResult.parsedIntent?.responseText ?: ""
            assertTrue("Should ask which video when context expired", reply.contains("किस वीडियो"))
        } finally {
            com.example.models.ConversationContextTracker.CONTEXT_TIMEOUT_MS = originalTimeout
        }
    }

    @Test
    fun test_MultiAction_LocalParsing() {
        val parser = LocalCommandParser()
        
        // Command: "Chrome kholo aur Google par Free Fire search kro"
        val result = parser.parse("Chrome kholo aur Google par Free Fire search kro")
        assertTrue("Should be recognized as local multi-step", result.recognized)
        assertEquals(IntentType.MULTI_ACTION, result.intent)
        val actions = result.parsedIntent?.actions
        assertNotNull("Actions list should not be null", actions)
        assertEquals("Should have exactly 2 actionable tasks", 2, actions?.size)
        assertEquals(IntentType.OPEN_APP, actions?.get(0)?.type)
        assertEquals("Chrome", actions?.get(0)?.app)
        assertEquals(IntentType.WEB_SEARCH, actions?.get(1)?.type)
        assertEquals("Free Fire", actions?.get(1)?.query)
    }

    @Test
    fun test_MultiAction_SequentialExecution_With5SecDelay() = kotlinx.coroutines.runBlocking {
        val mockContext = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val shadowPm = org.robolectric.Shadows.shadowOf(mockContext.packageManager)
        
        // Register Chrome in ShadowPackageManager
        val chromeResolve = android.content.pm.ResolveInfo().apply {
            activityInfo = android.content.pm.ActivityInfo().apply {
                packageName = "com.android.chrome"
                name = "com.android.chrome.Main"
                applicationInfo = android.content.pm.ApplicationInfo().apply {
                    packageName = "com.android.chrome"
                    flags = 0
                }
            }
        }
        val launchIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        shadowPm.addResolveInfoForIntent(launchIntent, chromeResolve)

        val appLauncher = AppLauncher(mockContext)
        val actionManager = ActionManager(appLauncher = appLauncher)
        
        val actions = listOf(
            ParsedIntent(type = IntentType.OPEN_APP, app = "Chrome"),
            ParsedIntent(type = IntentType.WEB_SEARCH, query = "Free Fire")
        )

        val startTime = System.currentTimeMillis()
        val result = actionManager.executeSequence(actions, mockContext)
        val elapsed = System.currentTimeMillis() - startTime

        assertTrue("Execution should be successful", result.success)
        assertEquals(2, result.stepResults?.size)
        assertTrue("Sequential execution must wait ~5000ms between action 1 and action 2 (elapsed: ${elapsed}ms)", elapsed >= 4900L)
    }

    @Test
    fun test_MultiAction_StopsOnFailure() = kotlinx.coroutines.runBlocking {
        val mockContext = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val appLauncher = AppLauncher(mockContext)
        val actionManager = ActionManager(appLauncher = appLauncher)
        
        // Create an intent that will fail (UNKNOWN type has no handler registered)
        val actions = listOf(
            ParsedIntent(type = IntentType.UNKNOWN),
            ParsedIntent(type = IntentType.WEB_SEARCH, query = "Free Fire")
        )

        val result = actionManager.executeSequence(actions, mockContext)
        assertFalse("Sequence should fail on first failed step", result.success)
        assertEquals("Should stop after step 1 failure and not execute step 2", 1, result.stepResults?.size)
    }

    @Test
    fun test_InstalledApps_QueryAndLaunch() {
        val mockContext = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val appLauncher = AppLauncher(mockContext)

        val installed = appLauncher.getInstalledApps()
        assertNotNull("Installed apps list should not be null", installed)

        // Verify dynamic matching helper works without crashing
        val matchResult = appLauncher.launchAppByName("Calculator")
        assertNotNull(matchResult)
    }

    // ==========================================
    // MYRA V3.4 — TESTS 1 TO 8
    // ==========================================

    @Test
    fun test_V34_TEST_1_LocalLaunch_WhatsApp() {
        val parser = LocalCommandParser()
        val result = parser.parse("WhatsApp kholo")

        assertTrue("TEST 1 should be recognized locally (Gemini: 0)", result.recognized)
        assertEquals("Intent must be OPEN_APP", IntentType.OPEN_APP, result.intent)
        assertEquals("WhatsApp", result.parsedIntent?.app)
    }

    @Test
    fun test_V34_TEST_2_LocalLaunch_Instagram() {
        val parser = LocalCommandParser()
        val result = parser.parse("Instagram open karo")

        assertTrue("TEST 2 should be recognized locally (Gemini: 0)", result.recognized)
        assertEquals("Intent must be OPEN_APP", IntentType.OPEN_APP, result.intent)
        assertEquals("Instagram", result.parsedIntent?.app)
    }

    @Test
    fun test_V34_TEST_3_LocalLaunch_Chrome() {
        val parser = LocalCommandParser()
        val result = parser.parse("Chrome launch kro")

        assertTrue("TEST 3 should be recognized locally (Gemini: 0)", result.recognized)
        assertEquals("Intent must be OPEN_APP", IntentType.OPEN_APP, result.intent)
        assertEquals("Chrome", result.parsedIntent?.app)
    }

    @Test
    fun test_V34_TEST_4_UnknownApp_Handling() {
        val mockContext = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val appLauncher = AppLauncher(mockContext)

        // Telegram is not installed in the mock environment
        val result = appLauncher.launchAppByName("Telegram")

        assertFalse("Should return failure for uninstalled app", result.success)
        assertTrue(
            "Response should clearly state app is not installed: '${result.message}'",
            result.message.contains("Telegram") && result.message.contains("installed नहीं है")
        )
    }

    @Test
    fun test_V34_TEST_5_HindiHinglishVariations() {
        val parser = LocalCommandParser()
        val variations = listOf(
            "Calculator kholo",
            "Calculator खोलो",
            "Calculator open karo",
            "Calculator chalao",
            "Calculator launch kro"
        )

        for (cmd in variations) {
            val result = parser.parse(cmd)
            assertTrue("Command '$cmd' should be recognized locally (Gemini: 0)", result.recognized)
            assertEquals("Intent for '$cmd' must be OPEN_APP", IntentType.OPEN_APP, result.intent)
            assertEquals("Calculator", result.parsedIntent?.app)
        }
    }

    @Test
    fun test_V34_TEST_6_DynamicDiscovery_FreeFire() {
        val parser = LocalCommandParser()
        val result = parser.parse("Free Fire kholo")

        assertTrue("TEST 6 should be recognized locally (Gemini: 0)", result.recognized)
        assertEquals("Intent must be OPEN_APP", IntentType.OPEN_APP, result.intent)
        assertEquals("Free Fire", result.parsedIntent?.app)
    }

    @Test
    fun test_V34_TEST_7_ContextBased_AppLaunch() {
        val parser = LocalCommandParser()
        com.example.models.ConversationContextTracker.clear()

        // 1. Topic discovery
        parser.parse("WhatsApp ke bare mein batao")

        // 2. Pronoun command
        val result = parser.parse("isko kholo")

        assertTrue("TEST 7 should be recognized locally (Gemini: 0)", result.recognized)
        assertEquals("Intent must be OPEN_APP", IntentType.OPEN_APP, result.intent)
        assertEquals("WhatsApp", result.parsedIntent?.app)
    }

    @Test
    fun test_V34_TEST_8_AmbiguousAppNames() {
        val mockContext = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val shadowPm = org.robolectric.Shadows.shadowOf(mockContext.packageManager)

        // Register two similar apps in Robolectric shadow package manager
        val app1 = android.content.pm.ResolveInfo().apply {
            activityInfo = android.content.pm.ActivityInfo().apply {
                packageName = "com.dts.freefireth"
                name = "com.dts.freefireth.MainActivity"
                applicationInfo = android.content.pm.ApplicationInfo().apply {
                    flags = 0
                }
            }
        }
        val app2 = android.content.pm.ResolveInfo().apply {
            activityInfo = android.content.pm.ActivityInfo().apply {
                packageName = "com.dts.freefiremax"
                name = "com.dts.freefiremax.MainActivity"
                applicationInfo = android.content.pm.ApplicationInfo().apply {
                    flags = 0
                }
            }
        }
        val launchIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        shadowPm.addResolveInfoForIntent(launchIntent, app1)
        shadowPm.addResolveInfoForIntent(launchIntent, app2)

        val appLauncher = AppLauncher(mockContext)

        // Querying an ambiguous prefix like "com.dts" when multiple match
        val result = appLauncher.launchAppByName("com.dts")
        assertNotNull(result)
        // Disambiguation must not crash or blindly launch
        assertFalse(result.success)
        assertTrue(result.message.contains("कौन सा app खोलना है") || result.message.contains("installed नहीं है"))
    }

    // ==========================================
    // ROOM DATABASE INTERACTION & CONTEXT HISTORY TESTS
    // ==========================================

    @Test
    fun test_RoomDatabase_InteractionHistoryTable_InsertAndQuery() = kotlinx.coroutines.runBlocking {
        val mockContext = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(mockContext, com.example.data.MyraDatabase::class.java).build()
        val historyDao = db.interactionHistoryDao()

        val item1 = com.example.data.InteractionHistoryEntity(
            userQuery = "WhatsApp kholo",
            assistantResponse = "WhatsApp खोला जा रहा है",
            intentType = "OPEN_APP",
            intentTarget = "WhatsApp",
            actionSuccess = true,
            executionSource = "LEVEL_3_ANDROID_API",
            contextEntity = "WhatsApp",
            contextTopic = "AppLaunch",
            timestamp = 1000L
        )
        val item2 = com.example.data.InteractionHistoryEntity(
            userQuery = "YouTube पर Arijit Singh ke gaane chalao",
            assistantResponse = "YouTube पर 'Arijit Singh ke gaane' चलाया जा रहा है",
            intentType = "YOUTUBE_SEARCH_AND_PLAY",
            intentTarget = "Arijit Singh ke gaane",
            actionSuccess = true,
            executionSource = "LEVEL_4_LOCAL_SEARCH",
            contextEntity = "Arijit Singh",
            contextTopic = "Music",
            timestamp = 2000L
        )

        val id1 = historyDao.insertInteraction(item1)
        val id2 = historyDao.insertInteraction(item2)

        assertTrue(id1 > 0)
        assertTrue(id2 > 0)

        val recent = historyDao.getRecentInteractions(10)
        assertEquals(2, recent.size)
        assertEquals("YouTube पर Arijit Singh ke gaane chalao", recent[0].userQuery)
        assertEquals("Arijit Singh", recent[0].contextEntity)

        db.close()
    }

    @Test
    fun test_RoomDatabase_InteractionHistory_SearchAndContextIntegration() = kotlinx.coroutines.runBlocking {
        val mockContext = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(mockContext, com.example.data.MyraDatabase::class.java).build()
        val repo = com.example.data.MemoryRepository(
            messageDao = db.messageDao(),
            preferenceDao = db.preferenceDao(),
            interactionHistoryDao = db.interactionHistoryDao(),
            context = mockContext
        )

        repo.recordInteraction(
            userQuery = "Free Fire kholo",
            assistantResponse = "Free Fire खोला जा रहा है",
            executionSource = "LOCAL_LEVEL_1",
            contextEntity = "Free Fire",
            contextTopic = "Gaming"
        )

        val contextStr = repo.buildMemoryContextString()
        assertTrue("Context string should contain recorded entity", contextStr.contains("Free Fire"))
        assertTrue("Context string should contain active entity tag", contextStr.contains("[Active Entity: Free Fire]"))

        val recent = repo.getRecentInteractions()
        assertEquals(1, recent.size)

        repo.clearInteractionHistory()
        val afterClear = repo.getRecentInteractions()
        assertEquals(0, afterClear.size)

        db.close()
    }

    // ==========================================
    // DEEP ACCESSIBILITY ENGINE TESTS
    // ==========================================

    @Test
    fun test_AccessibilityTreeReader_NullAndEmptySafety() {
        val reader = com.example.services.AccessibilityTreeReader()
        val state = reader.extractScreenState(null)
        assertNotNull(state)
        assertEquals(0, state.elements.size)
        assertNull(state.packageName)

        val clickResult = reader.performClick(null) { true }
        assertFalse(clickResult)

        val setTextResult = reader.performSetText(null, { true }, "test")
        assertFalse(setTextResult)

        val focusResult = reader.performFocus(null) { true }
        assertFalse(focusResult)
    }

    @Test
    fun test_ElementMatcher_MultiSignal_Scoring_And_Confidence() {
        val exactElement = UIElement(
            text = "Search",
            contentDescription = "Search button",
            viewId = "com.google.android.youtube:id/menu_item_search",
            isClickable = true
        )

        val hindiElement = UIElement(
            text = "खोजें",
            contentDescription = "यूट्यूब खोजें",
            isClickable = true
        )

        val fuzzyElement = UIElement(
            text = "Search in all playlists and videos",
            contentDescription = "Search bar",
            isClickable = true
        )

        // Exact match scoring
        val exactScore = com.example.services.ElementMatcher.scoreElement(exactElement, "Search")
        assertTrue("Exact match should have score >= 0.90", exactScore >= 0.90f)

        // Hindi synonym scoring
        val hindiScore = com.example.services.ElementMatcher.scoreElement(hindiElement, "search")
        assertTrue("Hindi synonym should have high score >= 0.75", hindiScore >= 0.75f)

        // Fuzzy match scoring
        val fuzzyScore = com.example.services.ElementMatcher.scoreElement(fuzzyElement, "Search")
        assertTrue("Fuzzy match score should be >= 0.60", fuzzyScore >= 0.60f)

        // Best match evaluation
        val best = com.example.services.ElementMatcher.findBestMatch(
            elements = listOf(exactElement, hindiElement, fuzzyElement),
            target = "Search"
        )
        assertNotNull(best.element)
        assertEquals(exactElement, best.element)
        assertEquals(com.example.models.MatchConfidenceLevel.HIGH, best.confidenceLevel)
        assertFalse(best.isAmbiguous)
    }

    @Test
    fun test_ElementMatcher_AmbiguityDetection() {
        val item1 = UIElement(
            text = "VK Bhuriya - New Song 2024",
            contentDescription = "VK Bhuriya song 1",
            isClickable = true
        )
        val item2 = UIElement(
            text = "VK Bhuriya - Top Hits Song 2024",
            contentDescription = "VK Bhuriya song 2",
            isClickable = true
        )

        val result = com.example.services.ElementMatcher.findBestMatch(
            elements = listOf(item1, item2),
            target = "VK Bhuriya Song 2024"
        )

        assertTrue("Should detect ambiguity when two items are virtually identical in score", result.isAmbiguous)
    }

    @Test
    fun test_ScreenState_SignatureAndChangeDetection() {
        val state1 = ScreenState(
            packageName = "com.google.android.youtube",
            elements = listOf(
                UIElement(text = "Home", isClickable = true),
                UIElement(text = "Subscriptions", isClickable = true)
            )
        )

        val state2 = ScreenState(
            packageName = "com.google.android.youtube",
            elements = listOf(
                UIElement(text = "Search Results", isClickable = false),
                UIElement(text = "Arijit Singh - Kesariya", isClickable = true),
                UIElement(text = "Arijit Singh - Apna Bana Le", isClickable = true)
            )
        )

        assertNotEquals("Different screen states should have different signatures", state1.screenSignature, state2.screenSignature)
        assertTrue("hasChangedSignificantly should return true between home and search results", state1.hasChangedSignificantly(state2))
    }

    @Test
    fun test_ActionChain_PlannerCreation() {
        val planner = com.example.services.ActionChainPlanner()
        val intent = ParsedIntent(
            type = IntentType.YOUTUBE_SEARCH_AND_PLAY,
            query = "Free Fire song",
            responseText = "Playing Free Fire song on YouTube"
        )
        val chain = planner.planFromIntent(intent, "YouTube पर Free Fire song चलाओ")

        assertEquals(3, chain.steps.size)
        assertEquals(com.example.models.ChainActionType.OPEN_APP, chain.steps[0].type)
        assertEquals(com.example.models.ChainActionType.SEARCH, chain.steps[1].type)
        assertEquals(com.example.models.ChainActionType.PLAY_VIDEO, chain.steps[2].type)
        assertEquals(chain.steps[0].id, chain.steps[1].dependsOnStepId)
        assertEquals(chain.steps[1].id, chain.steps[2].dependsOnStepId)
        assertTrue(chain.steps[1].verificationRule is com.example.models.VerificationRule.SearchResultsLoaded)
        assertTrue(chain.steps[2].verificationRule is com.example.models.VerificationRule.VideoPlaying)
    }

    @Test
    fun test_ScreenStateClassifier_Categories() {
        val searchScreen = ScreenState(
            packageName = "com.google.android.youtube",
            elements = listOf(
                UIElement(text = "Search YouTube", viewId = "com.google.android.youtube:id/search_edit_text", isEditable = true)
            )
        )
        assertEquals(com.example.models.ScreenStateCategory.SEARCH_SCREEN, com.example.services.ScreenStateClassifier.classify(searchScreen))

        val videoScreen = ScreenState(
            packageName = "com.google.android.youtube",
            elements = listOf(
                UIElement(text = "Kesariya - Brahmastra", contentDescription = "Pause video", isClickable = true),
                UIElement(text = "100M views", isClickable = false),
                UIElement(text = "Subscribe", isClickable = true)
            )
        )
        assertEquals(com.example.models.ScreenStateCategory.VIDEO_SCREEN, com.example.services.ScreenStateClassifier.classify(videoScreen))

        val dialogScreen = ScreenState(
            packageName = "com.android.permissioncontroller",
            elements = listOf(
                UIElement(text = "Allow Myra to access device location?", isClickable = false),
                UIElement(text = "While using the app", isClickable = true),
                UIElement(text = "Deny", isClickable = true)
            )
        )
        assertEquals(com.example.models.ScreenStateCategory.DIALOG, com.example.services.ScreenStateClassifier.classify(dialogScreen))
    }

    @Test
    fun test_ReferenceResolver_PositionalMatching() {
        assertEquals(0, com.example.services.ReferenceResolver.extractPositionalIndex("पहला वाला चलाओ"))
        assertEquals(0, com.example.services.ReferenceResolver.extractPositionalIndex("1st one"))
        assertEquals(0, com.example.services.ReferenceResolver.extractPositionalIndex("top one"))
        assertEquals(1, com.example.services.ReferenceResolver.extractPositionalIndex("दूसरा वाला"))
        assertEquals(1, com.example.services.ReferenceResolver.extractPositionalIndex("2nd"))
        assertEquals(2, com.example.services.ReferenceResolver.extractPositionalIndex("तीसरा वाला"))
        assertEquals(2, com.example.services.ReferenceResolver.extractPositionalIndex("3rd one"))
        assertEquals(3, com.example.services.ReferenceResolver.extractPositionalIndex("चौथा वाला"))
        assertEquals(4, com.example.services.ReferenceResolver.extractPositionalIndex("नीचे वाला", candidateCount = 5))
    }

    @Test
    fun test_ReferenceResolver_PronounAndAmbiguity() {
        val taskCtx = com.example.models.TaskContext(
            currentEntity = "Free Fire",
            currentQuery = "Free Fire trailer",
            currentApp = "YouTube",
            candidates = listOf(
                com.example.models.CandidateItem(index = 0, title = "Free Fire Official Trailer 1"),
                com.example.models.CandidateItem(index = 1, title = "Free Fire Max Gameplay 2")
            )
        )

        // Pronoun resolution
        val resolvedPronoun = com.example.services.ReferenceResolver.resolve("इसे play करो", taskCtx)
        assertEquals(com.example.models.ReferenceConfidence.HIGH, resolvedPronoun.confidence)
        assertEquals("Free Fire Official Trailer 1", resolvedPronoun.targetQuery)

        // Positional resolution with candidate match
        val resolvedSecond = com.example.services.ReferenceResolver.resolve("दूसरा वाला चलाओ", taskCtx)
        assertEquals(com.example.models.ReferenceConfidence.HIGH, resolvedSecond.confidence)
        assertEquals(1, resolvedSecond.targetIndex)
        assertEquals("Free Fire Max Gameplay 2", resolvedSecond.targetQuery)

        // Ambiguous context detection
        val ambiguousCtx = com.example.models.ConversationContext(
            candidateEntities = listOf("Free Fire", "Minecraft")
        )
        val resolvedAmbiguous = com.example.services.ReferenceResolver.resolve("iski website kholo", null, ambiguousCtx)
        assertEquals(com.example.models.ReferenceConfidence.LOW, resolvedAmbiguous.confidence)
        assertNotNull(resolvedAmbiguous.disambiguationPrompt)
        assertTrue(resolvedAmbiguous.disambiguationPrompt?.contains("Free Fire") == true)
        assertTrue(resolvedAmbiguous.disambiguationPrompt?.contains("Minecraft") == true)
    }

    @Test
    fun test_ActionChain_StepStateDependencies() {
        val step1 = com.example.models.ActionStep(
            type = com.example.models.ChainActionType.OPEN_APP,
            target = "YouTube",
            status = com.example.models.ChainActionStatus.FAILED
        )
        val step2 = com.example.models.ActionStep(
            type = com.example.models.ChainActionType.SEARCH,
            target = "Free Fire",
            dependsOnStepId = step1.id
        )
        val chain = com.example.models.ActionChain(
            taskTitle = "Search Free Fire on YouTube",
            originalCommand = "Search Free Fire on YouTube",
            steps = mutableListOf(step1, step2)
        )

        assertEquals(2, chain.steps.size)
        assertEquals(step1.id, chain.steps[1].dependsOnStepId)
        assertEquals(com.example.models.ChainActionStatus.FAILED, chain.steps[0].status)
    }

    @Test
    fun test_LocalGreetingsAndSmallTalk() {
        val parser = LocalCommandParser()

        val hiVariations = listOf("hii", "hiii", "hi", "heyy", "hey", "hello", "helloo", "hlo", "hlw", "hyy")
        for (greeting in hiVariations) {
            val result = parser.parse(greeting)
            assertTrue("Greeting '$greeting' should be recognized locally", result.recognized)
            assertEquals("Intent for '$greeting' should be GENERAL_CHAT", IntentType.GENERAL_CHAT, result.intent)
            assertNotNull(result.parsedIntent?.responseText)
        }

        val conversationalQueries = listOf(
            "kaise ho",
            "kya haal hai",
            "how are you",
            "who are you",
            "tum kaun ho",
            "namaste",
            "thank you"
        )
        for (query in conversationalQueries) {
            val result = parser.parse(query)
            assertTrue("Query '$query' should be recognized locally", result.recognized)
            assertEquals("Intent for '$query' should be GENERAL_CHAT", IntentType.GENERAL_CHAT, result.intent)
            assertNotNull(result.parsedIntent?.responseText)
        }
    }

    // ==========================================
    // SPEECH-TO-TEXT & HANDS-FREE VOICE TESTS
    // ==========================================

    @Test
    fun test_SpeechLanguage_FromCode_Mapping() {
        assertEquals(com.example.voice.SpeechLanguage.BILINGUAL, com.example.voice.SpeechLanguage.fromCode("hi_en"))
        assertEquals(com.example.voice.SpeechLanguage.HINDI, com.example.voice.SpeechLanguage.fromCode("hi"))
        assertEquals(com.example.voice.SpeechLanguage.ENGLISH, com.example.voice.SpeechLanguage.fromCode("en"))
        // Fallback for unknown code
        assertEquals(com.example.voice.SpeechLanguage.BILINGUAL, com.example.voice.SpeechLanguage.fromCode("unknown_code"))
    }

    @Test
    fun test_SpeechModels_StateAndResult() {
        val result = com.example.voice.SpeechRecognitionResult(
            transcript = "YouTube खोलो और Free Fire search करो",
            confidence = 0.95f,
            isFinal = true
        )
        assertEquals("YouTube खोलो और Free Fire search करो", result.transcript)
        assertTrue(result.isFinal)
        assertEquals(0.95f, result.confidence, 0.001f)

        val activeState = com.example.voice.SpeechState.LISTENING
        assertEquals(com.example.voice.SpeechState.LISTENING, activeState)
    }

    @Test
    fun test_SpeechToTextManager_Robolectric_Instantiation_And_StateFlows() {
        val mockContext = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = com.example.voice.SpeechToTextManager(mockContext)

        // Initial states
        assertEquals(com.example.voice.SpeechState.IDLE, manager.speechState.value)
        assertEquals("", manager.partialTranscript.value)
        assertEquals(0f, manager.soundLevel.value, 0.001f)
        assertNull(manager.lastError.value)

        // Language toggle
        manager.setSelectedLanguage(com.example.voice.SpeechLanguage.HINDI)
        assertEquals(com.example.voice.SpeechLanguage.HINDI, manager.selectedLanguage.value)

        manager.setHandsFreeEnabled(false)
        assertFalse(manager.handsFreeEnabled.value)
        manager.setHandsFreeEnabled(true)
        assertTrue(manager.handsFreeEnabled.value)

        manager.destroy()
    }

    @Test
    fun test_VoiceTriggeredCommand_InLocalCommandParser() {
        val parser = LocalCommandParser()

        // Spoken transcript simulation
        val voiceCommands = listOf(
            "YouTube kholo" to IntentType.OPEN_APP,
            "YouTube पर Arijit Singh ke gaane chalao" to IntentType.YOUTUBE_SEARCH_AND_PLAY,
            "Google par Free Fire search kro" to IntentType.WEB_SEARCH,
            "Settings open karo" to IntentType.OPEN_SETTINGS
        )

        for ((transcript, expectedIntent) in voiceCommands) {
            val result = parser.parse(transcript)
            assertTrue("Voice transcript '$transcript' should be recognized locally", result.recognized)
            assertEquals("Intent for '$transcript' must match expected", expectedIntent, result.intent)
        }
    }

    @Test
    fun test_WeatherQuery_InLocalCommandParser() {
        val parser = LocalCommandParser()

        val weatherQueries = listOf(
            "Aaj ka hmari current location ka weather btao",
            "Mausam kaisa hai",
            "Current weather report",
            "Delhi ka mausam kaisa hai"
        )

        for (query in weatherQueries) {
            val result = parser.parse(query)
            assertTrue("Query '$query' should be recognized", result.recognized)
            assertEquals("Query '$query' should route to PUBLIC_API", IntentType.PUBLIC_API, result.intent)
            assertNotNull("Parsed intent must not be null", result.parsedIntent)
        }

        val delhiResult = parser.parse("Delhi ka mausam kaisa hai")
        assertEquals("wttr_in", delhiResult.parsedIntent?.apiId)
        assertEquals("Delhi", delhiResult.parsedIntent?.apiParams?.get("location"))

        val genericResult = parser.parse("Aaj ka hmari current location ka weather btao")
        assertEquals("open_meteo", genericResult.parsedIntent?.apiId)
    }
}


