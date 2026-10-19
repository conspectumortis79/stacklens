package com.stacklens.adapter.out.compiler

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KotlincResolverCompanionTest {
    @Test
    fun `the system environment reads the real process variables`() {
        assertTrue(
            KotlincResolver.SystemKotlincEnvironment
                .get("PATH")
                .orEmpty()
                .isNotBlank(),
        )
        assertNull(KotlincResolver.SystemKotlincEnvironment.get("STACKLENS_NO_SUCH_VARIABLE"))
    }
}
