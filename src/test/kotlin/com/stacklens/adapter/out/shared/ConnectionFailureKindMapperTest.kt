package com.stacklens.adapter.out.shared

import com.stacklens.application.port.model.ConnectionFailureKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException

class ConnectionFailureKindMapperTest {
    @Test
    fun `status maps to the matching failure kind`() {
        assertEquals(ConnectionFailureKind.AUTHENTICATION, connectionFailureKind(401))
        assertEquals(ConnectionFailureKind.AUTHORIZATION, connectionFailureKind(403))
        assertEquals(ConnectionFailureKind.NOT_FOUND, connectionFailureKind(404))
        assertEquals(ConnectionFailureKind.CONNECTION, connectionFailureKind(null))
        assertEquals(ConnectionFailureKind.OTHER, connectionFailureKind(500))
    }

    @Test
    fun `cause maps to the matching failure kind`() {
        assertEquals(
            ConnectionFailureKind.AUTHENTICATION,
            connectionFailureKindFor(
                HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders(), ByteArray(0), null),
            ),
        )
        assertEquals(ConnectionFailureKind.CONNECTION, connectionFailureKindFor(RuntimeException("boom")))
    }

    @Test
    fun `message maps to the matching failure kind`() {
        assertEquals(ConnectionFailureKind.AUTHENTICATION, connectionFailureKindFromMessage("HTTP 401"))
        assertEquals(ConnectionFailureKind.AUTHENTICATION, connectionFailureKindFromMessage("Unauthorized"))
        assertEquals(ConnectionFailureKind.AUTHORIZATION, connectionFailureKindFromMessage("HTTP 403"))
        assertEquals(ConnectionFailureKind.AUTHORIZATION, connectionFailureKindFromMessage("Forbidden"))
        assertEquals(ConnectionFailureKind.NOT_FOUND, connectionFailureKindFromMessage("HTTP 404"))
        assertEquals(ConnectionFailureKind.CONNECTION, connectionFailureKindFromMessage("Read timeout"))
        assertEquals(ConnectionFailureKind.CONNECTION, connectionFailureKindFromMessage("Connection refused"))
        assertEquals(ConnectionFailureKind.OTHER, connectionFailureKindFromMessage("something else"))
    }
}
