package com.stacklens.testsupport

import com.stacklens.application.port.out.AiJsonRepairerPort
import com.stacklens.application.port.out.AiResponseJsonExtractorPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.TicketCreationJiraPort
import com.stacklens.application.port.out.repo.GitlabPort
import com.stacklens.application.port.out.repo.LocalRepoScanPort
import com.stacklens.application.service.JiraSubtaskCreator
import com.stacklens.application.service.TicketRefinementJsonParser
import com.stacklens.application.service.ticketrefinement.TicketRefinementCollaborators
import com.stacklens.application.service.ticketrefinement.TicketRefinementDeps
import com.stacklens.infrastructure.config.deps.TicketRefinementCollaboratorsConfig
import org.mockito.kotlin.mock

fun TicketRefinementDeps.testCollaborators(
    localRepoConfigRepo: LocalRepoConfigPort,
    gitlabConfigRepo: GitlabConfigPort,
    localRepoScan: LocalRepoScanPort,
    gitlab: GitlabPort,
    aiJsonRepairer: AiJsonRepairerPort,
    aiJsonExtractor: AiResponseJsonExtractorPort,
): TicketRefinementCollaborators = testCollaborators(localRepoConfigRepo, gitlabConfigRepo, localRepoScan, gitlab, aiJsonRepairer, aiJsonExtractor, mock<TicketCreationJiraPort>())

fun TicketRefinementDeps.testCollaborators(
    localRepoConfigRepo: LocalRepoConfigPort,
    gitlabConfigRepo: GitlabConfigPort,
    localRepoScan: LocalRepoScanPort,
    gitlab: GitlabPort,
    aiJsonRepairer: AiJsonRepairerPort,
    aiJsonExtractor: AiResponseJsonExtractorPort,
    ticketCreationJiraPort: TicketCreationJiraPort,
): TicketRefinementCollaborators =
    TicketRefinementCollaboratorsConfig()
        .ticketRefinementCollaborators(
            ticketRefinementRepo = ticketRefinementRepo,
            jiraConfigRepo = jiraConfigRepo,
            jira = jira,
            copilot = copilot,
            promptLoader = promptLoader,
            jsonCodec = jsonCodec,
            i18n = i18n,
            localRepoConfigRepo = localRepoConfigRepo,
            gitlabConfigRepo = gitlabConfigRepo,
            localRepoScan = localRepoScan,
            gitlab = gitlab,
            jsonParser = TicketRefinementJsonParser(jsonCodec, aiJsonRepairer, aiJsonExtractor),
            clock = com.stacklens.adapter.out.time.SystemClockPort,
            subtaskCreator = JiraSubtaskCreator(ticketCreationJiraPort, NoDemoTicketCreation, languageProvider = { copilot.resolveLanguage() }),
        )
