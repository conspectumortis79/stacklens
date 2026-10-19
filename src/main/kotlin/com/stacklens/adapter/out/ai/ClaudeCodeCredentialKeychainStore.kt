package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.ai.ClaudeCodeCredentialStorePort
import com.stacklens.application.port.out.platform.OsType
import com.stacklens.application.port.out.process.ProcessRunPort
import com.stacklens.domain.model.ClaudeCodeCredential
import com.stacklens.domain.util.exceptionDiagnostic
import org.slf4j.LoggerFactory
import java.security.MessageDigest

class ClaudeCodeCredentialKeychainStore(
    private val process: ProcessRunPort,
    private val codec: ClaudeCodeCredentialCodec,
    private val osNameProvider: () -> String = { System.getProperty("os.name", "") },
    private val envReader: (String) -> String? = { name -> System.getenv(name) },
    private val userNameProvider: () -> String? = { System.getProperty("user.name") },
    private val timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
) : ClaudeCodeCredentialStorePort {
    private val log = LoggerFactory.getLogger(ClaudeCodeCredentialKeychainStore::class.java)

    override val sourceLabel: String = SOURCE_LABEL

    override fun read(): ClaudeCodeCredential? {
        if (!isSupported()) return null
        val result = readItem() ?: return null
        if (!result.success) {
            log.debug("Claude Code keychain item is not readable (exit {})", result.exitCode)
            return null
        }
        return codec.decode(result.stdout.trim())
    }

    override fun write(credential: ClaudeCodeCredential) {
        if (!isSupported()) return
        val encoded = codec.encode(readItem()?.takeIf { it.success }?.stdout?.trim(), credential)
        val result =
            runCatching { process.run(securityAddCommand(hexEncode(encoded)), timeoutSeconds) }
                .getOrElse { e ->
                    log.warn("Claude Code keychain write failed: {}", exceptionDiagnostic(e))
                    return
                }
        if (!result.success) {
            log.warn("Claude Code keychain write rejected (exit {}): {}", result.exitCode, result.stderr.trim().take(MAX_LOG_CHARS))
        }
    }

    internal fun isSupported(): Boolean = OsType.fromOsName(osNameProvider()) == OsType.MACOS

    internal fun serviceName(): String {
        val secureStorageDir = envReader(ENV_SECURESTORAGE_CONFIG_DIR)
        val hashedDir =
            when {
                secureStorageDir != null -> secureStorageDir.takeIf { it.isNotBlank() }
                else -> envReader(ENV_CONFIG_DIR)?.takeIf { it.isNotBlank() }
            }
        val suffix = hashedDir?.let { "-${sha256Hex(it).substring(0, HASH_PREFIX_LENGTH)}" } ?: ""
        return SERVICE_PREFIX + CREDENTIALS_SUFFIX + suffix
    }

    internal fun accountName(): String {
        val raw = envReader(ENV_USER)?.takeIf { it.isNotBlank() } ?: userNameProvider()?.takeIf { it.isNotBlank() } ?: FALLBACK_ACCOUNT
        return if (VALID_ACCOUNT.matches(raw)) raw else FALLBACK_ACCOUNT
    }

    internal fun securityFindCommand(): List<String> = listOf(SECURITY_BINARY, FIND_ARGUMENT, "-a", accountName(), "-w", "-s", serviceName())

    internal fun securityAddCommand(hexSecret: String): List<String> = listOf(SECURITY_BINARY, ADD_ARGUMENT, "-U", "-a", accountName(), "-s", serviceName(), "-X", hexSecret)

    private fun readItem() =
        runCatching { process.run(securityFindCommand(), timeoutSeconds) }
            .onFailure { e -> log.debug("Claude Code keychain read failed: {}", exceptionDiagnostic(e)) }
            .getOrNull()

    private fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).toHex()

    private fun hexEncode(value: String): String = value.toByteArray(Charsets.UTF_8).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        const val SOURCE_LABEL: String = "claude-code-macos-keychain"

        const val SECURITY_BINARY: String = "security"

        const val FIND_ARGUMENT: String = "find-generic-password"

        const val ADD_ARGUMENT: String = "add-generic-password"

        const val SERVICE_PREFIX: String = "Claude Code"

        const val CREDENTIALS_SUFFIX: String = "-credentials"

        const val ENV_CONFIG_DIR: String = "CLAUDE_CONFIG_DIR"

        const val ENV_SECURESTORAGE_CONFIG_DIR: String = "CLAUDE_SECURESTORAGE_CONFIG_DIR"

        const val ENV_USER: String = "USER"

        private const val FALLBACK_ACCOUNT = "claude-code-user"

        private const val HASH_PREFIX_LENGTH = 8

        private const val MAX_LOG_CHARS = 200

        private const val DEFAULT_TIMEOUT_SECONDS = 5L

        private val VALID_ACCOUNT = Regex("^[a-zA-Z0-9._-]+$")
    }
}
