package com.stacklens.application.service

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.model.pipeline.StageStatus
import com.stacklens.application.port.out.textfilter.OutputFilterPort
import com.stacklens.application.port.out.textfilter.TextFilterConfigPort
import com.stacklens.domain.text.filter.FilterResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TextFilterIntegrationTest {
    private val realisticMakeOutput =
        buildString {
            appendLine("make[1]: Entering directory '/home/user/proj'")
            appendLine("gcc -O2 -c src/foo.c -o build/foo.o")
            appendLine("gcc -O2 -c src/bar.c -o build/bar.o")
            appendLine("gcc build/foo.o build/bar.o -o build/myapp")
            appendLine("make[1]: Leaving directory '/home/user/proj'")
            appendLine("make[1]: Entering directory '/home/user/proj'")
            appendLine("gcc -O2 -c src/foo.c -o build/foo.o")
            appendLine("gcc: error: src/baz.c: No such file or directory")
            appendLine("make[1]: *** [build/myapp] Error 1")
            appendLine("make[1]: Leaving directory '/home/user/proj'")
            appendLine("make: *** [Makefile:7: all] Error 2")
        }

    private val matchingCommand = listOf("make", "all")

    @Test
    fun `real make output is shrunk significantly when filter is enabled`() {
        val config = ToggleableConfig(initial = true)
        val filter = TextFilterIntegrationFixture.createRealFilter(config)

        val result = filter.filter(matchingCommand, realisticMakeOutput, "")

        assertTrue(result.text.length < realisticMakeOutput.length, "filter must produce a smaller output")
        val savings = (realisticMakeOutput.length - result.text.length) * 100 / realisticMakeOutput.length
        assertTrue(savings > 10, "expected >10% savings, got $savings% (raw=${realisticMakeOutput.length}, filtered=${result.text.length})")
        assertTrue(result.text.contains("Error"), "expected 'Error' to survive the filter, got: ${result.text}")
    }

    @Test
    fun `filter is bypassed when config port says disabled`() {
        val config = ToggleableConfig(initial = false)
        val filter = TextFilterIntegrationFixture.createRealFilter(config)

        val result = filter.filter(matchingCommand, realisticMakeOutput, "")

        assertEquals(realisticMakeOutput, result.text, "output must be unchanged when filter is disabled")
        assertEquals("<disabled>", result.specName)
    }

    @Test
    fun `filter is bypassed when adapter is constructed with disabled config and enabledProp false`() {
        val config =
            object : TextFilterConfigPort {
                override val enabled: Boolean = false

                override fun setEnabled(enabled: Boolean) {}
            }
        val adapter =
            com.stacklens.adapter.out.textfilter.TomlFilterAdapter(
                resourceLoader =
                    org.springframework.core.io
                        .DefaultResourceLoader(),
                config = config,
                enabledProp = false,
            )
        adapter.knownFilters()

        val result = adapter.filter(matchingCommand, realisticMakeOutput, "")

        assertEquals(realisticMakeOutput, result.text)
        assertEquals("<disabled>", result.specName)
    }

    @Test
    fun `toggling the config at runtime changes the next filter call immediately`() {
        val config = ToggleableConfig(initial = true)
        val filter = TextFilterIntegrationFixture.createRealFilter(config)

        val enabled = filter.filter(matchingCommand, realisticMakeOutput, "").text
        config.setEnabled(false)
        val disabled = filter.filter(matchingCommand, realisticMakeOutput, "").text

        assertNotEquals(enabled, disabled, "toggling must affect the next call's output")
        assertEquals(realisticMakeOutput, disabled)
        assertTrue(enabled.length < realisticMakeOutput.length)

        config.setEnabled(true)
        val enabledAgain = filter.filter(matchingCommand, realisticMakeOutput, "").text
        assertEquals(enabled, enabledAgain)
    }
}

internal class ToggleableConfig(
    initial: Boolean,
) : TextFilterConfigPort {
    private var state: Boolean = initial
    override val enabled: Boolean get() = state

    override fun setEnabled(enabled: Boolean) {
        state = enabled
    }
}

internal object TextFilterIntegrationFixture {
    fun createRealFilter(
        config: TextFilterConfigPort?,
        enabledByDefault: Boolean = true,
    ): OutputFilterPort {
        val realConfig =
            config ?: object : TextFilterConfigPort {
                override val enabled: Boolean = enabledByDefault

                override fun setEnabled(enabled: Boolean) {}
            }
        val adapter =
            com.stacklens.adapter.out.textfilter.TomlFilterAdapter(
                resourceLoader =
                    org.springframework.core.io
                        .DefaultResourceLoader(),
                config = realConfig,
                enabledProp = enabledByDefault,
            )
        adapter.knownFilters()
        return adapter
    }
}

class TextFilterIntegrationSmokeTest {
    @Test
    fun `AgentPipelineRun can be created with a null repoPath`() {
        val run = AgentPipelineRun(id = 99L, rawText = "feature", repoPath = null, featureSlug = "slug", createdAt = System.currentTimeMillis())
        assertEquals(StageStatus.PENDING, run.rex.status)
    }

    @Test
    fun `FilterResult exposes a savingsPercent in the closed range`() {
        val r = FilterResult(text = "abc", originalChars = 100, filteredChars = 0, specName = "x")
        assertEquals(100, r.savingsPercent)
        val r2 = FilterResult(text = "abc", originalChars = 0, filteredChars = 0, specName = "x")
        assertEquals(0, r2.savingsPercent)
    }
}
