package com.stacklens.adapter.out.knowledge

import com.stacklens.application.port.out.knowledge.IndexedKnowledgeChunk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions

class LuceneKnowledgeIndexAdapterIoFailureTest {
    private val reopened: MutableList<Path> = mutableListOf()

    @AfterEach
    fun restorePermissions() {
        reopened.forEach { path ->
            if (Files.exists(path)) {
                Files
                    .getFileAttributeView(path, PosixFileAttributeView::class.java)
                    ?.setPermissions(PosixFilePermissions.fromString("rwxr-xr-x"))
            }
        }
        reopened.clear()
    }

    private fun restrict(
        path: Path,
        spec: String,
    ) {
        reopened.add(path)
        Files
            .getFileAttributeView(path, PosixFileAttributeView::class.java)
            ?.setPermissions(PosixFilePermissions.fromString(spec))
    }

    private fun adapterAt(root: Path) = LuceneKnowledgeIndexAdapter(DefaultKnowledgeHashing(), root.toString())

    private fun chunk(
        baseId: Long,
        id: Long,
    ) = IndexedKnowledgeChunk(
        chunkId = id,
        documentId = id,
        knowledgeBaseId = baseId,
        title = "Titel $id",
        sectionTitle = null,
        content = "inhalt $id",
        sourceType = "MANUAL",
        sourceUri = null,
        embedding = floatArrayOf(1.0f, 0.0f, 0.0f, 0.0f),
        score = 0.0,
        citation = "KB:document-$id#chunk-0",
        ticketKey = "KEY-$id",
    )

    private fun indexOne(
        root: Path,
        baseId: Long,
        generation: String,
    ) {
        val adapter = adapterAt(root)
        adapter.replaceAllChunks(baseId, generation, listOf(chunk(baseId, 1L)))
        assertEquals(generation, adapter.activeGeneration(baseId))
    }

    @Test
    fun `vector search reports an index that cannot be read`(
        @TempDir root: Path,
    ) {
        indexOne(root, 31L, "g0")
        val segments = Files.list(root.resolve("base-31/g0")).use { it.toList().first { it.fileName.toString().startsWith("segments") } }
        restrict(segments, "---------")

        assertThrows(IOException::class.java) {
            adapterAt(root).searchByVector(31L, floatArrayOf(1.0f, 0.0f, 0.0f, 0.0f), 3)
        }
    }

    @Test
    fun `ticket key search reports an index that cannot be read`(
        @TempDir root: Path,
    ) {
        indexOne(root, 32L, "g0")
        val segments = Files.list(root.resolve("base-32/g0")).use { it.toList().first { it.fileName.toString().startsWith("segments") } }
        restrict(segments, "---------")

        assertThrows(IOException::class.java) {
            adapterAt(root).searchByTicketKey(32L, "KEY-1")
        }
    }

    @Test
    fun `the active generation lookup reports a base directory that cannot be listed`(
        @TempDir root: Path,
    ) {
        indexOne(root, 33L, "g0")
        restrict(root.resolve("base-33"), "---------")

        assertThrows(IOException::class.java) { adapterAt(root).activeGeneration(33L) }
    }

    @Test
    fun `replacing chunks reports a stale generation that cannot be listed`(
        @TempDir root: Path,
    ) {
        val stale = Files.createDirectories(root.resolve("base-34/stale"))
        Files.write(stale.resolve("payload.txt"), "x".toByteArray())
        restrict(stale, "---------")

        assertThrows(java.io.UncheckedIOException::class.java) {
            adapterAt(root).replaceAllChunks(34L, "g0", listOf(chunk(34L, 1L)))
        }
    }

    @Test
    fun `replacing chunks reports a generation directory that cannot be written`(
        @TempDir root: Path,
    ) {
        Files.createDirectories(root.resolve("base-35/g0"))
        restrict(root.resolve("base-35/g0"), "r-xr-xr-x")

        assertThrows(IOException::class.java) {
            adapterAt(root).replaceAllChunks(35L, "g0", listOf(chunk(35L, 1L)))
        }
    }

    @Test
    fun `appending chunks reports a generation directory that cannot be written`(
        @TempDir root: Path,
    ) {
        Files.createDirectories(root.resolve("base-36/g0"))
        restrict(root.resolve("base-36/g0"), "r-xr-xr-x")

        assertThrows(IOException::class.java) {
            adapterAt(root).appendChunks(36L, "g0", listOf(chunk(36L, 1L)))
        }
    }

    @Test
    fun `removing a document reports an index that cannot be written`(
        @TempDir root: Path,
    ) {
        indexOne(root, 37L, "g0")
        restrict(root.resolve("base-37/g0"), "r-xr-xr-x")

        assertThrows(IOException::class.java) { adapterAt(root).removeDocument(37L, 1L) }
    }
}
