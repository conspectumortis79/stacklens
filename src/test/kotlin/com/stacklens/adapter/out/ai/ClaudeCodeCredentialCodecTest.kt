package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.ObjectMapper
import com.stacklens.domain.model.ClaudeCodeCredential
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClaudeCodeCredentialCodecTest {
    private val mapper = ObjectMapper()

    private val codec = ClaudeCodeCredentialCodec(mapper)

    @Test
    fun `decodes the claudeAiOauth block the cli writes`() {
        val credential =
            codec.decode(
                """
                {"claudeAiOauth":{"accessToken":"sk-ant-oat01-live","refreshToken":"sk-ant-ort01-live",
                "expiresAt":1770000000000,"scopes":["user:inference"],"clientId":"9d1c250a","subscriptionType":"max"}}
                """.trimIndent(),
            )

        assertEquals(
            ClaudeCodeCredential(
                accessToken = "sk-ant-oat01-live",
                refreshToken = "sk-ant-ort01-live",
                expiresAtMillis = 1770000000000L,
                scopes = listOf("user:inference"),
                clientId = "9d1c250a",
            ),
            credential,
        )
    }

    @Test
    fun `decodes snake_case keys and a top level token`() {
        assertEquals("sk-ant-snake", codec.decode("""{"claudeAiOauth":{"access_token":"sk-ant-snake"}}""")?.accessToken)
        assertEquals("sk-ant-top", codec.decode("""{"accessToken":"sk-ant-top"}""")?.accessToken)
        assertEquals("sk-ant-plain", codec.decode("""{"token":"sk-ant-plain"}""")?.accessToken)
        assertEquals("sk-ant-oauth-block", codec.decode("""{"oauth":{"accessToken":"sk-ant-oauth-block"}}""")?.accessToken)
    }

    @Test
    fun `decodes a space separated scope string`() {
        val credential = codec.decode("""{"claudeAiOauth":{"accessToken":"t","scope":null,"scopes":"user:inference user:profile"}}""")

        assertEquals(listOf("user:inference", "user:profile"), credential?.scopes)
    }

    @Test
    fun `normalises a second based expiry to milliseconds`() {
        val credential = codec.decode("""{"claudeAiOauth":{"accessToken":"t","expiresAt":1770000000}}""")

        assertEquals(1770000000000L, credential?.expiresAtMillis)
    }

    @Test
    fun `treats a textual and a non positive expiry as absent`() {
        assertEquals(1770000000000L, codec.decode("""{"claudeAiOauth":{"accessToken":"t","expiresAt":"1770000000000"}}""")?.expiresAtMillis)
        assertNull(codec.decode("""{"claudeAiOauth":{"accessToken":"t","expiresAt":0}}""")?.expiresAtMillis)
        assertNull(codec.decode("""{"claudeAiOauth":{"accessToken":"t"}}""")?.expiresAtMillis)
    }

    @Test
    fun `an expiry that is neither numeric nor a number word is absent`() {
        assertNull(codec.decode("""{"accessToken":"t","expiresAt":"soon"}""")?.expiresAtMillis)
        assertNull(codec.decode("""{"accessToken":"t","expiresAt":true}""")?.expiresAtMillis)
        assertNull(codec.decode("""{"accessToken":"t","expiresAt":{"days":1}}""")?.expiresAtMillis)
        assertNull(codec.decode("""{"accessToken":"t","expiresAt":[1]}""")?.expiresAtMillis)
    }

    @Test
    fun `drops blank entries from a scope list`() {
        val scopes = codec.decode("""{"accessToken":"t","scopes":["user:inference","","   "]}""")?.scopes

        assertEquals(listOf("user:inference"), scopes)
    }

    @Test
    fun `drops blank entries from a scope string`() {
        assertEquals(listOf("user:inference"), codec.decode("""{"accessToken":"t","scopes":"  user:inference   "}""")?.scopes)
    }

    @Test
    fun `a scope value that is neither a list nor a text is ignored`() {
        assertEquals(emptyList<String>(), codec.decode("""{"accessToken":"t","scopes":42}""")?.scopes)
        assertEquals(emptyList<String>(), codec.decode("""{"accessToken":"t","scopes":{"one":1}}""")?.scopes)
        assertEquals(emptyList<String>(), codec.decode("""{"accessToken":"t","scopes":null}""")?.scopes)
    }

    @Test
    fun `rejects unusable documents`() {
        assertNull(codec.decode(null))
        assertNull(codec.decode("   "))
        assertNull(codec.decode("not json"))
        assertNull(codec.decode("[]"))
        assertNull(codec.decode("""{"claudeAiOauth":{"accessToken":""}}"""))
        assertNull(codec.decode("""{"claudeAiOauth":{"accessToken":42}}"""))
    }

    @Test
    fun `prefers the oauth block over a top level token`() {
        val credential = codec.decode("""{"accessToken":"sk-ant-top","claudeAiOauth":{"accessToken":"sk-ant-oauth"}}""")

        assertEquals("sk-ant-oauth", credential?.accessToken)
    }

    @Test
    fun `encodes into claudeAiOauth and keeps unrelated data`() {
        val encoded =
            codec.encode(
                """
                {"other":"kept","claudeAiOauth":{"accessToken":"old","refreshToken":"old-refresh",
                "subscriptionType":"max","rateLimitTier":"tier1"},"mcpXaaIdp":{}}
                """.trimIndent(),
                ClaudeCodeCredential(
                    accessToken = "new",
                    refreshToken = "new-refresh",
                    expiresAtMillis = 42L,
                    scopes = listOf("user:inference"),
                    clientId = "client-1",
                ),
            )

        val root = mapper.readTree(encoded)
        assertEquals("kept", root.path("other").asText())
        assertTrue(root.has("mcpXaaIdp"))
        val oauth = root.path("claudeAiOauth")
        assertEquals("new", oauth.path("accessToken").asText())
        assertEquals("new-refresh", oauth.path("refreshToken").asText())
        assertEquals(42L, oauth.path("expiresAt").asLong())
        assertEquals("client-1", oauth.path("clientId").asText())
        assertEquals("max", oauth.path("subscriptionType").asText())
        assertEquals("tier1", oauth.path("rateLimitTier").asText())
        assertEquals("user:inference", oauth.path("scopes").get(0).asText())
    }

    @Test
    fun `encodes a fresh document when nothing was stored`() {
        val encoded = codec.encode(null, ClaudeCodeCredential(accessToken = "only-access"))

        val oauth = mapper.readTree(encoded).path("claudeAiOauth")
        assertEquals("only-access", oauth.path("accessToken").asText())
        assertTrue(oauth.path("refreshToken").isMissingNode)
        assertTrue(oauth.path("expiresAt").isMissingNode)
        assertTrue(oauth.path("scopes").isMissingNode)
    }

    @Test
    fun `encodes over a corrupt document without losing the new token`() {
        val encoded = codec.encode("{oops", ClaudeCodeCredential(accessToken = "rescued"))

        assertEquals(
            "rescued",
            mapper
                .readTree(encoded)
                .path("claudeAiOauth")
                .path("accessToken")
                .asText(),
        )
    }

    @Test
    fun `encodes over a blank document without losing the new token`() {
        val encoded = codec.encode("   \t ", ClaudeCodeCredential(accessToken = "blank-source"))

        assertEquals(
            "blank-source",
            mapper
                .readTree(encoded)
                .path("claudeAiOauth")
                .path("accessToken")
                .asText(),
        )
    }

    @Test
    fun `encodes over a json document that holds no object`() {
        val credential = ClaudeCodeCredential(accessToken = "not-an-object")

        listOf("[]", "7", "\"text\"", "true").forEach { json ->
            val encoded = codec.encode(json, credential)

            assertEquals(
                "not-an-object",
                mapper
                    .readTree(encoded)
                    .path("claudeAiOauth")
                    .path("accessToken")
                    .asText(),
                "failed for $json",
            )
        }
    }

    @Test
    fun `replaces an oauth block that is not an object`() {
        val encoded = codec.encode("""{"claudeAiOauth":"legacy","other":"kept"}""", ClaudeCodeCredential(accessToken = "replaced"))

        val root = mapper.readTree(encoded)
        assertEquals("replaced", root.path("claudeAiOauth").path("accessToken").asText())
        assertEquals("kept", root.path("other").asText())
    }
}
