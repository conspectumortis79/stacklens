package com.stacklens.application.service
import com.stacklens.application.port.out.loadtest.LoadTestEnvironment
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.service.loadtest.LoadTestAi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class LoadTestAiServiceTest {
    @Mock
    lateinit var promptLoader: PromptReaderPort

    private val service by lazy {
        LoadTestAiService(
            promptLoader,
            com.stacklens.adapter.out.textfilter
                .DefaultAiResponseJsonExtractor(),
        )
    }

    @Test
    fun `buildAnalysisPrompt delegates to promptLoader`() {
        whenever(promptLoader.get(eq("de"), eq("loadtest.analysisPrompt"), any())).thenReturn("analysis prompt")
        val result = service.buildAnalysisPrompt("de", "MyProject", "/path", "tree", "contents")
        assertEquals("analysis prompt", result)
    }

    @Test
    fun `buildK6Prompt delegates to promptLoader`() {
        whenever(promptLoader.get(eq("de"), eq("loadtest.k6Prompt"), any())).thenReturn("k6 prompt")
        val result = service.buildK6Prompt("de", "MyProject", "{}", "Dockerfile", "routes.kt")
        assertEquals("k6 prompt", result)
    }

    @Test
    fun `buildEnvDetectPrompt delegates to promptLoader`() {
        whenever(promptLoader.get(eq("de"), eq("loadtest.envDetectPrompt"), any())).thenReturn("env prompt")
        val result = service.buildEnvDetectPrompt("de", "MyProject", "Dockerfile", "routes.kt")
        assertEquals("env prompt", result)
    }

    @Test
    fun `parseEnvironments returns empty list when json is null`() {
        val result = service.parseEnvironments(null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseEnvironments returns empty list when json is blank`() {
        val result = service.parseEnvironments("   ")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseEnvironments parses single environment`() {
        val json = """{"name": "local", "host": "localhost", "port": "8080", "scheme": "http"}"""
        val result = service.parseEnvironments(json)
        assertEquals(1, result.size)
        assertEquals("local", result[0].name)
        assertEquals("localhost", result[0].host)
        assertEquals("8080", result[0].port)
        assertEquals("http", result[0].scheme)
    }

    @Test
    fun `parseEnvironments parses multiple environments`() {
        val json =
            """
            {"name": "dev", "host": "dev.example.com", "port": "80", "scheme": "http"}
            {"name": "prod", "host": "prod.example.com", "port": "443", "scheme": "https"}
            """.trimIndent()
        val result = service.parseEnvironments(json)
        assertEquals(2, result.size)
    }

    @Test
    fun `parseEnvironments filters out entries without name`() {
        val json = """{"name": "", "host": "localhost", "port": "8080", "scheme": "http"}"""
        val result = service.parseEnvironments(json)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseEnvironments filters out entries without host`() {
        val json = """{"name": "dev", "host": "", "port": "8080", "scheme": "http"}"""
        val result = service.parseEnvironments(json)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseEnvironments returns blank fields for missing optional keys`() {
        val json = """{"name": "dev", "host": "localhost"}"""
        val result = service.parseEnvironments(json)

        assertEquals(1, result.size)
        assertEquals("", result[0].port)
        assertEquals("", result[0].scheme)
    }

    @Test
    fun `patchK6BaseUrl replaces const BASE_URL with http non-standard port`() {
        val script = "const BASE_URL = 'http://old.example.com'"
        val result =
            service.patchK6BaseUrl(
                script,
                com.stacklens.application.port.out.loadtest
                    .LoadTestEnvironment("dev", "new.example.com", "8080", "http"),
            )
        assertTrue(result.contains("http://new.example.com:8080"))
    }

    @Test
    fun `patchK6BaseUrl omits port 80 for http`() {
        val script = "const BASE_URL = 'http://old.example.com:80'"
        val result =
            service.patchK6BaseUrl(
                script,
                com.stacklens.application.port.out.loadtest
                    .LoadTestEnvironment("dev", "new.example.com", "80", "http"),
            )
        assertTrue(result.contains("http://new.example.com'"))
    }

    @Test
    fun `patchK6BaseUrl omits port 443 for https`() {
        val script = "const BASE_URL = 'https://old.example.com'"
        val result =
            service.patchK6BaseUrl(
                script,
                com.stacklens.application.port.out.loadtest
                    .LoadTestEnvironment("prod", "secure.example.com", "443", "https"),
            )
        assertTrue(result.contains("https://secure.example.com'"))
    }

    @Test
    fun `patchK6BaseUrl replaces let BASE_URL`() {
        val script = "let BASE_URL = 'http://old.example.com'"
        val result =
            service.patchK6BaseUrl(
                script,
                com.stacklens.application.port.out.loadtest
                    .LoadTestEnvironment("dev", "new.host", "3000", "http"),
            )
        assertTrue(result.contains("const BASE_URL = 'http://new.host:3000'"))
    }

    @Test
    fun `patchK6BaseUrl keeps script unchanged when no BASE_URL found`() {
        val script = "var someOtherVar = 'value'"
        val result =
            service.patchK6BaseUrl(
                script,
                com.stacklens.application.port.out.loadtest
                    .LoadTestEnvironment("dev", "host", "80", "http"),
            )
        assertEquals(script, result)
    }

    @Test
    fun `patchK6BaseUrl replaces var BASE_URL`() {
        val script = "var BASE_URL = 'http://old.example.com'"
        val result =
            service.patchK6BaseUrl(
                script,
                com.stacklens.application.port.out.loadtest
                    .LoadTestEnvironment("dev", "host", "81", "http"),
            )

        assertTrue(result.contains("const BASE_URL = 'http://host:81'"))
    }

    @Test
    fun `patchK6BaseUrl handles blank port`() {
        val script = "const BASE_URL = 'http://old.example.com'"
        val result =
            service.patchK6BaseUrl(
                script,
                com.stacklens.application.port.out.loadtest
                    .LoadTestEnvironment("dev", "host.com", "", "http"),
            )
        assertTrue(result.contains("http://host.com'"))
    }

    @Test
    fun `patchK6BaseUrl replaces AI-generated __ENV BASE_URL fallback`() {
        val script = "const BASE_URL = __ENV.BASE_URL || 'http://localhost:8085';"
        val result =
            service.patchK6BaseUrl(
                script,
                com.stacklens.application.port.out.loadtest
                    .LoadTestEnvironment("Local Dev", "localhost", "8090", "http"),
            )
        assertTrue(result.contains("const BASE_URL = 'http://localhost:8090'"))
        assertFalse(result.contains("__ENV.BASE_URL"))
        assertFalse(result.contains("localhost:8085"))
    }

    @Test
    fun `extractSection extracts text between markers`() {
        val text = "prefix---START---middle content---END---suffix"
        val result = service.extractSection(text, "---START---", "---END---")
        assertEquals("middle content", result)
    }

    @Test
    fun `extractSection returns empty string when startMarker not found`() {
        val result = service.extractSection("some text", "---NOT_FOUND---", "---END---")
        assertEquals("", result)
    }

    @Test
    fun `extractSection returns rest of text when endMarker not found`() {
        val text = "before---START---remaining text"
        val result = service.extractSection(text, "---START---", "---END---")
        assertEquals("remaining text", result)
    }

    @Test
    fun `extractJsonBlock extracts JSON array from section`() {
        val text = "---START---[{\"key\": \"val\"}]---END---"
        val result = service.extractJsonBlock(text, "---START---", "---END---")
        assertEquals("[{\"key\": \"val\"}]", result)
    }

    @Test
    fun `extractJsonBlock returns null when start marker not found`() {
        val result = service.extractJsonBlock("no markers here", "---START---", "---END---")
        assertNull(result)
    }

    @Test
    fun `extractJsonBlock returns null when no array brackets found`() {
        val text = "---START---no array content---END---"
        val result = service.extractJsonBlock(text, "---START---", "---END---")
        assertNull(result)
    }

    @Test
    fun `extractJsonArray extracts array from code fence`() {
        val text = "```json\n[{\"id\": 1}]\n```"
        val result = service.extractJsonArray(text)
        assertEquals("[{\"id\": 1}]", result)
    }

    @Test
    fun `extractJsonArray extracts bare array`() {
        val text = "Result: [{\"id\": 1}, {\"id\": 2}] done"
        val result = service.extractJsonArray(text)
        assertEquals("[{\"id\": 1}, {\"id\": 2}]", result)
    }

    @Test
    fun `extractJsonArray returns null when no array found`() {
        val result = service.extractJsonArray("no array in this text")
        assertNull(result)
    }

    @Test
    fun `extractJsonArray falls back to bare array when fenced block is not an array`() {
        val text =
            """
            ```json
            {"k":"v"}
            ```
            trailing [1,2,3]
            """.trimIndent()

        assertEquals("[1,2,3]", service.extractJsonArray(text))
    }

    @Test
    fun `extractCodeBlock extracts specific language code block`() {
        val text = "```javascript\nconst x = 1;\n```"
        val result = service.extractCodeBlock(text, "javascript", "js")
        assertEquals("const x = 1;", result)
    }

    @Test
    fun `extractCodeBlock falls back to any code block when lang not matched`() {
        val text = "```python\nprint('hello')\n```"
        val result = service.extractCodeBlock(text, "javascript")
        assertEquals("print('hello')", result)
    }

    @Test
    fun `extractCodeBlock returns null when no code block found`() {
        val result = service.extractCodeBlock("plain text without fences", "javascript")
        assertNull(result)
    }

    @Test
    fun `extractCodeBlock handles multiple languages`() {
        val text = "```k6\nexport default function() {}\n```"
        val result = service.extractCodeBlock(text, "javascript", "k6")
        assertEquals("export default function() {}", result)
    }

    @Test
    fun `patchK6BaseUrl keeps port for https non-standard port`() {
        val env = LoadTestEnvironment("s", "x.com", "8443", "https")
        val script = "const BASE_URL = 'https://old:8443'"
        val result = service.patchK6BaseUrl(script, env)
        assertTrue(result.contains(":8443"))
    }

    @Test
    fun `extractJsonBlock returns null when section has opening bracket but no closing`() {
        val text = "---START---[no closing bracket here---END---"
        assertNull(service.extractJsonBlock(text, "---START---", "---END---"))
    }

    @Test
    fun `extractJsonBlock returns null when closing bracket precedes opening bracket`() {
        val text = "---START---] then [---END---"
        assertNull(service.extractJsonBlock(text, "---START---", "---END---"))
    }

    @Test
    fun `extractJsonArray returns null when text has opening bracket but no valid closing`() {
        assertNull(service.extractJsonArray("[no closing"))
    }

    @Test
    fun `LoadTestAi buildK6Prompt with default params`() {
        val port =
            object : com.stacklens.application.service.loadtest.LoadTestAi {
                override fun buildAnalysisPrompt(
                    lang: String,
                    projectName: String,
                    projectPath: String,
                    projectTree: String,
                    fileContents: String,
                ) = "analysis"

                override fun buildK6Prompt(
                    lang: String,
                    projectName: String,
                    findingsJson: String,
                    dockerFiles: String,
                    routeFiles: String,
                ) = "k6"

                override fun buildEnvDetectPrompt(
                    lang: String,
                    projectName: String,
                    dockerFiles: String,
                    routeFiles: String,
                ) = "env"

                override fun parseEnvironments(json: String?) = emptyList<com.stacklens.application.port.out.loadtest.LoadTestEnvironment>()

                override fun patchK6BaseUrl(
                    script: String,
                    env: com.stacklens.application.port.out.loadtest.LoadTestEnvironment,
                ) = script

                override fun extractSection(
                    text: String,
                    startMarker: String,
                    endMarker: String,
                ) = ""

                override fun extractJsonBlock(
                    text: String,
                    startMarker: String,
                    endMarker: String,
                ): String? = null

                override fun extractJsonArray(text: String): String? = null

                override fun extractCodeBlock(
                    text: String,
                    vararg langs: String,
                ): String? = null
            }
        assertEquals("k6", port.buildK6Prompt("de", "proj", "[]"))
        assertEquals("env", port.buildEnvDetectPrompt("de", "proj"))
    }

    @Test
    fun `LoadTestEnvironment creates and equals`() {
        val e1 =
            com.stacklens.application.port.out.loadtest.LoadTestEnvironment(
                name = "prod",
                host = "prod.example.com",
                port = "443",
                scheme = "https",
            )
        val e2 =
            com.stacklens.application.port.out.loadtest.LoadTestEnvironment(
                name = "prod",
                host = "prod.example.com",
                port = "443",
                scheme = "https",
            )
        assertEquals(e1, e2)
        assertEquals("prod", e1.name)
    }

    @Test
    fun `LoadTestEnvironment copy`() {
        val e =
            com.stacklens.application.port.out.loadtest
                .LoadTestEnvironment("dev", "localhost", "8080", "http")
        val copy = e.copy(port = "9090")
        assertEquals("9090", copy.port)
        assertEquals("8080", e.port)
    }

    @Test
    fun `parseEnvironments returns empty list when JSON causes exception`() {
        val result = service.parseEnvironments("{{{")
        assertEquals(emptyList<Any>(), result)
    }

    @Test
    fun `extractCodeBlock returns null when no code blocks in text`() {
        val result = service.extractCodeBlock("plain text without code blocks", "javascript", "js")
        org.junit.jupiter.api.Assertions
            .assertNull(result)
    }

    @Test
    fun `extractCodeBlock returns null when language not matched and no generic fallback`() {
        val text = "Some text without any fenced blocks at all"
        val result = service.extractCodeBlock(text, "python")
        org.junit.jupiter.api.Assertions
            .assertNull(result)
    }
}
