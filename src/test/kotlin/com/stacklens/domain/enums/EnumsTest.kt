package com.stacklens.domain.enums

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class EnumsTest {
    @Test
    fun `SourceType GITLAB has expected label`() {
        assertEquals("GitLab", SourceType.GITLAB.label)
    }

    @Test
    fun `SourceType DATABASE has expected label`() {
        assertEquals("Database", SourceType.DATABASE.label)
    }

    @Test
    fun `SourceType PIPELINE has expected label`() {
        assertEquals("CI/CD Pipeline", SourceType.PIPELINE.label)
    }

    @Test
    fun `SourceType CONFLUENCE has expected label`() {
        assertEquals("Confluence", SourceType.CONFLUENCE.label)
    }

    @Test
    fun `SourceType GRAFANA has expected label`() {
        assertEquals("Grafana", SourceType.GRAFANA.label)
    }

    @Test
    fun `SourceType JIRA has expected label`() {
        assertEquals("Jira", SourceType.JIRA.label)
    }

    @Test
    fun `SourceType CONTEXT_MAP has expected label`() {
        assertEquals("Context Map", SourceType.CONTEXT_MAP.label)
    }

    @Test
    fun `SourceType GITHUB has expected label`() {
        assertEquals("GitHub", SourceType.GITHUB.label)
    }

    @Test
    fun `SourceType has exactly eight values`() {
        assertEquals(8, SourceType.entries.size)
    }

    @Test
    fun `ResultType FILE has expected label`() {
        assertEquals("File", ResultType.FILE.label)
    }

    @Test
    fun `ResultType CLASS has expected label`() {
        assertEquals("Class", ResultType.CLASS.label)
    }

    @Test
    fun `ResultType FUNCTION has expected label`() {
        assertEquals("Function", ResultType.FUNCTION.label)
    }

    @Test
    fun `ResultType TABLE has expected label`() {
        assertEquals("Table", ResultType.TABLE.label)
    }

    @Test
    fun `ResultType COLUMN has expected label`() {
        assertEquals("Column", ResultType.COLUMN.label)
    }

    @Test
    fun `ResultType VIEW has expected label`() {
        assertEquals("View", ResultType.VIEW.label)
    }

    @Test
    fun `ResultType PROCEDURE has expected label`() {
        assertEquals("Procedure/Function", ResultType.PROCEDURE.label)
    }

    @Test
    fun `ResultType INDEX has expected label`() {
        assertEquals("Index", ResultType.INDEX.label)
    }

    @Test
    fun `ResultType TRIGGER has expected label`() {
        assertEquals("Trigger", ResultType.TRIGGER.label)
    }

    @Test
    fun `ResultType SEQUENCE has expected label`() {
        assertEquals("Sequence", ResultType.SEQUENCE.label)
    }

    @Test
    fun `ResultType PIPELINE_JOB has expected label`() {
        assertEquals("Pipeline-Job", ResultType.PIPELINE_JOB.label)
    }

    @Test
    fun `ResultType PIPELINE_STAGE has expected label`() {
        assertEquals("Pipeline-Stage", ResultType.PIPELINE_STAGE.label)
    }

    @Test
    fun `ResultType PIPELINE_SCRIPT has expected label`() {
        assertEquals("Pipeline-Script", ResultType.PIPELINE_SCRIPT.label)
    }

    @Test
    fun `ResultType CONFLUENCE_PAGE has expected label`() {
        assertEquals("Confluence Page", ResultType.CONFLUENCE_PAGE.label)
    }

    @Test
    fun `ResultType CONFLUENCE_BLOGPOST has expected label`() {
        assertEquals("Confluence-Blogpost", ResultType.CONFLUENCE_BLOGPOST.label)
    }

    @Test
    fun `ResultType GRAFANA_DASHBOARD has expected label`() {
        assertEquals("Grafana-Dashboard", ResultType.GRAFANA_DASHBOARD.label)
    }

    @Test
    fun `ResultType GRAFANA_PANEL has expected label`() {
        assertEquals("Grafana-Panel", ResultType.GRAFANA_PANEL.label)
    }

    @Test
    fun `ResultType JIRA_ISSUE has expected label`() {
        assertEquals("Jira-Issue", ResultType.JIRA_ISSUE.label)
    }

    @Test
    fun `ResultType JIRA_BUG has expected label`() {
        assertEquals("Jira-Bug", ResultType.JIRA_BUG.label)
    }

    @Test
    fun `ResultType JIRA_STORY has expected label`() {
        assertEquals("Jira-Story", ResultType.JIRA_STORY.label)
    }

    @Test
    fun `ResultType JIRA_TASK has expected label`() {
        assertEquals("Jira-Task", ResultType.JIRA_TASK.label)
    }

    @Test
    fun `ResultType JIRA_EPIC has expected label`() {
        assertEquals("Jira-Epic", ResultType.JIRA_EPIC.label)
    }

    @Test
    fun `ResultType CONTEXT_MAP_FILE has expected label`() {
        assertEquals("Context Map File", ResultType.CONTEXT_MAP_FILE.label)
    }

    @Test
    fun `ResultType CONTEXT_MAP_COMPONENT has expected label`() {
        assertEquals("Context Map Component", ResultType.CONTEXT_MAP_COMPONENT.label)
    }

    @Test
    fun `SessionStatus has exactly five values`() {
        assertEquals(5, SessionStatus.entries.size)
    }

    @Test
    fun `SessionStatus contains PENDING`() {
        assertNotNull(SessionStatus.PENDING)
    }

    @Test
    fun `SessionStatus contains RUNNING`() {
        assertNotNull(SessionStatus.RUNNING)
    }

    @Test
    fun `SessionStatus contains COMPLETED`() {
        assertNotNull(SessionStatus.COMPLETED)
    }

    @Test
    fun `SessionStatus contains FAILED`() {
        assertNotNull(SessionStatus.FAILED)
    }

    @Test
    fun `SessionStatus contains CANCELLED`() {
        assertNotNull(SessionStatus.CANCELLED)
    }

    @Test
    fun `DatabaseType POSTGRESQL has correct driver class`() {
        assertEquals("org.postgresql.Driver", DatabaseType.POSTGRESQL.driverClass)
    }

    @Test
    fun `DatabaseType POSTGRESQL has correct default port`() {
        assertEquals(5432, DatabaseType.POSTGRESQL.defaultPort)
    }

    @Test
    fun `DatabaseType MYSQL has correct driver class`() {
        assertEquals("com.mysql.cj.jdbc.Driver", DatabaseType.MYSQL.driverClass)
    }

    @Test
    fun `DatabaseType MYSQL has correct default port`() {
        assertEquals(3306, DatabaseType.MYSQL.defaultPort)
    }

    @Test
    fun `DatabaseType MSSQL has correct driver class`() {
        assertEquals("com.microsoft.sqlserver.jdbc.SQLServerDriver", DatabaseType.MSSQL.driverClass)
    }

    @Test
    fun `DatabaseType MSSQL has correct default port`() {
        assertEquals(1433, DatabaseType.MSSQL.defaultPort)
    }

    @Test
    fun `DatabaseType has exactly three values`() {
        assertEquals(3, DatabaseType.entries.size)
    }
}
