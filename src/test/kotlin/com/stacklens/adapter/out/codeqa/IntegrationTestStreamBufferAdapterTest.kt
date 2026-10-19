package com.stacklens.adapter.out.codeqa

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.stacklens.adapter.out.codeqa.IntegrationTestStreamPlan
import com.stacklens.adapter.out.shared.JacksonJsonCodecAdapter
import com.stacklens.domain.model.CodeQaAnalysis
import com.stacklens.testsupport.FakeHostDirectoryPort
import com.stacklens.testsupport.FakeSystemInfoPort
import com.stacklens.testsupport.FixedClockPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IntegrationTestStreamBufferAdapterTest {
    private companion object {
        const val EPOCH_MILLIS = 1_750_000_000_000L
    }

    private val codec = JacksonJsonCodecAdapter(jacksonObjectMapper())

    private fun buffer() = IntegrationTestStreamBufferAdapter(FixedClockPort(EPOCH_MILLIS), FakeSystemInfoPort(), FakeHostDirectoryPort(), codec)

    @Test
    fun `emit appends raw text to the buffer`() {
        val buf = buffer()
        val sb = StringBuilder("start\n")
        buf.emit(sb, "next\n")
        assertEquals("start\nnext\n", sb.toString())
    }

    @Test
    fun `header returns the documented banner text`() {
        val h = buffer().header()
        assertTrue(h.contains("StackLens"))
        assertTrue(h.contains("Integration Tests"))
    }

    @Test
    fun `environmentBanner contains host Java and temp root information`() {
        val b = buffer().environmentBanner()
        assertTrue(b.contains("Host:"))
        assertTrue(b.contains("Java:"))
        assertTrue(b.contains("Temp root:"))
    }

    @Test
    fun `environmentBanner renders the injected host facts`() {
        val b = buffer().environmentBanner()
        assertTrue(b.contains("Host: TestOS 1.2.3 (testarch)"))
        assertTrue(b.contains("Java: 21.0.4"))
        assertTrue(b.contains("Temp root: /tmp/stacklens-test"))
    }

    @Test
    fun `environmentBanner strips trailing slashes from the temp root`() {
        val adapter =
            IntegrationTestStreamBufferAdapter(
                FixedClockPort(EPOCH_MILLIS),
                FakeSystemInfoPort(),
                FakeHostDirectoryPort(temporary = "/tmp/stacklens-test/"),
                codec,
            )
        val b = adapter.environmentBanner()
        assertTrue(b.contains("Temp root: /tmp/stacklens-test\n"))
    }

    @Test
    fun `emitStarted writes a STATUS started event with the MR facts`() {
        val sb = StringBuilder()
        buffer().emitStarted(sb, branch = "feat/x", targetBranch = "main", changedFiles = 3, tmpDir = "/tmp/w", os = "TestOS")
        val s = sb.toString()
        assertTrue(s.startsWith("[STATUS]{"))
        assertTrue(s.endsWith("[/STATUS]\n"))
        assertTrue(s.contains("\"ts\":$EPOCH_MILLIS"))
        assertTrue(s.contains("\"type\":\"started\""))
        assertTrue(s.contains("\"branch\":\"feat/x\""))
        assertTrue(s.contains("\"targetBranch\":\"main\""))
        assertTrue(s.contains("\"changedFiles\":3"))
        assertTrue(s.contains("\"tmpDir\":\"/tmp/w\""))
        assertTrue(s.contains("\"os\":\"TestOS\""))
    }

    @Test
    fun `emitStep writes a STATUS step event with the expected fields`() {
        val sb = StringBuilder()
        buffer().emitStep(sb, index = 1, name = "clone", state = "running", detail = "git clone...")
        val s = sb.toString()
        assertTrue(s.contains("\"type\":\"step\""))
        assertTrue(s.contains("\"index\":1"))
        assertTrue(s.contains("\"name\":\"clone\""))
        assertTrue(s.contains("\"state\":\"running\""))
        assertTrue(s.contains("\"detail\":\"git clone...\""))
    }

    @Test
    fun `emitStep with null detail writes null in the payload`() {
        val sb = StringBuilder()
        buffer().emitStep(sb, index = 1, name = "x", state = "done", detail = null)
        assertTrue(sb.toString().contains("\"detail\":null"))
    }

    @Test
    fun `emitPlan writes a STATUS services event with the plan fields`() {
        val plan =
            IntegrationTestStreamPlan(
                services = listOf("db", "web"),
                testCommands = listOf("npm test"),
                projectTypes = listOf("node"),
                testStrategy = "unit",
                testTargets = listOf("src/a.test.ts"),
                reasoning = "targeted",
                waitSeconds = 7,
            )
        val sb = StringBuilder()
        buffer().emitPlan(sb, plan)
        val s = sb.toString()
        assertTrue(s.contains("\"type\":\"services\""))
        assertTrue(s.contains("\"services\":[\"db\",\"web\"]"))
        assertTrue(s.contains("\"testCommands\":[\"npm test\"]"))
        assertTrue(s.contains("\"projectTypes\":[\"node\"]"))
        assertTrue(s.contains("\"testStrategy\":\"unit\""))
        assertTrue(s.contains("\"testTargets\":[\"src/a.test.ts\"]"))
        assertTrue(s.contains("\"reasoning\":\"targeted\""))
        assertTrue(s.contains("\"waitSeconds\":7"))
    }

    @Test
    fun `emitWaiting writes a STATUS waiting event with seconds and services`() {
        val sb = StringBuilder()
        buffer().emitWaiting(sb, seconds = 5, services = listOf("db"))
        val s = sb.toString()
        assertTrue(s.contains("\"type\":\"waiting\""))
        assertTrue(s.contains("\"seconds\":5"))
        assertTrue(s.contains("\"services\":[\"db\"]"))
    }

    @Test
    fun `emitService writes a STATUS service event`() {
        val sb = StringBuilder()
        buffer().emitService(sb, name = "db", state = "ready")
        val s = sb.toString()
        assertTrue(s.contains("\"type\":\"service\""))
        assertTrue(s.contains("\"name\":\"db\""))
        assertTrue(s.contains("\"state\":\"ready\""))
    }

    @Test
    fun `emitTestsRunning writes the queued command list with running states`() {
        val sb = StringBuilder()
        buffer().emitTestsRunning(sb, listOf("a", "b"))
        val s = sb.toString()
        assertTrue(s.contains("\"type\":\"tests\""))
        assertTrue(s.contains("\"state\":\"running\""))
        assertTrue(
            s.contains(
                "\"commands\":[{\"index\":0,\"command\":\"a\",\"state\":\"running\"},{\"index\":1,\"command\":\"b\",\"state\":\"running\"}]",
            ),
        )
    }

    @Test
    fun `emitTestRunning writes a running test event`() {
        val sb = StringBuilder()
        buffer().emitTestRunning(sb, index = 2, command = "npm test")
        val s = sb.toString()
        assertTrue(s.contains("\"type\":\"test\""))
        assertTrue(s.contains("\"index\":2"))
        assertTrue(s.contains("\"command\":\"npm test\""))
        assertTrue(s.contains("\"state\":\"running\""))
    }

    @Test
    fun `emitTestFinished writes a passed test event for a zero exit code`() {
        val sb = StringBuilder()
        buffer().emitTestFinished(sb, index = 2, command = "npm test", exitCode = 0)
        val s = sb.toString()
        assertTrue(s.contains("\"type\":\"test\""))
        assertTrue(s.contains("\"index\":2"))
        assertTrue(s.contains("\"command\":\"npm test\""))
        assertTrue(s.contains("\"state\":\"passed\""))
        assertTrue(s.contains("\"exitCode\":0"))
    }

    @Test
    fun `emitTestFinished writes a failed test event for a non-zero exit code`() {
        val sb = StringBuilder()
        buffer().emitTestFinished(sb, index = 2, command = "npm test", exitCode = 1)
        val s = sb.toString()
        assertTrue(s.contains("\"state\":\"failed\""))
        assertTrue(s.contains("\"exitCode\":1"))
    }

    @Test
    fun `emitTestsSummary writes the done event with totals and results`() {
        val results = listOf("a" to true, "b" to false)
        val sb = StringBuilder()
        buffer().emitTestsSummary(sb, passed = 1, total = 2, results = results)
        val s = sb.toString()
        assertTrue(s.contains("\"type\":\"tests\""))
        assertTrue(s.contains("\"state\":\"done\""))
        assertTrue(s.contains("\"passed\":1"))
        assertTrue(s.contains("\"total\":2"))
        assertTrue(s.contains("\"results\":${codec.encode(results)}"))
    }

    @Test
    fun `emitError writes a STATUS error event`() {
        val sb = StringBuilder()
        buffer().emitError(sb, "boom")
        val s = sb.toString()
        assertTrue(s.contains("\"type\":\"error\""))
        assertTrue(s.contains("\"message\":\"boom\""))
    }

    @Test
    fun `appendTestLog appends a timestamped line to the entity's log`() {
        val a = CodeQaAnalysis(mrUrl = "u")
        buffer().appendTestLog(a, "starting tests")
        assertNotNull(a.integrationTestLog)
        assertTrue(a.integrationTestLog!!.contains("starting tests"))
        assertTrue(a.integrationTestLog!!.matches(Regex(".*\\[\\d{2}:\\d{2}:\\d{2}\\] starting tests\n")))
    }

    @Test
    fun `appendTestLog appends to existing log content without overwriting`() {
        val a = CodeQaAnalysis(mrUrl = "u", integrationTestLog = "previous line\n")
        buffer().appendTestLog(a, "next line")
        assertTrue(a.integrationTestLog!!.startsWith("previous line\n"))
        assertTrue(a.integrationTestLog!!.endsWith("next line\n"))
    }

    @Test
    fun `appendTestLog stamps the injected clock`() {
        val a = CodeQaAnalysis(mrUrl = "u")
        buffer().appendTestLog(a, "started")
        assertEquals("[15:06:40] started\n", a.integrationTestLog)
    }
}
