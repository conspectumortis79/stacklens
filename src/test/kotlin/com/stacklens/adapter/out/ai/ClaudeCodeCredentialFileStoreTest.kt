package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.domain.model.ClaudeCodeCredential
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

class ClaudeCodeCredentialFileStoreTest {
    private val mapper = ObjectMapper()

    private val codec = ClaudeCodeCredentialCodec(mapper)

    @TempDir
    lateinit var home: Path

    private fun store(): ClaudeCodeCredentialFileStore = ClaudeCodeCredentialFileStore(homeDir = home, codec = codec)

    private fun writeCredentials(
        relative: String,
        json: String,
    ): Path {
        val target = home.resolve(relative)
        Files.createDirectories(target.parent)
        Files.writeString(target, json)
        return target
    }

    @Test
    fun `returns nothing when no credential file exists`() {
        assertNull(store().read())
    }

    @Test
    fun `reads the primary credentials file`() {
        writeCredentials(".claude/.credentials.json", """{"claudeAiOauth":{"accessToken":"sk-ant-oat01-file","expiresAt":1770000000000}}""")

        val credential = store().read()

        assertEquals("sk-ant-oat01-file", credential?.accessToken)
        assertEquals(1770000000000L, credential?.expiresAtMillis)
    }

    @Test
    fun `falls back to the xdg location when the primary file is unusable`() {
        writeCredentials(".claude/.credentials.json", "{broken")
        writeCredentials(".config/claude-code/auth.json", """{"accessToken":"sk-ant-xdg"}""")

        assertEquals("sk-ant-xdg", store().read()?.accessToken)
    }

    @Test
    fun `prefers the primary location over the xdg location`() {
        writeCredentials(".claude/.credentials.json", """{"accessToken":"sk-ant-primary"}""")
        writeCredentials(".config/claude-code/auth.json", """{"accessToken":"sk-ant-xdg"}""")

        assertEquals("sk-ant-primary", store().read()?.accessToken)
    }

    @Test
    fun `a failing file probe does not break the read`() {
        val store =
            ClaudeCodeCredentialFileStore(homeDir = home, codec = codec, fileProbe = { throw IllegalStateException("probe denied") })

        assertNull(store.read())
    }

    @Test
    fun `the source label names the credential file`() {
        assertEquals("claude-code-credential-file", store().sourceLabel)
    }

    @Test
    fun `a file probe that cannot answer leaves the write untouched`() {
        val store =
            ClaudeCodeCredentialFileStore(
                homeDir = home,
                codec = codec,
                fileProbe = { throw IllegalStateException("probe denied") },
            )

        store.write(ClaudeCodeCredential(accessToken = "cannot-persist"))

        assertTrue(Files.notExists(home.resolve(".claude")))
    }

    @Test
    fun `a home without posix support keeps the credentials readable`() {
        val archive = home.resolve("claude-home.zip")
        FileSystems.newFileSystem(URI.create("jar:${archive.toUri()}"), mapOf("create" to "true")).use { zip ->
            val inside = zip.getPath(ClaudeCodeCredentialFileStore.CREDENTIALS_FILE)
            assertFalse(ClaudeCodeCredentialFileStore.posixPermissionsSupported(inside))

            val store = ClaudeCodeCredentialFileStore(homeDir = zip.getPath("/"), codec = codec)
            assertNull(store.read())

            store.write(ClaudeCodeCredential(accessToken = "sk-ant-oat01-zip", refreshToken = "rt-zip"))

            val stored = store.read()
            assertEquals("sk-ant-oat01-zip", stored?.accessToken)
            assertEquals("rt-zip", stored?.refreshToken)
        }
    }

    @Test
    fun `writes back into the file it read from and keeps sibling data`() {
        val credentials = writeCredentials(".claude/.credentials.json", """{"claudeAiOauth":{"accessToken":"old","subscriptionType":"max"},"other":"kept"}""")

        store().write(ClaudeCodeCredential(accessToken = "sk-ant-oat01-refreshed", refreshToken = "rt-2", expiresAtMillis = 1800000000000L))

        val root = mapper.readTree(Files.readString(credentials))
        val oauth = root.path("claudeAiOauth")
        assertEquals("sk-ant-oat01-refreshed", oauth.path("accessToken").asText())
        assertEquals("rt-2", oauth.path("refreshToken").asText())
        assertEquals(1800000000000L, oauth.path("expiresAt").asLong())
        assertEquals("max", oauth.path("subscriptionType").asText())
        assertEquals("kept", root.path("other").asText())
    }

    @Test
    fun `the refreshed file stays readable for the next lookup`() {
        writeCredentials(".claude/.credentials.json", """{"claudeAiOauth":{"accessToken":"old","refreshToken":"rt"}}""")

        store().write(ClaudeCodeCredential(accessToken = "new", refreshToken = "rt-rotated", expiresAtMillis = 1800000000000L))

        val reread = store().read()
        assertEquals("new", reread?.accessToken)
        assertEquals("rt-rotated", reread?.refreshToken)
    }

    @Test
    fun `creates the primary location when no file exists yet`() {
        store().write(ClaudeCodeCredential(accessToken = "bootstrap"))

        val created = home.resolve(".claude").resolve(".credentials.json")
        assertTrue(Files.exists(created))
        assertEquals(
            "bootstrap",
            mapper
                .readTree(Files.readString(created))
                .path("claudeAiOauth")
                .path("accessToken")
                .asText(),
        )
    }

    @Test
    fun `restricts a written credentials file to its owner on posix filesystems`() {
        assumeTrue(ClaudeCodeCredentialFileStore.posixPermissionsSupported(home), "posix permissions are not supported on this filesystem")
        writeCredentials(".claude/.credentials.json", """{"claudeAiOauth":{"accessToken":"old"}}""")

        store().write(ClaudeCodeCredential(accessToken = "secret"))

        val permissions = Files.getPosixFilePermissions(home.resolve(".claude").resolve(".credentials.json"))
        assertEquals(setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), permissions)
    }

    @Test
    fun `an uncreatable credential directory is read as absent and written silently`() {
        Files.writeString(home.resolve(".claude"), "not a directory")

        assertNull(store().read())
        store().write(ClaudeCodeCredential(accessToken = "cannot-persist"))

        assertEquals("not a directory", Files.readString(home.resolve(".claude")))
    }
}
