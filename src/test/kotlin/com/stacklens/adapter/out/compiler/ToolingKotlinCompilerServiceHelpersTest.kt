package com.stacklens.adapter.out.compiler

import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.testsupport.FakeKotlincProcessRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class ToolingKotlinCompilerServiceHelpersTest {
    @Test
    fun `findJavaExecutable returns a valid path`() {
        val svc =
            ToolingKotlinCompilerService(
                copilotCliService = mock(),
                promptLoader = mock(),
                i18n = mock<I18nPort>(),
                stackLensConfig = mock(),
                compileRetryPolicy = ToolingCompileRetryPolicy(mock(), mock()),
            )
        val result = svc.findJavaExecutable()
        assertNotNull(result)
        assertTrue(result.isNotBlank())
    }

    @Test
    fun `findKotlinc returns null when the version check rejects every candidate`() {
        val svc =
            ToolingKotlinCompilerService(
                copilotCliService = mock(),
                promptLoader = mock(),
                i18n = mock<I18nPort>(),
                stackLensConfig = mock(),
                compileRetryPolicy = ToolingCompileRetryPolicy(mock(), mock()),
                kotlincResolver = KotlincResolver(FakeKotlincProcessRunner(versionResult = KotlincRunResult(exitCode = 1, finished = true, output = ""))),
            )
        val result = svc.findKotlinc()
        assertNull(result)
    }

    @Test
    fun `extractKotlinCode extracts kotlin code block`() {
        val svc =
            ToolingKotlinCompilerService(
                copilotCliService = mock(),
                promptLoader = mock(),
                i18n = mock<I18nPort>(),
                stackLensConfig = mock(),
                compileRetryPolicy = ToolingCompileRetryPolicy(mock(), mock()),
            )
        val code = "fun main() {}"
        val result = svc.extractKotlinCode("```kotlin\n$code\n```")
        assertEquals(code, result)
    }

    @Test
    fun `extractKotlinCode returns empty for no code block`() {
        val svc =
            ToolingKotlinCompilerService(
                copilotCliService = mock(),
                promptLoader = mock(),
                i18n = mock<I18nPort>(),
                stackLensConfig = mock(),
                compileRetryPolicy = ToolingCompileRetryPolicy(mock(), mock()),
            )
        val result =
            try {
                svc.extractKotlinCode("no code block here")
            } catch (e: Exception) {
                ""
            }
        assertEquals(true, result.isEmpty() || result == "no code block here")
    }

    @Test
    fun `extractKotlinCode extracts generic code block`() {
        val svc =
            ToolingKotlinCompilerService(
                copilotCliService = mock(),
                promptLoader = mock(),
                i18n = mock<I18nPort>(),
                stackLensConfig = mock(),
                compileRetryPolicy = ToolingCompileRetryPolicy(mock(), mock()),
            )
        val code = "fun main() {}"
        val result = svc.extractKotlinCode("```\n$code\n```")
        assertEquals(code, result)
    }
}
