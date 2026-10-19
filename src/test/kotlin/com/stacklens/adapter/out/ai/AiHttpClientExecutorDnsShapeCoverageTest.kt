package com.stacklens.adapter.out.ai

import com.stacklens.testsupport.PrivateInvocation
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import java.io.IOException
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException

class AiHttpClientExecutorDnsShapeCoverageTest {
    private val executor = AiHttpClientExecutor(pollIntervalMillis = 5L)

    private fun prime(uri: URI): Any? = PrivateInvocation.invoke(executor, "primeDnsCache", uri)

    private fun uriWithHost(host: String?): URI {
        val uri = Mockito.mock(URI::class.java)
        whenever(uri.host).thenReturn(host)
        return uri
    }

    @Test
    fun `a uri without a host is not looked up at all`() {
        Mockito.mockStatic(InetAddress::class.java).use { lookup ->
            prime(uriWithHost(null))

            lookup.verifyNoInteractions()
        }
    }

    @Test
    fun `a blank host is not looked up at all`() {
        Mockito.mockStatic(InetAddress::class.java).use { lookup ->
            prime(uriWithHost("   "))

            lookup.verifyNoInteractions()
        }
    }

    @Test
    fun `an unclosed bracket is kept as the lookup name`() {
        Mockito.mockStatic(InetAddress::class.java).use { lookup ->
            prime(uriWithHost("[::1"))

            lookup.verify { InetAddress.getAllByName("[::1") }
        }
    }

    @Test
    fun `a resolver failure without a message reports the host as unknown`() {
        Mockito.mockStatic(InetAddress::class.java).use { lookup ->
            lookup
                .`when`<Array<InetAddress>>({ InetAddress.getAllByName("broken.internal") })
                .thenThrow(UnknownHostException())

            val failure = assertThrows(IOException::class.java) { prime(uriWithHost("broken.internal")) }

            assertTrue(failure.message!!.endsWith("unknown host"), failure.message)
        }
    }
}
