package com.stacklens.adapter.out.ai

import com.stacklens.application.port.out.ai.ClaudeCodeCredentialStorePort
import com.stacklens.domain.model.ClaudeCodeCredential
import com.stacklens.domain.util.closingAuto
import com.stacklens.domain.util.exceptionDiagnostic
import com.stacklens.domain.util.quietly
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission

class ClaudeCodeCredentialFileStore(
    private val homeDir: Path = Path.of(System.getProperty("user.home")),
    private val codec: ClaudeCodeCredentialCodec,
    private val fileProbe: FileProbe = DefaultClaudeCredentialFileProbe,
) : ClaudeCodeCredentialStorePort {
    private val log = LoggerFactory.getLogger(ClaudeCodeCredentialFileStore::class.java)

    override val sourceLabel: String = SOURCE_LABEL

    override fun read(): ClaudeCodeCredential? =
        credentialLocations().firstNotNullOfOrNull { location ->
            runCatching { readLocation(location) }
                .getOrElse { e ->
                    log.warn("Failed to read Claude Code credentials at {}: {}", location.file, exceptionDiagnostic(e))
                    null
                }
        }

    override fun write(credential: ClaudeCodeCredential) {
        val location = writableLocation() ?: return
        try {
            val merged = codec.encode(readText(location.file), credential)
            val temp = Files.createTempFile(location.directory, TEMP_PREFIX, TEMP_SUFFIX)
            try {
                Files.writeString(temp, merged)
                applyOwnerOnlyPermissions(temp)
                Files.move(temp, location.file, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                quietly { Files.deleteIfExists(temp) }
            }
        } catch (e: Exception) {
            log.warn("Failed to persist refreshed Claude Code credentials at {}: {}", location.file, exceptionDiagnostic(e))
        }
    }

    internal fun credentialLocations(): List<CredentialLocation> =
        listOf(
            CredentialLocation(homeDir.resolve(DOT_CLAUDE), CREDENTIALS_FILE),
            CredentialLocation(homeDir.resolve(DOT_CONFIG).resolve(CLAUDE_CODE_DIR), AUTH_FILE),
        )

    private fun readLocation(location: CredentialLocation): ClaudeCodeCredential? {
        if (!fileProbe.exists(location.file)) return null
        val credential = codec.decode(readText(location.file))
        if (credential == null) {
            log.warn("Claude Code credentials at {} contain no usable access token", location.file)
        }
        return credential
    }

    private fun readText(location: Path): String? =
        try {
            Files.newBufferedReader(location).closingAuto { it.readText() }
        } catch (e: Exception) {
            log.warn("Failed to read Claude Code credentials at {}: {}", location, exceptionDiagnostic(e))
            null
        }

    private fun writableLocation(): CredentialLocation? {
        val existing = credentialLocations().firstOrNull { location -> existsSafely(location.file) }
        if (existing != null) return existing
        val primary = credentialLocations().first()
        return try {
            if (!fileProbe.exists(primary.directory)) Files.createDirectories(primary.directory)
            primary
        } catch (e: Exception) {
            log.warn("Failed to prepare Claude Code credential directory {}: {}", primary.directory, exceptionDiagnostic(e))
            null
        }
    }

    private fun existsSafely(path: Path): Boolean = runCatching { fileProbe.exists(path) }.getOrDefault(false)

    private fun applyOwnerOnlyPermissions(path: Path) {
        if (!posixPermissionsSupported(path)) return
        quietly {
            Files.setPosixFilePermissions(
                path,
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        }
    }

    fun interface FileProbe {
        fun exists(path: Path): Boolean
    }

    internal object DefaultClaudeCredentialFileProbe : FileProbe {
        override fun exists(path: Path): Boolean = Files.exists(path)
    }

    internal class CredentialLocation(
        val directory: Path,
        fileName: String,
    ) {
        val file: Path = directory.resolve(fileName)
    }

    companion object {
        const val SOURCE_LABEL: String = "claude-code-credential-file"

        const val CREDENTIALS_FILE: String = ".credentials.json"

        const val AUTH_FILE: String = "auth.json"

        private const val DOT_CLAUDE = ".claude"

        private const val DOT_CONFIG = ".config"

        private const val CLAUDE_CODE_DIR = "claude-code"

        private const val TEMP_PREFIX = ".claude-credentials-"

        private const val TEMP_SUFFIX = ".tmp"

        internal fun posixPermissionsSupported(path: Path): Boolean = path.fileSystem.supportedFileAttributeViews().contains("posix")
    }
}
