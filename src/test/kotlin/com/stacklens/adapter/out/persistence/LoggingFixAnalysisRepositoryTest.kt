package com.stacklens.adapter.out.persistence

import com.stacklens.domain.model.LoggingFixAnalysis
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest

@DataJpaTest
class LoggingFixAnalysisRepositoryTest {
    @Autowired private lateinit var repo: LoggingFixAnalysisRepository

    @Test
    fun `LoggingFixAnalysisRepository saveLoggingFixAnalysis delegates to save and returns entity with id`() {
        val saved =
            repo.saveLoggingFixAnalysis(
                LoggingFixAnalysis(
                    featureSlug = "fix-1",
                    logText = "ERROR NullPointerException at X.y(Z.java:1)",
                    selectedCategories = "error,exception",
                ),
            )
        assertNotNull(saved.id)
        assertEquals("fix-1", saved.featureSlug)
    }

    @Test
    fun `LoggingFixAnalysisRepository findLoggingFixAnalysisById returns saved entity`() {
        val saved =
            repo.saveLoggingFixAnalysis(
                LoggingFixAnalysis(
                    featureSlug = "fix-2",
                    logText = "ERROR boom",
                    selectedCategories = "error",
                ),
            )
        val found = repo.findLoggingFixAnalysisById(saved.id)
        assertNotNull(found)
        assertEquals("fix-2", found!!.featureSlug)
    }

    @Test
    fun `LoggingFixAnalysisRepository findLoggingFixAnalysisById returns null for unknown id`() {
        val found = repo.findLoggingFixAnalysisById(999_999L)
        assertNull(found)
    }

    @Test
    fun `LoggingFixAnalysisRepository findRecentLoggingFixAnalyses respects the limit and ordering`() {
        repeat(5) { i ->
            repo.saveLoggingFixAnalysis(
                LoggingFixAnalysis(
                    featureSlug = "fix-$i",
                    logText = "log-$i",
                    selectedCategories = "",
                ),
            )
        }
        val recent = repo.findRecentLoggingFixAnalyses(3)
        assertEquals(3, recent.size)
        assertTrue(recent[0].id > recent[1].id, "findRecent must return newest-first by id")
    }

    @Test
    fun `LoggingFixAnalysisRepository findAllByOrderByCreatedAtDesc returns all rows newest first`() {
        repo.saveLoggingFixAnalysis(
            LoggingFixAnalysis(
                featureSlug = "a",
                logText = "log-a",
                selectedCategories = "",
            ),
        )
        repo.saveLoggingFixAnalysis(
            LoggingFixAnalysis(
                featureSlug = "b",
                logText = "log-b",
                selectedCategories = "",
            ),
        )
        val all = repo.findAllByOrderByCreatedAtDesc()
        assertEquals(2, all.size)
    }

    @Test
    fun `LoggingFixAnalysisRepository deleteLoggingFixAnalysisById delegates to deleteById`() {
        val saved =
            repo.saveLoggingFixAnalysis(
                LoggingFixAnalysis(
                    featureSlug = "to-delete",
                    logText = "x",
                    selectedCategories = "",
                ),
            )
        repo.deleteLoggingFixAnalysisById(saved.id)
        assertNull(repo.findLoggingFixAnalysisById(saved.id))
    }

    @Test
    fun `LoggingFixAnalysisRepository deleteLoggingFixAnalysisById on unknown id does not throw`() {
        repo.deleteLoggingFixAnalysisById(999_999L)
    }

    @Test
    fun `LoggingFixAnalysisRepository deleteAllLoggingFixAnalyses delegates to deleteAll`() {
        repo.saveLoggingFixAnalysis(
            LoggingFixAnalysis(
                featureSlug = "x",
                logText = "x",
                selectedCategories = "",
            ),
        )
        repo.saveLoggingFixAnalysis(
            LoggingFixAnalysis(
                featureSlug = "y",
                logText = "y",
                selectedCategories = "",
            ),
        )
        repo.deleteAllLoggingFixAnalyses()
        assertTrue(repo.findAllByOrderByCreatedAtDesc().isEmpty())
    }

    @Test
    fun `LoggingFixAnalysisRepository deleteAllLoggingFixAnalyses on empty table does not throw`() {
        repo.deleteAllLoggingFixAnalyses()
        assertTrue(repo.findAllByOrderByCreatedAtDesc().isEmpty())
    }

    @Test
    fun `LoggingFixAnalysisRepository deleteFinishedLoggingFixAnalyses removes only DONE ERROR CANCELLED rows`() {
        repo.saveLoggingFixAnalysis(
            LoggingFixAnalysis(
                featureSlug = "done",
                logText = "d",
                selectedCategories = "",
                status = LoggingFixAnalysis.STATUS_DONE,
            ),
        )
        repo.saveLoggingFixAnalysis(
            LoggingFixAnalysis(
                featureSlug = "error",
                logText = "e",
                selectedCategories = "",
                status = LoggingFixAnalysis.STATUS_ERROR,
            ),
        )
        repo.saveLoggingFixAnalysis(
            LoggingFixAnalysis(
                featureSlug = "cancelled",
                logText = "c",
                selectedCategories = "",
                status = LoggingFixAnalysis.STATUS_CANCELLED,
            ),
        )
        val running =
            repo.saveLoggingFixAnalysis(
                LoggingFixAnalysis(
                    featureSlug = "running",
                    logText = "r",
                    selectedCategories = "",
                    status = LoggingFixAnalysis.STATUS_RUNNING,
                ),
            )
        val pending =
            repo.saveLoggingFixAnalysis(
                LoggingFixAnalysis(
                    featureSlug = "pending",
                    logText = "p",
                    selectedCategories = "",
                    status = LoggingFixAnalysis.STATUS_PENDING,
                ),
            )

        val deleted = repo.deleteFinishedLoggingFixAnalyses()
        assertEquals(3, deleted, "expected 3 finished rows removed (DONE/ERROR/CANCELLED)")

        val remaining = repo.findAllByOrderByCreatedAtDesc()
        assertEquals(2, remaining.size, "running + pending rows must remain")
        val remainingIds = remaining.map { it.id }.toSet()
        assertTrue(running.id in remainingIds)
        assertTrue(pending.id in remainingIds)
    }

    @Test
    fun `LoggingFixAnalysisRepository deleteFinishedLoggingFixAnalyses returns 0 on empty table`() {
        repo.deleteAllLoggingFixAnalyses()
        assertEquals(0, repo.deleteFinishedLoggingFixAnalyses())
    }

    @Test
    fun `LoggingFixAnalysisRepository CRUD roundtrip preserves all nullable columns`() {
        val saved =
            repo.saveLoggingFixAnalysis(
                LoggingFixAnalysis(
                    featureSlug = "full",
                    logText = "ERROR foo\n  at x.y(z.java:1)",
                    selectedCategories = "error",
                    localRepoConfigId = 42L,
                    language = "en",
                    status = LoggingFixAnalysis.STATUS_RUNNING,
                    errorMessage = "boom",
                    stageDiagnose = "diagnose text",
                    stagePlan = "plan text",
                    stageCode = "code text",
                    stageTests = "tests text",
                    stageStatusJson = """{"key":"value"}""",
                ),
            )
        val found = repo.findLoggingFixAnalysisById(saved.id)!!
        assertEquals("full", found.featureSlug)
        assertEquals(42L, found.localRepoConfigId)
        assertEquals("en", found.language)
        assertEquals(LoggingFixAnalysis.STATUS_RUNNING, found.status)
        assertEquals("boom", found.errorMessage)
        assertEquals("diagnose text", found.stageDiagnose)
        assertEquals("plan text", found.stagePlan)
        assertEquals("code text", found.stageCode)
        assertEquals("tests text", found.stageTests)
        assertEquals("""{"key":"value"}""", found.stageStatusJson)
    }
}
