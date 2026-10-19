package com.stacklens.application.service

import com.stacklens.application.port.out.DatabaseConfigPort
import com.stacklens.application.port.out.McpGatewayPort
import com.stacklens.application.port.out.McpProcessControlPort
import com.stacklens.application.port.out.McpServerConfigPort
import com.stacklens.application.port.out.mcp.McpCircuitStatePort
import com.stacklens.testsupport.PrivateInvocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class McpManagementServiceTranslationCoverageTest {
    private val withI18n =
        McpManagementService(
            repo = mock<McpServerConfigPort>(),
            gateway = mock<McpGatewayPort>(),
            processControl = mock<McpProcessControlPort>(),
            databaseConfigs = mock<DatabaseConfigPort>(),
            circuitState = mock<McpCircuitStatePort>(),
            i18n = FakeI18n(),
        )

    @Test
    fun `a service with a translation port renders the key through it`() {
        assertEquals("mcp.be_any_key", translate(withI18n))
    }

    private fun translate(target: McpManagementService): Any? = PrivateInvocation.invoke(target, "tr", "mcp.be_any_key", emptyArray<Any>())
}
