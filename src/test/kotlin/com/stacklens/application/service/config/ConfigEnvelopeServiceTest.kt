package com.stacklens.application.service.config

import com.stacklens.application.port.out.config.ConfigEnvelopePort
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ConfigEnvelopeServiceTest {
    private val port: ConfigEnvelopePort = mock()

    private val service = ConfigEnvelopeService(port)

    @Test
    fun `encrypt delegates to the config envelope port`() {
        whenever(port.encrypt(byteArrayOf(2), "pw")).thenReturn(byteArrayOf(1))

        assertArrayEquals(byteArrayOf(1), service.encrypt(byteArrayOf(2), "pw"))
        verify(port).encrypt(byteArrayOf(2), "pw")
    }

    @Test
    fun `decrypt delegates to the config envelope port`() {
        whenever(port.decrypt(byteArrayOf(2), "pw")).thenReturn(byteArrayOf(1))

        assertArrayEquals(byteArrayOf(1), service.decrypt(byteArrayOf(2), "pw"))
        verify(port).decrypt(byteArrayOf(2), "pw")
    }

    @Test
    fun `decrypt forwards a null result`() {
        whenever(port.decrypt(byteArrayOf(2), "pw")).thenReturn(null)

        assertNull(service.decrypt(byteArrayOf(2), "pw"))
    }
}
