package com.stacklens.application.service.pipeline

import com.stacklens.application.port.model.pipeline.AgentPipelineRun
import com.stacklens.application.port.out.AiConfigPort
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.appconfig.CopilotConfigPort
import com.stacklens.application.port.out.database.DatabaseSchemaPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.service.pipeline.loopback.HANDOFF_TO_FINN
import com.stacklens.application.service.pipeline.loopback.QuinnLoopback
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.util.concurrent.atomic.AtomicInteger

class QuinnLoopbackCancelledAfterRetessCoverageTest {
    @Test
    fun `a cancellation during the second tess pass stops the loop before syd`() {
        val run =
            AgentPipelineRun(
                id = 42L,
                rawText = "demo",
                repoPath = null,
                featureSlug = "demo",
                createdAt = System.currentTimeMillis(),
            )
        val tessCalls = AtomicInteger(0)
        val streamFn: (String, String, java.io.File?, (String) -> Unit) -> String = { _, prompt, _, onChunk ->
            onChunk("partial")
            when (prompt) {
                "prompt for tess" ->
                    when (tessCalls.incrementAndGet()) {
                        1 -> "tess erste\n$HANDOFF_TO_FINN finn bitte nachbessern"
                        else -> {
                            run.cancelled = true
                            "tess zweite"
                        }
                    }

                else -> "stubbed-stream"
            }
        }
        val ctx =
            PipelineContext(
                run,
                "test-model",
                mock<CopilotStreamingPort>(),
                mock<CopilotConfigPort>(),
                mock<I18nPort>(),
                mock<AiConfigPort>(),
                mock<DatabaseConfigPort>(),
                mock<DatabaseSchemaPort>(),
                { agentName, _ -> "prompt for $agentName" },
                { _, _, _ -> },
                streamFn,
                null,
            )
        ctx.quinnOutput = "🔄 HANDOFF_TO_TESS mehr Tests"

        val result = QuinnLoopback().runIfRequested(ctx)

        assertTrue(result)
        assertTrue(run.cancelled)
        assertEquals(2, tessCalls.get())
        assertEquals("tess zweite", ctx.tessOutput)
    }
}
