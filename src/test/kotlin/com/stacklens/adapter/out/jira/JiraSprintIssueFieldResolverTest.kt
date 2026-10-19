package com.stacklens.adapter.out.jira

import com.stacklens.adapter.out.shared.JiraChangelog
import com.stacklens.adapter.out.shared.JiraChangelogHistory
import com.stacklens.adapter.out.shared.JiraChangelogItem
import com.stacklens.adapter.out.shared.JiraSprintIssueFieldResolver
import com.stacklens.adapter.out.shared.JiraSprintIssueFields
import com.stacklens.adapter.out.shared.JiraSprintIssueItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class JiraSprintIssueFieldResolverTest {
    private val resolver = JiraSprintIssueFieldResolver()

    private fun item(
        resolutionDate: String? = null,
        statusCategoryChangeDate: String? = null,
        changelog: JiraChangelog? = null,
        created: String? = null,
    ): JiraSprintIssueItem =
        JiraSprintIssueItem(
            key = "PROJ-1",
            fields =
                JiraSprintIssueFields(
                    resolutiondate = resolutionDate,
                    statuscategorychangedate = statusCategoryChangeDate,
                    created = created,
                ),
            changelog = changelog,
        )

    private fun history(
        created: String? = null,
        items: List<JiraChangelogItem> = emptyList(),
    ): JiraChangelogHistory = JiraChangelogHistory(created = created, items = items)

    private fun changelogItem(
        field: String = "status",
        toStatus: String? = null,
    ): JiraChangelogItem = JiraChangelogItem(field = field, fromString = null, toString = toStatus)

    @Test
    fun `resolveDoneTimestamp returns resolutiondate when present`() {
        val result =
            resolver.resolveDoneTimestamp(
                item(resolutionDate = "2024-01-01T00:00:00.000+0000"),
            )
        assertEquals("2024-01-01T00:00:00.000+0000", result)
    }

    @Test
    fun `resolveDoneTimestamp falls back to statuscategorychangedate when resolutiondate blank`() {
        val result =
            resolver.resolveDoneTimestamp(
                item(
                    resolutionDate = "",
                    statusCategoryChangeDate = "2024-02-01T00:00:00.000+0000",
                ),
            )
        assertNull(result)
    }

    @Test
    fun `resolveDoneTimestamp falls back to statuscategorychangedate when resolutiondate null`() {
        val result =
            resolver.resolveDoneTimestamp(
                item(statusCategoryChangeDate = "2024-02-01T00:00:00.000+0000"),
            )
        assertEquals("2024-02-01T00:00:00.000+0000", result)
    }

    @Test
    fun `resolveDoneTimestamp falls back to changelog when both fields blank`() {
        val changelog =
            JiraChangelog(
                histories =
                    listOf(
                        history(created = "2024-03-01T10:00:00.000+0000", items = listOf(changelogItem(toStatus = "Done"))),
                    ),
            )
        val result =
            resolver.resolveDoneTimestamp(
                item(resolutionDate = "", statusCategoryChangeDate = "", changelog = changelog),
            )
        assertEquals("2024-03-01T10:00:00.000+0000", result)
    }

    @Test
    fun `resolveDoneTimestamp returns LAST changelog entry with done status`() {
        val changelog =
            JiraChangelog(
                histories =
                    listOf(
                        history(created = "2024-01-01T00:00:00.000+0000", items = listOf(changelogItem(toStatus = "Open"))),
                        history(created = "2024-02-01T00:00:00.000+0000", items = listOf(changelogItem(toStatus = "In Progress"))),
                        history(created = "2024-03-01T00:00:00.000+0000", items = listOf(changelogItem(toStatus = "Done"))),
                        history(created = "2024-04-01T00:00:00.000+0000", items = listOf(changelogItem(toStatus = "Closed"))),
                    ),
            )
        val result = resolver.resolveDoneTimestamp(item(changelog = changelog))
        assertEquals("2024-04-01T00:00:00.000+0000", result)
    }

    @Test
    fun `resolveDoneTimestamp returns null when no resolution date and no changelog`() {
        val result = resolver.resolveDoneTimestamp(item())
        assertNull(result)
    }

    @Test
    fun `resolveDoneTimestamp returns null when changelog has no done entries`() {
        val changelog =
            JiraChangelog(
                histories =
                    listOf(
                        history(created = "2024-01-01T00:00:00.000+0000", items = listOf(changelogItem(toStatus = "Open"))),
                    ),
            )
        val result = resolver.resolveDoneTimestamp(item(changelog = changelog))
        assertNull(result)
    }

    @Test
    fun `resolveDoneTimestamp ignores changelog entries where field is not status`() {
        val changelog =
            JiraChangelog(
                histories =
                    listOf(
                        history(
                            created = "2024-01-01T00:00:00.000+0000",
                            items = listOf(changelogItem(field = "assignee", toStatus = "Done")),
                        ),
                    ),
            )
        val result = resolver.resolveDoneTimestamp(item(changelog = changelog))
        assertNull(result)
    }

    @Test
    fun `resolveDoneTimestamp is case-insensitive on status match`() {
        val changelog =
            JiraChangelog(
                histories =
                    listOf(
                        history(created = "2024-01-01T00:00:00.000+0000", items = listOf(changelogItem(toStatus = "DONE"))),
                    ),
            )
        val result = resolver.resolveDoneTimestamp(item(changelog = changelog))
        assertEquals("2024-01-01T00:00:00.000+0000", result)
    }

    @Test
    fun `resolveDoneTimestamp matches Closed status`() {
        val changelog =
            JiraChangelog(
                histories =
                    listOf(
                        history(created = "2024-01-01T00:00:00.000+0000", items = listOf(changelogItem(toStatus = "Closed"))),
                    ),
            )
        val result = resolver.resolveDoneTimestamp(item(changelog = changelog))
        assertEquals("2024-01-01T00:00:00.000+0000", result)
    }

    @Test
    fun `resolveDoneTimestamp matches Resolved status`() {
        val changelog =
            JiraChangelog(
                histories =
                    listOf(
                        history(created = "2024-01-01T00:00:00.000+0000", items = listOf(changelogItem(toStatus = "Resolved"))),
                    ),
            )
        val result = resolver.resolveDoneTimestamp(item(changelog = changelog))
        assertEquals("2024-01-01T00:00:00.000+0000", result)
    }

    @Test
    fun `resolveDoneTimestamp ignores entries with null toString`() {
        val changelog =
            JiraChangelog(
                histories =
                    listOf(
                        history(created = "2024-01-01T00:00:00.000+0000", items = listOf(changelogItem(toStatus = null))),
                    ),
            )
        val result = resolver.resolveDoneTimestamp(item(changelog = changelog))
        assertNull(result)
    }

    @Test
    fun `resolveQaLoopCount returns 0 when no changelog`() {
        val result = resolver.resolveQaLoopCount(item())
        assertEquals(0, result)
    }

    @Test
    fun `resolveQaLoopCount returns 0 when changelog has no QA entries`() {
        val changelog =
            JiraChangelog(
                histories =
                    listOf(
                        history(created = "2024-01-01T00:00:00.000+0000", items = listOf(changelogItem(toStatus = "Done"))),
                    ),
            )
        val result = resolver.resolveQaLoopCount(item(changelog = changelog))
        assertEquals(0, result)
    }

    @Test
    fun `resolveQaLoopCount returns 0 when exactly 1 QA entry`() {
        val changelog =
            JiraChangelog(
                histories =
                    listOf(
                        history(created = "2024-01-01T00:00:00.000+0000", items = listOf(changelogItem(toStatus = "qa"))),
                    ),
            )
        val result = resolver.resolveQaLoopCount(item(changelog = changelog))
        assertEquals(0, result)
    }

    @Test
    fun `resolveQaLoopCount returns 1 when 2 QA entries`() {
        val changelog =
            JiraChangelog(
                histories =
                    listOf(
                        history(created = "2024-01-01T00:00:00.000+0000", items = listOf(changelogItem(toStatus = "qa"))),
                        history(created = "2024-02-01T00:00:00.000+0000", items = listOf(changelogItem(toStatus = "in qa"))),
                    ),
            )
        val result = resolver.resolveQaLoopCount(item(changelog = changelog))
        assertEquals(1, result)
    }

    @Test
    fun `resolveQaLoopCount returns 2 when 3 QA entries`() {
        val changelog =
            JiraChangelog(
                histories =
                    listOf(
                        history(created = "2024-01-01T00:00:00.000+0000", items = listOf(changelogItem(toStatus = "qa"))),
                        history(created = "2024-02-01T00:00:00.000+0000", items = listOf(changelogItem(toStatus = "in qa"))),
                        history(created = "2024-03-01T00:00:00.000+0000", items = listOf(changelogItem(toStatus = "ready for qa"))),
                    ),
            )
        val result = resolver.resolveQaLoopCount(item(changelog = changelog))
        assertEquals(2, result)
    }

    @Test
    fun `resolveQaLoopCount ignores non-status fields in QA entries`() {
        val changelog =
            JiraChangelog(
                histories =
                    listOf(
                        history(
                            created = "2024-01-01T00:00:00.000+0000",
                            items = listOf(changelogItem(field = "assignee", toStatus = "qa")),
                        ),
                        history(created = "2024-02-01T00:00:00.000+0000", items = listOf(changelogItem(toStatus = "in qa"))),
                    ),
            )
        val result = resolver.resolveQaLoopCount(item(changelog = changelog))
        assertEquals(0, result)
    }

    @Test
    fun `resolveQaLoopCount is case-insensitive`() {
        val changelog =
            JiraChangelog(
                histories =
                    listOf(
                        history(created = "2024-01-01T00:00:00.000+0000", items = listOf(changelogItem(toStatus = "qa"))),
                        history(created = "2024-02-01T00:00:00.000+0000", items = listOf(changelogItem(toStatus = "in qa"))),
                    ),
            )
        val result = resolver.resolveQaLoopCount(item(changelog = changelog))
        assertEquals(1, result)
    }

    @Test
    fun `resolveStartedTimestamp returns null when no changelog`() {
        val result = resolver.resolveStartedTimestamp(item())
        assertNull(result)
    }

    @Test
    fun `resolveStartedTimestamp returns null when changelog has no start entries`() {
        val changelog =
            JiraChangelog(
                histories =
                    listOf(
                        history(created = "2024-01-01T00:00:00.000+0000", items = listOf(changelogItem(toStatus = "Open"))),
                    ),
            )
        val result = resolver.resolveStartedTimestamp(item(changelog = changelog))
        assertNull(result)
    }

    @Test
    fun `resolveStartedTimestamp returns first changelog entry with start status`() {
        val changelog =
            JiraChangelog(
                histories =
                    listOf(
                        history(created = "2024-01-01T00:00:00.000+0000", items = listOf(changelogItem(toStatus = "Open"))),
                        history(created = "2024-02-01T00:00:00.000+0000", items = listOf(changelogItem(toStatus = "In Progress"))),
                        history(created = "2024-03-01T00:00:00.000+0000", items = listOf(changelogItem(toStatus = "Done"))),
                    ),
            )
        val result = resolver.resolveStartedTimestamp(item(changelog = changelog))
        assertEquals("2024-02-01T00:00:00.000+0000", result)
    }

    @Test
    fun `resolveStartedTimestamp matches In Progress status`() {
        val changelog =
            JiraChangelog(
                histories =
                    listOf(
                        history(created = "2024-01-01T00:00:00.000+0000", items = listOf(changelogItem(toStatus = "In Progress"))),
                    ),
            )
        val result = resolver.resolveStartedTimestamp(item(changelog = changelog))
        assertEquals("2024-01-01T00:00:00.000+0000", result)
    }

    @Test
    fun `resolveStartedTimestamp matches Start Work status`() {
        val changelog =
            JiraChangelog(
                histories =
                    listOf(
                        history(created = "2024-01-01T00:00:00.000+0000", items = listOf(changelogItem(toStatus = "started"))),
                    ),
            )
        val result = resolver.resolveStartedTimestamp(item(changelog = changelog))
        assertEquals("2024-01-01T00:00:00.000+0000", result)
    }

    @Test
    fun `resolveStartedTimestamp falls through to fields created when status matches field but not start statuses`() {
        val changelog =
            JiraChangelog(
                histories =
                    listOf(
                        history(created = "2024-02-01T00:00:00.000+0000", items = listOf(changelogItem(toStatus = "Done"))),
                    ),
            )
        val result =
            resolver.resolveStartedTimestamp(
                item(changelog = changelog, created = "2024-09-01T00:00:00.000+0000"),
            )
        assertEquals("2024-09-01T00:00:00.000+0000", result)
    }

    @Test
    fun `resolveStartedTimestamp is case-insensitive on status`() {
        val changelog =
            JiraChangelog(
                histories =
                    listOf(
                        history(created = "2024-01-01T00:00:00.000+0000", items = listOf(changelogItem(toStatus = "IN PROGRESS"))),
                    ),
            )
        val result = resolver.resolveStartedTimestamp(item(changelog = changelog))
        assertEquals("2024-01-01T00:00:00.000+0000", result)
    }

    @Test
    fun `resolveStartedTimestamp returns FIRST matching entry not last`() {
        val changelog =
            JiraChangelog(
                histories =
                    listOf(
                        history(created = "2024-01-01T00:00:00.000+0000", items = listOf(changelogItem(toStatus = "In Progress"))),
                        history(created = "2024-02-01T00:00:00.000+0000", items = listOf(changelogItem(toStatus = "Done"))),
                    ),
            )
        val result = resolver.resolveStartedTimestamp(item(changelog = changelog))
        assertEquals("2024-01-01T00:00:00.000+0000", result)
    }

    @Test
    fun `resolveQaLoopCount ignores QA entries whose toString is null`() {
        val changelog =
            JiraChangelog(
                histories =
                    listOf(
                        history(
                            created = "2024-01-01T00:00:00.000+0000",
                            items = listOf(changelogItem(toStatus = null)),
                        ),
                        history(
                            created = "2024-01-02T00:00:00.000+0000",
                            items = listOf(changelogItem(toStatus = "QA")),
                        ),
                        history(
                            created = "2024-01-03T00:00:00.000+0000",
                            items = listOf(changelogItem(toStatus = "QA")),
                        ),
                    ),
            )
        assertEquals(1, resolver.resolveQaLoopCount(item(changelog = changelog)))
    }

    @Test
    fun `resolveDoneTimestamp handles changelog entries with null toString`() {
        val changelog =
            JiraChangelog(
                histories =
                    listOf(
                        history(
                            created = "2024-01-01T00:00:00.000+0000",
                            items = listOf(changelogItem(toStatus = null)),
                        ),
                        history(
                            created = "2024-02-01T00:00:00.000+0000",
                            items = listOf(changelogItem(toStatus = "Done")),
                        ),
                    ),
            )
        val result = resolver.resolveDoneTimestamp(item(changelog = changelog))
        assertEquals("2024-02-01T00:00:00.000+0000", result)
    }

    @Test
    fun `resolveStartedTimestamp handles changelog entries with null toString`() {
        val changelog =
            JiraChangelog(
                histories =
                    listOf(
                        history(
                            created = "2024-01-01T00:00:00.000+0000",
                            items = listOf(changelogItem(toStatus = null)),
                        ),
                        history(
                            created = "2024-02-01T00:00:00.000+0000",
                            items = listOf(changelogItem(toStatus = "In Progress")),
                        ),
                    ),
            )
        val result = resolver.resolveStartedTimestamp(item(changelog = changelog))
        assertEquals("2024-02-01T00:00:00.000+0000", result)
    }

    @Test
    fun `resolveQaLoopCount handles changelog with empty histories list`() {
        val changelog = JiraChangelog(histories = emptyList())
        assertEquals(0, resolver.resolveQaLoopCount(item(changelog = changelog)))
    }

    @Test
    fun `resolveStartedTimestamp matches case-insensitive field name`() {
        val changelog =
            JiraChangelog(
                histories =
                    listOf(
                        history(
                            created = "2024-03-01T00:00:00.000+0000",
                            items = listOf(changelogItem(field = "Status", toStatus = "In Progress")),
                        ),
                    ),
            )
        assertEquals(
            "2024-03-01T00:00:00.000+0000",
            resolver.resolveStartedTimestamp(item(changelog = changelog)),
        )
    }

    @Test
    fun `resolveStartedTimestamp ignores changelog entries with non-status fields`() {
        val changelog =
            JiraChangelog(
                histories =
                    listOf(
                        history(
                            created = "2024-04-01T00:00:00.000+0000",
                            items = listOf(changelogItem(field = "assignee", toStatus = "In Progress")),
                        ),
                        history(
                            created = "2024-04-02T00:00:00.000+0000",
                            items = listOf(changelogItem(field = "status", toStatus = "Doing")),
                        ),
                    ),
            )
        assertEquals(
            "2024-04-02T00:00:00.000+0000",
            resolver.resolveStartedTimestamp(item(changelog = changelog)),
        )
    }
}
