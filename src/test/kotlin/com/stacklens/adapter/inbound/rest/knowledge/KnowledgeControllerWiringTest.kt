package com.stacklens.adapter.inbound.rest.knowledge

import com.stacklens.application.port.inbound.knowledge.KnowledgeBaseUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeBundleExportUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeBundleImportUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeConfigUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeExportUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeImportUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeJobUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeLocalFileImportUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeMaintenanceUseCase
import com.stacklens.application.port.inbound.knowledge.KnowledgeSourceUseCase
import com.stacklens.application.service.knowledge.KnowledgeReindexService
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class KnowledgeControllerWiringTest {
    @Test
    fun `all six knowledge controllers can be instantiated and expose a MockMvc`() {
        val sourceUseCase: KnowledgeSourceUseCase = mock()
        val configUseCase: KnowledgeConfigUseCase = mock()
        val jobUseCase: KnowledgeJobUseCase = mock()
        val baseUseCase: KnowledgeBaseUseCase = mock()
        val exportUseCase: KnowledgeExportUseCase = mock()
        val importUseCase: KnowledgeImportUseCase = mock()
        val bundleExportUseCase: KnowledgeBundleExportUseCase = mock()
        val bundleImportUseCase: KnowledgeBundleImportUseCase = mock()
        val localFileImportUseCase: KnowledgeLocalFileImportUseCase = mock()
        val maintenance: KnowledgeMaintenanceUseCase = mock()
        val reindexService: KnowledgeReindexService = mock()

        val config = KnowledgeConfigController(configUseCase)
        val bases = KnowledgeBasesController(baseUseCase)
        val sources = KnowledgeSourcesController(sourceUseCase)
        val jobs = KnowledgeJobsController(jobUseCase)
        val import =
            KnowledgeImportController(
                exportUseCase = exportUseCase,
                importUseCase = importUseCase,
                bundleExportUseCase = bundleExportUseCase,
                bundleImportUseCase = bundleImportUseCase,
                localFileImportUseCase = localFileImportUseCase,
            )
        val ops =
            KnowledgeOperationsController(
                maintenanceUseCase = maintenance,
                reindexService = reindexService,
            )

        val mockMvc =
            MockMvcBuilders
                .standaloneSetup(config, bases, sources, jobs, import, ops)
                .setControllerAdvice(KnowledgeExceptionAdvice())
                .build()

        assertNotNull(mockMvc)
    }
}
