package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.models.IntentType
import com.example.services.CommandParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Myra", appName)
  }

  @Test
  fun `test command parser deterministic intents`() {
    val parser = CommandParser()

    val ytIntent = parser.parseDeterministicCommand("YouTube खोलो")
    assertNotNull(ytIntent)
    assertEquals(IntentType.OPEN_APP, ytIntent?.type)
    assertEquals("YouTube", ytIntent?.app)

    val chromeIntent = parser.parseDeterministicCommand("Chrome खोलो")
    assertNotNull(chromeIntent)
    assertEquals(IntentType.OPEN_APP, chromeIntent?.type)
    assertEquals("Chrome", chromeIntent?.app)

    val settingsIntent = parser.parseDeterministicCommand("Settings खोलो")
    assertNotNull(settingsIntent)
    assertEquals(IntentType.OPEN_SETTINGS, settingsIntent?.type)

    val searchIntent = parser.parseDeterministicCommand("Google पर Free Fire search करो")
    assertNotNull(searchIntent)
    assertEquals(IntentType.WEB_SEARCH, searchIntent?.type)
    assertEquals("Free Fire", searchIntent?.query)

    val langIntent = parser.parseDeterministicCommand("मुझसे हमेशा हिंदी में बात करना")
    assertNotNull(langIntent)
    assertEquals(IntentType.SET_PREFERENCE, langIntent?.type)
    assertEquals("hi", langIntent?.value)

    // V2 Multi-Step Tests
    val multiStep1 = parser.parseDeterministicCommand("Open YouTube and search for Free Fire")
    assertNotNull(multiStep1)
    assertEquals(IntentType.MULTI_ACTION, multiStep1?.type)
    assertEquals(2, multiStep1?.actions?.size)
    assertEquals(IntentType.OPEN_APP, multiStep1?.actions?.get(0)?.type)
    assertEquals("YouTube", multiStep1?.actions?.get(0)?.app)
    assertEquals(IntentType.YOUTUBE_SEARCH, multiStep1?.actions?.get(1)?.type)
    assertEquals("Free Fire", multiStep1?.actions?.get(1)?.query)

    val multiStepHindi = parser.parseDeterministicCommand("YouTube खोलो और Free Fire search करो")
    assertNotNull(multiStepHindi)
    assertEquals(IntentType.MULTI_ACTION, multiStepHindi?.type)
    assertEquals(2, multiStepHindi?.actions?.size)
    assertEquals(IntentType.YOUTUBE_SEARCH, multiStepHindi?.actions?.get(1)?.type)

    // Test LocalCommandParser Level 1 Engine
    val localParser = com.example.services.LocalCommandParser()

    // Test case 1: "YouTube खोलो"
    val res1 = localParser.parse("YouTube खोलो")
    org.junit.Assert.assertTrue(res1.recognized)
    assertEquals(IntentType.OPEN_APP, res1.intent)
    assertEquals("YouTube", res1.parameters["app"])

    // Test case 2: "Chrome खोलो"
    val res2 = localParser.parse("Chrome खोलो")
    org.junit.Assert.assertTrue(res2.recognized)
    assertEquals(IntentType.OPEN_APP, res2.intent)
    assertEquals("Chrome", res2.parameters["app"])

    // Test case 3: "YouTube पर song search करो"
    val res3 = localParser.parse("YouTube पर song search करो")
    org.junit.Assert.assertTrue(res3.recognized)
    assertEquals(IntentType.YOUTUBE_SEARCH, res3.intent)
    assertEquals("song", res3.parameters["query"])

    // Test case 4: "VK Bhuriya का song search करो YouTube पर"
    val res4 = localParser.parse("VK Bhuriya का song search करो YouTube पर")
    org.junit.Assert.assertTrue(res4.recognized)
    assertEquals(IntentType.YOUTUBE_SEARCH, res4.intent)
    assertEquals("VK Bhuriya song", res4.parameters["query"])

    // Test case 5: "Motivational songs search करो YouTube पर"
    val res5 = localParser.parse("Motivational songs search करो YouTube पर")
    org.junit.Assert.assertTrue(res5.recognized)
    assertEquals(IntentType.YOUTUBE_SEARCH, res5.intent)
    assertEquals("Motivational songs", res5.parameters["query"])

    // Test case 5b: "YouTube par Free Fire search kro"
    val res5b = localParser.parse("YouTube par Free Fire search kro")
    org.junit.Assert.assertTrue(res5b.recognized)
    assertEquals(IntentType.YOUTUBE_SEARCH, res5b.intent)
    assertEquals("Free Fire", res5b.parameters["query"])

    // Test case 6: "Google पर Free Fire search करो"
    val res6 = localParser.parse("Google पर Free Fire search करो")
    org.junit.Assert.assertTrue(res6.recognized)
    assertEquals(IntentType.WEB_SEARCH, res6.intent)
    assertEquals("Free Fire", res6.parameters["query"])

    // Test case 6b: "Google par weather search kro"
    val res6b = localParser.parse("Google par weather search kro")
    org.junit.Assert.assertTrue(res6b.recognized)
    assertEquals(IntentType.WEB_SEARCH, res6b.intent)
    assertEquals("weather", res6b.parameters["query"])

    // Test case 7: Complex natural language query -> fallback to Gemini (recognized = false)
    val res7 = localParser.parse("मेरे लिए ऐसा गाना ढूंढो जो बारिश के मौसम के लिए अच्छा हो")
    org.junit.Assert.assertFalse(res7.recognized)

    val resClear = localParser.parse("clear chat")
    org.junit.Assert.assertTrue(resClear.recognized)
    assertEquals(IntentType.CLEAR_CHAT, resClear.intent)

    // Test Gemini Multi-Action JSON Parsing
    val jsonSample = """
      {
        "task": "multi_action",
        "actions": [
          {"type": "OPEN_APP", "app": "YouTube"},
          {"type": "WAIT", "durationMs": 1000},
          {"type": "WEB_SEARCH", "query": "Free Fire"}
        ],
        "responseText": "Opening YouTube and searching for Free Fire."
      }
    """.trimIndent()
    val parsedJsonIntent = parser.parseGeminiJsonResponse(jsonSample)
    assertEquals(IntentType.MULTI_ACTION, parsedJsonIntent.type)
    assertEquals(3, parsedJsonIntent.actions.size)
    assertEquals(IntentType.OPEN_APP, parsedJsonIntent.actions[0].type)
    assertEquals(IntentType.WEB_SEARCH, parsedJsonIntent.actions[2].type)
    assertEquals("Free Fire", parsedJsonIntent.actions[2].query)
  }

  @Test
  fun `test accessibility models and privacy protections`() {
    val treeReader = com.example.services.AccessibilityTreeReader()

    // Test ScreenState and UIElement models
    val element1 = com.example.models.UIElement(
        text = "Search",
        contentDescription = "Search YouTube",
        isClickable = true,
        isEditable = false,
        packageName = "com.google.android.youtube"
    )
    val element2 = com.example.models.UIElement(
        text = "Search query input",
        isClickable = true,
        isEditable = true,
        packageName = "com.google.android.youtube"
    )
    val element3 = com.example.models.UIElement(
        text = "Home",
        isClickable = true,
        isEditable = false,
        packageName = "com.google.android.youtube"
    )

    val screenState = com.example.models.ScreenState(
        packageName = "com.google.android.youtube",
        className = "com.google.android.apps.youtube.app.WatchWhileActivity",
        elements = listOf(element1, element2, element3)
    )

    assertEquals("com.google.android.youtube", screenState.packageName)
    assertEquals(3, screenState.elements.size)
    assertEquals(3, screenState.clickableElements.size)
    assertEquals(1, screenState.editableElements.size)
    assertEquals("Search", screenState.elements[0].displayLabel)

    // Verify empty root safely returns empty ScreenState
    val emptyState = treeReader.extractScreenState(null)
    assertEquals(0, emptyState.elements.size)

    // Verify action enum types
    val clickType = com.example.models.AccessibilityActionType.CLICK
    val setTextType = com.example.models.AccessibilityActionType.SET_TEXT
    val focusType = com.example.models.AccessibilityActionType.FOCUS
    val backType = com.example.models.AccessibilityActionType.BACK

    assertEquals("CLICK", clickType.name)
    assertEquals("SET_TEXT", setTextType.name)
    assertEquals("FOCUS", focusType.name)
    assertEquals("BACK", backType.name)
  }

  @Test
  fun `test V3_2 YouTube search commands zero gemini calls`() {
    val parser = CommandParser()

    // Test case 1: "YouTube पर VK Bhuriya का song search करो"
    val ytSongIntent = parser.parseDeterministicCommand("YouTube पर VK Bhuriya का song search करो")
    assertNotNull(ytSongIntent)
    assertEquals(IntentType.YOUTUBE_SEARCH, ytSongIntent?.type)
    assertEquals("VK Bhuriya song", ytSongIntent?.query)

    // Test case 2: "Motivational songs YouTube पर search करो"
    val ytMotivIntent = parser.parseDeterministicCommand("Motivational songs YouTube पर search करो")
    assertNotNull(ytMotivIntent)
    assertEquals(IntentType.YOUTUBE_SEARCH, ytMotivIntent?.type)
    assertEquals("Motivational songs", ytMotivIntent?.query)

    // Test case 3: "YouTube par Free Fire search kro"
    val ytFfIntent = parser.parseDeterministicCommand("YouTube par Free Fire search kro")
    assertNotNull(ytFfIntent)
    assertEquals(IntentType.YOUTUBE_SEARCH, ytFfIntent?.type)
    assertEquals("Free Fire", ytFfIntent?.query)
  }

  @Test
  fun `test ElementMatcher logic and YouTube matching`() {
    val searchButton = com.example.models.UIElement(
        contentDescription = "Search YouTube",
        isClickable = true,
        viewId = "com.google.android.youtube:id/menu_item_search",
        packageName = "com.google.android.youtube"
    )
    val searchInput = com.example.models.UIElement(
        text = "Search YouTube",
        isClickable = true,
        isEditable = true,
        viewId = "com.google.android.youtube:id/search_edit_text",
        packageName = "com.google.android.youtube"
    )
    val videoResult = com.example.models.UIElement(
        text = "VK Bhuriya New Aadiwasi Song 2026",
        contentDescription = "VK Bhuriya New Aadiwasi Song 2026 • 5 minutes • 1M views",
        isClickable = true,
        packageName = "com.google.android.youtube"
    )

    // Verify ElementMatcher matches
    val matchSearchBtn = com.example.services.ElementMatcher.forYouTubeSearchButton()
    assertEquals(true, matchSearchBtn(searchButton))

    val matchSearchInput = com.example.services.ElementMatcher.forSearchInputField()
    assertEquals(true, matchSearchInput(searchInput))

    val matchVideo = com.example.services.ElementMatcher.forYouTubeResultItem()
    assertEquals(true, matchVideo(videoResult))

    // Generic matchers
    assertEquals(true, com.example.services.ElementMatcher.matches(videoResult, "VK Bhuriya", exactMatch = false))
    assertEquals(true, com.example.services.ElementMatcher.matches(searchButton, "menu_item_search", exactMatch = false))
  }

  @Test
  fun `test V3_2_1 GeminiRateLimiter sliding window and cooldown protection`() = kotlinx.coroutines.runBlocking {
    val rateLimiter = com.example.services.GeminiRateLimiter
    rateLimiter.resetForTesting()

    // 1. Fill sliding window up to MAX_REQUESTS_PER_MINUTE (10)
    for (i in 1..com.example.services.GeminiRateLimiter.MAX_REQUESTS_PER_MINUTE) {
      val res = rateLimiter.checkAndAcquirePermission()
      assertEquals(com.example.services.RateLimitCheckResult.Allowed, res)
    }

    // 11th request must be blocked by RPM limit
    val blockedRes = rateLimiter.checkAndAcquirePermission()
    assertEquals(true, blockedRes is com.example.services.RateLimitCheckResult.BlockedByRpmLimit)

    // Reset and test HTTP 429 Cooldown
    rateLimiter.resetForTesting()
    rateLimiter.handleHttp429(retryAfterSeconds = 10)

    val cooldownRes = rateLimiter.checkAndAcquirePermission()
    assertEquals(true, cooldownRes is com.example.services.RateLimitCheckResult.BlockedByCooldown)

    rateLimiter.resetForTesting()
  }

  @Test
  fun `test V3_2_1 local commands bypass Gemini completely`() {
    val localParser = com.example.services.LocalCommandParser()

    // Test A: "YouTube par gamini search kro" -> 0 Gemini calls
    val ytGamini = localParser.parse("YouTube par gamini search kro")
    assertEquals(true, ytGamini.recognized)
    assertEquals(IntentType.YOUTUBE_SEARCH, ytGamini.parsedIntent?.type)
    assertEquals("gamini", ytGamini.parsedIntent?.query)

    // Test B: "YouTube kholo" -> 0 Gemini calls
    val ytKholo = localParser.parse("YouTube kholo")
    assertEquals(true, ytKholo.recognized)
    assertEquals(IntentType.OPEN_APP, ytKholo.parsedIntent?.type)
    assertEquals("YouTube", ytKholo.parsedIntent?.app)

    // Test C: "Google par Free Fire search kro" -> 0 Gemini calls
    val gFf = localParser.parse("Google par Free Fire search kro")
    assertEquals(true, gFf.recognized)
    assertEquals(IntentType.WEB_SEARCH, gFf.parsedIntent?.type)
    assertEquals("Free Fire", gFf.parsedIntent?.query)

    // Test D: "gamini" -> Not recognized locally, routes to Gemini AI (max 1 call)
    val gaminiChat = localParser.parse("gamini")
    assertEquals(false, gaminiChat.recognized)

    // Test E: "gamini search kro" -> Not recognized locally, routes to Gemini AI (max 1 call)
    val gaminiSearch = localParser.parse("gamini search kro")
    assertEquals(false, gaminiSearch.recognized)
  }

  @Test
  fun `test GeminiService model configuration and safe 404 retry termination`() = kotlinx.coroutines.runBlocking {
    com.example.services.GeminiRateLimiter.resetForTesting()

    // 1. Verify model constants
    assertEquals("gemini-3.5-flash", com.example.services.GeminiService.PRIMARY_MODEL)
    assertEquals("v1beta", com.example.services.GeminiService.API_VERSION)

    // 2. Mock 404 Not Found response and verify immediate stop (exactly 1 call, no retry)
    var callCount404 = 0
    val client404 = okhttp3.OkHttpClient.Builder()
      .addInterceptor { chain ->
        callCount404++
        okhttp3.Response.Builder()
          .request(chain.request())
          .protocol(okhttp3.Protocol.HTTP_1_1)
          .code(404)
          .message("Not Found")
          .body(
            """{"error":{"code":404,"message":"models/gemini-invalid is not found for API version v1beta"}}"""
              .toResponseBody("application/json".toMediaType())
          )
          .build()
      }
      .build()

    val geminiService404 = com.example.services.GeminiService(CommandParser(), client404)
    val result404 = geminiService404.analyzeAndRespond("test input", "context", customApiKey = "AIzaFakeKey123")

    assertEquals(true, result404.isFailure)
    assertEquals(1, callCount404) // CRITICAL: Stop retrying on 404 immediately
    assertEquals(true, result404.exceptionOrNull()?.message?.contains("404") == true)

    // 3. Mock 400 Bad Request response and verify immediate stop (exactly 1 call, no retry)
    var callCount400 = 0
    val client400 = okhttp3.OkHttpClient.Builder()
      .addInterceptor { chain ->
        callCount400++
        okhttp3.Response.Builder()
          .request(chain.request())
          .protocol(okhttp3.Protocol.HTTP_1_1)
          .code(400)
          .message("Bad Request")
          .body(
            """{"error":{"code":400,"message":"API key not valid"}}"""
              .toResponseBody("application/json".toMediaType())
          )
          .build()
      }
      .build()

    val geminiService400 = com.example.services.GeminiService(CommandParser(), client400)
    val result400 = geminiService400.analyzeAndRespond("test input", "context", customApiKey = "AIzaFakeKey123")

    assertEquals(true, result400.isFailure)
    assertEquals(1, callCount400) // CRITICAL: Stop retrying on 400 immediately

    // 4. Mock 429 Rate Limited response and verify cooldown activation
    com.example.services.GeminiRateLimiter.resetForTesting()
    var callCount429 = 0
    val client429 = okhttp3.OkHttpClient.Builder()
      .addInterceptor { chain ->
        callCount429++
        okhttp3.Response.Builder()
          .request(chain.request())
          .protocol(okhttp3.Protocol.HTTP_1_1)
          .code(429)
          .message("Too Many Requests")
          .header("Retry-After", "30")
          .body(
            """{"error":{"code":429,"message":"Resource exhausted"}}"""
              .toResponseBody("application/json".toMediaType())
          )
          .build()
      }
      .build()

    val geminiService429 = com.example.services.GeminiService(CommandParser(), client429)
    val result429 = geminiService429.analyzeAndRespond("test input", "context", customApiKey = "AIzaFakeKey123")

    assertEquals(true, result429.isFailure)
    assertEquals(1, callCount429) // Bounded rate limit handling with persistent cooldown

    // Subsequent call immediately blocked by cooldown without making network request
    val rateLimitBlockedResult = geminiService429.analyzeAndRespond("test input 2", "context", customApiKey = "AIzaFakeKey123")
    assertEquals(true, rateLimitBlockedResult.isFailure)
    assertEquals(1, callCount429) // Count remains 1 because cooldown blocked it locally

    com.example.services.GeminiRateLimiter.resetForTesting()
  }

  @Test
  fun `test text to speech cleaner sanitizes markdown and technical formatting`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val ttsManager = com.example.voice.TextToSpeechManager(context)

    // Markdown bold, italic, headers, bullet points
    val rawMarkdown = """
      # Myra Assistant
      **नमस्ते!** I have executed *your command*.
      - Step 1: Open app
      - Step 2: Search video
      Here is the link: https://youtube.com/watch?v=12345
      ```
      val code = "hidden"
      ```
      Status: {success: true}
    """.trimIndent()

    val cleaned = ttsManager.cleanTextForSpeech(rawMarkdown)

    // Assert that formatting symbols are stripped
    assertEquals(false, cleaned.contains("**"))
    assertEquals(false, cleaned.contains("#"))
    assertEquals(false, cleaned.contains("https://"))
    assertEquals(false, cleaned.contains("val code"))
    assertEquals(false, cleaned.contains("{"))
    assertEquals(false, cleaned.contains("}"))
    assertEquals(true, cleaned.contains("नमस्ते! I have executed your command."))
  }

  @Test
  fun `test memory repository voice output preferences`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = androidx.room.Room.inMemoryDatabaseBuilder(
      context,
      com.example.data.MyraDatabase::class.java
    ).allowMainThreadQueries().build()

    val memoryRepo = com.example.data.MemoryRepository(
      messageDao = db.messageDao(),
      preferenceDao = db.preferenceDao(),
      interactionHistoryDao = db.interactionHistoryDao(),
      context = context
    )

    // Default should be true
    assertEquals(true, memoryRepo.isVoiceOutputEnabled())

    // Update to false
    memoryRepo.setVoiceOutputEnabled(false)
    assertEquals(false, memoryRepo.isVoiceOutputEnabled())

    // Update speech rate
    memoryRepo.setTtsSpeechRate(1.2f)
    assertEquals(1.2f, memoryRepo.getTtsSpeechRate(), 0.01f)

    db.close()
  }
}

