package com.stacklens.adapter.out.ai

import com.stacklens.domain.model.ClaudeCodeCredential
import com.stacklens.testsupport.FixedClockPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClaudeCodeCredentialProviderTest {
    private val clock = FixedClockPort(1_000_000L)

    private val refreshPort = StubOAuthRefreshPort()

    private val keychain = RecordingCredentialStore(sourceLabel = "keychain")

    private val file = RecordingCredentialStore(sourceLabel = "file")

    private fun provider(
        skewMillis: Long = 300_000L,
        cooldownMillis: Long = 300_000L,
    ) = ClaudeCodeCredentialProvider(
        stores = listOf(file, keychain),
        refreshPort = refreshPort,
        clock = clock,
        expirySkewMillis = skewMillis,
        refreshCooldownMillis = cooldownMillis,
    )

    private fun credential(
        token: String,
        expiresInMillis: Long?,
        refreshToken: String? = "rt",
    ) = ClaudeCodeCredential(
        accessToken = token,
        refreshToken = refreshToken,
        expiresAtMillis = expiresInMillis?.let { clock.epochMillis() + it },
    )

    @Test
    fun `a token that is not due for refresh is handed out untouched`() {
        file.seed(credential("sk-ant-oat01-valid", 600_000L))

        assertEquals("sk-ant-oat01-valid", provider().accessToken())
        assertEquals(0, refreshPort.calls)
        assertEquals(emptyList<ClaudeCodeCredential>(), file.written)
    }

    @Test
    fun `a token without an expiry is treated as long lived`() {
        file.seed(ClaudeCodeCredential(accessToken = "sk-ant-oat01-setup-token"))

        assertEquals("sk-ant-oat01-setup-token", provider().accessToken())
        assertEquals(0, refreshPort.calls)
    }

    @Test
    fun `a token inside the expiry window is refreshed and written back to the same store`() {
        file.seed(credential("sk-ant-oat01-old", 60_000L))
        refreshPort.respondWith(credential("sk-ant-oat01-new", 3_600_000L, refreshToken = "rt-rotated"))

        assertEquals("sk-ant-oat01-new", provider().accessToken())
        assertEquals("sk-ant-oat01-old", refreshPort.requestedCredentials.single().accessToken)
        assertEquals("sk-ant-oat01-new", file.written.single().accessToken)
        assertEquals("rt-rotated", file.written.single().refreshToken)
        assertEquals("sk-ant-oat01-new", file.read()?.accessToken)
        assertEquals(0, keychain.readCount)
    }

    @Test
    fun `the refreshed token is taken from the store on the next call`() {
        file.seed(credential("sk-ant-oat01-old", 60_000L))
        refreshPort.respondWith(credential("sk-ant-oat01-new", 3_600_000L))
        val provider = provider()

        assertEquals("sk-ant-oat01-new", provider.accessToken())
        assertEquals("sk-ant-oat01-new", provider.accessToken())

        assertEquals(1, refreshPort.calls)
    }

    @Test
    fun `an expired token without a refresh token is skipped`() {
        file.seed(credential("sk-ant-oat01-dead", -1_000L, refreshToken = null))
        keychain.seed(credential("sk-ant-oat01-keychain", 3_600_000L))

        assertEquals("sk-ant-oat01-keychain", provider().accessToken())
        assertEquals(0, refreshPort.calls)
    }

    @Test
    fun `a failed refresh falls through to the next store`() {
        file.seed(credential("sk-ant-oat01-old", -1_000L))
        keychain.seed(credential("sk-ant-oat01-keychain", 3_600_000L))
        refreshPort.respondWith(null)

        assertEquals("sk-ant-oat01-keychain", provider().accessToken())
        assertTrue(file.written.isEmpty())
    }

    @Test
    fun `a store that cannot be read does not break the chain`() {
        file.failRead(IllegalStateException("keychain locked"))
        keychain.seed(credential("sk-ant-oat01-keychain", 3_600_000L))

        assertEquals("sk-ant-oat01-keychain", provider().accessToken())
    }

    @Test
    fun `an empty access token in a store is ignored`() {
        file.seed(ClaudeCodeCredential(accessToken = "   "))
        keychain.seed(credential("sk-ant-oat01-keychain", 3_600_000L))

        assertEquals("sk-ant-oat01-keychain", provider().accessToken())
    }

    @Test
    fun `nothing is returned when no store has a token`() {
        assertNull(provider().accessToken())
        assertEquals(0, refreshPort.calls)
    }

    @Test
    fun `a failed refresh is retried only after the cooldown`() {
        file.seed(credential("sk-ant-oat01-old", -1_000L))
        val provider = provider(cooldownMillis = 60_000L)

        assertNull(provider.accessToken())
        assertNull(provider.accessToken())
        assertEquals(1, refreshPort.calls)

        clock.advanceMillis(60_001L)
        assertNull(provider.accessToken())
        assertEquals(2, refreshPort.calls)
    }

    @Test
    fun `a throwing refresh port does not escape`() {
        file.seed(credential("sk-ant-oat01-old", -1_000L))
        refreshPort.failWith(IllegalStateException("token endpoint unreachable"))

        assertNull(provider().accessToken())
    }

    @Test
    fun `a refresh failure in one store does not suppress the next store`() {
        file.seed(credential("sk-ant-oat01-file-old", -1_000L))
        keychain.seed(credential("sk-ant-oat01-keychain-old", -1_000L))
        refreshPort.respondWith(null)

        assertNull(provider().accessToken())
        assertEquals(
            listOf("sk-ant-oat01-file-old", "sk-ant-oat01-keychain-old"),
            refreshPort.requestedCredentials.map { it.accessToken },
        )
    }

    @Test
    fun `a refresh result without an access token is rejected`() {
        file.seed(credential("sk-ant-oat01-old", -1_000L))
        refreshPort.respondWith(ClaudeCodeCredential(accessToken = " "))

        assertNull(provider().accessToken())
        assertEquals(emptyList<ClaudeCodeCredential>(), file.written)
    }
}
