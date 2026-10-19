package com.stacklens.application.service

import com.stacklens.domain.enums.SessionStatus
import com.stacklens.domain.model.DatabaseConnectionError
import com.stacklens.domain.model.SearchSession
import com.stacklens.testsupport.TestJsonCodec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SearchSessionErrorsCodecTest {
    private val codec = SearchSessionErrorsCodec(TestJsonCodec.create())

    @Test
    fun `decodeErrors returns empty list when the column is null`() {
        val session = session(databaseConnectionErrors = null)
        assertThat(codec.decodeErrors(session)).isEmpty()
    }

    @Test
    fun `decodeErrors returns empty list when the column is blank`() {
        val session = session(databaseConnectionErrors = "   ")
        assertThat(codec.decodeErrors(session)).isEmpty()
    }

    @Test
    fun `decodeErrors returns empty list when the JSON is malformed`() {
        val session = session(databaseConnectionErrors = "not json")
        assertThat(codec.decodeErrors(session)).isEmpty()
    }

    @Test
    fun `encodeErrors writes JSON and decodeErrors reads it back`() {
        val session = session(databaseConnectionErrors = null)
        val errors =
            listOf(
                DatabaseConnectionError(
                    configId = 50L,
                    configName = "DB-A",
                    host = "localhost",
                    databaseName = "x",
                    dbType = "POSTGRES",
                    errorMessage = "boom",
                ),
            )

        codec.encodeErrors(session, errors)

        assertThat(session.databaseConnectionErrors).isNotNull()
        assertThat(codec.decodeErrors(session)).isEqualTo(errors)
    }

    @Test
    fun `encodeErrors with empty list clears the column`() {
        val session = session(databaseConnectionErrors = "[{\"configId\":1}]")
        codec.encodeErrors(session, emptyList())
        assertThat(session.databaseConnectionErrors).isNull()
    }

    private fun session(databaseConnectionErrors: String?): SearchSession {
        val s =
            SearchSession(
                id = 1L,
                keywords = "k",
                databaseConnectionErrors = databaseConnectionErrors,
            )
        s.status = SessionStatus.PENDING
        return s
    }
}
