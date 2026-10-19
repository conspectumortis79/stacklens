package com.stacklens.systemtest

import com.stacklens.adapter.out.knowledge.LuceneKnowledgeIndexAdapter
import com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper
import com.stacklens.application.service.knowledge.KnowledgePublicationExecutor
import com.stacklens.domain.model.JiraTicketSummary
import com.stacklens.domain.model.ReviewAnalysis
import com.stacklens.domain.model.SprintReview
import com.stacklens.domain.model.TicketRefinement
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
        "stacklens.knowledge.index-dir=\${java.io.tmpdir}/stacklens-rag-all-\${random.uuid}",
        RagOllamaTestSupport.READ_TIMEOUT_PROPERTY,
    ],
)
class AllAnalysisRagRetrievalSystemTest {
    @Autowired
    lateinit var knowledgePublicationExecutor: KnowledgePublicationExecutor

    @Autowired
    lateinit var knowledgeMapper: AnalysisKnowledgeMapper

    @Autowired
    lateinit var indexAdapter: LuceneKnowledgeIndexAdapter

    @Autowired
    lateinit var knowledgeBasePort: com.stacklens.application.port.out.knowledge.KnowledgeBasePort

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

    private fun embedQuery(text: String): FloatArray = RagOllamaTestSupport.embedQuery(text)

    private fun assertFound(
        case: String,
        expectedTerm: String,
        query: String,
    ) {
        val hits =
            indexAdapter.searchByVector(
                knowledgeBaseId = resolvedBaseId,
                queryVector = embedQuery(query),
                limit = 5,
            )
        assertTrue(
            hits.isNotEmpty(),
            "$case: query='$query' returned 0 hits — RAG indexing failed for this source type",
        )
        val top = hits.first()
        assertTrue(
            top.content.contains(expectedTerm, ignoreCase = true),
            "$case: top hit does not contain '$expectedTerm' (query='$query', content='${top.content.take(120)}…')",
        )
        println("[$case] query='$query' → hit='${top.citation}' score=${top.score}")
    }

    @Test
    fun `review analysis content is findable in the RAG`() {
        val analysis =
            ReviewAnalysis(
                id = 100L,
                jiraSprintUrl = "https://jira.example.com/board/123",
                resultMarkdown =
                    """
                    # Review-Analyse

                    ## Zusammenfassung
                    AuthGatewayService enthält eine SQL-Injection in der User-Suche.

                    ## Findings
                    - AuthGatewayService.searchUsers: SQL-Injection via 'q' Parameter
                    - Empfehlung: Prepared Statements + Input-Validierung
                    """.trimIndent(),
            )
        val candidate =
            knowledgeMapper.fromReviewAnalysis(analysis)
                ?: error("review mapper returned null")
        knowledgePublicationExecutor.publish(candidate)
        assertFound("REVIEW", "AuthGatewayService", "SQL-Injection AuthGateway")
    }

    @Test
    fun `sprint review content is findable in the RAG`() {
        val rowsJson =
            """
            [
              {"ticketKey":"BUD-500","ticketSummary":"Login-Flow umbauen","state":"BEARBEITET",
               "improvementSummary":"Magic-Link + OIDC-Fallback","openPoints":[]},
              {"ticketKey":"BUD-501","ticketSummary":"Session-Timeout reduzieren","state":"OFFENE_FRAGE",
               "improvementSummary":"Sliding-Expiry auf 15min","openPoints":[]}
            ]
            """.trimIndent()
        val review =
            SprintReview(
                id = 200L,
                sprintName = "Sprint Bud Spencer",
                targetTicketKey = "BUD-200",
                rowsJson = rowsJson,
                totalIssues = 2,
            )
        val candidate =
            knowledgeMapper.fromSprintReview(review)
                ?: error("sprint-review mapper returned null")
        knowledgePublicationExecutor.publish(candidate)
        assertFound("SPRINT", "BUD-500", "BUD-500 Magic-Link")
    }

    @Test
    fun `ticket refinement content is findable in the RAG`() {
        val reviewsJson =
            """
            [
              {
                "ticketKey": "SEC-77",
                "ticketUrl": "https://jira.example.com/browse/SEC-77",
                "originalSummary": "Audit-Log unsicher",
                "originalDescription": "Aktuell kann jeder Service-Eintrag im Audit-Log manipuliert werden.",
                "improvedDescription": "Audit-Log absichern: Append-Only mit Hash-Chain. Jeder Eintrag enthält den Hash des Vorgängers.",
                "openPoints": ["Welcher WORM-Storage ist verfügbar?", "Retention 7 Jahre?"],
                "state": "OK",
                "commentPosted": false,
                "improvedNative": "",
                "improvedFormat": "markdown",
                "improvedSummary": "Audit-Log absichern: Append-Only mit Hash-Chain",
                "suggestedSubtasks": []
              }
            ]
            """.trimIndent()
        val refinement =
            TicketRefinement(
                id = 300L,
                parentTicketKey = "SEC-100",
                reviewsJson = reviewsJson,
            )
        val candidates = knowledgeMapper.fromTicketRefinement(refinement)
        assertTrue(candidates.isNotEmpty(), "ticket-refinement mapper returned empty list")
        candidates.forEach { knowledgePublicationExecutor.publish(it) }
        assertFound("REFINEMENT", "Audit-Log", "Audit-Log Hash-Chain")
    }

    @Test
    fun `ticket summary content is findable in the RAG`() {
        val summary =
            JiraTicketSummary(
                id = 400L,
                ticketKey = "PROJ-9001",
                summaryMarkdown =
                    """
                    # Ticket-Zusammenfassung: PROJ-9001

                    ## Was
                    Micrometer-basierte Metriken für alle REST-Endpoints.

                    ## Warum
                    Aktuell fehlt Observability im Backend. SRE-Team braucht p99-Latenzen.
                    """.trimIndent(),
            )
        val candidate =
            knowledgeMapper.fromTicketSummary(summary)
                ?: error("ticket-summary mapper returned null")
        knowledgePublicationExecutor.publish(candidate)
        assertFound("SUMMARY", "Micrometer", "Micrometer p99 Latenzen")
    }
}
