package com.stacklens.application.service

import com.stacklens.application.port.inbound.TicketCreationUseCase
import com.stacklens.application.port.model.SubtaskDraft
import com.stacklens.application.port.out.AiResponseJsonExtractorPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.TicketCreationJiraPort
import com.stacklens.application.port.out.TicketCreationRecordPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.service.knowledge.KnowledgePublication
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.IssueType
import com.stacklens.domain.model.JiraCreateField
import com.stacklens.domain.model.TicketCreationRecord
import org.slf4j.LoggerFactory

@ApplicationService
class TicketCreationService(
    private val copilot: CopilotPort,
    private val aiUsageContext: AiUsageContextPort,
    private val promptReader: PromptReaderPort,
    private val jiraConfigPort: JiraConfigPort,
    private val jiraPort: TicketCreationJiraPort,
    private val recordPort: TicketCreationRecordPort,
    private val jsonCodec: JsonCodecPort,
    private val resultNormalizer: TicketCreationResultNormalizer,
    private val subtaskCreator: JiraSubtaskCreator,
    private val aiJsonExtractor: AiResponseJsonExtractorPort,
    private val knowledgePublication: com.stacklens.application.service.knowledge.KnowledgePublication,
    private val knowledgeMapper: com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper,
    private val jiraService: TicketCreationJiraService,
    private val historyService: TicketCreationHistoryService,
) : TicketCreationUseCase {
    private val log = LoggerFactory.getLogger(TicketCreationService::class.java)

    override fun generateTicket(
        inputText: String,
        attachmentSection: String,
        issueType: IssueType?,
    ): Map<String, Any?> {
        val resolvedType = issueType ?: IssueType.DEFAULT
        val runningRecord =
            recordPort.saveTicketCreationRecord(
                TicketCreationRecord(
                    inputText = inputText.take(2000),
                    issueType = resolvedType.displayName,
                    status = AnalysisStatus.RUNNING,
                ),
            )
        val runningId = runningRecord.id

        return try {
            val result = runAiGeneration(inputText, attachmentSection, runningId, resolvedType)
            if (isCancelledOrDeleted(runningId)) {
                log.info("[TicketCreation] Generation cancelled or record deleted by user – discarding AI result.")
                return emptyMap()
            }
            persistCompletedRecord(runningId, inputText, result, resolvedType)
        } catch (e: Exception) {
            markRecordFailed(runningId, inputText, e.message)
            throw e
        } finally {
            aiUsageContext.clearContext()
        }
    }

    private fun runAiGeneration(
        inputText: String,
        attachmentSection: String,
        runningId: Long,
        issueType: IssueType,
    ): Map<String, Any?> {
        val lang = copilot.resolveLanguage().ifBlank { "en" }
        val model = copilot.resolveModel()
        val prompt =
            promptReader.get(
                lang,
                issueType.promptKey,
                mapOf(
                    "inputText" to inputText,
                    "attachmentSection" to attachmentSection,
                    "outputLanguage" to if (lang == "de") "German" else "English",
                ),
            )
        log.info(
            "[TicketCreation] Generating {} ticket, inputLength={}",
            issueType.displayName,
            inputText.length,
        )
        aiUsageContext.setContext("ticket_creation", runningId)
        aiUsageContext.setModelOverride(model)
        val raw = copilot.execute(model, prompt)
        val json = extractJson(raw)

        @Suppress("UNCHECKED_CAST")
        val decodedResult = jsonCodec.decodeLenientMap(json)
        val typedResult = decodedResult + ("issueType" to issueType.displayName)
        return resultNormalizer.normalize(
            typedResult,
            acceptanceLabel = SubtaskBodyFormatter.labelForLanguage(copilot.resolveLanguage()),
        )
    }

    private fun isCancelledOrDeleted(runningId: Long): Boolean {
        val currentRecord = recordPort.findTicketCreationRecordById(runningId)
        return currentRecord == null || currentRecord.status == AnalysisStatus.CANCELLED
    }

    private fun persistCompletedRecord(
        runningId: Long,
        inputText: String,
        result: Map<String, Any?>,
        issueType: IssueType,
    ): Map<String, Any?> {
        val resultJsonSnapshot = runCatching { jsonCodec.encode(result) }.getOrNull()
        val record =
            recordPort.saveTicketCreationRecord(
                TicketCreationRecord(
                    id = runningId,
                    inputText = inputText.take(2000),
                    summary = result["summary"] as? String,
                    issueType = issueType.displayName,
                    labels = (result["labels"] as? List<*>)?.joinToString(",") { it.toString() },
                    resultJson = resultJsonSnapshot,
                    status = AnalysisStatus.COMPLETED,
                ),
            )
        runCatching {
            knowledgeMapper.fromTicketCreation(record)?.let { candidate ->
                knowledgePublication.publish(candidate)
            }
        }.onFailure { log.warn("Knowledge publication failed for ticket creation {}: {}", record.id, it.message) }
        return result + ("historyId" to record.id)
    }

    private fun markRecordFailed(
        runningId: Long,
        inputText: String,
        errorMessage: String?,
    ) {
        runCatching {
            val currentOnError = recordPort.findTicketCreationRecordById(runningId)
            if (currentOnError != null && currentOnError.status != "CANCELLED") {
                recordPort.saveTicketCreationRecord(
                    TicketCreationRecord(
                        id = runningId,
                        inputText = inputText.take(2000),
                        status = AnalysisStatus.FAILED,
                        errorMessage = errorMessage?.take(2000),
                    ),
                )
            }
        }
    }

    override fun createJiraTicket(
        jiraConfigId: Long,
        projectKey: String,
        summary: String,
        description: String,
        issueType: String,
        labels: List<String>,
        historyId: Long?,
        priority: String,
        subtasks: List<SubtaskDraft>,
        customFields: Map<String, Any>,
    ): Map<String, Any?> =
        jiraService.createJiraTicket(
            jiraConfigId,
            projectKey,
            summary,
            description,
            issueType,
            labels,
            historyId,
            priority,
            subtasks,
            customFields,
        )

    override fun jiraConfigExists(id: Long): Boolean = jiraService.jiraConfigExists(id)

    override fun requiredJiraFields(
        jiraConfigId: Long,
        projectKey: String,
        issueType: String,
    ): List<JiraCreateField> = jiraService.requiredJiraFields(jiraConfigId, projectKey, issueType)

    fun saveFailedAttempt(
        inputText: String,
        errorMessage: String,
    ) = historyService.saveFailedAttempt(inputText, errorMessage)

    override fun getAllRecords(): List<TicketCreationRecord> = historyService.getAllRecords()

    override fun getRecord(id: Long): TicketCreationRecord? = historyService.getRecord(id)

    override fun cancelRecord(id: Long): Boolean = historyService.cancelRecord(id)

    override fun deleteRecord(id: Long) = historyService.deleteRecord(id)

    override fun deleteAllRecords() = historyService.deleteAllRecords()

    private fun extractJson(raw: String): String {
        val clean =
            raw
                .replace(Regex("```(?:json)?\\s*\\n?"), "")
                .trim()

        var searchFrom = 0
        while (searchFrom < clean.length) {
            val start = clean.indexOf('{', searchFrom)
            if (start < 0) break

            val candidate = aiJsonExtractor.extractBalancedJsonObject(clean, start)
            if (candidate != null && candidate.contains(':')) return candidate
            searchFrom = start + 1
        }
        throw IllegalArgumentException("No JSON object found in AI response.")
    }
}
