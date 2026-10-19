package com.stacklens.adapter.out.gitlab

import com.stacklens.domain.model.GitlabConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContextMapAdapterTest {
    private fun adapter() =
        ContextMapAdapter(
            connectTimeoutMs = 1000,
            readTimeoutMs = 1000,
        )

    private fun cfg(
        active: Boolean = true,
        projectIds: String = "",
        groupId: String? = null,
    ) = GitlabConfig(
        name = "test",
        baseUrl = "https://gitlab.test",
        accessToken = "tok",
        active = active,
        projectIds = projectIds,
        groupId = groupId,
    )

    @Test
    fun `isContextFile matches puml files`() {
        assertTrue(adapter().isContextFile("docs/architecture.puml"))
    }

    @Test
    fun `isContextFile matches plantuml files`() {
        assertTrue(adapter().isContextFile("docs/architecture.plantuml"))
    }

    @Test
    fun `isContextFile is case-insensitive for puml`() {
        assertTrue(adapter().isContextFile("ARCH.PUML"))
    }

    @Test
    fun `isContextFile matches mmd files`() {
        assertTrue(adapter().isContextFile("diagrams/system.mmd"))
    }

    @Test
    fun `isContextFile matches context markdown files`() {
        assertTrue(adapter().isContextFile("docs/context-map.md"))
    }

    @Test
    fun `isContextFile matches context yaml files`() {
        assertTrue(adapter().isContextFile("docs/context.yaml"))
    }

    @Test
    fun `isContextFile matches architecture markdown files`() {
        assertTrue(adapter().isContextFile("docs/architecture-overview.md"))
    }

    @Test
    fun `isContextFile matches C4 markdown files`() {
        assertTrue(adapter().isContextFile("docs/C4-system.md"))
    }

    @Test
    fun `isContextFile matches C4 puml files`() {
        assertTrue(adapter().isContextFile("docs/C4-context.puml"))
    }

    @Test
    fun `isContextFile matches kontextmap files`() {
        assertTrue(adapter().isContextFile("docs/kontextmap-system.md"))
    }

    @Test
    fun `isContextFile matches system map files`() {
        assertTrue(adapter().isContextFile("docs/system-map.md"))
    }

    @Test
    fun `isContextFile matches system map puml files`() {
        assertTrue(adapter().isContextFile("docs/system-map.puml"))
    }

    @Test
    fun `isContextFile rejects regular source code files`() {
        assertFalse(adapter().isContextFile("src/main/Foo.kt"))
        assertFalse(adapter().isContextFile("README.md"))
        assertFalse(adapter().isContextFile("build.gradle.kts"))
    }

    @Test
    fun `isContextFile rejects context-named files without context extension`() {
        assertFalse(adapter().isContextFile("ContextFile.kt"))
    }

    @Test
    fun `extractComponent parses PlantUML component bracket notation`() {
        assertEquals("UserService", adapter().extractComponent("component [UserService]"))
    }

    @Test
    fun `extractComponent parses PlantUML component quoted notation`() {
        assertEquals("OrderService", adapter().extractComponent("component \"OrderService\""))
    }

    @Test
    fun `extractComponent parses system quoted notation case-insensitively`() {
        assertEquals("PaymentAPI", adapter().extractComponent("System \"PaymentAPI\""))
    }

    @Test
    fun `extractComponent parses C4 notation`() {
        assertEquals("MySystem", adapter().extractComponent("System(MySystem, \"My System\")"))
    }

    @Test
    fun `extractComponent returns null when no pattern matches`() {
        assertNull(adapter().extractComponent("Just some random text without any diagram syntax"))
    }

    @Test
    fun `extractComponent truncates very long component names to 80 chars`() {
        val longName = "x".repeat(200)
        val component = adapter().extractComponent("component [$longName]")
        assertEquals(80, component?.length)
    }

    @Test
    fun `findKeywordInContent returns matching lines with line numbers`() {
        val content =
            """
            line one
            contains Keyword here
            line three
            also Keyword at end
            """.trimIndent()
        val matches = adapter().findKeywordInContent(content, "Keyword")
        assertEquals(2, matches.size)
        assertEquals(2, matches[0].first)
        assertEquals(4, matches[1].first)
    }

    @Test
    fun `findKeywordInContent returns empty when keyword absent`() {
        val content = "no match here"
        assertTrue(adapter().findKeywordInContent(content, "absent").isEmpty())
    }

    @Test
    fun `findKeywordInContent handles empty content`() {
        assertTrue(adapter().findKeywordInContent("", "anything").isEmpty())
    }

    @Test
    fun `findKeywordInContent handles empty keyword returns all lines`() {
        val content = "line1\nline2"
        val matches = adapter().findKeywordInContent(content, "")
        assertEquals(2, matches.size)
    }

    @Test
    fun `searchKeyword returns empty when config is inactive`() {
        val result = adapter().searchKeyword(cfg(active = false), "kw", emptyList(), 1L)
        assertTrue(result.isEmpty())
    }
}
