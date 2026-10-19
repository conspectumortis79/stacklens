package com.stacklens.application.service.config
import com.stacklens.application.port.out.ConfluenceConfigPort
import com.stacklens.application.port.out.ConfluenceConnectionTestPort
import com.stacklens.testsupport.PassthroughDemoConfigFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ConfluenceConfigServiceTest {
    private val connectionTest: ConfluenceConnectionTestPort = mock()
    private val confluenceConfigRepo: ConfluenceConfigPort = mock()

    private val service = ConfluenceConfigService(confluenceConfigRepo, PassthroughDemoConfigFilter.instance, connectionTest)

    private val confluence =
        com.stacklens.domain.model.ConfluenceConfig(
            name = "c",
            baseUrl = "u",
            username = "u",
            apiToken = "t",
        )

    @Test
    fun `listConfluenceConfigs returns every stored config`() {
        whenever(confluenceConfigRepo.findAllConfluenceConfigs()).thenReturn(listOf(confluence))
        assertEquals(listOf(confluence), service.listConfluenceConfigs())
    }

    @Test
    fun `findConfluenceConfig returns the config of the requested id`() {
        whenever(confluenceConfigRepo.findConfluenceConfigById(1L)).thenReturn(confluence)
        assertEquals(confluence, service.findConfluenceConfig(1L))
    }

    @Test
    fun `saveConfluenceConfig stores and returns the config`() {
        whenever(confluenceConfigRepo.saveConfluenceConfig(confluence)).thenReturn(confluence)
        assertEquals(confluence, service.saveConfluenceConfig(confluence))
    }

    @Test
    fun `deleteConfluenceConfig removes the config of the requested id`() {
        service.deleteConfluenceConfig(1L)
        verify(confluenceConfigRepo).deleteConfluenceConfigById(1L)
    }

    @Test
    fun `deleteAllConfluenceConfigs empties the store`() {
        service.deleteAllConfluenceConfigs()
        verify(confluenceConfigRepo).deleteAllConfluenceConfigs()
    }

    @Test
    fun `testConfluenceConnection forwards the stored config to the connection port`() {
        val config =
            com.stacklens.domain.model
                .ConfluenceConfig(name = "c", baseUrl = "u", username = "u", apiToken = "t")
        whenever(confluenceConfigRepo.findConfluenceConfigById(1L)).thenReturn(config)
        whenever(connectionTest.testConnection(config, null)).thenReturn(ConfluenceConnectionTestPort.TestResult.Success(payload))

        assertEquals(payload, service.testConfluenceConnection(1L, null))
    }

    @Test
    fun `testConfluenceConnection reports a missing config as null`() {
        whenever(confluenceConfigRepo.findConfluenceConfigById(99L)).thenReturn(null)

        assertNull(service.testConfluenceConnection(1L, null))
    }

    @Test
    fun `listVisibleConfluenceConfigs keeps every config when the test environment is on`() {
        whenever(confluenceConfigRepo.findAllConfluenceConfigs()).thenReturn(
            listOf(
                com.stacklens.domain.model
                    .ConfluenceConfig(name = "c", baseUrl = "u", username = "u", apiToken = "t"),
            ),
        )

        assertEquals(1, service.listVisibleConfluenceConfigs().size)
    }

    private val payload: Map<String, Any> = mapOf("success" to true)
}
