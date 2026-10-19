package com.stacklens.adapter.out.jira

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JiraJqlBuilderTest {
    private val builder = JiraJqlBuilder()

    @Test
    fun `buildJql returns key search when keyword matches an issue-key pattern`() {
        val jql = builder.buildJql(projectKeys = listOf("PROJ"), keyword = "PROJ-123")
        assertEquals("""key = "PROJ-123" ORDER BY updated DESC""", jql)
    }

    @Test
    fun `buildJql uses key clause when keyword is lowercase issue key (doesn't match pattern)`() {
        val jql = builder.buildJql(projectKeys = listOf("PROJ"), keyword = "proj-123")
        assertTrue(jql.contains("text ~"))
    }

    @Test
    fun `buildJql escapes double quotes in keyword`() {
        val jql = builder.buildJql(projectKeys = emptyList(), keyword = """hello"world""")
        assertTrue(jql.contains("\\\""), "expected escaped quote in $jql")
    }

    @Test
    fun `buildJql returns text search when keyword is not an issue key`() {
        val jql = builder.buildJql(projectKeys = listOf("PROJ"), keyword = "refactor")
        assertTrue(jql.contains("text ~ \"refactor\""))
        assertTrue(jql.contains("ORDER BY updated DESC"))
    }

    @Test
    fun `buildJql adds single component filter`() {
        val jql =
            builder.buildJql(
                projectKeys = listOf("PROJ"),
                keyword = "refactor",
                componentFilters = listOf("frontend"),
            )
        assertTrue(jql.contains("""component = "frontend""""))
        assertTrue(jql.contains(" AND "))
    }

    @Test
    fun `buildJql adds multiple component filter`() {
        val jql =
            builder.buildJql(
                projectKeys = listOf("PROJ"),
                keyword = "refactor",
                componentFilters = listOf("frontend", "backend"),
            )
        assertTrue(jql.contains("component in (\"frontend\",\"backend\")"))
    }

    @Test
    fun `buildJql adds single label filter`() {
        val jql =
            builder.buildJql(
                projectKeys = listOf("PROJ"),
                keyword = "refactor",
                labelFilters = listOf("bug"),
            )
        assertTrue(jql.contains("""labels = "bug""""))
    }

    @Test
    fun `buildJql adds multiple label filter`() {
        val jql =
            builder.buildJql(
                projectKeys = listOf("PROJ"),
                keyword = "refactor",
                labelFilters = listOf("bug", "tech-debt"),
            )
        assertTrue(jql.contains("labels in (\"bug\",\"tech-debt\")"))
    }

    @Test
    fun `buildJql combines component and label filters`() {
        val jql =
            builder.buildJql(
                projectKeys = listOf("PROJ"),
                keyword = "refactor",
                componentFilters = listOf("frontend"),
                labelFilters = listOf("bug"),
            )
        assertTrue(jql.contains("""component = "frontend""""))
        assertTrue(jql.contains("""labels = "bug""""))
        assertTrue(jql.contains(" AND "))
    }

    @Test
    fun `buildJql ignores blank component filter`() {
        val jql =
            builder.buildJql(
                projectKeys = listOf("PROJ"),
                keyword = "refactor",
                componentFilters = listOf("", "  "),
            )
        assertEquals(
            """(text ~ "refactor" OR component = "refactor" OR labels = "refactor") ORDER BY updated DESC""",
            jql,
        )
    }

    @Test
    fun `buildJql ignores blank label filter`() {
        val jql =
            builder.buildJql(
                projectKeys = listOf("PROJ"),
                keyword = "refactor",
                labelFilters = listOf("", "  "),
            )
        assertEquals(
            """(text ~ "refactor" OR component = "refactor" OR labels = "refactor") ORDER BY updated DESC""",
            jql,
        )
    }

    @Test
    fun `buildJql text clause includes text, component and labels`() {
        val jql = builder.buildJql(projectKeys = listOf("PROJ"), keyword = "refactor")
        assertTrue(jql.contains("text ~ \"refactor\""))
        assertTrue(jql.contains("component = \"refactor\""))
        assertTrue(jql.contains("labels = \"refactor\""))
    }

    @Test
    fun `buildJql always ends with ORDER BY updated DESC`() {
        val jql1 = builder.buildJql(projectKeys = emptyList(), keyword = "PROJ-1")
        val jql2 = builder.buildJql(projectKeys = emptyList(), keyword = "refactor")
        assertTrue(jql1.endsWith("ORDER BY updated DESC"))
        assertTrue(jql2.endsWith("ORDER BY updated DESC"))
    }

    @Test
    fun `buildJql cloudMode parameter is accepted but doesn't change output`() {
        val jqlCloud = builder.buildJql(listOf("PROJ"), "refactor", cloudMode = true)
        val jqlServer = builder.buildJql(listOf("PROJ"), "refactor", cloudMode = false)
        assertEquals(jqlCloud, jqlServer)
    }

    @Test
    fun `projectKeyList splits comma-separated keys`() {
        assertEquals(listOf("PROJ", "DEV"), builder.projectKeyList("PROJ,DEV"))
    }

    @Test
    fun `projectKeyList trims whitespace around keys`() {
        assertEquals(listOf("PROJ", "DEV"), builder.projectKeyList("  PROJ  ,  DEV  "))
    }

    @Test
    fun `projectKeyList filters out blank keys`() {
        assertEquals(listOf("PROJ"), builder.projectKeyList("PROJ,, ,"))
    }

    @Test
    fun `projectKeyList returns empty list for empty input`() {
        assertEquals(emptyList<String>(), builder.projectKeyList(""))
        assertEquals(emptyList<String>(), builder.projectKeyList("  "))
    }
}
