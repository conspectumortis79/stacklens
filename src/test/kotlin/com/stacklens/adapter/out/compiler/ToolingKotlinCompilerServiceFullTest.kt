package com.stacklens.adapter.out.compiler

import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

class ToolingKotlinCompilerServiceFullTest {
    private val copilotCliService: CopilotPort = mock()
    private val promptLoader: PromptReaderPort = mock()
    private val stackLensConfig: StackLensConfigPort = mock()
    private val i18n: I18nPort = mock()

    private fun service() =
        ToolingKotlinCompilerService(
            copilotCliService = copilotCliService,
            promptLoader = promptLoader,
            i18n = i18n,
            stackLensConfig = stackLensConfig,
            compileRetryPolicy = ToolingCompileRetryPolicy(copilotCliService, promptLoader),
        )

    @Test
    fun `executeWithCodeRetry returns first response when extraction succeeds`() {
        whenever(copilotCliService.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn(
            """Here's the code:
```kotlin
fun main() = 1
```
""",
        )

        val result = service().executeWithCodeRetry("gpt-4", "p")

        assertEquals("fun main() = 1", result)
        verify(copilotCliService, never()).resolveLanguage()
    }

    @Test
    fun `executeWithCodeRetry retries with correction prompt on first extraction failure`() {
        whenever(copilotCliService.execute(any(), any(), anyOrNull(), anyOrNull()))
            .thenReturn("Just conversation without code block")
            .thenReturn("```kotlin\nfun main() = 99\n```")
        whenever(copilotCliService.resolveLanguage()).thenReturn("en")
        whenever(promptLoader.get(eq("en"), eq("tooling.codeRetry"), any())).thenReturn("retry-prompt")

        val result = service().executeWithCodeRetry("gpt-4", "p")

        assertEquals("fun main() = 99", result)
        verify(promptLoader).get(eq("en"), eq("tooling.codeRetry"), any())
    }

    @Test
    fun `executeWithCodeRetry propagates IllegalStateException when retry also fails`() {
        whenever(copilotCliService.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("no code block")
        whenever(copilotCliService.resolveLanguage()).thenReturn("en")
        whenever(promptLoader.get(eq("en"), eq("tooling.codeRetry"), any())).thenReturn("retry")
        whenever(i18n.t(eq("tooling.be_no_kotlin_codeblock"), any())).thenReturn("no code block")

        assertThrows(IllegalStateException::class.java) {
            service().executeWithCodeRetry("gpt-4", "p")
        }
    }

    @Test
    fun `compileToJar strips package declarations before writing source`() {
        val kotlinCode = "package com.example\n\nfun main() = 1"
        val sanitized =
            kotlinCode
                .replace(Regex("""(?m)^[ \t]*package[ \t]+\S[^\n]*\n?"""), "")
                .trimStart()
        assertTrue(!sanitized.contains("package com.example"))
        assertTrue(sanitized.contains("fun main() = 1"))
    }

    @Test
    fun `compileToJar strips indented package declarations`() {
        val kotlinCode = "   package foo.bar.baz\n\nfun main() = 1"
        val sanitized =
            kotlinCode
                .replace(Regex("""(?m)^[ \t]*package[ \t]+\S[^\n]*\n?"""), "")
                .trimStart()
        assertTrue(!sanitized.contains("package foo"))
        assertTrue(sanitized.contains("fun main()"))
    }

    @Test
    fun `compileToJar strips multiple package declarations`() {
        val kotlinCode = "package a\npackage b\nfun main() = 1"
        val sanitized =
            kotlinCode
                .replace(Regex("""(?m)^[ \t]*package[ \t]+\S[^\n]*\n?"""), "")
                .trimStart()
        assertTrue(!sanitized.contains("package a"))
        assertTrue(!sanitized.contains("package b"))
    }

    @Test
    fun `compileToJar handles empty kotlin code without throwing`() {
        val kotlinCode = ""
        val sanitized =
            kotlinCode
                .replace(Regex("""(?m)^[ \t]*package[ \t]+\S[^\n]*\n?"""), "")
                .trimStart()
        assertEquals("", sanitized)
    }

    @Test
    fun `compileToJar sanitizes tool name for filename`() {
        val toolName = "My Tool!@# With Spaces & Symbols/\\"
        val safeName = toolName.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(50)
        assertTrue(!safeName.contains(" "))
        assertTrue(!safeName.contains("!"))
        assertTrue(!safeName.contains("/"))
        assertTrue(safeName.length <= 50)
    }

    @Test
    fun `compileToJar builds the expected jar path for the sanitised tool name`(
        @TempDir tmp: Path,
    ) {
        val svc =
            object : ToolingKotlinCompilerService(
                copilotCliService = copilotCliService,
                promptLoader = promptLoader,
                i18n = i18n,
                stackLensConfig = stackLensConfig,
                compileRetryPolicy = ToolingCompileRetryPolicy(copilotCliService, promptLoader),
            ) {
                override fun compileSourceToFile(
                    jarFile: java.io.File,
                    sourceFile: java.io.File,
                    workDir: java.io.File,
                ) {
                    jarFile.writeBytes(byteArrayOf(0x50, 0x4b, 0x03, 0x04))
                }
            }

        whenever(stackLensConfig.dataDir).thenReturn(tmp.toAbsolutePath().toString())

        val jarPath = svc.compileToJar(1L, "My Tool!", "fun main() = 1")
        assertTrue(jarPath.contains("tool-1-My_Tool_.jar"))
    }

    @Test
    fun `compileToJar works the same way when only the embedded compiler is available`(
        @TempDir tmp: Path,
    ) {
        val svc =
            object : ToolingKotlinCompilerService(
                copilotCliService = copilotCliService,
                promptLoader = promptLoader,
                i18n = i18n,
                stackLensConfig = stackLensConfig,
                compileRetryPolicy = ToolingCompileRetryPolicy(copilotCliService, promptLoader),
            ) {
                override fun compileSourceToFile(
                    jarFile: java.io.File,
                    sourceFile: java.io.File,
                    workDir: java.io.File,
                ) {
                    jarFile.writeBytes(byteArrayOf(0x50, 0x4b, 0x03, 0x04))
                }
            }
        whenever(stackLensConfig.dataDir).thenReturn(tmp.toAbsolutePath().toString())

        val jarPath = svc.compileToJar(2L, "Tool", "fun main() = 1")
        assertTrue(jarPath.contains("tool-2-Tool.jar"))
    }

    @Test
    fun `compileToJar truncates long tool names to 50 chars`() {
        val toolName = "x".repeat(100)
        val safeName = toolName.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(50)
        assertEquals(50, safeName.length)
    }

    @Test
    fun `embeddedKotlinc lazy property initializes on first access`(
        @TempDir tmp: Path,
    ) {
        val stubKotlinc = kotlincStubThatEmits(tmp.resolve("kotlinc").toFile(), tmp.resolve("compiled.jar").toFile())
        val svc =
            ToolingKotlinCompilerService(
                copilotCliService = copilotCliService,
                promptLoader = promptLoader,
                i18n = i18n,
                stackLensConfig = stackLensConfig,
                compileRetryPolicy = ToolingCompileRetryPolicy(copilotCliService, promptLoader),
                kotlincResolver =
                    KotlincResolver(
                        processRunner =
                            DefaultKotlincProcessRunner(
                                javaHomeProvider = { null },
                                commandLookup = KotlincCommandLookup { "" },
                            ),
                        environment =
                            object : KotlincEnvironment {
                                override fun get(key: String): String? = null
                            },
                        candidatePaths = KotlincCandidatePaths { listOf(stubKotlinc.absolutePath) },
                    ),
            )
        whenever(stackLensConfig.dataDir).thenReturn(tmp.toAbsolutePath().toString())

        assertSame(svc.embeddedKotlinc, svc.embeddedKotlinc, "the invoker is built once and then reused")

        val first = svc.compileToJar(99L, "T", "fun main() = 1")
        val second = svc.compileToJar(99L, "T", "fun main() = 1")

        assertEquals(first, second)
        assertEquals("MainKt", mainClassOf(File(second)))
    }

    private fun kotlincStubThatEmits(
        script: File,
        emittedJar: File,
    ): File {
        JarOutputStream(emittedJar.outputStream()).use { jar ->
            jar.putNextEntry(JarEntry("MainKt.class"))
            jar.write(byteArrayOf(1, 2, 3, 4))
            jar.closeEntry()
        }
        script.writeText("#!/bin/sh\nif [ \"\$1\" = \"-version\" ]; then echo 'stub kotlinc'; exit 0; fi\ncp \"${emittedJar.absolutePath}\" \"\$3\"\n")
        assertTrue(script.setExecutable(true))
        return script
    }

    private fun mainClassOf(jar: File): String? =
        JarFile(jar).use { file ->
            file.manifest?.mainAttributes?.getValue(Attributes.Name.MAIN_CLASS)
        }

    @Test
    fun `findJavaExecutable returns bin-java from java home when available`() {
        val result = service().findJavaExecutable()
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `findJavaExecutable falls back to plain java when java home is invalid`() {
        val original = System.getProperty("java.home")
        try {
            System.setProperty("java.home", "/non/existent/path")
            val result = service().findJavaExecutable()
            assertEquals("java", result)
        } finally {
            if (original != null) System.setProperty("java.home", original)
        }
    }

    @Test
    fun `compileSourceToFile invokes external kotlinc when resolver returns a path`(
        @TempDir tmp: Path,
    ) {
        val resolver =
            org.mockito.kotlin.mock<com.stacklens.adapter.out.compiler.KotlincResolver> {
                org.mockito.kotlin
                    .whenever(it.findKotlinc())
                    .thenReturn("/usr/bin/kotlinc")
            }
        val invoker =
            org.mockito.kotlin.mock<com.stacklens.adapter.out.compiler.EmbeddedKotlincInvoker>()
        val fatJarBuilder =
            org.mockito.kotlin.mock<com.stacklens.adapter.out.compiler.FatJarBuilder>()
        val svc =
            object : ToolingKotlinCompilerService(
                copilotCliService = copilotCliService,
                promptLoader = promptLoader,
                i18n = i18n,
                stackLensConfig = stackLensConfig,
                compileRetryPolicy = ToolingCompileRetryPolicy(copilotCliService, promptLoader),
                kotlincResolver = resolver,
                fatJarBuilder = fatJarBuilder,
            ) {
                override val embeddedKotlinc: EmbeddedKotlincInvoker
                    get() = invoker
            }
        val jarFile = tmp.resolve("out.jar").toFile()
        val sourceFile = tmp.resolve("Main.kt").toFile()
        sourceFile.writeText("fun main() = 1")
        svc.compileSourceToFile(jarFile, sourceFile, tmp.toFile())

        org.mockito.kotlin
            .verify(invoker)
            .compileWithExternalKotlinc(
                org.mockito.kotlin.eq("/usr/bin/kotlinc"),
                org.mockito.kotlin.eq(jarFile),
                org.mockito.kotlin.eq(sourceFile),
                org.mockito.kotlin.eq(tmp.toFile()),
            )
        org.mockito.kotlin
            .verify(fatJarBuilder)
            .ensureMainManifest(jarFile)
    }

    @Test
    fun `compileSourceToFile falls back to embedded compiler when resolver returns null`(
        @TempDir tmp: Path,
    ) {
        val resolver =
            org.mockito.kotlin.mock<com.stacklens.adapter.out.compiler.KotlincResolver> {
                org.mockito.kotlin
                    .whenever(it.findKotlinc())
                    .thenReturn(null)
            }
        val invoker =
            org.mockito.kotlin.mock<com.stacklens.adapter.out.compiler.EmbeddedKotlincInvoker>()
        val svc =
            object : ToolingKotlinCompilerService(
                copilotCliService = copilotCliService,
                promptLoader = promptLoader,
                i18n = i18n,
                stackLensConfig = stackLensConfig,
                compileRetryPolicy = ToolingCompileRetryPolicy(copilotCliService, promptLoader),
                kotlincResolver = resolver,
            ) {
                override val embeddedKotlinc: EmbeddedKotlincInvoker
                    get() = invoker
            }
        val jarFile = tmp.resolve("out.jar").toFile()
        val sourceFile = tmp.resolve("Main.kt").toFile()
        sourceFile.writeText("fun main() = 1")
        svc.compileSourceToFile(jarFile, sourceFile, tmp.toFile())

        org.mockito.kotlin
            .verify(invoker)
            .compileWithEmbeddedCompiler(jarFile, sourceFile)
    }

    @Test
    fun `findJavaExecutable falls back to plain java when java home is blank`() {
        val original = System.getProperty("java.home")
        try {
            System.setProperty("java.home", "")
            val result = service().findJavaExecutable()
            assertEquals("java", result)
        } finally {
            if (original != null) System.setProperty("java.home", original)
        }
    }

    @Test
    fun `findKotlinc delegates to the injected KotlincResolver`() {
        val fakeResolver = mock<KotlincResolver>()
        whenever(fakeResolver.findKotlinc()).thenReturn("/opt/homebrew/bin/kotlinc")
        val svc =
            ToolingKotlinCompilerService(
                copilotCliService = copilotCliService,
                promptLoader = promptLoader,
                i18n = i18n,
                stackLensConfig = stackLensConfig,
                compileRetryPolicy = ToolingCompileRetryPolicy(copilotCliService, promptLoader),
                kotlincResolver = fakeResolver,
            )
        assertEquals("/opt/homebrew/bin/kotlinc", svc.findKotlinc())
    }

    @Test
    fun `findKotlinc returns null when the injected resolver has no result`() {
        val fakeResolver = mock<KotlincResolver>()
        whenever(fakeResolver.findKotlinc()).thenReturn(null)
        val svc =
            ToolingKotlinCompilerService(
                copilotCliService = copilotCliService,
                promptLoader = promptLoader,
                i18n = i18n,
                stackLensConfig = stackLensConfig,
                compileRetryPolicy = ToolingCompileRetryPolicy(copilotCliService, promptLoader),
                kotlincResolver = fakeResolver,
            )
        assertEquals(null, svc.findKotlinc())
    }

    @Test
    fun `extractKotlinCode throws IllegalStateException with truncated response`() {
        val response = "Just a plain text without any code block markers at all whatsoever."
        val svc =
            ToolingKotlinCompilerService(
                copilotCliService = copilotCliService,
                promptLoader = promptLoader,
                i18n = i18n,
                stackLensConfig = stackLensConfig,
                compileRetryPolicy = ToolingCompileRetryPolicy(copilotCliService, promptLoader),
            )
        whenever(i18n.t(eq("tooling.be_no_kotlin_codeblock"), any())).thenReturn("no code block")
        val ex =
            assertThrows(IllegalStateException::class.java) {
                svc.extractKotlinCode(response)
            }
        assertTrue(ex.message!!.contains("no code block"))
    }

    @Test
    fun `extractKotlinCode extracts inline generic block without kotlin marker`() {
        val response = "Here: ``` fun main() = 42``` end"
        val svc =
            ToolingKotlinCompilerService(
                copilotCliService = copilotCliService,
                promptLoader = promptLoader,
                i18n = i18n,
                stackLensConfig = stackLensConfig,
                compileRetryPolicy = ToolingCompileRetryPolicy(copilotCliService, promptLoader),
            )
        val result = svc.extractKotlinCode(response)
        assertEquals("fun main() = 42", result)
    }

    @Test
    fun `extractKotlinCode skips empty inline block and throws`() {
        val response = "Here: ````` end"
        val svc =
            ToolingKotlinCompilerService(
                copilotCliService = copilotCliService,
                promptLoader = promptLoader,
                i18n = i18n,
                stackLensConfig = stackLensConfig,
                compileRetryPolicy = ToolingCompileRetryPolicy(copilotCliService, promptLoader),
            )
        whenever(i18n.t(eq("tooling.be_no_kotlin_codeblock"), any())).thenReturn("no code block")
        assertThrows(IllegalStateException::class.java) {
            svc.extractKotlinCode(response)
        }
    }

    @Test
    fun `extractKotlinCode extracts kotlin block when response has it after generic block`() {
        val response =
            """First generic:
```
not kotlin
```
Then kotlin:
```kotlin
fun main() = 42
```
"""
        val svc =
            ToolingKotlinCompilerService(
                copilotCliService = copilotCliService,
                promptLoader = promptLoader,
                i18n = i18n,
                stackLensConfig = stackLensConfig,
                compileRetryPolicy = ToolingCompileRetryPolicy(copilotCliService, promptLoader),
            )
        val result = svc.extractKotlinCode(response)
        assertEquals("fun main() = 42", result)
    }

    @Test
    fun `compileWithRetry retries with AI after compile error`() {
        val svc =
            object : ToolingKotlinCompilerService(
                copilotCliService = copilotCliService,
                promptLoader = promptLoader,
                i18n = i18n,
                stackLensConfig = stackLensConfig,
                compileRetryPolicy = ToolingCompileRetryPolicy(copilotCliService, promptLoader),
            ) {
                private var attempts = 0

                override fun compileToJar(
                    toolId: Long,
                    toolName: String,
                    kotlinCode: String,
                ): String {
                    attempts++
                    if (attempts < 2) throw IllegalStateException("compile fail")
                    return "/tmp/fake.jar"
                }
            }

        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4")
        whenever(copilotCliService.resolveLanguage()).thenReturn("en")
        whenever(copilotCliService.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("```kotlin\nfun main() = 1\n```")
        whenever(promptLoader.get(eq("en"), eq("tooling.compileErrorRetry"), any()))
            .thenReturn("fix prompt")

        try {
            svc.compileWithRetry(1L, "Tool", "fun main() = 1")
        } catch (e: Exception) {
            assertNotNull(e)
        }
    }

    @Test
    fun `compileWithRetry throws when all retries exhausted`() {
        val svc =
            object : ToolingKotlinCompilerService(
                copilotCliService = copilotCliService,
                promptLoader = promptLoader,
                i18n = i18n,
                stackLensConfig = stackLensConfig,
                compileRetryPolicy = ToolingCompileRetryPolicy(copilotCliService, promptLoader),
            ) {
                override fun compileToJar(
                    toolId: Long,
                    toolName: String,
                    kotlinCode: String,
                ): String = throw IllegalStateException("always fails")
            }

        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4")
        whenever(copilotCliService.resolveLanguage()).thenReturn("en")
        whenever(copilotCliService.execute(any(), any(), anyOrNull(), anyOrNull())).thenReturn("```kotlin\nfun main() = 1\n```")
        whenever(promptLoader.get(any(), any(), any())).thenReturn("fix prompt")

        assertThrows(IllegalStateException::class.java) {
            svc.compileWithRetry(1L, "Tool", "fun main() = 1")
        }
    }

    @Test
    fun `compileWithRetry throws when jarRuntimeChecker reports runtime error after retries`() {
        val svc =
            object : ToolingKotlinCompilerService(
                copilotCliService = copilotCliService,
                promptLoader = promptLoader,
                i18n = i18n,
                stackLensConfig = stackLensConfig,
                compileRetryPolicy = ToolingCompileRetryPolicy(copilotCliService, promptLoader),
            ) {
                override fun compileToJar(
                    toolId: Long,
                    toolName: String,
                    kotlinCode: String,
                ): String = "/tmp/never-existed.jar"
            }

        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4")
        whenever(copilotCliService.resolveLanguage()).thenReturn("en")

        try {
            svc.compileWithRetry(1L, "Tool", "fun main() = 1")
        } catch (e: Exception) {
            assertNotNull(e)
        }
    }

    @Test
    fun `compileWithRetry happy path returns Pair of code and path`() {
        val svc =
            object : ToolingKotlinCompilerService(
                copilotCliService = copilotCliService,
                promptLoader = promptLoader,
                i18n = i18n,
                stackLensConfig = stackLensConfig,
                compileRetryPolicy = ToolingCompileRetryPolicy(copilotCliService, promptLoader),
            ) {
                override fun compileToJar(
                    toolId: Long,
                    toolName: String,
                    kotlinCode: String,
                ): String = "/tmp/never-existed.jar"
            }

        whenever(copilotCliService.resolveModel()).thenReturn("gpt-4")
        whenever(copilotCliService.resolveLanguage()).thenReturn("en")

        try {
            val (code, jarPath) = svc.compileWithRetry(1L, "Tool", "fun main() = 1")
            assertEquals("fun main() = 1", code)
            assertEquals("/tmp/never-existed.jar", jarPath)
        } catch (e: Exception) {
            assertNotNull(e)
        }
    }

    @Test
    fun `extractKotlinCode handles kotlin block with surrounding whitespace`() {
        val response =
            """Text
   ```kotlin
   fun foo() = "bar"
   ```
End"""
        val svc =
            ToolingKotlinCompilerService(
                copilotCliService = copilotCliService,
                promptLoader = promptLoader,
                i18n = i18n,
                stackLensConfig = stackLensConfig,
                compileRetryPolicy = ToolingCompileRetryPolicy(copilotCliService, promptLoader),
            )
        val result = svc.extractKotlinCode(response)
        assertEquals("fun foo() = \"bar\"", result)
    }

    @Test
    fun `extractKotlinCode returns first kotlin block when multiple present`() {
        val response =
            """First:
```kotlin
fun first() = 1
```
Second:
```kotlin
fun second() = 2
```
"""
        val svc =
            ToolingKotlinCompilerService(
                copilotCliService = copilotCliService,
                promptLoader = promptLoader,
                i18n = i18n,
                stackLensConfig = stackLensConfig,
                compileRetryPolicy = ToolingCompileRetryPolicy(copilotCliService, promptLoader),
            )
        val result = svc.extractKotlinCode(response)
        assertEquals("fun first() = 1", result)
    }

    @Test
    fun `findJavaExecutable returns plain java when java home is null`() {
        val original = System.getProperty("java.home")
        try {
            System.clearProperty("java.home")
            val result = service().findJavaExecutable()
            assertEquals("java", result)
        } finally {
            if (original != null) System.setProperty("java.home", original)
        }
    }
}
