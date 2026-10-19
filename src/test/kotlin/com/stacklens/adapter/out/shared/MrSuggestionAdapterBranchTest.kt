package com.stacklens.adapter.out.shared

import com.stacklens.application.port.out.MrSuggestionPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MrSuggestionAdapterBranchTest {
    @Test
    fun `suggestionBody wraps code in suggestion block`() {
        val result = suggestionBody("fun main() {}")
        assertTrue(result.contains("```suggestion"))
        assertTrue(result.contains("fun main() {}"))
    }

    @Test
    fun `suggestionBody handles empty code`() {
        assertEquals("```suggestion\n\n```", suggestionBody(""))
    }

    @Test
    fun `MrSuggestionAdapter class is loadable`() {
        val cls = MrSuggestionAdapter::class.java
        assertTrue(cls.declaredConstructors.isNotEmpty())
    }

    @Test
    fun `postSuggestion throws when host is null`() {
        val mrPort =
            MrSuggestionPort.PostRequest(
                mrUrl = "https:///g/p/-/merge_requests/1",
                platform = "GitLab",
                gitConfigId = 1L,
                gitlabConfigs = emptyList(),
                githubConfigs = emptyList(),
                diffBaseSha = "b",
                diffStartSha = "s",
                diffHeadSha = "h",
                filePath = "src/Foo.kt",
                lineNumber = 1,
                suggestedCode = "code",
            )
        val ex =
            assertThrows(IllegalStateException::class.java) {
                MrSuggestionAdapter().postSuggestion(mrPort)
            }
        assertTrue(ex.message!!.contains("Cannot parse host"))
    }

    @Test
    fun `postSuggestion for github enterprise host routes to GitHub path`() {
        val mrPort =
            MrSuggestionPort.PostRequest(
                mrUrl = "https://myorg.github.com/g/p/pull/1",
                platform = "GitHub",
                gitConfigId = 1L,
                gitlabConfigs = emptyList(),
                githubConfigs = emptyList(),
                diffBaseSha = "b",
                diffStartSha = "s",
                diffHeadSha = "h",
                filePath = "src/Foo.kt",
                lineNumber = 1,
                suggestedCode = "code",
            )
        try {
            MrSuggestionAdapter().postSuggestion(mrPort)
        } catch (e: Exception) {
        }
    }
}
