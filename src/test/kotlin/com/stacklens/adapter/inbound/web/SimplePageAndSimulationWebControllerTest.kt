package com.stacklens.adapter.inbound.web

import com.stacklens.application.port.inbound.web.LoggingAnalysePageData
import com.stacklens.application.port.inbound.web.LoggingAnalysePageQueryUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.ui.ExtendedModelMap

class SimplePageAndSimulationWebControllerTest {
    private val pageQuery: LoggingAnalysePageQueryUseCase = mock()
    private val controller = SimplePageWebController(pageQuery)

    @Test
    fun `root redirects to search`() {
        assertEquals("redirect:/search", controller.root())
    }

    @Test
    fun `docsPage returns the docs template name`() {
        assertEquals("docs", controller.docsPage())
    }

    @Test
    fun `sqlAnalysePage returns the sql-analysis template name`() {
        assertEquals("sql-analysis", controller.sqlAnalysePage())
    }

    @Test
    fun `loggingPage returns the logging template name and populates the model`() {
        whenever(pageQuery.getLoggingAnalysePageData()).thenReturn(LoggingAnalysePageData(emptyList(), emptyList()))
        val model = ExtendedModelMap()
        assertEquals("logging", controller.loggingPage(model))
        assertEquals(emptyList<Any>(), model.getAttribute("localRepoConfigs"))
        assertEquals(emptyList<Any>(), model.getAttribute("recentFixAnalyses"))
    }

    @Test
    fun `dockerTestPage returns the docker-test template name`() {
        assertEquals("docker-test", controller.dockerTestPage())
    }

    @Test
    fun `simulatedMrPage sets the analysisId model attribute and returns the template name`() {
        val model = ExtendedModelMap()
        val view = SimulationWebController().simulatedMrPage(42L, model)
        assertEquals("simulated-mr", view)
        assertEquals(42L, model.getAttribute("analysisId"))
    }

    @Test
    fun `simulatedGithubPrPage sets the analysisId model attribute and returns the template name`() {
        val model = ExtendedModelMap()
        val view = SimulationWebController().simulatedGithubPrPage(7L, model)
        assertEquals("simulated-github-pr", view)
        assertEquals(7L, model.getAttribute("analysisId"))
    }

    @Test
    fun `simulatedPipelineMrPage sets the analysisId model attribute and returns the template name`() {
        val model = ExtendedModelMap()
        val view = SimulationWebController().simulatedPipelineMrPage(1L, model)
        assertEquals("simulated-pipeline-mr", view)
        assertEquals(1L, model.getAttribute("analysisId"))
    }

    @Test
    fun `simulatedPipelinePrPage sets the analysisId model attribute and returns the template name`() {
        val model = ExtendedModelMap()
        val view = SimulationWebController().simulatedPipelinePrPage(2L, model)
        assertEquals("simulated-pipeline-pr", view)
        assertEquals(2L, model.getAttribute("analysisId"))
    }
}
