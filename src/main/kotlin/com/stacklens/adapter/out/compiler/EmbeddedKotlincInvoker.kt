package com.stacklens.adapter.out.compiler

import com.stacklens.application.port.out.i18n.I18nPort
import com.stacklens.shared.ProcessUtils
import com.stacklens.shared.utf8Reader
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class EmbeddedKotlincInvoker(
    private val i18n: I18nPort,
    private val kotlincResolver: KotlincResolver,
    private val fatJarBuilder: FatJarBuilder,
    private val javaExecutableProvider: () -> String,
    private val compileTimeoutSeconds: Long = 180L,
    internal val jarLocator: KotlinCompilerJarLocator =
        KotlinCompilerJarLocator.default(FatJarBuilder()),
    internal val runtimeJarLocator: KotlinRuntimeJarLocator =
        KotlinRuntimeJarLocator.default(),
    private val outputReader: ProcessOutputReader = ProcessOutputReader(),
    private val compilerAnchorClass: Class<*> = K2JVMCompiler::class.java,
) {
    constructor(
        i18n: I18nPort,
        kotlincResolver: KotlincResolver,
        fatJarBuilder: FatJarBuilder,
        javaExecutableProvider: () -> String,
    ) : this(
        i18n = i18n,
        kotlincResolver = kotlincResolver,
        fatJarBuilder = fatJarBuilder,
        javaExecutableProvider = javaExecutableProvider,
        jarLocator = KotlinCompilerJarLocator.default(fatJarBuilder),
        runtimeJarLocator = KotlinRuntimeJarLocator.default(),
    )

    private val log = LoggerFactory.getLogger(EmbeddedKotlincInvoker::class.java)

    fun findKotlinCompilerJar(): String? {
        if (cachedCompilerJarResolved) return cachedCompilerJarPath
        cachedCompilerJarPath = jarLocator.locate()
        cachedCompilerJarResolved = true
        return cachedCompilerJarPath
    }

    @Volatile private var cachedCompilerJarPath: String? = null

    @Volatile private var cachedCompilerJarResolved = false

    fun compileWithExternalKotlinc(
        kotlinc: String,
        jarFile: File,
        sourceFile: File,
        workDir: File,
    ) {
        val pb =
            ProcessBuilder(
                kotlinc,
                "-include-runtime",
                "-d",
                jarFile.absolutePath,
                sourceFile.absolutePath,
            ).directory(workDir)
                .redirectErrorStream(true)
        kotlincResolver.injectJavaHome(pb)
        val process = pb.start()

        val outputDrain = drainStdoutAsync(process)
        val finished = process.waitFor(compileTimeoutSeconds, TimeUnit.SECONDS)

        if (!finished) {
            throw IllegalStateException(timeoutMessage(stopAndCollect(process, outputDrain)))
        }
        val output = outputReader.read(outputDrain.future).ifEmpty { outputDrain.snapshot() }
        if (process.exitValue() != 0) {
            throw IllegalStateException(i18n.t("tooling.be_compile_error", process.exitValue(), output))
        }
    }

    fun compileWithEmbeddedCompiler(
        jarFile: File,
        sourceFile: File,
    ) {
        val compilerJar = findKotlinCompilerJar()
        val runtimeJars = findKotlinRuntimeJars()

        if (compilerJar != null) {
            log.info("Kompiliere via kotlin-compiler-embeddable JAR subprocess: {}", compilerJar)
            runSubprocessCompile(jarFile, sourceFile, compilerJar, runtimeJars)
        } else {
            log.warn("kotlin-compiler-embeddable JAR not found – trying in-process compiler")
            runInProcessCompile(jarFile, sourceFile, runtimeJars.find { it.endsWith("kotlin-stdlib.jar") || it.substringBeforeLast("/").contains("kotlin-stdlib") })
        }
    }

    fun findKotlinRuntimeJars(): List<String> {
        if (cachedRuntimeJarsResolved) return cachedRuntimeJars
        cachedRuntimeJars = runtimeJarLocator.locateAll()
        cachedRuntimeJarsResolved = true
        return cachedRuntimeJars
    }

    @Volatile private var cachedRuntimeJars: List<String> = emptyList()

    @Volatile private var cachedRuntimeJarsResolved = false

    private fun runSubprocessCompile(
        jarFile: File,
        sourceFile: File,
        compilerJar: String,
        runtimeJars: List<String>,
    ) {
        val classesDir = Files.createTempDirectory("stacklens-cls-").toFile()
        try {
            val cmd = buildSubprocessCommand(sourceFile, compilerJar, runtimeJars, classesDir)
            val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
            val outputDrain = drainStdoutAsync(process)
            val finished = process.waitFor(compileTimeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                throw IllegalStateException(timeoutMessage(stopAndCollect(process, outputDrain)))
            }
            val output = outputReader.read(outputDrain.future).ifEmpty { outputDrain.snapshot() }
            if (process.exitValue() != 0) {
                throw IllegalStateException(i18n.t("tooling.be_compile_error", process.exitValue(), output))
            }
            val stdlibForFatJar = runtimeJars.firstOrNull { it.contains("kotlin-stdlib") }
            fatJarBuilder.buildFatJar(jarFile, classesDir, stdlibForFatJar?.let { File(it) })
        } finally {
            classesDir.deleteRecursively()
        }
    }

    private fun buildSubprocessCommand(
        sourceFile: File,
        compilerJar: String,
        runtimeJars: List<String>,
        classesDir: File,
    ): List<String> =
        buildList {
            add(javaExecutableProvider())
            add("--sun-misc-unsafe-memory-access=allow")
            add("-cp")
            add((listOf(compilerJar) + runtimeJars).joinToString(File.pathSeparator))
            add("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
            val stdlibJar = runtimeJars.firstOrNull { it.contains("kotlin-stdlib") }
            if (stdlibJar != null) {
                add("-classpath")
                add(stdlibJar)
            }
            addAll(listOf("-no-stdlib", "-no-reflect"))
            addAll(listOf("-d", classesDir.absolutePath))
            add(sourceFile.absolutePath)
        }

    private fun timeoutMessage(output: String): String {
        val message = i18n.t("tooling.be_compile_timeout", compileTimeoutSeconds)
        return if (output.isEmpty()) message else "$message\n$output"
    }

    private fun stopAndCollect(
        process: Process,
        drain: ProcessOutputDrain,
    ): String {
        ProcessUtils.killProcessTree(process)
        return outputReader.read(drain.future).ifEmpty { drain.snapshot() }
    }

    private fun drainStdoutAsync(process: Process): ProcessOutputDrain = ProcessOutputDrain(process)

    private class ProcessOutputDrain(
        process: Process,
    ) {
        private val captured = StringBuilder()

        val future: java.util.concurrent.CompletableFuture<String> =
            java.util.concurrent.CompletableFuture.supplyAsync {
                process.inputStream.utf8Reader().use { reader ->
                    val chunk = CharArray(8 * 1024)
                    while (true) {
                        val read = reader.read(chunk)
                        if (read < 0) break
                        synchronized(captured) { captured.append(chunk, 0, read) }
                    }
                    snapshot()
                }
            }

        fun snapshot(): String = synchronized(captured) { captured.toString() }
    }

    private fun runInProcessCompile(
        jarFile: File,
        sourceFile: File,
        stdlibJar: String?,
    ) {
        val errStream = ByteArrayOutputStream()
        val compiler = K2JVMCompiler()
        val classesDir = Files.createTempDirectory("stacklens-cls-").toFile()
        try {
            val args = buildInProcessArgs(sourceFile, stdlibJar, classesDir)
            val compilerCl = compilerAnchorClass.classLoader ?: ClassLoader.getSystemClassLoader()
            val origTccl = Thread.currentThread().contextClassLoader
            val exitCode =
                try {
                    Thread.currentThread().contextClassLoader = compilerCl
                    compiler.exec(PrintStream(errStream), *args.toTypedArray())
                } finally {
                    Thread.currentThread().contextClassLoader = origTccl
                }
            val output = errStream.toString(Charsets.UTF_8)
            if (exitCode != ExitCode.OK) {
                throw IllegalStateException(
                    i18n.t("tooling.be_compile_error_embedded", exitCode, output),
                )
            }
            fatJarBuilder.buildFatJar(jarFile, classesDir, stdlibJar?.let { File(it) })
        } finally {
            classesDir.deleteRecursively()
        }
    }

    private fun buildInProcessArgs(
        sourceFile: File,
        stdlibJar: String?,
        classesDir: File,
    ): List<String> =
        buildList {
            if (stdlibJar != null) addAll(listOf("-classpath", stdlibJar))
            addAll(listOf("-no-stdlib", "-no-reflect"))
            addAll(listOf("-d", classesDir.absolutePath))
            add(sourceFile.absolutePath)
        }
}

private typealias ExitCode = org.jetbrains.kotlin.cli.common.ExitCode
