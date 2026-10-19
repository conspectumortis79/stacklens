package com.stacklens.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class IssueTypeTest {
    @Test
    fun `every enum entry has a non-blank displayName`() {
        IssueType.entries.forEach { type ->
            assert(type.displayName.isNotBlank()) { "IssueType.${type.name} has a blank displayName" }
        }
    }

    @Test
    fun `every enum entry has a non-blank promptKey`() {
        IssueType.entries.forEach { type ->
            assert(type.promptKey.isNotBlank()) { "IssueType.${type.name} has a blank promptKey" }
        }
    }

    @Test
    fun `every promptKey lives in the ticketcreation namespace`() {
        IssueType.entries.forEach { type ->
            assert(type.promptKey.startsWith("ticketcreation.")) {
                "IssueType.${type.name}.promptKey=${type.promptKey} must start with 'ticketcreation.'"
            }
        }
    }

    @Test
    fun `promptKeys are unique across entries`() {
        val keys = IssueType.entries.map { it.promptKey }
        assertEquals(keys.size, keys.toSet().size) { "Duplicate promptKey: $keys" }
    }

    @Test
    fun `STORY uses the user-story prompt key`() {
        assertEquals("ticketcreation.create_story", IssueType.STORY.promptKey)
        assertEquals("Story", IssueType.STORY.displayName)
    }

    @Test
    fun `TECHNICAL_TASK uses the technical-task prompt key`() {
        assertEquals("ticketcreation.create_technical_task", IssueType.TECHNICAL_TASK.promptKey)
        assertEquals("Technical Task", IssueType.TECHNICAL_TASK.displayName)
    }

    @Test
    fun `BUG uses the bug-report prompt key`() {
        assertEquals("ticketcreation.create_bug", IssueType.BUG.promptKey)
        assertEquals("Bug", IssueType.BUG.displayName)
    }

    @Test
    fun `DEFAULT is STORY for backwards compatibility`() {
        assertSame(IssueType.STORY, IssueType.DEFAULT)
    }

    @Test
    fun `fromString resolves the displayName case-insensitively`() {
        assertSame(IssueType.STORY, IssueType.fromString("Story"))
        assertSame(IssueType.STORY, IssueType.fromString("story"))
        assertSame(IssueType.STORY, IssueType.fromString("STORY"))
        assertSame(IssueType.TECHNICAL_TASK, IssueType.fromString("Technical Task"))
        assertSame(IssueType.TECHNICAL_TASK, IssueType.fromString("technical task"))
        assertSame(IssueType.BUG, IssueType.fromString("Bug"))
        assertSame(IssueType.BUG, IssueType.fromString("bug"))
        assertSame(IssueType.BUG, IssueType.fromString("BUG"))
    }

    @Test
    fun `fromString resolves the enum-name case-insensitively`() {
        assertSame(IssueType.STORY, IssueType.fromString("STORY"))
        assertSame(IssueType.TECHNICAL_TASK, IssueType.fromString("TECHNICAL_TASK"))
        assertSame(IssueType.BUG, IssueType.fromString("BUG"))
    }

    @Test
    fun `fromString trims surrounding whitespace`() {
        assertSame(IssueType.STORY, IssueType.fromString("  Story  "))
        assertSame(IssueType.TECHNICAL_TASK, IssueType.fromString("\tTechnical Task\n"))
    }

    @Test
    fun `fromString returns null for null input`() {
        assertNull(IssueType.fromString(null))
    }

    @Test
    fun `fromString returns null for blank input`() {
        assertNull(IssueType.fromString(""))
        assertNull(IssueType.fromString("   "))
        assertNull(IssueType.fromString("\t\n"))
    }

    @Test
    fun `fromString returns null for unknown values`() {
        assertNull(IssueType.fromString("Epic"))
        assertNull(IssueType.fromString("Subtask"))
        assertNull(IssueType.fromString("Feature"))
        assertNull(IssueType.fromString("storyy"))
    }

    @Test
    fun `all entries are reachable from fromString via displayName`() {
        IssueType.entries.forEach { entry ->
            val parsed = IssueType.fromString(entry.displayName)
            assertNotNull(parsed, "Display name '${entry.displayName}' must be parseable")
            assertSame(entry, parsed)
        }
    }

    @Test
    fun `all entries are reachable from fromString via enum name`() {
        IssueType.entries.forEach { entry ->
            val parsed = IssueType.fromString(entry.name)
            assertNotNull(parsed, "Enum name '${entry.name}' must be parseable")
            assertSame(entry, parsed)
        }
    }
}
