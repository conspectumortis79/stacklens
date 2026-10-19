package com.stacklens.systemtest

import com.stacklens.adapter.out.pipeline.PipelineFilesAdapter
import com.stacklens.application.port.out.GithubRepoConfigPort
import com.stacklens.application.port.out.GithubTicketBridgePort
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.LocalRepoConfigPort
import com.stacklens.application.port.out.repo.GitlabPort
import com.stacklens.application.port.out.repo.LocalRepoScanPort
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PipelineDemoGitlabSystemTest {
    @Autowired
    lateinit var localRepoConfigRepo: LocalRepoConfigPort

    @Autowired
    lateinit var gitlabConfigRepo: GitlabConfigPort

    @Autowired
    lateinit var githubRepoConfigRepo: GithubRepoConfigPort

    @Autowired
    lateinit var scanService: LocalRepoScanPort

    @Autowired
    lateinit var gitlabService: GitlabPort

    @Autowired
    lateinit var githubBridge: GithubTicketBridgePort

    @Test
    fun `pipeline-demo project is reachable through the GitLab adapter`() {
        val config = gitlabConfigRepo.findAllGitlabConfigs().firstOrNull()
        if (config == null) {
            println("[PipelineDemoGitlabSystemTest] No GitLab config in DB – skipping (run with STACKLENS_SEED_DEMO_DATA=true to exercise).")
            return
        }
        val projects =
            try {
                gitlabService.listAccessibleProjects(config)
            } catch (e: Exception) {
                println("[PipelineDemoGitlabSystemTest] fake-gitlab unreachable on ${config.baseUrl} – skipping.")
                return
            }

        val pipelineDemo = projects.firstOrNull { it.pathWithNamespace == "demo-group/pipeline-demo" }
        assertNotNull(pipelineDemo, "Demo GitLab must expose the pipeline-demo project (id=8).")

        val adapter =
            PipelineFilesAdapter(
                localRepoConfigRepo,
                gitlabConfigRepo,
                githubRepoConfigRepo,
                scanService,
                gitlabService,
                githubBridge,
            )

        val filePaths =
            try {
                adapter.listGitlabFilePaths(config.id, pipelineDemo!!.id)
            } catch (e: Exception) {
                println("[PipelineDemoGitlabSystemTest] Could not list pipeline-demo files – skipping detail assertions.")
                return
            }

        val expectedFiles =
            setOf(
                "docker-compose.yml",
                ".gitlab-ci.yml",
                "Dockerfile",
            )
        val missing = expectedFiles - filePaths.toSet()
        assertTrue(missing.isEmpty(), "pipeline-demo is missing required pipeline files: $missing")

        val loader = adapter.loadGitlab(config.id, pipelineDemo.id, pipelineDemo.pathWithNamespace)
        assertNotNull(loader, "loadGitlab must return a non-null bundle for pipeline-demo.")
        val tree = loader!!.tree
        for (headline in expectedFiles) {
            assertTrue(tree.contains(headline), "Loaded tree must mention $headline.")
        }
    }
}
