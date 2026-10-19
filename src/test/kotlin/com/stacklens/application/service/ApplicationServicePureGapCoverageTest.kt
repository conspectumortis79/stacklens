package com.stacklens.application.service

import com.stacklens.application.port.inbound.repo.DetectedRepoStack
import com.stacklens.application.port.inbound.web.LoggingAnalysePageData
import com.stacklens.application.port.inbound.web.PipelineAnalysePageData
import com.stacklens.application.port.out.JsonCodecPort
import com.stacklens.application.port.out.process.ShellPort
import com.stacklens.application.service.essential.GhCopilotToolInstaller
import com.stacklens.application.service.essential.ToolInstallation
import com.stacklens.application.service.grafana.DashboardPatchApplier
import com.stacklens.application.service.knowledge.KnowledgeChunker
import com.stacklens.application.service.loadtest.RouteFileDetector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RouteFileDetectorCoverageTest {
    @Test
    fun `a router named jvm source file is a route file`() {
        assertTrue(RouteFileDetector.isRouteFileByName("src/main/kotlin/api/UserRouter.kt"))
        assertTrue(RouteFileDetector.isRouteFileByName("src/main/java/api/UserRouter.java"))
        assertTrue(RouteFileDetector.isRouteFileByName("build/UserRouter.kts"))
    }

    @Test
    fun `a controller named jvm source file is a route file`() {
        assertTrue(RouteFileDetector.isRouteFileByName("src/main/kotlin/web/OrderController.kt"))
        assertTrue(RouteFileDetector.isRouteFileByName("src/main/java/web/OrderResource.java"))
        assertTrue(RouteFileDetector.isRouteFileByName("src/main/kotlin/web/OrderEndpoint.kt"))
    }

    @Test
    fun `the php python and node entry points are route files`() {
        assertTrue(RouteFileDetector.isRouteFileByName("api.php"))
        assertTrue(RouteFileDetector.isRouteFileByName("app/urls.py"))
        assertTrue(RouteFileDetector.isRouteFileByName("web/router.js"))
        assertTrue(RouteFileDetector.isRouteFileByName("web/router.ts"))
        assertTrue(RouteFileDetector.isRouteFileByName("app.js"))
        assertTrue(RouteFileDetector.isRouteFileByName("routes.php"))
        assertTrue(RouteFileDetector.isRouteFileByName("routes/web.php"))
        assertTrue(RouteFileDetector.isRouteFileByName("src/routes/users.ts"))
        assertTrue(RouteFileDetector.isRouteFileByName("src/routing/loader.ts"))
    }

    @Test
    fun `unrelated files are not route files`() {
        assertFalse(RouteFileDetector.isRouteFileByName("src/main/kotlin/util/StringRouter.scala"))
        assertFalse(RouteFileDetector.isRouteFileByName("src/main/kotlin/util/Helper.kt"))
        assertFalse(RouteFileDetector.isRouteFileByName("README.md"))
    }
}

class KnowledgeChunkerBlankParagraphCoverageTest {
    private val chunker = KnowledgeChunker()

    @Test
    fun `blank paragraphs between paragraphs do not create chunks`() {
        val chunks = chunker.chunk("erster Absatz\n\n   \n\nzweiter Absatz")

        assertEquals(1, chunks.size)
        assertTrue(chunks.single().content.contains("erster Absatz"))
        assertTrue(chunks.single().content.contains("zweiter Absatz"))
    }

    @Test
    fun `an oversized paragraph is split per sentence`() {
        val sentence = "wort ".repeat(600).trim()
        val text = "$sentence.\n\n$sentence."

        val chunks = chunker.chunk(text, chunkSize = 700, chunkOverlap = 10)

        assertTrue(chunks.size > 1)
    }
}

class DashboardPatchApplierCoverageTest {
    private val jsonCodec = mock<JsonCodecPort>()
    private val applier = DashboardPatchApplier(jsonCodec)

    @Test
    fun `a payload without a dashboard node is patched at the root level`() {
        whenever(jsonCodec.decodeMap("{\"panels\":[]}")).thenReturn(mutableMapOf<String, Any?>("panels" to mutableListOf<Any?>()))
        whenever(jsonCodec.decodeMap("{\"dashboardPatch\":{\"title\":\"neu\"}}")).thenReturn(
            mapOf("dashboardPatch" to mapOf("title" to "neu")),
        )
        whenever(jsonCodec.encode(any())).thenReturn("ENCODED")

        assertEquals("ENCODED", applier.applyPatch("{\"panels\":[]}", "{\"dashboardPatch\":{\"title\":\"neu\"}}"))
    }
}

class GhCopilotToolInstallerCoverageTest {
    private val installer = GhCopilotToolInstaller()

    @Test
    fun `detection keeps the previously known installation path`() {
        val shell = mock<ShellPort>()
        whenever(shell.commandSucceeds("gh copilot --help")).thenReturn(true)

        val detected = installer.detect(shell, ToolInstallation(installed = false, path = "/opt/gh"))

        assertTrue(detected.installed)
        assertEquals("/opt/gh", detected.path)
    }

    @Test
    fun `detection without a known installation reports no path`() {
        val shell = mock<ShellPort>()
        whenever(shell.commandSucceeds("gh copilot --help")).thenReturn(false)

        val detected = installer.detect(shell, null)

        assertFalse(detected.installed)
        assertEquals(null, detected.path)
    }
}

class WebPageQueryModelsCoverageTest {
    @Test
    fun `page models default their optional collections to empty lists`() {
        val logging = LoggingAnalysePageData(localRepoConfigs = emptyList())
        val pipeline = PipelineAnalysePageData(localRepoConfigs = emptyList(), gitlabConfigs = emptyList())

        assertTrue(logging.recentAnalyses.isEmpty())
        assertTrue(pipeline.githubRepoConfigs.isEmpty())
        assertEquals(DetectedRepoStack(emptyList(), emptyList()).stacks, emptyList<String>())
    }
}
