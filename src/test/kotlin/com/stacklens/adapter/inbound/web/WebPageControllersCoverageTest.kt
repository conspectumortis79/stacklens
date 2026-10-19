package com.stacklens.adapter.inbound.web

import com.stacklens.adapter.out.demo.DemoConfigFilter
import com.stacklens.application.port.inbound.grafana.GrafanaAnalysisUseCase
import com.stacklens.application.port.inbound.jwt.JwtAnalysisUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeBaseUseCase
import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.web.LoggingAnalysePageQueryUseCase
import com.stacklens.testsupport.PassthroughDemoConfigFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.ui.ExtendedModelMap

class WebPageControllersCoverageTest {
    private val demoConfigFilter: DemoConfigFilter = PassthroughDemoConfigFilter.instance

    @Test
    fun `grafana page exposes the configured data sources to the view`() {
        val useCase = mock<GrafanaAnalysisUseCase>()
        whenever(useCase.listVisibleConfigs()).thenReturn(emptyList())
        val model = ExtendedModelMap()

        val view = GrafanaAnalyseController(useCase).grafanaAnalyse(model)

        assertEquals("grafana-analysis", view)
        assertEquals(emptyList<Any>(), model.asMap().getValue("grafanaConfigs"))
        verify(useCase).listVisibleConfigs()
    }

    @Test
    fun `knowledge page exposes every knowledge base`() {
        val useCase = mock<KnowledgeBaseUseCase>()
        whenever(useCase.listBases()).thenReturn(emptyList())
        val model = ExtendedModelMap()

        assertEquals("knowledge", KnowledgeWebController(useCase).knowledgePage(model))
        assertEquals(emptyList<Any>(), model.asMap().getValue("knowledgeBases"))
    }

    @Test
    fun `image extraction page resolves to its template`() {
        val controller = SimplePageWebController(mock<LoggingAnalysePageQueryUseCase>())

        assertEquals("image-extraction", controller.imageExtractionPage())
    }

    @Test
    fun `a blank jwt token is rejected with the translated hint`() {
        val i18n = mock<I18nUseCase>()
        whenever(i18n.t("jwt_analysis.be_token_required")).thenReturn("Token erforderlich")
        val controller = JwtAnalysisController(mock<JwtAnalysisUseCase>(), i18n)

        val response = controller.analyze(JwtAnalysisController.AnalyzeRequest("   "))

        assertEquals("Token erforderlich", response.body?.let { (it as Map<*, *>)["error"] })
    }
}
