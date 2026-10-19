package com.stacklens.application.service

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.out.process.AgentProcessPort
import com.stacklens.testsupport.FakeI18n
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

class RexCoverageStepAffectedPathsCoverageTest {
    @Test
    @Suppress("UNCHECKED_CAST")
    fun `the coverage step asks the ai fixer for the paths named in the coverage output`() {
        val processPort = mock<AgentProcessPort>()
        whenever(processPort.findMakeTarget(any(), any(), any())).thenReturn("coverage")
        whenever(processPort.runMakeTarget(any(), any(), any()))
            .thenReturn(0 to "Lines: 61.5%\ntests/unit/ReportTest.php")
        val aiFixer = mock<RexAiFixService>()
        val seen = mutableListOf<String>()
        whenever(aiFixer.extractMentionedPaths(any(), any(), any())).thenAnswer { invocation ->
            seen += invocation.arguments[0] as String
            listOf("tests/unit/ReportTest.php")
        }
        whenever(
            aiFixer.executeFixAttempt(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
            ),
        ).thenAnswer { invocation ->
            val paths = (invocation.arguments[7] as () -> List<String>).invoke()
            check(paths.isNotEmpty()) { "the coverage step must hand the mentioned paths over" }
            0 to "Lines: 72.0%"
        }
        val run =
            AgentPipelineRun(
                id = 3L,
                rawText = "raw",
                repoPath = "/tmp/rex",
                featureSlug = "feat",
                createdAt = 1L,
            )
        val step = RexCoverageStep(processPort, FakeI18n(), aiFixer)

        step.run(
            repoPath = "/tmp/rex",
            repoRoot = File("/tmp/rex"),
            sb = StringBuilder(),
            run = run,
            model = "gpt-test",
            flush = { },
        )

        assertTrue(seen.isNotEmpty(), "extractMentionedPaths was never reached")
        assertTrue(seen.first().contains("ReportTest.php"), seen.toString())
    }
}
