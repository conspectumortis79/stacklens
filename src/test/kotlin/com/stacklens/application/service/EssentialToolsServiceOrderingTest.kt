package com.stacklens.application.service

import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.platform.OsType
import com.stacklens.application.port.out.platform.Platform
import com.stacklens.application.port.out.platform.PlatformPort
import com.stacklens.application.port.out.process.ShellPort
import com.stacklens.application.service.essential.ToolInstallation
import com.stacklens.application.service.essential.ToolInstaller
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class EssentialToolsServiceOrderingTest {
    private val macos =
        Platform(
            osType = OsType.MACOS,
            distroId = "",
            distroVersion = "",
            pretty = "macOS",
            supported = true,
        )

    private fun installer(
        id: String,
        requires: String?,
    ): ToolInstaller {
        val installer = mock<ToolInstaller>()
        whenever(installer.toolId).thenReturn(id)
        whenever(installer.displayName).thenReturn(id)
        whenever(installer.required).thenReturn(false)
        whenever(installer.requiresToolId).thenReturn(requires)
        whenever(installer.detect(any(), anyOrNull())).thenReturn(ToolInstallation(installed = true, path = "/usr/bin/$id", version = "1.0"))
        return installer
    }

    private fun platformPort(): PlatformPort = mock<PlatformPort>().also { whenever(it.detect()).thenReturn(macos) }

    @Test
    fun `installers that share the same dependency state are ordered by their tool id`() {
        val service =
            EssentialToolsService(
                mock<I18nPort>(),
                platformPort(),
                mock<ShellPort>(),
                listOf(installer("zzz", null), installer("aaa", null), installer("mmm", null)),
                emptyList(),
            )

        @Suppress("UNCHECKED_CAST")
        val tools = service.getStatus()["tools"] as List<Map<String, Any?>>

        assertEquals(listOf("aaa", "mmm", "zzz"), tools.map { it["id"] })
    }

    @Test
    fun `installers that need another tool are ordered last`() {
        val service =
            EssentialToolsService(
                mock<I18nPort>(),
                platformPort(),
                mock<ShellPort>(),
                listOf(installer("dependent", "zzz"), installer("standalone", null)),
                emptyList(),
            )

        @Suppress("UNCHECKED_CAST")
        val tools = service.getStatus()["tools"] as List<Map<String, Any?>>

        assertEquals(listOf("standalone", "dependent"), tools.map { it["id"] })
    }
}
