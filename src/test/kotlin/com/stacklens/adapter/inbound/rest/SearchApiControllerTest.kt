package com.stacklens.adapter.inbound.rest

import com.stacklens.application.port.inbound.platform.I18nUseCase
import com.stacklens.application.port.inbound.search.SearchHistoryUseCase
import com.stacklens.application.port.inbound.search.SearchLoadMoreUseCase
import com.stacklens.application.port.inbound.search.SearchUseCase
import com.stacklens.application.service.SearchSessionErrorsCodec
import com.stacklens.domain.enums.SessionStatus
import com.stacklens.domain.model.SearchSession
import com.stacklens.testsupport.TestJsonCodec
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus

class SearchApiControllerTest {
    private val searchUseCase: SearchUseCase = mock()
    private val searchHistory: SearchHistoryUseCase = mock()
    private val loadMoreUseCase: SearchLoadMoreUseCase = mock()
    private val sessionErrorsCodec: SearchSessionErrorsCodec =
        SearchSessionErrorsCodec(TestJsonCodec.create())
    private val controller =
        SearchApiController(
            searchUseCase,
            searchHistory,
            loadMoreUseCase,
            sessionErrorsUseCase = sessionErrorsCodec,
            i18n =
                com.stacklens.application.service
                    .FakeI18n(),
        )

    private fun session(
        id: Long = 1L,
        status: SessionStatus = SessionStatus.COMPLETED,
        keywords: String = "test",
    ) = SearchSession(id = id, keywords = keywords, status = status)

    @Test
    fun `startSearch delegates to use case and returns sessionId and status`() {
        val s = session(id = 42L, status = SessionStatus.RUNNING)
        whenever(searchUseCase.startSearch(any())).thenReturn(s)
        val req = SearchRequestDto(keywords = listOf("foo"))
        val resp = controller.startSearch(req)
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any>
        assertEquals(42L, body["sessionId"])
        assertEquals("RUNNING", body["status"])
    }

    @Test
    fun `sessionStatus returns 404 when not found`() {
        whenever(searchHistory.findSession(99L)).thenReturn(null)
        val resp = controller.sessionStatus(99L)
        assertEquals(HttpStatus.NOT_FOUND, resp.statusCode)
    }

    @Test
    fun `sessionStatus returns the session fields`() {
        val s = session(id = 7L, status = SessionStatus.COMPLETED)
        s.applicationName = "my-app"
        s.finishedAt = java.time.LocalDateTime.now()
        s.errorMessage = "boom"
        whenever(searchHistory.findSession(7L)).thenReturn(s)
        whenever(searchHistory.getSessionResultCount(7L)).thenReturn(12L)
        val resp = controller.sessionStatus(7L)
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any>
        assertEquals(7L, body["sessionId"])
        assertEquals(12L, body["resultCount"])
        assertEquals("my-app", body["applicationName"])
        assertEquals("boom", body["error"])
    }

    @Test
    fun `sessionStatus returns databaseErrors as an empty list when none are persisted`() {
        val s = session(id = 8L, status = SessionStatus.COMPLETED)
        whenever(searchHistory.findSession(8L)).thenReturn(s)
        whenever(searchHistory.getSessionResultCount(8L)).thenReturn(0L)
        val resp = controller.sessionStatus(8L)

        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any>
        assertEquals(emptyList<Any>(), body["databaseErrors"])
    }

    @Test
    fun `listSessions returns a DTO list mapped from the history`() {
        val s = session(id = 1L, status = SessionStatus.COMPLETED, keywords = "k")
        s.applicationName = "app"
        s.finishedAt = java.time.LocalDateTime.now()
        whenever(searchHistory.listSessions()).thenReturn(listOf(s))
        val dtos = controller.listSessions()
        assertEquals(1, dtos.size)
        assertEquals(1L, dtos[0].id)
        assertEquals("app", dtos[0].applicationName)
    }

    @Test
    fun `listSessions serialises null finishedAt to null in the DTO`() {
        val s = session(id = 2L, status = SessionStatus.RUNNING, keywords = "k")
        s.applicationName = "app"
        whenever(searchHistory.listSessions()).thenReturn(listOf(s))
        val dtos = controller.listSessions()
        assertEquals(1, dtos.size)
        assertEquals(null, dtos[0].finishedAt)
    }

    @Test
    fun `getSession returns 404 when not found`() {
        whenever(searchHistory.findSession(99L)).thenReturn(null)
        val resp = controller.getSession(99L)
        assertEquals(HttpStatus.NOT_FOUND, resp.statusCode)
    }

    @Test
    fun `getSession returns the session fields`() {
        val s = session(id = 5L, status = SessionStatus.RUNNING)
        s.gitlabConfigIds = "1,2"
        s.jiraProjects = "P"
        whenever(searchHistory.findSession(5L)).thenReturn(s)
        val resp = controller.getSession(5L)
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any>
        assertEquals(5L, body["id"])
        assertEquals("RUNNING", body["status"])
    }

    @Test
    fun `deleteSession returns 404 when not found`() {
        whenever(searchHistory.sessionExists(99L)).thenReturn(false)
        val resp = controller.deleteSession(99L)
        assertEquals(HttpStatus.NOT_FOUND, resp.statusCode)
    }

    @Test
    fun `deleteSession returns 204 and delegates to history`() {
        whenever(searchHistory.sessionExists(1L)).thenReturn(true)
        val resp = controller.deleteSession(1L)
        assertEquals(HttpStatus.NO_CONTENT, resp.statusCode)
        verify(searchHistory).deleteSession(1L)
    }

    @Test
    fun `deleteAllSessions returns the deleted count`() {
        whenever(searchHistory.deleteAllSessions()).thenReturn(7)
        val resp = controller.deleteAllSessions()
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any>
        assertEquals(7, body["deleted"])
    }

    @Test
    fun `cancelSession returns 404 when not found`() {
        whenever(searchHistory.findSession(99L)).thenReturn(null)
        val resp = controller.cancelSession(99L)
        assertEquals(HttpStatus.NOT_FOUND, resp.statusCode)
    }

    @Test
    fun `cancelSession returns 400 when session is not RUNNING or PENDING`() {
        whenever(searchHistory.findSession(1L)).thenReturn(session(id = 1L, status = SessionStatus.COMPLETED))
        val resp = controller.cancelSession(1L)
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `cancelSession delegates to history when session is RUNNING`() {
        val s = session(id = 1L, status = SessionStatus.RUNNING)
        val cancelled = session(id = 1L, status = SessionStatus.CANCELLED)
        whenever(searchHistory.findSession(1L)).thenReturn(s)
        whenever(searchHistory.cancelSession(1L)).thenReturn(cancelled)
        val resp = controller.cancelSession(1L)
        assertEquals(HttpStatus.OK, resp.statusCode)
        verify(searchHistory).cancelSession(1L)
    }

    @Test
    fun `results returns an empty list when there are none`() {
        whenever(searchHistory.getResults(any(), any())).thenReturn(emptyList())
        val out = controller.results(1L, null)
        assertEquals(0, out.size)
    }

    @Test
    fun `results maps all nullable fields to empty string when null`() {
        val result =
            com.stacklens.domain.model.SearchResult(
                sessionId = 1L,
                keyword = "kw",
                sourceType = com.stacklens.domain.enums.SourceType.GITLAB,
                resultType = null,
                repoName = null,
                repoUrl = null,
                filePath = null,
                lineNumber = null,
                className = null,
                functionName = null,
                codeSnippet = null,
                configName = null,
                dbHost = null,
                databaseName = null,
                schemaName = null,
                tableName = null,
                columnName = null,
                dataType = null,
                ddlContext = null,
                crossRefParentId = null,
            )
        whenever(searchHistory.getResults(1L, null)).thenReturn(listOf(result))
        val out = controller.results(1L, null)
        assertEquals(1, out.size)
        assertEquals("", out[0]["resultType"])
        assertEquals("", out[0]["repoName"])
        assertEquals("", out[0]["repoUrl"])
        assertEquals("", out[0]["filePath"])
        assertEquals("", out[0]["lineNumber"])
        assertEquals("", out[0]["className"])
        assertEquals("", out[0]["functionName"])
        assertEquals("", out[0]["codeSnippet"])
        assertEquals("", out[0]["configName"])
        assertEquals("", out[0]["dbHost"])
        assertEquals("", out[0]["databaseName"])
        assertEquals("", out[0]["schemaName"])
        assertEquals("", out[0]["tableName"])
        assertEquals("", out[0]["columnName"])
        assertEquals("", out[0]["dataType"])
        assertEquals("", out[0]["ddlContext"])
        assertEquals("", out[0]["crossRefParent"])
    }

    @Test
    fun `results maps all non-null optional fields to their values`() {
        val result =
            com.stacklens.domain.model.SearchResult(
                sessionId = 2L,
                keyword = "q",
                sourceType = com.stacklens.domain.enums.SourceType.DATABASE,
                resultType = com.stacklens.domain.enums.ResultType.TABLE,
                repoName = "repo",
                repoUrl = "http://repo",
                filePath = "src/Foo.kt",
                lineNumber = 42,
                className = "Foo",
                functionName = "bar",
                codeSnippet = "val x = 1",
                configName = "db1",
                dbHost = "localhost",
                databaseName = "mydb",
                schemaName = "public",
                tableName = "users",
                columnName = "id",
                dataType = "int",
                ddlContext = "CREATE TABLE",
                crossRefParentId = 99L,
            )
        whenever(searchHistory.getResults(2L, "DATABASE")).thenReturn(listOf(result))
        val out = controller.results(2L, "DATABASE")
        assertEquals(1, out.size)
        assertEquals("TABLE", out[0]["resultType"])
        assertEquals("repo", out[0]["repoName"])
        assertEquals("src/Foo.kt", out[0]["filePath"])
        assertEquals(42, out[0]["lineNumber"])
        assertEquals("users", out[0]["tableName"])
        assertEquals(99L, out[0]["crossRefParent"])
    }

    @Test
    fun `sessionStatus returns empty strings for null optional fields`() {
        val s = session(id = 10L, status = SessionStatus.COMPLETED)
        whenever(searchHistory.findSession(10L)).thenReturn(s)
        whenever(searchHistory.getSessionResultCount(10L)).thenReturn(0L)
        val resp = controller.sessionStatus(10L)
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any>
        assertEquals("", body["applicationName"])
        assertEquals("", body["finishedAt"])
        assertEquals("", body["error"])
    }

    @Test
    fun `cancelSession delegates to history when session is PENDING`() {
        val s = session(id = 2L, status = SessionStatus.PENDING)
        val cancelled = session(id = 2L, status = SessionStatus.CANCELLED)
        whenever(searchHistory.findSession(2L)).thenReturn(s)
        whenever(searchHistory.cancelSession(2L)).thenReturn(cancelled)
        val resp = controller.cancelSession(2L)
        assertEquals(HttpStatus.OK, resp.statusCode)
        verify(searchHistory).cancelSession(2L)
    }

    @Test
    fun `tr uses i18n translation when i18n is set`() {
        val i18nMock = mock<I18nUseCase>()
        whenever(i18nMock.t(any<String>())).thenReturn("translated error")
        val ctrl =
            SearchApiController(
                searchUseCase,
                searchHistory,
                loadMoreUseCase,
                SearchSessionErrorsCodec(TestJsonCodec.create()),
                i18nMock,
            )
        val s = session(id = 55L, status = SessionStatus.COMPLETED)
        whenever(searchHistory.findSession(55L)).thenReturn(s)
        val resp = ctrl.cancelSession(55L)
        assertEquals(400, resp.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any>
        assertEquals("translated error", body["error"])
    }

    @Test
    fun `loadMoreConfluence returns 404 when session not found`() {
        whenever(loadMoreUseCase.loadMoreConfluence(99L))
            .thenThrow(IllegalArgumentException("session 99 not found"))
        val resp = controller.loadMoreConfluence(99L)
        assertEquals(HttpStatus.NOT_FOUND, resp.statusCode)
    }

    @Test
    fun `loadMoreConfluence returns 400 when session is not COMPLETED`() {
        whenever(loadMoreUseCase.loadMoreConfluence(1L))
            .thenThrow(IllegalStateException("session 1 is not completed"))
        val resp = controller.loadMoreConfluence(1L)
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
    }

    @Test
    fun `loadMoreConfluence returns 400 with i18n message when session is not COMPLETED`() {
        val i18nMock = mock<I18nUseCase>()
        whenever(i18nMock.t(any<String>())).thenReturn("session not running")
        val ctrl =
            SearchApiController(
                searchUseCase,
                searchHistory,
                loadMoreUseCase,
                sessionErrorsUseCase = SearchSessionErrorsCodec(TestJsonCodec.create()),
                i18nMock,
            )
        whenever(loadMoreUseCase.loadMoreConfluence(2L))
            .thenThrow(IllegalStateException("session 2 is not completed"))
        val resp = ctrl.loadMoreConfluence(2L)
        assertEquals(HttpStatus.BAD_REQUEST, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any>
        assertEquals("session not running", body["error"])
    }

    @Test
    fun `loadMoreConfluence returns addedCount 0 when no confluence configs`() {
        whenever(loadMoreUseCase.loadMoreConfluence(3L)).thenReturn(
            SearchLoadMoreUseCase.SearchLoadMoreResult(addedCount = 0L, totalCount = 5L, hasMore = false),
        )
        val resp = controller.loadMoreConfluence(3L)
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any>
        assertEquals(0L, body["addedCount"])
        assertEquals(5L, body["totalCount"])
        assertEquals(false, body["hasMore"])
    }

    @Test
    fun `loadMoreConfluence returns the use case result for a normal fetch`() {
        whenever(loadMoreUseCase.loadMoreConfluence(4L)).thenReturn(
            SearchLoadMoreUseCase.SearchLoadMoreResult(addedCount = 1L, totalCount = 1L, hasMore = false),
        )
        val resp = controller.loadMoreConfluence(4L)
        assertEquals(HttpStatus.OK, resp.statusCode)
        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any>
        assertEquals(1L, body["addedCount"])
        assertEquals(1L, body["totalCount"])
        assertEquals(false, body["hasMore"])
        verify(loadMoreUseCase).loadMoreConfluence(4L)
    }

    @Test
    fun `loadMoreConfluence returns hasMore true when the use case says so`() {
        whenever(loadMoreUseCase.loadMoreConfluence(5L)).thenReturn(
            SearchLoadMoreUseCase.SearchLoadMoreResult(addedCount = 25L, totalCount = 25L, hasMore = true),
        )
        val resp = controller.loadMoreConfluence(5L)

        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any>
        assertEquals(25L, body["addedCount"])
        assertEquals(true, body["hasMore"])
    }

    @Test
    fun `loadMoreConfluence returns addedCount 0 when use case returns empty`() {
        whenever(loadMoreUseCase.loadMoreConfluence(6L)).thenReturn(
            SearchLoadMoreUseCase.SearchLoadMoreResult(addedCount = 0L, totalCount = 0L, hasMore = false),
        )
        val resp = controller.loadMoreConfluence(6L)

        @Suppress("UNCHECKED_CAST")
        val body = resp.body as Map<String, Any>
        assertEquals(0L, body["addedCount"])
        assertEquals(false, body["hasMore"])
    }
}
