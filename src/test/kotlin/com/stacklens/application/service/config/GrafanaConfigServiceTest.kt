package com.stacklens.application.service.config
import com.stacklens.application.port.out.GrafanaConfigPort
import com.stacklens.application.port.out.GrafanaConnectionTestPort
import com.stacklens.testsupport.PassthroughDemoConfigFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class GrafanaConfigServiceTest {
    private val connectionTest: GrafanaConnectionTestPort = mock()
    private val grafanaConfigRepo: GrafanaConfigPort = mock()

    private val service = GrafanaConfigService(grafanaConfigRepo, PassthroughDemoConfigFilter.instance, connectionTest)

    private val grafana =
        com.stacklens.domain.model.GrafanaConfig(
            name = "g",
            baseUrl = "u",
            username = "u",
            password = "p",
        )

    @Test
    fun `listGrafanaConfigs returns every stored config`() {
        whenever(grafanaConfigRepo.findAllGrafanaConfigs()).thenReturn(listOf(grafana))
        assertEquals(listOf(grafana), service.listGrafanaConfigs())
    }

    @Test
    fun `findGrafanaConfig returns the config of the requested id`() {
        whenever(grafanaConfigRepo.findGrafanaConfigById(1L)).thenReturn(grafana)
        assertEquals(grafana, service.findGrafanaConfig(1L))
    }

    @Test
    fun `saveGrafanaConfig stores and returns the config`() {
        whenever(grafanaConfigRepo.saveGrafanaConfig(grafana)).thenReturn(grafana)
        assertEquals(grafana, service.saveGrafanaConfig(grafana))
    }

    @Test
    fun `deleteGrafanaConfig removes the config of the requested id`() {
        service.deleteGrafanaConfig(1L)
        verify(grafanaConfigRepo).deleteGrafanaConfigById(1L)
    }

    @Test
    fun `deleteAllGrafanaConfigs empties the store`() {
        service.deleteAllGrafanaConfigs()
        verify(grafanaConfigRepo).deleteAllGrafanaConfigs()
    }

    @Test
    fun `testGrafanaConnection forwards the stored config to the connection port`() {
        val config =
            com.stacklens.domain.model
                .GrafanaConfig(name = "g", baseUrl = "u", username = "u", password = "p", apiToken = "t", orgId = 1L)
        whenever(grafanaConfigRepo.findGrafanaConfigById(1L)).thenReturn(config)
        whenever(connectionTest.testConnection(config)).thenReturn(GrafanaConnectionTestPort.TestResult.Success(payload))

        assertEquals(payload, service.testGrafanaConnection(1L))
    }

    @Test
    fun `testGrafanaConnection reports a missing config as null`() {
        whenever(grafanaConfigRepo.findGrafanaConfigById(99L)).thenReturn(null)

        assertNull(service.testGrafanaConnection(1L))
    }

    @Test
    fun `listVisibleGrafanaConfigs keeps every config when the test environment is on`() {
        whenever(
            grafanaConfigRepo.findAllGrafanaConfigs(),
        ).thenReturn(
            listOf(
                com.stacklens.domain.model
                    .GrafanaConfig(name = "g", baseUrl = "u", username = "u", password = "p", apiToken = "t", orgId = 1L),
            ),
        )

        assertEquals(1, service.listVisibleGrafanaConfigs().size)
    }

    private val payload: Map<String, Any> = mapOf("success" to true)
}
