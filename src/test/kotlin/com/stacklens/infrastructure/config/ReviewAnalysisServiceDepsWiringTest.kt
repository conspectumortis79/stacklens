package com.stacklens.infrastructure.config.deps

import com.stacklens.application.collaboration.StartupRecovery
import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.ReviewAnalysisConfigPort
import com.stacklens.application.port.out.ai.AiUsageContextPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.appconfig.CopilotConfigPort
import com.stacklens.application.port.out.async.AsyncExecutorPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.jira.JiraSprintFetchPort
import com.stacklens.application.service.ReviewAnalysisPromptBuilder
import com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper
import com.stacklens.application.service.knowledge.KnowledgePublication
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class ReviewAnalysisServiceDepsWiringTest {
    @Test
    fun `ReviewAnalysisServiceDepsConfig factory wires knowledge publication and mapper`() {
        val publication = mock<KnowledgePublication>()
        val mapper = mock<AnalysisKnowledgeMapper>()
        val copilotConfig = mock<CopilotConfigPort>()

        val deps =
            ReviewAnalysisServiceDepsConfig()
                .reviewAnalysisServiceDeps(
                    analysisRepo = mock(),
                    configRepo = mock<ReviewAnalysisConfigPort>(),
                    jiraConfigRepo = mock<JiraConfigPort>(),
                    sprintHttpClient = mock<JiraSprintFetchPort>(),
                    copilotCliService = mock<CopilotPort>(),
                    aiUsageContext = mock<AiUsageContextPort>(),
                    promptBuilder = mock<ReviewAnalysisPromptBuilder>(),
                    jsonCodec = mock<JsonCodecPort>(),
                    i18n = mock<I18nPort>(),
                    asyncExecutor = mock<AsyncExecutorPort>(),
                    startupHealer = mock<StartupRecovery>(),
                    knowledgePublication = publication,
                    knowledgeMapper = mapper,
                    copilotConfig = copilotConfig,
                )

        assertNotNull(deps.knowledgePublication, "knowledgePublication must be injected, not null")
        assertNotNull(deps.knowledgeMapper, "knowledgeMapper must be injected, not null")
        assertSame(publication, deps.knowledgePublication)
        assertSame(mapper, deps.knowledgeMapper)
        assertSame(copilotConfig, deps.copilotConfig)
    }
}
