package com.stacklens.application.service

import com.stacklens.application.collaboration.SearchSessionHistory
import com.stacklens.application.port.inbound.search.SearchLoadMoreUseCase
import com.stacklens.application.port.out.SearchResultPort
import com.stacklens.application.port.out.SearchSessionPort
import com.stacklens.application.port.out.search.ConfluenceSearchPort
import com.stacklens.application.service.search.SearchConfigResolver
import com.stacklens.domain.enums.SessionStatus
import com.stacklens.domain.enums.SourceType
import com.stacklens.domain.model.ConfluenceConfig
import com.stacklens.domain.model.SearchResult
import com.stacklens.domain.model.SearchSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SearchLoadMoreServiceTest {
    @Mock private lateinit var searchHistory: SearchSessionHistory

    @Mock private lateinit var searchConfigProvider: SearchConfigResolver

    @Mock private lateinit var resultRepo: SearchResultPort

    @Mock private lateinit var sessionRepo: SearchSessionPort

    @Mock private lateinit var confluenceSearch: ConfluenceSearchPort

    private fun makeService() =
        SearchLoadMoreService(
            searchHistory = searchHistory,
            searchConfigProvider = searchConfigProvider,
            resultRepo = resultRepo,
            sessionRepo = sessionRepo,
            confluenceSearch = confluenceSearch,
        )

    private fun completedSession(
        id: Long = 1L,
        keywords: String = "login",
    ) = SearchSession(
        id = id,
        keywords = keywords,
        status = SessionStatus.COMPLETED,
    )

    private fun confluenceConfig(name: String = "MyConf") =
        ConfluenceConfig(
            id = 1L,
            name = name,
            baseUrl = "https://conf.example.com",
            username = "user",
            apiToken = "token",
        )

    private fun makeSearchResult(
        sessionId: Long,
        keyword: String,
        configName: String? = null,
    ) = SearchResult(
        sessionId = sessionId,
        keyword = keyword,
        sourceType = SourceType.CONFLUENCE,
        configName = configName,
    )

    @Test
    fun `loadMoreConfluence throws IllegalArgumentException when session is not found`() {
        whenever(searchHistory.findSession(99L)).thenReturn(null)
        val svc = makeService()
        assertThrows(IllegalArgumentException::class.java) {
            svc.loadMoreConfluence(99L)
        }
    }

    @Test
    fun `loadMoreConfluence throws IllegalStateException when session is RUNNING`() {
        val session = SearchSession(id = 1L, keywords = "kw", status = SessionStatus.RUNNING)
        whenever(searchHistory.findSession(1L)).thenReturn(session)
        val svc = makeService()
        assertThrows(IllegalStateException::class.java) {
            svc.loadMoreConfluence(1L)
        }
    }

    @Test
    fun `loadMoreConfluence throws IllegalStateException when session is PENDING`() {
        val session = SearchSession(id = 1L, keywords = "kw", status = SessionStatus.PENDING)
        whenever(searchHistory.findSession(1L)).thenReturn(session)
        val svc = makeService()
        assertThrows(IllegalStateException::class.java) {
            svc.loadMoreConfluence(1L)
        }
    }

    @Test
    fun `loadMoreConfluence throws IllegalStateException when session is CANCELLED`() {
        val session = SearchSession(id = 1L, keywords = "kw", status = SessionStatus.CANCELLED)
        whenever(searchHistory.findSession(1L)).thenReturn(session)
        val svc = makeService()
        assertThrows(IllegalStateException::class.java) {
            svc.loadMoreConfluence(1L)
        }
    }

    @Test
    fun `loadMoreConfluence returns zero added when no confluence configs`() {
        val session = completedSession()
        whenever(searchHistory.findSession(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveConfluenceConfigs(session)).thenReturn(emptyList())
        whenever(searchHistory.getSessionResultCount(1L)).thenReturn(42L)

        val svc = makeService()
        val result = svc.loadMoreConfluence(1L)

        assertEquals(0L, result.addedCount)
        assertEquals(42L, result.totalCount)
        assertFalse(result.hasMore)
        verify(confluenceSearch, never()).searchKeyword(any(), any(), any(), any(), any())
    }

    @Test
    fun `loadMoreConfluence fetches results for each keyword x config pair`() {
        val session = completedSession(keywords = "login,security")
        val config = confluenceConfig("MyConf")
        whenever(searchHistory.findSession(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveConfluenceConfigs(session)).thenReturn(listOf(config))
        whenever(resultRepo.findBySessionIdAndSourceType(1L, SourceType.CONFLUENCE))
            .thenReturn(emptyList())
        val r1 = makeSearchResult(1L, "login", "MyConf")
        val r2 = makeSearchResult(1L, "security", "MyConf")
        whenever(confluenceSearch.searchKeyword(config, "login", emptyList(), 1L, 0))
            .thenReturn(listOf(r1))
        whenever(confluenceSearch.searchKeyword(config, "security", emptyList(), 1L, 0))
            .thenReturn(listOf(r2))
        whenever(resultRepo.saveAllResults(any())).thenReturn(listOf(r1, r2))
        val updatedSession = completedSession()
        whenever(sessionRepo.findSessionById(1L)).thenReturn(updatedSession)
        whenever(searchHistory.getSessionResultCount(1L)).thenReturn(2L)

        val svc = makeService()
        val result = svc.loadMoreConfluence(1L)

        assertEquals(2L, result.addedCount)
        assertEquals(2L, result.totalCount)
        assertFalse(result.hasMore)
    }

    @Test
    fun `loadMoreConfluence sets hasMore to true when any keyword-config pair has 25 or more results`() {
        val session = completedSession()
        val config = confluenceConfig()
        whenever(searchHistory.findSession(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveConfluenceConfigs(session)).thenReturn(listOf(config))
        whenever(resultRepo.findBySessionIdAndSourceType(1L, SourceType.CONFLUENCE))
            .thenReturn(emptyList())
        val results = (1..25).map { makeSearchResult(1L, "login", null) }
        whenever(confluenceSearch.searchKeyword(any(), any(), any(), any(), any()))
            .thenReturn(results)
        whenever(resultRepo.saveAllResults(any())).thenReturn(results)
        val updatedSession = completedSession()
        whenever(sessionRepo.findSessionById(1L)).thenReturn(updatedSession)
        whenever(searchHistory.getSessionResultCount(1L)).thenReturn(25L)

        val svc = makeService()
        val result = svc.loadMoreConfluence(1L)

        assertTrue(result.hasMore)
    }

    @Test
    fun `loadMoreConfluence uses existing results count as offset`() {
        val session = completedSession()
        val config = confluenceConfig("C1")
        whenever(searchHistory.findSession(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveConfluenceConfigs(session)).thenReturn(listOf(config))
        val existing = (1..5).map { makeSearchResult(1L, "login", "C1") }
        whenever(resultRepo.findBySessionIdAndSourceType(1L, SourceType.CONFLUENCE))
            .thenReturn(existing)
        val newResults = listOf(makeSearchResult(1L, "login", "C1"))
        whenever(confluenceSearch.searchKeyword(config, "login", emptyList(), 1L, 5))
            .thenReturn(newResults)
        whenever(resultRepo.saveAllResults(any())).thenReturn(newResults)
        val updatedSession = completedSession()
        whenever(sessionRepo.findSessionById(1L)).thenReturn(updatedSession)
        whenever(searchHistory.getSessionResultCount(1L)).thenReturn(6L)

        val svc = makeService()
        val result = svc.loadMoreConfluence(1L)

        assertEquals(1L, result.addedCount)
    }

    @Test
    fun `loadMoreConfluence handles null configName in existing results via empty string key`() {
        val session = completedSession()
        val config = confluenceConfig("Config")
        whenever(searchHistory.findSession(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveConfluenceConfigs(session)).thenReturn(listOf(config))
        val existing = listOf(makeSearchResult(1L, "login", null))
        whenever(resultRepo.findBySessionIdAndSourceType(1L, SourceType.CONFLUENCE))
            .thenReturn(existing)
        val newResults = listOf(makeSearchResult(1L, "login", "Config"))
        whenever(confluenceSearch.searchKeyword(config, "login", emptyList(), 1L, 0))
            .thenReturn(newResults)
        whenever(resultRepo.saveAllResults(any())).thenReturn(newResults)
        whenever(sessionRepo.findSessionById(1L)).thenReturn(completedSession())
        whenever(searchHistory.getSessionResultCount(1L)).thenReturn(2L)

        val svc = makeService()
        val result = svc.loadMoreConfluence(1L)
        assertEquals(1L, result.addedCount)
    }

    @Test
    fun `loadMoreConfluence skips session update when updatedSession is null`() {
        val session = completedSession()
        val config = confluenceConfig()
        whenever(searchHistory.findSession(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveConfluenceConfigs(session)).thenReturn(listOf(config))
        whenever(resultRepo.findBySessionIdAndSourceType(1L, SourceType.CONFLUENCE))
            .thenReturn(emptyList())
        val results = listOf(makeSearchResult(1L, "login", null))
        whenever(confluenceSearch.searchKeyword(any(), any(), any(), any(), any()))
            .thenReturn(results)
        whenever(resultRepo.saveAllResults(any())).thenReturn(results)
        whenever(sessionRepo.findSessionById(1L)).thenReturn(null)
        whenever(searchHistory.getSessionResultCount(1L)).thenReturn(1L)

        val svc = makeService()
        val result = svc.loadMoreConfluence(1L)

        assertEquals(1L, result.addedCount)
        verify(sessionRepo, never()).saveSession(any())
    }

    @Test
    fun `loadMoreConfluence does not save results when confluence returns empty list`() {
        val session = completedSession()
        val config = confluenceConfig()
        whenever(searchHistory.findSession(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveConfluenceConfigs(session)).thenReturn(listOf(config))
        whenever(resultRepo.findBySessionIdAndSourceType(1L, SourceType.CONFLUENCE))
            .thenReturn(emptyList())
        whenever(confluenceSearch.searchKeyword(any(), any(), any(), any(), any()))
            .thenReturn(emptyList())
        whenever(searchHistory.getSessionResultCount(1L)).thenReturn(0L)

        val svc = makeService()
        val result = svc.loadMoreConfluence(1L)

        assertEquals(0L, result.addedCount)
        assertFalse(result.hasMore)
        verify(resultRepo, never()).saveAllResults(any())
    }

    @Test
    fun `loadMoreConfluence handles multiple configs for the same keyword`() {
        val session = completedSession()
        val config1 = ConfluenceConfig(id = 1L, name = "A", baseUrl = "https://a.com", username = "u", apiToken = "t")
        val config2 = ConfluenceConfig(id = 2L, name = "B", baseUrl = "https://b.com", username = "u", apiToken = "t")
        whenever(searchHistory.findSession(1L)).thenReturn(session)
        whenever(searchConfigProvider.resolveConfluenceConfigs(session)).thenReturn(listOf(config1, config2))
        whenever(resultRepo.findBySessionIdAndSourceType(1L, SourceType.CONFLUENCE))
            .thenReturn(emptyList())
        val r1 = makeSearchResult(1L, "login", "A")
        val r2 = makeSearchResult(1L, "login", "B")
        whenever(confluenceSearch.searchKeyword(config1, "login", emptyList(), 1L, 0)).thenReturn(listOf(r1))
        whenever(confluenceSearch.searchKeyword(config2, "login", emptyList(), 1L, 0)).thenReturn(listOf(r2))
        whenever(resultRepo.saveAllResults(any())).thenReturn(listOf(r1, r2))
        whenever(sessionRepo.findSessionById(1L)).thenReturn(completedSession())
        whenever(searchHistory.getSessionResultCount(1L)).thenReturn(2L)

        val svc = makeService()
        val result = svc.loadMoreConfluence(1L)

        assertEquals(2L, result.addedCount)
    }

    @Test
    fun `SearchLoadMoreResult properties are accessible`() {
        val r = SearchLoadMoreUseCase.SearchLoadMoreResult(addedCount = 5L, totalCount = 10L, hasMore = true)
        assertEquals(5L, r.addedCount)
        assertEquals(10L, r.totalCount)
        assertTrue(r.hasMore)
    }
}
