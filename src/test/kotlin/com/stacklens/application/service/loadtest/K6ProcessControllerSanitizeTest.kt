package com.stacklens.application.service.loadtest
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.adapter.out.loadtest.K6ScriptTransformerAdapter
import com.stacklens.application.port.out.LoadTestAnalysisPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.loadtest.LocalBaseUrlPort
import com.stacklens.application.port.out.process.ProcessRunPort
import com.stacklens.application.service.loadtest.LoadTestAi
import com.stacklens.testsupport.FakeExecutableLookupPort
import com.stacklens.testsupport.FakeHostDirectoryPort
import com.stacklens.testsupport.FakeShellPort
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class K6ProcessControllerSanitizeTest {
    private val shellProbe = FakeShellPort()
    private val hostDirs = FakeHostDirectoryPort()

    private fun controller(): K6ProcessController {
        val baseUrl =
            mock(LocalBaseUrlPort::class.java)
                .also {
                    org.mockito.Mockito
                        .doReturn("http://localhost:8090")
                        .`when`(it)
                        .currentBaseUrl()
                }
        return testK6ProcessController(
            mock(LoadTestAnalysisPort::class.java),
            mock(LoadTestAi::class.java),
            mock(I18nPort::class.java),
            mock(ProcessRunPort::class.java),
            localBaseUrl = baseUrl,
            scriptTransformer = K6ScriptTransformerAdapter(baseUrl),
            executables = FakeExecutableLookupPort(),
            fileSystem = JavaFileSystemAdapter(),
        )
    }

    @Test
    fun `moveTopLevelStagesIntoDefaultScenario rewrites the real AI-script shape so k6 v2-x accepts it`() {
        val script =
            K6ProcessControllerSanitizeTest::class.java
                .getResourceAsStream("/loadtest/sample-ai-script.js")
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error(
                    "sample-ai-script.js not found on classpath — expected at " +
                        "src/test/resources/loadtest/sample-ai-script.js",
                )
        val result = controller().moveTopLevelStagesIntoDefaultScenario(script)
        val beforeScenarios =
            result.substringBefore("scenarios:")
        val hasTopLevelStages = Regex("""(?m)^\s*stages\s*:""").containsMatchIn(beforeScenarios)
        assertFalse(
            hasTopLevelStages,
            "After the sanitiser no top-level `stages:` key may remain before `scenarios:`:\n$result",
        )
        assertTrue(
            Regex("""(?ms)scenarios\s*:\s*\{.*default\s*:\s*\{""").containsMatchIn(result),
            "Expected `scenarios.default = { ... }` after sanitisation:\n$result",
        )
        assertTrue(
            result.contains("destructiveBench"),
            "`destructiveBench` scenario must survive the sanitisation:\n$result",
        )
        assertTrue(
            result.contains("executor: 'ramping-vus'") || result.contains("executor:  'ramping-vus'"),
            "Expected `ramping-vus` executor in the new `scenarios.default`:\n$result",
        )
    }
}
