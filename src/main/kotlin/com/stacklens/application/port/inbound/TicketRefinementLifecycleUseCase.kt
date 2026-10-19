package com.stacklens.application.port.inbound

import com.stacklens.domain.model.TicketRefinement

interface TicketRefinementLifecycleUseCase {
    fun startPreparation(
        parentTicketKey: String,
        jiraConfigId: Long?,
        templateOverride: String?,
        repoSelections: List<RepoSelection> = emptyList(),
        singleTicketOnly: Boolean = true,
        dbConfigIds: List<Long> = emptyList(),
    ): TicketRefinement

    fun getPreparation(id: Long): TicketRefinement?

    fun getAllPreparations(): List<TicketRefinement>

    fun deletePreparation(id: Long)

    fun deleteAllPreparations()

    fun cancelPreparation(id: Long)
}
