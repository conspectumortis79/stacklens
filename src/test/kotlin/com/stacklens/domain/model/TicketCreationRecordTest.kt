package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TicketCreationRecordTest {
    @Test
    fun `default constructor populates sensible defaults`() {
        val rec = TicketCreationRecord()
        assertEquals(0L, rec.id)
        assertEquals("", rec.inputText)
        assertNull(rec.summary)
        assertNull(rec.issueType)
        assertNull(rec.labels)
        assertNull(rec.resultJson)
        assertEquals("COMPLETED", rec.status)
        assertNull(rec.errorMessage)
        assertNull(rec.jiraConfigId)
        assertNull(rec.jiraProjectKey)
        assertNull(rec.jiraKey)
        assertNull(rec.jiraUrl)
    }

    @Test
    fun `toMap returns expected default values`() {
        val rec = TicketCreationRecord()
        val map = rec.toMap()
        assertEquals(0L, map["id"])
        assertEquals("", map["inputText"])
        assertNull(map["summary"])
        assertNull(map["issueType"])
        assertNull(map["labels"])
        assertNull(map["resultJson"])
        assertEquals("COMPLETED", map["status"])
        assertNull(map["errorMessage"])
        assertNull(map["jiraConfigId"])
        assertNull(map["jiraProjectKey"])
        assertNull(map["jiraKey"])
        assertNull(map["jiraUrl"])
    }

    @Test
    fun `toMap with all populated fields`() {
        val rec =
            TicketCreationRecord(
                id = 1L,
                inputText = "User request",
                summary = "Title",
                issueType = "Story",
                labels = "label1,label2",
                resultJson = """{"x":1}""",
                status = "COMPLETED",
                errorMessage = null,
                jiraConfigId = 5L,
                jiraProjectKey = "PROJ",
                jiraKey = "PROJ-1",
                jiraUrl = "https://jira/PROJ-1",
            )
        val map = rec.toMap()
        assertEquals(1L, map["id"])
        assertEquals("User request", map["inputText"])
        assertEquals("Title", map["summary"])
        assertEquals("Story", map["issueType"])
        assertEquals("label1,label2", map["labels"])
        assertEquals("""{"x":1}""", map["resultJson"])
        assertEquals("COMPLETED", map["status"])
        assertEquals(5L, map["jiraConfigId"])
        assertEquals("PROJ", map["jiraProjectKey"])
        assertEquals("PROJ-1", map["jiraKey"])
        assertEquals("https://jira/PROJ-1", map["jiraUrl"])
    }
}
