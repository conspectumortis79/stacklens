package com.stacklens.systemtest

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.adapter.out.knowledge.LuceneKnowledgeIndexAdapter
import com.stacklens.application.port.out.knowledge.KnowledgeBasePort
import com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper
import com.stacklens.application.service.knowledge.KnowledgePublicationExecutor
import com.stacklens.domain.model.IssueType
import com.stacklens.domain.model.TicketCreationRecord
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.TimeUnit

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "stacklens.knowledge.index-dir=\${java.io.tmpdir}/stacklens-rag-e2e-\${random.uuid}",
        RagOllamaTestSupport.READ_TIMEOUT_PROPERTY,
    ],
)
class TicketCreationRagRetrievalSystemTest {
    @Autowired
    lateinit var knowledgePublicationExecutor: KnowledgePublicationExecutor

    @Autowired
    lateinit var knowledgeMapper: AnalysisKnowledgeMapper

    @Autowired
    lateinit var indexAdapter: LuceneKnowledgeIndexAdapter

    @Autowired
    lateinit var knowledgeBasePort: KnowledgeBasePort

    private val mapper = ObjectMapper()

    private var resolvedBaseId: Long = 0L

    companion object {
        @BeforeAll
        @JvmStatic
        @Timeout(value = 5, unit = TimeUnit.MINUTES)
        fun warmupOllama() = RagOllamaTestSupport.warmup()
    }

    @BeforeEach
    fun resolveAnalysisBaseId() {
        resolvedBaseId =
            knowledgeBasePort
                .findAllByOrderByCreatedAtAsc()
                .firstOrNull { it.systemManaged }
                ?.id
                ?: error("no system-managed analysis base found — KnowledgeBaseStartupRunner did not seed one")
    }

    private val fixedTicketJson =
        """
        {
          "summary": "Sicherheits-Refactoring: OrderReferenceBook-Komponente gegen JWT-Bypass absichern",
          "description": "Die OrderReferenceBook-Komponente verarbeitet aktuell eingehende JWT-Tokens ohne Validierung der Signatur. Dadurch kann ein Angreifer mit einem beliebigen Token Aktionen im Namen anderer Nutzer auslösen. Die Komponente muss zwingend eine vollständige JWT-Validierung (Signatur + Ablauf + Audience) durchführen.",
          "acceptanceCriteria": [
            "Jeder Endpunkt der OrderReferenceBook-API verifiziert die JWT-Signatur mit dem konfigurierten Schlüssel",
            "Abgelaufene Tokens werden mit HTTP 401 abgewiesen",
            "Tokens mit falscher Audience werden abgelehnt"
          ],
          "openPoints": [
            "Welche JWT-Library soll zum Einsatz kommen?",
            "Wo liegt der Schlüssel für die Verifikation?"
          ],
          "labels": ["security", "jwt", "OrderReferenceBook"]
        }
        """.trimIndent()

    @Test
    fun `ticket content created by ticket-creation flow is findable via chat retrieval`() {
        @Suppress("UNCHECKED_CAST")
        val aiResult = mapper.readValue(fixedTicketJson, Map::class.java) as Map<String, Any?>
        val resultJson = mapper.writeValueAsString(aiResult)

        val record =
            TicketCreationRecord(
                id = 0L,
                inputText = "OrderReferenceBook sichern, weil JWT-Validierung fehlt".take(2000),
                summary = aiResult["summary"] as? String,
                issueType = IssueType.STORY.displayName,
                labels = (aiResult["labels"] as? List<*>)?.joinToString(",") { it.toString() },
                resultJson = resultJson,
                status = "COMPLETED",
            )

        val candidate =
            knowledgeMapper.fromTicketCreation(record)
                ?: error("mapper returned null — resultJson missing or malformed")

        knowledgePublicationExecutor.publish(candidate)

        val queries =
            listOf(
                "OrderReferenceBook",
                "Sicherheitsrisiken JWT",
                "OrderReferenceBook Sicherheitsrisiken",
                "jwt validation bypass",
            )

        for (q in queries) {
            val hits =
                indexAdapter.searchByVector(
                    knowledgeBaseId = resolvedBaseId,
                    queryVector = embedQuery(q),
                    limit = 5,
                )
            assertTrue(
                hits.isNotEmpty(),
                "ticket content not retrievable for query='$q' — RAG indexing failed silently",
            )
            val top = hits.first()
            assertTrue(
                top.content.contains("OrderReferenceBook") ||
                    top.content.contains("JWT") ||
                    top.content.contains("jwt"),
                "top hit does not contain expected ticket terms (query='$q', citation='${top.citation}')",
            )
            println("[E2E] query='$q' → hit='${top.citation}' score=${top.score}")
        }
    }

    private fun embedQuery(text: String): FloatArray = RagOllamaTestSupport.embedQuery(text)
}
