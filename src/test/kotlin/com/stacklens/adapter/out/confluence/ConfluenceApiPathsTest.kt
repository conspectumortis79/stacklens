package com.stacklens.adapter.out.confluence

import com.stacklens.domain.model.ConfluenceConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConfluenceApiPathsTest {
    private fun config(cloudMode: Boolean) =
        ConfluenceConfig(
            name = "conf",
            baseUrl = "https://confluence.example.com",
            username = "u",
            apiToken = "t",
            cloudMode = cloudMode,
        )

    @Test
    fun `returns the wiki path for Cloud mode`() {
        assertEquals("/wiki/rest/api/content/search", contentSearchPath(config(cloudMode = true)))
    }

    @Test
    fun `returns the non-wiki path for Server mode`() {
        assertEquals("/rest/api/content/search", contentSearchPath(config(cloudMode = false)))
    }
}
