package com.stacklens.application.port.out

import com.stacklens.domain.enums.FileChangeType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JiraTicketModelsCoverageTest {
    @Test
    fun `JiraMrFileChange stores its arguments verbatim`() {
        val c = JiraMrFileChange(filePath = "src/x.kt", diffExcerpt = "+added")
        assertEquals("src/x.kt", c.filePath)
        assertEquals("+added", c.diffExcerpt)
    }

    @Test
    fun `JiraRelatedMr stores its arguments verbatim`() {
        val fc = JiraMrFileChange("a.kt", "diff")
        val m =
            JiraRelatedMr(
                projectPath = "group/proj",
                mrIid = 12L,
                title = "t",
                description = "d",
                state = "merged",
                url = "u",
                foundViaTicketKey = "PROJ-1",
                foundViaKeyword = "kw",
                fileChanges = listOf(fc),
            )
        assertEquals("group/proj", m.projectPath)
        assertEquals(12L, m.mrIid)
        assertEquals("t", m.title)
        assertEquals("d", m.description)
        assertEquals("merged", m.state)
        assertEquals("u", m.url)
        assertEquals("PROJ-1", m.foundViaTicketKey)
        assertEquals("kw", m.foundViaKeyword)
        assertEquals(listOf(fc), m.fileChanges)
    }

    @Test
    fun `JiraRepoFile stores its arguments verbatim`() {
        val f = JiraRepoFile(path = "p", content = "c")
        assertEquals("p", f.path)
        assertEquals("c", f.content)
    }

    @Test
    fun `JiraRepoContext stores its arguments verbatim`() {
        val f = JiraRepoFile("p", "c")
        val ctx = JiraRepoContext(repoName = "repo", projectId = 7L, files = listOf(f))
        assertEquals("repo", ctx.repoName)
        assertEquals(7L, ctx.projectId)
        assertEquals(listOf(f), ctx.files)
    }

    @Test
    fun `JiraCodeFile stores its arguments verbatim`() {
        val patch = JiraCodePatch("a", "b")
        val f =
            JiraCodeFile(
                repository = "r",
                path = "p",
                language = "Kotlin",
                description = "d",
                changeType = FileChangeType.MODIFY,
                code = "fun f() = 0",
                patches = listOf(patch),
            )
        assertEquals("r", f.repository)
        assertEquals("p", f.path)
        assertEquals("Kotlin", f.language)
        assertEquals("d", f.description)
        assertEquals(FileChangeType.MODIFY, f.changeType)
        assertEquals("fun f() = 0", f.code)
        assertEquals(listOf(patch), f.patches)
    }

    @Test
    fun `JiraCodeFile default patches list is empty`() {
        val f =
            JiraCodeFile(
                repository = "r",
                path = "p",
                language = "Kotlin",
                description = "d",
                changeType = FileChangeType.ADD,
                code = "fun f() = 0",
            )
        assertTrue(f.patches.isEmpty())
    }

    @Test
    fun `JiraCodePatch stores its arguments verbatim`() {
        val p = JiraCodePatch(search = "old", replace = "new")
        assertEquals("old", p.search)
        assertEquals("new", p.replace)
    }

    @Test
    fun `applyPatches returns original when no patches match`() {
        val out = applyPatches("hello world", listOf(JiraCodePatch("x", "y")))
        assertEquals("hello world", out)
    }

    @Test
    fun `applyPatches replaces first occurrence of each patch in order`() {
        val out =
            applyPatches(
                "abc-abc-abc",
                listOf(
                    JiraCodePatch("abc", "X"),
                    JiraCodePatch("X-", "Y"),
                ),
            )
        assertEquals("Yabc-abc", out)
    }

    @Test
    fun `applyPatches skips blank search patches`() {
        val out = applyPatches("hello", listOf(JiraCodePatch("", "boom"), JiraCodePatch("hello", "world")))
        assertEquals("world", out)
    }

    @Test
    fun `applyPatches returns original on empty patch list`() {
        assertEquals("xyz", applyPatches("xyz", emptyList()))
    }

    @Test
    fun `JiraDatabaseChange stores its arguments verbatim`() {
        val c = JiraDatabaseChange(database = "pg", description = "d", sql = "SELECT 1")
        assertEquals("pg", c.database)
        assertEquals("d", c.description)
        assertEquals("SELECT 1", c.sql)
    }

    @Test
    fun `JiraCodeImplementation default systemTestFiles is empty list`() {
        val impl =
            JiraCodeImplementation(
                identifiedRepositories = emptyList(),
                identifiedDatabases = emptyList(),
                files = emptyList(),
                testFiles = emptyList(),
                databaseChanges = emptyList(),
                implementationNotes = "",
            )
        assertTrue(impl.systemTestFiles.isEmpty())
    }

    @Test
    fun `JiraCodeImplementation stores systemTestFiles verbatim`() {
        val f =
            JiraCodeFile(
                repository = "r",
                path = "p",
                language = "Kotlin",
                description = "d",
                changeType = FileChangeType.ADD,
                code = "fun f() = 0",
            )
        val impl =
            JiraCodeImplementation(
                identifiedRepositories = listOf("r1"),
                identifiedDatabases = listOf("d1"),
                files = listOf(f),
                testFiles = listOf(f),
                systemTestFiles = listOf(f),
                databaseChanges = listOf(JiraDatabaseChange("pg", "d", "SELECT 1")),
                implementationNotes = "notes",
            )
        assertEquals(listOf(f), impl.systemTestFiles)
    }

    @Test
    fun `JiraMonorepoIdentification defaults are sensible`() {
        val m =
            JiraMonorepoIdentification(
                identifiedApplication = "app",
                relativeRootPath = "",
                reasoning = "r",
                confidence = "HIGH",
                isMonorepo = false,
                techStack = emptyList(),
                codingConventions = "",
                testFramework = "",
                buildTool = "",
                testCommand = "",
            )
        assertEquals("", m.architectureStyle)
        assertEquals("", m.diApproach)
        assertFalse(m.hasUnitTests)
        assertFalse(m.hasIntegrationTests)
        assertFalse(m.hasSystemTests)
        assertEquals("", m.testStructureNotes)
        assertEquals("", m.projectPurpose)
        assertEquals("", m.keyModulesDescription)
        assertEquals("", m.designPatterns)
        assertEquals("", m.dataModelSummary)
        assertEquals("", m.exampleTestPatterns)
    }

    @Test
    fun `JiraMonorepoIdentification stores every provided field verbatim`() {
        val m =
            JiraMonorepoIdentification(
                identifiedApplication = "app",
                relativeRootPath = "services/x",
                reasoning = "r",
                confidence = "LOW",
                isMonorepo = true,
                techStack = listOf("Kotlin"),
                codingConventions = "hexagonal",
                testFramework = "JUnit5",
                buildTool = "Gradle",
                testCommand = "./gradlew test",
                architectureStyle = "hexagonal",
                diApproach = "Spring DI",
                hasUnitTests = true,
                hasIntegrationTests = true,
                hasSystemTests = true,
                testStructureNotes = "see test/",
                projectPurpose = "do X",
                keyModulesDescription = "core/io",
                designPatterns = "Repository",
                dataModelSummary = "Customer → Order",
                exampleTestPatterns = "*Test.kt",
            )
        assertEquals("hexagonal", m.architectureStyle)
        assertEquals("Spring DI", m.diApproach)
        assertTrue(m.hasUnitTests)
        assertTrue(m.hasIntegrationTests)
        assertTrue(m.hasSystemTests)
        assertEquals("see test/", m.testStructureNotes)
        assertEquals("do X", m.projectPurpose)
        assertEquals("core/io", m.keyModulesDescription)
        assertEquals("Repository", m.designPatterns)
        assertEquals("Customer → Order", m.dataModelSummary)
        assertEquals("*Test.kt", m.exampleTestPatterns)
    }

    @Test
    fun `applyPatches replaces only the first occurrence per patch`() {
        val out = applyPatches("aaa", listOf(JiraCodePatch("a", "b")))
        assertEquals("baa", out)
    }
}
