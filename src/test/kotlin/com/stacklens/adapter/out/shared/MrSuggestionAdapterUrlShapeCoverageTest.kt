package com.stacklens.adapter.out.shared

import com.stacklens.application.port.out.MrSuggestionPort
import com.stacklens.testsupport.PrivateInvocation
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI

class MrSuggestionAdapterUrlShapeCoverageTest {
    private val request =
        MrSuggestionPort.PostRequest(
            mrUrl = "http://gitlab.example/group/-/merge_requests/42",
            platform = "gitlab",
            gitConfigId = null,
            gitlabConfigs = emptyList(),
            githubConfigs = emptyList(),
            diffBaseSha = "base",
            diffStartSha = "start",
            diffHeadSha = "head",
            filePath = "a/B.kt",
            lineNumber = 3,
            suggestedCode = "code",
        )

    private fun reject(url: String): String {
        val failure =
            assertThrows(IllegalStateException::class.java) {
                PrivateInvocation.invoke(GitlabMrSuggestionAdapter(), "post", request, URI(url))
            }
        return failure.message.orEmpty()
    }

    @Test
    fun `a url without a path is rejected`() {
        assertTrue(reject("http://gitlab.example").contains("No path"), reject("http://gitlab.example"))
    }

    @Test
    fun `a url without a merge request marker is rejected`() {
        assertTrue(reject("http://gitlab.example/group/-/issues").contains("Cannot parse MR IID"))
    }

    @Test
    fun `a merge request number that overflows is rejected`() {
        assertTrue(reject("http://gitlab.example/group/-/merge_requests/99999999999999999999").contains("Cannot parse MR IID"))
    }
}
