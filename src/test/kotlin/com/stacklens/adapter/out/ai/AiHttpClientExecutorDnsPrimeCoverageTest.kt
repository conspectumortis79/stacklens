package com.stacklens.adapter.out.ai

import com.stacklens.testsupport.PrivateInvocation
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.URI

class AiHttpClientExecutorDnsPrimeCoverageTest {
    private val executor = AiHttpClientExecutor()

    @Test
    fun `a bracketed ipv6 host is unwrapped before the lookup`() {
        PrivateInvocation.invoke(executor, "primeDnsCache", URI("http://[::1]:8080/v1/models"))
    }

    @Test
    fun `a single character host skips the bracket unwrapping`() {
        assertThrows(IOException::class.java) {
            PrivateInvocation.invoke(executor, "primeDnsCache", URI("http://a/"))
        }
    }

    @Test
    fun `a host that cannot be resolved surfaces a dns failure`() {
        val failure =
            assertThrows(IOException::class.java) {
                PrivateInvocation.invoke(executor, "primeDnsCache", URI("http://stacklens-unresolvable.invalid:80/x"))
            }

        assertTrue(failure.message.orEmpty().startsWith("DNS pre-resolution failed"), "unexpected: ${failure.message}")
    }
}
