
package com.stacklens.application.port.out

import com.stacklens.domain.enums.FileChangeType

data class JiraMrFileChange(
    val filePath: String,
    val diffExcerpt: String,
)

data class JiraRelatedMr(
    val projectPath: String,
    val mrIid: Long,
    val title: String,
    val description: String,
    val state: String,
    val url: String,
    val foundViaTicketKey: String?,
    val foundViaKeyword: String?,
    val fileChanges: List<JiraMrFileChange>,
)

data class JiraRepoFile(
    val path: String,
    val content: String,
)

data class JiraRepoContext(
    val repoName: String,
    val projectId: Long,
    val files: List<JiraRepoFile>,
)

data class JiraCodeFile(
    val repository: String,
    val path: String,
    val language: String,
    val description: String,
    val changeType: FileChangeType,
    val code: String,
    val patches: List<JiraCodePatch> = emptyList(),
)

data class JiraCodePatch(
    val search: String,
    val replace: String,
)

fun applyPatches(
    original: String,
    patches: List<JiraCodePatch>,
): String {
    var result = original
    for (patch in patches) {
        if (patch.search.isBlank()) continue
        val idx = result.indexOf(patch.search)
        if (idx >= 0) {
            result = result.substring(0, idx) + patch.replace + result.substring(idx + patch.search.length)
        }
    }
    return result
}

data class JiraDatabaseChange(
    val database: String,
    val description: String,
    val sql: String,
)

data class JiraCodeImplementation(
    val identifiedRepositories: List<String>,
    val identifiedDatabases: List<String>,
    val files: List<JiraCodeFile>,
    val testFiles: List<JiraCodeFile>,
    val systemTestFiles: List<JiraCodeFile> = emptyList(),
    val databaseChanges: List<JiraDatabaseChange>,
    val implementationNotes: String,
)

data class JiraMonorepoIdentification(
    val identifiedApplication: String,
    val relativeRootPath: String,
    val reasoning: String,
    val confidence: String,
    val isMonorepo: Boolean,
    val techStack: List<String>,
    val codingConventions: String,
    val testFramework: String,
    val buildTool: String,
    val testCommand: String,
    val architectureStyle: String = "",
    val diApproach: String = "",
    val hasUnitTests: Boolean = false,
    val hasIntegrationTests: Boolean = false,
    val hasSystemTests: Boolean = false,
    val testStructureNotes: String = "",
    val projectPurpose: String = "",
    val keyModulesDescription: String = "",
    val designPatterns: String = "",
    val dataModelSummary: String = "",
    val exampleTestPatterns: String = "",
)
