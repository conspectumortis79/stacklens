package com.stacklens.application.service

import com.stacklens.application.service.startup.StaleWorkHealer
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class StartupMaintenanceServiceTest {
    @Test
    fun `healStaleWork heals every registered healer exactly once`() {
        val first = mock<StaleWorkHealer>()
        val second = mock<StaleWorkHealer>()

        StartupMaintenanceService(listOf(first, second)).healStaleWork()

        verify(first).heal()
        verify(second).heal()
    }
}
