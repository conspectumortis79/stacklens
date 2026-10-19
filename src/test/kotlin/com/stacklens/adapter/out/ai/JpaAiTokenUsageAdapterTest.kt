package com.stacklens.adapter.out.ai

import com.stacklens.adapter.out.persistence.AiTokenUsageRepository
import com.stacklens.domain.model.AiUsage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.ActiveProfiles

@DataJpaTest
@AutoConfigureTestDatabase
@ActiveProfiles("test")
@EntityScan(basePackages = ["com.stacklens.domain.model"])
@EnableJpaRepositories(basePackages = ["com.stacklens.adapter.out.persistence"])
@Import(JpaAiTokenUsageAdapter::class)
class JpaAiTokenUsageAdapterTest {
    @Autowired
    private lateinit var adapter: JpaAiTokenUsageAdapter

    @Autowired
    private lateinit var repo: AiTokenUsageRepository

    @BeforeEach
    fun setUp() {
        repo.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        repo.deleteAll()
    }

    @Test
    fun `record persists a row and aggregate returns it`() {
        adapter.record("loadtest", 1L, 0, sampleUsage(10, 5, 0))
        val agg = adapter.aggregate("loadtest", 1L)
        assertNotNull(agg)
        assertEquals(10L, agg!!.promptTokens)
        assertEquals(5L, agg.completionTokens)
        assertEquals(15L, agg.totalTokens)
        assertEquals("claude-sonnet-4.6", agg.model)
        assertEquals("Copilot", agg.provider)
    }

    @Test
    fun `aggregate sums multiple calls per analysis`() {
        adapter.record("sql", 1L, 0, sampleUsage(10, 5, 100))
        adapter.record("sql", 1L, 1, sampleUsage(20, 3, 200))
        adapter.record("sql", 1L, 2, sampleUsage(30, 7, 50))
        val agg = adapter.aggregate("sql", 1L)!!
        assertEquals(60L, agg.promptTokens)
        assertEquals(15L, agg.completionTokens)
        assertEquals(350L, agg.filterTokensSaved)
        assertEquals(75L, agg.totalTokens)
        assertEquals(3_000L, agg.durationMs)
    }

    @Test
    fun `aggregate returns null when no rows exist`() {
        assertNull(adapter.aggregate("missing", 99L))
    }

    @Test
    fun `findAll returns rows in callIndex order`() {
        adapter.record("code", 1L, 0, sampleUsage(1, 1))
        adapter.record("code", 1L, 1, sampleUsage(2, 2))
        adapter.record("code", 1L, 2, sampleUsage(3, 3))
        val rows = adapter.findAll("code", 1L)
        assertEquals(listOf(0, 1, 2), rows.map { it.callIndex })
    }

    @Test
    fun `deleteAll removes only the matching analysis`() {
        adapter.record("code", 1L, 0, sampleUsage(1, 1))
        adapter.record("code", 2L, 0, sampleUsage(2, 2))
        adapter.record("sql", 1L, 0, sampleUsage(3, 3))
        adapter.deleteAll("code", 1L)
        assertEquals(0, adapter.findAll("code", 1L).size)
        assertEquals(1, adapter.findAll("code", 2L).size)
        assertEquals(1, adapter.findAll("sql", 1L).size)
    }

    @Test
    fun `aggregate keeps the first non-blank model when later rows are blank`() {
        adapter.record("code", 1L, 0, sampleUsage(1, 1, 0).copy(model = "first-model", provider = "First"))
        adapter.record("code", 1L, 1, sampleUsage(1, 1, 0).copy(model = "", provider = ""))
        val agg = adapter.aggregate("code", 1L)!!
        assertEquals("first-model", agg.model)
        assertEquals("First", agg.provider)
    }

    @Test
    fun `aggregate returns empty model and provider when all rows are blank`() {
        adapter.record("code", 1L, 0, sampleUsage(1, 1, 0).copy(model = "", provider = ""))
        val agg = adapter.aggregate("code", 1L)!!
        assertEquals("", agg.model)
        assertEquals("", agg.provider)
    }

    @Test
    fun `aggregate uses last non-empty model and provider`() {
        adapter.record("code", 1L, 0, sampleUsage(1, 1, 0).copy(model = "old-model", provider = "Copilot"))
        adapter.record("code", 1L, 1, sampleUsage(1, 1, 0).copy(model = "new-model", provider = "OpenAI"))
        val agg = adapter.aggregate("code", 1L)!!
        assertEquals("new-model", agg.model)
        assertEquals("OpenAI", agg.provider)
    }

    @Test
    fun `aggregate falls back to earliest model when only one row exists`() {
        adapter.record("code", 1L, 0, sampleUsage(1, 1, 0).copy(model = "only-model", provider = "Ollama"))
        val agg = adapter.aggregate("code", 1L)!!
        assertEquals("only-model", agg.model)
        assertEquals("Ollama", agg.provider)
    }

    private fun sampleUsage(
        prompt: Long,
        completion: Long,
        saved: Long = 0L,
    ): AiUsage =
        AiUsage(
            promptTokens = prompt,
            completionTokens = completion,
            filterTokensSaved = saved,
            model = "claude-sonnet-4.6",
            provider = "Copilot",
            durationMs = 1_000L,
        )
}
