package com.stacklens.systemtest

import com.stacklens.application.service.logging.LoggingFixMarkers
import com.stacklens.domain.pipeline.LoggingInputHeuristic
import com.stacklens.testsupport.catalogBackedLoggingFixMarkers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LoggingInputHeuristicSystemTest {
    private val markers: LoggingFixMarkers = catalogBackedLoggingFixMarkers()

    @Test
    fun `the german skip marker in the catalog is identical to the one embedded in both prompt files`() {
        assertThat(prompt("de")).contains(markers.skipMarker("de"))
        assertThat(prompt("en")).contains(markers.skipMarker("de"))
    }

    @Test
    fun `heuristic recognises JVM stack-trace frames in the middle of an input`() {
        val input =
            """
            2025-01-15 10:00:00 ERROR Something failed
            ${"\t"}at com.stacklens.x.Y.z(Y.java:42)
            ${"\t"}at com.stacklens.a.B.c(C.java:17)
            """.trimIndent()
        val verdict = LoggingInputHeuristic.classify(input)
        assertThat(verdict.tooThin).isFalse
        assertThat(verdict.hasStackTraceMarker).isTrue
    }

    @Test
    fun `a too-thin input is reported with the translated skip marker of the run language`() {
        val verdict = LoggingInputHeuristic.classify("")

        assertThat(verdict.tooThin).isTrue
        assertThat(verdict.nonBlankLineCount).isEqualTo(0)
        assertThat(markers.isSkipMarker(markers.skipMarker("de"))).isTrue
        assertThat(markers.isSkipMarker(markers.skipMarker("en"))).isTrue
    }

    private fun prompt(language: String): String {
        val classLoader = LoggingInputHeuristicSystemTest::class.java.classLoader
        return classLoader
            .getResourceAsStream("prompts/$language/logging.md")
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("prompts/$language/logging.md not found on classpath")
    }
}
