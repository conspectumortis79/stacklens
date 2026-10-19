package com.stacklens.systemtest

import com.stacklens.application.port.out.ConfluenceToolPort
import com.stacklens.domain.AnalysisStatus
import com.stacklens.domain.model.ConfluenceTool
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class WebPageSystemTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var toolPort: ConfluenceToolPort

    private fun persistedTool(
        status: String,
        errorMessage: String?,
    ): ConfluenceTool =
        toolPort.saveConfluenceTool(
            ConfluenceTool(
                name = "banner-check-$status-${System.nanoTime()}",
                confluencePageUrl = "https://example.atlassian.net/wiki/x",
                status = status,
                generatedCode = "fun main() {}",
                errorMessage = errorMessage,
            ),
        )

    @Test
    fun `GET root redirects to search page`() {
        mockMvc
            .get("/")
            .andExpect { status { is3xxRedirection() } }
    }

    @Test
    fun `GET search page renders HTML`() {
        mockMvc
            .get("/search")
            .andExpect {
                status { isOk() }
                content { string(containsString("StackLens")) }
            }
    }

    @Test
    fun `GET config page renders HTML`() {
        mockMvc
            .get("/config")
            .andExpect {
                status { isOk() }
                content { string(containsString("StackLens")) }
            }
    }

    @Test
    fun `GET code-analysis page renders HTML`() {
        mockMvc
            .get("/code-analysis")
            .andExpect {
                status { isOk() }
                content { string(containsString("StackLens")) }
            }
    }

    @Test
    fun `GET ticket-implementation page renders HTML`() {
        mockMvc
            .get("/ticket-implementation")
            .andExpect {
                status { isOk() }
                content { string(containsString("StackLens")) }
            }
    }

    @Test
    fun `GET lasttest-analysis page renders HTML`() {
        mockMvc
            .get("/lasttest-analysis")
            .andExpect {
                status { isOk() }
                content { string(containsString("StackLens")) }
            }
    }

    @Test
    fun `GET sql-analysis page renders HTML`() {
        mockMvc
            .get("/sql-analysis")
            .andExpect {
                status { isOk() }
                content { string(containsString("StackLens")) }
            }
    }

    @Test
    fun `GET logging page renders HTML`() {
        mockMvc
            .get("/logging")
            .andExpect {
                status { isOk() }
                content { string(containsString("StackLens")) }
            }
    }

    @Test
    fun `GET logging page renders the history card markup`() {
        mockMvc
            .get("/logging")
            .andExpect {
                status { isOk() }
                content { string(containsString("id=\"hxCard-logging\"")) }
                content { string(containsString("data-hx-page=\"logging\"")) }
                content { string(containsString("id=\"lgRestoreToast\"")) }
                content { string(containsString("titleKey: 'logging.history_title'")) }
                content { string(containsString("/js/history-list.js")) }
            }
    }

    @Test
    fun `GET security-analysis page renders HTML`() {
        mockMvc
            .get("/security-analysis")
            .andExpect {
                status { isOk() }
                content { string(containsString("StackLens")) }
            }
    }

    @Test
    fun `GET review-analysis page renders HTML`() {
        mockMvc
            .get("/review-analysis")
            .andExpect {
                status { isOk() }
                content { string(containsString("StackLens")) }
            }
    }

    @Test
    fun `GET docs page renders HTML`() {
        mockMvc
            .get("/docs")
            .andExpect {
                status { isOk() }
                content { string(containsString("StackLens")) }
            }
    }

    @Test
    fun `GET tooling page renders HTML`() {
        mockMvc
            .get("/tooling")
            .andExpect {
                status { isOk() }
                content { string(containsString("StackLens")) }
            }
    }

    @Test
    fun `GET docker-test page renders HTML`() {
        mockMvc
            .get("/docker-test")
            .andExpect {
                status { isOk() }
                content { string(containsString("StackLens")) }
            }
    }

    @Test
    fun `GET simulated-mr page for an id renders HTML`() {
        mockMvc
            .get("/simulated-mr/1")
            .andExpect {
                status { isOk() }
                content { string(containsString("StackLens")) }
            }
    }

    @Test
    fun `GET simulated-github-pr page for an id renders HTML`() {
        mockMvc
            .get("/simulated-github-pr/1")
            .andExpect {
                status { isOk() }
                content { string(containsString("StackLens")) }
            }
    }

    @Test
    fun `GET ki-code-plan page for an id renders HTML`() {
        mockMvc
            .get("/ki-code-plan/1")
            .andExpect {
                status { isOk() }
                content { string(containsString("StackLens")) }
            }
    }

    @Test
    fun `GET tool page shows the abort banner for a cancelled tool`() {
        val tool = persistedTool(AnalysisStatus.CANCELLED, "Tool-Generierung wurde abgebrochen. Starte die Generierung neu.")

        mockMvc
            .get("/tool/${tool.id}")
            .andExpect {
                status { isOk() }
                content { string(containsString("tvErrorMsg")) }
                content { string(containsString("abgebrochen")) }
            }
    }

    @Test
    fun `GET tool page hides the abort banner once the tool is ready again`() {
        val tool =
            persistedTool(
                AnalysisStatus.READY,
                "Tool-Generierung wurde abgebrochen. Starte die Generierung neu.",
            )

        mockMvc
            .get("/tool/${tool.id}")
            .andExpect {
                status { isOk() }
                content { string(not(containsString("Tool-Generierung wurde abgebrochen"))) }
            }
    }

    @Test
    fun `GET tool detail page for unknown id redirects to tooling`() {
        mockMvc
            .get("/tool/999999")
            .andExpect { status { is3xxRedirection() } }
    }
}
