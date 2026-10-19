package com.stacklens.application.port.out

import com.stacklens.domain.enums.FileChangeType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JiraTicketModelsTest {
    @Test
    fun `applyPatches returns the original when the patch list is empty`() {
        assertEquals("abc", applyPatches("abc", emptyList()))
    }

    @Test
    fun `applyPatches replaces the first match of each patch`() {
        val patches = listOf(JiraCodePatch(search = "foo", replace = "bar"))
        assertEquals("xbarx", applyPatches("xfoox", patches))
    }

    @Test
    fun `applyPatches applies patches sequentially`() {
        val patches =
            listOf(
                JiraCodePatch(search = "a", replace = "b"),
                JiraCodePatch(search = "b", replace = "c"),
            )
        assertEquals("caa", applyPatches("aaa", patches))
    }

    @Test
    fun `applyPatches skips blank search strings`() {
        val patches =
            listOf(
                JiraCodePatch(search = "", replace = "x"),
                JiraCodePatch(search = " ", replace = "y"),
                JiraCodePatch(search = "a", replace = "b"),
            )
        assertEquals("ba", applyPatches("aa", patches))
    }

    @Test
    fun `applyPatches does not modify content when no patches match`() {
        val patches = listOf(JiraCodePatch(search = "z", replace = "x"))
        assertEquals("abc", applyPatches("abc", patches))
    }

    @Test
    fun `JiraMrFileChange populates fields`() {
        val c = JiraMrFileChange(filePath = "p", diffExcerpt = "d")
        assertEquals("p", c.filePath)
        assertEquals("d", c.diffExcerpt)
    }

    @Test
    fun `JiraRelatedMr populates fields`() {
        val r =
            JiraRelatedMr(
                projectPath = "g/p",
                mrIid = 1L,
                title = "t",
                description = "d",
                state = "merged",
                url = "u",
                foundViaTicketKey = "K",
                foundViaKeyword = "kw",
                fileChanges = emptyList(),
            )
        assertEquals("g/p", r.projectPath)
        assertEquals(1L, r.mrIid)
        assertEquals("merged", r.state)
    }

    @Test
    fun `JiraRepoFile populates fields`() {
        val f = JiraRepoFile(path = "p", content = "c")
        assertEquals("p", f.path)
        assertEquals("c", f.content)
    }

    @Test
    fun `JiraRepoContext populates fields`() {
        val c = JiraRepoContext(repoName = "r", projectId = 1L, files = emptyList())
        assertEquals("r", c.repoName)
        assertEquals(1L, c.projectId)
    }

    @Test
    fun `JiraCodeFile with default patches`() {
        val f =
            JiraCodeFile(
                repository = "r",
                path = "p",
                language = "kotlin",
                description = "d",
                changeType = FileChangeType.UPDATE,
                code = "c",
            )
        assertEquals(emptyList<JiraCodePatch>(), f.patches)
    }

    @Test
    fun `JiraCodeFile with explicit patches`() {
        val f =
            JiraCodeFile(
                repository = "r",
                path = "p",
                language = "kotlin",
                description = "d",
                changeType = FileChangeType.UPDATE,
                code = "c",
                patches = listOf(JiraCodePatch(search = "x", replace = "y")),
            )
        assertEquals(1, f.patches.size)
    }

    @Test
    fun `JiraDatabaseChange populates fields`() {
        val c = JiraDatabaseChange(database = "d", description = "desc", sql = "SELECT 1")
        assertEquals("d", c.database)
        assertEquals("SELECT 1", c.sql)
    }

    @Test
    fun `JiraCodeImplementation with default system tests`() {
        val i =
            JiraCodeImplementation(
                identifiedRepositories = listOf("r"),
                identifiedDatabases = emptyList(),
                files = emptyList(),
                testFiles = emptyList(),
                databaseChanges = emptyList(),
                implementationNotes = "n",
            )
        assertEquals(emptyList<JiraCodeFile>(), i.systemTestFiles)
    }

    @Test
    fun `JiraCodeImplementation with explicit system tests`() {
        val i =
            JiraCodeImplementation(
                identifiedRepositories = listOf("r"),
                identifiedDatabases = emptyList(),
                files = emptyList(),
                testFiles = emptyList(),
                systemTestFiles = listOf(JiraCodeFile(repository = "r", path = "p", language = "kotlin", description = "d", changeType = FileChangeType.UPDATE, code = "c")),
                databaseChanges = emptyList(),
                implementationNotes = "n",
            )
        assertEquals(1, i.systemTestFiles.size)
    }

    @Test
    fun `JiraMonorepoIdentification defaults`() {
        val m =
            JiraMonorepoIdentification(
                identifiedApplication = "a",
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
        assertEquals(false, m.hasUnitTests)
        assertEquals(false, m.hasIntegrationTests)
        assertEquals(false, m.hasSystemTests)
        assertEquals("", m.testStructureNotes)
        assertEquals("", m.projectPurpose)
        assertEquals("", m.keyModulesDescription)
        assertEquals("", m.designPatterns)
        assertEquals("", m.dataModelSummary)
        assertEquals("", m.exampleTestPatterns)
    }

    @Test
    fun `JiraMonorepoIdentification populates all fields`() {
        val m =
            JiraMonorepoIdentification(
                identifiedApplication = "a",
                relativeRootPath = "sub",
                reasoning = "r",
                confidence = "MEDIUM",
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
                testStructureNotes = "src/test/...",
                projectPurpose = "User service",
                keyModulesDescription = "user, role",
                designPatterns = "Repository",
                dataModelSummary = "User entity",
                exampleTestPatterns = "FooTest",
            )
        assertEquals("a", m.identifiedApplication)
        assertEquals("sub", m.relativeRootPath)
        assertEquals(true, m.isMonorepo)
        assertEquals(true, m.hasUnitTests)
        assertEquals("User service", m.projectPurpose)
    }
}
