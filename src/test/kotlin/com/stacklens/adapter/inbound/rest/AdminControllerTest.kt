package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.platform.FactoryResetUseCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class AdminControllerTest {
    @Mock
    private lateinit var factoryResetService: FactoryResetUseCase

    private val controller by lazy { AdminController(factoryResetService) }

    @Test
    fun `factoryResetDb calls service and returns success`() {
        val resp = controller.factoryResetDb()
        assertEquals(200, resp.statusCode.value())
        assertTrue(resp.body?.get("success") == true)
    }
}
