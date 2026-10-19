package com.stacklens.application.service

import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.out.CodeAnalysisPort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.GrafanaConfigPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.UiPreferencesPort
import com.stacklens.application.port.out.grafana.GrafanaConfigInfo
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.platform.OsType
import com.stacklens.application.port.out.repo.GitClonePort
import com.stacklens.application.port.out.textfilter.TextFilterConfigPort
import com.stacklens.application.service.config.TextFilterSettingsService
import com.stacklens.application.service.config.UiPreferencesService
import com.stacklens.testsupport.FakePlatformPort
import com.stacklens.testsupport.NameFilteringDemoConfigFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SystemGuidanceServiceTest {
    private fun guidance(os: OsType) = SystemGuidanceService(FakePlatformPort(os))

    @Test
    fun `macOS hosts receive the full disk access hint`() {
        assertTrue(guidance(OsType.MACOS).writePermissionHint().contains("Full Disk Access"))
    }

    @Test
    fun `other hosts get no hint`() {
        assertEquals("", guidance(OsType.LINUX).writePermissionHint())
        assertEquals("", guidance(OsType.WINDOWS).writePermissionHint())
        assertEquals("", guidance(OsType.UNSUPPORTED).writePermissionHint())
    }
}

class UiPreferencesServiceBranchTest {
    private val port: UiPreferencesPort =
        mock {
            on { getAll() } doReturn mapOf("theme" to "modern")
        }

    @Test
    fun `reads are delegated to the preferences port`() {
        val service = UiPreferencesService(port, mock())

        assertEquals(mapOf("theme" to "modern"), service.allPreferences())
        assertEquals("modern", service.preference("theme"))
        assertEquals("", service.preference("unknown"))
    }

    @Test
    fun `writing a preference without the toggle collaborator still persists`() {
        UiPreferencesService(port, mock()).setPreference("theme", "legacy")

        verify(port).replaceAll(mapOf("theme" to "legacy"))
    }
}

class TextFilterSettingsServiceTest {
    private class FakeTextFilterPort(
        initial: Boolean,
    ) : TextFilterConfigPort {
        private var state = initial

        var lastSet: Boolean? = null

        override val enabled: Boolean get() = state

        override fun setEnabled(enabled: Boolean) {
            lastSet = enabled
            state = enabled
        }
    }

    @Test
    fun `the setting is read back from the port after writing`() {
        val port = FakeTextFilterPort(initial = false)
        val service = TextFilterSettingsService(port)

        assertFalse(service.isTextFilterEnabled())
        assertTrue(service.setTextFilterEnabled(true))
        assertEquals(true, port.lastSet)
        assertTrue(service.isTextFilterEnabled())
        assertFalse(service.setTextFilterEnabled(false))
    }
}

class VisibleConfigListingTest {
    @Test
    fun `the grafana analyse view hides demo configs through the use case`() {
        val grafanaConfigPort: GrafanaConfigPort = mock()
        whenever(grafanaConfigPort.findAllGrafanaConfigs()).thenReturn(
            listOf(
                com.stacklens.domain.model.GrafanaConfig(
                    name = "Demo grafana",
                    baseUrl = "http://gf",
                    username = "u",
                    password = "p",
                    apiToken = "t",
                    orgId = 1L,
                ),
                com.stacklens.domain.model.GrafanaConfig(
                    name = "Real grafana",
                    baseUrl = "http://gf2",
                    username = "u",
                    password = "p",
                    apiToken = "t",
                    orgId = 1L,
                ),
            ),
        )
        val service =
            GrafanaAnalysisService(
                grafanaConfigPort,
                mock(),
                mock(),
                mock(),
                mock(),
                mock(),
                mock(),
                NameFilteringDemoConfigFilter.instance,
            )

        assertEquals(listOf("Real grafana"), service.listVisibleConfigs().map(GrafanaConfigInfo::name))
        assertEquals(2, service.listConfigs().size)
    }

    @Test
    fun `the local repo use case hides demo gitlab configs`() {
        val gitlabConfigPort: GitlabConfigPort = mock()
        whenever(gitlabConfigPort.findByActiveTrue()).thenReturn(
            listOf(
                com.stacklens.domain.model
                    .GitlabConfig(name = "Demo gl", baseUrl = "https://gl", accessToken = "t"),
                com.stacklens.domain.model
                    .GitlabConfig(name = "Real gl", baseUrl = "https://gl", accessToken = "t"),
            ),
        )
        val service =
            LocalRepoService(
                gitlabConfigPort,
                mock(),
                mock<LocalRepoConfigPort>(),
                mock<CodeAnalysisPort>(),
                mock<GitClonePort>(),
                mock<I18nPort>(),
                NameFilteringDemoConfigFilter.instance,
                JavaFileSystemAdapter(),
            )

        assertEquals(listOf("Real gl"), service.listVisibleActiveGitlabConfigs().map { it.name })
    }
}
