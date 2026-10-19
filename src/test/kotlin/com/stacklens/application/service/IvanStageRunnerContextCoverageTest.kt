package com.stacklens.application.service

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.File

class IvanStageRunnerContextCoverageTest {
    private fun run() =
        AgentPipelineRun(
            id = 5L,
            rawText = "raw",
            repoPath = "/tmp/ivan",
            featureSlug = "feat",
            createdAt = 1L,
        )

    @Test
    fun `the run context carries the binaries and the branch state`() {
        val ctx = IvanStageRunner.IvanStageContext(run(), StringBuilder())

        ctx.root = File("/tmp/ivan")
        ctx.gitBin = "/usr/bin/git"
        ctx.makeBin = "/usr/bin/make"
        ctx.featureBranch = "feature/x"
        ctx.checkedOutBase = true
        ctx.featureBranchCheckoutFailed = false

        assertEquals(File("/tmp/ivan"), ctx.root)
        assertEquals("/usr/bin/git", ctx.gitBin)
        assertEquals("/usr/bin/make", ctx.makeBin)
        assertEquals("feature/x", ctx.featureBranch)
        assertEquals(true, ctx.checkedOutBase)
        assertEquals(false, ctx.featureBranchCheckoutFailed)
    }

    @Test
    fun `a fresh context has no branch decided yet`() {
        val ctx = IvanStageRunner.IvanStageContext(run(), StringBuilder())

        assertNull(ctx.featureBranch)
        assertEquals(false, ctx.checkedOutBase)
        assertEquals(0, ctx.log.length)
    }
}
