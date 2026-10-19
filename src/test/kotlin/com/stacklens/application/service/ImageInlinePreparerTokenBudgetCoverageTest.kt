package com.stacklens.application.service

import com.stacklens.adapter.out.image.ImageInlinePreparer
import com.stacklens.testsupport.PrivateInvocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImageInlinePreparerTokenBudgetCoverageTest {
    private val preparer =
        ImageInlinePreparer(
            com.stacklens.adapter.out.image
                .DefaultInlineImageEncoder(),
        )

    @Test
    fun `a picture within the token budget keeps the full dimension`() {
        assertEquals(1568, target(width = 1024, height = 768, tokens = 150_000))
    }

    @Test
    fun `a picture over the token budget shrinks its largest dimension`() {
        val scaled = target(width = 4096, height = 4096, tokens = 800_000) as Int

        assertTrue(scaled < 1568, "expected a shrink below 1568 but was $scaled")
        assertTrue(scaled >= 512, "expected a floor at 512 but was $scaled")
    }

    private fun target(
        width: Int,
        height: Int,
        tokens: Int,
    ): Any? = PrivateInvocation.invoke(preparer, "computeTargetDimension", width, height, tokens)
}
