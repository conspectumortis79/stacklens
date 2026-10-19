package com.stacklens.application.service.securityanalysis

import com.stacklens.domain.enums.FileChangeType
import com.stacklens.domain.model.security.SecurityFixResult
import com.stacklens.domain.model.security.SecurityLocalFileChange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SecurityAnalysisModelsCoverageTest {
    @Test
    fun `SecurityFinding defaults are sensible`() {
        val f = SecurityFinding()
        assertEquals("", f.id)
        assertEquals("", f.file)
        assertEquals("", f.type)
        assertEquals("MEDIUM", f.severity)
        assertNull(f.line)
        assertNull(f.snippet)
        assertEquals("", f.description)
        assertEquals("", f.suggestion)
        assertNull(f.suggestedFix)
        assertNull(f.cwe)
        assertNull(f.exploitPayload)
        assertNull(f.attackVector)
        assertNull(f.attackSteps)
        assertNull(f.proofOfConcept)
        assertNull(f.cvssScore)
        assertNull(f.exploitability)
        assertFalse(f.confirmed)
        assertEquals(1, f.iterationFound)
    }

    @Test
    fun `SecurityFinding stores provided values verbatim`() {
        val f =
            SecurityFinding(
                id = "F1",
                file = "src/Foo.kt",
                type = "NULL_POINTER",
                severity = "HIGH",
                line = 42,
                snippet = "foo()",
                description = "d",
                suggestion = "s",
                suggestedFix = "fix",
                cwe = "CWE-476",
                exploitPayload = "payload",
                attackVector = "vector",
                attackSteps = listOf("step1", "step2"),
                proofOfConcept = "poc",
                cvssScore = "7.5",
                exploitability = "easy",
                confirmed = true,
                iterationFound = 2,
            )
        assertEquals("F1", f.id)
        assertEquals("src/Foo.kt", f.file)
        assertEquals("NULL_POINTER", f.type)
        assertEquals("HIGH", f.severity)
        assertEquals(42, f.line)
        assertEquals("foo()", f.snippet)
        assertEquals("d", f.description)
        assertEquals("s", f.suggestion)
        assertEquals("fix", f.suggestedFix)
        assertEquals("CWE-476", f.cwe)
        assertEquals("payload", f.exploitPayload)
        assertEquals("vector", f.attackVector)
        assertEquals(listOf("step1", "step2"), f.attackSteps)
        assertEquals("poc", f.proofOfConcept)
        assertEquals("7.5", f.cvssScore)
        assertEquals("easy", f.exploitability)
        assertTrue(f.confirmed)
        assertEquals(2, f.iterationFound)
    }

    @Test
    fun `ParsedFileChange defaults are sensible`() {
        val c = ParsedFileChange()
        assertEquals("", c.path)
        assertEquals("", c.code)
        assertEquals(FileChangeType.UPDATE, c.changeType)
    }

    @Test
    fun `ParsedFileChange stores provided values verbatim`() {
        val c = ParsedFileChange(path = "p", code = "x", changeType = FileChangeType.ADD)
        assertEquals(FileChangeType.ADD, c.changeType)
    }

    @Test
    fun `SecurityFixResult stores provided values verbatim`() {
        val local = SecurityLocalFileChange(path = "p", code = "x", changeType = FileChangeType.ADD)
        val r =
            SecurityFixResult(
                prUrl = "u",
                branchName = "b",
                explanation = "e",
                filesChanged = listOf("a.kt"),
                isLocalRepo = true,
                localChanges = listOf(local),
            )
        assertEquals("u", r.prUrl)
        assertEquals("b", r.branchName)
        assertEquals(listOf("a.kt"), r.filesChanged)
        assertTrue(r.isLocalRepo)
        assertEquals(listOf(local), r.localChanges)
    }

    @Test
    fun `SecurityLocalFileChange stores provided values verbatim`() {
        val c = SecurityLocalFileChange(path = "p", code = "x", changeType = FileChangeType.DELETE)
        assertEquals(FileChangeType.DELETE, c.changeType)
    }

    @Test
    fun `SecurityRepoContext stores provided values verbatim`() {
        val c =
            SecurityRepoContext(
                appName = "app",
                projectPath = "p",
                projectTree = "tree",
                fileContents = "files",
            )
        assertEquals("app", c.appName)
        assertEquals("p", c.projectPath)
        assertEquals("tree", c.projectTree)
        assertEquals("files", c.fileContents)
    }

    @Test
    fun `SecurityFileContext default allFiles is empty`() {
        val c =
            SecurityFileContext(
                projectName = "p",
                tree = "t",
                fileContent = "f",
                testContent = "t",
            )
        assertTrue(c.allFiles.isEmpty())
    }

    @Test
    fun `SecurityFileContext stores provided values verbatim`() {
        val c =
            SecurityFileContext(
                projectName = "p",
                tree = "t",
                fileContent = "f",
                testContent = "t",
                allFiles = listOf("a" to "b"),
            )
        assertEquals(listOf("a" to "b"), c.allFiles)
    }

    @Test
    fun `computeUnifiedDiff returns empty string for identical input`() {
        assertEquals("", computeUnifiedDiff("a\nb", "a\nb", "p"))
        assertEquals("", computeUnifiedDiff("", "", "p"))
    }

    @Test
    fun `computeUnifiedDiff returns a non-empty patch for differing input`() {
        val patch = computeUnifiedDiff("a\nb\nc", "a\nB\nc", "p")
        assertTrue(patch.isNotEmpty())
    }

    @Test
    fun `computeUnifiedDiff handles blank input by treating it as empty`() {
        val patch = computeUnifiedDiff("   ", "abc", "p")
        assertTrue(patch.isNotEmpty())
    }

    @Test
    fun `isTestFile returns true for paths containing test or spec`() {
        assertTrue(isTestFile("src/test/Foo.kt"))
        assertTrue(isTestFile("path/with/test/in/name.kt"))
        assertTrue(isTestFile("path/with/spec/in/name.kt"))
        assertTrue(isTestFile("TEST_File.kt"))
        assertTrue(isTestFile("my_spec_file.kt"))
    }

    @Test
    fun `isTestFile returns false for paths without test or spec`() {
        assertFalse(isTestFile("src/main/Foo.kt"))
        assertFalse(isTestFile("src/production/Bar.kt"))
    }

    @Test
    fun `isRelatedToFile matches paths containing the file basename`() {
        assertTrue(isRelatedToFile("src/test/FooTest.kt", "src/main/Foo.kt"))
        assertTrue(isRelatedToFile("path/Foo_spec.kt", "path/Foo.kt"))
    }

    @Test
    fun `isRelatedToFile returns false when basename does not appear`() {
        assertFalse(isRelatedToFile("src/test/BarTest.kt", "src/main/Foo.kt"))
    }

    @Test
    fun `extensionToLanguage maps common extensions correctly`() {
        assertEquals("kotlin", extensionToLanguage("foo.kt"))
        assertEquals("kotlin", extensionToLanguage("foo.kts"))
        assertEquals("java", extensionToLanguage("Foo.java"))
        assertEquals("python", extensionToLanguage("foo.py"))
        assertEquals("typescript", extensionToLanguage("foo.ts"))
        assertEquals("typescript", extensionToLanguage("foo.tsx"))
        assertEquals("javascript", extensionToLanguage("foo.js"))
        assertEquals("javascript", extensionToLanguage("foo.jsx"))
        assertEquals("go", extensionToLanguage("foo.go"))
        assertEquals("ruby", extensionToLanguage("foo.rb"))
        assertEquals("csharp", extensionToLanguage("foo.cs"))
        assertEquals("php", extensionToLanguage("foo.php"))
        assertEquals("swift", extensionToLanguage("foo.swift"))
        assertEquals("rust", extensionToLanguage("foo.rs"))
        assertEquals("cpp", extensionToLanguage("foo.cpp"))
        assertEquals("cpp", extensionToLanguage("foo.cc"))
        assertEquals("cpp", extensionToLanguage("foo.cxx"))
        assertEquals("c", extensionToLanguage("foo.c"))
        assertEquals("html", extensionToLanguage("foo.html"))
        assertEquals("xml", extensionToLanguage("foo.xml"))
        assertEquals("sql", extensionToLanguage("foo.sql"))
        assertEquals("text", extensionToLanguage("foo.unknown"))
        assertEquals("text", extensionToLanguage("noextension"))
        assertEquals("text", extensionToLanguage("Makefile"))
    }

    @Test
    fun `buildFixTicket maps CRITICAL severity to Blocker priority`() {
        val finding =
            SecurityFinding(
                severity = "CRITICAL",
                description = "x",
                file = "Foo.kt",
                cwe = "CWE-89",
                type = "INJECTION",
            )
        val ticket = buildFixTicket("PROJ-1", finding, "fix")
        assertEquals("Blocker", ticket.priority)
        assertEquals(listOf("security", "security-analysis", "CWE-89"), ticket.labels)
        assertTrue("Security Vulnerability" in ticket.description)
    }

    @Test
    fun `buildFixTicket maps HIGH severity to High priority`() {
        val finding = SecurityFinding(severity = "HIGH", description = "x", file = "Foo.kt")
        val ticket = buildFixTicket("PROJ-1", finding, "fix")
        assertEquals("High", ticket.priority)
    }

    @Test
    fun `buildFixTicket maps MEDIUM severity to Medium priority`() {
        val finding = SecurityFinding(severity = "MEDIUM", description = "x", file = "Foo.kt")
        val ticket = buildFixTicket("PROJ-1", finding, "fix")
        assertEquals("Medium", ticket.priority)
    }

    @Test
    fun `buildFixTicket maps LOW severity to Low priority`() {
        val finding = SecurityFinding(severity = "LOW", description = "x", file = "Foo.kt")
        val ticket = buildFixTicket("PROJ-1", finding, "fix")
        assertEquals("Low", ticket.priority)
    }

    @Test
    fun `buildFixTicket drops blank cwe from labels`() {
        val finding = SecurityFinding(severity = "LOW", description = "x", file = "Foo.kt", cwe = "")
        val ticket = buildFixTicket("PROJ-1", finding, "fix")
        assertEquals(listOf("security", "security-analysis"), ticket.labels)
    }

    @Test
    fun `buildCodeImpl builds a JiraCodeImplementation from ParsedFileChanges`() {
        val finding = SecurityFinding(id = "F1", description = "d", file = "Foo.kt", cwe = "CWE-89")
        val changes = listOf(ParsedFileChange(path = "Foo.kt", code = "x", changeType = FileChangeType.UPDATE))
        val impl = buildCodeImpl("repo", finding, changes)
        assertEquals(listOf("repo"), impl.identifiedRepositories)
        assertEquals(1, impl.files.size)
        assertEquals("Foo.kt", impl.files[0].path)
        assertEquals("kotlin", impl.files[0].language)
        assertEquals(FileChangeType.UPDATE, impl.files[0].changeType)
        assertTrue("CWE-89" in impl.files[0].description)
    }
}
