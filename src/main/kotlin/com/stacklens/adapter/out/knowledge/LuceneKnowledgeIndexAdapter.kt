package com.stacklens.adapter.out.knowledge

import com.stacklens.application.port.out.knowledge.IndexedKnowledgeChunk
import com.stacklens.application.port.out.knowledge.KnowledgeHashingPort
import com.stacklens.application.port.out.knowledge.KnowledgeIndexPort
import com.stacklens.domain.util.closingAuto
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.codecs.Codec
import org.apache.lucene.codecs.lucene104.Lucene104Codec
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.KnnFloatVectorField
import org.apache.lucene.document.StoredField
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.index.VectorSimilarityFunction
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.KnnFloatVectorQuery
import org.apache.lucene.search.TermQuery
import org.apache.lucene.search.TopDocs
import org.apache.lucene.store.FSDirectory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Component
class LuceneKnowledgeIndexAdapter(
    private val hashing: KnowledgeHashingPort,
    @Value("\${stacklens.knowledge.index-dir:\${user.home}/.stacklens/data/knowledge-index}")
    internal val indexDir: String,
) : KnowledgeIndexPort {
    private fun vectorCodec(): Codec = Lucene104Codec()

    private val log = LoggerFactory.getLogger(LuceneKnowledgeIndexAdapter::class.java)

    override fun searchByVector(
        knowledgeBaseId: Long,
        queryVector: FloatArray,
        limit: Int,
    ): List<IndexedKnowledgeChunk> {
        if (queryVector.isEmpty() || limit <= 0) return emptyList()
        val baseDir = activeDir(knowledgeBaseId) ?: return emptyList()
        val results = mutableListOf<IndexedKnowledgeChunk>()
        FSDirectory.open(baseDir).use { dir ->
            DirectoryReader.open(dir).closingAuto { reader ->
                val searcher = IndexSearcher(reader)
                val activeFilter =
                    BooleanQuery
                        .Builder()
                        .add(TermQuery(Term("active", "true")), BooleanClause.Occur.FILTER)
                        .build()
                val knnQuery = KnnFloatVectorQuery(VECTOR_FIELD, queryVector, limit, activeFilter)
                val top: TopDocs =
                    try {
                        searcher.search(knnQuery, limit)
                    } catch (err: Exception) {
                        log.warn(
                            "Lucene KNN search failed for knowledge base {}: {}",
                            knowledgeBaseId,
                            err.message,
                        )
                        throw IllegalStateException("Lucene KNN search failed for knowledge base $knowledgeBaseId", err)
                    }
                for (hit in top.scoreDocs) {
                    val doc = searcher.storedFields().document(hit.doc)
                    val content = doc.get("content") ?: continue
                    val vectorBytes = doc.getBinaryValue(VECTOR_FIELD)
                    val embedding =
                        vectorBytes?.let {
                            val buf =
                                java.nio.ByteBuffer
                                    .wrap(it.bytes, it.offset, it.length)
                                    .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                            val arr = FloatArray(it.length / 4)
                            for (j in arr.indices) arr[j] = buf.getFloat()
                            arr
                        }
                    results +=
                        IndexedKnowledgeChunk(
                            chunkId = doc.get("chunkId")?.toLongOrNull() ?: continue,
                            documentId = doc.get("documentId")?.toLongOrNull() ?: continue,
                            knowledgeBaseId = knowledgeBaseId,
                            title = doc.get("title") ?: "",
                            sectionTitle = doc.get("sectionTitle"),
                            content = content,
                            sourceType = doc.get("sourceType") ?: "MANUAL",
                            sourceUri = doc.get("sourceUri"),
                            embedding = embedding,
                            score = hit.score.toDouble(),
                            citation = "KB:document-${doc.get("documentId")}#chunk-${doc.get("ordinal")}",
                            ticketKey = doc.get("ticketKey"),
                            sprintReference = doc.get("sprintReference"),
                        )
                    if (results.size >= limit) break
                }
            }
        }
        return results
    }

    override fun searchByTicketKey(
        knowledgeBaseId: Long,
        ticketKey: String,
    ): List<IndexedKnowledgeChunk> {
        if (ticketKey.isBlank()) return emptyList()
        val baseDir = activeDir(knowledgeBaseId) ?: return emptyList()
        val results = mutableListOf<IndexedKnowledgeChunk>()
        FSDirectory.open(baseDir).use { dir ->
            DirectoryReader.open(dir).closingAuto { reader ->
                val searcher = IndexSearcher(reader)
                val activeFilter =
                    BooleanQuery
                        .Builder()
                        .add(TermQuery(Term("active", "true")), BooleanClause.Occur.FILTER)
                        .build()
                val ticketQuery =
                    BooleanQuery
                        .Builder()
                        .add(TermQuery(Term("ticketKey", ticketKey)), BooleanClause.Occur.MUST)
                        .add(activeFilter, BooleanClause.Occur.FILTER)
                        .build()
                val top: TopDocs =
                    try {
                        searcher.search(ticketQuery, MAX_TICKET_CHUNKS)
                    } catch (err: Exception) {
                        log.warn(
                            "Lucene ticket-key search failed for key {}: {}",
                            ticketKey,
                            err.message,
                        )
                        throw IllegalStateException("Lucene ticket-key search failed for key $ticketKey", err)
                    }
                for (hit in top.scoreDocs) {
                    val doc = searcher.storedFields().document(hit.doc)
                    val content = doc.get("content") ?: continue
                    val vectorBytes = doc.getBinaryValue(VECTOR_FIELD)
                    val embedding =
                        vectorBytes?.let {
                            val buf =
                                java.nio.ByteBuffer
                                    .wrap(it.bytes, it.offset, it.length)
                                    .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                            val arr = FloatArray(it.length / 4)
                            for (j in arr.indices) arr[j] = buf.getFloat()
                            arr
                        }
                    results +=
                        IndexedKnowledgeChunk(
                            chunkId = doc.get("chunkId")?.toLongOrNull() ?: continue,
                            documentId = doc.get("documentId")?.toLongOrNull() ?: continue,
                            knowledgeBaseId = knowledgeBaseId,
                            title = doc.get("title") ?: "",
                            sectionTitle = doc.get("sectionTitle"),
                            content = content,
                            sourceType = doc.get("sourceType") ?: "MANUAL",
                            sourceUri = doc.get("sourceUri"),
                            embedding = embedding,
                            score = TICKET_KEY_HIT_SCORE,
                            citation = "KB:document-${doc.get("documentId")}#chunk-${doc.get("ordinal")}",
                            ticketKey = doc.get("ticketKey"),
                            sprintReference = doc.get("sprintReference"),
                        )
                }
            }
        }
        return results
    }

    override fun replaceAllChunks(
        knowledgeBaseId: Long,
        generationId: String,
        chunks: List<IndexedKnowledgeChunk>,
    ) {
        val newDir = Paths.get(indexDir, "base-$knowledgeBaseId", generationId)
        val base = Paths.get(indexDir, "base-$knowledgeBaseId")
        if (Files.exists(base)) {
            Files.walk(base).use { stream ->
                stream
                    .filter { it != base && it != newDir }
                    .sorted(Comparator.reverseOrder())
                    .forEach { Files.deleteIfExists(it) }
            }
            if (Files.exists(newDir)) {
                Files.walk(newDir).closingAuto { stream ->
                    stream
                        .filter { it != newDir }
                        .sorted(Comparator.reverseOrder())
                        .forEach { Files.deleteIfExists(it) }
                }
            }
        }
        Files.createDirectories(newDir)
        FSDirectory.open(newDir).use { dir ->
            val analyzer = StandardAnalyzer()
            val config = IndexWriterConfig(analyzer).setOpenMode(IndexWriterConfig.OpenMode.CREATE).setCodec(vectorCodec())
            IndexWriter(dir, config).closingAuto { writer ->
                addChunksToWriter(writer, chunks)
                writer.commit()
            }
        }
    }

    override fun appendChunks(
        knowledgeBaseId: Long,
        generationId: String,
        chunks: List<IndexedKnowledgeChunk>,
    ) {
        if (chunks.isEmpty()) return
        val resolvedGeneration =
            if (generationId == "g0") activeGeneration(knowledgeBaseId) ?: "g0" else generationId
        val baseDir = Paths.get(indexDir, "base-$knowledgeBaseId", resolvedGeneration)
        Files.createDirectories(baseDir)
        FSDirectory.open(baseDir).use { dir ->
            val analyzer = StandardAnalyzer()
            val config = IndexWriterConfig(analyzer).setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND).setCodec(vectorCodec())
            IndexWriter(dir, config).closingAuto { writer ->
                addChunksToWriter(writer, chunks)
                writer.commit()
            }
        }
    }

    private fun addChunksToWriter(
        writer: IndexWriter,
        chunks: List<IndexedKnowledgeChunk>,
    ) {
        for (chunk in chunks) {
            val doc = Document()
            doc.add(StringField("chunkId", chunk.chunkId.toString(), Field.Store.YES))
            doc.add(StringField("documentId", chunk.documentId.toString(), Field.Store.YES))
            doc.add(StringField("knowledgeBaseId", chunk.knowledgeBaseId.toString(), Field.Store.YES))
            doc.add(StringField("active", "true", Field.Store.YES))
            doc.add(StringField("sourceType", chunk.sourceType, Field.Store.YES))
            chunk.ticketKey
                ?.takeIf { it.isNotBlank() }
                ?.let { doc.add(StringField("ticketKey", it, Field.Store.YES)) }
            chunk.sprintReference
                ?.takeIf { it.isNotBlank() }
                ?.let { doc.add(StringField("sprintReference", it, Field.Store.YES)) }
            chunk.sectionTitle?.let { doc.add(TextField("sectionTitle", it, Field.Store.YES)) }
            doc.add(TextField("title", chunk.title, Field.Store.YES))
            doc.add(TextField("content", chunk.content, Field.Store.YES))
            doc.add(StoredField("ordinal", chunk.ordinal()))
            chunk.sourceUri?.let { doc.add(StoredField("sourceUri", it)) }
            val vec = chunk.embedding
            if (vec != null && vec.isNotEmpty()) {
                doc.add(KnnFloatVectorField(VECTOR_FIELD, vec, VectorSimilarityFunction.COSINE))
                doc.add(
                    StoredField(
                        VECTOR_FIELD,
                        hashing.floatArrayToBytes(vec),
                    ),
                )
            }
            writer.addDocument(doc)
        }
    }

    override fun removeDocument(
        knowledgeBaseId: Long,
        documentId: Long,
    ) {
        val baseDir = activeDir(knowledgeBaseId) ?: return
        FSDirectory.open(baseDir).use { dir ->
            val analyzer = StandardAnalyzer()
            val config = IndexWriterConfig(analyzer).setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND).setCodec(vectorCodec())
            IndexWriter(dir, config).use { writer ->
                writer.deleteDocuments(TermQuery(Term("documentId", documentId.toString())))
                writer.commit()
            }
        }
    }

    override fun clearKnowledgeBase(knowledgeBaseId: Long) {
        val baseDir = Paths.get(indexDir, "base-$knowledgeBaseId")
        if (!Files.exists(baseDir)) return
        Files
            .walk(baseDir)
            .sorted(Comparator.reverseOrder())
            .forEach { Files.deleteIfExists(it) }
    }

    override fun activeGeneration(knowledgeBaseId: Long): String? = activeDir(knowledgeBaseId)?.fileName?.toString()

    private fun activeDir(knowledgeBaseId: Long): Path? {
        val base = Paths.get(indexDir, "base-$knowledgeBaseId")
        if (!Files.isDirectory(base)) return null
        val children = Files.list(base).closingAuto { it.toList() }
        return children
            .filter { Files.isDirectory(it) }
            .maxByOrNull { Files.getLastModifiedTime(it).toMillis() }
    }

    private companion object {
        const val VECTOR_FIELD = "vector"

        const val MAX_TICKET_CHUNKS = 32

        const val TICKET_KEY_HIT_SCORE = 1.5
    }
}

private fun IndexedKnowledgeChunk.ordinal(): Int {
    val match = Regex("chunk-(\\d+)$").find(citation)
    return match?.groupValues?.get(1)?.toIntOrNull() ?: 0
}
