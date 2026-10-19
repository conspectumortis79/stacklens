package com.stacklens.application.service

import com.stacklens.application.port.out.JiraConfigPort
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.ReviewAnalysisConfigPort
import com.stacklens.application.port.out.ReviewAnalysisPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.jira.JiraSprintFetchPort
import com.stacklens.application.service.ReviewAnalysisPromptBuilder
import com.stacklens.application.service.StartupHealer
import com.stacklens.infrastructure.config.CopilotProperties
import com.stacklens.testsupport.FakeKnowledgePublicationPort
import com.stacklens.testsupport.FakeTransactionPort
import com.stacklens.testsupport.TestJsonCodec
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class ReviewAnalysisServiceBranchTest {
    @Test
    fun `ReviewAnalysisService is constructible`() {
        val svc =
            ReviewAnalysisService(
                ReviewAnalysisServiceDeps(
                    analysisRepo = mock<ReviewAnalysisPort>(),
                    configRepo = mock<ReviewAnalysisConfigPort>(),
                    jiraConfigRepo = mock<JiraConfigPort>(),
                    sprintHttpClient = mock<JiraSprintFetchPort>(),
                    copilotCliService = mock<CopilotPort>(),
                    aiUsageContext = mock<com.stacklens.application.port.out.ai.AiUsageContextPort>(),
                    promptBuilder = mock<ReviewAnalysisPromptBuilder>(),
                    jsonCodec = mock<JsonCodecPort>(),
                    i18n = mock<I18nPort>(),
                    asyncExecutor = com.stacklens.testsupport.InlineAsyncExecutor(),
                    startupHealer = mock<StartupHealer>(),
                    knowledgePublication = FakeKnowledgePublicationPort(),
                    knowledgeMapper = mock<com.stacklens.application.service.knowledge.AnalysisKnowledgeMapper>(),
                    copilotConfig = CopilotProperties(),
                ),
                pptxStorage =
                    mock<com.stacklens.application.port.out.PptxStoragePort>(),
                pptxOrchestrator =
                    com.stacklens.application.service.review.PptxAnalysisOrchestrator(
                        com.stacklens.adapter.out.review.pptx
                            .PptxStructureExtractor(),
                        TestJsonCodec.create(),
                        com.stacklens.application.service.review
                            .PptxQuestionExtractor(TestJsonCodec.create()),
                        com.stacklens.application.service.review
                            .PptxAnswerMapper(TestJsonCodec.create()),
                        com.stacklens.adapter.out.review.pptx
                            .PptxSlideHtmlRenderer(),
                        mock<com.stacklens.application.port.out.PptxStoragePort>(),
                        com.stacklens.application.service.review.PptxPromptAssembler(
                            com.stacklens.adapter.out.ai.PromptLoader(
                                org.springframework.core.io
                                    .DefaultResourceLoader(),
                                com.fasterxml.jackson.databind
                                    .ObjectMapper(),
                                "./data",
                            ),
                        ),
                        com.stacklens.application.service.review
                            .PptxAnalysisLog(TestJsonCodec.create()),
                        clock = com.stacklens.adapter.out.time.SystemClockPort,
                    ),
                aiCaller =
                    com.stacklens.application.service.ai
                        .CopilotAiCaller(mock<CopilotPort>()),
                transactionPort = FakeTransactionPort(),
                clock = com.stacklens.adapter.out.time.SystemClockPort,
            )
        assertNotNull(svc)
    }
}
