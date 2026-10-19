package com.stacklens.adapter.out.compiler

import com.stacklens.shared.utf8Reader
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

data class KotlincRunResult(
    val exitCode: Int,
    val finished: Boolean,
    val output: String,
)

interface KotlincProcessRunner {
    fun lookupInPath(): String?

    fun runVersionCheck(kotlincPath: String): KotlincRunResult
}

interface KotlincEnvironment {
    fun get(key: String): String?
}

fun interface KotlincCommandLookup {
    fun lookup(command: String): String
}

class ShellKotlincCommandLookup : KotlincCommandLookup {
    override fun lookup(command: String): String {
        val which = ProcessBuilder("/bin/sh", "-lc", "command -v $command").redirectErrorStream(true).start()
        val output =
            which.inputStream
                .utf8Reader()
                .readText()
                .trim()
        which.waitFor(LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return output
    }

    private companion object {
        const val LOOKUP_TIMEOUT_SECONDS = 5L
    }
}

class ProcessOutputReader(
    private val awaitTimeoutSeconds: Long = 1L,
) {
    fun read(output: Future<String>): String = runCatching { output.get(awaitTimeoutSeconds, TimeUnit.SECONDS) }.getOrDefault("")
}

class DefaultKotlincProcessRunner(
    private val versionCheckTimeoutSeconds: Long = 15L,
    private val javaHomeProvider: () -> String? = { System.getProperty("java.home") },
    private val commandLookup: KotlincCommandLookup = ShellKotlincCommandLookup(),
    private val outputReader: ProcessOutputReader = ProcessOutputReader(),
) : KotlincProcessRunner {
    override fun lookupInPath(): String? =
        runCatching {
            val path = commandLookup.lookup("kotlinc")
            if (path.isNotBlank() && File(path).exists()) path else null
        }.getOrNull()

    override fun runVersionCheck(kotlincPath: String): KotlincRunResult {
        val pb = ProcessBuilder(kotlincPath, "-version").redirectErrorStream(true)
        val javaHome = javaHomeProvider()
        if (!javaHome.isNullOrBlank()) pb.environment()["JAVA_HOME"] = javaHome
        val process = pb.start()
        val outputFuture =
            CompletableFuture.supplyAsync {
                process.inputStream.utf8Reader().readText()
            }
        val finished = process.waitFor(versionCheckTimeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return KotlincRunResult(exitCode = -1, finished = false, output = outputReader.read(outputFuture))
        }
        return KotlincRunResult(exitCode = process.exitValue(), finished = true, output = outputReader.read(outputFuture))
    }
}

fun interface KotlincCandidatePaths {
    fun paths(): List<String>
}

class DefaultKotlincCandidatePaths(
    private val userHomeProvider: () -> String = { System.getProperty("user.home") },
) : KotlincCandidatePaths {
    override fun paths(): List<String> =
        listOf(
            File(userHomeProvider(), ".sdkman/candidates/kotlin/current/bin/kotlinc").absolutePath,
            "/opt/homebrew/bin/kotlinc",
            "/usr/local/bin/kotlinc",
            "/usr/bin/kotlinc",
            "/opt/intellij/plugins/Kotlin/kotlinc/bin/kotlinc",
            "/opt/idea/plugins/Kotlin/kotlinc/bin/kotlinc",
            "/snap/intellij-idea-ultimate/current/plugins/Kotlin/kotlinc/bin/kotlinc",
            "/snap/intellij-idea-community/current/plugins/Kotlin/kotlinc/bin/kotlinc",
            File(
                userHomeProvider(),
                ".local/share/JetBrains/Toolbox/apps/intellij-idea-ultimate/plugins/Kotlin/kotlinc/bin/kotlinc",
            ).absolutePath,
            File(
                userHomeProvider(),
                ".local/share/JetBrains/Toolbox/apps/intellij-idea-community/plugins/Kotlin/kotlinc/bin/kotlinc",
            ).absolutePath,
            "/Applications/IntelliJ IDEA.app/Contents/plugins/Kotlin/kotlinc/bin/kotlinc",
            "/Applications/IntelliJ IDEA CE.app/Contents/plugins/Kotlin/kotlinc/bin/kotlinc",
        )
}

class KotlincResolver(
    private val processRunner: KotlincProcessRunner = DefaultKotlincProcessRunner(),
    private val environment: KotlincEnvironment = SystemKotlincEnvironment,
    private val javaHomeProvider: () -> String? = { System.getProperty("java.home") },
    private val candidatePaths: KotlincCandidatePaths = DefaultKotlincCandidatePaths(),
) {
    private val log = LoggerFactory.getLogger(KotlincResolver::class.java)

    @Volatile private var resolved: String? = null

    @Volatile private var didResolve = false

    fun findKotlinc(): String? {
        if (didResolve) return resolved
        val result = resolveKotlinc()
        resolved = result
        didResolve = true
        return result
    }

    private fun resolveKotlinc(): String? {
        val checked = mutableSetOf<String>()

        fun tryPath(path: String): String? {
            val canonical = runCatching { File(path).canonicalPath }.getOrElse { path }
            if (!checked.add(canonical)) return null
            return if (File(canonical).exists() && isKotlincCompatible(canonical)) canonical else null
        }

        environment.get("KOTLINC_PATH")?.let { path ->
            val candidate = File(path)
            if (candidate.exists() && candidate.canExecute()) {
                tryPath(candidate.absolutePath)?.let { return it }
            }
        }

        processRunner.lookupInPath()?.let { path ->
            if (path.isNotBlank()) {
                tryPath(path)?.let { return it }
            }
        }

        environment.get("KOTLIN_HOME")?.let { home ->
            tryPath(File(home, "bin/kotlinc").absolutePath)?.let { return it }
        }

        candidatePaths.paths().forEach { path -> tryPath(path)?.let { return it } }

        return null
    }

    internal fun isKotlincCompatible(kotlincPath: String): Boolean {
        val result =
            runCatching { processRunner.runVersionCheck(kotlincPath) }.getOrElse { e ->
                log.warn("kotlinc compatibility check failed for {}: {}", kotlincPath, e.message)
                return false
            }
        if (!result.finished) {
            log.warn("kotlinc compatibility check timed out for: {}", kotlincPath)
            return false
        }
        if (result.exitCode != 0) {
            log.warn(
                "kotlinc at {} is not compatible with the current JVM (exit {}); falling back to embedded compiler",
                kotlincPath,
                result.exitCode,
            )
        }
        return result.exitCode == 0
    }

    fun injectJavaHome(pb: ProcessBuilder) {
        val javaHome = javaHomeProvider()
        if (!javaHome.isNullOrBlank()) {
            pb.environment()["JAVA_HOME"] = javaHome
        }
    }

    companion object {
        val SystemKotlincEnvironment =
            object : KotlincEnvironment {
                override fun get(key: String): String? = System.getenv(key)
            }
    }
}
