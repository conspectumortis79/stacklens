package com.stacklens.domain.pipeline

import com.stacklens.domain.pipeline.LoggingInputHeuristic.Reason
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LoggingInputHeuristicTest {
    @Test
    fun `single word input is classified as too thin`() {
        val verdict = LoggingInputHeuristic.classify("error")
        assertThat(verdict.tooThin).isTrue
        assertThat(verdict.nonBlankLineCount).isEqualTo(1)
        assertThat(verdict.hasStackTraceMarker).isFalse
        assertThat(verdict.hasIsoDate).isFalse
    }

    @Test
    fun `blank input is classified as too thin`() {
        val verdict = LoggingInputHeuristic.classify("   \n\n\t\n")
        assertThat(verdict.tooThin).isTrue
        assertThat(verdict.nonBlankLineCount).isEqualTo(0)
    }

    @Test
    fun `four non-blank lines without markers are still too thin`() {
        val text = "ERROR foo\nWARN bar\nINFO baz\nERROR qux"
        val verdict = LoggingInputHeuristic.classify(text)
        assertThat(verdict.tooThin).isTrue
        assertThat(verdict.nonBlankLineCount).isEqualTo(4)
    }

    @Test
    fun `five non-blank lines without markers bypass the heuristic`() {
        val text = "ERROR 1\nERROR 2\nERROR 3\nERROR 4\nERROR 5"
        val verdict = LoggingInputHeuristic.classify(text)
        assertThat(verdict.tooThin).isFalse
        assertThat(verdict.nonBlankLineCount).isEqualTo(5)
    }

    @Test
    fun `input with stack-trace marker bypasses the heuristic even when short`() {
        val verdict = LoggingInputHeuristic.classify("Caused by: java.lang.NullPointerException")
        assertThat(verdict.tooThin).isFalse
        assertThat(verdict.hasStackTraceMarker).isTrue
    }

    @Test
    fun `input with java source location bypasses the heuristic even when short`() {
        val verdict = LoggingInputHeuristic.classify("at com.example.X.y(X.java:42)")
        assertThat(verdict.tooThin).isFalse
        assertThat(verdict.hasStackTraceMarker).isTrue
    }

    @Test
    fun `input with python traceback bypasses the heuristic even when short`() {
        val verdict = LoggingInputHeuristic.classify("Traceback (most recent call last):")
        assertThat(verdict.tooThin).isFalse
        assertThat(verdict.hasStackTraceMarker).isTrue
    }

    @Test
    fun `input with a single ISO date bypasses the heuristic`() {
        val verdict = LoggingInputHeuristic.classify("2025-01-15 boom")
        assertThat(verdict.tooThin).isFalse
        assertThat(verdict.hasIsoDate).isTrue
    }

    @Test
    fun `reasons list always explains the verdict`() {
        val thin = LoggingInputHeuristic.classify("foo")

        assertThat(thin.reasons).containsExactly(
            Reason.LINE_COUNT_BELOW_MINIMUM,
            Reason.NO_STACK_TRACE_MARKER,
            Reason.NO_ISO_DATE,
        )
    }

    @Test
    fun `a rich input only reports the missing line count`() {
        val rich = LoggingInputHeuristic.classify("2025-01-15 ERROR\n  at com.x.Y.z(X.java:1)")

        assertThat(rich.tooThin).isFalse
        assertThat(rich.reasons).containsExactly(Reason.LINE_COUNT_BELOW_MINIMUM)
    }

    @Test
    fun `skip detection matches the language neutral tokens without any localized signature`() {
        assertThat(LoggingInputHeuristic.isStageSkipMarker("DIAGNOSIS_INPUT_TOO_THIN here", emptyList())).isTrue
        assertThat(LoggingInputHeuristic.isStageSkipMarker("says DIAGNOSIS_NOT_POSSIBLE", emptyList())).isTrue
    }

    @Test
    fun `skip detection matches localized signatures case insensitively`() {
        assertThat(LoggingInputHeuristic.isStageSkipMarker("log says OHNE ERGEBNIS", listOf("ohne Ergebnis"))).isTrue
        assertThat(LoggingInputHeuristic.isStageSkipMarker("a real diagnosis", listOf("ohne Ergebnis"))).isFalse
    }

    @Test
    fun `STAGE_SKIP_TOKEN is exposed for structured downstream consumers`() {
        assertThat(LoggingInputHeuristic.STAGE_SKIP_TOKEN).isEqualTo("DIAGNOSIS_INPUT_TOO_THIN")
    }
}
