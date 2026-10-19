package com.stacklens.adapter.out.persistence

import com.stacklens.domain.model.AiConfig
import com.stacklens.domain.model.GithubRepoConfig
import com.stacklens.domain.model.JwtAnalysisRecord
import com.stacklens.domain.model.McpServerConfig
import com.stacklens.domain.model.ReviewAnalysisConfig
import com.stacklens.domain.model.TicketCreationRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest

@DataJpaTest
class RepositoryAdditionalCoverageTest {
    @Autowired private lateinit var ticketCreationRepo: TicketCreationRecordRepository

    @Autowired private lateinit var githubRepo: GithubRepoConfigRepository

    @Autowired private lateinit var mcpRepo: McpServerConfigRepository

    @Autowired private lateinit var aiConfigRepo: AiConfigRepository

    @Autowired private lateinit var reviewConfigRepo: ReviewAnalysisConfigRepository

    @Autowired private lateinit var jwtRepo: JwtAnalysisRepository

    @Test
    fun `TicketCreationRecordRepository save findAll findById delete deleteAll`() {
        val saved =
            ticketCreationRepo.saveTicketCreationRecord(
                TicketCreationRecord(inputText = "hello", summary = "s", status = "RUNNING"),
            )
        assertNotNull(saved.id)

        val all = ticketCreationRepo.findAllTicketCreationRecordsByCreatedAtDesc()
        assertEquals(1, all.size)
        assertEquals("hello", all[0].inputText)

        val byId = ticketCreationRepo.findTicketCreationRecordById(saved.id)
        assertNotNull(byId)
        assertEquals(saved.id, byId!!.id)

        assertNull(ticketCreationRepo.findTicketCreationRecordById(999_999L))

        ticketCreationRepo.deleteTicketCreationRecordById(saved.id)
        assertNull(ticketCreationRepo.findTicketCreationRecordById(saved.id))

        ticketCreationRepo.saveTicketCreationRecord(TicketCreationRecord(inputText = "x"))
        ticketCreationRepo.deleteAllTicketCreationRecords()
        assertTrue(ticketCreationRepo.findAll().isEmpty())
    }

    @Test
    fun `TicketCreationRecordRepository markAllRunningAsFailed updates only RUNNING rows and returns the count`() {
        val running1 =
            ticketCreationRepo.saveTicketCreationRecord(
                TicketCreationRecord(inputText = "r1", status = "RUNNING"),
            )
        val running2 =
            ticketCreationRepo.saveTicketCreationRecord(
                TicketCreationRecord(inputText = "r2", status = "RUNNING"),
            )
        val running3 =
            ticketCreationRepo.saveTicketCreationRecord(
                TicketCreationRecord(inputText = "r3", status = "RUNNING"),
            )
        val completed =
            ticketCreationRepo.saveTicketCreationRecord(
                TicketCreationRecord(
                    inputText = "ok",
                    summary = "Already done",
                    status = "COMPLETED",
                ),
            )
        val failed =
            ticketCreationRepo.saveTicketCreationRecord(
                TicketCreationRecord(
                    inputText = "bad",
                    status = "FAILED",
                    errorMessage = "previous error",
                ),
            )
        val cancelled =
            ticketCreationRepo.saveTicketCreationRecord(
                TicketCreationRecord(inputText = "stop", status = "CANCELLED"),
            )
        assertNotNull(running1.id)
        assertNotNull(running2.id)
        assertNotNull(running3.id)

        val reapMessage = "Generation was interrupted by an unclean JVM shutdown. Please retry."
        val affected = ticketCreationRepo.markAllRunningAsFailed(reapMessage)

        assertEquals(3, affected)

        val r1After = ticketCreationRepo.findTicketCreationRecordById(running1.id)
        val r2After = ticketCreationRepo.findTicketCreationRecordById(running2.id)
        val r3After = ticketCreationRepo.findTicketCreationRecordById(running3.id)
        assertEquals("FAILED", r1After!!.status)
        assertEquals("FAILED", r2After!!.status)
        assertEquals("FAILED", r3After!!.status)
        assertEquals(reapMessage, r1After.errorMessage)
        assertEquals(reapMessage, r2After.errorMessage)
        assertEquals(reapMessage, r3After.errorMessage)

        val completedAfter = ticketCreationRepo.findTicketCreationRecordById(completed.id)
        val failedAfter = ticketCreationRepo.findTicketCreationRecordById(failed.id)
        val cancelledAfter = ticketCreationRepo.findTicketCreationRecordById(cancelled.id)
        assertEquals("COMPLETED", completedAfter!!.status)
        assertNull(completedAfter.errorMessage)
        assertEquals("FAILED", failedAfter!!.status)
        assertEquals("previous error", failedAfter.errorMessage)
        assertEquals("CANCELLED", cancelledAfter!!.status)
        assertNull(cancelledAfter.errorMessage)

        assertEquals(0, ticketCreationRepo.markAllRunningAsFailed(reapMessage))
    }

    @Test
    fun `TicketCreationRecordRepository markAllRunningAsFailed appends to existing errorMessage rather than overwriting it`() {
        val existingMsg = "AI returned empty result"
        ticketCreationRepo.saveTicketCreationRecord(
            TicketCreationRecord(
                inputText = "r-with-msg",
                status = "RUNNING",
                errorMessage = existingMsg,
            ),
        )

        val reapMessage = "Generation was interrupted by an unclean JVM shutdown. Please retry."
        val affected = ticketCreationRepo.markAllRunningAsFailed(reapMessage)

        assertEquals(1, affected)
        val all = ticketCreationRepo.findAllTicketCreationRecordsByCreatedAtDesc()
        val updated = all.first { it.inputText == "r-with-msg" }
        assertEquals("FAILED", updated.status)
        assertEquals("$existingMsg\n$reapMessage", updated.errorMessage)
    }

    @Test
    fun `TicketCreationRecordRepository markAllRunningAsFailed returns zero when there are no RUNNING rows`() {
        ticketCreationRepo.saveTicketCreationRecord(
            TicketCreationRecord(inputText = "done", status = "COMPLETED"),
        )

        assertEquals(0, ticketCreationRepo.markAllRunningAsFailed("anything"))
    }

    @Test
    fun `GithubRepoConfigRepository save findAll findById findAllByIds findByActive exists delete deleteAll`() {
        val active =
            githubRepo.saveGithubRepoConfig(
                GithubRepoConfig(
                    name = "g1",
                    owner = "octo",
                    repo = "cat",
                    active = true,
                    accessToken = "t",
                ),
            )
        val inactive =
            githubRepo.saveGithubRepoConfig(
                GithubRepoConfig(
                    name = "g2",
                    owner = "octo",
                    repo = "dog",
                    active = false,
                    accessToken = "t",
                ),
            )
        assertNotNull(active.id)
        assertNotNull(inactive.id)

        val all = githubRepo.findAllGithubRepoConfigs()
        assertEquals(2, all.size)

        val byId = githubRepo.findGithubRepoConfigById(active.id)
        assertNotNull(byId)
        assertEquals("octo", byId!!.owner)
        assertNull(githubRepo.findGithubRepoConfigById(999_999L))

        val byIds = githubRepo.findAllGithubRepoConfigsByIds(listOf(active.id, inactive.id))
        assertEquals(2, byIds.size)

        val activeOnly = githubRepo.findByActiveTrue()
        assertEquals(1, activeOnly.size)
        assertTrue(activeOnly[0].active)

        assertTrue(githubRepo.githubRepoConfigExistsById(active.id))
        assertFalse(githubRepo.githubRepoConfigExistsById(999_999L))

        githubRepo.deleteGithubRepoConfigById(active.id)
        assertNull(githubRepo.findGithubRepoConfigById(active.id))

        githubRepo.deleteAllGithubRepoConfigs()
        assertTrue(githubRepo.findAll().isEmpty())
    }

    @Test
    fun `McpServerConfigRepository save findAll findById findFirstActive delete deleteAll`() {
        val active =
            mcpRepo.saveMcpServerConfig(
                McpServerConfig(name = "m1", baseUrl = "http://localhost:1", active = true),
            )
        val inactive =
            mcpRepo.saveMcpServerConfig(
                McpServerConfig(name = "m2", baseUrl = "http://localhost:2", active = false),
            )
        assertNotNull(active.id)
        assertNotNull(inactive.id)

        val all = mcpRepo.findAllMcpServerConfigs()
        assertEquals(2, all.size)

        val byId = mcpRepo.findMcpServerConfigById(active.id)
        assertNotNull(byId)
        assertNull(mcpRepo.findMcpServerConfigById(999_999L))

        val firstActive = mcpRepo.findFirstActiveMcpServerConfig()
        assertNotNull(firstActive)
        assertTrue(firstActive!!.active)
        assertEquals("m1", firstActive.name)

        mcpRepo.deleteMcpServerConfigById(active.id)
        assertNull(mcpRepo.findMcpServerConfigById(active.id))

        mcpRepo.deleteAllMcpServerConfigs()
        assertTrue(mcpRepo.findAll().isEmpty())

        @Suppress("UNUSED_VARIABLE")
        val x = inactive
    }

    @Test
    fun `AiConfigRepository find save deleteAll`() {
        assertNull(aiConfigRepo.findAiConfig())

        val saved = aiConfigRepo.saveAiConfig(AiConfig(model = "gpt-4"))
        assertNotNull(saved)

        val found = aiConfigRepo.findAiConfig()
        assertNotNull(found)
        assertEquals("gpt-4", found!!.model)

        aiConfigRepo.deleteAiConfig()
        assertNull(aiConfigRepo.findAiConfig())
    }

    @Test
    fun `ReviewAnalysisConfigRepository find save`() {
        assertNull(reviewConfigRepo.findReviewAnalysisConfig())

        val saved =
            reviewConfigRepo.saveReviewAnalysisConfig(
                ReviewAnalysisConfig(jiraSprintUrl = "https://sprint/1"),
            )
        assertNotNull(saved)

        val found = reviewConfigRepo.findReviewAnalysisConfig()
        assertNotNull(found)
        assertEquals("https://sprint/1", found!!.jiraSprintUrl)
    }

    @Test
    fun `JwtAnalysisRepository save and deleteAll`() {
        jwtRepo.save(JwtAnalysisRecord(token = "tok-1", status = "VALID"))
        assertEquals(1, jwtRepo.findAll().size)

        jwtRepo.deleteAllJwtAnalyses()
        assertTrue(jwtRepo.findAll().isEmpty())
    }
}
