package com.stacklens.adapter.out.compiler

import com.stacklens.domain.util.closingAuto
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.slf4j.LoggerFactory
import java.io.File
import java.io.InputStream
import java.net.URI
import java.net.URL
import java.nio.file.Files

fun interface KotlinCompilerJarStrategy {
    fun locate(): String?
}

interface RuntimeJarStrategy {
    fun locateAll(): List<String>
}

fun interface NestedJarSource {
    fun open(nestedJarUrl: String): InputStream
}

class UrlNestedJarSource : NestedJarSource {
    override fun open(nestedJarUrl: String): InputStream = URI(nestedJarUrl).toURL().openStream()
}

class TempJarWriter {
    fun write(
        tempPrefix: String,
        input: InputStream,
    ): File {
        val tempJar =
            Files
                .createTempFile(tempPrefix, ".jar")
                .toFile()
        tempJar.deleteOnExit()
        input.closingAuto { source -> tempJar.outputStream().closingAuto { sink -> source.copyTo(sink) } }
        return tempJar
    }
}

class NestedJarExtractor(
    private val source: NestedJarSource = UrlNestedJarSource(),
    private val tempJarWriter: TempJarWriter = TempJarWriter(),
) {
    fun extract(
        nestedJarUrl: String,
        tempPrefix: String,
    ): File? = runCatching { tempJarWriter.write(tempPrefix, source.open(nestedJarUrl)) }.getOrNull()
}

class ClasspathJarStrategy(
    private val fatJarBuilder: FatJarBuilder,
) : KotlinCompilerJarStrategy {
    override fun locate(): String? =
        runCatching {
            fatJarBuilder.findJarOnClasspath("kotlin-compiler-embeddable")
        }.getOrNull()
}

class ProtectionDomainJarStrategy(
    private val anchorClass: Class<*> = K2JVMCompiler::class.java,
) : KotlinCompilerJarStrategy {
    override fun locate(): String? =
        runCatching {
            val location =
                anchorClass.protectionDomain
                    .codeSource
                    ?.location ?: return@runCatching null
            val f = File(location.toURI())
            if (f.exists() && f.isFile && f.name.endsWith(".jar")) f.absolutePath else null
        }.getOrNull()
}

class ClassloaderResourceJarStrategy(
    private val compilerClass: Class<*> = K2JVMCompiler::class.java,
    private val nestedJarExtractor: NestedJarExtractor = NestedJarExtractor(),
) : KotlinCompilerJarStrategy {
    override fun locate(): String? =
        runCatching {
            val cl = compilerClass.classLoader ?: return@runCatching null
            val classResource = compilerClass.name.replace('.', '/') + ".class"
            val classUrl = cl.getResource(classResource) ?: return@runCatching null
            parseJarUrl(classUrl)
        }.getOrNull()

    internal fun parseJarUrl(classUrl: URL): String? {
        val urlStr = classUrl.toString()
        val exclamIdx = urlStr.lastIndexOf("!/")
        if (exclamIdx < 0) return null
        val jarUrlStr = urlStr.substring(0, exclamIdx)
        if (jarUrlStr.startsWith("jar:file:")) {
            val path = jarUrlStr.removePrefix("jar:file:")
            val f = File(path)
            return if (f.exists() && f.isFile) f.absolutePath else null
        }

        val nestedJarUrl = if (jarUrlStr.endsWith("!/")) jarUrlStr else "$jarUrlStr!/"
        return nestedJarExtractor.extract(nestedJarUrl, "stacklens-kotlinc-")?.absolutePath
    }
}

class UrlClassLoaderChainJarStrategy(
    private val compilerClass: Class<*> = K2JVMCompiler::class.java,
    private val classLoaderSupplier: (Class<*>) -> ClassLoader? = { it.classLoader },
) : KotlinCompilerJarStrategy {
    override fun locate(): String? =
        runCatching {
            var cl: ClassLoader? = classLoaderSupplier(compilerClass)
            while (cl != null) {
                val ucl = cl as? java.net.URLClassLoader
                if (ucl != null) {
                    val match =
                        ucl.urLs.firstOrNull { url -> "kotlin-compiler-embeddable" in url.file }
                    if (match != null) {
                        val f = File(match.toURI())
                        if (f.exists()) return@runCatching f.absolutePath
                    }
                }
                cl = cl.parent
            }
            null
        }.getOrNull()
}

class KotlinCompilerJarLocator(
    private val strategies: List<KotlinCompilerJarStrategy>,
) {
    fun locate(): String? = strategies.firstNotNullOfOrNull { it.locate() }

    companion object {
        fun default(fatJarBuilder: FatJarBuilder): KotlinCompilerJarLocator =
            KotlinCompilerJarLocator(
                listOf(
                    ClasspathJarStrategy(fatJarBuilder),
                    ProtectionDomainJarStrategy(),
                    ClassloaderResourceJarStrategy(),
                    UrlClassLoaderChainJarStrategy(),
                ),
            )
    }
}

class KotlinStdlibJarLocator(
    private val jarNamePrefix: String,
    private val strategies: List<KotlinCompilerJarStrategy>,
) {
    private val log = LoggerFactory.getLogger(KotlinStdlibJarLocator::class.java)

    fun locate(): String? {
        log.info("[KotlincJar-Locator] Trying {} stdlib strategies for {}", strategies.size, jarNamePrefix)
        val r = strategies.firstNotNullOfOrNull { it.locate() }
        log.info("[KotlincJar-Locator] FINAL {} RESULT: {}", jarNamePrefix, r ?: "null")
        return r
    }

    companion object {
        fun default(
            fatJarBuilder: FatJarBuilder,
            jarNamePrefix: String = "kotlin-stdlib",
        ): KotlinStdlibJarLocator =
            KotlinStdlibJarLocator(
                jarNamePrefix,
                listOf(
                    ClasspathJarStrategyFor(fatJarBuilder, jarNamePrefix),
                    ProtectionDomainJarStrategyFor(jarNamePrefix),
                    ClassloaderResourceJarStrategyFor(jarNamePrefix),
                    UrlClassLoaderChainJarStrategyFor(jarNamePrefix),
                ),
            )
    }
}

internal class ClasspathJarStrategyFor(
    private val fatJarBuilder: FatJarBuilder,
    private val prefix: String,
) : KotlinCompilerJarStrategy {
    override fun locate(): String? = runCatching { fatJarBuilder.findJarOnClasspath(prefix) }.getOrNull()
}

internal class ProtectionDomainJarStrategyFor(
    private val prefix: String,
    private val anchorClass: Class<*> = K2JVMCompiler::class.java,
) : KotlinCompilerJarStrategy {
    override fun locate(): String? =
        runCatching {
            val location =
                anchorClass.protectionDomain
                    .codeSource
                    ?.location ?: return@runCatching null
            val f = File(location.toURI())
            val stem = f.name.removeSuffix(".jar")
            if (f.exists() && f.isFile && (stem == prefix || stem.startsWith("$prefix-"))) f.absolutePath else null
        }.getOrNull()
}

internal class ClassloaderResourceJarStrategyFor(
    private val prefix: String,
    private val anchorClass: Class<*> = K2JVMCompiler::class.java,
    private val nestedJarExtractor: NestedJarExtractor = NestedJarExtractor(),
) : KotlinCompilerJarStrategy {
    override fun locate(): String? =
        runCatching {
            val cl = anchorClass.classLoader ?: return@runCatching null
            val compilerUrl =
                cl.getResource(
                    anchorClass.name.replace('.', '/') + ".class",
                ) ?: return@runCatching null
            val compilerUrlStr = compilerUrl.toString()
            val markerIdx = compilerUrlStr.indexOf(CompilerJarNames.EMBEDDABLE_MARKER)
            if (markerIdx < 0) return@runCatching null
            val jarPrefix = compilerUrlStr.substring(0, markerIdx)
            val tail = compilerUrlStr.substring(markerIdx + CompilerJarNames.EMBEDDABLE_MARKER.length)
            val versionEnd = tail.indexOf(".jar!/")
            if (versionEnd < 0) return@runCatching null
            val version = tail.substring(0, versionEnd).removePrefix("-")
            val nestedJarUrl = "$jarPrefix$prefix-$version.jar!/"
            val tempJar = nestedJarExtractor.extract(nestedJarUrl, "stacklens-kotlin-$prefix-") ?: return@runCatching null
            if (tempJar.length() > 0L) tempJar.absolutePath else null
        }.getOrNull()
}

internal class UrlClassLoaderChainJarStrategyFor(
    private val prefix: String,
    private val anchorClass: Class<*> = K2JVMCompiler::class.java,
) : KotlinCompilerJarStrategy {
    override fun locate(): String? =
        runCatching {
            var cl: ClassLoader? = anchorClass.classLoader
            while (cl != null) {
                val ucl = cl as? java.net.URLClassLoader
                if (ucl != null) {
                    val match =
                        ucl.urLs.firstOrNull { url ->
                            val name = url.file.removeSuffix(".jar").substringAfterLast("/")
                            name == prefix || name.startsWith("$prefix-")
                        }
                    if (match != null) {
                        val f = File(match.toURI())
                        if (f.exists()) return@runCatching f.absolutePath
                    }
                }
                cl = cl.parent
            }
            null
        }.getOrNull()
}

class KotlinRuntimeJarLocator(
    private val strategies: List<KotlinCompilerJarStrategy>,
) {
    fun locateAll(): List<String> {
        for (s in strategies) {
            val r = runCatching { (s as? RuntimeJarStrategy)?.locateAll() ?: s.locate()?.let { listOf(it) } }.getOrNull()
            if (!r.isNullOrEmpty()) return r
        }
        return emptyList()
    }

    companion object {
        fun default(): KotlinRuntimeJarLocator =
            KotlinRuntimeJarLocator(
                listOf(
                    RuntimeClasspathStrategy(),
                    RuntimeNestedJarStrategy(),
                    RuntimeUrlClassLoaderChainStrategy(),
                ),
            )
    }
}

internal object CompilerJarNames {
    const val EMBEDDABLE_MARKER = "kotlin-compiler-embeddable"

    val STDLIB_CANDIDATES: List<String> =
        listOf(
            "kotlin-stdlib",
            "kotlin-reflect",
            "kotlin-script-runtime",
            "kotlinx-coroutines-core-jvm",
            "kotlinx-coroutines-reactive",
            "kotlinx-coroutines-reactor",
            "kotlinx-coroutines-jdk8",
            "kotlinx-serialization-core-jvm",
            "kotlinx-serialization-json-jvm",
        )

    val CANDIDATE_VERSIONS: List<String> = listOf("1.8.1", "2.3.20", "2.1.0", "1.7.3", "1.6.4")
}

internal fun isKotlinRuntimeArtifact(fileName: String): Boolean =
    fileName.startsWith("kotlin") &&
        fileName.endsWith(".jar") &&
        !fileName.contains("sources") &&
        !fileName.contains("javadoc")

internal class RuntimeClasspathStrategy(
    private val classPathSupplier: () -> String? = { System.getProperty("java.class.path") },
) : KotlinCompilerJarStrategy,
    RuntimeJarStrategy {
    override fun locate(): String? = null

    override fun locateAll(): List<String> {
        val entries = classPathSupplier()?.split(File.pathSeparator) ?: return emptyList()
        return entries.filter { entry -> isKotlinRuntimeArtifact(File(entry).name) }
    }
}

internal class RuntimeNestedJarStrategy(
    private val anchorClass: Class<*> = K2JVMCompiler::class.java,
    private val nestedJarExtractor: NestedJarExtractor = NestedJarExtractor(),
) : KotlinCompilerJarStrategy,
    RuntimeJarStrategy {
    override fun locate(): String? = null

    override fun locateAll(): List<String> =
        runCatching {
            val libPrefix = nestedJarLibPrefix() ?: return@runCatching emptyList()
            val found = mutableListOf<String>()
            for (name in CompilerJarNames.STDLIB_CANDIDATES) {
                extractedNestedJar(libPrefix, name)?.let { found += it.absolutePath }
            }
            found
        }.getOrNull() ?: emptyList()

    private fun nestedJarLibPrefix(): String? {
        val cl = anchorClass.classLoader ?: return null
        val compilerUrl =
            cl.getResource(anchorClass.name.replace('.', '/') + ".class") ?: return null
        val compilerUrlStr = compilerUrl.toString()
        val libIdx = compilerUrlStr.lastIndexOf(CompilerJarNames.EMBEDDABLE_MARKER)
        if (libIdx < 0) return null
        return compilerUrlStr.substring(0, compilerUrlStr.lastIndexOf("/", libIdx) + 1)
    }

    private fun extractedNestedJar(
        libPrefix: String,
        name: String,
    ): File? {
        for (version in CompilerJarNames.CANDIDATE_VERSIONS) {
            val tempJar =
                nestedJarExtractor.extract("$libPrefix$name-$version.jar!/", "stacklens-$name-")
                    ?: continue
            if (tempJar.length() > 0L) return tempJar
            tempJar.delete()
        }
        return null
    }
}

internal class RuntimeUrlClassLoaderChainStrategy(
    private val anchorClass: Class<*> = K2JVMCompiler::class.java,
) : KotlinCompilerJarStrategy,
    RuntimeJarStrategy {
    override fun locate(): String? = null

    override fun locateAll(): List<String> =
        runCatching {
            val found = mutableListOf<String>()
            var cl: ClassLoader? = anchorClass.classLoader
            while (cl != null) {
                val ucl = cl as? java.net.URLClassLoader
                if (ucl != null) {
                    for (url in ucl.urLs) {
                        if (isKotlinRuntimeArtifact(url.file.substringAfterLast("/"))) {
                            val f = File(url.toURI())
                            if (f.exists()) found += f.absolutePath
                        }
                    }
                }
                cl = cl.parent
            }
            found.distinct()
        }.getOrNull() ?: emptyList()
}
