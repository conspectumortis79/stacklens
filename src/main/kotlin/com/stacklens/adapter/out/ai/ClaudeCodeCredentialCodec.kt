package com.stacklens.adapter.out.ai

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.stacklens.domain.model.ClaudeCodeCredential

class ClaudeCodeCredentialCodec(
    private val objectMapper: ObjectMapper,
) {
    fun decode(json: String?): ClaudeCodeCredential? {
        if (json.isNullOrBlank()) return null
        val root =
            runCatching { objectMapper.readTree(json) }
                .getOrNull()
                ?.takeIf { it.isObject }
                ?: return null
        return listOfNotNull(root.get(CLAUDE_AI_OAUTH), root.get(OAUTH), root)
            .filter { it.isObject }
            .firstNotNullOfOrNull { decodeContainer(it) }
    }

    fun encode(
        existingJson: String?,
        credential: ClaudeCodeCredential,
    ): String {
        val root = parseObject(existingJson)
        val oauth =
            root.get(CLAUDE_AI_OAUTH)?.takeIf { it is ObjectNode }
                ?: root.putObject(CLAUDE_AI_OAUTH)
        oauth as ObjectNode
        oauth.put("accessToken", credential.accessToken)
        credential.refreshToken?.let { oauth.put("refreshToken", it) }
        credential.expiresAtMillis?.let { oauth.put("expiresAt", it) }
        credential.clientId?.let { oauth.put("clientId", it) }
        if (credential.scopes.isNotEmpty()) {
            val scopes = oauth.putArray("scopes")
            credential.scopes.forEach { scopes.add(it) }
        }
        return objectMapper.writeValueAsString(root)
    }

    internal fun parseObject(json: String?): ObjectNode =
        json
            ?.takeIf { it.isNotBlank() }
            ?.let {
                runCatching { objectMapper.readTree(it) }
                    .getOrNull()
            }?.takeIf { it.isObject }
            ?.let { it as ObjectNode }
            ?: objectMapper.createObjectNode()

    private fun decodeContainer(container: JsonNode): ClaudeCodeCredential? {
        val accessToken = text(container, "accessToken", "access_token", "token") ?: return null
        return ClaudeCodeCredential(
            accessToken = accessToken,
            refreshToken = text(container, "refreshToken", "refresh_token"),
            expiresAtMillis = epochMillis(container.get("expiresAt") ?: container.get("expires_at")),
            scopes = scopes(container.get("scopes")),
            clientId = text(container, "clientId", "client_id"),
        )
    }

    private fun text(
        container: JsonNode,
        vararg keys: String,
    ): String? =
        keys
            .asSequence()
            .map { container.get(it) }
            .filterNotNull()
            .mapNotNull { node -> node.takeIf { it.isTextual }?.asText()?.trim() }
            .firstOrNull { it.isNotEmpty() }

    private fun scopes(node: JsonNode?): List<String> =
        when {
            node == null -> emptyList()
            node.isArray -> node.map { it.asText().trim() }.filter { it.isNotEmpty() }
            node.isTextual ->
                node
                    .asText()
                    .split(' ')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            else -> emptyList()
        }

    private fun epochMillis(node: JsonNode?): Long? {
        val raw =
            when {
                node == null -> null
                node.isNumber -> node.asLong()
                node.isTextual -> node.asText().trim().toLongOrNull()
                else -> null
            } ?: return null
        if (raw <= 0L) return null
        return if (raw < SECONDS_EPOCH_CUTOFF) raw * 1000L else raw
    }

    companion object {
        const val CLAUDE_AI_OAUTH: String = "claudeAiOauth"

        const val OAUTH: String = "oauth"

        private const val SECONDS_EPOCH_CUTOFF: Long = 100_000_000_000L
    }
}
