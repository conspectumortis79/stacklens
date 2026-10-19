
package com.stacklens.application.service

import com.stacklens.application.port.out.classpath.ClasspathResourcePort
import com.stacklens.application.port.out.file.FileAttributes
import com.stacklens.application.port.out.file.FileContentStore
import java.io.File

internal object AgentPromptLoader {
    fun load(
        dataDir: String,
        agent: String,
        vars: Map<String, String>,
        classpathResource: ClasspathResourcePort,
        fileAttributes: FileAttributes,
        fileContentStore: FileContentStore,
    ): String {
        val customFile = File("$dataDir/custom-agents/$agent.md")
        var template =
            if (fileAttributes.exists(customFile)) {
                fileContentStore.readText(customFile)
            } else {
                classpathResource.readText("agents/pipeline/$agent.md")
            }
        if (template.contains("{codingGuidelines}")) {
            template =
                template.replace(
                    "{codingGuidelines}",
                    classpathResource.readText("agents/pipeline/skills/coding-guidelines.md").trim(),
                )
        }
        if (template.contains("{frontendCodingGuidelines}")) {
            template =
                template.replace(
                    "{frontendCodingGuidelines}",
                    classpathResource.readText("agents/pipeline/skills/frontend-coding-guidelines.md").trim(),
                )
        }
        vars.forEach { (key, value) -> template = template.replace("{$key}", value) }
        return template.trim()
    }
}
