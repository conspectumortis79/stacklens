package com.stacklens.adapter.out.jira

import com.stacklens.adapter.out.shared.JiraAuthor
import com.stacklens.adapter.out.shared.JiraComment
import com.stacklens.adapter.out.shared.JiraCommentContainer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class JiraAdapterModelsTest {
    @Test
    fun `JiraIssueResponse default constructor`() {
        val r = JiraIssueResponse()
        assertEquals("", r.key)
        assertNotNull(r.fields)
    }

    @Test
    fun `JiraIssueResponse with values`() {
        val r = JiraIssueResponse(key = "ABC", fields = JiraIssueFields(summary = "S"))
        assertEquals("ABC", r.key)
        assertEquals("S", r.fields.summary)
    }

    @Test
    fun `JiraIssueFields default constructor`() {
        val f = JiraIssueFields()
        assertNull(f.summary)
        assertNull(f.description)
        assertNull(f.issuetype)
        assertNull(f.status)
        assertNull(f.priority)
        assertNull(f.labels)
        assertNull(f.components)
        assertNull(f.comment)
    }

    @Test
    fun `JiraIssueFields with values`() {
        val f =
            JiraIssueFields(
                summary = "S",
                description = null,
                issuetype = JiraNamedValue(name = "Bug"),
                status = JiraNamedValue(name = "Open"),
                priority = JiraNamedValue(name = "High"),
                labels = listOf("a", "b"),
                components = listOf(JiraComponentDto(name = "comp")),
                comment = JiraCommentContainer(),
            )
        assertEquals("S", f.summary)
        assertEquals("Bug", f.issuetype?.name)
        assertEquals(listOf("a", "b"), f.labels)
    }

    @Test
    fun `JiraComponentDto default constructor`() {
        val c = JiraComponentDto()
        assertEquals("", c.name)
    }

    @Test
    fun `JiraComponentDto with values`() {
        assertEquals("c1", JiraComponentDto(name = "c1").name)
    }

    @Test
    fun `JiraCommentContainer default constructor`() {
        val c = JiraCommentContainer()
        assertEquals(emptyList<JiraComment>(), c.comments)
    }

    @Test
    fun `JiraCommentContainer with values`() {
        val c = JiraCommentContainer(comments = listOf(JiraComment()))
        assertEquals(1, c.comments.size)
    }

    @Test
    fun `JiraComment default constructor`() {
        val c = JiraComment()
        assertNull(c.body)
        assertNull(c.author)
    }

    @Test
    fun `JiraComment with values`() {
        val c = JiraComment(body = null, author = JiraAuthor(displayName = "Bob"))
        assertEquals("Bob", c.author?.displayName)
    }

    @Test
    fun `JiraAuthor default constructor`() {
        assertNull(JiraAuthor().displayName)
    }

    @Test
    fun `JiraAuthor with values`() {
        assertEquals("Alice", JiraAuthor(displayName = "Alice").displayName)
    }

    @Test
    fun `JiraNamedValue default constructor`() {
        assertNull(JiraNamedValue().name)
    }

    @Test
    fun `JiraNamedValue with values`() {
        assertEquals("foo", JiraNamedValue(name = "foo").name)
    }

    @Test
    fun `JiraAdfNode default constructor`() {
        val n = JiraAdfNode()
        assertEquals("", n.type)
        assertNull(n.text)
        assertEquals(emptyList<JiraAdfNode>(), n.content)
    }

    @Test
    fun `JiraAdfNode with values`() {
        val n =
            JiraAdfNode(
                type = "paragraph",
                text = "hello",
                content = listOf(JiraAdfNode(type = "text", text = "world")),
            )
        assertEquals("paragraph", n.type)
        assertEquals("hello", n.text)
        assertEquals(1, n.content.size)
    }

    @Test
    fun `JiraSearchResponse default constructor`() {
        val r = JiraSearchResponse()
        assertEquals(emptyList<JiraIssueRef>(), r.issues)
    }

    @Test
    fun `JiraSearchResponse with values`() {
        val r = JiraSearchResponse(issues = listOf(JiraIssueRef(key = "ABC")))
        assertEquals(1, r.issues.size)
        assertEquals("ABC", r.issues[0].key)
    }

    @Test
    fun `JiraIssueRef default constructor`() {
        val r = JiraIssueRef()
        assertEquals("", r.key)
        assertNotNull(r.fields)
    }

    @Test
    fun `JiraIssueRef with values`() {
        val r = JiraIssueRef(key = "X", fields = JiraIssueFields(summary = "S"))
        assertEquals("X", r.key)
        assertEquals("S", r.fields.summary)
    }
}
