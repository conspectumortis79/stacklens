package com.stacklens.adapter.out.jira

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TicketCreationNativeJiraAdapterHelpersTest {
    private val adapter =
        TicketCreationNativeJiraAdapter(
            jiraTicketHttp = mock(),
        )

    private fun mock() = org.mockito.kotlin.mock<JiraTicketHttpClient>()

    @Test
    fun `buildFieldsMap includes project, summary and issuetype`() {
        val fields =
            adapter.buildFieldsMap(
                projectKey = "PROJ",
                summary = "Title",
                description = "",
                issueType = "Bug",
                labels = emptyList(),
                priority = "",
                cloudMode = false,
            )
        assertEquals(mapOf("key" to "PROJ"), fields["project"])
        assertEquals("Title", fields["summary"])
        assertEquals(mapOf("name" to "Bug"), fields["issuetype"])
    }

    @Test
    fun `buildFieldsMap skips description when blank`() {
        val fields =
            adapter.buildFieldsMap(
                projectKey = "P",
                summary = "S",
                description = "   ",
                issueType = "Task",
                labels = emptyList(),
                priority = "",
                cloudMode = true,
            )
        assertFalse(fields.containsKey("description"))
    }

    @Test
    fun `buildFieldsMap adds ADF description for Cloud mode`() {
        val fields =
            adapter.buildFieldsMap(
                projectKey = "P",
                summary = "S",
                description = "Hello world",
                issueType = "Task",
                labels = emptyList(),
                priority = "",
                cloudMode = true,
            )
        val desc = fields["description"]
        assertNotNull(desc)
        assertTrue(desc is Map<*, *>)
        assertEquals("doc", (desc as Map<*, *>)["type"])
    }

    @Test
    fun `buildFieldsMap adds wiki markup description for Server mode`() {
        val fields =
            adapter.buildFieldsMap(
                projectKey = "P",
                summary = "S",
                description = "Hello world",
                issueType = "Task",
                labels = emptyList(),
                priority = "",
                cloudMode = false,
            )
        val desc = fields["description"]
        assertNotNull(desc)
        assertEquals("Hello world", desc)
    }

    @Test
    fun `buildFieldsMap adds labels when non-empty`() {
        val fields =
            adapter.buildFieldsMap(
                projectKey = "P",
                summary = "S",
                description = "",
                issueType = "Task",
                labels = listOf("backend", "urgent"),
                priority = "",
                cloudMode = false,
            )
        assertEquals(listOf("backend", "urgent"), fields["labels"])
    }

    @Test
    fun `buildFieldsMap adds priority when non-blank`() {
        val fields =
            adapter.buildFieldsMap(
                projectKey = "P",
                summary = "S",
                description = "",
                issueType = "Task",
                labels = emptyList(),
                priority = "High",
                cloudMode = false,
            )
        assertEquals(mapOf("name" to "High"), fields["priority"])
    }

    @Test
    fun `buildFieldsMap skips priority when blank`() {
        val fields =
            adapter.buildFieldsMap(
                projectKey = "P",
                summary = "S",
                description = "",
                issueType = "Task",
                labels = emptyList(),
                priority = "",
                cloudMode = false,
            )
        assertFalse(fields.containsKey("priority"))
    }

    @Test
    fun `markdownToAdf returns doc with version 1`() {
        val doc = adapter.markdownToAdf("Hello")
        assertEquals("doc", doc["type"])
        assertEquals(1, doc["version"])
    }

    @Test
    fun `markdownToAdf converts h1 heading`() {
        val doc = adapter.markdownToAdf("# Title")
        val content = doc["content"] as List<*>
        val first = content.first() as Map<*, *>
        assertEquals("heading", first["type"])
        assertEquals(1, (first["attrs"] as Map<*, *>)["level"])
    }

    @Test
    fun `markdownToAdf converts h2 heading`() {
        val doc = adapter.markdownToAdf("## Subtitle")
        val content = doc["content"] as List<*>
        val first = content.first() as Map<*, *>
        assertEquals("heading", first["type"])
        assertEquals(2, (first["attrs"] as Map<*, *>)["level"])
    }

    @Test
    fun `markdownToAdf converts h3 heading`() {
        val doc = adapter.markdownToAdf("### Subsub")
        val content = doc["content"] as List<*>
        val first = content.first() as Map<*, *>
        assertEquals("heading", first["type"])
        assertEquals(3, (first["attrs"] as Map<*, *>)["level"])
    }

    @Test
    fun `markdownToAdf converts --- to rule`() {
        val doc = adapter.markdownToAdf("---")
        val content = doc["content"] as List<*>
        val first = content.first() as Map<*, *>
        assertEquals("rule", first["type"])
    }

    @Test
    fun `markdownToAdf converts triple-asterisk to rule`() {
        val doc = adapter.markdownToAdf("***")
        val content = doc["content"] as List<*>
        val first = content.first() as Map<*, *>
        assertEquals("rule", first["type"])
    }

    @Test
    fun `markdownToAdf converts dash bullet list`() {
        val doc = adapter.markdownToAdf("- Item 1\n- Item 2")
        val content = doc["content"] as List<*>
        val list = content.first() as Map<*, *>
        assertEquals("bulletList", list["type"])
        assertEquals(2, (list["content"] as List<*>).size)
    }

    @Test
    fun `markdownToAdf converts asterisk bullet list`() {
        val doc = adapter.markdownToAdf("* Item")
        val content = doc["content"] as List<*>
        val list = content.first() as Map<*, *>
        assertEquals("bulletList", list["type"])
    }

    @Test
    fun `markdownToAdf converts ordered list`() {
        val doc = adapter.markdownToAdf("1. First\n2. Second")
        val content = doc["content"] as List<*>
        val list = content.first() as Map<*, *>
        assertEquals("orderedList", list["type"])
        assertEquals(2, (list["content"] as List<*>).size)
    }

    @Test
    fun `markdownToAdf adds empty paragraph when content is empty`() {
        val doc = adapter.markdownToAdf("")
        val content = doc["content"] as List<*>
        assertEquals(1, content.size)
        val first = content.first() as Map<*, *>
        assertEquals("paragraph", first["type"])
    }

    @Test
    fun `markdownToAdf strips unchecked checkbox marker`() {
        val doc = adapter.markdownToAdf("- [ ] Task")
        val content = doc["content"] as List<*>
        val list = content.first() as Map<*, *>
        val firstItem = (list["content"] as List<*>).first() as Map<*, *>
        val itemContent = (firstItem["content"] as List<*>).first() as Map<*, *>
        val inlineText = (itemContent["content"] as List<*>).first() as Map<*, *>
        assertEquals("Task", inlineText["text"])
    }

    @Test
    fun `markdownToAdf strips checked checkbox marker`() {
        val doc = adapter.markdownToAdf("- [x] Done")
        val content = doc["content"] as List<*>
        val list = content.first() as Map<*, *>
        val firstItem = (list["content"] as List<*>).first() as Map<*, *>
        val itemContent = (firstItem["content"] as List<*>).first() as Map<*, *>
        val inlineText = (itemContent["content"] as List<*>).first() as Map<*, *>
        assertEquals("Done", inlineText["text"])
    }

    @Test
    fun `markdownToAdf flushes bullets on blank line`() {
        val doc = adapter.markdownToAdf("- A\n\n- B")
        val content = doc["content"] as List<*>
        assertEquals(2, content.size)
    }

    @Test
    fun `inlineToAdf returns plain text node for unmarked text`() {
        val nodes = adapter.inlineToAdf("Hello world")
        assertEquals(1, nodes.size)
        assertEquals("Hello world", nodes.first()["text"])
    }

    @Test
    fun `inlineToAdf wraps bold text in strong mark`() {
        val nodes = adapter.inlineToAdf("Hello **bold** end")
        val strong = nodes.find { (it["marks"] as? List<*>)?.contains(mapOf("type" to "strong")) == true }
        assertNotNull(strong)
        assertEquals("bold", strong!!["text"])
    }

    @Test
    fun `inlineToAdf wraps inline code in code mark`() {
        val nodes = adapter.inlineToAdf("Use `Foo.bar()` here")
        val code = nodes.find { (it["marks"] as? List<*>)?.contains(mapOf("type" to "code")) == true }
        assertNotNull(code)
        assertEquals("Foo.bar()", code!!["text"])
    }

    @Test
    fun `inlineToAdf wraps italic text in em mark`() {
        val nodes = adapter.inlineToAdf("This is _italic_ end")
        val em = nodes.find { (it["marks"] as? List<*>)?.contains(mapOf("type" to "em")) == true }
        assertNotNull(em)
        assertEquals("italic", em!!["text"])
    }

    @Test
    fun `inlineToAdf includes text after the last match`() {
        val nodes = adapter.inlineToAdf("**bold** then plain")
        val last = nodes.last()
        assertEquals("text", last["type"])
        assertEquals(" then plain", last["text"])
    }

    @Test
    fun `inlineToAdf includes text before the first match`() {
        val nodes = adapter.inlineToAdf("preface **bold** end")
        val first = nodes.first()
        assertEquals("text", first["type"])
        assertEquals("preface ", first["text"])
    }

    @Test
    fun `inlineToAdf handles all three inline mark types in one string`() {
        val nodes = adapter.inlineToAdf("**B** `C` _I_")

        @Suppress("UNCHECKED_CAST")
        val marks = nodes.flatMap { (it["marks"] as? List<Map<*, *>> ?: emptyList()) }.map { it["type"] }
        assertTrue(marks.contains("strong"))
        assertTrue(marks.contains("code"))
        assertTrue(marks.contains("em"))
    }

    @Test
    fun `inlineToAdf returns a single text node when nothing matches`() {
        val nodes = adapter.inlineToAdf("")
        assertEquals(1, nodes.size)
        assertEquals("text", nodes[0]["type"])
    }

    @Test
    fun `markdownToWikiMarkup maps h1 to the h2 level used across jira flows`() {
        val result = adapter.markdownToWikiMarkup("# Title")
        assertEquals("h2. Title", result)
    }

    @Test
    fun `markdownToWikiMarkup converts h2`() {
        val result = adapter.markdownToWikiMarkup("## Subtitle")
        assertEquals("h2. Subtitle", result)
    }

    @Test
    fun `markdownToWikiMarkup converts h3`() {
        val result = adapter.markdownToWikiMarkup("### Subsub")
        assertEquals("h3. Subsub", result)
    }

    @Test
    fun `markdownToWikiMarkup converts --- to rule`() {
        val result = adapter.markdownToWikiMarkup("---")
        assertEquals("----", result)
    }

    @Test
    fun `markdownToWikiMarkup converts triple-asterisk to rule`() {
        val result = adapter.markdownToWikiMarkup("***")
        assertEquals("----", result)
    }

    @Test
    fun `markdownToWikiMarkup converts dash bullet to asterisk bullet`() {
        val result = adapter.markdownToWikiMarkup("- Item")
        assertEquals("* Item", result)
    }

    @Test
    fun `markdownToWikiMarkup preserves asterisk bullet`() {
        val result = adapter.markdownToWikiMarkup("* Item")
        assertEquals("* Item", result)
    }

    @Test
    fun `markdownToWikiMarkup converts ordered list to hash item`() {
        val result = adapter.markdownToWikiMarkup("1. First")
        assertEquals("# First", result)
    }

    @Test
    fun `markdownToWikiMarkup converts blockquote to quote block`() {
        val result = adapter.markdownToWikiMarkup("> quoted")
        assertEquals("{quote}quoted{quote}", result)
    }

    @Test
    fun `markdownToWikiMarkup leaves plain text unchanged`() {
        val result = adapter.markdownToWikiMarkup("Just text")
        assertEquals("Just text", result)
    }

    @Test
    fun `buildSubtaskFieldsMap includes project summary issuetype and parent key`() {
        val fields =
            adapter.buildSubtaskFieldsMap(
                parentKey = "PROJ-1",
                projectKey = "PROJ",
                summary = "Subtask title",
                description = "",
                issueType = "Subtask",
                labels = emptyList(),
                priority = "",
                cloudMode = false,
            )
        assertEquals(mapOf("key" to "PROJ"), fields["project"])
        assertEquals("Subtask title", fields["summary"])
        assertEquals(mapOf("name" to "Subtask"), fields["issuetype"])
        assertEquals(mapOf("key" to "PROJ-1"), fields["parent"])
    }

    @Test
    fun `buildSubtaskFieldsMap skips description when blank`() {
        val fields =
            adapter.buildSubtaskFieldsMap(
                parentKey = "PROJ-1",
                projectKey = "PROJ",
                summary = "S",
                description = "   ",
                issueType = "Subtask",
                labels = emptyList(),
                priority = "",
                cloudMode = true,
            )
        assertFalse(fields.containsKey("description"))
    }

    @Test
    fun `buildSubtaskFieldsMap adds ADF description for Cloud mode`() {
        val fields =
            adapter.buildSubtaskFieldsMap(
                parentKey = "PROJ-1",
                projectKey = "PROJ",
                summary = "S",
                description = "Hello world",
                issueType = "Subtask",
                labels = emptyList(),
                priority = "",
                cloudMode = true,
            )
        val desc = fields["description"]
        assertNotNull(desc)
        assertTrue(desc is Map<*, *>)
        assertEquals("doc", (desc as Map<*, *>)["type"])
    }

    @Test
    fun `buildSubtaskFieldsMap adds wiki markup description for Server mode`() {
        val fields =
            adapter.buildSubtaskFieldsMap(
                parentKey = "PROJ-1",
                projectKey = "PROJ",
                summary = "S",
                description = "Hello world",
                issueType = "Subtask",
                labels = emptyList(),
                priority = "",
                cloudMode = false,
            )
        val desc = fields["description"]
        assertNotNull(desc)
        assertEquals("Hello world", desc)
    }

    @Test
    fun `buildSubtaskFieldsMap adds labels and priority when present`() {
        val fields =
            adapter.buildSubtaskFieldsMap(
                parentKey = "PROJ-99",
                projectKey = "PROJ",
                summary = "S",
                description = "",
                issueType = "Subtask",
                labels = listOf("frontend", "urgent"),
                priority = "Major",
                cloudMode = true,
            )
        assertEquals(listOf("frontend", "urgent"), fields["labels"])
        assertEquals(mapOf("name" to "Major"), fields["priority"])
    }

    @Test
    fun `buildSubtaskFieldsMap uses project-specific issue type id when resolved`() {
        val fields =
            adapter.buildSubtaskFieldsMap(
                parentKey = "PROJ-99",
                projectKey = "PROJ",
                summary = "S",
                description = "",
                issueType = "Subtask",
                labels = emptyList(),
                priority = "Minor",
                cloudMode = true,
                issueTypeId = "10042",
            )

        assertEquals(mapOf("id" to "10042"), fields["issuetype"])
    }

    @Test
    fun `findSubtaskIssueTypeId prefers matching subtask name`() {
        val metadata =
            mapOf(
                "issueTypes" to
                    listOf(
                        mapOf("id" to "10001", "name" to "Story", "subtask" to false),
                        mapOf("id" to "10002", "name" to "Unteraufgabe", "subtask" to true),
                        mapOf("id" to "10003", "name" to "Subtask", "subtask" to true),
                    ),
            )

        assertEquals("10003", adapter.findSubtaskIssueTypeId(metadata, "subtask"))
    }

    @Test
    fun `findSubtaskIssueTypeId falls back to first project subtask type`() {
        val metadata =
            mapOf(
                "issueTypes" to
                    listOf(
                        "invalid",
                        mapOf("id" to "10002", "name" to "Unteraufgabe", "subtask" to true),
                    ),
            )

        assertEquals("10002", adapter.findSubtaskIssueTypeId(metadata, "Subtask"))
    }

    @Test
    fun `findSubtaskIssueTypeId returns null without a usable subtask id`() {
        assertEquals(null, adapter.findSubtaskIssueTypeId(emptyMap<String, Any>(), "Subtask"))
        assertEquals(
            null,
            adapter.findSubtaskIssueTypeId(
                mapOf("issueTypes" to listOf(mapOf("id" to "", "name" to "Subtask", "subtask" to true))),
                "Subtask",
            ),
        )
    }
}
