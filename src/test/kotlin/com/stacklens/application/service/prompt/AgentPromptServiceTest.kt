package com.stacklens.application.service.prompt

import com.stacklens.adapter.out.filesystem.JavaFileSystemAdapter
import com.stacklens.application.port.inbound.prompt.AgentPromptSaveOutcome
import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.classpath.ClasspathEntry
import com.stacklens.application.port.out.classpath.ClasspathResourcePort
import com.stacklens.testsupport.FakeStackLensConfigPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class AgentPromptServiceTest {
    @TempDir
    private lateinit var tempDir: Path

    private val fileSystem = JavaFileSystemAdapter()

    private val defaultPrompts =
        object : ClasspathResourcePort {
            override fun listEntries(pattern: String): List<ClasspathEntry> = emptyList()

            override fun readText(path: String): String = "default prompt for ${agentOf(path)}"

            override fun exists(path: String): Boolean = !path.contains("missing")

            private fun agentOf(path: String): String = path.substringAfterLast('/').removeSuffix(".md")
        }

    private val noPrompts =
        object : ClasspathResourcePort {
            override fun listEntries(pattern: String): List<ClasspathEntry> = emptyList()

            override fun readText(path: String): String = error("missing $path")

            override fun exists(path: String): Boolean = false
        }

    private fun service(
        classpath: ClasspathResourcePort = defaultPrompts,
        dataDir: String = tempDir.toString(),
    ) = AgentPromptService(fileSystem, classpath, configPort(dataDir), fileSystem)

    private fun configPort(dataDir: String): StackLensConfigPort = FakeStackLensConfigPort(dataDir = dataDir)

    @Test
    fun `the twelve pipeline agents are exposed with their labels`() {
        val agents = service().listAgents()

        assertEquals(12, agents.size)
        assertEquals("Lena (Jira)", agents.first { it.name == "lena" }.label)
        assertEquals("Nora (Analyse)", agents.first { it.name == "nora" }.label)
        assertTrue(agents.any { it.name == "rex" })
    }

    @Test
    fun `an unknown agent name is rejected for every operation`() {
        val service = service()

        assertFalse(service.isKnownAgent("nobody"))
        assertNull(service.getAgent("nobody"))
        assertEquals(AgentPromptSaveOutcome.UNKNOWN_AGENT, service.saveAgent("nobody", "content"))
        assertNull(service.resetAgent("nobody"))
    }

    @Test
    fun `without a custom file the default prompt is served and not flagged as custom`() {
        val agent = requireNotNull(service().getAgent("nora"))

        assertEquals("default prompt for nora", agent.content)
        assertEquals(agent.content, agent.defaultContent)
        assertFalse(agent.isCustom)
    }

    @Test
    fun `a saved prompt becomes the custom content and lands in the data directory`() {
        val service = service()

        assertEquals(AgentPromptSaveOutcome.SAVED, service.saveAgent("nora", "  custom text  "))

        val agent = requireNotNull(service.getAgent("nora"))
        assertEquals("  custom text  ", agent.content)
        assertTrue(agent.isCustom)
        assertEquals("  custom text  ", customFile("nora").readText())
    }

    @Test
    fun `a prompt that only differs in whitespace is not custom`() {
        val service = service()
        service.saveAgent("nora", "  default prompt for nora  ")

        val agent = requireNotNull(service.getAgent("nora"))

        assertFalse(agent.isCustom)
        assertEquals("default prompt for nora", agent.content)
    }

    @Test
    fun `a blank prompt is refused without touching the file system`() {
        val service = service()

        assertEquals(AgentPromptSaveOutcome.BLANK_CONTENT, service.saveAgent("nora", "   "))
        assertEquals(AgentPromptSaveOutcome.BLANK_CONTENT, service.saveAgent("nora", null))
        assertFalse(customFile("nora").exists())
    }

    @Test
    fun `resetting deletes the custom file and serves the default again`() {
        val service = service()
        service.saveAgent("nora", "custom")

        val reset = requireNotNull(service.resetAgent("nora"))

        assertFalse(customFile("nora").exists())
        assertFalse(reset.isCustom)
        assertEquals("default prompt for nora", reset.content)
    }

    @Test
    fun `a missing classpath default yields an empty default prompt`() {
        val agent = requireNotNull(service(classpath = noPrompts).getAgent("lena"))

        assertEquals("", agent.defaultContent)
        assertEquals("", agent.content)
    }

    @Test
    fun `a trailing slash in the configured data directory is tolerated`() {
        val service = service(dataDir = "$tempDir/")

        service.saveAgent("finn", "text")

        assertTrue(customFile("finn").exists())
    }

    private fun customFile(name: String): File = File(tempDir.toFile(), "custom-agents/$name.md")
}
