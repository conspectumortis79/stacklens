package com.stacklens.adapter.out.compiler

import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import java.io.File
import java.nio.file.Path

class ToolingKotlinCompilerServiceProbeTest {
    private val copilotCliService: CopilotPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val stackLensConfig: StackLensConfigPort = mock()
    private val i18n: I18nPort = mock()

    @Test
    fun `findJavaExecutable uses the java binary of the configured jdk`(
        @TempDir tmp: Path,
    ) {
        val javaBin =
            File(tmp.toFile(), "bin/java").apply {
                parentFile.mkdirs()
                writeText("#!/bin/sh\n")
                setExecutable(true)
            }

        assertEquals(javaBin.absolutePath, service(javaHome = tmp.toFile().absolutePath).findJavaExecutable())
    }

    @Test
    fun `findJavaExecutable falls back to the lookup name when the jdk binary is not executable`(
        @TempDir tmp: Path,
    ) {
        File(tmp.toFile(), "bin/java").apply {
            parentFile.mkdirs()
            writeText("not executable")
            setExecutable(false)
        }

        assertEquals("java", service(javaHome = tmp.toFile().absolutePath).findJavaExecutable())
    }

    @Test
    fun `findJavaExecutable falls back to the lookup name when the jdk has no java binary`(
        @TempDir tmp: Path,
    ) {
        assertEquals("java", service(javaHome = tmp.toFile().absolutePath).findJavaExecutable())
    }

    @Test
    fun `findJavaExecutable falls back to the lookup name when the java home is blank`() {
        assertEquals("java", service(javaHome = "   ").findJavaExecutable())
    }

    @Test
    fun `findJavaExecutable falls back to the lookup name when the java home is missing`() {
        assertEquals("java", service(javaHome = null).findJavaExecutable())
    }

    @Test
    fun `extractKotlinCode returns the content of an inline fenced block`() {
        assertEquals("fun main() {}", service().extractKotlinCode("text ```fun main() {}``` more"))
    }

    @Test
    fun `extractKotlinCode ignores an empty inline fenced block`() {
        val failure =
            assertThrows(IllegalStateException::class.java) {
                service().extractKotlinCode("text `````` more")
            }

        assertEquals("no code block", failure.message)
    }

    @Test
    fun `extractKotlinCode returns the content of a generic fenced block`() {
        assertEquals("val x = 1", service().extractKotlinCode("```groovy\nval x = 1\n```"))
    }

    private fun service(javaHome: String? = null): ToolingKotlinCompilerService {
        org.mockito.kotlin
            .whenever(i18n.t(org.mockito.kotlin.eq("tooling.be_no_kotlin_codeblock"), org.mockito.kotlin.any()))
            .thenReturn("no code block")
        return ToolingKotlinCompilerService(
            copilotCliService = copilotCliService,
            promptLoader = promptLoader,
            i18n = i18n,
            stackLensConfig = stackLensConfig,
            compileRetryPolicy = ToolingCompileRetryPolicy(copilotCliService, promptLoader),
            javaHomeProvider = { javaHome },
        )
    }
}
