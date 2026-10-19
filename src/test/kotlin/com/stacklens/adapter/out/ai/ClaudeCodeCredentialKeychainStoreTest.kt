package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.application.port.out.process.ProcessResult
import com.stacklens.application.port.out.process.ProcessRunPort
import com.stacklens.domain.model.ClaudeCodeCredential
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.security.MessageDigest

class ClaudeCodeCredentialKeychainStoreTest {
    private val process: ProcessRunPort = mock()

    private val mapper = ObjectMapper()

    private val store =
        ClaudeCodeCredentialKeychainStore(
            process = process,
            codec = ClaudeCodeCredentialCodec(mapper),
            osNameProvider = { "Mac OS X" },
            envReader = { null },
            userNameProvider = { "tester" },
        )

    private fun storeFor(
        osName: String = "Mac OS X",
        env: (String) -> String? = { null },
        user: () -> String? = { "tester" },
    ) = ClaudeCodeCredentialKeychainStore(process, ClaudeCodeCredentialCodec(mapper), { osName }, env, user)

    private fun stub(
        find: ProcessResult = ProcessResult(44, "", "sgKeyNotFound"),
        add: ProcessResult = ProcessResult(0, "", ""),
    ) {
        whenever(process.run(any(), any(), anyOrNull()))
            .thenAnswer { invocation ->
                when (invocation.getArgument<List<String>>(0).getOrNull(1)) {
                    ClaudeCodeCredentialKeychainStore.FIND_ARGUMENT -> find
                    else -> add
                }
            }
    }

    @Test
    fun `the store stays silent off macOS`() {
        val linuxStore = storeFor(osName = "Linux")

        assertNull(linuxStore.read())
        linuxStore.write(ClaudeCodeCredential(accessToken = "sk-ant-oat01-x"))

        verifyNoInteractions(process)
    }

    @Test
    fun `reads the credential document out of the keychain item`() {
        stub(find = ProcessResult(0, """{"claudeAiOauth":{"accessToken":"sk-ant-oat01-keychain","refreshToken":"rt","expiresAt":1770000000000,"clientId":"cid"}}""", ""))

        val credential = store.read()

        assertEquals("sk-ant-oat01-keychain", credential?.accessToken)
        assertEquals("rt", credential?.refreshToken)
        assertEquals("cid", credential?.clientId)
    }

    @Test
    fun `a missing item yields nothing`() {
        stub()

        assertNull(store.read())
    }

    @Test
    fun `an unusable item payload yields nothing`() {
        stub(find = ProcessResult(0, "   ", ""))

        assertNull(store.read())
    }

    @Test
    fun `a failing security binary yields nothing`() {
        whenever(process.run(any(), any(), anyOrNull())).thenThrow(IllegalStateException("keychain locked"))

        assertNull(store.read())
        store.write(ClaudeCodeCredential(accessToken = "sk-ant-oat01-x"))
    }

    @Test
    fun `a security binary failure without a message is contained`() {
        whenever(process.run(any(), any(), anyOrNull())).thenThrow(IllegalStateException())

        assertNull(store.read())
        store.write(ClaudeCodeCredential(accessToken = "sk-ant-oat01-x"))
    }

    @Test
    fun `names the item the way the cli does`() {
        assertEquals("Claude Code-credentials", store.serviceName())
        assertEquals(
            listOf("security", "find-generic-password", "-a", "tester", "-w", "-s", "Claude Code-credentials"),
            store.securityFindCommand(),
        )
    }

    @Test
    fun `the source label names the keychain item`() {
        assertEquals("claude-code-macos-keychain", store.sourceLabel)
    }

    @Test
    fun `the built in collaborators follow the host environment`() {
        val host = ClaudeCodeCredentialKeychainStore(process = process, codec = ClaudeCodeCredentialCodec(mapper))
        stub(find = ProcessResult(0, """{"claudeAiOauth":{"accessToken":"sk-ant-oat01-host"}}""", ""))

        assertEquals(listOf("security", ClaudeCodeCredentialKeychainStore.FIND_ARGUMENT), host.securityFindCommand().take(2))
        assertTrue(host.accountName().isNotBlank())
        assertTrue(host.serviceName().startsWith("Claude Code"))
        assertEquals(if (host.isSupported()) "sk-ant-oat01-host" else null, host.read()?.accessToken)
    }

    @Test
    fun `the host account is used when the environment names no user`() {
        val host =
            ClaudeCodeCredentialKeychainStore(
                process = process,
                codec = ClaudeCodeCredentialCodec(mapper),
                envReader = { null },
            )

        assertTrue(Regex("^[a-zA-Z0-9._-]+$").matches(host.accountName()))
    }

    @Test
    fun `a whitespace only secure storage directory keeps the default item name`() {
        val blank =
            storeFor(
                env = { name ->
                    when (name) {
                        ClaudeCodeCredentialKeychainStore.ENV_SECURESTORAGE_CONFIG_DIR -> "   "
                        ClaudeCodeCredentialKeychainStore.ENV_CONFIG_DIR -> "/tmp/claude-profile"
                        else -> null
                    }
                },
            )

        assertEquals("Claude Code-credentials", blank.serviceName())
    }

    @Test
    fun `a blank config directory keeps the default item name`() {
        assertEquals("Claude Code-credentials", storeFor(env = { name -> if (name == ClaudeCodeCredentialKeychainStore.ENV_CONFIG_DIR) "   " else null }).serviceName())
        assertEquals("Claude Code-credentials", storeFor(env = { name -> if (name == ClaudeCodeCredentialKeychainStore.ENV_CONFIG_DIR) "" else null }).serviceName())
    }

    @Test
    fun `a blank environment user falls back to the host account`() {
        assertEquals("tester", storeFor(env = { name -> if (name == ClaudeCodeCredentialKeychainStore.ENV_USER) "" else null }).accountName())
        assertEquals("tester", storeFor(env = { name -> if (name == ClaudeCodeCredentialKeychainStore.ENV_USER) "   " else null }).accountName())
        assertEquals("claude-code-user", storeFor(env = { null }, user = { "   " }).accountName())
        assertEquals("claude-code-user", storeFor(env = { "" }, user = { "" }).accountName())
    }

    @Test
    fun `hashes a non default config directory into the item name`() {
        val custom = storeFor(env = { name -> if (name == ClaudeCodeCredentialKeychainStore.ENV_CONFIG_DIR) "/tmp/claude-profile" else null })

        assertEquals("Claude Code-credentials-${sha256Hex("/tmp/claude-profile").substring(0, 8)}", custom.serviceName())
    }

    @Test
    fun `the secure storage directory is hashed into the item name and wins over the legacy directory`() {
        val secure =
            storeFor(
                env = { name ->
                    when (name) {
                        ClaudeCodeCredentialKeychainStore.ENV_SECURESTORAGE_CONFIG_DIR -> "/tmp/secure"
                        ClaudeCodeCredentialKeychainStore.ENV_CONFIG_DIR -> "/tmp/claude-profile"
                        else -> null
                    }
                },
            )

        assertEquals("Claude Code-credentials-${sha256Hex("/tmp/secure").substring(0, 8)}", secure.serviceName())
    }

    @Test
    fun `an empty secure storage directory keeps the default item name`() {
        val secure =
            storeFor(
                env = { name ->
                    when (name) {
                        ClaudeCodeCredentialKeychainStore.ENV_SECURESTORAGE_CONFIG_DIR -> ""
                        ClaudeCodeCredentialKeychainStore.ENV_CONFIG_DIR -> "/tmp/claude-profile"
                        else -> null
                    }
                },
            )

        assertEquals("Claude Code-credentials", secure.serviceName())
    }

    @Test
    fun `sanitises an account name the security tool cannot use`() {
        assertEquals("claude-code-user", storeFor(user = { "invalid user!" }).accountName())
        assertEquals("claude-code-user", storeFor(user = { null }).accountName())
        assertEquals(
            "explicit-user",
            storeFor(env = { name -> if (name == ClaudeCodeCredentialKeychainStore.ENV_USER) "explicit-user" else null }).accountName(),
        )
    }

    @Test
    fun `writes the rotated pair as hex and keeps unrelated item data`() {
        stub(
            find = ProcessResult(0, """{"other":"kept","claudeAiOauth":{"accessToken":"old","subscriptionType":"max"}}""", ""),
        )

        store.write(ClaudeCodeCredential(accessToken = "sk-ant-oat01-rotated", refreshToken = "rt-2", expiresAtMillis = 1800000000000L))

        val command = addCommand()
        assertEquals(listOf("security", "add-generic-password", "-U", "-a", "tester", "-s", "Claude Code-credentials", "-X"), command.take(8))
        val payload = mapper.readTree(hexDecode(command[8]))
        assertEquals("kept", payload.path("other").asText())
        val oauth = payload.path("claudeAiOauth")
        assertEquals("sk-ant-oat01-rotated", oauth.path("accessToken").asText())
        assertEquals("rt-2", oauth.path("refreshToken").asText())
        assertEquals(1800000000000L, oauth.path("expiresAt").asLong())
        assertEquals("max", oauth.path("subscriptionType").asText())
    }

    @Test
    fun `writes a fresh item when the keychain holds nothing`() {
        stub()

        store.write(ClaudeCodeCredential(accessToken = "fresh"))

        assertEquals("""{"claudeAiOauth":{"accessToken":"fresh"}}""", hexDecode(addCommand()[8]))
    }

    @Test
    fun `a rejected keychain write does not throw`() {
        stub(
            find = ProcessResult(0, """{"claudeAiOauth":{"accessToken":"old"}}""", ""),
            add = ProcessResult(1, "", "authorization denied"),
        )

        store.write(ClaudeCodeCredential(accessToken = "new"))

        assertTrue(addCommand().getOrNull(1) == "add-generic-password")
    }

    private fun addCommand(): List<String> {
        val captor = argumentCaptor<List<String>>()
        verify(process, atLeastOnce()).run(captor.capture(), any(), anyOrNull())
        return captor.allValues.first { it.getOrNull(1) == ClaudeCodeCredentialKeychainStore.ADD_ARGUMENT }
    }

    private fun hexDecode(hex: String): String = String(hex.chunked(2).map { it.toInt(radix = 16).toByte() }.toByteArray(), Charsets.UTF_8)

    private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
