package com.stacklens.adapter.out.knowledge.bundle

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.knowledge.KnowledgeBundleArchive
import com.stacklens.application.port.out.knowledge.KnowledgeBundleEntry
import com.stacklens.application.port.out.knowledge.KnowledgeBundlePort
import com.stacklens.shared.KnowledgeZipReader
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Component
class KnowledgeBundleAdapter(
    private val objectMapper: ObjectMapper,
) : KnowledgeBundlePort {
    override fun inspect(bundleBytes: ByteArray): List<KnowledgeBundleEntry> {
        val manifestNode = readManifestNode(bundleBytes)
        val archives =
            manifestNode.path("archives").takeIf { it.isArray } ?: return emptyList()
        return archives.map { n ->
            KnowledgeBundleEntry(
                entryName = n.path("entryName").asText(""),
                displayName = n.path("displayName").asText(""),
                knowledgeBaseStableId = n.path("knowledgeBaseStableId").asText(""),
                knowledgeBaseName = n.path("knowledgeBaseName").asText(""),
                knowledgeBaseDescription = n.path("knowledgeBaseDescription").asText(""),
                sourceCount = n.path("sourceCount").asInt(0),
                documentCount = n.path("documentCount").asInt(0),
                chunkCount = n.path("chunkCount").asInt(0),
                containsVectors = n.path("containsVectors").asBoolean(false),
            )
        }
    }

    override fun readEntry(
        bundleBytes: ByteArray,
        entryName: String,
    ): ByteArray = KnowledgeZipReader.findEntry(bundleBytes, entryName) ?: error("Bundle entry not found: $entryName")

    override fun write(archives: List<KnowledgeBundleArchive>): ByteArray {
        val manifest =
            mapOf(
                "format" to "stacklens-bundle",
                "version" to 1,
                "exportedAt" to Instant.now().toString(),
                "stacklensVersion" to "0.0.1-SNAPSHOT",
                "entryCount" to archives.size,
                "archives" to
                    archives.map { a ->
                        mapOf(
                            "entryName" to a.entryName,
                            "displayName" to a.displayName,
                            "knowledgeBaseStableId" to a.knowledgeBaseStableId,
                            "knowledgeBaseName" to a.knowledgeBaseName,
                            "knowledgeBaseDescription" to a.knowledgeBaseDescription,
                            "sourceCount" to a.sourceCount,
                            "documentCount" to a.documentCount,
                            "chunkCount" to a.chunkCount,
                            "containsVectors" to a.containsVectors,
                        )
                    },
            )
        val out = java.io.ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest))
            zip.closeEntry()
            for (a in archives) {
                zip.putNextEntry(ZipEntry(a.entryName))
                zip.write(a.bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun readManifestNode(bundleBytes: ByteArray): com.fasterxml.jackson.databind.JsonNode {
        val manifest = KnowledgeZipReader.findEntry(bundleBytes, MANIFEST_ENTRY) ?: error("Bundle is missing $MANIFEST_ENTRY")
        return objectMapper.readTree(manifest)
    }

    companion object {
        const val MANIFEST_ENTRY = "MANIFEST.json"
    }
}
