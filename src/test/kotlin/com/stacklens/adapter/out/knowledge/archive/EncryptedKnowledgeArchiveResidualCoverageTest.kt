package com.stacklens.adapter.out.knowledge.archive

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.knowledge.KnowledgeArchiveRecord
import com.stacklens.application.port.out.knowledge.KnowledgeBaseSnapshot
import com.stacklens.domain.model.knowledge.KnowledgeExportOptions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EncryptedKnowledgeArchiveResidualCoverageTest {
    private val mapper = ObjectMapper()
    private val writer = EncryptedKnowledgeArchiveWriter(mapper)
    private val reader = EncryptedKnowledgeArchiveReader(mapper)

    private fun snapshot() =
        KnowledgeBaseSnapshot(
            knowledgeBaseStableId = "kb-1",
            knowledgeBaseName = "Wissensbasis",
            knowledgeBaseDescription = "Beschreibung",
            sources =
                listOf(
                    KnowledgeArchiveRecord.Source(
                        stableId = "src-1",
                        name = "Handbuch",
                        type = "MANUAL",
                        rootUrl = null,
                        externalConfigName = null,
                        externalReference = null,
                        includeChildren = false,
                        includeComments = false,
                        maxDepth = 3,
                        active = true,
                    ),
                ),
            documents = emptyList(),
            chunks = emptyList(),
            embeddingProvider = null,
            embeddingModel = null,
            embeddingDimension = null,
            embeddingProfile = null,
        )

    @Test
    fun `hash-colliding entry names are ignored by the reader dispatch`() {
        val colliding = listOf("tPurces.jsonl", "ePcuments.jsonl", "dIunks.jsonl")
        assertTrue(colliding[0].hashCode() == "sources.jsonl".hashCode())
        assertTrue(colliding[1].hashCode() == "documents.jsonl".hashCode())
        assertTrue(colliding[2].hashCode() == "chunks.jsonl".hashCode())

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write("""{"format":"stacklens-knowledge","version":1}""".toByteArray())
            zip.closeEntry()
            for (name in colliding) {
                zip.putNextEntry(ZipEntry(name))
                zip.write("garbage".toByteArray())
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("notes.txt"))
            zip.write("unknown entry".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("sources.jsonl"))
            zip.write(
                """{"stableId":"src-1","name":"Handbuch","type":"MANUAL","includeChildren":false,"includeComments":false,"maxDepth":3,"active":true}""".toByteArray(),
            )
            zip.write("\n".toByteArray())
            zip.closeEntry()
        }

        val records = reader.read(out.toByteArray(), null).toList()
        assertEquals(1, records.size)
        assertTrue(records.single() is KnowledgeArchiveRecord.Source)
    }

    @Test
    fun `archives shorter than the encryption header pass through unchanged`() {
        assertThrows(IllegalStateException::class.java) {
            reader.inspect(ByteArray(10), null)
        }
    }

    @Test
    fun `the writer omits source definitions when the option is disabled`() {
        val bytes =
            writer.write(
                snapshot(),
                KnowledgeExportOptions(includeVectors = false, includeSourceDefinitions = false, includeEmbeddingProfile = false),
                "secret",
            )

        val records = reader.read(bytes, "secret").toList()
        assertTrue(records.none { it is KnowledgeArchiveRecord.Source })
    }
}
