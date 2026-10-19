package com.stacklens.application.service.ticketrefinement

import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.TicketRefinementPort
import com.stacklens.domain.model.TicketRefinement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class TicketRefinementSubtaskLedgerCoverageTest {
    private val repo = mock<TicketRefinementPort>()
    private val jsonCodec = mock<JsonCodecPort>()
    private val ledger = TicketRefinementSubtaskLedger(repo, jsonCodec)

    private fun entityWith(json: String?): TicketRefinement = TicketRefinement(parentTicketKey = "PARENT-1", subtasksCreatedJson = json)

    @Test
    fun `starts from an empty ledger when the stored json is blank`() {
        whenever(jsonCodec.encode(any())).thenReturn("ENCODED")
        val entity = entityWith("   ")

        ledger.mergeAndPersist(entity, "PARENT-1", listOf(mapOf("summary" to "Sub one"), mapOf("summary" to "Sub two")))

        assertEquals("ENCODED", entity.subtasksCreatedJson)
        verify(repo).saveTicketRefinement(entity)
    }

    @Test
    fun `keeps previously created subtasks and drops unparsable entries`() {
        whenever(jsonCodec.decodeMap("STORED")).thenReturn(
            mapOf(
                "PARENT-1" to listOf("Existing"),
                "PARENT-2" to "not-a-list",
                "PARENT-3" to listOf("mixed", 42),
                "PARENT-4" to null,
            ),
        )
        whenever(jsonCodec.encode(any())).thenReturn("ENCODED")
        val entity = entityWith("STORED")

        ledger.mergeAndPersist(entity, "PARENT-1", listOf(mapOf("summary" to "Existing"), mapOf("summary" to "New")))

        verify(jsonCodec).encode(mapOf("PARENT-1" to listOf("Existing", "New")))
        assertEquals("ENCODED", entity.subtasksCreatedJson)
    }

    @Test
    fun `adds a new ticket key when the ledger has no entry for it`() {
        whenever(jsonCodec.decodeMap("STORED")).thenReturn(mapOf("OTHER-1" to listOf("Done")))
        whenever(jsonCodec.encode(any())).thenReturn("ENCODED")
        val entity = entityWith("STORED")

        ledger.mergeAndPersist(entity, "PARENT-9", listOf(mapOf("summary" to "Fresh"), mapOf("detail" to "no summary")))

        verify(jsonCodec).encode(mapOf("OTHER-1" to listOf("Done"), "PARENT-9" to listOf("Fresh")))
    }

    @Test
    fun `ignores a corrupt stored ledger instead of failing`() {
        whenever(jsonCodec.decodeMap("BOOM")).thenThrow(IllegalArgumentException("not json"))
        whenever(jsonCodec.encode(any())).thenReturn("ENCODED")
        val entity = entityWith("BOOM")

        ledger.mergeAndPersist(entity, "PARENT-1", listOf(mapOf("summary" to "Only")))

        verify(jsonCodec).encode(mapOf("PARENT-1" to listOf("Only")))
        assertEquals("ENCODED", entity.subtasksCreatedJson)
    }

    @Test
    fun `stores nothing when encoding the ledger fails`() {
        whenever(jsonCodec.decodeMap("STORED")).thenReturn(mapOf("PARENT-1" to listOf("Existing")))
        whenever(jsonCodec.encode(any())).thenThrow(IllegalStateException("serializer down"))
        val entity = entityWith("STORED")

        ledger.mergeAndPersist(entity, "PARENT-1", emptyList())

        assertNull(entity.subtasksCreatedJson)
        verify(repo).saveTicketRefinement(entity)
    }
}
