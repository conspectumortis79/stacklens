package com.stacklens.adapter.out.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.mcp.McpAwareJiraSprintFetchAdapter.Companion.BOARD_ID_REGEX
import com.stacklens.adapter.out.mcp.McpAwareJiraSprintFetchAdapter.Companion.SPRINT_ID_REGEX
import com.stacklens.adapter.out.mcp.McpAwareJiraSprintFetchAdapter.Companion.SPRINT_PARAM_REGEX
import com.stacklens.shared.sprintIdFromRegex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class McpAwareJiraSprintFetchAdapterExtractTest {
    private val adapter =
        McpAwareJiraSprintFetchAdapter(
            mcpRepo = mock(),
            gateway = mock(),
            objectMapper = ObjectMapper(),
            delegate = mock(),
        )

    @Test
    fun `extractSprintIdFromRegex returns null when regex does not match`() {
        val result = sprintIdFromRegex(SPRINT_ID_REGEX, "https://example.com/other")
        assertNull(result)
    }

    @Test
    fun `extractSprintIdFromRegex returns the captured id when regex matches`() {
        val result = sprintIdFromRegex(SPRINT_ID_REGEX, "https://example.com/sprints/42")
        assertEquals(42L, result)
    }

    @Test
    fun `extractSprintIdFromRegex returns null when capture group is non-numeric`() {
        val result = sprintIdFromRegex(SPRINT_ID_REGEX, "https://example.com/abc")
        assertNull(result)
    }

    @Test
    fun `SPRINT_PARAM_REGEX companion constant is non-null and matchable`() {
        assertNotNull(SPRINT_PARAM_REGEX)
        assertEquals("42", SPRINT_PARAM_REGEX.find("?sprint=42")?.groupValues?.get(1))
    }

    @Test
    fun `BOARD_ID_REGEX companion constant is non-null and matchable`() {
        assertNotNull(BOARD_ID_REGEX)
        assertEquals("7", BOARD_ID_REGEX.find("/board/7")?.groupValues?.get(1))
    }
}
