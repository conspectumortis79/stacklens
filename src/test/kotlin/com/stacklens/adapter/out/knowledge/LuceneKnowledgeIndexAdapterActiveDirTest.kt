package com.stacklens.adapter.out.knowledge

import com.stacklens.application.port.out.knowledge.IndexedKnowledgeChunk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class LuceneKnowledgeIndexAdapterActiveDirTest {
    private fun newAdapter(): Pair<LuceneKnowledgeIndexAdapter, String> {
        val indexDir = "/tmp/stacklens-activedir-test-${System.nanoTime()}"
        return LuceneKnowledgeIndexAdapter(
            hashing =
                com.stacklens.adapter.out.knowledge
                    .DefaultKnowledgeHashing(),
            indexDir = indexDir,
        ) to indexDir
    }

    private fun sampleChunk(
        baseId: Long,
        chunkId: Long,
        embedding: FloatArray,
    ): IndexedKnowledgeChunk =
        IndexedKnowledgeChunk(
            chunkId = chunkId,
            documentId = chunkId,
            knowledgeBaseId = baseId,
            title = "Title $chunkId",
            sectionTitle = null,
            content = "Body $chunkId",
            sourceType = "MANUAL",
            sourceUri = null,
            embedding = embedding,
            score = 0.0,
            citation = "KB:document-$chunkId#chunk-0",
        )

    @Test
    fun `activeDir is deterministic — the newest modified directory wins over g0`() {
        val (adapter, indexDir) = newAdapter()
        val baseId = 4101L
        val base = Paths.get(indexDir, "base-$baseId")
        val g0 = Files.createDirectories(base.resolve("g0"))
        val regen100 = Files.createDirectories(base.resolve("g0-regen-100"))
        val regen200 = Files.createDirectories(base.resolve("g0-regen-200"))
        Files.setLastModifiedTime(
            g0,
            java.nio.file.attribute.FileTime
                .fromMillis(1_000L),
        )
        Files.setLastModifiedTime(
            regen100,
            java.nio.file.attribute.FileTime
                .fromMillis(2_000L),
        )
        Files.setLastModifiedTime(
            regen200,
            java.nio.file.attribute.FileTime
                .fromMillis(3_000L),
        )

        val first = adapter.activeGeneration(baseId)
        for (i in 0 until 50) {
            assertEquals(first, adapter.activeGeneration(baseId), "activeDir must return the same path on every call (iter $i)")
        }
        assertEquals("g0-regen-200", Paths.get(first!!).fileName.toString(), "the newest directory wins, even when g0 exists")
    }

    @Test
    fun `activeDir falls back to the most recently modified regen directory when g0 is absent`() {
        val (adapter, indexDir) = newAdapter()
        val baseId = 4102L
        val base = Paths.get(indexDir, "base-$baseId")
        val older = base.resolve("g0-regen-100")
        val newer = base.resolve("g0-regen-200")
        Files.createDirectories(older)
        Files.createDirectories(newer)
        Files.setLastModifiedTime(
            older,
            java.nio.file.attribute.FileTime
                .fromMillis(1_000L),
        )
        Files.setLastModifiedTime(
            newer,
            java.nio.file.attribute.FileTime
                .fromMillis(2_000L),
        )

        val active = adapter.activeGeneration(baseId)
        assertEquals("g0-regen-200", Paths.get(active!!).fileName.toString(), "newest mtime must win")
    }

    @Test
    fun `searchByVector after a reindex returns the new chunks and not the stale g0 ones`() {
        val (adapter, indexDir) = newAdapter()
        val baseId = 4103L
        adapter.replaceAllChunks(baseId, "g0", listOf(sampleChunk(baseId, 0L, floatArrayOf(1f, 0f, 0f, 0f))))
        val pre = adapter.searchByVector(baseId, floatArrayOf(1f, 0f, 0f, 0f), 5)
        assertEquals(1, pre.size, "pre-condition: g0 has 1 chunk")
        assertEquals(0L, pre.first().chunkId)

        adapter.replaceAllChunks(
            baseId,
            "g0-regen-999",
            listOf(
                sampleChunk(baseId, 100L, floatArrayOf(0f, 1f, 0f, 0f)),
                sampleChunk(baseId, 101L, floatArrayOf(0f, 0f, 1f, 0f)),
            ),
        )

        val after = adapter.searchByVector(baseId, floatArrayOf(0f, 1f, 0f, 0f), 5)
        val ids = after.map { it.chunkId }
        assertEquals(listOf(100L, 101L), ids, "stale chunkId 0 from g0 must NOT appear in hits after reindex")
    }

    @Test
    fun `appendChunks with g0 appends to the active generation instead of creating g0`() {
        val (adapter, indexDir) = newAdapter()
        val baseId = 4105L
        adapter.replaceAllChunks(baseId, "g0-regen-777", listOf(sampleChunk(baseId, 0L, floatArrayOf(1f, 0f, 0f, 0f))))

        adapter.appendChunks(baseId, "g0", listOf(sampleChunk(baseId, 1L, floatArrayOf(0f, 1f, 0f, 0f))))

        assertEquals("g0-regen-777", adapter.activeGeneration(baseId))
        assertFalse(Files.exists(Paths.get(indexDir, "base-$baseId", "g0")))
        val hits = adapter.searchByVector(baseId, floatArrayOf(1f, 0f, 0f, 0f), 5)
        assertEquals(setOf(0L, 1L), hits.map { it.chunkId }.toSet())
    }

    @Test
    fun `replaceAllChunks removes stale regen siblings from previous reindexes`() {
        val (adapter, indexDir) = newAdapter()
        val baseId = 4104L
        val base = Paths.get(indexDir, "base-$baseId")
        Files.createDirectories(base.resolve("g0-regen-1"))
        Files.createDirectories(base.resolve("g0-regen-2"))
        Files.createDirectories(base.resolve("g0-regen-3"))
        assertEquals(3, Files.list(base).use { it.count() }, "pre-condition: 3 stale regen dirs")

        adapter.replaceAllChunks(baseId, "g0-regen-4", listOf(sampleChunk(baseId, 0L, floatArrayOf(1f, 0f))))

        val remaining = Files.list(base).use { it.toList() }
        assertEquals(1, remaining.size, "exactly one generation directory must remain after reindex")
        assertEquals("g0-regen-4", remaining.first().fileName.toString(), "the new regen dir is the survivor")
    }
}
