package com.stacklens.adapter.out.knowledge.archive

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.knowledge.KnowledgeArchiveManifest
import com.stacklens.application.port.out.knowledge.KnowledgeArchiveReaderPort
import com.stacklens.application.port.out.knowledge.KnowledgeArchiveRecord
import com.stacklens.domain.knowledge.KnowledgeArchiveHeader
import com.stacklens.shared.KnowledgeZipReader
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class EncryptedKnowledgeArchiveReader(
    private val objectMapper: ObjectMapper,
) : KnowledgeArchiveReaderPort {
    private val log = LoggerFactory.getLogger(EncryptedKnowledgeArchiveReader::class.java)

    override fun inspect(
        archiveBytes: ByteArray,
        password: String?,
    ): KnowledgeArchiveManifest {
        val payload = decryptIfNeeded(archiveBytes, password)
        val manifestJson = KnowledgeZipReader.readEntryAsString(payload, MANIFEST_ENTRY)
        if (manifestJson == null) {
            if (KnowledgeZipReader.readEntryAsString(payload, BUNDLE_MANIFEST_ENTRY) != null) {
                throw IllegalStateException(
                    "Die Datei ist ein .slbundle (mehrere Wissensdatenbanken). " +
                        "Bitte den Bundle-Import verwenden, nicht den Einzelimport.",
                )
            }
            throw IllegalStateException("Archive is missing manifest.json")
        }
        return parseManifest(objectMapper.readTree(manifestJson))
    }

    override fun read(
        archiveBytes: ByteArray,
        password: String?,
    ): Sequence<KnowledgeArchiveRecord> =
        sequence {
            val payload = decryptIfNeeded(archiveBytes, password)
            val entries =
                KnowledgeZipReader.entries(
                    payload,
                    names = setOf(SOURCES_ENTRY, DOCUMENTS_ENTRY, CHUNKS_ENTRY),
                    prefix = KnowledgeZipReader.VECTORS_PREFIX,
                )
            val sources = LinkedHashMap<String, KnowledgeArchiveRecord.Source>()
            forEachLine(entries, SOURCES_ENTRY) { line ->
                val node = objectMapper.readTree(line)
                sources[node.path("stableId").asText()] = parseSource(node)
            }
            val documents = LinkedHashMap<String, KnowledgeArchiveRecord.Document>()
            forEachLine(entries, DOCUMENTS_ENTRY) { line ->
                val node = objectMapper.readTree(line)
                documents[node.path("stableId").asText()] = parseDocument(node)
            }
            val chunkNodes = mutableListOf<Pair<JsonNode, String?>>()
            forEachLine(entries, CHUNKS_ENTRY) { line ->
                val node = objectMapper.readTree(line)
                chunkNodes.add(node to node.path("vectorEntry").asText(null))
            }
            log.debug(
                "Knowledge archive read: sources={} docs={} chunks={}",
                sources.size,
                documents.size,
                chunkNodes.size,
            )
            for (source in sources.values) yield(source)
            for (document in documents.values) {
                log.debug(
                    "Knowledge archive yields document: stableId={} sourceStableId={}",
                    document.stableId,
                    document.sourceStableId,
                )
                yield(document)
            }
            val vectorBytesByName =
                entries.filterKeys { it.startsWith(KnowledgeZipReader.VECTORS_PREFIX) }
            for ((node, vectorEntry) in chunkNodes) {
                val docStableId = node.path("documentStableId").asText()
                val doc =
                    documents[docStableId]
                        ?: error("Chunk ${node.path("stableId").asText()} references missing document $docStableId")
                yield(parseChunk(node, doc, vectorEntry?.let { vectorBytesByName[it] }))
            }
        }

    private fun forEachLine(
        entries: Map<String, ByteArray>,
        entryName: String,
        consume: (String) -> Unit,
    ) {
        val text = entries[entryName]?.toString(Charsets.UTF_8) ?: return
        text.lineSequence().filter { it.isNotBlank() }.forEach(consume)
    }

    private fun decryptIfNeeded(
        archiveBytes: ByteArray,
        password: String?,
    ): ByteArray {
        if (!KnowledgeArchiveHeader.isEncryptedArchive(archiveBytes)) return archiveBytes
        require(!password.isNullOrBlank()) { "Password is required to decrypt the archive" }
        val header = KnowledgeArchiveHeader.decode(archiveBytes)
        return KnowledgeArchiveCipherFactory
            .decryptor(password, header)
            .doFinal(archiveBytes.copyOfRange(header.ciphertextOffset, archiveBytes.size))
    }

    private fun parseManifest(node: JsonNode): KnowledgeArchiveManifest =
        KnowledgeArchiveManifest(
            format = node.path("format").asText(""),
            version = node.path("version").asInt(1),
            exportedAt = node.path("exportedAt").asText(""),
            stacklensVersion = node.path("stacklensVersion").asText(""),
            knowledgeBaseStableId = node.path("knowledgeBaseStableId").asText(""),
            knowledgeBaseName = node.path("knowledgeBaseName").asText(""),
            knowledgeBaseDescription = node.path("knowledgeBaseDescription").asText(""),
            sourceCount = node.path("sourceCount").asInt(0),
            documentCount = node.path("documentCount").asInt(0),
            chunkCount = node.path("chunkCount").asInt(0),
            containsVectors = node.path("containsVectors").asBoolean(false),
            embeddingProvider = node.path("embeddingProvider").asText(null),
            embeddingModel = node.path("embeddingModel").asText(null),
            embeddingDimension = node.path("embeddingDimension").asInt(0).takeIf { it > 0 },
            embeddingProfile = node.path("embeddingProfile").asText(null),
        )

    private fun parseSource(n: JsonNode): KnowledgeArchiveRecord.Source =
        KnowledgeArchiveRecord.Source(
            stableId = n.path("stableId").asText(""),
            name = n.path("name").asText(""),
            type = n.path("type").asText("MANUAL"),
            rootUrl = n.path("rootUrl").asText(null),
            externalConfigName = n.path("externalConfigName").asText(null),
            externalReference = n.path("externalReference").asText(null),
            includeChildren = n.path("includeChildren").asBoolean(false),
            includeComments = n.path("includeComments").asBoolean(false),
            maxDepth = n.path("maxDepth").asInt(10),
            active = n.path("active").asBoolean(true),
        )

    private fun parseDocument(n: JsonNode): KnowledgeArchiveRecord.Document {
        val metadata = mutableMapOf<String, String>()
        n.path("metadata").properties().forEach { (k, v) -> metadata[k] = v.asText("") }
        return KnowledgeArchiveRecord.Document(
            stableId = n.path("stableId").asText(""),
            sourceStableId = n.path("sourceStableId").asText(""),
            externalId = n.path("externalId").asText(null),
            title = n.path("title").asText(""),
            sourceUri = n.path("sourceUri").asText(null),
            mimeType = n.path("mimeType").asText(null),
            plainText = n.path("plainText").asText(""),
            contentHash = n.path("contentHash").asText(""),
            metadata = metadata,
        )
    }

    private fun parseChunk(
        n: JsonNode,
        doc: KnowledgeArchiveRecord.Document,
        vectorBytes: ByteArray?,
    ): KnowledgeArchiveRecord.Chunk {
        val dimension = n.path("vectorDimension").asInt(0).takeIf { it > 0 }
        return KnowledgeArchiveRecord.Chunk(
            stableId = n.path("stableId").asText(""),
            documentStableId = doc.stableId,
            ordinal = n.path("ordinal").asInt(0),
            sectionTitle = n.path("sectionTitle").asText(null),
            content = n.path("content").asText(""),
            contentHash = n.path("contentHash").asText(""),
            tokenEstimate = n.path("tokenEstimate").asInt(0),
            vectorBytes = vectorBytes,
            vectorDimension = dimension,
        )
    }

    private companion object {
        const val MANIFEST_ENTRY = "manifest.json"
        const val BUNDLE_MANIFEST_ENTRY = "MANIFEST.json"
        const val SOURCES_ENTRY = "sources.jsonl"
        const val DOCUMENTS_ENTRY = "documents.jsonl"
        const val CHUNKS_ENTRY = "chunks.jsonl"
    }
}
