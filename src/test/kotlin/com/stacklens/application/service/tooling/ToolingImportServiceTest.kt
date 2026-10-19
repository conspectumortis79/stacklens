package com.stacklens.application.service.tooling

import com.stacklens.adapter.out.tooling.FileToolJarStore
import com.stacklens.application.port.out.ConfluenceToolPort
import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.tooling.StoredToolJar
import com.stacklens.application.port.out.tooling.ToolJarStore
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.ConfluenceTool
import com.stacklens.testsupport.JarFileFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Path

class ToolingImportServiceTest {
    private val toolRepo: ConfluenceToolPort = mock()
    private val stackLensConfig: StackLensConfigPort = mock()
    private val i18n: I18nPort = mock()

    private val service =
        ToolingImportService(
            toolJarStore = FileToolJarStore(stackLensConfig),
            toolRepo = toolRepo,
            i18n = i18n,
            clock = com.stacklens.adapter.out.time.SystemClockPort,
        )

    @Test
    fun `importTool rejects when JAR is not valid`(
        @TempDir tempDir: Path,
    ) {
        whenever(stackLensConfig.dataDir).thenReturn(tempDir.toFile().absolutePath)
        whenever(i18n.t(eq("tooling.be_invalid_jar"))).thenReturn("invalid jar")

        assertThrows(IllegalArgumentException::class.java) {
            service.importTool(
                name = "MyTool",
                fileBytes = "not a jar".toByteArray(),
                originalFilename = "test.jar",
            )
        }
    }

    @Test
    fun `importTool rejects when JAR lacks X-StackLens-Tool marker`(
        @TempDir tempDir: Path,
    ) {
        whenever(stackLensConfig.dataDir).thenReturn(tempDir.toFile().absolutePath)
        whenever(i18n.t(eq("tooling.be_invalid_jar"))).thenReturn("invalid jar")
        whenever(i18n.t(eq("tooling.be_jar_not_stacklens_tool"))).thenReturn("not stacklens")

        assertThrows(IllegalArgumentException::class.java) {
            service.importTool(
                name = "MyTool",
                fileBytes = JarFileFixtures.minimalJar(),
                originalFilename = "test.jar",
            )
        }
    }

    @Test
    fun `importTool succeeds with valid marker JAR`(
        @TempDir tempDir: Path,
    ) {
        whenever(stackLensConfig.dataDir).thenReturn(tempDir.toFile().absolutePath)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }

        val tool =
            service.importTool(
                name = "MyTool",
                fileBytes = JarFileFixtures.markerJar(),
                originalFilename = "test.jar",
            )

        assertEquals(AnalysisStatus.READY, tool.status)
        assertEquals("MyTool", tool.name)
        assertEquals("IMPORTED", tool.confluencePageUrl)
        assertNotNull(tool.jarPath)
        assertNotNull(tool.completedAt)
    }

    @Test
    fun `importTool uses embedded name from manifest when present`(
        @TempDir tempDir: Path,
    ) {
        whenever(stackLensConfig.dataDir).thenReturn(tempDir.toFile().absolutePath)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }

        val tool =
            service.importTool(
                name = "OriginalName",
                fileBytes = JarFileFixtures.markerJar(name = "EmbeddedName", icon = "bi-tools"),
                originalFilename = "test.jar",
            )

        assertEquals("EmbeddedName", tool.name)
        assertEquals("bi-tools", tool.icon)
    }

    @Test
    fun `importTool sanitizes original filename`(
        @TempDir tempDir: Path,
    ) {
        whenever(stackLensConfig.dataDir).thenReturn(
            tempDir
                .resolve("sanitize-test")
                .toFile()
                .apply { mkdirs() }
                .absolutePath,
        )
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }

        val tool =
            service.importTool(
                name = "T",
                fileBytes = JarFileFixtures.markerJar(),
                originalFilename = "../../etc/passwd",
            )

        val jarPath = tool.jarPath ?: error("jarPath should not be null")
        val jarName = jarPath.substringAfterLast("/")
        assertFalse(jarName.contains(".."))
        assertFalse(jarName.contains("/"))
    }

    @Test
    fun `importTool sanitizes filenames with multiple dots into underscore separated forms`(
        @TempDir tempDir: Path,
    ) {
        whenever(stackLensConfig.dataDir).thenReturn(
            tempDir
                .resolve("dots-test")
                .toFile()
                .apply { mkdirs() }
                .absolutePath,
        )
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }

        val tool =
            service.importTool(
                name = "T",
                fileBytes = JarFileFixtures.markerJar(),
                originalFilename = "many...dots...jar",
            )
        val jarPath = tool.jarPath ?: error("jarPath should not be null")
        val jarName = jarPath.substringAfterLast("/")

        assertFalse(jarName.contains(".."), "filename must not contain consecutive dots: $jarName")
    }

    @Test
    fun `importTool sanitizes filenames that start with a dot`(
        @TempDir tempDir: Path,
    ) {
        whenever(stackLensConfig.dataDir).thenReturn(
            tempDir
                .resolve("leading-dot-test")
                .toFile()
                .apply { mkdirs() }
                .absolutePath,
        )
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }

        val tool =
            service.importTool(
                name = "T",
                fileBytes = JarFileFixtures.markerJar(),
                originalFilename = ".hidden.jar",
            )
        val jarPath = tool.jarPath ?: error("jarPath should not be null")
        val jarName = jarPath.substringAfterLast("/")

        assertFalse(jarName.startsWith("."), "filename must not start with a dot: $jarName")
    }

    @Test
    fun `importTool stores embedded icon when present`(
        @TempDir tempDir: Path,
    ) {
        whenever(stackLensConfig.dataDir).thenReturn(tempDir.toFile().absolutePath)
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }

        val tool =
            service.importTool(
                name = "T",
                fileBytes = JarFileFixtures.markerJar(icon = "bi-wrench"),
                originalFilename = "test.jar",
            )

        assertEquals("bi-wrench", tool.icon)
    }

    @Test
    fun `an import without embedded name uses the request name`() {
        val store: ToolJarStore = mock()
        whenever(store.storeImported(any(), any())).thenReturn(StoredToolJar("/tmp/a.jar", null, null))
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }
        val svc = ToolingImportService(toolJarStore = store, toolRepo = toolRepo, i18n = i18n, clock = com.stacklens.adapter.out.time.SystemClockPort)

        val tool = svc.importTool("  Requested Name  ", ByteArray(2), "tool.jar")

        assertEquals("Requested Name", tool.name)
    }

    @Test
    fun `an import with a blank embedded name uses the request name`() {
        val store: ToolJarStore = mock()
        whenever(store.storeImported(any(), any())).thenReturn(StoredToolJar("/tmp/b.jar", "   ", null))
        whenever(toolRepo.saveConfluenceTool(any())).thenAnswer { it.arguments[0] as ConfluenceTool }
        val svc = ToolingImportService(toolJarStore = store, toolRepo = toolRepo, i18n = i18n, clock = com.stacklens.adapter.out.time.SystemClockPort)

        val tool = svc.importTool("Fallback", ByteArray(2), "tool.jar")

        assertEquals("Fallback", tool.name)
    }
}
