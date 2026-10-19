package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.ai.AiAttachment
import com.stacklens.application.port.out.ai.AiTokenUsagePort
import com.stacklens.application.port.out.ai.AiUsageExtractor
import com.stacklens.application.port.out.ai.CopilotStreamingPort
import com.stacklens.application.port.out.ai.ExtractedUsage
import com.stacklens.application.port.out.ai.TokenEstimator
import com.stacklens.domain.model.AiTokenUsageEntity
import com.stacklens.domain.model.AiUsage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.whenever
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class TokenTrackingCopilotPortTest {
    private lateinit var delegate: CopilotStreamingPort
    private lateinit var store: RecordingStore
    private lateinit var extractor: AiUsageExtractor
    private lateinit var port: TokenTrackingCopilotPort
    private val fixedClock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC)

    @BeforeEach
    fun setUp() {
        delegate = mock()
        whenever(delegate.providerLabel).thenReturn("OpenAI")
        store = RecordingStore()
        extractor = mock()
        whenever(extractor.extract(any(), any())).thenReturn(null)
        port =
            TokenTrackingCopilotPort(
                delegate = delegate,
                usagePort = store,
                extractor = extractor,
                tokenEstimator = TokenEstimator.Default,
                clock = fixedClock,
            )
    }

    @AfterEach
    fun tearDown() {
        port.clearContext()
    }

    @Test
    fun `records one row per call when context is set`() {
        whenever(delegate.execute("m", "p", null)).thenReturn("out")
        whenever(extractor.extract("out", "OpenAI")).thenReturn(ExtractedUsage(10, 5))

        port.withContext("loadtest", 42L) {
            port.execute("m", "p", null)
        }

        val rows = store.snapshot("loadtest", 42L)
        assertEquals(1, rows.size)
        val r = rows[0]
        assertEquals(0, r.callIndex)
        assertEquals(10L, r.promptTokens)
        assertEquals(5L, r.completionTokens)
        assertEquals("m", r.model)
        assertEquals("OpenAI", r.provider)
    }

    @Test
    fun `does not record when no context is set`() {
        whenever(delegate.execute("m", "p", null)).thenReturn("out")
        port.execute("m", "p", null)
        assertEquals(0, store.totalRows)
    }

    @Test
    fun `call index increments per call on the same thread`() {
        whenever(delegate.execute("m", "p", null)).thenReturn("o1", "o2", "o3")
        whenever(extractor.extract(any(), any())).thenReturn(ExtractedUsage(1, 1))

        port.withContext("code", 1L) {
            port.execute("m", "p", null)
            port.execute("m", "p", null)
            port.execute("m", "p", null)
        }

        val rows = store.snapshot("code", 1L).map { it.callIndex }
        assertEquals(listOf(0, 1, 2), rows)
    }

    @Test
    fun `context is cleared after withContext even on exception`() {
        whenever(delegate.execute("m", "p", null)).thenReturn("o")
        try {
            port.withContext("x", 1L) {
                port.execute("m", "p", null)
                throw RuntimeException("boom")
            }
        } catch (_: RuntimeException) {
        }
        port.execute("m", "p", null)
        assertEquals(1, store.totalRows)
    }

    @Test
    fun `extractor result is used when present`() {
        whenever(delegate.execute("m", "p", null)).thenReturn("o")
        whenever(extractor.extract("o", "OpenAI")).thenReturn(ExtractedUsage(100, 50))

        port.withContext("sql", 1L) { port.execute("m", "p", null) }
        val row = store.snapshot("sql", 1L).single()
        assertEquals(100L, row.promptTokens)
        assertEquals(50L, row.completionTokens)
    }

    @Test
    fun `estimator fallback is used when extractor returns null`() {
        whenever(delegate.execute("m", "a".repeat(40), null)).thenReturn("b".repeat(20))
        whenever(extractor.extract(any(), any())).thenReturn(null)

        port.withContext("x", 1L) { port.execute("m", "a".repeat(40), null) }
        val row = store.snapshot("x", 1L).single()
        assertEquals(10L, row.promptTokens)
        assertEquals(5L, row.completionTokens)
    }

    @Test
    fun `estimator fallback is used when extractor throws (defensive)`() {
        whenever(delegate.execute("m", "p", null)).thenReturn("o")
        whenever(extractor.extract(any(), any())).thenReturn(null)

        port.withContext("x", 1L) { port.execute("m", "p", null) }
        val row = store.snapshot("x", 1L).single()
        assertEquals(1L, row.promptTokens)
        assertEquals(1L, row.completionTokens)
    }

    @Test
    fun `filter savings are added to the next call then consumed`() {
        whenever(delegate.execute("m", "p", null)).thenReturn("o")
        whenever(extractor.extract(any(), any())).thenReturn(ExtractedUsage(1, 1))

        port.withContext("x", 1L) {
            port.addFilterSavings(500L)
            port.execute("m", "p", null)
            port.execute("m", "p", null)
        }
        val rows = store.snapshot("x", 1L)
        assertEquals(500L, rows[0].filterTokensSaved)
        assertEquals(0L, rows[1].filterTokensSaved)
    }

    @Test
    fun `addFilterSavings is additive across multiple pushes`() {
        whenever(delegate.execute("m", "p", null)).thenReturn("o")
        whenever(extractor.extract(any(), any())).thenReturn(ExtractedUsage(1, 1))

        port.withContext("x", 1L) {
            port.addFilterSavings(100L)
            port.addFilterSavings(250L)
            port.execute("m", "p", null)
        }
        assertEquals(350L, store.snapshot("x", 1L).single().filterTokensSaved)
    }

    @Test
    fun `addFilterSavings rejects negative values`() {
        try {
            port.addFilterSavings(-1L)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("non-negative"))
            return
        }
        throw AssertionError("expected IllegalArgumentException")
    }

    @Test
    fun `model override is used for one call then consumed`() {
        whenever(delegate.execute("real-model", "p", null)).thenReturn("o")
        whenever(extractor.extract(any(), any())).thenReturn(ExtractedUsage(1, 1))

        port.withContext("x", 1L) {
            port.setModelOverride("claude-sonnet-4.6")
            port.execute("real-model", "p", null)
            port.execute("real-model", "p", null)
        }
        val rows = store.snapshot("x", 1L)
        assertEquals("claude-sonnet-4.6", rows[0].model)
        assertEquals("real-model", rows[1].model)
    }

    @Test
    fun `model override with blank string does not override`() {
        whenever(delegate.execute("real-model", "p", null)).thenReturn("o")
        whenever(extractor.extract(any(), any())).thenReturn(ExtractedUsage(1, 1))

        port.withContext("x", 1L) {
            port.setModelOverride("")
            port.execute("real-model", "p", null)
        }
        val rows = store.snapshot("x", 1L)
        assertEquals("real-model", rows[0].model)
    }

    @Test
    fun `model override with whitespace string does not override`() {
        whenever(delegate.execute("real-model", "p", null)).thenReturn("o")
        whenever(extractor.extract(any(), any())).thenReturn(ExtractedUsage(1, 1))

        port.withContext("x", 1L) {
            port.setModelOverride("   ")
            port.execute("real-model", "p", null)
        }
        val rows = store.snapshot("x", 1L)
        assertEquals("real-model", rows[0].model)
    }

    @Test
    fun `CompletableFuture worker with withContext records under its own kind`() {
        whenever(delegate.execute("m", "p", null)).thenReturn("o")
        whenever(extractor.extract(any(), any())).thenReturn(ExtractedUsage(1, 1))

        val future =
            CompletableFuture.supplyAsync {
                port.withContext("pipeline", 99L) { port.execute("m", "p", null) }
                true
            }
        future.get(5, TimeUnit.SECONDS)

        assertEquals(1, store.snapshot("pipeline", 99L).size)
        assertEquals(0, store.snapshot("loadtest", 99L).size)
    }

    @Test
    fun `providerLabel delegates to inner port`() {
        assertEquals("OpenAI", port.providerLabel)
    }

    @Test
    fun `writesFilesNatively delegates to inner port`() {
        whenever(delegate.writesFilesNatively).thenReturn(true)
        assertEquals(true, port.writesFilesNatively)
    }

    @Test
    fun `executeWithStreaming records the final output tokens`() {
        val realDelegate =
            object : CopilotStreamingPort {
                override val providerLabel: String = "OpenAI"

                override fun resolveToken(): String? = null

                override fun resolveModel(): String = "m"

                override fun resolveLanguage(): String = "en"

                override fun isReady(): Boolean = true

                override fun execute(
                    model: String,
                    prompt: String,
                    timeoutOverride: Long?,
                    attachments: List<AiAttachment>,
                ): String = error("not used in this test")

                override fun executeWithStreaming(
                    model: String,
                    prompt: String,
                    timeoutOverride: Long?,
                    isCancelled: (() -> Boolean)?,
                    onPartialOutput: (String) -> Unit,
                    attachments: List<AiAttachment>,
                ): String {
                    onPartialOutput("partial")
                    return "final-output"
                }

                override fun executeWithStreamingPerLine(
                    model: String,
                    prompt: String,
                    timeoutOverride: Long?,
                    repoDir: File?,
                    isCancelled: (() -> Boolean)?,
                    onPartialOutput: (String) -> Unit,
                    attachments: List<AiAttachment>,
                ): String = error("not used in this test")
            }
        val p =
            TokenTrackingCopilotPort(
                delegate = realDelegate,
                usagePort = store,
                extractor = extractor,
                clock = fixedClock,
            )
        whenever(extractor.extract("final-output", "OpenAI")).thenReturn(ExtractedUsage(20, 10))

        p.withContext("review", 1L) {
            p.executeWithStreaming("m", "p", null, onPartialOutput = { })
        }
        val row = store.snapshot("review", 1L).single()
        assertEquals(20L, row.promptTokens)
        assertEquals(10L, row.completionTokens)
    }

    @Test
    fun `resolveToken resolveModel resolveLanguage isReady delegate to inner port`() {
        whenever(delegate.resolveToken()).thenReturn("tk")
        whenever(delegate.resolveModel()).thenReturn("mdl")
        whenever(delegate.resolveLanguage()).thenReturn("de")
        whenever(delegate.isReady()).thenReturn(true)
        assertEquals("tk", port.resolveToken())
        assertEquals("mdl", port.resolveModel())
        assertEquals("de", port.resolveLanguage())
        assertTrue(port.isReady())
    }

    @Test
    fun `executeWithStreamingPerLine records tokens with repoDir`() {
        val realDelegate =
            object : CopilotStreamingPort {
                override val providerLabel: String = "OpenAI"

                override fun resolveToken(): String? = null

                override fun resolveModel(): String = "m"

                override fun resolveLanguage(): String = "en"

                override fun isReady(): Boolean = true

                override fun execute(
                    model: String,
                    prompt: String,
                    timeoutOverride: Long?,
                    attachments: List<AiAttachment>,
                ): String = error("not used in this test")

                override fun executeWithStreaming(
                    model: String,
                    prompt: String,
                    timeoutOverride: Long?,
                    isCancelled: (() -> Boolean)?,
                    onPartialOutput: (String) -> Unit,
                    attachments: List<AiAttachment>,
                ): String = error("not used in this test")

                override fun executeWithStreamingPerLine(
                    model: String,
                    prompt: String,
                    timeoutOverride: Long?,
                    repoDir: File?,
                    isCancelled: (() -> Boolean)?,
                    onPartialOutput: (String) -> Unit,
                    attachments: List<AiAttachment>,
                ): String {
                    onPartialOutput("line-1\nline-2\n")
                    return "final-output-per-line"
                }
            }
        val p =
            TokenTrackingCopilotPort(
                delegate = realDelegate,
                usagePort = store,
                extractor = extractor,
                clock = fixedClock,
            )
        whenever(extractor.extract("final-output-per-line", "OpenAI"))
            .thenReturn(ExtractedUsage(30, 15))

        p.withContext("review", 1L) {
            p.executeWithStreamingPerLine("m", "p", null, null, onPartialOutput = { })
        }
        val row = store.snapshot("review", 1L).single()
        assertEquals(30L, row.promptTokens)
        assertEquals(15L, row.completionTokens)
    }

    @Test
    fun `store exception does not break the AI call`() {
        whenever(delegate.execute("m", "p", null)).thenReturn("o")
        whenever(extractor.extract(any(), any())).thenReturn(ExtractedUsage(1, 1))
        val explodingStore =
            object : AiTokenUsagePort {
                override fun record(
                    kind: String,
                    analysisId: Long,
                    callIndex: Int,
                    usage: AiUsage,
                ): Unit = throw RuntimeException("DB down")

                override fun findAll(
                    kind: String,
                    analysisId: Long,
                ): List<AiTokenUsageEntity> = emptyList()

                override fun aggregate(
                    kind: String,
                    analysisId: Long,
                ): AiUsage? = null

                override fun deleteAll(
                    kind: String,
                    analysisId: Long,
                ) {}
            }
        val p = TokenTrackingCopilotPort(delegate, explodingStore, extractor, clock = fixedClock)
        val out = p.withContext("x", 1L) { p.execute("m", "p", null) }
        assertEquals("o", out)
    }

    private class RecordingStore : AiTokenUsagePort {
        private val rows = Collections.synchronizedList(mutableListOf<AiTokenUsageEntity>())
        private val byKey = ConcurrentHashMap<Pair<String, Long>, MutableList<AiTokenUsageEntity>>()

        val totalRows: Int get() = rows.size

        override fun record(
            kind: String,
            analysisId: Long,
            callIndex: Int,
            usage: AiUsage,
        ) {
            val e = AiTokenUsageEntity.from(kind, analysisId, callIndex, usage)
            rows.add(e)
            byKey.computeIfAbsent(kind to analysisId) { Collections.synchronizedList(mutableListOf()) }.add(e)
        }

        override fun findAll(
            kind: String,
            analysisId: Long,
        ): List<AiTokenUsageEntity> = byKey[kind to analysisId]?.toList() ?: emptyList()

        override fun aggregate(
            kind: String,
            analysisId: Long,
        ): AiUsage? {
            val list = byKey[kind to analysisId] ?: return null
            if (list.isEmpty()) return null
            return AiUsage(
                promptTokens = list.sumOf { it.promptTokens },
                completionTokens = list.sumOf { it.completionTokens },
                filterTokensSaved = list.sumOf { it.filterTokensSaved },
                model = list.last().model,
                provider = list.last().provider,
                durationMs = list.sumOf { it.durationMs },
            )
        }

        override fun deleteAll(
            kind: String,
            analysisId: Long,
        ) {
            val list = byKey.remove(kind to analysisId) ?: return
            rows.removeAll(list)
        }

        fun snapshot(
            kind: String,
            analysisId: Long,
        ): List<AiTokenUsageEntity> = byKey[kind to analysisId]?.toList() ?: emptyList()
    }
}
