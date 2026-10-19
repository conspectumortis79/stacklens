package com.stacklens.application.service

import com.stacklens.application.port.out.MrFetchPort
import com.stacklens.application.port.out.ai.CopilotPort
import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.application.port.out.prompt.PromptReaderPort

class CodeQaPromptAssembler(
    private val i18n: I18nPort,
    private val copilotPort: CopilotPort,
    private val promptLoader: PromptReaderPort,
) {
    fun buildPrompt(
        mrData: MrFetchPort.MrData,
        jiraTicketText: String?,
        additionalInstructions: String? = null,
    ): String {
        val hasJira = !jiraTicketText.isNullOrBlank()
        val lang = copilotPort.resolveLanguage()

        val part1Header =
            if (hasJira) {
                i18n.tFor(lang, "mr_analysis.prompt_part1_jira_header")
            } else {
                i18n.tFor(lang, "mr_analysis.prompt_part1_desc_header")
            }
        val part1Task =
            if (hasJira) {
                i18n.tFor(lang, "mr_analysis.prompt_part1_jira_task")
            } else {
                i18n.tFor(lang, "mr_analysis.prompt_part1_desc_task")
            }

        val changedFilesSb = StringBuilder()
        if (mrData.changedFiles.isNotEmpty()) {
            changedFilesSb.appendLine(i18n.tFor(lang, "mr_analysis.be_files_count", mrData.changedFiles.size))
            mrData.changedFiles.take(50).forEach { changedFilesSb.appendLine("- $it") }
            if (mrData.changedFiles.size > 50) {
                changedFilesSb.append(i18n.tFor(lang, "mr_analysis.be_files_more", mrData.changedFiles.size - 50))
            }
        } else {
            changedFilesSb.append(i18n.tFor(lang, "mr_analysis.be_no_file_info"))
        }

        val jiraSection =
            if (hasJira) {
                "---\n\n${i18n.tFor(lang, "mr_analysis.prompt_jira_section_header")}\n\n${jiraTicketText.take(10_000)}\n"
            } else {
                ""
            }

        val additionalInstructionsSection =
            if (!additionalInstructions.isNullOrBlank()) {
                "---\n\n## Additional Instructions\n\n${additionalInstructions.take(2000)}\n"
            } else {
                ""
            }

        return promptLoader.get(
            lang,
            "mranalysis.analyzePrompt",
            mapOf(
                "mrUrl" to mrData.mrUrl,
                "platform" to mrData.platform,
                "title" to mrData.title,
                "description" to mrData.description.take(1000).ifBlank { i18n.tFor(lang, "mr_analysis.be_no_description") },
                "sourceBranch" to mrData.sourceBranch,
                "targetBranch" to mrData.targetBranch,
                "authorName" to mrData.authorName,
                "state" to mrData.state,
                "changedFilesList" to changedFilesSb.toString(),
                "diffText" to mrData.diffText,
                "jiraSection" to jiraSection,
                "part1Header" to part1Header,
                "part1Task" to part1Task,
                "langInstruction" to i18n.tFor(lang, "mr_analysis.prompt_lang_instruction"),
                "additionalInstructionsSection" to additionalInstructionsSection,
            ),
        )
    }
}
