package com.stacklens.adapter.out.textfilter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TextFilterPropertiesTest {
    @Test
    fun `the annotated property expression matches the centralized key and default`() {
        assertNotNull(TextFilterProperties)
        assertEquals(
            "\${" + TextFilterProperties.ENABLED_KEY + ":" + TextFilterProperties.ENABLED_DEFAULT + "}",
            TextFilterProperties.ENABLED_VALUE,
        )
        assertTrue(TextFilterProperties.ENABLED_VALUE.contains(TextFilterProperties.ENABLED_KEY))
    }
}
