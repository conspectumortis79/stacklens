package com.stacklens.adapter.inbound.rest.knowledge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.NoSuchElementException

class KnowledgeExceptionAdviceCoverageTest {
    private val advice = KnowledgeExceptionAdvice()

    @Test
    fun `missing entries are reported with the original message`() {
        val response = advice.handleNotFound(NoSuchElementException("knowledge source 3 is gone"))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("knowledge source 3 is gone", response.body?.getValue("error"))
    }

    @Test
    fun `missing entries without a message fall back to a generic text`() {
        val response = advice.handleNotFound(NoSuchElementException())

        assertEquals("Not found", response.body?.getValue("error"))
    }

    @Test
    fun `bad requests keep the rejection reason or use the fallback`() {
        assertEquals("slug must not be blank", advice.handleBadRequest(IllegalArgumentException("slug must not be blank")).body?.getValue("error"))
        assertEquals("Invalid request", advice.handleBadRequest(IllegalArgumentException()).body?.getValue("error"))
    }

    @Test
    fun `unavailable services keep the failure reason or use the fallback`() {
        assertEquals("index is busy", advice.handleUnavailable(IllegalStateException("index is busy")).body?.getValue("error"))
        assertEquals("Not available", advice.handleUnavailable(IllegalStateException()).body?.getValue("error"))
    }
}
