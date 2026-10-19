package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.repo.DetectedRepoStack
import com.stacklens.application.port.inbound.repo.LocalRepoUseCase
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.service.repo.GitlabConnectivityErrorMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path

class LocalRepoApiControllerDetectStackTest {
    private val localRepoUseCase: LocalRepoUseCase = mock()

    private val i18n: I18nUseCase = mock()

    private val i18nPort: I18nPort =
        object : I18nPort {
            override fun t(
                key: String,
                vararg args: Any,
            ): String = i18n.t(key, *args)

            override fun tFor(
                lang: String,
                key: String,
                vararg args: Any,
            ): String = i18n.tFor(lang, key, *args)

            override fun translateError(e: Exception): String = i18n.translateError(e)

            override fun resolveLanguage(): String = i18n.resolveLanguage()
        }

    private val controller =
        LocalRepoApiController(
            localRepoUseCase = localRepoUseCase,
            i18n = i18n,
            connectivityErrorMapper = GitlabConnectivityErrorMapper(i18nPort),
            directoryBrowse = mock(),
        )

    @Test
    fun `a blank path is rejected without touching the file system`() {
        val response = controller.detectStack("   ")

        assertEquals(400, response.statusCode.value())
        assertEquals("path is required", response.body?.get("error"))
        verify(localRepoUseCase, never()).detectStack(any())
    }

    @Test
    fun `a missing path reports an empty stack instead of failing`() {
        val response = controller.detectStack(File(System.getProperty("java.io.tmpdir"), "stacklens-plain-fehlend").absolutePath)

        assertEquals(200, response.statusCode.value())
        assertEquals(emptyList<String>(), response.body?.get("stacks"))
        assertEquals(emptyList<String>(), response.body?.get("indicators"))
        verify(localRepoUseCase, never()).detectStack(any())
    }

    @Test
    fun `a file that is not a directory also reports an empty stack`(
        @TempDir tmp: Path,
    ) {
        val file = File(tmp.toFile(), "datei.txt").apply { writeText("x") }

        val response = controller.detectStack(file.absolutePath)

        assertEquals(emptyList<String>(), response.body?.get("stacks"))
        verify(localRepoUseCase, never()).detectStack(any())
    }

    @Test
    fun `an existing directory is detected with its canonical path`(
        @TempDir tmp: Path,
    ) {
        whenever(localRepoUseCase.detectStack(eq(tmp.toFile().canonicalPath)))
            .thenReturn(DetectedRepoStack(stacks = listOf("kotlin", "spring"), indicators = listOf("gradle")))

        val response = controller.detectStack("  ${tmp.toFile().absolutePath}  ")

        assertEquals(listOf("kotlin", "spring"), response.body?.get("stacks"))
        assertEquals(listOf("gradle"), response.body?.get("indicators"))
        assertEquals(tmp.toFile().canonicalPath, response.body?.get("path"))
        verify(localRepoUseCase).detectStack(tmp.toFile().canonicalPath)
    }
}
