package com.stacklens.application.service.securityanalysis

import com.stacklens.domain.enums.FileChangeType
import com.stacklens.domain.model.security.SecurityFixResult
import com.stacklens.domain.model.security.SecurityLocalFileChange
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SecurityAnalysisModelsTest {
    @Test
    fun `SecurityFinding defaults`() {
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
    fun `SecurityFinding with all fields populated`() {
        val f =
            SecurityFinding(
                id = "F",
                file = "f.kt",
                type = "SECURITY_VULN",
                severity = "CRITICAL",
                line = 1,
                snippet = "code",
                description = "d",
                suggestion = "s",
                suggestedFix = "patch",
                cwe = "CWE-79",
                exploitPayload = "p",
                attackVector = "v",
                attackSteps = listOf("a", "b"),
                proofOfConcept = "poc",
                cvssScore = "9.5",
                exploitability = "high",
                confirmed = true,
                iterationFound = 2,
            )
        assertEquals("F", f.id)
        assertEquals("SECURITY_VULN", f.type)
        assertEquals("CRITICAL", f.severity)
        assertEquals(1, f.line)
        assertEquals("code", f.snippet)
        assertEquals("d", f.description)
        assertEquals("s", f.suggestion)
        assertEquals("patch", f.suggestedFix)
        assertEquals("CWE-79", f.cwe)
        assertEquals("p", f.exploitPayload)
        assertEquals("v", f.attackVector)
        assertEquals(listOf("a", "b"), f.attackSteps)
        assertEquals("poc", f.proofOfConcept)
        assertEquals("9.5", f.cvssScore)
        assertEquals("high", f.exploitability)
        assertTrue(f.confirmed)
        assertEquals(2, f.iterationFound)
    }

    @Test
    fun `SecurityFinding equality`() {
        val a = SecurityFinding(id = "X", severity = "HIGH")
        val b = SecurityFinding(id = "X", severity = "HIGH")
        assertEquals(a, b)
        assertNotEquals(a, SecurityFinding(id = "Y", severity = "HIGH"))
    }

    @Test
    fun `ParsedFileChange defaults`() {
        val p = ParsedFileChange()
        assertEquals("", p.path)
        assertEquals("", p.code)
        assertEquals(FileChangeType.UPDATE, p.changeType)
    }

    @Test
    fun `ParsedFileChange with all fields`() {
        val p = ParsedFileChange(path = "p", code = "c", changeType = FileChangeType.CREATE)
        assertEquals("p", p.path)
        assertEquals("c", p.code)
        assertEquals(FileChangeType.CREATE, p.changeType)
    }

    @Test
    fun `SecurityFixResult with values`() {
        val r =
            SecurityFixResult(
                prUrl = "u",
                branchName = "b",
                explanation = "e",
                filesChanged = listOf("a"),
                isLocalRepo = true,
                localChanges = emptyList(),
            )
        assertEquals("u", r.prUrl)
        assertEquals("b", r.branchName)
        assertEquals("e", r.explanation)
        assertEquals(listOf("a"), r.filesChanged)
        assertTrue(r.isLocalRepo)
    }

    @Test
    fun `SecurityLocalFileChange populates fields`() {
        val c = SecurityLocalFileChange(path = "p", code = "c", changeType = FileChangeType.CREATE)
        assertEquals("p", c.path)
        assertEquals("c", c.code)
        assertEquals(FileChangeType.CREATE, c.changeType)
    }

    @Test
    fun `ParsedFix populates fields`() {
        val p = ParsedFix(explanation = "e", changes = listOf(ParsedFileChange(path = "p")))
        assertEquals("e", p.explanation)
        assertEquals(1, p.changes.size)
    }

    @Test
    fun `SecurityRepoContext populates fields`() {
        val c =
            SecurityRepoContext(
                appName = "a",
                projectPath = "p",
                projectTree = "t",
                fileContents = "f",
            )
        assertEquals("a", c.appName)
        assertEquals("p", c.projectPath)
        assertEquals("t", c.projectTree)
        assertEquals("f", c.fileContents)
    }

    @Test
    fun `SecurityFileContext populates fields`() {
        val c =
            SecurityFileContext(
                projectName = "p",
                tree = "t",
                fileContent = "f",
                testContent = "tc",
            )
        assertEquals("p", c.projectName)
        assertEquals("t", c.tree)
        assertEquals("f", c.fileContent)
        assertEquals("tc", c.testContent)
        assertEquals(emptyList<Pair<String, String>>(), c.allFiles)
    }

    @Test
    fun `SecurityFileContext with allFiles`() {
        val c =
            SecurityFileContext(
                projectName = "p",
                tree = "t",
                fileContent = "f",
                testContent = "tc",
                allFiles = listOf("a" to "b"),
            )
        assertEquals(1, c.allFiles.size)
    }

    @Test
    fun `computeUnifiedDiff returns empty when content is identical`() {
        assertEquals("", computeUnifiedDiff("a\nb", "a\nb", "f.kt"))
    }

    @Test
    fun `computeUnifiedDiff returns non-empty when content differs`() {
        val diff = computeUnifiedDiff("a\nb", "a\nc", "f.kt")
        assertFalse(diff.isBlank())
        assertTrue(diff.contains("f.kt"))
    }

    @Test
    fun `computeUnifiedDiff trims trailing newlines before comparing`() {
        assertEquals("", computeUnifiedDiff("a", "a\n", "f.kt"))
    }

    @Test
    fun `computeUnifiedDiff handles blank inputs`() {
        assertEquals("", computeUnifiedDiff("", "", "f.kt"))
    }

    @Test
    fun `computeUnifiedDiff with blank newContent but non-blank original`() {
        val diff = computeUnifiedDiff("a\nb\nc", "", "f.kt")
        assertFalse(diff.isBlank())
        assertTrue(diff.contains("f.kt"))
    }

    @Test
    fun `computeUnifiedDiff with blank original but non-blank newContent`() {
        val diff = computeUnifiedDiff("", "a\nb\nc", "f.kt")
        assertFalse(diff.isBlank())
        assertTrue(diff.contains("f.kt"))
    }

    @Test
    fun `isTestFile returns true when path contains test`() {
        assertTrue(isTestFile("src/test/FooTest.kt"))
    }

    @Test
    fun `isTestFile returns true when path contains spec`() {
        assertTrue(isTestFile("src/spec/foo.spec.ts"))
    }

    @Test
    fun `isTestFile returns true case-insensitively`() {
        assertTrue(isTestFile("src/TEST/Foo.kt"))
        assertTrue(isTestFile("src/SPEC/foo.ts"))
    }

    @Test
    fun `isTestFile returns false for plain source files`() {
        assertFalse(isTestFile("src/main/Foo.kt"))
    }

    @Test
    fun `isRelatedToFile matches base name in test path`() {
        assertTrue(isRelatedToFile("src/test/FooTest.kt", "src/main/Foo.kt"))
    }

    @Test
    fun `isRelatedToFile is case-insensitive`() {
        assertTrue(isRelatedToFile("src/test/footest.kt", "src/main/Foo.kt"))
    }

    @Test
    fun `isRelatedToFile returns false for unrelated base name`() {
        assertFalse(isRelatedToFile("src/test/BarTest.kt", "src/main/Foo.kt"))
    }

    @Test
    fun `extensionToLanguage recognises common file types`() {
        assertEquals("kotlin", extensionToLanguage("Foo.kt"))
        assertEquals("kotlin", extensionToLanguage("build.gradle.kts"))
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
    }

    @Test
    fun `extensionToLanguage returns text for unknown extensions`() {
        assertEquals("text", extensionToLanguage("foo.unknownext"))
    }

    @Test
    fun `extensionToLanguage returns text for files without an extension`() {
        assertEquals("text", extensionToLanguage("Makefile"))
    }

    @Test
    fun `buildFixTicket maps CRITICAL to Blocker priority`() {
        val f = SecurityFinding(description = "d", severity = "CRITICAL", cwe = "CWE-79", file = "f.kt")
        val t = buildFixTicket("PROJ-1", f, "fix")
        assertEquals("Blocker", t.priority)
        assertTrue(t.labels.contains("CWE-79"))
        assertTrue(t.labels.contains("security"))
    }

    @Test
    fun `buildFixTicket maps HIGH to High priority`() {
        val t = buildFixTicket("PROJ-1", SecurityFinding(severity = "HIGH"), "fix")
        assertEquals("High", t.priority)
    }

    @Test
    fun `buildFixTicket maps MEDIUM to Medium priority`() {
        val t = buildFixTicket("PROJ-1", SecurityFinding(severity = "MEDIUM"), "fix")
        assertEquals("Medium", t.priority)
    }

    @Test
    fun `buildFixTicket maps other severities to Low priority`() {
        val t = buildFixTicket("PROJ-1", SecurityFinding(severity = "LOW"), "fix")
        assertEquals("Low", t.priority)
    }

    @Test
    fun `buildFixTicket summary truncates long descriptions to 72 chars`() {
        val longDesc = "x".repeat(200)
        val t = buildFixTicket("PROJ-1", SecurityFinding(description = longDesc), "fix")
        assertTrue(t.summary.length <= 72 + "Security Fix: ".length)
    }

    @Test
    fun `buildCodeImpl populates files with the security-fix metadata`() {
        val finding = SecurityFinding(id = "F", cwe = "CWE-79", description = "d", file = "f.kt")
        val changes = listOf(ParsedFileChange(path = "f.kt", code = "code", changeType = FileChangeType.UPDATE))
        val impl = buildCodeImpl("svc", finding, changes)
        assertEquals(1, impl.files.size)
        assertEquals("svc", impl.files[0].repository)
        assertEquals("f.kt", impl.files[0].path)
        assertEquals("kotlin", impl.files[0].language)
        assertEquals(FileChangeType.UPDATE, impl.files[0].changeType)
        assertEquals("code", impl.files[0].code)
        assertTrue(impl.implementationNotes.contains("F"))
    }
}
