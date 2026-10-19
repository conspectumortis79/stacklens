package com.stacklens.adapter.out.codeqa

import com.stacklens.application.port.out.process.ProcessResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.File

class CodeQaDataClassTest {
    @Test
    fun `CloneResult with only success param uses null errorMessage default`() {
        val r = GitCloneOrchestrator.CloneResult(success = true)
        assertEquals(true, r.success)
        assertNull(r.errorMessage)
    }

    @Test
    fun `CloneResult with all params stores both`() {
        val r = GitCloneOrchestrator.CloneResult(success = false, errorMessage = "clone failed")
        assertEquals(false, r.success)
        assertEquals("clone failed", r.errorMessage)
    }

    @Test
    fun `CloneResult equals and hashCode`() {
        val a = GitCloneOrchestrator.CloneResult(true)
        val b = GitCloneOrchestrator.CloneResult(true)
        val c = GitCloneOrchestrator.CloneResult(false, "error")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
        assertNotNull(a.toString())
    }

    @Test
    fun `UpResult stores success and composeDir`() {
        val dir = File("/tmp/compose")
        val r = DockerTestRunner.UpResult(success = true, composeDir = dir)
        assertEquals(true, r.success)
        assertEquals(dir, r.composeDir)
    }

    @Test
    fun `UpResult equals and hashCode`() {
        val dir = File("/tmp/compose")
        val a = DockerTestRunner.UpResult(true, dir)
        val b = DockerTestRunner.UpResult(true, dir)
        val c = DockerTestRunner.UpResult(false, dir)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
        assertNotNull(a.toString())
    }

    @Test
    fun `TestOutput stores command result and passed flag`() {
        val result = ProcessResult(exitCode = 0, stdout = "ok", stderr = "")
        val out = DockerTestRunner.TestOutput(command = "pytest", result = result, passed = true)
        assertEquals("pytest", out.command)
        assertEquals(result, out.result)
        assertEquals(true, out.passed)
    }

    @Test
    fun `TestOutput equals and hashCode`() {
        val result = ProcessResult(exitCode = 1, stdout = "", stderr = "err")
        val a = DockerTestRunner.TestOutput("cmd", result, false)
        val b = DockerTestRunner.TestOutput("cmd", result, false)
        val c = DockerTestRunner.TestOutput("other", result, false)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
        assertNotNull(a.toString())
    }
}
