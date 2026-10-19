package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TicketRefinementTest {
    @Test
    fun `default constructor populates sensible defaults`() {
        val r = TicketRefinement(parentTicketKey = "PROJ-1")
        assertEquals(0L, r.id)
        assertEquals("PROJ-1", r.parentTicketKey)
        assertNull(r.jiraConfigId)
        assertNull(r.templateOverride)
        assertTrue(r.singleTicketOnly)
        assertEquals("PENDING", r.status)
        assertNull(r.currentStep)
        assertNull(r.errorMessage)
        assertNull(r.parentTicketJson)
        assertNull(r.reviewsJson)
        assertNull(r.repoSelectionsJson)
        assertNull(r.databaseConfigId)
        assertNull(r.databaseConfigIds)
        assertNull(r.subtasksCreatedJson)
        assertNull(r.completedAt)
    }

    @Test
    fun `named constructor populates all fields`() {
        val r =
            TicketRefinement(
                id = 1L,
                parentTicketKey = "P",
                jiraConfigId = 2L,
                templateOverride = "to",
                singleTicketOnly = false,
                status = "COMPLETED",
                currentStep = "step",
                errorMessage = "err",
                parentTicketJson = "{}",
                reviewsJson = "[]",
                repoSelectionsJson = "[]",
                databaseConfigId = 3L,
                databaseConfigIds = "3,4",
                subtasksCreatedJson = "{}",
            )
        assertEquals(1L, r.id)
        assertEquals(2L, r.jiraConfigId)
        assertEquals("to", r.templateOverride)
        assertFalse(r.singleTicketOnly)
        assertEquals("COMPLETED", r.status)
        assertEquals("step", r.currentStep)
        assertEquals("err", r.errorMessage)
        assertEquals("{}", r.parentTicketJson)
        assertEquals("[]", r.reviewsJson)
        assertEquals("[]", r.repoSelectionsJson)
        assertEquals(3L, r.databaseConfigId)
        assertEquals("3,4", r.databaseConfigIds)
        assertEquals("{}", r.subtasksCreatedJson)
    }
}
