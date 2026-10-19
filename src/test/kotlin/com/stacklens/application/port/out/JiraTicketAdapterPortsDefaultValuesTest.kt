package com.stacklens.application.port.out

import com.stacklens.application.port.out.repo.GitlabProjectInfo
import com.stacklens.domain.model.GitlabConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JiraTicketAdapterPortsDefaultValuesTest {
    private val stub =
        object : GitlabTicketBridgePort {
            override fun fetchRelatedMrs(
                gitlabConfigs: List<GitlabConfig>,
                similarTickets: List<JiraSimilarTicket>,
                problem: JiraProblemAnalysis,
                originalTicket: JiraTicketData,
                selectedProjects: Map<Long, Set<Long>>,
            ): List<JiraRelatedMr> = emptyList()

            override fun findProjectById(
                config: GitlabConfig,
                gitlabProjectId: Long,
            ): GitlabProjectInfo? = null

            override fun fetchRepoContextForCodeGen(
                gitlabConfigs: List<GitlabConfig>,
                ticket: JiraTicketData,
                problem: JiraProblemAnalysis,
                relatedMrs: List<JiraRelatedMr>,
                selectedProjects: Map<Long, Set<Long>>,
            ): List<JiraRepoContext> = emptyList()

            override fun createMergeRequest(
                config: GitlabConfig,
                repoName: String,
                ticket: JiraTicketData,
                codeImpl: JiraCodeImplementation,
                gitlabProjectId: Long?,
                monorepoInfo: JiraMonorepoIdentification?,
            ): String = "https://example.com/mr"

            override fun commitDirectly(
                config: GitlabConfig,
                repoName: String,
                ticket: JiraTicketData,
                codeImpl: JiraCodeImplementation,
                gitlabProjectId: Long?,
                monorepoInfo: JiraMonorepoIdentification?,
            ): String = "https://example.com/commit"

            override fun detectGitlabRemote(
                localPath: java.nio.file.Path,
                gitlabConfigs: List<GitlabConfig>,
            ): Pair<GitlabConfig, Long?>? = null

            override fun getGitRemotePathWithNamespace(localPath: java.nio.file.Path): String? = null
        }

    @Test
    fun `fetchRelatedMrs default value for selectedProjects`() {
        val r =
            stub.fetchRelatedMrs(
                gitlabConfigs = emptyList(),
                similarTickets = emptyList(),
                problem = sampleProblem(),
                originalTicket = sampleTicket(),
            )
        assertEquals(0, r.size)
    }

    @Test
    fun `fetchRelatedMrs explicit selectedProjects`() {
        val r =
            stub.fetchRelatedMrs(
                gitlabConfigs = emptyList(),
                similarTickets = emptyList(),
                problem = sampleProblem(),
                originalTicket = sampleTicket(),
                selectedProjects = mapOf(1L to setOf(2L)),
            )
        assertEquals(0, r.size)
    }

    @Test
    fun `fetchRepoContextForCodeGen default value for selectedProjects`() {
        val r =
            stub.fetchRepoContextForCodeGen(
                gitlabConfigs = emptyList(),
                ticket = sampleTicket(),
                problem = sampleProblem(),
                relatedMrs = emptyList(),
            )
        assertEquals(0, r.size)
    }

    @Test
    fun `createMergeRequest default value for gitlabProjectId and monorepoInfo`() {
        val r =
            stub.createMergeRequest(
                config = sampleConfig(),
                repoName = "repo",
                ticket = sampleTicket(),
                codeImpl = sampleCodeImpl(),
            )
        assertEquals("https://example.com/mr", r)
    }

    @Test
    fun `createMergeRequest explicit gitlabProjectId and monorepoInfo`() {
        val r =
            stub.createMergeRequest(
                config = sampleConfig(),
                repoName = "repo",
                ticket = sampleTicket(),
                codeImpl = sampleCodeImpl(),
                gitlabProjectId = 7L,
                monorepoInfo = sampleMonorepo(),
            )
        assertEquals("https://example.com/mr", r)
    }

    @Test
    fun `commitDirectly default value for gitlabProjectId and monorepoInfo`() {
        val r =
            stub.commitDirectly(
                config = sampleConfig(),
                repoName = "repo",
                ticket = sampleTicket(),
                codeImpl = sampleCodeImpl(),
            )
        assertEquals("https://example.com/commit", r)
    }

    @Test
    fun `commitDirectly explicit gitlabProjectId and monorepoInfo`() {
        val r =
            stub.commitDirectly(
                config = sampleConfig(),
                repoName = "repo",
                ticket = sampleTicket(),
                codeImpl = sampleCodeImpl(),
                gitlabProjectId = 7L,
                monorepoInfo = sampleMonorepo(),
            )
        assertEquals("https://example.com/commit", r)
    }

    @Test
    fun `findProjectById and detectGitlabRemote and getGitRemotePathWithNamespace are invokable`() {
        stub.findProjectById(sampleConfig(), 1L)
        stub.detectGitlabRemote(
            java.nio.file.Path
                .of("/tmp"),
            emptyList(),
        )
        stub.getGitRemotePathWithNamespace(
            java.nio.file.Path
                .of("/tmp"),
        )
    }

    private fun sampleConfig() = GitlabConfig(name = "g", baseUrl = "https://gl", accessToken = "t")

    private fun sampleMonorepo() =
        JiraMonorepoIdentification(
            identifiedApplication = "app",
            relativeRootPath = "",
            reasoning = "",
            confidence = "LOW",
            isMonorepo = false,
            techStack = emptyList(),
            codingConventions = "",
            testFramework = "",
            buildTool = "",
            testCommand = "",
        )

    private fun sampleCodeImpl() =
        JiraCodeImplementation(
            identifiedRepositories = emptyList(),
            identifiedDatabases = emptyList(),
            files = emptyList(),
            testFiles = emptyList(),
            databaseChanges = emptyList(),
            implementationNotes = "",
        )

    private fun sampleTicket() =
        JiraTicketData(
            key = "TICK-1",
            summary = "s",
            description = "d",
            comments = "",
            issueType = "Task",
            status = "Open",
            priority = "Medium",
            labels = emptyList(),
            components = emptyList(),
            url = "https://example.com",
        )

    private fun sampleProblem() =
        JiraProblemAnalysis(
            problemDescription = "p",
            rootCause = "r",
            affectedComponents = emptyList(),
            searchKeywords = emptyList(),
            errorMessages = emptyList(),
        )
}
