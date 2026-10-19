package com.stacklens.adapter.out.compiler

import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.testsupport.FakeKotlincProcessRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class ToolingKotlinCompilerServiceBranchTest {
    private val stackLensConfig = mock<StackLensConfigPort>()
    private val i18n: I18nPort = mock()

    private fun service(): ToolingKotlinCompilerService =
        ToolingKotlinCompilerService(
            copilotCliService = mock(),
            promptLoader = mock(),
            i18n = i18n,
            stackLensConfig = stackLensConfig,
            compileRetryPolicy = ToolingCompileRetryPolicy(mock(), mock()),
            kotlincResolver = KotlincResolver(FakeKotlincProcessRunner(versionResult = KotlincRunResult(exitCode = 1, finished = true, output = ""))),
        )

    @Test
    fun `findJavaExecutable returns a string path`() {
        val result = service().findJavaExecutable()
        assertNotNull(result)
        assertEquals(true, result.isNotBlank())
    }

    @Test
    fun `findKotlinc returns null when no candidate is compatible`() {
        val result = service().findKotlinc()

        assertNull(result)
    }

    @Test
    fun `service is constructible with all dependencies`() {
        val svc = service()
        assertNotNull(svc)
    }
}
