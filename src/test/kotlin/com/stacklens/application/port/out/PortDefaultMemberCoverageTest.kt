package com.stacklens.application.port.out

import com.stacklens.application.port.out.health.ConnectionHealthPort
import com.stacklens.application.port.out.knowledge.CalendarEvent
import com.stacklens.application.port.out.knowledge.JiraHierarchyPort
import com.stacklens.application.port.out.knowledge.JiraIssueSnapshot
import com.stacklens.application.port.out.knowledge.LinearizedPage
import com.stacklens.application.port.out.knowledge.TableMeta
import com.stacklens.application.port.out.sql.H2TestQueryResult
import com.stacklens.application.port.out.sql.H2TestSession
import com.stacklens.domain.model.ConnectionHealthSnapshot
import com.stacklens.domain.model.JiraConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.Month

class PortDefaultMemberCoverageTest {
    private val snapshot =
        ConnectionHealthSnapshot(
            id = 4L,
            kind = "jira",
            name = "primary",
            active = true,
            consecutiveFailures = 1,
            lastErrorAt = null,
            lastErrorMessage = "timeout",
            healthy = false,
        )

    private val healthPort =
        object : ConnectionHealthPort {
            override fun snapshotAll(): Map<String, ConnectionHealthSnapshot> = mapOf("jira:4" to snapshot)
        }

    private val h2Session =
        object : H2TestSession {
            var lastSql: String? = null

            var budget: Long = -1

            override fun executeStatement(sql: String): Boolean {
                lastSql = sql
                return true
            }

            override fun runQuery(
                sql: String,
                maxRows: Int,
                timeoutSec: Int,
            ): H2TestQueryResult {
                lastSql = sql
                budget = maxRows.toLong() * 100 + timeoutSec
                return object : H2TestQueryResult {
                    override val columns: List<String> = emptyList()

                    override val rows: List<List<String?>> = emptyList()

                    override val truncated: Boolean = false
                }
            }

            override fun close() = Unit
        }

    private val hierarchyPort =
        object : JiraHierarchyPort {
            var seenMaxIssues = -1

            override fun ensureHostMatches(
                config: JiraConfig,
                rootTicketKeyOrUrl: String,
            ) = Unit

            override fun walkHierarchy(
                config: JiraConfig,
                rootTicketKeyOrUrl: String,
                includeChildren: Boolean,
                includeComments: Boolean,
                maxDepth: Int,
                maxIssues: Int,
                includeLinkedTickets: Boolean,
                includeParent: Boolean,
            ): Sequence<JiraIssueSnapshot> {
                seenMaxIssues = maxIssues
                return emptySequence()
            }
        }

    @Test
    fun `the health port looks a snapshot up by kind and id`() {
        assertEquals(snapshot, healthPort.snapshotFor("jira", 4L))
        assertNull(healthPort.snapshotFor("jira", 5L))
    }

    @Test
    fun `h2 query helpers default their row and time budgets`() {
        val result = h2Session.runQuery("select 1")

        assertTrue(result.columns.isEmpty())
        assertEquals(200 * 100 + 10, h2Session.budget)
        assertTrue(h2Session.executeStatement("create table t (id int)"))
        h2Session.close()
    }

    @Test
    fun `the jira hierarchy walk defaults its issue budget and flags`() {
        val config =
            JiraConfig(
                name = "jira",
                baseUrl = "http://jira.example",
                username = "u",
                apiToken = "t",
            )

        assertTrue(hierarchyPort.walkHierarchy(config, "PROJ-1", includeChildren = true, includeComments = false, maxDepth = 3).toList().isEmpty())
        assertEquals(1000, hierarchyPort.seenMaxIssues)
    }

    @Test
    fun `a linearized page carries no calendars by default`() {
        val page = LinearizedPage(text = "body", tables = listOf(TableMeta(caption = "t", headers = emptyList(), rowCount = 0)))

        assertTrue(page.calendars.isEmpty())
        assertEquals(page, page.copy())
        assertEquals(page, LinearizedPage("body", page.tables))
        assertEquals(page.hashCode(), LinearizedPage("body", page.tables).hashCode())
        assertTrue(page.toString().contains("body"))
    }

    @Test
    fun `calendar metadata defaults its optional fields`() {
        val event =
            CalendarEvent(
                calendarId = "cal-1",
                id = "e-1",
                title = "Standup",
                start = Instant.EPOCH,
                end = Instant.ofEpochMilli(1000),
            )

        assertNull(event.description)
        assertNull(event.location)
        assertEquals(1000, event.end.toEpochMilli())
        assertEquals(event, CalendarEvent("cal-1", "e-1", "Standup", Instant.EPOCH, Instant.ofEpochMilli(1000)))
    }

    @Test
    fun `the system clock exposes the platform date and time`() {
        val now =
            com.stacklens.adapter.out.time.SystemClockPort
                .now()

        assertTrue(
            com.stacklens.adapter.out.time.SystemClockPort
                .epochMillis() in now.toEpochMilli() - 5_000..now.toEpochMilli() + 5_000,
        )
        assertTrue(
            !com.stacklens.adapter.out.time.SystemClockPort
                .localDateTime()
                .isBefore(LocalDateTime.of(2020, Month.JANUARY, 1, 0, 0)),
        )
        assertTrue(
            com.stacklens.adapter.out.time.SystemClockPort
                .localDate()
                .year >= 2020,
        )
    }
}
