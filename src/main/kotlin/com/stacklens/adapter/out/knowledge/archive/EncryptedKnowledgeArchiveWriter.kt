package com.stacklens.adapter.out.knowledge.archive

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.knowledge.KnowledgeArchiveWriterPort
import com.stacklens.application.port.out.knowledge.KnowledgeBaseSnapshot
import com.stacklens.domain.knowledge.KnowledgeArchiveHeader
import com.stacklens.domain.model.knowledge.KnowledgeExportOptions
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Component
class EncryptedKnowledgeArchiveWriter(
    private val objectMapper: ObjectMapper,
) : KnowledgeArchiveWriterPort {
    override fun write(
        snapshot: KnowledgeBaseSnapshot,
        options: KnowledgeExportOptions,
        password: String,
    ): ByteArray {
        require(password.isNotBlank()) { "Export password must not be blank" }
        val rng = SecureRandom()
        val header =
            KnowledgeArchiveHeader(
                salt = ByteArray(KnowledgeArchiveHeader.SALT_BYTES).also { rng.nextBytes(it) },
                iv = ByteArray(KnowledgeArchiveHeader.IV_BYTES).also { rng.nextBytes(it) },
                iterations = KnowledgeArchiveHeader.DEFAULT_PBKDF2_ITERATIONS,
            )
        val cipher = KnowledgeArchiveCipherFactory.encryptor(password, header)

        val plain = buildZip(snapshot, options)

        val out = java.io.ByteArrayOutputStream()
        out.write(header.encode())
        out.write(cipher.doFinal(plain))
        return out.toByteArray()
    }

    private fun buildZip(
        snapshot: KnowledgeBaseSnapshot,
        options: KnowledgeExportOptions,
    ): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            val manifest =
                mapOf(
                    "format" to "stacklens-knowledge",
                    "version" to 1,
                    "exportedAt" to Instant.now().toString(),
                    "stacklensVersion" to "0.0.1-SNAPSHOT",
                    "knowledgeBaseStableId" to snapshot.knowledgeBaseStableId,
                    "knowledgeBaseName" to snapshot.knowledgeBaseName,
                    "knowledgeBaseDescription" to snapshot.knowledgeBaseDescription,
                    "sourceCount" to snapshot.sources.size,
                    "documentCount" to snapshot.documents.size,
                    "chunkCount" to snapshot.chunks.size,
                    "containsVectors" to options.includeVectors,
                    "embeddingProvider" to snapshot.embeddingProvider,
                    "embeddingModel" to snapshot.embeddingModel,
                    "embeddingDimension" to snapshot.embeddingDimension,
                    "embeddingProfile" to snapshot.embeddingProfile,
                )
            zip.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("knowledge-base.json"))
            zip.write(
                objectMapper.writeValueAsBytes(
                    mapOf(
                        "stableId" to snapshot.knowledgeBaseStableId,
                        "name" to snapshot.knowledgeBaseName,
                        "description" to snapshot.knowledgeBaseDescription,
                    ),
                ),
            )
            zip.closeEntry()

            if (options.includeEmbeddingProfile) {
                zip.putNextEntry(ZipEntry("embedding-profile.json"))
                zip.write(
                    objectMapper.writeValueAsBytes(
                        mapOf(
                            "provider" to snapshot.embeddingProvider,
                            "model" to snapshot.embeddingModel,
                            "dimension" to snapshot.embeddingDimension,
                            "profile" to snapshot.embeddingProfile,
                        ),
                    ),
                )
                zip.closeEntry()
            }

            if (options.includeSourceDefinitions) {
                zip.putNextEntry(ZipEntry("sources.jsonl"))
                snapshot.sources.forEach { source ->
                    val line =
                        objectMapper.writeValueAsString(
                            mapOf(
                                "stableId" to source.stableId,
                                "name" to source.name,
                                "type" to source.type,
                                "rootUrl" to source.rootUrl,
                                "externalConfigName" to source.externalConfigName,
                                "externalReference" to source.externalReference,
                                "includeChildren" to source.includeChildren,
                                "includeComments" to source.includeComments,
                                "maxDepth" to source.maxDepth,
                                "active" to source.active,
                            ),
                        )
                    zip.write(line.toByteArray(Charsets.UTF_8))
                    zip.write("\n".toByteArray(Charsets.UTF_8))
                }
                zip.closeEntry()
            }

            zip.putNextEntry(ZipEntry("documents.jsonl"))
            snapshot.documents.forEach { doc ->
                val line =
                    objectMapper.writeValueAsString(
                        mapOf(
                            "stableId" to doc.stableId,
                            "sourceStableId" to doc.sourceStableId,
                            "externalId" to doc.externalId,
                            "title" to doc.title,
                            "sourceUri" to doc.sourceUri,
                            "mimeType" to doc.mimeType,
                            "plainText" to doc.plainText,
                            "contentHash" to doc.contentHash,
                            "metadata" to doc.metadata,
                        ),
                    )
                zip.write(line.toByteArray(Charsets.UTF_8))
                zip.write("\n".toByteArray(Charsets.UTF_8))
            }
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("chunks.jsonl"))
            snapshot.chunks.forEach { chunk ->
                val entry = if (options.includeVectors && chunk.vectorBytes != null) "vectors/${chunk.stableId}.f32" else null
                val line =
                    objectMapper.writeValueAsString(
                        mapOf(
                            "stableId" to chunk.stableId,
                            "documentStableId" to chunk.documentStableId,
                            "ordinal" to chunk.ordinal,
                            "sectionTitle" to chunk.sectionTitle,
                            "content" to chunk.content,
                            "contentHash" to chunk.contentHash,
                            "tokenEstimate" to chunk.tokenEstimate,
                            "vectorEntry" to entry,
                            "vectorDimension" to chunk.vectorDimension,
                        ),
                    )
                zip.write(line.toByteArray(Charsets.UTF_8))
                zip.write("\n".toByteArray(Charsets.UTF_8))
            }
            zip.closeEntry()

            if (options.includeVectors) {
                snapshot.chunks.forEach { chunk ->
                    if (chunk.vectorBytes != null) {
                        val entry = "vectors/${chunk.stableId}.f32"
                        zip.putNextEntry(ZipEntry(entry))
                        zip.write(chunk.vectorBytes)
                        zip.closeEntry()
                    }
                }
            }
        }
        return out.toByteArray()
    }
}
