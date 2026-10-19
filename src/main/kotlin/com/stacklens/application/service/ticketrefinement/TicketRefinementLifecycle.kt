
package com.stacklens.application.service.ticketrefinement

import com.stacklens.application.port.inbound.RepoSelection
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.JsonWriterPort
import com.stacklens.application.port.out.TicketRefinementPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.service.AnalysisConstants
import com.stacklens.application.stereotype.ApplicationService
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.TicketRefinement

@ApplicationService
class TicketRefinementLifecycle(
    private val ticketRefinementRepo: TicketRefinementPort,
    private val jiraConfigRepo: JiraConfigPort,
    private val jsonCodec: JsonWriterPort,
    private val i18n: I18nPort,
    private val copilot: CopilotPort,
    private val promptLoader: PromptReaderPort,
    private val clock: ClockPort,
) {
    fun persistNewPreparation(
        parentTicketKey: String,
        jiraConfigId: Long?,
        templateOverride: String?,
        repoSelections: List<RepoSelection>,
        singleTicketOnly: Boolean,
        dbConfigIds: List<Long>,
    ): TicketRefinement {
        require(parentTicketKey.isNotBlank()) { "parentTicketKey must not be blank" }
        val resolvedConfigId = jiraConfigId ?: jiraConfigRepo.findFirstByActiveTrue()?.id
        val entity =
            TicketRefinement(
                parentTicketKey = parentTicketKey.trim(),
                jiraConfigId = resolvedConfigId,
                templateOverride = templateOverride?.takeIf { it.isNotBlank() },
                singleTicketOnly = singleTicketOnly,
                status = AnalysisStatus.PENDING,
                repoSelectionsJson =
                    if (repoSelections.isEmpty()) {
                        null
                    } else {
                        jsonCodec.encode(repoSelections)
                    },
                databaseConfigIds = if (dbConfigIds.isEmpty()) null else dbConfigIds.joinToString(","),
            )
        return ticketRefinementRepo.saveTicketRefinement(entity)
    }

    fun getPreparation(id: Long): TicketRefinement? = ticketRefinementRepo.findTicketRefinementById(id)

    fun getAllPreparations(): List<TicketRefinement> = ticketRefinementRepo.findRecentTicketRefinements(AnalysisConstants.MAX_RECENT_PREPARATIONS)

    fun cancelPreparation(id: Long) {
        val entity = ticketRefinementRepo.findTicketRefinementById(id) ?: return
        if (entity.status == AnalysisStatus.RUNNING || entity.status == AnalysisStatus.PENDING) {
            entity.status = AnalysisStatus.CANCELLED
            entity.errorMessage = i18n.t("ticket_refinement.be_cancelled")
            entity.completedAt = clock.localDateTime()
            ticketRefinementRepo.saveTicketRefinement(entity)
        }
    }

    fun deletePreparation(id: Long) = ticketRefinementRepo.deleteTicketRefinementById(id)

    fun deleteAllPreparations() = ticketRefinementRepo.deleteAllTicketRefinements()

    fun firstActiveJiraConfigId(): Long? = jiraConfigRepo.findFirstByActiveTrue()?.id

    fun jiraConfigExists(id: Long): Boolean = jiraConfigRepo.jiraConfigExistsById(id)

    fun defaultPromptTemplate(): String = promptLoader.get(copilot.resolveLanguage(), "ticketrefinement.improve_ticket", emptyMap())

    fun save(entity: TicketRefinement): TicketRefinement = ticketRefinementRepo.saveTicketRefinement(entity)

    fun markFailed(
        id: Long,
        errorMessage: String,
    ) {
        val fresh = ticketRefinementRepo.findTicketRefinementById(id) ?: return
        fresh.status = AnalysisStatus.FAILED
        fresh.errorMessage = errorMessage
        fresh.completedAt = clock.localDateTime()
        runCatching { ticketRefinementRepo.saveTicketRefinement(fresh) }
    }

    fun currentState(id: Long): TicketRefinement? = ticketRefinementRepo.findTicketRefinementById(id)
}
