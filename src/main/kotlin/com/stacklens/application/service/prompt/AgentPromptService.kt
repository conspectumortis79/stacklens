package com.stacklens.application.service.prompt

import com.stacklens.application.port.inbound.prompt.AgentPromptContent
import com.stacklens.application.port.inbound.prompt.AgentPromptSaveOutcome
import com.stacklens.application.port.inbound.prompt.AgentPromptSummary
import com.stacklens.application.port.inbound.prompt.AgentPromptUseCase
import com.stacklens.application.port.out.appconfig.StackLensConfigPort
import com.stacklens.application.port.out.classpath.ClasspathResourcePort
import com.stacklens.application.port.out.file.FileAttributes
import com.stacklens.application.port.out.file.FileWriteDeletePort
import com.stacklens.application.stereotype.ApplicationService
import java.io.File

@ApplicationService
class AgentPromptService(
    private val filePort: FileWriteDeletePort,
    private val classpathResource: ClasspathResourcePort,
    private val configPort: StackLensConfigPort,
    private val fileAttributes: FileAttributes,
) : AgentPromptUseCase {
    override fun listAgents(): List<AgentPromptSummary> = AgentCatalog.names.map { (name, label) -> AgentPromptSummary(name, label) }

    fun isKnownAgent(name: String): Boolean = AgentCatalog.names.containsKey(name)

    override fun getAgent(name: String): AgentPromptContent? {
        if (!isKnownAgent(name)) return null
        val defaultContent = defaultPrompt(name)
        val customContent = readCustom(name)
        val isCustom = customContent != null && customContent.trim() != defaultContent.trim()
        return AgentPromptContent(
            name = name,
            content = if (isCustom) customContent else defaultContent,
            isCustom = isCustom,
            defaultContent = defaultContent,
        )
    }

    override fun saveAgent(
        name: String,
        content: String?,
    ): AgentPromptSaveOutcome {
        if (!isKnownAgent(name)) return AgentPromptSaveOutcome.UNKNOWN_AGENT
        if (content == null) return AgentPromptSaveOutcome.BLANK_CONTENT
        if (content.isBlank()) return AgentPromptSaveOutcome.BLANK_CONTENT
        filePort.writeFile(customFile(name), content)
        return AgentPromptSaveOutcome.SAVED
    }

    override fun resetAgent(name: String): AgentPromptContent? {
        if (!isKnownAgent(name)) return null
        val defaultContent = defaultPrompt(name)
        filePort.deleteFile(customFile(name))
        return AgentPromptContent(
            name = name,
            content = defaultContent,
            isCustom = false,
            defaultContent = defaultContent,
        )
    }

    internal fun defaultPrompt(name: String): String {
        val path = "${AgentCatalog.AGENT_RESOURCE_DIR}$name.md"
        return if (classpathResource.exists(path)) classpathResource.readText(path) else ""
    }

    private fun readCustom(name: String): String? {
        val file = customFile(name)
        return if (fileAttributes.exists(file)) filePort.readText(file) else null
    }

    private fun customFile(name: String): File = File("$dataDir/${AgentCatalog.CUSTOM_AGENT_DIR}/$name.md")

    private val dataDir: String get() = configPort.dataDir.removeSuffix("/")

    private object AgentCatalog {
        const val CUSTOM_AGENT_DIR: String = "custom-agents"
        const val AGENT_RESOURCE_DIR: String = "agents/pipeline/"

        val names: Map<String, String> =
            linkedMapOf(
                "lena" to "Lena (Jira)",
                "nora" to "Nora (Analyse)",
                "cole" to "Cole (GitLab)",
                "max" to "Max (Planung)",
                "finn" to "Finn (Code)",
                "faye" to "Faye (Frontend)",
                "vera" to "Vera (Doku)",
                "tess" to "Tess (Tests)",
                "syd" to "Syd (System Tests)",
                "quinn" to "Quinn (Review)",
                "sage" to "Sage (Security)",
                "rex" to "Rex (QA)",
            )
    }
}
