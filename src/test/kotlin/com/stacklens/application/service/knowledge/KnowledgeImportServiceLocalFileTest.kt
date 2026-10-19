package com.stacklens.application.service.knowledge

import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.adapter.out.knowledge.DefaultKnowledgeHashing
import com.stacklens.application.port.model.KnowledgeImportMode
import com.stacklens.application.port.model.KnowledgeImportResult
import com.stacklens.application.port.out.knowledge.KnowledgeArchiveManifest
import com.stacklens.application.port.out.knowledge.KnowledgeArchiveReaderPort
import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.application.port.out.knowledge.KnowledgeBundleEntry
import com.stacklens.application.port.out.knowledge.KnowledgeBundlePort
import com.stacklens.application.port.out.knowledge.KnowledgeChunkPort
import com.stacklens.application.port.out.knowledge.KnowledgeDocumentPort
import com.stacklens.application.port.out.knowledge.KnowledgeIndexPort
import com.stacklens.application.port.out.knowledge.KnowledgeSourcePort
import com.stacklens.domain.model.knowledge.KnowledgeBase
import com.stacklens.testsupport.FixedClockPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class KnowledgeImportServiceLocalFileTest {
    @TempDir
    lateinit var tempDir: Path

    private val basePort: KnowledgeBasePort = mock()
    private val sourcePort: KnowledgeSourcePort = mock()
    private val documentPort: KnowledgeDocumentPort = mock()
    private val chunkPort: KnowledgeChunkPort = mock()
    private val readerPort: KnowledgeArchiveReaderPort = mock()
    private val indexPort: KnowledgeIndexPort = mock()
    private val bundlePort: KnowledgeBundlePort = mock()
    private val filePort = JavaFileSystemAdapter()

    private val now = LocalDateTime.of(2024, 1, 1, 0, 0)

    private val service =
        knowledgeImportService(
            basePort = basePort,
            sourcePort = sourcePort,
            documentPort = documentPort,
            chunkPort = chunkPort,
            readerPort = readerPort,
            indexPort = indexPort,
            hashing = DefaultKnowledgeHashing(),
            clock = FixedClockPort(1_750_000_000_000L),
            fileReadWritePort = filePort,
            bundlePort = bundlePort,
        )

    private fun archive(
        name: String,
        content: ByteArray,
    ): String {
        val target = tempDir.resolve(name).toFile()
        target.writeBytes(content)
        return target.absolutePath
    }

    private fun singleArchive(): ByteArray = "SLKNpayload".toByteArray()

    private fun bundleArchive(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("MANIFEST.json"))
            zip.write("{}".toByteArray())
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    private fun result(
        id: Long,
        name: String,
    ) = KnowledgeImportResult(id, name, 1, 2, 3, KnowledgeImportMode.NEW_COPY)

    private fun manifest(name: String) =
        KnowledgeArchiveManifest(
            format = "stacklens-knowledge",
            version = 1,
            exportedAt = now.toString(),
            stacklensVersion = "1.0.0",
            knowledgeBaseStableId = "kb-stable",
            knowledgeBaseName = name,
            knowledgeBaseDescription = "from archive",
            sourceCount = 0,
            documentCount = 0,
            chunkCount = 0,
            containsVectors = false,
            embeddingProvider = null,
            embeddingModel = null,
            embeddingDimension = null,
            embeddingProfile = null,
        )

    private fun base(
        id: Long,
        name: String,
    ) = KnowledgeBase(
        id = id,
        stableId = "kb-stable",
        name = name,
        description = "from archive",
        createdAt = now,
        updatedAt = now,
    )

    private fun entry(name: String) =
        KnowledgeBundleEntry(
            entryName = name,
            displayName = name,
            knowledgeBaseStableId = "stable-$name",
            knowledgeBaseName = name,
            knowledgeBaseDescription = "from bundle",
            sourceCount = 0,
            documentCount = 0,
            chunkCount = 0,
            containsVectors = false,
        )

    private fun stubSingleImport() {
        whenever(readerPort.inspect(any(), anyOrNull())).thenReturn(manifest("Imported Base"))
        whenever(readerPort.read(any(), anyOrNull())).thenReturn(emptySequence())
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(emptyList())
        whenever(basePort.save(any<KnowledgeBase>())).thenReturn(base(7L, "Imported Base"))
        whenever(sourcePort.findByKnowledgeBaseId(any())).thenReturn(emptyList())
    }

    @Test
    fun `bundle import rejects unusable local paths`() {
        assertEquals(
            "Datei nicht gefunden: /definitely/missing.slknowledge",
            assertThrows(IllegalArgumentException::class.java) {
                service.importFromLocalFile("/definitely/missing.slknowledge", "pw", KnowledgeImportMode.NEW_COPY)
            }.message,
        )
        assertEquals(
            "Datei nicht gefunden: $tempDir",
            assertThrows(IllegalArgumentException::class.java) {
                service.importFromLocalFile(tempDir.toString(), "pw", KnowledgeImportMode.NEW_COPY)
            }.message,
        )
        val rejected = archive("notes.txt", singleArchive())
        assertEquals(
            true,
            assertThrows(IllegalArgumentException::class.java) {
                service.importFromLocalFile(rejected, "pw", KnowledgeImportMode.NEW_COPY)
            }.message!!.startsWith("Ungültige Dateiendung"),
        )
    }

    @Test
    fun `bundle import requires a password before reading the archive format`() {
        assertEquals(
            "Passwort ist erforderlich",
            assertThrows(IllegalArgumentException::class.java) {
                service.importFromLocalFile(archive("a.slknowledge", singleArchive()), null, KnowledgeImportMode.NEW_COPY)
            }.message,
        )
        assertEquals(
            "Passwort ist erforderlich",
            assertThrows(IllegalArgumentException::class.java) {
                service.importFromLocalFile(archive("a.slknowledge", singleArchive()), "   ", KnowledgeImportMode.NEW_COPY)
            }.message,
        )
    }

    @Test
    fun `bundle import delegates single archives to the single import`() {
        stubSingleImport()
        val single = service.importFromLocalFile(archive("b.slknowledge", singleArchive()), "pw", KnowledgeImportMode.SKIP_EXISTING)
        assertEquals(1, single.size)
        assertEquals(7L, single.single().knowledgeBaseId)
    }

    @Test
    fun `bundle import expands bundle entries through the single import`() {
        val bundleBytes = bundleArchive()
        whenever(bundlePort.inspect(bundleBytes)).thenReturn(listOf(entry("First"), entry("Second")))
        whenever(bundlePort.readEntry(bundleBytes, "First")).thenReturn("SLKN-first".toByteArray())
        whenever(bundlePort.readEntry(bundleBytes, "Second")).thenReturn("SLKN-second".toByteArray())
        whenever(readerPort.inspect(eq("SLKN-first".toByteArray()), anyOrNull())).thenReturn(manifest("First"))
        whenever(readerPort.inspect(eq("SLKN-second".toByteArray()), anyOrNull())).thenReturn(manifest("Second"))
        whenever(readerPort.read(any(), anyOrNull())).thenReturn(emptySequence())
        whenever(basePort.findAllByOrderByCreatedAtAsc()).thenReturn(emptyList())
        whenever(basePort.save(any<KnowledgeBase>())).thenReturn(base(1L, "First"), base(2L, "Second"))
        whenever(sourcePort.findByKnowledgeBaseId(any())).thenReturn(emptyList())

        val results = service.importFromLocalFile(archive("a.slbundle", bundleBytes), "pw", KnowledgeImportMode.MERGE)

        assertEquals(listOf(1L, 2L), results.map { it.knowledgeBaseId })
        assertEquals(listOf("First", "Second"), results.map { it.knowledgeBaseName })
    }

    @Test
    fun `bundle import rejects unknown archives`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.importFromLocalFile(archive("c.slknowledge", "junk-junk".toByteArray()), "pw", KnowledgeImportMode.NEW_COPY)
        }
    }

    @Test
    fun `aggregated import requires a password for bundles`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.importAggregatedFromLocalFile(archive("a.slbundle", bundleArchive()), null, KnowledgeImportMode.NEW_COPY)
        }
    }

    @Test
    fun `aggregated import treats a blank password as missing`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.importAggregatedFromLocalFile(archive("a.slbundle", bundleArchive()), "   ", KnowledgeImportMode.NEW_COPY)
        }
    }

    @Test
    fun `aggregated import summarizes an empty bundle`() {
        val empty = service.importAggregatedFromLocalFile(archive("a.slbundle", bundleArchive()), "pw", KnowledgeImportMode.NEW_COPY).single()
        assertEquals(0L, empty.knowledgeBaseId)
        assertEquals("Bundle-Import", empty.knowledgeBaseName)
    }

    @Test
    fun `aggregated import summarizes bundle results`() {
        val method =
            KnowledgeImportService::class.java.getDeclaredMethod(
                "aggregateBundleResults",
                List::class.java,
                KnowledgeImportMode::class.java,
            )
        method.isAccessible = true
        val aggregated =
            method.invoke(service, listOf(result(3L, "alpha"), result(4L, "beta")), KnowledgeImportMode.REPLACE) as KnowledgeImportResult
        assertEquals(3L, aggregated.knowledgeBaseId)
        assertEquals("alpha, beta", aggregated.knowledgeBaseName)
        assertEquals(2, aggregated.documentsImported)
        assertEquals(4, aggregated.chunksImported)
        assertEquals(6, aggregated.vectorsImported)
        assertEquals(KnowledgeImportMode.REPLACE, aggregated.mode)
    }

    @Test
    fun `aggregated import handles single and unknown archives`() {
        stubSingleImport()
        val single = service.importAggregatedFromLocalFile(archive("s.slknowledge", singleArchive()), "pw", KnowledgeImportMode.NEW_COPY).single()
        assertEquals(7L, single.knowledgeBaseId)

        assertThrows(IllegalArgumentException::class.java) {
            service.importAggregatedFromLocalFile(archive("u.slknowledge", "junk-junk".toByteArray()), "pw", KnowledgeImportMode.NEW_COPY)
        }
    }
}
