package com.stacklens.application.port.out

import com.stacklens.domain.enums.FileChangeType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JiraTicketPortModelsTest {
    @Test
    fun `applyPatches returns original string when patches list is empty`() {
        val original = "hello world"
        assertEquals(original, applyPatches(original, emptyList()))
    }

    @Test
    fun `applyPatches skips patch when search is blank`() {
        val original = "hello world"
        val result = applyPatches(original, listOf(JiraCodePatch(search = "", replace = "REPLACED")))
        assertEquals(original, result)
    }

    @Test
    fun `applyPatches skips patch when search is whitespace only`() {
        val original = "hello world"
        val result = applyPatches(original, listOf(JiraCodePatch(search = "   ", replace = "REPLACED")))
        assertEquals(original, result)
    }

    @Test
    fun `applyPatches applies replacement when search string is found`() {
        val result = applyPatches("hello world", listOf(JiraCodePatch(search = "world", replace = "Kotlin")))
        assertEquals("hello Kotlin", result)
    }

    @Test
    fun `applyPatches returns original when search string is not found`() {
        val result = applyPatches("hello world", listOf(JiraCodePatch(search = "missing", replace = "X")))
        assertEquals("hello world", result)
    }

    @Test
    fun `applyPatches applies multiple patches sequentially`() {
        val patches =
            listOf(
                JiraCodePatch(search = "foo", replace = "bar"),
                JiraCodePatch(search = "bar", replace = "baz"),
            )
        val result = applyPatches("foo foo", patches)
        assertEquals("baz foo", result)
    }

    @Test
    fun `applyPatches replaces only first occurrence of search string`() {
        val result = applyPatches("a a a", listOf(JiraCodePatch(search = "a", replace = "X")))
        assertEquals("X a a", result)
    }

    @Test
    fun `applyPatches handles blank search among valid patches`() {
        val patches =
            listOf(
                JiraCodePatch(search = "", replace = "IGNORED"),
                JiraCodePatch(search = "hello", replace = "hi"),
            )
        val result = applyPatches("hello world", patches)
        assertEquals("hi world", result)
    }

    @Test
    fun `JiraMrFileChange creates with values`() {
        val fc = JiraMrFileChange(filePath = "src/Foo.kt", diffExcerpt = "+new line")
        assertEquals("src/Foo.kt", fc.filePath)
        assertEquals("+new line", fc.diffExcerpt)
    }

    @Test
    fun `JiraMrFileChange equals and hashCode`() {
        val a = JiraMrFileChange("src/A.kt", "+line")
        val b = JiraMrFileChange("src/A.kt", "+line")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `JiraMrFileChange copy`() {
        val original = JiraMrFileChange("src/A.kt", "+line")
        val copy = original.copy(diffExcerpt = "-removed")
        assertEquals("src/A.kt", copy.filePath)
        assertEquals("-removed", copy.diffExcerpt)
    }

    @Test
    fun `JiraRelatedMr creates with values`() {
        val change = JiraMrFileChange("src/Foo.kt", "+x")
        val mr =
            JiraRelatedMr(
                projectPath = "group/repo",
                mrIid = 42,
                title = "Fix: add feature",
                description = "Details here",
                state = "merged",
                url = "https://gitlab.example.com/mr/42",
                foundViaTicketKey = "PROJ-1",
                foundViaKeyword = null,
                fileChanges = listOf(change),
            )
        assertEquals("group/repo", mr.projectPath)
        assertEquals(42L, mr.mrIid)
        assertEquals("merged", mr.state)
        assertEquals("PROJ-1", mr.foundViaTicketKey)
        assertNull(mr.foundViaKeyword)
        assertEquals(1, mr.fileChanges.size)
    }

    @Test
    fun `JiraRelatedMr copy`() {
        val mr = JiraRelatedMr("g/r", 1, "T", "D", "opened", "url", null, null, emptyList())
        val copy = mr.copy(state = "merged")
        assertEquals("merged", copy.state)
        assertEquals("opened", mr.state)
    }

    @Test
    fun `JiraCodeFile creates with values`() {
        val patch = JiraCodePatch(search = "old", replace = "new")
        val cf =
            JiraCodeFile(
                repository = "repo1",
                path = "src/Foo.kt",
                language = "Kotlin",
                description = "Main class",
                changeType = FileChangeType.MODIFY,
                code = "class Foo {}",
                patches = listOf(patch),
            )
        assertEquals("repo1", cf.repository)
        assertEquals("Kotlin", cf.language)
        assertEquals(FileChangeType.MODIFY, cf.changeType)
        assertEquals(1, cf.patches.size)
    }

    @Test
    fun `JiraCodeFile default patches is empty`() {
        val cf = JiraCodeFile("r", "p", "l", "d", FileChangeType.PATCH, "code")
        assertEquals(emptyList<JiraCodePatch>(), cf.patches)
    }

    @Test
    fun `JiraMonorepoIdentification creates with required values`() {
        val mono =
            JiraMonorepoIdentification(
                identifiedApplication = "backend",
                relativeRootPath = "services/backend",
                reasoning = "Found Spring Boot in services/backend",
                confidence = "HIGH",
                isMonorepo = true,
                techStack = listOf("Kotlin", "Spring Boot"),
                codingConventions = "hexagonal",
                testFramework = "JUnit5",
                buildTool = "Gradle",
                testCommand = "./gradlew test",
            )
        assertEquals("backend", mono.identifiedApplication)
        assertEquals("HIGH", mono.confidence)
        assertTrue(mono.isMonorepo)
        assertEquals(listOf("Kotlin", "Spring Boot"), mono.techStack)
    }

    @Test
    fun `JiraMonorepoIdentification defaults`() {
        val mono =
            JiraMonorepoIdentification(
                identifiedApplication = "app",
                relativeRootPath = "",
                reasoning = "single repo",
                confidence = "LOW",
                isMonorepo = false,
                techStack = emptyList(),
                codingConventions = "",
                testFramework = "",
                buildTool = "npm",
                testCommand = "npm test",
            )
        assertEquals("", mono.architectureStyle)
        assertEquals("", mono.diApproach)
        assertEquals(false, mono.hasUnitTests)
        assertEquals(false, mono.hasIntegrationTests)
        assertEquals(false, mono.hasSystemTests)
        assertEquals("", mono.testStructureNotes)
        assertEquals("", mono.projectPurpose)
        assertEquals("", mono.keyModulesDescription)
        assertEquals("", mono.designPatterns)
        assertEquals("", mono.dataModelSummary)
        assertEquals("", mono.exampleTestPatterns)
    }

    @Test
    fun `JiraMonorepoIdentification copy`() {
        val mono =
            JiraMonorepoIdentification(
                "app",
                "",
                "reason",
                "HIGH",
                false,
                emptyList(),
                "",
                "",
                "Gradle",
                "./gradlew test",
            )
        val copy = mono.copy(confidence = "LOW", hasUnitTests = true)
        assertEquals("LOW", copy.confidence)
        assertTrue(copy.hasUnitTests)
        assertEquals("HIGH", mono.confidence)
    }

    @Test
    fun `JiraTicketData creates with acceptanceCriteria default`() {
        val t =
            JiraTicketData(
                key = "T-1",
                summary = "s",
                description = "d",
                comments = "",
                issueType = "Bug",
                status = "Open",
                priority = null,
                labels = emptyList(),
                components = emptyList(),
                url = "http://jira/T-1",
            )
        assertEquals(emptyList<String>(), t.acceptanceCriteria)
    }

    @Test
    fun `JiraProblemAnalysis creates and equals`() {
        val a =
            JiraProblemAnalysis(
                problemDescription = "NullPointerException in login",
                rootCause = "Missing null check",
                affectedComponents = listOf("auth"),
                searchKeywords = listOf("NPE", "login"),
                errorMessages = listOf("java.lang.NullPointerException"),
            )
        assertEquals("NullPointerException in login", a.problemDescription)
        assertEquals(listOf("auth"), a.affectedComponents)
        assertEquals(a, a.copy())
    }

    @Test
    fun `JiraSimilarTicket creates and copy`() {
        val t =
            JiraSimilarTicket(
                key = "OLD-1",
                summary = "Old bug",
                description = "desc",
                issueType = "Bug",
                status = "Done",
                url = "http://jira/OLD-1",
                foundViaKeyword = "login",
            )
        assertEquals("OLD-1", t.key)
        assertEquals("login", t.foundViaKeyword)
        val copy = t.copy(foundViaKeyword = null)
        assertNull(copy.foundViaKeyword)
    }

    @Test
    fun `SprintInfo creates with nulls`() {
        val s = SprintInfo(id = 1, name = "Sprint 1", state = "active", startDate = null, endDate = null, goal = null)
        assertEquals(1L, s.id)
        assertEquals("active", s.state)
        assertNull(s.goal)
    }

    @Test
    fun `SprintIssue creates`() {
        val i =
            SprintIssue(
                key = "SP-1",
                summary = "Fix bug",
                description = "desc",
                issueType = "Bug",
                status = "In Progress",
                priority = "High",
                assignee = "dev",
                labels = listOf("backend"),
                comments = "",
                url = "http://jira/SP-1",
            )
        assertEquals("SP-1", i.key)
        assertEquals("High", i.priority)
    }

    @Test
    fun `JiraTicketConstants has expected values`() {
        assertEquals(10, JiraTicketConstants.MAX_COMMENTS)
        assertEquals(6, JiraTicketConstants.MAX_SEARCH_KEYWORDS)
        assertEquals(20, JiraTicketConstants.MAX_SIMILAR_TICKETS)
        assertEquals(120, JiraTicketConstants.MAX_FILES_FOR_CONTEXT)
    }

    @Test
    fun `JiraDatabaseChange stores all fields`() {
        val change =
            JiraDatabaseChange(
                database = "mydb",
                description = "Add index on users",
                sql = "CREATE INDEX idx_email ON users(email)",
            )
        assertEquals("mydb", change.database)
        assertEquals("Add index on users", change.description)
        assertEquals("CREATE INDEX idx_email ON users(email)", change.sql)
    }

    @Test
    fun `JiraDatabaseChange equals and copy`() {
        val a = JiraDatabaseChange(database = "db1", description = "desc", sql = "SELECT 1")
        val b = a.copy(database = "db2")
        assertEquals("db1", a.database)
        assertEquals("db2", b.database)
        assertEquals(a, a.copy())
    }

    @Test
    fun `JiraProblemAnalysis all fields accessed`() {
        val a =
            JiraProblemAnalysis(
                problemDescription = "desc",
                rootCause = "null check",
                affectedComponents = listOf("auth"),
                searchKeywords = listOf("NPE"),
                errorMessages = listOf("NullPointerException"),
            )
        assertEquals("null check", a.rootCause)
        assertEquals(listOf("NPE"), a.searchKeywords)
        assertEquals(listOf("NullPointerException"), a.errorMessages)
    }

    @Test
    fun `JiraSimilarTicket all fields accessed`() {
        val t =
            JiraSimilarTicket(
                key = "T-1",
                summary = "Summary",
                description = "Desc",
                issueType = "Bug",
                status = "Done",
                url = "http://jira/T-1",
                foundViaKeyword = "login",
            )
        assertEquals("Summary", t.summary)
        assertEquals("Desc", t.description)
        assertEquals("Bug", t.issueType)
        assertEquals("Done", t.status)
        assertEquals("http://jira/T-1", t.url)
    }

    @Test
    fun `JiraTicketData url field accessed`() {
        val d =
            JiraTicketData(
                key = "T-1",
                summary = "S",
                description = "D",
                comments = "",
                issueType = "Bug",
                status = "Open",
                priority = null,
                labels = emptyList(),
                components = emptyList(),
                url = "http://jira/T-1",
            )
        assertEquals("http://jira/T-1", d.url)
    }

    @Test
    fun `JiraRelatedMr all fields accessed`() {
        val mr =
            JiraRelatedMr(
                projectPath = "g/r",
                mrIid = 1,
                title = "Fix bug",
                description = "Fixes NPE",
                state = "merged",
                url = "http://gitlab/mr/1",
                foundViaTicketKey = "T-1",
                foundViaKeyword = null,
                fileChanges = emptyList(),
            )
        assertEquals("Fix bug", mr.title)
        assertEquals("Fixes NPE", mr.description)
        assertEquals("http://gitlab/mr/1", mr.url)
    }

    @Test
    fun `JiraRepoContext all fields accessed`() {
        val ctx = JiraRepoContext(repoName = "myrepo", projectId = 42L, files = emptyList())
        assertEquals("myrepo", ctx.repoName)
        assertEquals(42L, ctx.projectId)
    }

    @Test
    fun `JiraCodeFile code field accessed`() {
        val f =
            JiraCodeFile(
                repository = "repo",
                path = "src/Foo.kt",
                language = "kotlin",
                description = "Main class",
                changeType = FileChangeType.UPDATE,
                code = "class Foo",
            )
        assertEquals("class Foo", f.code)
    }

    @Test
    fun `JiraCodeImplementation all fields accessed`() {
        val impl =
            JiraCodeImplementation(
                identifiedRepositories = listOf("repo1"),
                identifiedDatabases = listOf("db1"),
                files = emptyList(),
                testFiles = emptyList(),
                systemTestFiles = emptyList(),
                databaseChanges = emptyList(),
                implementationNotes = "Some notes",
            )
        assertEquals(listOf("db1"), impl.identifiedDatabases)
        assertEquals(emptyList<Any>(), impl.systemTestFiles)
        assertEquals("Some notes", impl.implementationNotes)
    }

    @Test
    fun `JiraMonorepoIdentification all fields accessed`() {
        val m =
            JiraMonorepoIdentification(
                identifiedApplication = "app",
                relativeRootPath = "services/app",
                reasoning = "Found main class",
                confidence = "HIGH",
                isMonorepo = true,
                techStack = listOf("Kotlin"),
                codingConventions = "hexagonal",
                testFramework = "JUnit5",
                buildTool = "Gradle",
                testCommand = "./gradlew test",
            )
        assertEquals("services/app", m.relativeRootPath)
        assertEquals("Found main class", m.reasoning)
        assertEquals("hexagonal", m.codingConventions)
        assertEquals("JUnit5", m.testFramework)
        assertEquals("Gradle", m.buildTool)
        assertEquals("./gradlew test", m.testCommand)
    }

    @Test
    fun `LinkedIssue holds all fields including nullable status`() {
        val withStatus = LinkedIssue(key = "PROJ-1", summary = "A bug", relationship = "blocks", status = "Done")
        assertEquals("PROJ-1", withStatus.key)
        assertEquals("A bug", withStatus.summary)
        assertEquals("blocks", withStatus.relationship)
        assertEquals("Done", withStatus.status)

        val withoutStatus = LinkedIssue(key = "PROJ-2", summary = "Task", relationship = "relates to", status = null)
        assertEquals(null, withoutStatus.status)
    }
}
