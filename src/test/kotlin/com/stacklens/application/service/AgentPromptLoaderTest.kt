package com.stacklens.application.service
import com.stacklens.adapter.out.filesystem.ClasspathResourceAdapter
import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class AgentPromptLoaderTest {
    @TempDir
    lateinit var tmp: Path

    private val fileSystem = JavaFileSystemAdapter()

    @Test
    fun `loads bundled prompt from classpath when no override exists`() {
        val out =
            AgentPromptLoader.load(
                dataDir = tmp.toString(),
                agent = "lena",
                vars = emptyMap(),
                classpathResource = ClasspathResourceAdapter(),
                fileAttributes = fileSystem,
                fileContentStore = fileSystem,
            )

        assertTrue(out.isNotBlank(), "lena prompt must not be empty")
        assertFalse(out.contains("{codingGuidelines}"))
        assertFalse(out.contains("{frontendCodingGuidelines}"))
    }

    @Test
    fun `auto-injects coding guidelines when placeholder is present`() {
        val out =
            AgentPromptLoader.load(
                dataDir = tmp.toString(),
                agent = "rex",
                vars = emptyMap(),
                classpathResource = ClasspathResourceAdapter(),
                fileAttributes = fileSystem,
                fileContentStore = fileSystem,
            )

        assertFalse(out.contains("{codingGuidelines}"))
        assertTrue(out.isNotBlank())
    }

    @Test
    fun `prefers custom override and substitutes both guidelines plus vars`() {
        val custom = File(tmp.toFile(), "custom-agents")
        custom.mkdirs()
        File(custom, "lena.md").writeText(
            """
            Hello {name}!
            backend rules:
            {codingGuidelines}
            frontend rules:
            {frontendCodingGuidelines}
            """.trimIndent(),
        )

        val out =
            AgentPromptLoader.load(
                dataDir = tmp.toString(),
                agent = "lena",
                vars = mapOf("name" to "World"),
                classpathResource = ClasspathResourceAdapter(),
                fileAttributes = fileSystem,
                fileContentStore = fileSystem,
            )

        assertTrue(out.contains("Hello World!"))
        assertFalse(out.contains("{codingGuidelines}"))
        assertFalse(out.contains("{frontendCodingGuidelines}"))
        assertFalse(out.contains("{name}"))
    }

    @Test
    fun `override without placeholders returns trimmed content unchanged`() {
        val custom = File(tmp.toFile(), "custom-agents")
        custom.mkdirs()
        val raw = "  plain text\n  with leading and trailing whitespace  \n\n"
        File(custom, "plain.md").writeText(raw)

        val out =
            AgentPromptLoader.load(
                dataDir = tmp.toString(),
                agent = "plain",
                vars = emptyMap(),
                classpathResource = ClasspathResourceAdapter(),
                fileAttributes = fileSystem,
                fileContentStore = fileSystem,
            )

        assertEquals(raw.trim(), out)
    }
}
