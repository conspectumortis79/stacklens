package com.stacklens.adapter.out.shared

import com.stacklens.application.port.out.DiffHunkParserPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class MrFetchAdapterRefusedHostCoverageTest {
    private val adapter =
        MrFetchAdapter(
            diffHunkParser = mock<DiffHunkParserPort>(),
        )

    @Test
    fun `a host with a port is rendered as host colon port`() {
        assertEquals("127.0.0.1:8080", adapter.refusedServerHost("http://127.0.0.1:8080/group/-/merge_requests/1"))
    }

    @Test
    fun `a host without a port is rendered bare`() {
        assertEquals("gitlab.example", adapter.refusedServerHost("https://gitlab.example/group/-/merge_requests/1"))
    }

    @Test
    fun `an unparseable url is quoted as it is`() {
        assertEquals("not a url at all", adapter.refusedServerHost("not a url at all"))
    }
}
