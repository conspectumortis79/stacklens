package com.stacklens.infrastructure.config.deps

import com.stacklens.application.collaboration.StartupRecovery
import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.TicketCreationJiraPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.async.AsyncExecutorPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import com.stacklens.application.port.out.repo.GitlabPort
import com.stacklens.application.port.out.repo.LocalRepoScanPort
import com.stacklens.application.port.out.time.ClockPort
import com.stacklens.application.service.TicketRefinementJsonParser
import com.stacklens.application.service.ticketrefinement.TicketCommentPoster
import com.stacklens.application.service.ticketrefinement.TicketFieldFormatter
import com.stacklens.application.service.ticketrefinement.TicketRefinementCollaborators
import com.stacklens.application.service.ticketrefinement.TicketRefinementLifecycle
import com.stacklens.application.service.ticketrefinement.TicketRefinementSubtaskCoordinator
import com.stacklens.application.service.ticketrefinement.TicketRefinementSubtaskLedger
import com.stacklens.application.service.ticketrefinement.TicketRepoAnalyser
import com.stacklens.application.service.ticketrefinement.TicketReviewEngine
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class TicketRefinementDepsConfig {
    @Bean
    fun ticketRefinementDeps(
        ticketRefinementRepo: com.stacklens.application.port.out.TicketRefinementPort,
        jiraConfigRepo: JiraConfigPort,
        jira: com.stacklens.application.port.out.TicketRefinementJiraPort,
        copilot: com.stacklens.application.port.out.ai.CopilotPort,
        aiUsageContext: AiUsageContextPort,
        promptLoader: PromptReaderPort,
        jsonCodec: JsonCodecPort,
        i18n: I18nPort,
        dbConfigRepo: DatabaseConfigPort,
        dbSchemaPort: com.stacklens.application.port.out.database.DatabaseSchemaPort,
        asyncExecutor: AsyncExecutorPort,
        startupHealer: StartupRecovery,
    ): com.stacklens.application.service.ticketrefinement.TicketRefinementDeps =
        com.stacklens.application.service.ticketrefinement.TicketRefinementDeps(
            ticketRefinementRepo = ticketRefinementRepo,
            jiraConfigRepo = jiraConfigRepo,
            jira = jira,
            copilot = copilot,
            aiUsageContext = aiUsageContext,
            promptLoader = promptLoader,
            jsonCodec = jsonCodec,
            i18n = i18n,
            dbConfigRepo = dbConfigRepo,
            dbSchemaPort = dbSchemaPort,
            asyncExecutor = asyncExecutor,
            startupHealer = startupHealer,
        )
}

@Configuration
class TicketRefinementCollaboratorsConfig {
    @Bean
    fun ticketRefinementCollaborators(
        ticketRefinementRepo: com.stacklens.application.port.out.TicketRefinementPort,
        jiraConfigRepo: JiraConfigPort,
        jira: com.stacklens.application.port.out.TicketRefinementJiraPort,
        copilot: com.stacklens.application.port.out.ai.CopilotPort,
        promptLoader: PromptReaderPort,
        jsonCodec: JsonCodecPort,
        i18n: I18nPort,
        localRepoConfigRepo: LocalRepoConfigPort,
        gitlabConfigRepo: GitlabConfigPort,
        localRepoScan: LocalRepoScanPort,
        gitlab: GitlabPort,
        jsonParser: TicketRefinementJsonParser,
        clock: ClockPort,
        subtaskCreator: com.stacklens.application.service.JiraSubtaskCreator,
    ): TicketRefinementCollaborators {
        val fieldFormatter = TicketFieldFormatter()
        val subtaskLedger = TicketRefinementSubtaskLedger(ticketRefinementRepo, jsonCodec)
        return TicketRefinementCollaborators(
            fieldFormatter = fieldFormatter,
            reviewEngine = TicketReviewEngine(copilot, promptLoader, jira, i18n, jsonCodec, fieldFormatter, jsonParser),
            repoAnalyser =
                TicketRepoAnalyser(
                    localRepoConfigRepo,
                    gitlabConfigRepo,
                    localRepoScan,
                    gitlab,
                    copilot,
                    promptLoader,
                    i18n,
                    jsonCodec,
                    fieldFormatter,
                    jsonParser,
                ),
            commentPoster = TicketCommentPoster(jira, jiraConfigRepo, i18n, copilot, jsonCodec),
            lifecycle =
                TicketRefinementLifecycle(
                    ticketRefinementRepo,
                    jiraConfigRepo,
                    jsonCodec,
                    i18n,
                    copilot,
                    promptLoader,
                    clock,
                ),
            subtaskLedger = subtaskLedger,
            subtaskCoordinator = TicketRefinementSubtaskCoordinator(ticketRefinementRepo, jiraConfigRepo, subtaskCreator, subtaskLedger),
        )
    }
}

@Configuration
class TicketCreationCollaboratorsConfig {
    @Bean
    fun demoTicketSimulator(recordPort: com.stacklens.application.port.out.TicketCreationRecordPort): com.stacklens.adapter.out.demo.DemoTicketSimulator =
        com.stacklens.adapter.out.demo
            .DemoTicketSimulator(recordPort)

    @Bean
    fun ticketCreationHistoryService(recordPort: com.stacklens.application.port.out.TicketCreationRecordPort): com.stacklens.application.service.TicketCreationHistoryService =
        com.stacklens.application.service
            .TicketCreationHistoryService(recordPort)

    @Bean
    fun ticketCreationJiraService(
        jiraConfigPort: JiraConfigPort,
        jiraPort: TicketCreationJiraPort,
        demoTicketSimulator: com.stacklens.application.port.out.ticket.TicketCreationDemoPort,
        subtaskCreator: com.stacklens.application.service.JiraSubtaskCreator,
        recordPort: com.stacklens.application.port.out.TicketCreationRecordPort,
    ): com.stacklens.application.service.TicketCreationJiraService =
        com.stacklens.application.service
            .TicketCreationJiraService(jiraConfigPort, jiraPort, demoTicketSimulator, subtaskCreator, recordPort)

    @Bean
    fun jiraSubtaskCreator(
        jiraPort: com.stacklens.application.port.out.TicketCreationJiraPort,
        demoTicketSimulator: com.stacklens.application.port.out.ticket.TicketCreationDemoPort,
        copilot: com.stacklens.application.port.out.ai.CopilotPort,
    ): com.stacklens.application.service.JiraSubtaskCreator =
        com.stacklens.application.service
            .JiraSubtaskCreator(jiraPort, demoTicketSimulator, languageProvider = { copilot.resolveLanguage() })
}
