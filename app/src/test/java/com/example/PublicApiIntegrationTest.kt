package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.actions.ActionManager
import com.example.actions.PublicApiAction
import com.example.apis.client.ApiResponseFormatter
import com.example.apis.client.GenericApiClient
import com.example.apis.model.ApiDefinition
import com.example.apis.model.ApiParameter
import com.example.apis.registry.ApiRegistry
import com.example.apis.router.ApiToolRouter
import com.example.models.IntentType
import com.example.models.ParsedIntent
import com.example.platform.android.AppLauncher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PublicApiIntegrationTest {

    private lateinit var context: android.content.Context
    private lateinit var registry: ApiRegistry
    private lateinit var apiClient: GenericApiClient
    private lateinit var router: ApiToolRouter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        registry = ApiRegistry(context)
        apiClient = GenericApiClient()
        router = ApiToolRouter(registry, apiClient)
    }

    @Test
    fun testApiRegistry_LoadsCuratedDefinitions() {
        val allApis = registry.getAllApis()
        assertTrue("Registry should load curated public APIs", allApis.isNotEmpty())
        assertTrue("Registry should contain at least 20 APIs", allApis.size >= 20)

        // Verify key public APIs exist
        val jokeApi = registry.getApiById("official_joke_api")
        assertNotNull("Official Joke API must be present", jokeApi)
        assertEquals("Official Joke API", jokeApi?.name)
        assertFalse("Official Joke API requires no authentication", jokeApi?.authRequired ?: true)

        val fruityvice = registry.getApiById("fruityvice")
        assertNotNull("Fruityvice API must be present", fruityvice)
        assertEquals("Food & Drink", fruityvice?.category)

        val coingecko = registry.getApiById("coingecko_simple_price")
        assertNotNull("CoinGecko API must be present", coingecko)
        assertEquals("Cryptocurrency", coingecko?.category)
    }

    @Test
    fun testApiRegistry_CategoriesAndFiltering() {
        val categories = registry.getCategories()
        assertTrue("Categories should include Animals", categories.contains("Animals"))
        assertTrue("Categories should include Entertainment", categories.contains("Entertainment"))

        val animalApis = registry.getApisByCategory("Animals")
        assertTrue("Should have animal APIs", animalApis.isNotEmpty())
        assertTrue(animalApis.all { it.category == "Animals" })

        val searchResults = registry.searchApis("joke")
        assertTrue("Searching for 'joke' should return relevant APIs", searchResults.isNotEmpty())
    }

    @Test
    fun testApiRegistry_EnableDisableToggle() {
        val apiId = "official_joke_api"
        val originalState = registry.getApiById(apiId)?.enabled ?: true
        assertTrue("Should be enabled initially", originalState)

        registry.setApiEnabled(apiId, false)
        assertFalse("Should be disabled after toggle", registry.getApiById(apiId)?.enabled ?: true)

        // Disabled API should not match in router
        val match = router.matchApiByQuery("Tell me a joke")
        // Either matches another joke api or is null, but not the disabled one
        assertNotEquals(apiId, match?.first?.id)

        // Re-enable
        registry.setApiEnabled(apiId, true)
        assertTrue(registry.getApiById(apiId)?.enabled ?: false)
    }

    @Test
    fun testApiToolRouter_FastLocalMatching() {
        // Test 1: Joke query
        val jokeMatch = router.matchApiByQuery("tell me a random joke")
        assertNotNull("Joke query should match an API", jokeMatch)
        assertTrue(jokeMatch?.first?.id?.contains("joke") == true)

        // Test 2: Cat fact query
        val catMatch = router.matchApiByQuery("give me a cat fact")
        assertNotNull("Cat fact query should match an API", catMatch)
        assertEquals("catfact_ninja", catMatch?.first?.id)

        // Test 3: Nutrition / Fruit query
        val fruitMatch = router.matchApiByQuery("nutrition facts for banana")
        assertNotNull("Nutrition query should match an API", fruitMatch)
        assertEquals("fruityvice", fruitMatch?.first?.id)
        assertEquals("banana", fruitMatch?.second?.get("name"))

        // Test 4: Crypto price query
        val cryptoMatch = router.matchApiByQuery("what is the price of bitcoin")
        assertNotNull("Crypto query should match an API", cryptoMatch)
        assertEquals("coingecko_simple_price", cryptoMatch?.first?.id)
        assertEquals("bitcoin", cryptoMatch?.second?.get("ids"))

        // Test 5: Agify query
        val agifyMatch = router.matchApiByQuery("guess age of Rahul")
        assertNotNull("Agify query should match an API", agifyMatch)
        assertEquals("agify_io", agifyMatch?.first?.id)
        assertEquals("Rahul", agifyMatch?.second?.get("name"))
    }

    @Test
    fun testApiToolRouter_GeminiToolDefinitionGeneration() {
        val toolDefs = router.getGeminiToolDefinitions()
        assertTrue("Should generate Gemini tool definitions for enabled APIs", toolDefs.isNotEmpty())
        val jokeDef = toolDefs.find { it.name == "official_joke_api" }
        assertNotNull("Tool definition for official_joke_api should exist", jokeDef)
        assertEquals("Official Joke API", jokeDef?.name?.let { registry.getApiById(it)?.name })
    }

    @Test
    fun testApiResponseFormatter_RobustJsonHandling() {
        // Object formatting
        val sampleJson = """
            {
                "setup": "Why did the chicken cross the road?",
                "punchline": "To get to the other side!"
            }
        """.trimIndent()
        val formatted = ApiResponseFormatter.formatResponse(
            rawBody = sampleJson,
            responseTemplate = "{setup} — {punchline}",
            category = "Entertainment"
        )
        assertTrue("Formatted response should contain setup", formatted.contains("Why did the chicken cross the road?"))
        assertTrue("Formatted response should contain punchline", formatted.contains("To get to the other side!"))

        // Array formatting
        val sampleArrayJson = """
            [
                {"name": "Apple", "calories": 52},
                {"name": "Banana", "calories": 89}
            ]
        """.trimIndent()
        val formattedArray = ApiResponseFormatter.formatResponse(
            rawBody = sampleArrayJson,
            responseTemplate = null,
            category = "Food & Drink"
        )
        assertTrue("Formatted array should contain items", formattedArray.contains("Apple") && formattedArray.contains("Banana"))

        // Safe failure / malformed JSON handling without stack trace exposure
        val malformed = "{ broken json: true "
        val formattedSafe = ApiResponseFormatter.formatResponse(
            rawBody = malformed,
            responseTemplate = null,
            category = "Other"
        )
        assertFalse("Should not expose stack trace", formattedSafe.contains("Exception") || formattedSafe.contains("at com."))
    }

    @Test
    fun testPublicApiAction_RegisteredInActionManager() = runBlocking {
        val appLauncher = AppLauncher(context)
        val actionManager = ActionManager(appLauncher = appLauncher)
        actionManager.registerAction(PublicApiAction(router))

        val intent = ParsedIntent(
            type = IntentType.PUBLIC_API,
            apiId = "official_joke_api",
            apiParams = emptyMap(),
            query = "Tell me a joke"
        )

        val result = actionManager.execute(intent, context)
        assertNotNull("Result should not be null", result)
        // Public API action returns a formatted response message (even if offline/mock in tests, it handles gracefully)
        assertTrue(result.message.isNotBlank())
    }
}
