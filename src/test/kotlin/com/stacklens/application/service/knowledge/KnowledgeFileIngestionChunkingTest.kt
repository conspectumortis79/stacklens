package com.stacklens.application.service.knowledge

import com.stacklens.adapter.out.knowledge.confluence.ConfluenceHierarchyAdapter
import com.stacklens.adapter.out.knowledge.extraction.PlainTextKnowledgeExtractor
import com.stacklens.adapter.out.knowledge.jira.JiraHierarchyAdapter
import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.application.port.out.knowledge.KnowledgeChunkPort
import com.stacklens.application.port.out.knowledge.KnowledgeDocumentPort
import com.stacklens.application.port.out.knowledge.KnowledgeEmbeddingConfigPort
import com.stacklens.application.port.out.knowledge.KnowledgeIndexPort
import com.stacklens.application.port.out.knowledge.KnowledgeIngestionJobPort
import com.stacklens.application.port.out.knowledge.KnowledgeSourcePort
import com.stacklens.application.port.out.knowledge.extraction.DocumentTextExtractor
import com.stacklens.domain.model.knowledge.KnowledgeBase
import com.stacklens.domain.model.knowledge.KnowledgeChunk
import com.stacklens.domain.model.knowledge.KnowledgeDocument
import com.stacklens.domain.model.knowledge.KnowledgeEmbeddingConfig
import com.stacklens.domain.model.knowledge.KnowledgeSource
import com.stacklens.testsupport.knowledgeSourceService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class KnowledgeFileIngestionChunkingTest {
    private val basePort: KnowledgeBasePort = mock()
    private val sourcePort: KnowledgeSourcePort = mock()
    private val documentPort: KnowledgeDocumentPort = mock()
    private val chunkPort: KnowledgeChunkPort = mock()
    private val jobPort: KnowledgeIngestionJobPort = mock()
    private val chunker = KnowledgeChunker()
    private val dispatcher: KnowledgeEmbeddingDispatcher = mock()
    private val configPort: KnowledgeEmbeddingConfigPort = mock()
    private val confluenceAdapter: ConfluenceHierarchyAdapter = mock()
    private val jiraAdapter: JiraHierarchyAdapter = mock()
    private val confluenceConfigPort: ConfluenceConfigPort = mock()
    private val jiraConfigPort: JiraConfigPort = mock()
    private val indexPort: KnowledgeIndexPort = mock()

    private val service =
        knowledgeSourceService(
            KnowledgeSourceServiceDeps(
                basePort = basePort,
                sourcePort = sourcePort,
                documentPort = documentPort,
                chunkPort = chunkPort,
                jobPort = jobPort,
                chunker = chunker,
                dispatcher = dispatcher,
                configPort = configPort,
                extractors = listOf<DocumentTextExtractor>(PlainTextKnowledgeExtractor()),
                confluenceAdapter = confluenceAdapter,
                jiraAdapter = jiraAdapter,
                confluenceConfigPort = confluenceConfigPort,
                jiraConfigPort = jiraConfigPort,
                indexPort = indexPort,
                sprintReviewPort = mock(),
                sprintIssueKeyLister = mock(),
                ingestors = emptyList(),
                documentIngester =
                    KnowledgeDocumentIngester(
                        documentPort = documentPort,
                        chunkPort = chunkPort,
                        configPort = configPort,
                        chunker = chunker,
                        dispatcher = dispatcher,
                        indexPort = indexPort,
                        hashing =
                            com.stacklens.adapter.out.knowledge
                                .DefaultKnowledgeHashing(),
                        clock = com.stacklens.adapter.out.time.SystemClockPort,
                    ),
            ),
        )

    init {
        whenever(configPort.get()).thenReturn(
            KnowledgeEmbeddingConfig(
                chunkSize = 1600,
                chunkOverlap = 200,
            ),
        )
    }

    @Test
    fun `a long plain-text file is split into multiple chunks and each chunk is embedded separately`() {
        val longFile = buildLongPlainText(paragraphCount = 80)
        val longBytes = longFile.toByteArray(Charsets.UTF_8)

        val embedCaptor = argumentCaptor<List<String>>()
        @Suppress("UNCHECKED_CAST")
        whenever(dispatcher.embed(embedCaptor.capture())).thenAnswer { invocation ->
            val texts = invocation.arguments[0] as List<String>
            EmbeddingResult(
                vectors = texts.map { FloatArray(4) { 0.1f } },
                dimension = 4,
                profile = "test|test|test|4|chunker-v1|1600|200",
                model = "test-model",
            )
        }

        val base = KnowledgeBase(id = 1L, name = "KB")
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(listOf(base))
        val savedSources = mutableListOf<KnowledgeSource>()
        var sourceIdSeq = 1L
        whenever(sourcePort.save(any<KnowledgeSource>())).thenAnswer { invocation ->
            val s = invocation.arguments[0] as KnowledgeSource
            if (s.id == 0L) {
                setId(s, "id", sourceIdSeq++)
            }
            savedSources += s
            s
        }
        whenever(documentPort.save(any<KnowledgeDocument>())).thenAnswer { invocation ->
            invocation.arguments[0] as KnowledgeDocument
        }
        val savedChunks = mutableListOf<KnowledgeChunk>()
        var chunkIdSeq = 1L
        whenever(chunkPort.save(any<KnowledgeChunk>())).thenAnswer { invocation ->
            val c = invocation.arguments[0] as KnowledgeChunk
            if (c.id == 0L) {
                setId(c, "id", chunkIdSeq++)
            }
            savedChunks += c
            c
        }

        service.createFileSource(
            knowledgeBaseId = 1L,
            originalFilename = "big-manual.txt",
            contentBytes = longBytes,
        )

        verify(dispatcher, times(1)).embed(any())
        val textsPassedToDispatcher = embedCaptor.singleValue
        assertTrue(
            textsPassedToDispatcher.size > 1,
            "long file must be split into multiple chunks (got ${textsPassedToDispatcher.size})",
        )

        val fullDocLength = longFile.length
        assertTrue(
            textsPassedToDispatcher.all { it.length < fullDocLength },
            "the dispatcher must never receive the whole file as a single input " +
                "(max chunk=${textsPassedToDispatcher.maxOf { it.length }}, doc=$fullDocLength)",
        )

        assertEquals(
            textsPassedToDispatcher.size,
            savedChunks.size,
            "every chunk handed to the embedder must be persisted as its own row",
        )
        val ordinals = savedChunks.map { it.ordinal }.sorted()
        assertEquals(
            ordinals,
            (0 until savedChunks.size).toList(),
            "chunk ordinals must be a contiguous 0..N-1 sequence",
        )
        savedChunks.forEach { chunk ->
            assertTrue(
                chunk.embeddingBlob != null && chunk.embeddingBlob!!.isNotEmpty(),
                "each chunk must carry its own embedding bytes (chunk ordinal=${chunk.ordinal})",
            )
            assertEquals(4, chunk.embeddingDimension, "dimension must match the embedder's output")
        }

        val concatenated = savedChunks.joinToString("\n\n") { it.content }
        assertTrue(
            concatenated.contains("paragraph-0"),
            "first paragraph of the input must appear in at least one persisted chunk",
        )
        assertTrue(
            concatenated.contains("paragraph-${80 - 1}"),
            "last paragraph of the input must appear in at least one persisted chunk",
        )
    }

    @Test
    fun `a one-paragraph short text file is embedded as exactly one chunk`() {
        val shortText = "Single short paragraph with only a handful of words."
        val embedCaptor = argumentCaptor<List<String>>()
        whenever(dispatcher.embed(embedCaptor.capture())).thenReturn(
            EmbeddingResult(
                vectors = listOf(FloatArray(4) { 0.1f }),
                dimension = 4,
                profile = "test|test|test|4|chunker-v1|1600|200",
                model = "test-model",
            ),
        )

        val base = KnowledgeBase(id = 1L, name = "KB")
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(listOf(base))
        var sourceIdSeq = 1L
        whenever(sourcePort.save(any<KnowledgeSource>())).thenAnswer {
            val s = it.arguments[0] as KnowledgeSource
            if (s.id == 0L) setId(s, "id", sourceIdSeq++)
            s
        }
        whenever(documentPort.save(any<KnowledgeDocument>())).thenAnswer { it.arguments[0] as KnowledgeDocument }
        val savedChunks = mutableListOf<KnowledgeChunk>()
        var chunkIdSeq = 1L
        whenever(chunkPort.save(any<KnowledgeChunk>())).thenAnswer {
            val c = it.arguments[0] as KnowledgeChunk
            if (c.id == 0L) setId(c, "id", chunkIdSeq++)
            savedChunks += c
            c
        }

        service.createFileSource(
            knowledgeBaseId = 1L,
            originalFilename = "tiny.txt",
            contentBytes = shortText.toByteArray(Charsets.UTF_8),
        )

        verify(dispatcher, times(1)).embed(any())
        assertEquals(1, embedCaptor.singleValue.size)
        assertEquals(1, savedChunks.size)
    }

    @Test
    fun `a blank file produces zero chunks and zero embedding calls`() {
        val base = KnowledgeBase(id = 1L, name = "KB")
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(listOf(base))
        var sourceIdSeq = 1L
        whenever(sourcePort.save(any<KnowledgeSource>())).thenAnswer {
            val s = it.arguments[0] as KnowledgeSource
            if (s.id == 0L) setId(s, "id", sourceIdSeq++)
            s
        }
        whenever(documentPort.save(any<KnowledgeDocument>())).thenAnswer { it.arguments[0] as KnowledgeDocument }
        whenever(chunkPort.save(any<KnowledgeChunk>())).thenAnswer { it.arguments[0] as KnowledgeChunk }

        service.createFileSource(
            knowledgeBaseId = 1L,
            originalFilename = "empty.txt",
            contentBytes = "".toByteArray(Charsets.UTF_8),
        )

        verify(dispatcher, never()).embed(any())
    }

    private fun buildLongPlainText(paragraphCount: Int): String =
        (0 until paragraphCount).joinToString("\n\n") { i ->
            "paragraph-$i: " + (1..15).joinToString(" ") { "lorem ipsum dolor sit amet #$i-$it" }
        }

    private fun setId(
        target: Any,
        fieldName: String,
        newId: Long,
    ) {
        val field = target.javaClass.declaredFields.first { it.name == fieldName }
        field.isAccessible = true
        field.setLong(target, newId)
    }
}
