package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConfluenceConfigTest {
    private fun config(
        spaceKeys: String? = null,
        cloudMode: Boolean = true,
        username: String = "user",
        apiToken: String = "token",
    ) = ConfluenceConfig(
        name = "conf",
        baseUrl = "https://company.atlassian.net",
        username = username,
        apiToken = apiToken,
        spaceKeys = spaceKeys,
        cloudMode = cloudMode,
    )

    @Test
    fun `spaceKeyList returns empty list when spaceKeys is null`() {
        assertEquals(emptyList<String>(), config(spaceKeys = null).spaceKeyList())
    }

    @Test
    fun `spaceKeyList returns empty list when spaceKeys is blank`() {
        assertEquals(emptyList<String>(), config(spaceKeys = "  ").spaceKeyList())
    }

    @Test
    fun `spaceKeyList returns single space key`() {
        assertEquals(listOf("DEV"), config(spaceKeys = "DEV").spaceKeyList())
    }

    @Test
    fun `spaceKeyList returns multiple space keys and trims whitespace`() {
        assertEquals(listOf("DEV", "OPS", "ARCH"), config(spaceKeys = "DEV, OPS , ARCH").spaceKeyList())
    }

    @Test
    fun `spaceKeyList filters out blank entries`() {
        assertEquals(listOf("DEV", "ARCH"), config(spaceKeys = "DEV,,ARCH").spaceKeyList())
    }

    @Test
    fun `default cloudMode is true`() {
        assertEquals(true, config().cloudMode)
    }

    @Test
    fun `default active is true`() {
        assertEquals(true, config().active)
    }
}
