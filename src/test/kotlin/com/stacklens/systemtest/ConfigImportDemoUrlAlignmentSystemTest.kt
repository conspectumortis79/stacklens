package com.stacklens.systemtest

import com.stacklens.adapter.out.demo.DemoRuntimeMode
import com.stacklens.application.port.out.GitlabConfigPort
import com.stacklens.application.port.out.transaction.TransactionPort
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.io.File

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ConfigImportDemoUrlAlignmentSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var gitlabConfigPort: GitlabConfigPort

    @Autowired
    lateinit var tx: TransactionPort

    private val tempFiles = mutableListOf<File>()

    @AfterEach
    fun removeImportFiles() {
        System.clearProperty(DOCKER_FLAG)
        tempFiles.forEach { it.delete() }
        tx.inTransaction { gitlabConfigPort.deleteAllGitlabConfigs() }
    }

    @Test
    fun `import in container mode persists host docker internal`() {
        System.setProperty(DOCKER_FLAG, "true")
        importDownloadedDemoConfig()

        val stored = gitlabConfigPort.findAllGitlabConfigs().first { it.name == DEMO_NAME }.baseUrl
        assertEquals("http://host.docker.internal:3080", stored)
    }

    @Test
    fun `import in native mode persists localhost`() {
        System.clearProperty(DOCKER_FLAG)
        importDownloadedDemoConfig()

        val stored = gitlabConfigPort.findAllGitlabConfigs().first { it.name == DEMO_NAME }.baseUrl
        assertEquals("http://localhost:3080", stored)
    }

    private fun importDownloadedDemoConfig() {
        val file = File.createTempFile("stacklens-demo-import", ".json")
        tempFiles += file
        file.writeText(
            """
            {"version":1,"gitlab":[{"name":"$DEMO_NAME","baseUrl":"http://localhost:3080","accessToken":"demo-token","groupId":"10","defaultBranch":"main","active":true}]}
            """.trimIndent(),
        )
        mockMvc
            .post("/api/config/import-from-path") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"path": "${file.absolutePath}", "skipIfNotEmpty": false}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.success") { value(true) }
                jsonPath("$.imported.gitlab") { value(1) }
            }
    }

    private companion object {
        const val DOCKER_FLAG = DemoRuntimeMode.DOCKER_FLAG_PROPERTY
        const val DEMO_NAME = "Demo GitLab"
    }
}
